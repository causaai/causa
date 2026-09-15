package com.causa.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Config Snapshot Tests")
class ConfigSnapshotTests {

    // -------------------------------------------------------------------------
    // AlertConfigSnapshot
    // -------------------------------------------------------------------------
    @Nested @DisplayName("AlertConfigSnapshot Tests")
    class AlertConfigSnapshotTests {

        @Test void defaults_whenEmpty() {
            AlertConfigSnapshot s = new AlertConfigSnapshot(Map.of());
            assertThat(s.getFilterSeverity()).isEqualTo("critical");
            assertThat(s.getCooldownMinutes()).isEqualTo(15);
            assertThat(s.getIgnoreNamespaces()).isEmpty();
            assertThat(s.getCooldownCleanupInterval()).isEqualTo("5m");
        }

        @Test void overriddenValues() {
            AlertConfigSnapshot s = new AlertConfigSnapshot(Map.of(
                "ALERT_FILTER_SEVERITY", "warning",
                "ALERT_COOLDOWN_MINUTES", "30",
                "ALERT_IGNORE_NAMESPACES", "kube-system,istio-system",
                "ALERT_COOLDOWN_CLEANUP_INTERVAL", "10m"
            ));
            assertThat(s.getFilterSeverity()).isEqualTo("warning");
            assertThat(s.getCooldownMinutes()).isEqualTo(30);
            assertThat(s.getIgnoreNamespaces()).containsExactlyInAnyOrder("kube-system", "istio-system");
            assertThat(s.getCooldownCleanupInterval()).isEqualTo("10m");
        }

        @Test void invalidCooldownMinutes_returnsDefault() {
            AlertConfigSnapshot s = new AlertConfigSnapshot(Map.of("ALERT_COOLDOWN_MINUTES", "not-a-number"));
            assertThat(s.getCooldownMinutes()).isEqualTo(15);
        }

        @Test void blankCooldownMinutes_returnsDefault() {
            AlertConfigSnapshot s = new AlertConfigSnapshot(Map.of("ALERT_COOLDOWN_MINUTES", "  "));
            assertThat(s.getCooldownMinutes()).isEqualTo(15);
        }

        @Test void singleIgnoreNamespace() {
            AlertConfigSnapshot s = new AlertConfigSnapshot(Map.of("ALERT_IGNORE_NAMESPACES", "kube-system"));
            assertThat(s.getIgnoreNamespaces()).containsExactly("kube-system");
        }
    }

    // -------------------------------------------------------------------------
    // ClusterConfigSnapshot
    // -------------------------------------------------------------------------
    @Nested @DisplayName("ClusterConfigSnapshot Tests")
    class ClusterConfigSnapshotTests {

        @Test void defaults_whenEmpty() {
            ClusterConfigSnapshot s = new ClusterConfigSnapshot(Map.of());
            assertThat(s.getClusterName()).isEqualTo("default");
            assertThat(s.getClusterType()).isEqualTo("vm");
        }

        @Test void overriddenValues() {
            ClusterConfigSnapshot s = new ClusterConfigSnapshot(Map.of(
                "CLUSTER_NAME", "prod-cluster",
                "CLUSTER_TYPE", "cluster"
            ));
            assertThat(s.getClusterName()).isEqualTo("prod-cluster");
            assertThat(s.getClusterType()).isEqualTo("cluster");
        }
    }

    // -------------------------------------------------------------------------
    // LlmConfigSnapshot
    // -------------------------------------------------------------------------
    @Nested @DisplayName("LlmConfigSnapshot Tests")
    class LlmConfigSnapshotTests {

        @Test void defaults_whenEmpty() {
            LlmConfigSnapshot s = new LlmConfigSnapshot(Map.of());
            assertThat(s.getProvider()).isEmpty();
            assertThat(s.getModelName()).isEmpty();
            assertThat(s.getTemperature()).isEqualTo(0.1);
            assertThat(s.getMaxTokens()).isEqualTo(8192);
            assertThat(s.getTimeoutSeconds()).isEqualTo(180);
            assertThat(s.getChatMemorySize()).isEqualTo(10);
            assertThat(s.getBobShellPath()).isEqualTo("bob");
            assertThat(s.isSkillsEnabled()).isTrue();
            assertThat(s.getCustomHeaders()).isEqualTo("{}");
        }

        @Test void overriddenValues() {
            LlmConfigSnapshot s = new LlmConfigSnapshot(Map.of(
                "LLM_PROVIDER", "anthropic",
                "LLM_MODEL_NAME", "claude-3",
                "LLM_TEMPERATURE", "0.7",
                "LLM_MAX_TOKENS", "4096",
                "LLM_TIMEOUT_SECONDS", "60",
                "LLM_CHAT_MEMORY_SIZE", "5",
                "LLM_SKILLS_ENABLED", "false",
                "LLM_API_KEY", "key123"
            ));
            assertThat(s.getProvider()).isEqualTo("anthropic");
            assertThat(s.getModelName()).isEqualTo("claude-3");
            assertThat(s.getTemperature()).isEqualTo(0.7);
            assertThat(s.getMaxTokens()).isEqualTo(4096);
            assertThat(s.getTimeoutSeconds()).isEqualTo(60);
            assertThat(s.getChatMemorySize()).isEqualTo(5);
            assertThat(s.isSkillsEnabled()).isFalse();
            assertThat(s.getApiKey()).isEqualTo("key123");
        }

        @Test void invalidTemperature_returnsDefault() {
            LlmConfigSnapshot s = new LlmConfigSnapshot(Map.of("LLM_TEMPERATURE", "abc"));
            assertThat(s.getTemperature()).isEqualTo(0.1);
        }

        @Test void invalidMaxTokens_returnsDefault() {
            LlmConfigSnapshot s = new LlmConfigSnapshot(Map.of("LLM_MAX_TOKENS", "xyz"));
            assertThat(s.getMaxTokens()).isEqualTo(8192);
        }

        @Test void vertexFields() {
            LlmConfigSnapshot s = new LlmConfigSnapshot(Map.of(
                "VERTEX_PROJECT_ID", "my-project",
                "VERTEX_LOCATION", "us-east5"
            ));
            assertThat(s.getVertexProjectId()).isEqualTo("my-project");
            assertThat(s.getVertexLocation()).isEqualTo("us-east5");
        }
    }
}
