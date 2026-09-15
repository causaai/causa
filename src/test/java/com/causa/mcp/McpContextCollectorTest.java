package com.causa.mcp;

import java.util.Optional;
import com.causa.common.constants.AlertConstants.AlertSeverity;
import com.causa.common.constants.AlertConstants.AlertStatus;
import com.causa.config.McpConfig;
import com.causa.core.domain.Alert;
import com.causa.core.domain.DiagnosticContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("McpContextCollector Tests")
class McpContextCollectorTest {

    @Mock McpConfig mcpConfig;
    @Mock McpConfig.KubernetesConfig k8sConfig;
    @Mock McpConfig.KruizeConfig kruizeConfig;
    @Mock McpConfig.CryostatConfig cryostatConfig;
    @Mock McpConfig.QuarkusConfig quarkusConfig;
    @Mock McpConfig.AsyncProfilerConfig asyncProfilerConfig;
    @Mock McpConfig.JmxConfig jmxConfig;
    @Mock McpConfig.FilesystemConfig filesystemMcpConfig;
    @Mock LibertyLogsContextCollector libertyLogsContextCollector;

    static Alert buildAlert() {
        return Alert.builder()
            .alertId("alrt_test00000001234")
            .alertName("HighMemory")
            .severity(AlertSeverity.CRITICAL)
            .status(AlertStatus.ACCEPTED)
            .workloadInfo(Alert.WorkloadInfo.of("pod-1", "container-1", "production", "cluster-1", "Deployment"))
            .workloadName("container-1")
            .alertTimestamp(Instant.now())
            .build();
    }

    // -----------------------------------------------------------------------
    // cluster platform — all HTTP calls fail → context returned with nulls
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("collectContext() — cluster platform, HTTP unavailable")
    class ClusterPlatformTests {

        McpContextCollector collector;

        @BeforeEach
        void setUp() {
            // Use TEST-NET address (RFC 5737) — guaranteed unreachable, fails immediately
            when(mcpConfig.kubernetes()).thenReturn(k8sConfig);
            when(k8sConfig.endpoint()).thenReturn("http://192.0.2.1");
            when(k8sConfig.timeoutMs()).thenReturn(1);

            when(mcpConfig.kruize()).thenReturn(kruizeConfig);
            when(kruizeConfig.endpoint()).thenReturn("http://192.0.2.1");
            when(kruizeConfig.timeoutMs()).thenReturn(1);

            when(mcpConfig.cryostat()).thenReturn(cryostatConfig);
            when(cryostatConfig.endpoint()).thenReturn("http://192.0.2.1");
            when(cryostatConfig.timeoutMs()).thenReturn(1);
            when(cryostatConfig.maxRetries()).thenReturn(0);
            when(cryostatConfig.retryDelayMs()).thenReturn(1);

            when(mcpConfig.quarkus()).thenReturn(quarkusConfig);
            when(quarkusConfig.endpoint()).thenReturn(Optional.of("http://192.0.2.1"));
            when(quarkusConfig.timeoutMs()).thenReturn(1);

            when(mcpConfig.asyncProfiler()).thenReturn(asyncProfilerConfig);
            when(asyncProfilerConfig.endpoint()).thenReturn(Optional.of("http://192.0.2.1"));
            when(asyncProfilerConfig.timeoutMs()).thenReturn(1);

            collector = new McpContextCollector(mcpConfig, libertyLogsContextCollector, "cluster");
        }

        @Test
        @DisplayName("returns DiagnosticContext (not null) even when all HTTP fails")
        void returnsContext_notNull() {
            DiagnosticContext ctx = collector.collectContext(buildAlert());
            assertThat(ctx).isNotNull();
        }

        @Test
        @DisplayName("platform is set to cluster")
        void platformIsCluster() {
            DiagnosticContext ctx = collector.collectContext(buildAlert());
            assertThat(ctx.getPlatform()).isEqualTo(DiagnosticContext.PLATFORM_CLUSTER);
        }

