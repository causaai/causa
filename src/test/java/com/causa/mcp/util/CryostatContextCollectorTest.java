package com.causa.mcp.util;

import com.causa.common.constants.AlertConstants.AlertSeverity;
import com.causa.common.constants.AlertConstants.AlertStatus;
import com.causa.common.constants.ContextConstants;
import com.causa.common.constants.McpConstants;
import com.causa.core.domain.Alert;
import com.causa.core.domain.DiagnosticContext;
import com.causa.mcp.McpClient;
import com.causa.mcp.config.McpSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CryostatContextCollector Tests")
class CryostatContextCollectorTest {

    private static final String NAMESPACE = "openshift-tuning";
    private static final String POD = "auth-cache-5bb75466d8-zbw2t";
    private static final Instant ALERT_TIME = Instant.parse("2026-09-15T19:54:16Z");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private McpClient client;

    private String discoveryJson;
    private String reportJson;

    @BeforeEach
    void setUp() throws IOException {
        discoveryJson = resource("getDiscoveryTree.json");
        reportJson = resource("getAnalysisReport.json");
        when(client.getConfig()).thenReturn(serverConfig());
    }

    @Test
    @DisplayName("matching namespace and pod calls the report and keeps only score > 0 rules")
    void matchingPodCompactsReport() throws Exception {
        when(client.callTool(eq(McpConstants.Tools.CRYOSTAT_GET_DISCOVERY_TREE), anyMap()))
                .thenReturn(discoveryJson);
        when(client.callTool(eq(McpConstants.Tools.CRYOSTAT_GET_ANALYSIS_REPORT), anyMap()))
                .thenReturn(reportJson);

        DiagnosticContext context = collect(NAMESPACE, POD);

        String discovery = context.get(CryostatContextCollector.DISCOVERY_CONTEXT_KEY);
        assertThat(discovery).contains("\"namespace\" : \"" + NAMESPACE + "\"");
        assertThat(discovery).contains("\"deployment\" : \"auth-cache\"");
        assertThat(discovery).contains("\"replicaSet\" : \"auth-cache-5bb75466d8\"");
        assertThat(discovery).contains("\"pod\" : \"" + POD + "\"");
        assertThat(discovery).contains("E00HeZHT1_HtUdcJ7LDJxp7DB0qb-bwdoBqsoFpMKoA=");
        assertThat(discovery).contains("quarkus-app/quarkus-run.jar");
        assertThat(discovery).doesNotContain("k8s.ovn.org/pod-networks");

        JsonNode analysis = MAPPER.readTree(context.get(CryostatContextCollector.ANALYSIS_CONTEXT_KEY));
        assertThat(analysis.path("omittedRuleCount").asInt()).isPositive();
        JsonNode rules = analysis.path("rules");
        assertThat(rules.isArray()).isTrue();
        assertThat(rules).isNotEmpty();
        double previous = Double.MAX_VALUE;
        boolean sawAllocations = false;
        for (JsonNode rule : rules) {
            assertThat(rule.path("score").asDouble()).isGreaterThan(0.0d);
            assertThat(rule.path("score").asDouble()).isLessThanOrEqualTo(previous);
            previous = rule.path("score").asDouble();
            assertThat(rule.path("id").asText()).isNotEqualTo("HeapInspectionGc");
            if ("Allocations.class".equals(rule.path("id").asText())) {
                sawAllocations = true;
                assertThat(rule.path("topic").asText()).isEqualTo("heap");
            }
        }
        assertThat(sawAllocations).isTrue();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
        verify(client).callTool(eq(McpConstants.Tools.CRYOSTAT_GET_ANALYSIS_REPORT), captor.capture());
        assertThat(captor.getValue())
                .containsEntry("namespace", NAMESPACE)
                .containsEntry("podName", POD)
                .containsEntry("fromTimestamp", "2026-09-15T19:39:16Z")
                .containsEntry("toTimestamp", "2026-09-15T19:54:16Z");
    }

