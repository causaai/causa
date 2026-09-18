package com.causa.mcp.util;

import com.causa.common.constants.McpConstants;

import java.util.Map;
import java.util.function.Function;

/**
 * MCP Response Formatter
 *
 * <p>Tool-specific post-processing for known MCP tool responses — e.g. turning Kubernetes'
 * YAML event stream into readable {@code [Type] Timestamp: Reason - Message} lines. This is the
 * same formatting logic {@code McpContextCollector} used to apply per hardcoded call site, now
 * keyed generically by {@code (serverName, toolName)} so the dynamic collection pipeline can
 * apply it without any per-server branching.
 *
 * <p>A {@code (serverName, toolName)} pair with no registered formatter — including every
 * server/tool added purely via {@code mcp.json} — passes its raw text straight through
 * unchanged. Formatting here is a cosmetic improvement for known tools, never a requirement.
 *
 * @since 0.0.1
 */
public final class McpResponseFormatter {

    private static final Map<String, Function<String, String>> FORMATTERS = Map.of(
            key("kubernetes", "pods_get"), McpResponseFormatter::formatPodStatus,
            key("kubernetes", "events_list"), McpResponseFormatter::formatEvents,
            key("kubernetes", "pods_log"), McpResponseFormatter::formatLogs
    );

    private McpResponseFormatter() {
    }

