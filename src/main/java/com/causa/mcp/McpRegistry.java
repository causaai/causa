package com.causa.mcp;

import com.causa.api.dto.ComponentHealthDto;
import com.causa.common.constants.AppConstants;
import com.causa.core.domain.Alert;
import com.causa.core.domain.DiagnosticContext;
import com.causa.mcp.config.McpSettings;
import com.causa.mcp.util.AsyncProfilerContextCollector;
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

    @Inject
    public McpRegistry(LibertyLogsContextCollector libertyLogsContextCollector) {
        this.libertyLogsContextCollector = libertyLogsContextCollector;
    }

    public void init(McpSettings settings) {
        clients.clear();
        settings.mcpServers().forEach((name, config) -> clients.put(name, new McpClient(name, config)));
        initializationError = null;
        initialized = true;
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
            default -> {
                for (McpSettings.ToolConfig tool : client.getConfig().tools()) {
                    processTool(client, tool, alert, Map.of(), builder);
                }
            }
        }
    }

    /**
     * Resolves {@code ${token}} placeholders against the alert's workload info, this server's
     * metadata, and any extra chained tokens. Unresolvable tokens become empty strings.
     */
    public static Map<String, String> resolveArguments(Map<String, String> template, Alert alert,
            McpSettings.ServerConfig config, Map<String, String> extraTokens) {
        Alert.WorkloadInfo workload = alert.getWorkloadInfo();
        Map<String, String> resolved = new HashMap<>();
        template.forEach((key, value) -> {
            String result = value
                    .replace("${podName}", orEmpty(workload.podName()))
                    .replace("${namespace}", orEmpty(workload.namespace()))
                    .replace("${containerName}", orEmpty(workload.containerName()));
            for (Map.Entry<String, String> extra : extraTokens.entrySet()) {
                result = result.replace("${" + extra.getKey() + "}", orEmpty(extra.getValue()));
            }
            for (Map.Entry<String, Object> meta : config.metadata().entrySet()) {
                result = result.replace("${metadata." + meta.getKey() + "}", String.valueOf(meta.getValue()));
            }
            resolved.put(key, result);
        });
        return resolved;
    }

    /**
     * Resolves args, calls the tool, formats the result, and stores it in {@code builder} by
     * {@code contextKey}. Never throws — a failed tool is simply skipped.
     */
    public static void processTool(McpClient client, McpSettings.ToolConfig tool, Alert alert,
            Map<String, String> extraTokens, DiagnosticContext.Builder builder) {
        try {
            Map<String, String> args = resolveArguments(tool.arguments(), alert, client.getConfig(), extraTokens);
            String raw = client.callTool(tool.name(), args);
            String formatted = McpResponseFormatter.format(client.getServerName(), tool.name(), raw);
            builder.put(tool.contextKey(), formatted);
        } catch (Exception e) {
            // one bad tool must not affect the rest of this server's (or any other server's) collection
            log.warn("MCP tool processing failed")
                    .field("server", client.getServerName())
                    .field("tool", tool.name())
                    .field("error", e.getMessage())
                    .log();
        }
    }

    private static String orEmpty(String value) {
        return value != null ? value : "";
    }
}
