package com.causa.llm;

import com.causa.common.constants.ConfigConstants.LlmProvider;
import com.causa.common.constants.LLMConstants;
import com.causa.common.exceptions.LLMException;
import com.causa.config.LlmConfigCache;
import com.causa.core.domain.LlmConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatModelFactory Tests")
class ChatModelFactoryTest {

    @Mock LlmConfigCache llmConfigCache;

    private ChatModelFactory factory() {
        return new ChatModelFactory(llmConfigCache);
    }

    /** Helper: returns an active LlmConfig with the given provider and model. */
    private LlmConfig activeConfig(LlmProvider provider, String model) {
        return LlmConfig.builder()
            .id("llm_cnf_test")
            .provider(provider)
            .url("https://api.example.com")
            .models(model != null && !model.isBlank() ? List.of(model) : List.of())
            .authConfig(new com.causa.core.domain.AuthConfig(null, null, null, null, null, null, null, null))
            .build();
    }

    @Nested
    @DisplayName("chatModel() — no active config")
    class NoActiveConfigTests {

        @Test
        @DisplayName("throws LLMException with MISSING_CONFIGURATION when cache is empty")
        void throws_whenCacheEmpty() {
            when(llmConfigCache.getActive()).thenReturn(Optional.empty());
            assertThatThrownBy(() -> factory().chatModel())
                    .isInstanceOf(LLMException.class)
                    .satisfies(ex -> assertThat(((LLMException) ex).getErrorType())
                            .isEqualTo(LLMConstants.ErrorTypes.MISSING_CONFIGURATION));
        }
    }

    @Nested
    @DisplayName("chatModel() — anthropic (missing API key)")
    class AnthropicMissingKeyTests {

        @Test
        @DisplayName("throws LLMException when API key is null")
        void throws_whenApiKeyNull() {
            LlmConfig cfg = activeConfig(LlmProvider.ANTHROPIC, "claude-3");
            when(llmConfigCache.getActive()).thenReturn(Optional.of(cfg));
            // authConfig.apiKey() is null on the config built by activeConfig()
            assertThatThrownBy(() -> factory().chatModel())
                    .isInstanceOf(LLMException.class);
        }
    }

    @Nested
    @DisplayName("chatModel() — vertex-ai-anthropic (missing project ID)")
    class VertexMissingProjectTests {

        @Test
        @DisplayName("throws LLMException when additionalConfig has no projectId")
        void throws_whenVertexProjectIdMissing() {
            LlmConfig cfg = activeConfig(LlmProvider.VERTEX_AI, "claude-3");
            when(llmConfigCache.getActive()).thenReturn(Optional.of(cfg));
            assertThatThrownBy(() -> factory().chatModel())
                    .isInstanceOf(LLMException.class);
        }
    }

    @Nested
    @DisplayName("chatModel() — vertex-ai-anthropic (missing credentials)")
    class VertexMissingCredentialsTests {

        @Test
        @DisplayName("throws LLMException when GOOGLE_APPLICATION_CREDENTIALS is null")
        void throws_whenAdcNull() {
            var authConfig = new com.causa.core.domain.AuthConfig(
                "SA_JSON_KEY", null, null, null, null, null, null, null);
            LlmConfig cfg = LlmConfig.builder()
                .id("llm_cnf_test")
                .provider(LlmProvider.VERTEX_AI)
                .url("https://api.example.com")
                .models(List.of("claude-3"))
                .authConfig(authConfig)
                .additionalConfig(Map.of("projectId", "my-project"))
                .build();
            when(llmConfigCache.getActive()).thenReturn(Optional.of(cfg));
            assertThatThrownBy(() -> factory().chatModel())
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("GOOGLE_APPLICATION_CREDENTIALS")
                    .satisfies(ex -> assertThat(((LLMException) ex).getErrorType())
                            .isEqualTo(LLMConstants.ErrorTypes.MISSING_CONFIGURATION));
        }

        @Test
        @DisplayName("throws LLMException when GOOGLE_APPLICATION_CREDENTIALS is not valid Base64")
        void throws_whenAdcNotBase64() {
            var authConfig = new com.causa.core.domain.AuthConfig(
                "SA_JSON_KEY", null, null, null, "!!!not-base64!!!", null, null, null);
            LlmConfig cfg = LlmConfig.builder()
                .id("llm_cnf_test")
                .provider(LlmProvider.VERTEX_AI)
                .url("https://api.example.com")
                .models(List.of("claude-3"))
                .authConfig(authConfig)
                .additionalConfig(Map.of("projectId", "my-project"))
                .build();
            when(llmConfigCache.getActive()).thenReturn(Optional.of(cfg));
            assertThatThrownBy(() -> factory().chatModel())
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("Base64")
                    .satisfies(ex -> assertThat(((LLMException) ex).getErrorType())
                            .isEqualTo(LLMConstants.ErrorTypes.INVALID_CONFIGURATION));
        }
    }
}