    @Test
    @DisplayName("a different namespace does not call getAnalysisReport")
    void differentNamespaceSkipsReport() {
        when(client.callTool(eq(McpConstants.Tools.CRYOSTAT_GET_DISCOVERY_TREE), anyMap()))
                .thenReturn(discoveryJson);

        DiagnosticContext context = collect("other-namespace", POD);

        assertThat(context.get(CryostatContextCollector.DISCOVERY_CONTEXT_KEY))
                .contains("was not found in the Cryostat discovery tree");
        assertThat(context.get(CryostatContextCollector.ANALYSIS_CONTEXT_KEY)).isNull();
        verify(client, never()).callTool(eq(McpConstants.Tools.CRYOSTAT_GET_ANALYSIS_REPORT), anyMap());
    }

    @Test
    @DisplayName("a different pod does not call getAnalysisReport")
    void differentPodSkipsReport() {
        when(client.callTool(eq(McpConstants.Tools.CRYOSTAT_GET_DISCOVERY_TREE), anyMap()))
                .thenReturn(discoveryJson);

        DiagnosticContext context = collect(NAMESPACE, "some-other-pod");

        assertThat(context.get(CryostatContextCollector.DISCOVERY_CONTEXT_KEY))
                .contains("was not found in the Cryostat discovery tree");
        assertThat(context.get(CryostatContextCollector.ANALYSIS_CONTEXT_KEY)).isNull();
        verify(client, never()).callTool(eq(McpConstants.Tools.CRYOSTAT_GET_ANALYSIS_REPORT), anyMap());
    }

    @Test
    @DisplayName("a matched pod whose report call fails leaves analysis as no data")
    void reportFailureLeavesAnalysisEmpty() {
        when(client.callTool(eq(McpConstants.Tools.CRYOSTAT_GET_DISCOVERY_TREE), anyMap()))
                .thenReturn(discoveryJson);
        when(client.callTool(eq(McpConstants.Tools.CRYOSTAT_GET_ANALYSIS_REPORT), anyMap()))
                .thenReturn(null);

        DiagnosticContext context = collect(NAMESPACE, POD);

        assertThat(context.get(CryostatContextCollector.DISCOVERY_CONTEXT_KEY)).contains(POD);
        assertThat(context.get(CryostatContextCollector.ANALYSIS_CONTEXT_KEY))
                .isEqualTo(ContextConstants.NOT_AVAILABLE);
    }

    private DiagnosticContext collect(String namespace, String podName) {
        DiagnosticContext.Builder builder = DiagnosticContext.builder();
        CryostatContextCollector.collect(client, alert(namespace, podName), builder);
        return builder.build();
    }

    private static Alert alert(String namespace, String podName) {
        return Alert.builder()
                .alertId("alrt_cryostat000001")
                .alertName("HighMemory")
                .severity(AlertSeverity.CRITICAL)
                .status(AlertStatus.ACCEPTED)
                .workloadInfo(Alert.WorkloadInfo.of(podName, "auth-cache", namespace, "cluster-1", "Deployment"))
                .workloadName("auth-cache")
                .alertTimestamp(ALERT_TIME)
                .build();
    }

    private static McpSettings.ServerConfig serverConfig() {
        return new McpSettings.ServerConfig(
                "streamable-http",
                "http://cryostat-mcp:8000/mcp",
                Map.of(),
                true,
                new McpSettings.HealthCheckConfig("http://cryostat-mcp-api:8080/healthz", 15000),
                60000,
                Map.of(CryostatContextCollector.LOOKBACK_METADATA_KEY, 15),
                "Cryostat MCP",
                List.of(
                        new McpSettings.ToolConfig(
                                McpConstants.Tools.CRYOSTAT_GET_DISCOVERY_TREE,
                                CryostatContextCollector.DISCOVERY_CONTEXT_KEY,
                                "Discovery tree",
                                Map.of("namespace", "${namespace}", "mergeRealms", "true")),
                        new McpSettings.ToolConfig(
                                McpConstants.Tools.CRYOSTAT_GET_ANALYSIS_REPORT,
                                CryostatContextCollector.ANALYSIS_CONTEXT_KEY,
                                "Analysis report",
                                Map.of(
                                        "namespace", "${namespace}",
                                        "podName", "${podName}",
                                        "fromTimestamp", "${fromTimestamp}",
                                        "toTimestamp", "${toTimestamp}"))));
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = CryostatContextCollectorTest.class.getResourceAsStream("/cryostat/" + name)) {
            if (in == null) {
                throw new IOException("Missing test resource /cryostat/" + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
