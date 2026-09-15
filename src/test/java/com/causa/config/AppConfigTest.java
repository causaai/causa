package com.causa.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AppConfig Tests")
class AppConfigTest {

    @Nested @DisplayName("get() / put() Tests")
    class GetPutTests {
        @Test void putAndGetValue() {
            AppConfig c = new AppConfig();
            c.put("k", "v");
            assertThat(c.get("k")).contains("v");
        }
        @Test void getMissingReturnsEmpty() {
            assertThat(new AppConfig().get("missing")).isEmpty();
        }
        @Test void putNullRemovesKey() {
            AppConfig c = new AppConfig();
            c.put("k", "v");
            c.put("k", null);
            assertThat(c.get("k")).isEmpty();
        }
        @Test void putBlankRemovesKey() {
            AppConfig c = new AppConfig();
            c.put("k", "v");
            c.put("k", "   ");
            assertThat(c.get("k")).isEmpty();
        }
    }

    @Nested @DisplayName("clear() Tests")
    class ClearTests {
        @Test void clearRemovesAll() {
            AppConfig c = new AppConfig();
            c.put("a","1"); c.put("b","2");
            c.clear();
            assertThat(c.get("a")).isEmpty();
            assertThat(c.asMap()).isEmpty();
        }
    }

    @Nested @DisplayName("asMap() Tests")
    class AsMapTests {
        @Test void containsAllEntries() {
            AppConfig c = new AppConfig();
            c.put("x","1"); c.put("y","2");
            assertThat(c.asMap()).containsEntry("x","1").containsEntry("y","2");
        }
        @Test void isImmutable() {
            AppConfig c = new AppConfig();
            c.put("k","v");
            Map<String,String> m = c.asMap();
            assertThatThrownBy(() -> m.put("new","val")).isInstanceOf(UnsupportedOperationException.class);
        }
    }

    @Nested @DisplayName("getLlmConfig() Tests")
    class LlmConfigTests {
        @Test void returnsSnapshot() {
            AppConfig c = new AppConfig();
            c.put("LLM_PROVIDER", "anthropic");
            assertThat(c.getLlmConfig().getProvider()).isEqualTo("anthropic");
        }
    }

    @Nested @DisplayName("getAlertConfig() Tests")
    class AlertConfigTests {
        @Test void returnsSnapshot() {
            assertThat(new AppConfig().getAlertConfig().getFilterSeverity()).isEqualTo("critical");
        }
    }

    @Nested @DisplayName("getClusterConfig() Tests")
    class ClusterConfigTests {
        @Test void returnsSnapshot() {
            assertThat(new AppConfig().getClusterConfig().getClusterName()).isEqualTo("default");
        }
    }
}
