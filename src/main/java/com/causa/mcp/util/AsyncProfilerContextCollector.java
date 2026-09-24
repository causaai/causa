package com.causa.mcp.util;

import com.causa.core.domain.Alert;
import com.causa.core.domain.DiagnosticContext;
import com.causa.mcp.McpClient;
import com.causa.mcp.McpRegistry;
import com.causa.mcp.config.McpSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Handles async-profiler's one chaining requirement: {@code get_recording}/{@code get_flame_graph}
 * need a {@code recording_id} that only exists after calling {@code list_profiled_pods} first.
 *
 * @since 0.0.1
 */
public final class AsyncProfilerContextCollector {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private AsyncProfilerContextCollector() {}

    public static void collect(McpClient client, Alert alert, DiagnosticContext.Builder builder) {
        McpSettings.ToolConfig listTool = findTool(client, "list_profiled_pods");
        if (listTool == null) {
            return;
        }

        String podListJson = client.callTool("list_profiled_pods", Map.of());
        builder.put(listTool.contextKey(), McpResponseFormatter.format(client.getServerName(), "list_profiled_pods", podListJson));

        String recordingId = extractLatestRecordingId(podListJson, alert.getWorkloadInfo().podName());
        Map<String, String> extraTokens = recordingId != null ? Map.of("recordingId", recordingId) : Map.of();

        for (McpSettings.ToolConfig tool : client.getConfig().tools()) {
            if ("list_profiled_pods".equals(tool.name())) {
                continue;
            }
            McpRegistry.processTool(client, tool, alert, extraTokens, builder);
        }
    }

    private static McpSettings.ToolConfig findTool(McpClient client, String toolName) {
        return client.getConfig().tools().stream()
                .filter(t -> toolName.equals(t.name()))
                .findFirst()
                .orElse(null);
    }

    /** Extracts latestRecordingId from list_profiled_pods' JSON array, matching on podName. */
    private static String extractLatestRecordingId(String podListJson, String podName) {
        if (podListJson == null || podListJson.isBlank()) {
            return null;
        }
        try {
            JsonNode arr = OBJECT_MAPPER.readTree(podListJson);
            if (arr.isArray()) {
                for (JsonNode pod : arr) {
                    if (podName != null && podName.equals(pod.path("podName").asText(null))) {
                        String id = pod.path("latestRecordingId").asText(null);
                        return (id != null && !id.isBlank()) ? id : null;
                    }
                }
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }
}
