package com.causa.mcp;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.causa.common.constants.McpConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.config.McpConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Liberty Logs Context Collector
 *
 * <p>Collects Liberty application log files from a running pod using the Filesystem MCP server.
 * Uses two Filesystem MCP tools in sequence:
 * <ol>
 *   <li>{@code list_directory_with_sizes} — lists the Liberty logs directory with file sizes and metadata</li>
 *   <li>{@code read_text_file} — reads the content of each selected log file</li>
 * </ol>
 *
 * <p><strong>Time window filtering:</strong> Only files whose timestamp falls within
 * {@code alertTimestamp - 5 minutes → alertTimestamp + 1 minute} are collected.
 * Timestamp parsing uses embedded timestamps in filenames for archived logs.
 *
 * <p><strong>Size thresholds:</strong> Files exceeding defined size limits are skipped to prevent
 * token exhaustion and memory pressure.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class LibertyLogsContextCollector {

    private static final CausaLogger log = CausaLogger.getLogger(LibertyLogsContextCollector.class);

    private static final String VERBOSE_GC_PREFIX = "verbosegc.";
    private static final String MESSAGES_LOG_PREFIX = "messages";

    /**
     * Maximum characters of filtered verbosegc content to pass to RCA.
     * 30 global GC blocks × ~3KB avg = ~93KB needed to capture full heap exhaustion
     * progression. 120k chars gives comfortable headroom (~40k tokens).
     */
    private static final int MAX_VERBOSEGC_CHARS = 120_000;

    /**
     * Liberty log levels retained for RCA — SEVERE and WARNING only.
     * INFO and AUDIT lines are dropped as they add no diagnostic value.
     */
    private static final String LOGLEVEL_SEVERE = "\"loglevel\":\"SEVERE\"";
    private static final String LOGLEVEL_WARNING = "\"loglevel\":\"WARNING\"";

    /** Marks the start of a GC pause block. */
    private static final String EXCLUSIVE_START_TAG = "<exclusive-start ";
    /** Marks the end of a GC pause block. */
    private static final String EXCLUSIVE_END_TAG = "<exclusive-end ";
    /** Marks the end of the <initialized> header block. */
    private static final String INITIALIZED_END_TAG = "</initialized>";
    /** Attribute pattern to extract timestamp value from any XML element. */
    private static final Pattern GC_TIMESTAMP_PATTERN = Pattern.compile("timestamp=\"([^\"]+)\"");
    /** Identifies a global (full) GC cycle — always retained regardless of window. */
    private static final Pattern GLOBAL_GC_PATTERN = Pattern.compile("type=\"(?:global|system)\"");

    /**
     * Pattern to parse archived messages.log filename: messages_YY.MM.DD_HH.MM.SS.ms.log
     */
    private static final Pattern MESSAGES_ARCHIVE_PATTERN = Pattern.compile(
        "messages_(\\d{2})\\.(\\d{2})\\.(\\d{2})_(\\d{2})\\.(\\d{2})\\.(\\d{2})\\.(\\d+)\\.log"
    );

    /**
     * Pattern to parse FFDC filename: exception_summary_YY.MM.DD_HH.MM.SS.ms.log or ffdc_YY.MM.DD_HH.MM.SS.ms.log
     */
    private static final Pattern FFDC_LOG_PATTERN = Pattern.compile(
        "(?:exception_summary|ffdc)_(\\d{2})\\.(\\d{2})\\.(\\d{2})_(\\d{2})\\.(\\d{2})\\.(\\d{2})\\.(\\d+)\\.log"
    );

    private final McpConfig mcpConfig;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Inject
    public LibertyLogsContextCollector(McpConfig mcpConfig) {
        this.mcpConfig = mcpConfig;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    /**
     * Collects Liberty application logs from the Filesystem MCP server.
     *
     * <p>First calls {@code list_directory_with_sizes} on the configured Liberty logs directory,
     * filters files by timestamp window and size thresholds, then calls {@code read_text_file} for each selected file.
     *
     * @param alertId the alert ID (used only for logging)
     * @param alertTimestamp the alert timestamp — used to compute 5-minute time window
     * @return combined log content from all read files, or {@code null} on complete failure
     */
    public String collectLibertyLogs(String alertId, Instant alertTimestamp) {
        String libertyLogsDir = mcpConfig.filesystem().libertyLogsDir();
        int timeoutMs = mcpConfig.filesystem().timeoutMs();
        String endpoint = mcpConfig.filesystem().endpoint() + McpConstants.Paths.MCP_ENDPOINT;

        try {
            // Step 1: list_directory_with_sizes to discover log files with metadata
            String sessionId = initializeMcpSession(endpoint, timeoutMs);

            ObjectNode listArgs = objectMapper.createObjectNode();
            listArgs.put(McpConstants.Arguments.PATH, libertyLogsDir);

            log.info(LogMessages.Mcp.MCP_FILESYSTEM_LIST_DIRECTORY)
                .field(McpConstants.LogFields.ALERT_ID, alertId)
                .field(McpConstants.Arguments.PATH, libertyLogsDir)
                .log();

            JsonNode listResult = callMcpTool(endpoint, sessionId,
                McpConstants.Tools.FILESYSTEM_LIST_DIRECTORY_WITH_SIZES, listArgs, timeoutMs);

            String directoryListing = extractTextFromContent(listResult);
            if (directoryListing == null || directoryListing.isBlank()) {
                log.warn(LogMessages.Mcp.MCP_CALL_FAILED)
                    .field(McpConstants.LogFields.TOOL, McpConstants.Tools.FILESYSTEM_LIST_DIRECTORY_WITH_SIZES)
                    .field(McpConstants.LogFields.ALERT_ID, alertId)
                    .field(McpConstants.LogFields.ERROR, "Empty directory listing returned")
                    .log();
                return null;
            }

            // Step 2: Parse directory listing and filter files by window + size
            List<LogFileEntry> candidateFiles = parseDirectoryListing(directoryListing, alertId, alertTimestamp);

            // Step 3: read_text_file for each selected file
            StringBuilder combined = new StringBuilder();
            int filesRead = 0;

            for (LogFileEntry entry : candidateFiles) {
                String filePath = libertyLogsDir.endsWith("/")
                    ? libertyLogsDir + entry.filename
                    : libertyLogsDir + "/" + entry.filename;

                try {
                    String fileSessionId = initializeMcpSession(endpoint, timeoutMs);

                    ObjectNode readArgs = objectMapper.createObjectNode();
                    readArgs.put(McpConstants.Arguments.PATH, filePath);

                    log.info(LogMessages.Mcp.MCP_FILESYSTEM_READ_FILE)
                        .field(McpConstants.LogFields.ALERT_ID, alertId)
                        .field(McpConstants.Arguments.PATH, filePath)
                        .log();

                    JsonNode readResult = callMcpTool(endpoint, fileSessionId,
                        McpConstants.Tools.FILESYSTEM_READ_FILE, readArgs, timeoutMs);

                    String fileContent = extractTextFromContent(readResult);
                    if (fileContent != null && !fileContent.isBlank()) {
                        combined.append("=== ").append(entry.filename).append(" ===\n");
                        String content;
                        if (entry.filename.startsWith(VERBOSE_GC_PREFIX)) {
                            content = filterVerboseGcContent(fileContent, alertTimestamp);
                        } else if (entry.filename.startsWith(MESSAGES_LOG_PREFIX)) {
                            content = filterMessagesLogContent(fileContent);
                        } else {
                            content = fileContent;
                        }
                        combined.append(content).append("\n\n");
                        filesRead++;
                    }

                } catch (Exception e) {
                    log.warn(LogMessages.Mcp.MCP_CALL_FAILED)
                        .field(McpConstants.LogFields.TOOL, McpConstants.Tools.FILESYSTEM_READ_FILE)
                        .field(McpConstants.LogFields.ALERT_ID, alertId)
                        .field(McpConstants.Arguments.PATH, filePath)
                        .field(McpConstants.LogFields.ERROR, e.getMessage())
                        .log();
                }
            }

            // Step 4: Collect FFDC logs if ffdc/ directory exists
            String ffdcContent = collectFfdcLogs(endpoint, libertyLogsDir, alertId, alertTimestamp, timeoutMs);
            if (ffdcContent != null && !ffdcContent.isBlank()) {
                combined.append(ffdcContent);
            }

            if (combined.length() == 0) {
                return null;
            }

            log.info(LogMessages.Mcp.MCP_FILESYSTEM_LIBERTY_LOGS_COLLECTED)
                .field(McpConstants.LogFields.ALERT_ID, alertId)
                .field("filesRead", filesRead)
                .log();

            return combined.toString().trim();

        } catch (Exception e) {
            log.warn(LogMessages.Mcp.MCP_CALL_FAILED)
                .field(McpConstants.LogFields.TOOL, McpConstants.Tools.FILESYSTEM_LIST_DIRECTORY_WITH_SIZES)
                .field(McpConstants.LogFields.ALERT_ID, alertId)
                .field(McpConstants.LogFields.ERROR, e.getMessage())
                .log();
            return null;
        }
    }

    /**
     * Collects FFDC exception logs from the ffdc/ subdirectory.
     */
    private String collectFfdcLogs(String endpoint, String logsDir, String alertId,
                                    Instant alertTimestamp, int timeoutMs) {
        String ffdcDir = logsDir.endsWith("/") ? logsDir + McpConstants.Filesystem.FFDC_DIR
                                                : logsDir + "/" + McpConstants.Filesystem.FFDC_DIR;
        try {
            String sessionId = initializeMcpSession(endpoint, timeoutMs);

            ObjectNode listArgs = objectMapper.createObjectNode();
            listArgs.put(McpConstants.Arguments.PATH, ffdcDir);

            log.info(LogMessages.Mcp.MCP_FILESYSTEM_FFDC_LIST)
                .field(McpConstants.LogFields.ALERT_ID, alertId)
                .field(McpConstants.Arguments.PATH, ffdcDir)
                .log();

            JsonNode listResult = callMcpTool(endpoint, sessionId,
                McpConstants.Tools.FILESYSTEM_LIST_DIRECTORY_WITH_SIZES, listArgs, timeoutMs);

            String ffdcListing = extractTextFromContent(listResult);
            if (ffdcListing == null || ffdcListing.isBlank()) {
                return null;
            }

            List<LogFileEntry> ffdcFiles = parseFfdcDirectoryListing(ffdcListing, alertId, alertTimestamp);
            StringBuilder ffdcContent = new StringBuilder();

            for (LogFileEntry entry : ffdcFiles) {
                String filePath = ffdcDir.endsWith("/") ? ffdcDir + entry.filename
                                                        : ffdcDir + "/" + entry.filename;
                try {
                    String fileSessionId = initializeMcpSession(endpoint, timeoutMs);
                    ObjectNode readArgs = objectMapper.createObjectNode();
                    readArgs.put(McpConstants.Arguments.PATH, filePath);

                    JsonNode readResult = callMcpTool(endpoint, fileSessionId,
                        McpConstants.Tools.FILESYSTEM_READ_FILE, readArgs, timeoutMs);

                    String fileContent = extractTextFromContent(readResult);
                    if (fileContent != null && !fileContent.isBlank()) {
                        ffdcContent.append("=== ffdc/").append(entry.filename).append(" ===\n");
                        ffdcContent.append(filterFfdcContent(fileContent)).append("\n\n");
                    }
                } catch (Exception e) {
                    log.warn(LogMessages.Mcp.MCP_CALL_FAILED)
                        .field(McpConstants.LogFields.TOOL, McpConstants.Tools.FILESYSTEM_READ_FILE)
                        .field(McpConstants.LogFields.ALERT_ID, alertId)
                        .field(McpConstants.Arguments.PATH, filePath)
                        .field(McpConstants.LogFields.ERROR, e.getMessage())
                        .log();
                }
            }

            return ffdcContent.toString();

        } catch (Exception e) {
            log.warn(LogMessages.Mcp.MCP_CALL_FAILED)
                .field(McpConstants.LogFields.TOOL, McpConstants.Tools.FILESYSTEM_LIST_DIRECTORY_WITH_SIZES)
                .field(McpConstants.LogFields.ALERT_ID, alertId)
                .field(McpConstants.Arguments.PATH, ffdcDir)
                .field(McpConstants.LogFields.ERROR, e.getMessage())
                .log();
            return null;
        }
    }

    /**
     * Parses the directory listing output from list_directory_with_sizes.
     * Filters files by time window and size thresholds.
     * Collects messages.log, archived messages logs within the alert window, and the latest verbosegc.* file.
     */
    private List<LogFileEntry> parseDirectoryListing(String listing, String alertId, Instant alertTimestamp) {
        List<LogFileEntry> result = new ArrayList<>();
        Instant windowStart = alertTimestamp.minus(Duration.ofMinutes(mcpConfig.filesystem().alertWindowMinutes()));
        Instant windowEnd = alertTimestamp.plus(Duration.ofMinutes(1)); // 1 min forward tolerance
        LogFileEntry latestVerboseGc = null;

        for (String line : listing.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith(McpConstants.Filesystem.FILE_PREFIX)) {
                continue;
            }

            String remainder = trimmed.substring(McpConstants.Filesystem.FILE_PREFIX.length()).trim();
            ParsedFileEntry parsedEntry = parseSizedListingEntry(remainder);
            String filename = parsedEntry.filename();
            long sizeBytes = parsedEntry.sizeBytes();

            if (filename.equals(McpConstants.Filesystem.MESSAGES_LOG)) {
                if (sizeBytes > 0 && sizeBytes > McpConstants.Filesystem.MAX_MESSAGES_TRACE_BYTES) {
                    log.info(LogMessages.Mcp.MCP_FILESYSTEM_FILE_SKIPPED_SIZE)
                        .field(McpConstants.LogFields.ALERT_ID, alertId)
                        .field("filename", filename)
                        .field("sizeBytes", sizeBytes)
                        .log();
                    continue;
                }
                result.add(new LogFileEntry(filename, sizeBytes, alertTimestamp, true));
                continue;
            }

            if (filename.startsWith(McpConstants.Filesystem.MESSAGES_ARCHIVE_PREFIX) && filename.endsWith(".log")) {
                Instant fileTimestamp = parseMessagesArchiveTimestamp(filename);
                if (fileTimestamp != null && fileTimestamp.isAfter(windowStart) && fileTimestamp.isBefore(windowEnd)) {
                    if (sizeBytes > 0 && sizeBytes > McpConstants.Filesystem.MAX_MESSAGES_TRACE_BYTES) {
                        log.info(LogMessages.Mcp.MCP_FILESYSTEM_FILE_SKIPPED_SIZE)
                            .field(McpConstants.LogFields.ALERT_ID, alertId)
                            .field("filename", filename)
                            .field("sizeBytes", sizeBytes)
                            .log();
                        continue;
                    }
                    result.add(new LogFileEntry(filename, sizeBytes, fileTimestamp, true));
                }
                continue;
            }

            if (filename.startsWith(VERBOSE_GC_PREFIX)) {
                if (sizeBytes > 0 && sizeBytes > McpConstants.Filesystem.MAX_MESSAGES_TRACE_BYTES) {
                    log.info(LogMessages.Mcp.MCP_FILESYSTEM_FILE_SKIPPED_SIZE)
                        .field(McpConstants.LogFields.ALERT_ID, alertId)
                        .field("filename", filename)
                        .field("sizeBytes", sizeBytes)
                        .log();
                    continue;
                }

                LogFileEntry verboseGcEntry = new LogFileEntry(filename, sizeBytes, alertTimestamp, false);
                if (latestVerboseGc == null || filename.compareTo(latestVerboseGc.filename) > 0) {
                    latestVerboseGc = verboseGcEntry;
                }
            }
        }

        if (latestVerboseGc != null) {
            result.add(latestVerboseGc);
        }

        result.sort(Comparator.comparing((LogFileEntry e) -> e.timestamp).reversed());
        return result;
    }

    /**
     * Parses FFDC directory listing and filters exception_summary and ffdc files by time window and size.
     */
    private List<LogFileEntry> parseFfdcDirectoryListing(String listing, String alertId, Instant alertTimestamp) {
        List<LogFileEntry> result = new ArrayList<>();
        Instant windowStart = alertTimestamp.minus(Duration.ofMinutes(mcpConfig.filesystem().alertWindowMinutes()));
        Instant windowEnd = alertTimestamp.plus(Duration.ofMinutes(1));

        for (String line : listing.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith(McpConstants.Filesystem.FILE_PREFIX)) {
                continue;
            }

            String remainder = trimmed.substring(McpConstants.Filesystem.FILE_PREFIX.length()).trim();
            ParsedFileEntry parsedEntry = parseSizedListingEntry(remainder);
            String filename = parsedEntry.filename();
            long sizeBytes = parsedEntry.sizeBytes();

            if (!filename.endsWith(".log")
                    || !(filename.startsWith("exception_summary_") || filename.startsWith("ffdc_"))) {
                continue;
            }

            Instant fileTimestamp = parseFfdcLogTimestamp(filename);
            if (fileTimestamp == null || !fileTimestamp.isAfter(windowStart) || !fileTimestamp.isBefore(windowEnd)) {
                log.info(LogMessages.Mcp.MCP_FILESYSTEM_FILE_SKIPPED_WINDOW)
                    .field(McpConstants.LogFields.ALERT_ID, alertId)
                    .field("filename", filename)
                    .log();
                continue;
            }

            if (sizeBytes > 0 && sizeBytes > McpConstants.Filesystem.MAX_FFDC_BYTES) {
                log.info(LogMessages.Mcp.MCP_FILESYSTEM_FILE_SKIPPED_SIZE)
                    .field(McpConstants.LogFields.ALERT_ID, alertId)
                    .field("filename", filename)
                    .field("sizeBytes", sizeBytes)
                    .log();
                continue;
            }

            result.add(new LogFileEntry(filename, sizeBytes, fileTimestamp, false));
        }

        result.sort(Comparator.comparing((LogFileEntry e) -> e.timestamp).reversed());
        return result;
    }

    /**
     * Parses timestamp from archived messages.log filename: messages_YY.MM.DD_HH.MM.SS.ms.log
     */
    private Instant parseMessagesArchiveTimestamp(String filename) {
        Matcher m = MESSAGES_ARCHIVE_PATTERN.matcher(filename);
        if (m.matches()) {
            try {
                int year = 2000 + Integer.parseInt(m.group(1)); // YY → 20YY
                int month = Integer.parseInt(m.group(2));
                int day = Integer.parseInt(m.group(3));
                int hour = Integer.parseInt(m.group(4));
                int minute = Integer.parseInt(m.group(5));
                int second = Integer.parseInt(m.group(6));
                return Instant.parse(String.format("%04d-%02d-%02dT%02d:%02d:%02dZ",
                    year, month, day, hour, minute, second));
            } catch (DateTimeParseException | NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * Parses timestamp from FFDC filename: exception_summary_YY.MM.DD_HH.MM.SS.ms.log or ffdc_YY.MM.DD_HH.MM.SS.ms.log
     */
    private Instant parseFfdcLogTimestamp(String filename) {
        Matcher m = FFDC_LOG_PATTERN.matcher(filename);
        if (m.matches()) {
            try {
                int year = 2000 + Integer.parseInt(m.group(1));
                int month = Integer.parseInt(m.group(2));
                int day = Integer.parseInt(m.group(3));
                int hour = Integer.parseInt(m.group(4));
                int minute = Integer.parseInt(m.group(5));
                int second = Integer.parseInt(m.group(6));
                return Instant.parse(String.format("%04d-%02d-%02dT%02d:%02d:%02dZ",
                    year, month, day, hour, minute, second));
            } catch (DateTimeParseException | NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private ParsedFileEntry parseSizedListingEntry(String entry) {
        String[] parts = entry.split("\\s{2,}", 2);
        if (parts.length < 2) {
            return new ParsedFileEntry(entry.trim(), 0);
        }
        return new ParsedFileEntry(parts[0].trim(), parseHumanReadableSize(parts[1].trim()));
    }

    private long parseHumanReadableSize(String sizeText) {
        String normalized = sizeText.replace(",", "").trim();
        Matcher matcher = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*([A-Za-z]+)").matcher(normalized);
        if (!matcher.find()) {
            return 0;
        }

        double value;
        try {
            value = Double.parseDouble(matcher.group(1));
        } catch (NumberFormatException e) {
            return 0;
        }

        String unit = matcher.group(2).toUpperCase();
        long multiplier = switch (unit) {
            case "B" -> 1L;
            case "KB" -> 1024L;
            case "MB" -> 1024L * 1024L;
            case "GB" -> 1024L * 1024L * 1024L;
            default -> 0L;
        };

        return multiplier == 0L ? 0L : Math.round(value * multiplier);
    }

    // -------------------------------------------------------------------------
    // MCP protocol helpers (same protocol as McpContextCollector)
    // -------------------------------------------------------------------------

    private String initializeMcpSession(String endpoint, int timeoutMs) throws Exception {
        ObjectNode initRequest = objectMapper.createObjectNode();
        initRequest.put(McpConstants.JsonRpc.FIELD_JSONRPC, McpConstants.JsonRpc.VERSION);
        initRequest.put(McpConstants.JsonRpc.FIELD_ID, 1);
        initRequest.put(McpConstants.JsonRpc.FIELD_METHOD, McpConstants.JsonRpc.METHOD_INITIALIZE);

        ObjectNode params = objectMapper.createObjectNode();
        params.put(McpConstants.JsonRpc.PARAM_PROTOCOL_VERSION, McpConstants.PROTOCOL_VERSION);

        ObjectNode clientInfo = objectMapper.createObjectNode();
        clientInfo.put(McpConstants.Arguments.NAME, McpConstants.CLIENT_NAME);
        clientInfo.put(McpConstants.Format.VERSION, McpConstants.CLIENT_VERSION);
        params.set(McpConstants.JsonRpc.PARAM_CLIENT_INFO, clientInfo);
        params.set(McpConstants.JsonRpc.PARAM_CAPABILITIES, objectMapper.createObjectNode());
        initRequest.set(McpConstants.JsonRpc.FIELD_PARAMS, params);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header(McpConstants.Headers.CONTENT_TYPE, McpConstants.Headers.CONTENT_TYPE_JSON)
            .header(McpConstants.Headers.ACCEPT, McpConstants.Headers.ACCEPT_VALUE)
            .POST(HttpRequest.BodyPublishers.ofString(initRequest.toString()))
            .timeout(Duration.ofMillis(timeoutMs))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException(String.format(McpConstants.Errors.MCP_INITIALIZE_FAILED,
                response.statusCode(), response.body()));
        }

        String sessionId = response.headers().firstValue(McpConstants.Headers.MCP_SESSION_ID)
            .orElse(UUID.randomUUID().toString());

        sendInitializedNotification(endpoint, sessionId, timeoutMs);
        return sessionId;
    }

    private void sendInitializedNotification(String endpoint, String sessionId, int timeoutMs) throws Exception {
        ObjectNode notification = objectMapper.createObjectNode();
        notification.put(McpConstants.JsonRpc.FIELD_JSONRPC, McpConstants.JsonRpc.VERSION);
        notification.put(McpConstants.JsonRpc.FIELD_METHOD, McpConstants.JsonRpc.METHOD_NOTIFICATIONS_INITIALIZED);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header(McpConstants.Headers.CONTENT_TYPE, McpConstants.Headers.CONTENT_TYPE_JSON)
            .header(McpConstants.Headers.ACCEPT, McpConstants.Headers.ACCEPT_VALUE)
            .header(McpConstants.Headers.MCP_SESSION_ID, sessionId)
            .POST(HttpRequest.BodyPublishers.ofString(notification.toString()))
            .timeout(Duration.ofMillis(timeoutMs))
            .build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode callMcpTool(String endpoint, String sessionId, String toolName,
                                  ObjectNode arguments, int timeoutMs) throws Exception {
        ObjectNode toolRequest = objectMapper.createObjectNode();
        toolRequest.put(McpConstants.JsonRpc.FIELD_JSONRPC, McpConstants.JsonRpc.VERSION);
        toolRequest.put(McpConstants.JsonRpc.FIELD_ID, 2);
        toolRequest.put(McpConstants.JsonRpc.FIELD_METHOD, McpConstants.JsonRpc.METHOD_TOOLS_CALL);

        ObjectNode params = objectMapper.createObjectNode();
        params.put(McpConstants.JsonRpc.PARAM_NAME, toolName);
        params.set(McpConstants.JsonRpc.PARAM_ARGUMENTS, arguments);
        toolRequest.set(McpConstants.JsonRpc.FIELD_PARAMS, params);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header(McpConstants.Headers.CONTENT_TYPE, McpConstants.Headers.CONTENT_TYPE_JSON)
            .header(McpConstants.Headers.ACCEPT, McpConstants.Headers.ACCEPT_VALUE)
            .header(McpConstants.Headers.MCP_SESSION_ID, sessionId)
            .POST(HttpRequest.BodyPublishers.ofString(toolRequest.toString()))
            .timeout(Duration.ofMillis(timeoutMs))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException(String.format(McpConstants.Errors.MCP_TOOL_CALL_FAILED,
                response.statusCode(), response.body()));
        }

        String jsonData = parseSSEResponse(response.body());
        JsonNode responseNode = objectMapper.readTree(jsonData);

        if (responseNode.has(McpConstants.JsonRpc.FIELD_ERROR)) {
            throw new RuntimeException(String.format(McpConstants.Errors.MCP_TOOL_ERROR,
                responseNode.get(McpConstants.JsonRpc.FIELD_ERROR).toString()));
        }

        return responseNode.get(McpConstants.JsonRpc.FIELD_RESULT);
    }

    private String parseSSEResponse(String sseResponse) {
        for (String line : sseResponse.split(McpConstants.SSE.LINE_SEPARATOR)) {
            if (line.startsWith(McpConstants.SSE.DATA_PREFIX)) {
                return line.substring(McpConstants.SSE.DATA_PREFIX.length()).trim();
            }
        }
        return sseResponse;
    }

    private String extractTextFromContent(JsonNode result) {
        if (result == null) {
            return null;
        }
        if (result.has(McpConstants.JsonRpc.FIELD_CONTENT) && result.get(McpConstants.JsonRpc.FIELD_CONTENT).isArray()) {
            ArrayNode content = (ArrayNode) result.get(McpConstants.JsonRpc.FIELD_CONTENT);
            if (content.size() > 0 && content.get(0).has(McpConstants.JsonRpc.FIELD_TEXT)) {
                String text = content.get(0).get(McpConstants.JsonRpc.FIELD_TEXT).asText();
                if (text != null && (text.contains(McpConstants.ErrorMarkers.ERROR_CALLING_TOOL)
                        || text.contains(McpConstants.ErrorMarkers.LIST_INDEX_OUT_OF_RANGE))) {
                    return null;
                }
                return text;
            }
        }
        return null;
    }

    /**
     * Filters Liberty FFDC log content for RCA relevance.
     *
     * <p>An FFDC file has two sections:
     * <ol>
     *   <li>Header + exception + stack trace — essential for RCA (exception type, source, call chain)</li>
     *   <li>{@code Dump of callerThis} onwards — deep object state dump with inaccessible fields,
     *       adds no diagnostic value and can be thousands of lines</li>
     * </ol>
     *
     * @param raw the full FFDC file content
     * @return content up to (but not including) the {@code Dump of callerThis} section
     */
    String filterFfdcContent(String raw) {
        int dumpIdx = raw.indexOf("\nDump of callerThis");
        if (dumpIdx < 0) {
            // No object dump section found — return as-is
            return raw;
        }
        return raw.substring(0, dumpIdx)
            + "\n<!-- ffdc object dump section omitted -->\n";
    }

    /**
     * Filters Liberty messages.log content for RCA relevance.
     *
     * <p>The file has two sections:
     * <ol>
     *   <li>A plain-text header block (product info, JVM details) — always kept</li>
     *   <li>JSON-per-line log entries — only {@code SEVERE} and {@code WARNING} lines are kept</li>
     * </ol>
     *
     * <p>INFO and AUDIT lines (startup banners, feature loading, LTPA keys, etc.)
     * are dropped as they add no diagnostic value for RCA.
     *
     * @param raw the full file content as read from the filesystem MCP server
     * @return filtered content with header + SEVERE/WARNING lines only
     */
    String filterMessagesLogContent(String raw) {
        StringBuilder result = new StringBuilder();
        int kept = 0;
        int dropped = 0;

        for (String line : raw.split("\n")) {
            // Plain-text header lines (not JSON) are always kept
            if (!line.trim().startsWith("{")) {
                result.append(line).append("\n");
                continue;
            }
            // JSON log lines: keep only SEVERE and WARNING
            if (line.contains(LOGLEVEL_SEVERE) || line.contains(LOGLEVEL_WARNING)) {
                result.append(line).append("\n");
                kept++;
            } else {
                dropped++;
            }
        }

        if (dropped > 0) {
            result.append("<!-- messages filtered: kept ").append(kept)
                  .append(" SEVERE/WARNING, dropped ").append(dropped)
                  .append(" INFO/AUDIT lines -->\n");
        }

        return result.toString();
    }

    /**
     * Filters verbosegc XML content to only the GC pause blocks relevant for RCA:
     * <ul>
     *   <li>The {@code <initialized>} header block (heap config, JVM args)</li>
     *   <li>Any {@code <exclusive-start>...<exclusive-end>} block whose timestamp falls
     *       within {@code alertTimestamp - 5min} to {@code alertTimestamp + 1min}</li>
     *   <li>Any block containing a {@code global} or {@code system} GC cycle,
     *       regardless of timestamp (these indicate heap exhaustion)</li>
     * </ul>
     * Output is capped at {@link #MAX_VERBOSEGC_CHARS} characters.
     */
    String filterVerboseGcContent(String raw, Instant alertTimestamp) {
        Instant windowStart = alertTimestamp.minus(Duration.ofMinutes(mcpConfig.filesystem().alertWindowMinutes()));
        Instant windowEnd = alertTimestamp.plus(Duration.ofMinutes(1));

        String[] lines = raw.split("\n");

        // 1. Extract the <initialized> header (everything up to and including </initialized>)
        StringBuilder header = new StringBuilder();
        int bodyStartLine = 0;
        for (int i = 0; i < lines.length; i++) {
            header.append(lines[i]).append("\n");
            if (lines[i].contains(INITIALIZED_END_TAG)) {
                bodyStartLine = i + 1;
                break;
            }
        }

        // 2. Collect exclusive-start..exclusive-end blocks, filtering by window or global GC
        List<String> keptBlocks = new ArrayList<>();
        int i = bodyStartLine;
        while (i < lines.length) {
            if (!lines[i].contains(EXCLUSIVE_START_TAG)) {
                i++;
                continue;
            }

            // Collect the full block until exclusive-end
            StringBuilder block = new StringBuilder();
            boolean foundEnd = false;
            boolean isGlobal = false;
            Instant blockTimestamp = null;

            while (i < lines.length) {
                String line = lines[i];
                block.append(line).append("\n");

                if (blockTimestamp == null && line.contains(EXCLUSIVE_START_TAG)) {
                    Matcher m = GC_TIMESTAMP_PATTERN.matcher(line);
                    if (m.find()) {
                        try {
                            blockTimestamp = Instant.parse(m.group(1));
                        } catch (DateTimeParseException ignored) {}
                    }
                }

                if (GLOBAL_GC_PATTERN.matcher(line).find()) {
                    isGlobal = true;
                }

                if (line.contains(EXCLUSIVE_END_TAG)) {
                    foundEnd = true;
                    i++;
                    break;
                }
                i++;
            }

            if (!foundEnd) {
                // incomplete trailing block — skip
                break;
            }

            boolean inWindow = blockTimestamp != null
                && blockTimestamp.isAfter(windowStart)
                && blockTimestamp.isBefore(windowEnd);

            if (inWindow || isGlobal) {
                keptBlocks.add(block.toString());
            }
        }

        // 3. Assemble output, cap at MAX_VERBOSEGC_CHARS
        StringBuilder result = new StringBuilder(header);
        result.append(String.format("<!-- verbosegc filtered: %d of %d blocks in window or global -->\n",
            keptBlocks.size(), blockStart(lines, bodyStartLine)));

        int charsUsed = result.length();
        for (String block : keptBlocks) {
            if (charsUsed + block.length() > MAX_VERBOSEGC_CHARS) {
                result.append("<!-- remaining blocks truncated at char limit -->\n");
                break;
            }
            result.append(block);
            charsUsed += block.length();
        }

        result.append("</verbosegc>\n");
        return result.toString();
    }

    /** Counts exclusive-start blocks from bodyStartLine onward (for the summary comment). */
    private int blockStart(String[] lines, int from) {
        int count = 0;
        for (int i = from; i < lines.length; i++) {
            if (lines[i].contains(EXCLUSIVE_START_TAG)) count++;
        }
        return count;
    }

    private record ParsedFileEntry(String filename, long sizeBytes) {}

    /**
     * Internal value object to hold parsed log file metadata.
     */
    private static class LogFileEntry {
        final String filename;
        final long sizeBytes;
        final Instant timestamp;
        final boolean largeLogFile;

        LogFileEntry(String filename, long sizeBytes, Instant timestamp, boolean largeLogFile) {
            this.filename = filename;
            this.sizeBytes = sizeBytes;
            this.timestamp = timestamp;
            this.largeLogFile = largeLogFile;
        }

        boolean isLargeLogFile() {
            return largeLogFile;
        }
    }
}
