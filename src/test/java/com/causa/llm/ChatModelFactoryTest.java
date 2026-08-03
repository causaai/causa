package com.causa.llm;

import com.causa.common.exceptions.LLMException;
import com.causa.config.AppConfig;
import com.causa.config.LlmConfigSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatModelFactory Tests")
class ChatModelFactoryTest {

    @Mock AppConfig appConfig;
    @Mock LlmConfigSnapshot llmConfig;

    private ChatModelFactory factory() {
        return new ChatModelFactory(appConfig);
    }

    @Nested
    @DisplayName("chatModel() — missing/blank provider")
    class MissingProviderTests {

        @Test
        @DisplayName("throws LLMException when provider is empty")
        void throws_whenProviderEmpty() {
            when(appConfig.getLlmConfig()).thenReturn(llmConfig);
            when(llmConfig.getProvider()).thenReturn("");
            assertThatThrownBy(() -> factory().chatModel())
                    .isInstanceOf(LLMException.class);
        }

        @Test
        @DisplayName("throws LLMException when provider is blank")
        void throws_whenProviderBlank() {
            when(appConfig.getLlmConfig()).thenReturn(llmConfig);
            when(llmConfig.getProvider()).thenReturn("   ");
            assertThatThrownBy(() -> factory().chatModel())
                    .isInstanceOf(LLMException.class);
        }
    }

    @Nested
    @DisplayName("chatModel() — unknown provider")
    class UnknownProviderTests {

        @Test
        @DisplayName("throws LLMException for unsupported provider string")
        void throws_forUnknownProvider() {
            when(appConfig.getLlmConfig()).thenReturn(llmConfig);
            when(llmConfig.getProvider()).thenReturn("some-unknown-llm");
            assertThatThrownBy(() -> factory().chatModel())
                    .isInstanceOf(LLMException.class)
                    .hasMessageContaining("some-unknown-llm");
        }
    }

    @Nested
    @DisplayName("chatModel() — anthropic (missing API key)")
    class AnthropicMissingKeyTests {

        @Test
        @DisplayName("throws LLMException when API key is empty")
        void throws_whenApiKeyEmpty() {
            when(appConfig.getLlmConfig()).thenReturn(llmConfig);
            when(llmConfig.getProvider()).thenReturn("anthropic");
            when(llmConfig.getApiKey()).thenReturn("");
            assertThatThrownBy(() -> factory().chatModel())
                    .isInstanceOf(LLMException.class);
        }

        @Test
        @DisplayName("throws LLMException when API key is blank")
        void throws_whenApiKeyBlank() {
            when(appConfig.getLlmConfig()).thenReturn(llmConfig);
            when(llmConfig.getProvider()).thenReturn("anthropic");
            when(llmConfig.getApiKey()).thenReturn("  ");
            assertThatThrownBy(() -> factory().chatModel())
                    .isInstanceOf(LLMException.class);
        }
    }

    @Nested
    @DisplayName("chatModel() — vertex-ai-anthropic (missing project ID)")
    class VertexMissingProjectTests {

        @Test
        @DisplayName("throws LLMException when vertex project ID is empty")
        void throws_whenVertexProjectIdEmpty() {
            when(appConfig.getLlmConfig()).thenReturn(llmConfig);
            when(llmConfig.getProvider()).thenReturn("vertex-ai-anthropic");
            when(llmConfig.getVertexProjectId()).thenReturn("");
            assertThatThrownBy(() -> factory().chatModel())
                    .isInstanceOf(LLMException.class);
        }

        @Test
        @DisplayName("throws LLMException when vertex project ID is blank")
        void throws_whenVertexProjectIdBlank() {
            when(appConfig.getLlmConfig()).thenReturn(llmConfig);
            when(llmConfig.getProvider()).thenReturn("vertex-ai-anthropic");
            when(llmConfig.getVertexProjectId()).thenReturn("  ");
            assertThatThrownBy(() -> factory().chatModel())
                    .isInstanceOf(LLMException.class);
        }
    }
}