        @Test
        @DisplayName("hasAnyContext is false when all HTTP calls fail")
        void hasNoContext_whenAllHttpFails() {
            DiagnosticContext ctx = collector.collectContext(buildAlert());
            assertThat(ctx.hasKubernetesContext()).isFalse();
            assertThat(ctx.hasKruizeContext()).isFalse();
            assertThat(ctx.hasCryostatContext()).isFalse();
            assertThat(ctx.hasQuarkusContext()).isFalse();
            assertThat(ctx.hasAsyncProfilerContext()).isFalse();
        }

        @Test
        @DisplayName("hasAsyncProfilerContext is true when Async Profiler MCP tool returns pod list")
        void shouldCollectAsyncProfilerContextWhenMcpToolSucceeds() throws Exception {
            // given
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode textEntry = mapper.createObjectNode();
            textEntry.put("text", "[{\"podName\":\"pod-1\",\"latestRecordingId\":null}]");
            ArrayNode contentArray = mapper.createArrayNode();
            contentArray.add(textEntry);
            ObjectNode podListResult = mapper.createObjectNode();
            podListResult.set("content", contentArray);

            McpContextCollector testCollector =
                    new McpContextCollector(mcpConfig, libertyLogsContextCollector, "cluster") {
                        @Override
                        protected String initializeMcpSession(String endpoint, int timeoutMs) {
                            return "test-session";
                        }

                        @Override
                        protected JsonNode callMcpTool(String endpoint, String sessionId,
                                String toolName, ObjectNode arguments, int timeoutMs) {
                            return podListResult;
                        }
                    };

            // when
            DiagnosticContext ctx = testCollector.collectContext(buildAlert());

            // then
            assertThat(ctx.hasAsyncProfilerContext()).isTrue();
            assertThat(ctx.getAsyncProfilerPodList()).contains("pod-1");
        }

        @Test
        @DisplayName("terminateMcpSession is called after successful callAsyncProfilerTool")
        void shouldTerminateSessionAfterSuccessfulAsyncProfilerCall() throws Exception {
            // given
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode textEntry = mapper.createObjectNode();
            textEntry.put("text", "[{\"podName\":\"pod-1\",\"latestRecordingId\":null}]");
            ArrayNode contentArray = mapper.createArrayNode();
            contentArray.add(textEntry);
            ObjectNode podListResult = mapper.createObjectNode();
            podListResult.set("content", contentArray);

            java.util.List<String> terminatedSessions = new java.util.ArrayList<>();

            McpContextCollector testCollector =
                    new McpContextCollector(mcpConfig, libertyLogsContextCollector, "cluster") {
                        @Override
                        protected String initializeMcpSession(String endpoint, int timeoutMs) {
                            return "test-session";
                        }

                        @Override
                        protected JsonNode callMcpTool(String endpoint, String sessionId,
                                String toolName, ObjectNode arguments, int timeoutMs) {
                            return podListResult;
                        }

                        @Override
                        protected void terminateMcpSession(String endpoint, String sessionId, int timeoutMs) {
                            terminatedSessions.add(sessionId);
                        }
                    };

            // when
            testCollector.collectContext(buildAlert());

            // then — session was terminated at least once (called per callAsyncProfilerTool invocation)
            assertThat(terminatedSessions).isNotEmpty();
            assertThat(terminatedSessions).allMatch(s -> s.equals("test-session"));
        }

        @Test
        @DisplayName("terminateMcpSession is called even when callMcpTool throws")
        void shouldTerminateSessionEvenWhenAsyncProfilerToolThrows() throws Exception {
            // given
            java.util.List<String> terminatedSessions = new java.util.ArrayList<>();

            McpContextCollector testCollector =
                    new McpContextCollector(mcpConfig, libertyLogsContextCollector, "cluster") {
                        @Override
                        protected String initializeMcpSession(String endpoint, int timeoutMs) {
                            return "test-session";
                        }

                        @Override
                        protected JsonNode callMcpTool(String endpoint, String sessionId,
                                String toolName, ObjectNode arguments, int timeoutMs) throws Exception {
                            throw new RuntimeException("MCP tool failure");
                        }

                        @Override
                        protected void terminateMcpSession(String endpoint, String sessionId, int timeoutMs) {
                            terminatedSessions.add(sessionId);
                        }
                    };

            // when — exception is swallowed by callAsyncProfilerTool, context is returned
            DiagnosticContext ctx = testCollector.collectContext(buildAlert());

            // then — session still terminated despite the exception
            assertThat(ctx).isNotNull();
            assertThat(terminatedSessions).isNotEmpty();
            assertThat(terminatedSessions).allMatch(s -> s.equals("test-session"));
        }



