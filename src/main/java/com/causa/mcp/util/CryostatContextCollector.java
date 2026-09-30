package com.causa.mcp.util;

import com.causa.common.constants.ContextConstants;
import com.causa.common.constants.McpConstants;
import com.causa.core.domain.Alert;
import com.causa.core.domain.DiagnosticContext;
import com.causa.mcp.McpClient;
import com.causa.mcp.McpRegistry;
import com.causa.mcp.config.McpSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Calls Cryostat only after the alert's namespace and pod appear in {@code getDiscoveryTree}.
 * {@code getAnalysisReport} still receives namespace, pod name, and an ISO-8601 window; the tree
 * is a gate, not an argument source.
 *
 * @since 0.0.1
 */
public final class CryostatContextCollector {

    static final String DISCOVERY_CONTEXT_KEY = "CRYOSTAT_DISCOVERY";
    static final String ANALYSIS_CONTEXT_KEY = "CRYOSTAT_ANALYSIS";
    static final int DEFAULT_LOOKBACK_MINUTES = 15;
    static final String LOOKBACK_METADATA_KEY = "analysisLookbackMinutes";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private CryostatContextCollector() {}

    public static void collect(McpClient client, Alert alert, DiagnosticContext.Builder builder) {
        McpSettings.ToolConfig discoveryTool = findTool(client, McpConstants.Tools.CRYOSTAT_GET_DISCOVERY_TREE);
        if (discoveryTool == null) {
            return;
        }

        String namespace = alert.getWorkloadInfo().namespace();
        String podName = alert.getWorkloadInfo().podName();
        Map<String, String> discoveryArgs = McpRegistry.resolveArguments(
                discoveryTool.arguments(), alert, client.getConfig(), Map.of());
        String discoveryRaw = client.callTool(discoveryTool.name(), discoveryArgs);
        String discoveryKey = contextKey(discoveryTool, DISCOVERY_CONTEXT_KEY);

        Match match = findMatch(discoveryRaw, namespace, podName);
        if (match == null) {
            builder.put(discoveryKey, notFoundNote(namespace, podName));
            return;
        }
        builder.put(discoveryKey, match.toJson());

        McpSettings.ToolConfig reportTool = findTool(client, McpConstants.Tools.CRYOSTAT_GET_ANALYSIS_REPORT);
        if (reportTool == null) {
            return;
        }

        Instant to = alert.getAlertTimestamp() != null ? alert.getAlertTimestamp() : Instant.now();
        Instant from = to.minus(lookbackMinutes(client.getConfig()), ChronoUnit.MINUTES);
        Map<String, String> extraTokens = Map.of(
                "fromTimestamp", from.toString(),
                "toTimestamp", to.toString());
        Map<String, String> reportArgs = McpRegistry.resolveArguments(
                reportTool.arguments(), alert, client.getConfig(), extraTokens);
        String reportRaw = client.callTool(reportTool.name(), reportArgs);
        String analysisKey = contextKey(reportTool, ANALYSIS_CONTEXT_KEY);
        if (reportRaw == null || reportRaw.isBlank()) {
            builder.put(analysisKey, ContextConstants.NOT_AVAILABLE);
            return;
        }
        builder.put(analysisKey, compactReport(reportRaw));
    }

    static Match findMatch(String discoveryJson, String namespace, String podName) {
        if (discoveryJson == null || discoveryJson.isBlank()
                || namespace == null || namespace.isBlank()
                || podName == null || podName.isBlank()) {
            return null;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(discoveryJson);
            return search(root, namespace, podName, false, null, null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Keeps rules whose {@code score} is greater than zero, highest score first.
     * Rules with score {@code 0} or {@code -1} are counted and dropped.
     */
    static String compactReport(String reportJson) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(reportJson);
            if (root == null || !root.isObject()) {
                return reportJson;
            }
            List<Rule> kept = new ArrayList<>();
            int omitted = 0;
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                JsonNode rule = field.getValue();
                JsonNode scoreNode = rule.path("score");
                if (!rule.isObject() || !scoreNode.isNumber() || scoreNode.asDouble() <= 0.0d) {
                    omitted++;
                    continue;
                }
                JsonNode evaluation = rule.path("evaluation");
                kept.add(new Rule(
                        field.getKey(),
                        textOrNull(rule, "name"),
                        textOrNull(rule, "topic"),
                        scoreNode.asDouble(),
                        textOrNull(evaluation, "summary"),
                        textOrNull(evaluation, "explanation"),
                        textOrNull(evaluation, "solution"),
                        evaluation.path("suggestions")));
            }
            kept.sort(Comparator.comparingDouble(Rule::score).reversed().thenComparing(Rule::id));

            ObjectNode out = OBJECT_MAPPER.createObjectNode();
            out.put("omittedRuleCount", omitted);
            ArrayNode rules = out.putArray("rules");
            for (Rule rule : kept) {
                ObjectNode node = rules.addObject();
                node.put("id", rule.id());
                putText(node, "name", rule.name());
                putText(node, "topic", rule.topic());
                node.put("score", rule.score());
                putText(node, "summary", rule.summary());
                putText(node, "explanation", rule.explanation());
                putText(node, "solution", rule.solution());
                if (rule.suggestions() != null && rule.suggestions().isArray()) {
                    node.set("suggestions", rule.suggestions().deepCopy());
                } else {
                    node.putArray("suggestions");
                }
            }
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(out);
        } catch (Exception e) {
            return reportJson;
        }
    }

