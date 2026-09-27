package com.causa.api.validators;

import com.causa.api.dto.request.ExternalConfigRequest;
import com.causa.api.dto.request.LlmConfigRequest;
import com.causa.common.exceptions.ConfigException;
import com.causa.core.domain.AuthConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ConfigRequestValidator}.
 *
 * @since 0.0.4
 */
@DisplayName("ConfigRequestValidator Tests")
class ConfigRequestValidatorTest {

    private ConfigRequestValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ConfigRequestValidator();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ExternalConfigRequest externalRequest(String name, String url, AuthConfig auth, Map<String, Object> additional) {
        ExternalConfigRequest req = new ExternalConfigRequest();
        req.setName(name);
        req.setUrl(url);
        req.setAuthConfig(auth);
        req.setAdditionalConfig(additional);
        return req;
    }

    private LlmConfigRequest llmRequest(String url, List<String> models, AuthConfig auth) {
        LlmConfigRequest req = new LlmConfigRequest();
        req.setUrl(url);
        req.setModels(models);
        req.setTemperature(BigDecimal.valueOf(0.1));
        req.setMaxTokens(8192);
        req.setAuthConfig(auth);
        return req;
    }

    private AuthConfig apiKeyAuth(String apiKey) {
        return new AuthConfig("API_KEY", apiKey, null, null, null, null, null, null);
    }

    private AuthConfig saJsonKeyAuth(String credentialsJson) {
        return new AuthConfig("SA_JSON_KEY", null, null, null, credentialsJson, null, null, null);
    }

    // =========================================================================
    // validateExternal()
    // =========================================================================

    @Nested
    @DisplayName("validateExternal() — platform guard")
    class ExternalPlatformGuardTests {