        @Test
        @DisplayName("hasQuarkusContext is true when Quarkus MCP tool returns metrics")
        void shouldCollectQuarkusMetricsWhenMcpToolSucceeds() throws Exception {
            // given
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode textEntry = mapper.createObjectNode();
            textEntry.put("text", "quarkus_http_server_bytes_total 42\n");
            ArrayNode contentArray = mapper.createArrayNode();
            contentArray.add(textEntry);
            ObjectNode metricsResult = mapper.createObjectNode();
            metricsResult.set("content", contentArray);

            McpContextCollector testCollector =
                    new McpContextCollector(mcpConfig, libertyLogsContextCollector, "cluster") {
                        @Override
                        protected String initializeMcpSession(String endpoint, int timeoutMs) {
                            return "test-session";
                        }

                        @Override
                        protected JsonNode callMcpTool(String endpoint, String sessionId,
                                String toolName, ObjectNode arguments, int timeoutMs) {
                            return metricsResult;
                        }
                    };

            // when
            DiagnosticContext ctx = testCollector.collectContext(buildAlert());

            // then
            assertThat(ctx.hasQuarkusContext()).isTrue();
            assertThat(ctx.getQuarkusRawMetrics()).contains("quarkus_http_server_bytes_total 42");
        }

        @Test
        @DisplayName("alert with no pod name skips k8s + cryostat calls")
        void noPodName_skipsKubernetesAndCryostat() {
            Alert alertNoPod = Alert.builder()
                .alertId("alrt_nopod000000001")
                .alertName("HighMemory")
                .severity(AlertSeverity.CRITICAL)
                .status(AlertStatus.ACCEPTED)
                .workloadInfo(Alert.WorkloadInfo.of(null, "container-1", "production", "cluster-1", "Deployment"))
                .workloadName("container-1")
                .build();

            // Should complete without exception and skip k8s/cryostat paths
            assertThatCode(() -> collector.collectContext(alertNoPod)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("alert with no container name skips kruize calls")
        void noContainerName_skipsKruize() {
            Alert alertNoContainer = Alert.builder()
                .alertId("alrt_nocontainer001")
                .alertName("HighMemory")
                .severity(AlertSeverity.CRITICAL)
                .status(AlertStatus.ACCEPTED)
                .workloadInfo(Alert.WorkloadInfo.of("pod-1", null, "production", "cluster-1", "Deployment"))
                .workloadName("my-workload")
                .build();

            assertThatCode(() -> collector.collectContext(alertNoContainer)).doesNotThrowAnyException();
        }
    }

    // -----------------------------------------------------------------------
    // vm platform — delegates to LibertyLogsContextCollector + JMX (HTTP fails)
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("collectContext() — vm platform")
    class VmPlatformTests {

        McpContextCollector collector;

        @BeforeEach
        void setUp() {
            when(mcpConfig.filesystem()).thenReturn(filesystemMcpConfig);
            when(filesystemMcpConfig.libertyLogsDir()).thenReturn("/logs");
            when(filesystemMcpConfig.timeoutMs()).thenReturn(1);
            when(filesystemMcpConfig.endpoint()).thenReturn("http://192.0.2.1");
            when(filesystemMcpConfig.alertWindowMinutes()).thenReturn(5);

            when(mcpConfig.jmx()).thenReturn(jmxConfig);
            when(jmxConfig.endpoint()).thenReturn("http://192.0.2.1");
            when(jmxConfig.timeoutMs()).thenReturn(1);

            // LibertyLogsContextCollector is mocked — returns null (no HTTP)
            when(libertyLogsContextCollector.collectLibertyLogs(any(), any())).thenReturn(null);

            collector = new McpContextCollector(mcpConfig, libertyLogsContextCollector, "vm");
        }

        @Test
        @DisplayName("returns DiagnosticContext with vm platform")
        void returnsContext_vmPlatform() {
            DiagnosticContext ctx = collector.collectContext(buildAlert());
            assertThat(ctx).isNotNull();
            assertThat(ctx.getPlatform()).isEqualTo(DiagnosticContext.PLATFORM_VM);
        }

        @Test
        @DisplayName("delegates liberty logs collection to LibertyLogsContextCollector")
        void delegatesToLibertyLogsCollector() {
            collector.collectContext(buildAlert());
            verify(libertyLogsContextCollector).collectLibertyLogs(eq("alrt_test00000001234"), any());
        }

        @Test
        @DisplayName("JMX HTTP failure leaves jmx context null — no exception thrown")
        void jmxFailure_noException() {
            assertThatCode(() -> collector.collectContext(buildAlert())).doesNotThrowAnyException();
            assertThat(collector.collectContext(buildAlert()).hasJmxContext()).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // platform normalisation
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("platform normalisation Tests")
    class PlatformNormalisationTests {

        @Test
        @DisplayName("null platform defaults to cluster")
        void nullPlatform_defaultsToCluster() {
            when(mcpConfig.kubernetes()).thenReturn(k8sConfig);
            when(k8sConfig.endpoint()).thenReturn("http://192.0.2.1");
            when(k8sConfig.timeoutMs()).thenReturn(1);
            when(mcpConfig.kruize()).thenReturn(kruizeConfig);
            when(kruizeConfig.endpoint()).thenReturn("http://192.0.2.1");
            when(kruizeConfig.timeoutMs()).thenReturn(1);
            when(mcpConfig.cryostat()).thenReturn(cryostatConfig);
            when(cryostatConfig.endpoint()).thenReturn("http://192.0.2.1");
            when(cryostatConfig.timeoutMs()).thenReturn(1);
            when(cryostatConfig.maxRetries()).thenReturn(0);
            when(cryostatConfig.retryDelayMs()).thenReturn(1);

            when(mcpConfig.quarkus()).thenReturn(quarkusConfig);
            when(quarkusConfig.endpoint()).thenReturn(Optional.of("http://192.0.2.1"));
            when(quarkusConfig.timeoutMs()).thenReturn(1);

            when(mcpConfig.asyncProfiler()).thenReturn(asyncProfilerConfig);
            when(asyncProfilerConfig.endpoint()).thenReturn(Optional.of("http://192.0.2.1"));
            when(asyncProfilerConfig.timeoutMs()).thenReturn(1);

            McpContextCollector c = new McpContextCollector(mcpConfig, libertyLogsContextCollector, null);
            DiagnosticContext ctx = c.collectContext(buildAlert());
            assertThat(ctx.getPlatform()).isEqualTo(DiagnosticContext.PLATFORM_CLUSTER);
        }

        @Test
        @DisplayName("UPPERCASE platform is normalised to lowercase")
        void upperCasePlatform_normalisedToLowercase() {
            when(mcpConfig.filesystem()).thenReturn(filesystemMcpConfig);
            when(filesystemMcpConfig.libertyLogsDir()).thenReturn("/logs");
            when(filesystemMcpConfig.timeoutMs()).thenReturn(1);
            when(filesystemMcpConfig.endpoint()).thenReturn("http://192.0.2.1");
            when(filesystemMcpConfig.alertWindowMinutes()).thenReturn(5);
            when(mcpConfig.jmx()).thenReturn(jmxConfig);
            when(jmxConfig.endpoint()).thenReturn("http://192.0.2.1");
            when(jmxConfig.timeoutMs()).thenReturn(1);
            when(libertyLogsContextCollector.collectLibertyLogs(any(), any())).thenReturn(null);

            McpContextCollector c = new McpContextCollector(mcpConfig, libertyLogsContextCollector, "VM");
            assertThat(c.collectContext(buildAlert()).getPlatform()).isEqualTo("vm");
        }
    }
}
