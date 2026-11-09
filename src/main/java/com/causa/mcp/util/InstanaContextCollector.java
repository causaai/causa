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
import java.util.Set;

/**
 * Handles Instana tools that require chaining or post-processing — all other
 * tools are processed automatically from {@code mcp.json} via
 * {@link McpRegistry#processTool}.
 *
 * <p>Special handling:
 * <ul>
 *   <li>{@code get_kubernetes_info_events} → client-side filtering to the alert pod</li>
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

    /** Context keys that must be scoped to the alert pod; skipped when the pod identity is unknown. */
    private static final Set<String> POD_SCOPED_KEYS = Set.of(
            "INSTANA_K8S_EVENTS", "INSTANA_SNAPSHOTS", "INSTANA_SNAPSHOT_DETAIL", "INSTANA_JVM_METRICS",
            "INSTANA_INFRA_ALERTS");

    public static void collect(McpClient client, Alert alert, DiagnosticContext.Builder builder) {
        Alert.WorkloadInfo workload = alert.getWorkloadInfo();
        boolean podKnown = workload != null
                && workload.podName() != null && !workload.podName().isBlank()
                && workload.namespace() != null && !workload.namespace().isBlank();
        if (!podKnown) {
            log.warn("Alert has no pod/namespace — skipping pod-scoped Instana tools").log();
        }

        String snapshotsResult = null;
        McpSettings.ToolConfig snapshotDetailTool = null;

        String sloGetAllResult = null;
        McpSettings.ToolConfig sloReportTool = null;

        for (McpSettings.ToolConfig tool : client.getConfig().tools()) {
            String operation = getOperation(tool);

            if (!podKnown && POD_SCOPED_KEYS.contains(tool.contextKey())) {
                continue;
            }

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

            if (McpConstants.Instana.OP_FIND_ACTIVE.equals(operation)
                    && McpConstants.Tools.INSTANA_MANAGE_INFRASTRUCTURE.equals(tool.name())) {
                processInfraAlerts(client, tool, alert, builder);
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

        if (podKnown && snapshotDetailTool != null && snapshotsResult != null) {
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
     * Calls {@code find_active} for infra smart alert configs, then keeps only configs that
     * apply to the alert pod. The API has no server-side entity filter and returns
     * environment-wide configs.
     */
    private static void processInfraAlerts(McpClient client, McpSettings.ToolConfig tool,
            Alert alert, DiagnosticContext.Builder builder) {
        try {
            Map<String, Object> args = McpRegistry.resolveArguments(
                    tool.arguments(), alert, client.getConfig(), Map.of());
            String raw = client.callTool(tool.name(), args);
            if (raw == null) {
                return;
            }
            Alert.WorkloadInfo workload = alert.getWorkloadInfo();
            String filtered = filterAlertConfigsForPod(raw, workload.podName(), workload.namespace());
            if (filtered == null) {
                // Filter failure (already logged) — do not publish unfiltered, environment-wide configs.
                return;
            }
            builder.put(tool.contextKey(),
                    McpResponseFormatter.format(client.getServerName(), tool.name(), filtered));
        } catch (Exception e) {
            log.warn("MCP tool processing failed")
                    .field("server", client.getServerName())
                    .field("tool", tool.name())
                    .field("error", e.getMessage())
                    .log();
        }
    }

    /**
     * Keeps alert configs whose {@code tagFilterExpression} is empty (applies to every entity)
     * or contains a tag filter whose value equals the pod name or namespace.
     *
     * @return the filtered JSON (with an empty {@code configs} array when nothing applies), or
     *         {@code null} if the response could not be parsed or filtered — a failure that is
     *         logged here, as opposed to a valid empty result
     */
    static String filterAlertConfigsForPod(String alertsJson, String podName, String namespace) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(alertsJson);
            JsonNode resultsNode = root.path("results");
            JsonNode configs = resultsNode.path("configs");
            if (!resultsNode.isObject() || !configs.isArray()) {
                log.warn("Infra alert response has no results.configs array — dropping INSTANA_INFRA_ALERTS")
                        .field("pod", podName)
                        .field("namespace", namespace)
                        .log();
                return null;
            }
            ArrayNode kept = OBJECT_MAPPER.createArrayNode();
            for (JsonNode config : configs) {
                if (appliesToPod(config.path("tagFilterExpression"), podName, namespace)) {
                    kept.add(config);
                }
            }
            ObjectNode results = (ObjectNode) resultsNode;
            results.set("configs", kept);
            results.put("count", kept.size());
            results.put("total", kept.size());
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            log.warn("Infra alert filtering failed — dropping INSTANA_INFRA_ALERTS")
                    .field("pod", podName)
                    .field("namespace", namespace)
                    .field("error", e.getMessage())
                    .log();
            return null;
        }
    }

    private static boolean appliesToPod(JsonNode expression, String podName, String namespace) {
        boolean noFilter = expression.isMissingNode() || expression.isNull()
                || ("EXPRESSION".equals(expression.path("type").asText())
                        && expression.path("elements").isEmpty());
        return noFilter || hasPodTarget(expression, podName, namespace);
    }

    private static boolean hasPodTarget(JsonNode node, String podName, String namespace) {
        if ("EXPRESSION".equals(node.path("type").asText())) {
            for (JsonNode element : node.path("elements")) {
                if (hasPodTarget(element, podName, namespace)) {
                    return true;
                }
            }
            return false;
        }
        String value = node.path("value").asText(node.path("stringValue").asText(""));
        return value.equals(podName) || value.equals(namespace);
    }

    /**
     * Calls the K8s events tool, then filters the response to keep only events
     * whose {@code entityLabel} is exactly {@code namespace/podName}. The Instana
     * {@code get_kubernetes_info_events} API returns cluster-wide events with no
     * server-side pod/namespace filter (its {@code query} param is ignored), so we
     * scope them client-side. The tool's {@code max_events} must be large enough that
     * the cap does not drop the alert pod's events before this filter runs.
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

            Alert.WorkloadInfo workload = alert.getWorkloadInfo();
            String filtered = filterEventsByEntityLabel(raw, workload.namespace() + "/" + workload.podName());
            if (filtered == null) {
                return;
            }

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

    static String filterEventsByEntityLabel(String eventsJson, String entityLabel) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(eventsJson);
            JsonNode resultsNode = root.path("results");
            if (!resultsNode.isObject()) {
                return null;
            }
            ObjectNode results = (ObjectNode) resultsNode;

            filterArray(results, "events", entityLabel);
            filterProblemAnalyses(results, entityLabel);

            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return null;
        }
    }

    private static void filterArray(ObjectNode results, String field, String entityLabel) {
        JsonNode arr = results.path(field);
        if (!arr.isArray()) {
            return;
        }
        ArrayNode filtered = OBJECT_MAPPER.createArrayNode();
        for (JsonNode event : arr) {
            if (entityLabel.equals(event.path("entityLabel").asText(""))) {
                filtered.add(event);
            }
        }
        results.set(field, filtered);
    }

    private static void filterProblemAnalyses(ObjectNode results, String entityLabel) {
        JsonNode analyses = results.path("problem_analyses");
        if (!analyses.isArray()) {
            return;
        }
        ArrayNode filtered = OBJECT_MAPPER.createArrayNode();
        for (JsonNode analysis : analyses) {
            JsonNode sampleEventsNode = analysis.path("sample_events");
            if (!sampleEventsNode.isArray()) {
                continue;
            }
            ArrayNode filteredSamples = OBJECT_MAPPER.createArrayNode();
            for (JsonNode sample : sampleEventsNode) {
                if (entityLabel.equals(sample.path("entityLabel").asText(""))) {
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
