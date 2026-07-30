package com.causa.llm;

import com.causa.common.exceptions.LLMException;
import com.causa.config.LLMConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatModelFactory Tests")
class ChatModelFactoryTest {

    @Mock LLMConfig config;
    @InjectMocks ChatModelFactory factory;

    @Nested
    @DisplayName("chatModel() — missing/blank provider")
    class MissingProviderTests {

        @Test
        @DisplayName("throws LLMException when provider is empty")
        void throws_whenProviderEmpty() {
            when(config.provider()).thenReturn(Optional.empty());
            assertThatThrownBy(() -> factory.chatModel())
                    .isInstanceOf(LLMException.class);
        }

        @Test
        @DisplayName("throws LLMException when provider is blank")
        void throws_whenProviderBlank() {
            when(config.provider()).thenReturn(Optional.of("   "));
            assertThatThrownBy(() -> factory.chatModel())
                    .isInstanceOf(LLMException.class);
        }
    }

    @Nested
    @DisplayName("chatModel() — unknown provider")
    class UnknownProviderTests {

        @Test
        @DisplayName("throws LLMException for unsupported provider string")
        void throws_forUnknownProvider() {
            when(config.provider()).thenReturn(Optional.of("some-unknown-llm"));
            assertThatThrownBy(() -> factory.chatModel())
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
            when(config.provider()).thenReturn(Optional.of("anthropic"));
            when(config.apiKey()).thenReturn(Optional.empty());
            assertThatThrownBy(() -> factory.chatModel())
                    .isInstanceOf(LLMException.class);
        }

        @Test
        @DisplayName("throws LLMException when API key is blank")
        void throws_whenApiKeyBlank() {
            when(config.provider()).thenReturn(Optional.of("anthropic"));
            when(config.apiKey()).thenReturn(Optional.of("  "));
            assertThatThrownBy(() -> factory.chatModel())
                    .isInstanceOf(LLMException.class);
        }
    }

    @Nested
    @DisplayName("chatModel() — vertex-ai-anthropic (missing project ID)")
    class VertexMissingProjectTests {

        @Mock LLMConfig.VertexConfig vertexConfig;

        @Test
        @DisplayName("throws LLMException when vertex project ID is empty")
        void throws_whenVertexProjectIdEmpty() {
            when(config.provider()).thenReturn(Optional.of("vertex-ai-anthropic"));
            when(config.vertex()).thenReturn(vertexConfig);
            when(vertexConfig.projectId()).thenReturn(Optional.empty());
            assertThatThrownBy(() -> factory.chatModel())
                    .isInstanceOf(LLMException.class);
        }

        @Test
        @DisplayName("throws LLMException when vertex project ID is blank")
        void throws_whenVertexProjectIdBlank() {
            when(config.provider()).thenReturn(Optional.of("vertex-ai-anthropic"));
            when(config.vertex()).thenReturn(vertexConfig);
            when(vertexConfig.projectId()).thenReturn(Optional.of("  "));
            assertThatThrownBy(() -> factory.chatModel())
                    .isInstanceOf(LLMException.class);
        }
    }
}
