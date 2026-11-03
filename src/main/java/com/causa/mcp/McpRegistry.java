package com.causa.mcp;

import com.causa.api.dto.ComponentHealthDto;
import com.causa.common.constants.AppConstants;
import com.causa.common.constants.ConfigConstants.PlatformCategory;
import com.causa.common.constants.McpConstants;
import com.causa.common.utils.EncryptionUtils;
import com.causa.config.ExternalConfigCache;
import com.causa.core.domain.Alert;
import com.causa.core.domain.DiagnosticContext;
import com.causa.core.domain.ExternalConfig;
import com.causa.mcp.config.McpSettings;
import com.causa.mcp.util.AsyncProfilerContextCollector;
import com.causa.mcp.util.CryostatContextCollector;
import com.causa.mcp.util.InstanaContextCollector;
import com.causa.mcp.util.LibertyLogsContextCollector;
import com.causa.mcp.util.McpResponseFormatter;
import com.causa.common.logging.CausaLogger;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Owns one {@link McpClient} per server declared in {@code mcp.json}, populated once at startup
 * via an explicit {@link #init(McpSettings)} call.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class McpRegistry {

    private static final CausaLogger log = CausaLogger.getLogger(McpRegistry.class);

    private final Map<String, McpClient> clients = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;
    private volatile String initializationError;

    private final LibertyLogsContextCollector libertyLogsContextCollector;
    private final ExternalConfigCache externalConfigCache;

    @Inject
    public McpRegistry(LibertyLogsContextCollector libertyLogsContextCollector,
                       ExternalConfigCache externalConfigCache) {
        this.libertyLogsContextCollector = libertyLogsContextCollector;
        this.externalConfigCache = externalConfigCache;
    }

    public void init(McpSettings settings) {
        clients.clear();
        settings.mcpServers().forEach((name, config) -> clients.put(name, new McpClient(name, config)));
        initializationError = null;
        initialized = true;
        refreshInstanaHeaders();
    }

    public void markInitFailed(String reason) {
        clients.clear();
        initializationError = reason;
        initialized = false;
    }

    public Optional<McpClient> getClient(String name) {
        return Optional.ofNullable(clients.get(name));
    }

    public Collection<McpClient> allClients() {
        return List.copyOf(clients.values());
    }

    public boolean isInitialized() {
        return initialized;
    }

    public Optional<String> getInitializationError() {
        return Optional.ofNullable(initializationError);
    }

    /**
     * Collects diagnostic context from every configured, healthy MCP server for this alert.
     * Servers are collected in parallel via virtual threads; each server's own tool calls stay
     * sequential.
     */
    public DiagnosticContext collectContext(Alert alert) {
        Alert.WorkloadInfo workload = alert.getWorkloadInfo();
        DiagnosticContext.Builder builder = DiagnosticContext.builder()
                .podName(workload.podName())
                .containerName(workload.containerName())
                .namespace(workload.namespace())
                .workloadName(workload.containerName());

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (McpClient client : allClients()) {
                executor.submit(() -> collectFromServer(client, alert, builder));
            }
        }

        return builder.build();
    }

    private void collectFromServer(McpClient client, Alert alert, DiagnosticContext.Builder builder) {
        ComponentHealthDto health = client.checkHealth();
        if (!AppConstants.HealthStatus.UP.getValue().equals(health.getStatus())) {
            return;
        }
        switch (client.getServerName()) {
            case "filesystem" -> builder.put("LIBERTY_LOGS",
                    libertyLogsContextCollector.collectLibertyLogs(alert.getAlertId(), alert.getAlertTimestamp()));
            case "async-profiler" -> AsyncProfilerContextCollector.collect(client, alert, builder);
            case "cryostat" -> CryostatContextCollector.collect(client, alert, builder);
            case McpConstants.Instana.SERVER_NAME -> InstanaContextCollector.collect(client, alert, builder);
            default -> {
                for (McpSettings.ToolConfig tool : client.getConfig().tools()) {
                    processTool(client, tool, alert, Map.of(), builder);
                }
            }
        }
    }

    /**
     * Resolves {@code ${token}} placeholders against the alert's workload info, this server's
     * metadata, and any extra chained tokens. Handles nested maps and lists recursively.
     * Top-level entries whose resolved value is a string containing an unresolved
     * {@code ${alertTimestamp}} placeholder are omitted so that optional time-based arguments
     * fall back to the MCP server's documented defaults.
     */
    public static Map<String, Object> resolveArguments(Map<String, Object> template, Alert alert,
            McpSettings.ServerConfig config, Map<String, String> extraTokens) {
        Alert.WorkloadInfo workload = alert.getWorkloadInfo();
        java.time.Instant alertTs = alert.getAlertTimestamp();
        return resolveMap(template, workload, alertTs, extraTokens, config);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resolveMap(Map<String, Object> map, Alert.WorkloadInfo workload,
            java.time.Instant alertTs, Map<String, String> extraTokens,
            McpSettings.ServerConfig config) {
        Map<String, Object> resolved = new HashMap<>();
        map.forEach((key, value) -> {
            Object resolvedValue = resolveValue(value, workload, alertTs, extraTokens, config);
            if (resolvedValue instanceof String strValue && strValue.contains("${alertTimestamp")) {
                return;
            }
            resolved.put(key, resolvedValue);
        });
        return resolved;
    }

    @SuppressWarnings("unchecked")
    private static Object resolveValue(Object value, Alert.WorkloadInfo workload,
            java.time.Instant alertTs, Map<String, String> extraTokens,
            McpSettings.ServerConfig config) {
        if (value instanceof String strValue) {
            return resolveString(strValue, workload, alertTs, extraTokens, config);
        } else if (value instanceof Map<?, ?> mapValue) {
            return resolveMap((Map<String, Object>) mapValue, workload, alertTs, extraTokens, config);
        } else if (value instanceof List<?> listValue) {
            return listValue.stream()
                    .map(e -> resolveValue(e, workload, alertTs, extraTokens, config))
                    .toList();
        }
        return value;
    }

    private static String resolveString(String value, Alert.WorkloadInfo workload,
            java.time.Instant alertTs, Map<String, String> extraTokens,
            McpSettings.ServerConfig config) {
        String result = value
                .replace("${podName}", orEmpty(workload.podName()))
                .replace("${namespace}", orEmpty(workload.namespace()))
                .replace("${containerName}", orEmpty(workload.containerName()));
        if (alertTs != null) {
            result = result
                    .replace("${alertTimestamp}", alertTs.toString())
                    .replace("${alertTimestampMinus15m}", alertTs.minusSeconds(900).toString());
        }
        for (Map.Entry<String, String> extra : extraTokens.entrySet()) {
            result = result.replace("${" + extra.getKey() + "}", orEmpty(extra.getValue()));
        }
        for (Map.Entry<String, Object> meta : config.metadata().entrySet()) {
            result = result.replace("${metadata." + meta.getKey() + "}", String.valueOf(meta.getValue()));
        }
        return result;
    }

    /**
     * Resolves args, calls the tool, formats the result, and stores it in {@code builder} by
     * {@code contextKey}. Never throws — a failed tool is simply skipped.
     *
     * @return the raw (unformatted) tool response, or {@code null} on failure
     */
    public static String processTool(McpClient client, McpSettings.ToolConfig tool, Alert alert,
            Map<String, String> extraTokens, DiagnosticContext.Builder builder) {
        try {
            Map<String, Object> args = resolveArguments(tool.arguments(), alert, client.getConfig(), extraTokens);
            String raw = client.callTool(tool.name(), args);
            String formatted = McpResponseFormatter.format(client.getServerName(), tool.name(), raw);
            builder.put(tool.contextKey(), formatted);
            return raw;
        } catch (Exception e) {
            // one bad tool must not affect the rest of this server's (or any other server's) collection
            log.warn("MCP tool processing failed")
                    .field("server", client.getServerName())
                    .field("tool", tool.name())
                    .field("error", e.getMessage())
                    .log();
            return null;
        }
    }

    /**
     * Resolves Instana credentials from ExternalConfigCache and sets them as runtime headers
     * on the Instana MCP client. Called at init and on every config cache refresh.
     */
    public void refreshInstanaHeaders() {
        if (externalConfigCache == null) {
            return;
        }
        getClient(McpConstants.Instana.SERVER_NAME).ifPresent(client -> {
            List<ExternalConfig> configs = externalConfigCache.getByCategory(PlatformCategory.OBSERVABILITY);
            configs.stream()
                    .filter(c -> McpConstants.Instana.PLATFORM.equals(c.getPlatform())
                            && McpConstants.Instana.CONFIG_NAME.equals(c.getName())
                            && c.isActive())
                    .findFirst()
                    .ifPresentOrElse(
                            config -> {
                                String token = EncryptionUtils.decrypt(config.getAuthConfig().token());
                                client.setRuntimeHeaders(Map.of(
                                        McpConstants.Instana.HEADER_API_TOKEN, token,
                                        McpConstants.Instana.HEADER_BASE_URL, config.getUrl()));
                                log.info("Instana MCP headers resolved from external config").log();
                            },
                            () -> log.info("No active Instana external config found — headers not set").log());
        });
    }

    private static String orEmpty(String value) {
        return value != null ? value : "";
    }
}
