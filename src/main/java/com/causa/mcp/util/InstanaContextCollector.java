package com.causa.mcp.util;

import com.causa.common.constants.McpConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.core.domain.Alert;
import com.causa.core.domain.DiagnosticContext;
import com.causa.mcp.McpClient;
import com.causa.mcp.McpRegistry;
import com.causa.mcp.config.McpSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;

/**
 * Handles Instana tools that require chaining or post-processing — all other
 * tools are processed automatically from {@code mcp.json} via
 * {@link McpRegistry#processTool}.
 *
 * <p>Special handling:
 * <ul>
 *   <li>{@code get_kubernetes_info_events} → client-side namespace filtering</li>
 *   <li>{@code get_snapshots} → extract {@code snapshotId} → {@code get_snapshot}</li>
 *   <li>{@code get_all} (SLO configurations) → extract {@code sloId} → {@code get} (SLO report)</li>
 * </ul>
 *
 * @since 0.0.1
 */
public final class InstanaContextCollector {

    private static final CausaLogger log = CausaLogger.getLogger(InstanaContextCollector.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private InstanaContextCollector() {}

    public static void collect(McpClient client, Alert alert, DiagnosticContext.Builder builder) {
        String snapshotsResult = null;
        McpSettings.ToolConfig snapshotDetailTool = null;

        String sloGetAllResult = null;
        McpSettings.ToolConfig sloReportTool = null;

        for (McpSettings.ToolConfig tool : client.getConfig().tools()) {
            String operation = getOperation(tool);

            if (McpConstants.Instana.OP_GET_SNAPSHOT.equals(operation)) {
                snapshotDetailTool = tool;
                continue;
            }

            if (McpConstants.Instana.OP_GET.equals(operation)
                    && McpConstants.Tools.INSTANA_MANAGE_SLO.equals(tool.name())) {
                sloReportTool = tool;
                continue;
            }

            if (McpConstants.Instana.OP_GET_KUBERNETES_INFO_EVENTS.equals(operation)) {
                processK8sEvents(client, tool, alert, builder);
                continue;
            }

            String raw = McpRegistry.processTool(client, tool, alert, Map.of(), builder);

            if (McpConstants.Instana.OP_GET_SNAPSHOTS.equals(operation)) {
                snapshotsResult = raw;
            }
            if (McpConstants.Instana.OP_GET_ALL.equals(operation)
                    && McpConstants.Tools.INSTANA_MANAGE_SLO.equals(tool.name())) {
                sloGetAllResult = raw;
            }
        }

        if (snapshotDetailTool != null && snapshotsResult != null) {
            String snapshotId = extractSnapshotId(snapshotsResult);
            if (snapshotId != null) {
                McpRegistry.processTool(client, snapshotDetailTool, alert,
                        Map.of("snapshotId", snapshotId), builder);
            }
        }

        if (sloReportTool != null && sloGetAllResult != null) {
            String sloId = extractSloId(sloGetAllResult);
            if (sloId != null) {
                McpRegistry.processTool(client, sloReportTool, alert,
                        Map.of("sloId", sloId), builder);
            }
        }
    }

    /**
     * Calls the K8s events tool, then filters the response to keep only events
     * whose {@code entityLabel} contains the alert's namespace. The Instana
     * {@code get_kubernetes_info_events} API returns cluster-wide events with no
     * server-side namespace filter, so we scope them client-side.
     */
    private static void processK8sEvents(McpClient client, McpSettings.ToolConfig tool,
            Alert alert, DiagnosticContext.Builder builder) {
        try {
            Map<String, Object> args = McpRegistry.resolveArguments(
                    tool.arguments(), alert, client.getConfig(), Map.of());
            String raw = client.callTool(tool.name(), args);
            if (raw == null) {
                return;
            }

            String namespace = alert.getWorkloadInfo().namespace();
            String filtered = (namespace != null && !namespace.isEmpty())
                    ? filterEventsByNamespace(raw, namespace)
                    : raw;

            String formatted = McpResponseFormatter.format(
                    client.getServerName(), tool.name(), filtered);
            builder.put(tool.contextKey(), formatted);
        } catch (Exception e) {
            log.warn("MCP tool processing failed")
                    .field("server", client.getServerName())
                    .field("tool", tool.name())
                    .field("error", e.getMessage())
                    .log();
        }
    }

    static String filterEventsByNamespace(String eventsJson, String namespace) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(eventsJson);
            if (!root.has("results")) {
                return eventsJson;
            }
            ObjectNode results = (ObjectNode) root.path("results");

            filterArray(results, "events", namespace);
            filterProblemAnalyses(results, namespace);

            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return eventsJson;
        }
    }

    private static void filterArray(ObjectNode results, String field, String namespace) {
        JsonNode arr = results.path(field);
        if (!arr.isArray()) {
            return;
        }
        ArrayNode filtered = OBJECT_MAPPER.createArrayNode();
        for (JsonNode event : arr) {
            String label = event.path("entityLabel").asText("");
            if (label.startsWith(namespace + "/")) {
                filtered.add(event);
            }
        }
        results.set(field, filtered);
    }

    private static void filterProblemAnalyses(ObjectNode results, String namespace) {
        JsonNode analyses = results.path("problem_analyses");
        if (!analyses.isArray()) {
            return;
        }
        ArrayNode filtered = OBJECT_MAPPER.createArrayNode();
        for (JsonNode analysis : analyses) {
            ArrayNode sampleEvents = (ArrayNode) analysis.path("sample_events");
            if (!sampleEvents.isArray()) {
                continue;
            }
            ArrayNode filteredSamples = OBJECT_MAPPER.createArrayNode();
            for (JsonNode sample : sampleEvents) {
                String label = sample.path("entityLabel").asText("");
                if (label.startsWith(namespace + "/")) {
                    filteredSamples.add(sample);
                }
            }
            if (!filteredSamples.isEmpty()) {
                ObjectNode copy = analysis.deepCopy();
                copy.set("sample_events", filteredSamples);
                copy.put("count", filteredSamples.size());
                filtered.add(copy);
            }
        }
        results.set("problem_analyses", filtered);
    }

    private static String getOperation(McpSettings.ToolConfig tool) {
        Object op = tool.arguments().get(McpConstants.Instana.OPERATION);
        return op instanceof String ? (String) op : null;
    }

    private static String extractSnapshotId(String snapshotsJson) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(snapshotsJson);
            JsonNode snapshots = root.path("results").path("snapshots");
            if (snapshots.isArray() && !snapshots.isEmpty()) {
                return snapshots.get(0).path("snapshotId").asText(null);
            }
        } catch (Exception e) {
            // fall through
        }
        return null;
    }

    private static String extractSloId(String sloGetAllJson) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(sloGetAllJson);
            JsonNode items = root.path("results").path("items");
            if (items.isArray() && !items.isEmpty()) {
                return items.get(0).path("id").asText(null);
            }
        } catch (Exception e) {
            // fall through
        }
        return null;
    }
}
