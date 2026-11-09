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
import java.util.regex.Pattern;

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

    /** {@code manage_infrastructure} operations that must be scoped to the alert pod. */
    private static final Set<String> POD_SCOPED_INFRA_OPERATIONS = Set.of(
            McpConstants.Instana.OP_GET_SNAPSHOTS, McpConstants.Instana.OP_GET_SNAPSHOT,
            McpConstants.Instana.OP_GET_ENTITIES, McpConstants.Instana.OP_FIND_ACTIVE);

    /** Kubernetes pod name (DNS-1123 subdomain) and namespace (DNS-1123 label) formats. */
    private static final Pattern POD_NAME_FORMAT = Pattern.compile("[a-z0-9]([-a-z0-9.]{0,251}[a-z0-9])?");
    private static final Pattern NAMESPACE_FORMAT = Pattern.compile("[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?");

    public static void collect(McpClient client, Alert alert, DiagnosticContext.Builder builder) {
        Alert.WorkloadInfo workload = alert.getWorkloadInfo();
        // Pod and namespace are interpolated into Instana queries, so anything that is not a valid
        // Kubernetes name (e.g. containing quotes or operators) is treated as "pod unknown".
        boolean podKnown = workload != null
                && workload.podName() != null && POD_NAME_FORMAT.matcher(workload.podName()).matches()
                && workload.namespace() != null && NAMESPACE_FORMAT.matcher(workload.namespace()).matches();
        if (!podKnown) {
            log.warn("Alert has no valid pod/namespace — skipping pod-scoped Instana tools").log();
        }

        String snapshotsResult = null;
        McpSettings.ToolConfig snapshotDetailTool = null;

        String sloGetAllResult = null;
        McpSettings.ToolConfig sloReportTool = null;

        for (McpSettings.ToolConfig tool : client.getConfig().tools()) {
            String operation = getOperation(tool);

            if (!podKnown && isPodScoped(tool, operation)) {
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

    /** Decided from the tool identity, not the configurable {@code contextKey}. */
    private static boolean isPodScoped(McpSettings.ToolConfig tool, String operation) {
        if (operation == null) {
            return false;
        }
        return McpConstants.Instana.OP_GET_KUBERNETES_INFO_EVENTS.equals(operation)
                || (McpConstants.Tools.INSTANA_MANAGE_INFRASTRUCTURE.equals(tool.name())
                        && POD_SCOPED_INFRA_OPERATIONS.contains(operation));
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

    /** A config applies only when its filter is empty or definitely matches this pod. */
    private static boolean appliesToPod(JsonNode expression, String podName, String namespace) {
        if (expression.isMissingNode() || expression.isNull()) {
            return true;
        }
        return Boolean.TRUE.equals(evaluate(expression, podName, namespace));
    }

    /**
     * Evaluates a tag filter expression against the alert pod. Returns {@code TRUE}/{@code FALSE}
     * when decidable from the pod name and namespace, or {@code null} (unknown) when the filter
     * depends on a tag or operator we cannot evaluate. Unknown never counts as a match.
     */
    private static Boolean evaluate(JsonNode node, String podName, String namespace) {
        if ("EXPRESSION".equals(node.path("type").asText())) {
            boolean isAnd = !"OR".equals(node.path("logicalOperator").asText("AND"));
            boolean unknown = false;
            for (JsonNode element : node.path("elements")) {
                Boolean result = evaluate(element, podName, namespace);
                if (result == null) {
                    unknown = true;
                } else if (result != isAnd) {
                    return result; // AND short-circuits on false, OR on true
                }
            }
            return unknown ? null : isAnd; // empty AND = true, empty OR = false
        }
        String actual;
        switch (node.path("name").asText("")) {
            case "kubernetes.pod.name" -> actual = podName;
            case "kubernetes.namespace.name" -> actual = namespace;
            default -> {
                return null;
            }
        }
        String expected = node.path("value").asText(node.path("stringValue").asText(""));
        return switch (node.path("operator").asText("")) {
            case "EQUALS" -> actual.equals(expected);
            case "NOT_EQUAL" -> !actual.equals(expected);
            case "CONTAINS" -> actual.contains(expected);
            case "NOT_CONTAIN" -> !actual.contains(expected);
            case "STARTS_WITH" -> actual.startsWith(expected);
            case "ENDS_WITH" -> actual.endsWith(expected);
            default -> null;
        };
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
            warnIfEventsTruncated(raw, alert);

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

    /**
     * Instana applies {@code max_events} before we filter, so the alert pod's events can be
     * missing when the cluster produced more events than the cap. Make that visible.
     */
    private static void warnIfEventsTruncated(String eventsJson, Alert alert) {
        try {
            JsonNode results = OBJECT_MAPPER.readTree(eventsJson).path("results");
            int total = results.path("events_count").asInt(0);
            int analyzed = results.path("events_analyzed").asInt(0);
            if (analyzed < total) {
                log.warn("Instana K8s events were capped before pod filtering — pod events may be incomplete")
                        .field("eventsCount", total)
                        .field("eventsAnalyzed", analyzed)
                        .field("pod", alert.getWorkloadInfo().podName())
                        .log();
            }
        } catch (Exception e) {
            // best-effort diagnostic only
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