    /**
     * Applies the formatter registered for {@code (serverName, toolName)}, if any.
     *
     * @param serverName the MCP server name (e.g. {@code kubernetes})
     * @param toolName   the MCP tool name (e.g. {@code events_list})
     * @param rawText    the tool's raw text payload
     * @return the formatted text, or {@code rawText} unchanged if no formatter is registered or
     *         {@code rawText} is {@code null}/blank
     */
    public static String format(String serverName, String toolName, String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return rawText;
        }
        Function<String, String> formatter = FORMATTERS.get(key(serverName, toolName));
        return formatter != null ? formatter.apply(rawText) : rawText;
    }

    private static String key(String serverName, String toolName) {
        return serverName + ":" + toolName;
    }

    /**
     * Extracts essential pod status information from full {@code pods_get} YAML.
     * Returns: state, startedAt, restartCount, image, resources (requests/limits).
     */
    private static String formatPodStatus(String podStatusYaml) {
        StringBuilder summary = new StringBuilder();
        String[] lines = podStatusYaml.split("\n");

        String state = null;
        String startedAt = null;
        String restartCount = null;
        String cpuLimit = null;
        String memoryLimit = null;
        String cpuRequest = null;
        String memoryRequest = null;
        String lastTerminatedReason = null;
        String lastTerminatedExitCode = null;
        String lastTerminatedAt = null;
        String containerImage = null;

        boolean inStatus = false;
        boolean inContainerStatuses = false;
        boolean readingFirstContainerStatus = false;
        boolean inStateSection = false;
        boolean inLastStateSection = false;
        boolean inLastStateTerminated = false;

        boolean inSpec = false;
        boolean inSpecContainers = false;
        boolean readingFirstSpecContainer = false;
        boolean inResources = false;
        boolean inLimits = false;
        boolean inRequests = false;

        int containerStatusBaseIndent = 0;
        int specContainerBaseIndent = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            int indent = line.length() - line.replaceAll("^\\s+", "").length();

            if (trimmed.equals(McpConstants.Yaml.STATUS_SECTION)) {
                inStatus = true;
                inSpec = false;
                continue;
            }

            if (trimmed.equals(McpConstants.Yaml.SPEC_SECTION)) {
                inSpec = true;
                inStatus = false;
                continue;
            }

            // === Parse status.containerStatuses (for runtime state) ===
            if (inStatus && trimmed.equals(McpConstants.Yaml.CONTAINER_STATUSES_SECTION)) {
                inContainerStatuses = true;
                containerStatusBaseIndent = indent;
                continue;
            }

            if (inContainerStatuses) {
                if (trimmed.startsWith(McpConstants.Yaml.ITEM_PREFIX)
                        && (indent == containerStatusBaseIndent || indent == containerStatusBaseIndent + 2)) {
                    readingFirstContainerStatus = true;
                    continue;
                }

                if (indent <= containerStatusBaseIndent && !trimmed.isEmpty() && !trimmed.startsWith(McpConstants.Yaml.ITEM_PREFIX)) {
                    inContainerStatuses = false;
                    readingFirstContainerStatus = false;
                }

                if (readingFirstContainerStatus) {
                    if (trimmed.startsWith(McpConstants.Yaml.RESTART_COUNT_FIELD)) {
                        restartCount = trimmed.split(McpConstants.Yaml.COLON_SEPARATOR, McpConstants.Yaml.COLON_SPLIT_LIMIT)[1].trim();
                    } else if (trimmed.equals(McpConstants.Yaml.STATE_FIELD)) {
                        inStateSection = true;
                        inLastStateSection = false;
                        inLastStateTerminated = false;
                    } else if (trimmed.equals(McpConstants.Yaml.LAST_STATE_FIELD)) {
                        inLastStateSection = true;
                        inStateSection = false;
                    } else if (inStateSection && trimmed.equals(McpConstants.Yaml.RUNNING_STATE)) {
                        state = "Running";
                    } else if (inStateSection && trimmed.equals(McpConstants.Yaml.WAITING_STATE)) {
                        state = "Waiting";
                    } else if (inStateSection && trimmed.equals(McpConstants.Yaml.TERMINATED_STATE)) {
                        state = "Terminated";
                    } else if (inStateSection && trimmed.startsWith(McpConstants.Yaml.STARTED_AT_FIELD)) {
                        startedAt = trimmed.split(McpConstants.Yaml.COLON_SEPARATOR, McpConstants.Yaml.COLON_SPLIT_LIMIT)[1].trim().replace("\"", "");
                        inStateSection = false;
                    } else if (inLastStateSection && trimmed.equals(McpConstants.Yaml.TERMINATED_STATE)) {
                        inLastStateTerminated = true;
                    } else if (inLastStateTerminated && trimmed.startsWith(McpConstants.Yaml.REASON_PREFIX)) {
                        lastTerminatedReason = trimmed.split(McpConstants.Yaml.COLON_SEPARATOR, McpConstants.Yaml.COLON_SPLIT_LIMIT)[1].trim();
                    } else if (inLastStateTerminated && trimmed.startsWith(McpConstants.Yaml.EXIT_CODE_FIELD)) {
                        lastTerminatedExitCode = trimmed.split(McpConstants.Yaml.COLON_SEPARATOR, McpConstants.Yaml.COLON_SPLIT_LIMIT)[1].trim();
                    } else if (inLastStateTerminated && trimmed.startsWith(McpConstants.Yaml.FINISHED_AT_FIELD)) {
                        lastTerminatedAt = trimmed.split(McpConstants.Yaml.COLON_SEPARATOR, McpConstants.Yaml.COLON_SPLIT_LIMIT)[1].trim().replace("\"", "");
                    }
                }
            }

            // === Parse spec.containers (for resources) ===
            if (inSpec && trimmed.equals(McpConstants.Yaml.CONTAINERS_SECTION)) {
                inSpecContainers = true;
                specContainerBaseIndent = indent;
                continue;
            }

            if (inSpec && trimmed.equals(McpConstants.Yaml.INIT_CONTAINER_STATUSES_SECTION)) {
                inSpecContainers = false;
                readingFirstSpecContainer = false;
            }

            if (inSpecContainers) {
                if (trimmed.startsWith(McpConstants.Yaml.ITEM_PREFIX)
                        && (indent == specContainerBaseIndent || indent == specContainerBaseIndent + 2)) {
                    readingFirstSpecContainer = true;
                    continue;
                }

                if (readingFirstSpecContainer) {
                    if (containerImage == null && trimmed.startsWith(McpConstants.Yaml.IMAGE_FIELD)) {
                        containerImage = trimmed.split(McpConstants.Yaml.COLON_SEPARATOR, McpConstants.Yaml.COLON_SPLIT_LIMIT)[1].trim();
                    } else if (trimmed.equals(McpConstants.Yaml.RESOURCES_SECTION)) {
                        inResources = true;
                    } else if (inResources && trimmed.equals(McpConstants.Yaml.LIMITS_SECTION)) {
                        inLimits = true;
                        inRequests = false;
                    } else if (inResources && trimmed.equals(McpConstants.Yaml.REQUESTS_SECTION)) {
                        inRequests = true;
                        inLimits = false;
                    } else if (inLimits && trimmed.startsWith(McpConstants.Yaml.CPU_FIELD)) {
                        cpuLimit = trimmed.split(McpConstants.Yaml.COLON_SEPARATOR, McpConstants.Yaml.COLON_SPLIT_LIMIT)[1].trim();
                    } else if (inLimits && trimmed.startsWith(McpConstants.Yaml.MEMORY_FIELD)) {
                        memoryLimit = trimmed.split(McpConstants.Yaml.COLON_SEPARATOR, McpConstants.Yaml.COLON_SPLIT_LIMIT)[1].trim();
                    } else if (inRequests && trimmed.startsWith(McpConstants.Yaml.CPU_FIELD)) {
                        cpuRequest = trimmed.split(McpConstants.Yaml.COLON_SEPARATOR, McpConstants.Yaml.COLON_SPLIT_LIMIT)[1].trim();
                    } else if (inRequests && trimmed.startsWith(McpConstants.Yaml.MEMORY_FIELD)) {
                        memoryRequest = trimmed.split(McpConstants.Yaml.COLON_SEPARATOR, McpConstants.Yaml.COLON_SPLIT_LIMIT)[1].trim();
                        readingFirstSpecContainer = false;
                        inSpecContainers = false;
                        inResources = false;
                    }
                }
            }
        }

        summary.append("State: ").append(state != null ? state : "Unknown").append("\n");
        if (startedAt != null) {
            summary.append("Started At: ").append(startedAt).append("\n");
        }
        summary.append("Restart Count: ").append(restartCount != null ? restartCount : "0").append("\n");
        if (containerImage != null) {
            summary.append("Image: ").append(containerImage).append("\n");
        }
        if (lastTerminatedReason != null) {
            summary.append("\nLast Terminated State:\n");
            summary.append("  Reason: ").append(lastTerminatedReason).append("\n");
            if (lastTerminatedExitCode != null) {
                summary.append("  Exit Code: ").append(lastTerminatedExitCode).append("\n");
            }
            if (lastTerminatedAt != null) {
                summary.append("  Finished At: ").append(lastTerminatedAt).append("\n");
            }
        }
        summary.append("\nResource Limits:\n");
        summary.append("  CPU: ").append(cpuLimit != null ? cpuLimit : "not set").append("\n");
        summary.append("  Memory: ").append(memoryLimit != null ? memoryLimit : "not set").append("\n");
        summary.append("Resource Requests:\n");
        summary.append("  CPU: ").append(cpuRequest != null ? cpuRequest : "not set").append("\n");
        summary.append("  Memory: ").append(memoryRequest != null ? memoryRequest : "not set").append("\n");

        return summary.toString();
    }

    /**
     * Turns a raw {@code events_list} YAML event stream into readable
     * {@code [Type] Timestamp: Reason - Message} lines.
     */
    private static String formatEvents(String rawText) {
        StringBuilder formatted = new StringBuilder();
        String[] lines = rawText.split("\n");

        String currentType = null;
        String currentReason = null;
        String currentMessage = null;
        String currentTimestamp = null;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith(McpConstants.Yaml.TYPE_PREFIX)) {
                currentType = trimmed.split(McpConstants.Yaml.COLON_SEPARATOR, McpConstants.Yaml.COLON_SPLIT_LIMIT)[1].trim();
            } else if (trimmed.startsWith(McpConstants.Yaml.REASON_FIELD)) {
                currentReason = trimmed.split(McpConstants.Yaml.COLON_SEPARATOR, McpConstants.Yaml.COLON_SPLIT_LIMIT)[1].trim();
            } else if (trimmed.startsWith(McpConstants.Yaml.MESSAGE_FIELD)) {
                currentMessage = trimmed.split(McpConstants.Yaml.COLON_SEPARATOR, McpConstants.Yaml.COLON_SPLIT_LIMIT)[1].trim();
            } else if (trimmed.startsWith(McpConstants.Yaml.TIMESTAMP_FIELD)) {
                currentTimestamp = trimmed.split(McpConstants.Yaml.COLON_SEPARATOR, McpConstants.Yaml.COLON_SPLIT_LIMIT)[1].trim();

                if (currentType != null && currentReason != null) {
                    formatted.append(String.format("[%s] %s: %s - %s\n",
                            currentType, currentTimestamp, currentReason, currentMessage));
                    currentType = null;
                    currentReason = null;
                    currentMessage = null;
                    currentTimestamp = null;
                }
            }
        }

        return formatted.length() > 0 ? formatted.toString() : rawText;
    }

    /** Trims a raw {@code pods_log} response down to its last {@code DEFAULT_TAIL_LINES} lines. */
    private static String formatLogs(String rawText) {
        String[] lines = rawText.split("\n");
        int totalLines = lines.length;
        int startIndex = Math.max(0, totalLines - McpConstants.Defaults.DEFAULT_TAIL_LINES);

        StringBuilder lastLines = new StringBuilder();
        for (int i = startIndex; i < totalLines; i++) {
            if (!lines[i].trim().isEmpty()) {
                lastLines.append(lines[i]).append("\n");
            }
        }

        String trimmed = lastLines.toString().trim();
        return trimmed.isEmpty() ? rawText : trimmed;
    }
}