        @Test
        @DisplayName("throws VALIDATION_ERROR for null platform")
        void throws_forNullPlatform() {
            assertThatThrownBy(() -> validator.validateExternal(null, new ExternalConfigRequest()))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("platform")
                .satisfies(e -> assertThat(((ConfigException) e).getErrorType()).isEqualTo("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("throws VALIDATION_ERROR for blank platform")
        void throws_forBlankPlatform() {
            assertThatThrownBy(() -> validator.validateExternal("  ", new ExternalConfigRequest()))
                .isInstanceOf(ConfigException.class)
                .satisfies(e -> assertThat(((ConfigException) e).getErrorType()).isEqualTo("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("throws UNKNOWN_PLATFORM for unregistered platform")
        void throws_forUnknownPlatform() {
            assertThatThrownBy(() -> validator.validateExternal("PROMETHEUS", new ExternalConfigRequest()))
                .isInstanceOf(ConfigException.class)
                .satisfies(e -> assertThat(((ConfigException) e).getErrorType()).isEqualTo("UNKNOWN_PLATFORM"));
        }

        @Test
        @DisplayName("throws VALIDATION_ERROR when request body is null")
        void throws_forNullRequest() {
            assertThatThrownBy(() -> validator.validateExternal("DATADOG", null))
                .isInstanceOf(ConfigException.class)
                .satisfies(e -> assertThat(((ConfigException) e).getErrorType()).isEqualTo("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("accepts platform name case-insensitively")
        void accepts_caseInsensitivePlatform() {
            AuthConfig auth = new AuthConfig("API_KEY", "key", "appkey", null, null, null, null, null);
            ExternalConfigRequest req = externalRequest("prod", "https://api.datadoghq.com", auth, Map.of());
            assertThatNoException().isThrownBy(() -> validator.validateExternal("datadog", req));
        }
    }

    @Nested
    @DisplayName("validateExternal() — DATADOG")
    class ExternalDatadogTests {

        @Test
        @DisplayName("passes for valid DATADOG request")
        void passes_validDatadog() {
            AuthConfig auth = new AuthConfig("API_KEY", "dd-api-key", "dd-app-key", null, null, null, null, null);
            ExternalConfigRequest req = externalRequest("dd-prod", "https://api.datadoghq.com", auth, Map.of());
            assertThatNoException().isThrownBy(() -> validator.validateExternal("DATADOG", req));
        }

        @Test
        @DisplayName("throws when name is missing for DATADOG")
        void throws_missingName() {
            AuthConfig auth = new AuthConfig("API_KEY", "key", "appkey", null, null, null, null, null);
            ExternalConfigRequest req = externalRequest(null, "https://api.datadoghq.com", auth, Map.of());
            assertThatThrownBy(() -> validator.validateExternal("DATADOG", req))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("name");
        }

        @Test
        @DisplayName("throws when url is missing for DATADOG")
        void throws_missingUrl() {
            AuthConfig auth = new AuthConfig("API_KEY", "key", "appkey", null, null, null, null, null);
            ExternalConfigRequest req = externalRequest("dd-prod", null, auth, Map.of());
            assertThatThrownBy(() -> validator.validateExternal("DATADOG", req))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("url");
        }

        @Test
        @DisplayName("throws when auth_config is missing for DATADOG")
        void throws_missingAuthConfig() {
            ExternalConfigRequest req = externalRequest("dd-prod", "https://api.datadoghq.com", null, Map.of());
            assertThatThrownBy(() -> validator.validateExternal("DATADOG", req))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("auth_config");
        }

        @Test
        @DisplayName("throws when apiKey is blank for DATADOG")
        void throws_blankApiKey() {
            AuthConfig auth = new AuthConfig("API_KEY", "  ", "appkey", null, null, null, null, null);
            ExternalConfigRequest req = externalRequest("dd-prod", "https://api.datadoghq.com", auth, Map.of());
            assertThatThrownBy(() -> validator.validateExternal("DATADOG", req))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("apiKey");
        }

        @Test
        @DisplayName("throws when appKey is blank for DATADOG")
        void throws_blankAppKey() {
            AuthConfig auth = new AuthConfig("API_KEY", "key", null, null, null, null, null, null);
            ExternalConfigRequest req = externalRequest("dd-prod", "https://api.datadoghq.com", auth, Map.of());
            assertThatThrownBy(() -> validator.validateExternal("DATADOG", req))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("appKey");
        }
    }

    @Nested
    @DisplayName("validateExternal() — SLACK")
    class ExternalSlackTests {

        @Test
        @DisplayName("passes for valid SLACK request")
        void passes_validSlack() {
            AuthConfig auth = new AuthConfig("WEBHOOK", null, null, "xoxb-token", null, null, null, null);
            Map<String, Object> additional = new HashMap<>();
            additional.put("channel", "#alerts");
            ExternalConfigRequest req = externalRequest("slack-prod", "https://hooks.slack.com/services/...", auth, additional);
            assertThatNoException().isThrownBy(() -> validator.validateExternal("SLACK", req));
        }

        @Test
        @DisplayName("throws when channel is missing in additional_config for SLACK")
        void throws_missingChannel() {
            AuthConfig auth = new AuthConfig("WEBHOOK", null, null, "xoxb-token", null, null, null, null);
            ExternalConfigRequest req = externalRequest("slack-prod", "https://hooks.slack.com/services/...", auth, Map.of());
            assertThatThrownBy(() -> validator.validateExternal("SLACK", req))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("channel");
        }
    }

    @Nested
    @DisplayName("validateExternal() — JIRA")
    class ExternalJiraTests {

        @Test
        @DisplayName("passes for valid JIRA request (url optional)")
        void passes_validJira() {
            AuthConfig auth = new AuthConfig("API_TOKEN", null, null, "jira-token", null, null, "bot@example.com", null);
            Map<String, Object> additional = new HashMap<>();
            additional.put("projectName", "OPS");
            ExternalConfigRequest req = externalRequest("jira-prod", null, auth, additional);
            assertThatNoException().isThrownBy(() -> validator.validateExternal("JIRA", req));
        }

        @Test
        @DisplayName("throws when projectName missing for JIRA")
        void throws_missingProjectName() {
            AuthConfig auth = new AuthConfig("API_TOKEN", null, null, "jira-token", null, null, "bot@example.com", null);
            ExternalConfigRequest req = externalRequest("jira-prod", null, auth, Map.of());
            assertThatThrownBy(() -> validator.validateExternal("JIRA", req))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("projectName");
        }
    }

    // =========================================================================
    // validateLlm()
    // =========================================================================

    @Nested
    @DisplayName("validateLlm() — auth type guard")
    class LlmAuthTypeGuardTests {

        @Test
        @DisplayName("throws VALIDATION_ERROR for null authType")
        void throws_forNullAuthType() {
            assertThatThrownBy(() -> validator.validateLlm(null, new LlmConfigRequest()))
                .isInstanceOf(ConfigException.class)
                .satisfies(e -> assertThat(((ConfigException) e).getErrorType()).isEqualTo("VALIDATION_ERROR"));
        }

        @Test
        @DisplayName("throws UNKNOWN_AUTH_TYPE for unregistered authType")
        void throws_forUnknownAuthType() {
            assertThatThrownBy(() -> validator.validateLlm("OAUTH", new LlmConfigRequest()))
                .isInstanceOf(ConfigException.class)
                .satisfies(e -> assertThat(((ConfigException) e).getErrorType()).isEqualTo("UNKNOWN_AUTH_TYPE"));
        }

        @Test
        @DisplayName("throws VALIDATION_ERROR when request is null")
        void throws_forNullRequest() {
            assertThatThrownBy(() -> validator.validateLlm("API_KEY", null))
                .isInstanceOf(ConfigException.class)
                .satisfies(e -> assertThat(((ConfigException) e).getErrorType()).isEqualTo("VALIDATION_ERROR"));
        }
    }

    @Nested
    @DisplayName("validateLlm() — API_KEY")
    class LlmApiKeyTests {

        @Test
        @DisplayName("passes for valid API_KEY request")
        void passes_validApiKey() {
            LlmConfigRequest req = llmRequest("https://api.anthropic.com", List.of("claude-3"), apiKeyAuth("sk-ant-..."));
            assertThatNoException().isThrownBy(() -> validator.validateLlm("API_KEY", req));
        }

        @Test
        @DisplayName("throws when url is missing")
        void throws_missingUrl() {
            LlmConfigRequest req = llmRequest(null, List.of("claude-3"), apiKeyAuth("sk-ant-..."));
            assertThatThrownBy(() -> validator.validateLlm("API_KEY", req))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("url");
        }

        @Test
        @DisplayName("throws when models list is empty")
        void throws_emptyModels() {
            LlmConfigRequest req = llmRequest("https://api.anthropic.com", List.of(), apiKeyAuth("sk-ant-..."));
            assertThatThrownBy(() -> validator.validateLlm("API_KEY", req))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("models");
        }

        @Test
        @DisplayName("throws when models list is null")
        void throws_nullModels() {
            LlmConfigRequest req = llmRequest("https://api.anthropic.com", null, apiKeyAuth("sk-ant-..."));
            assertThatThrownBy(() -> validator.validateLlm("API_KEY", req))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("models");
        }

        @Test
        @DisplayName("throws when apiKey is blank")
        void throws_blankApiKey() {
            LlmConfigRequest req = llmRequest("https://api.anthropic.com", List.of("claude-3"), apiKeyAuth("  "));
            assertThatThrownBy(() -> validator.validateLlm("API_KEY", req))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("apiKey");
        }

        @Test
        @DisplayName("throws when auth_config is null")
        void throws_nullAuthConfig() {
            LlmConfigRequest req = llmRequest("https://api.anthropic.com", List.of("claude-3"), null);
            assertThatThrownBy(() -> validator.validateLlm("API_KEY", req))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("auth_config");
        }
    }

    @Nested
    @DisplayName("validateLlm() — SA_JSON_KEY")
    class LlmSaJsonKeyTests {

        @Test
        @DisplayName("passes for valid SA_JSON_KEY request")
        void passes_validSaJsonKey() {
            LlmConfigRequest req = llmRequest(
                "https://us-east5-aiplatform.googleapis.com",
                List.of("claude-3"),
                saJsonKeyAuth("eyJhbGciOiJSUzI1NiJ9...")
            );
            assertThatNoException().isThrownBy(() -> validator.validateLlm("SA_JSON_KEY", req));
        }

        @Test
        @DisplayName("throws when credentialsJson is blank")
        void throws_blankCredentialsJson() {
            LlmConfigRequest req = llmRequest(
                "https://us-east5-aiplatform.googleapis.com",
                List.of("claude-3"),
                saJsonKeyAuth("   ")
            );
            assertThatThrownBy(() -> validator.validateLlm("SA_JSON_KEY", req))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("credentialsJson");
        }
    }

    @Nested
    @DisplayName("validateLlm() — CUSTOM_HEADERS")
    class LlmCustomHeadersTests {

        @Test
        @DisplayName("passes for valid CUSTOM_HEADERS request")
        void passes_validCustomHeaders() {
            AuthConfig auth = new AuthConfig("CUSTOM_HEADERS", null, null, null, null,
                Map.of("X-Api-Key", "secret"), null, null);
            LlmConfigRequest req = llmRequest("https://custom-llm.example.com", List.of("my-model"), auth);
            assertThatNoException().isThrownBy(() -> validator.validateLlm("CUSTOM_HEADERS", req));
        }

        @Test
        @DisplayName("throws when headers map is empty")
        void throws_emptyHeaders() {
            AuthConfig auth = new AuthConfig("CUSTOM_HEADERS", null, null, null, null,
                Map.of(), null, null);
            LlmConfigRequest req = llmRequest("https://custom-llm.example.com", List.of("my-model"), auth);
            assertThatThrownBy(() -> validator.validateLlm("CUSTOM_HEADERS", req))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("headers");
        }

        @Test
        @DisplayName("throws when headers are null")
        void throws_nullHeaders() {
            AuthConfig auth = new AuthConfig("CUSTOM_HEADERS", null, null, null, null,
                null, null, null);
            LlmConfigRequest req = llmRequest("https://custom-llm.example.com", List.of("my-model"), auth);
            assertThatThrownBy(() -> validator.validateLlm("CUSTOM_HEADERS", req))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("headers");
        }
    }
}