    private static Match search(JsonNode node, String namespace, String podName,
                                boolean inNamespace, String deployment, String replicaSet) {
        if (node == null || !node.isObject()) {
            return null;
        }
        String type = node.path("nodeType").asText("");
        String name = node.path("name").asText("");
        boolean nextInNamespace = inNamespace;
        String nextDeployment = deployment;
        String nextReplicaSet = replicaSet;

        if ("Namespace".equals(type)) {
            if (!namespace.equals(name)) {
                return null;
            }
            nextInNamespace = true;
        } else if (nextInNamespace && "Deployment".equals(type)) {
            nextDeployment = name;
        } else if (nextInNamespace && "ReplicaSet".equals(type)) {
            nextReplicaSet = name;
        } else if (nextInNamespace && "Pod".equals(type) && podMatches(node, podName)) {
            return new Match(namespace, nextDeployment, nextReplicaSet,
                    name.isBlank() ? podName : name, firstTarget(node));
        }

        JsonNode children = node.get("children");
        if (children != null && children.isArray()) {
            for (JsonNode child : children) {
                Match found = search(child, namespace, podName, nextInNamespace, nextDeployment, nextReplicaSet);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean podMatches(JsonNode podNode, String podName) {
        if (podName.equals(podNode.path("name").asText(null))) {
            return true;
        }
        return targetMatches(podNode, podName);
    }

    private static boolean targetMatches(JsonNode node, String podName) {
        JsonNode target = node.get("target");
        if (target != null && target.isObject()) {
            if (podName.equals(target.path("alias").asText(null))) {
                return true;
            }
            if (podName.equals(cryostatAnnotation(target, "HOST"))) {
                return true;
            }
        }
        JsonNode children = node.get("children");
        if (children != null && children.isArray()) {
            for (JsonNode child : children) {
                if (targetMatches(child, podName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static TargetInfo firstTarget(JsonNode node) {
        JsonNode target = node.get("target");
        if (target != null && target.isObject()
                && (target.hasNonNull("jvmId") || target.hasNonNull("alias"))) {
            return new TargetInfo(
                    textOrNull(target, "jvmId"),
                    textOrNull(target, "alias"),
                    cryostatAnnotation(target, "JAVA_MAIN"));
        }
        JsonNode children = node.get("children");
        if (children != null && children.isArray()) {
            for (JsonNode child : children) {
                TargetInfo found = firstTarget(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String cryostatAnnotation(JsonNode target, String key) {
        JsonNode entries = target.path("annotations").path("cryostat");
        if (!entries.isArray()) {
            return null;
        }
        for (JsonNode entry : entries) {
            if (key.equals(entry.path("key").asText(null))) {
                return textOrNull(entry, "value");
            }
        }
        return null;
    }

    private static int lookbackMinutes(McpSettings.ServerConfig config) {
        Object raw = config.metadata().get(LOOKBACK_METADATA_KEY);
        int minutes;
        if (raw instanceof Number number) {
            minutes = number.intValue();
        } else if (raw != null) {
            try {
                minutes = Integer.parseInt(raw.toString().trim());
            } catch (NumberFormatException e) {
                return DEFAULT_LOOKBACK_MINUTES;
            }
        } else {
            return DEFAULT_LOOKBACK_MINUTES;
        }
        return minutes > 0 ? minutes : DEFAULT_LOOKBACK_MINUTES;
    }

    private static McpSettings.ToolConfig findTool(McpClient client, String toolName) {
        return client.getConfig().tools().stream()
                .filter(tool -> toolName.equals(tool.name()))
                .findFirst()
                .orElse(null);
    }

    private static String contextKey(McpSettings.ToolConfig tool, String fallback) {
        return tool.contextKey() != null && !tool.contextKey().isBlank() ? tool.contextKey() : fallback;
    }

    private static String notFoundNote(String namespace, String podName) {
        return "Pod " + (podName != null ? podName : "")
                + " in namespace " + (namespace != null ? namespace : "")
                + " was not found in the Cryostat discovery tree.";
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    private static void putText(ObjectNode node, String field, String value) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    record Match(String namespace, String deployment, String replicaSet, String pod, TargetInfo target) {
        String toJson() {
            ObjectNode node = OBJECT_MAPPER.createObjectNode();
            node.put("namespace", namespace);
            putText(node, "deployment", deployment);
            putText(node, "replicaSet", replicaSet);
            node.put("pod", pod);
            if (target != null) {
                putText(node, "jvmId", target.jvmId());
                putText(node, "alias", target.alias());
                putText(node, "javaMain", target.javaMain());
            }
            try {
                return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
            } catch (Exception e) {
                return node.toString();
            }
        }
    }

    private record TargetInfo(String jvmId, String alias, String javaMain) {}

    private record Rule(
            String id,
            String name,
            String topic,
            double score,
            String summary,
            String explanation,
            String solution,
            JsonNode suggestions) {}
}
