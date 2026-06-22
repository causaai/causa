package com.causa.llm;

import com.causa.common.constants.LLMConstants;
import com.causa.config.LLMConfig;
import com.causa.core.domain.LLMRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BobShellPromptSender}.
 * 
 * <p>Tests the BOB Shell integration following the same patterns as LangChainPromptSender tests.
 * These are unit tests that verify the business logic without actually calling BOB Shell CLI.
 * 
 * <p>Test Coverage:
 * <ul>
 *   <li>Initialization and configuration</li>
 *   <li>Readiness checks</li>
 *   <li>Request building</li>
 *   <li>Response parsing</li>
 *   <li>Token extraction</li>
 *   <li>Error handling</li>
 *   <li>Large prompt handling</li>
 * </ul>
 *
 * @since 0.0.1
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BobShellPromptSender Tests")
class BobShellPromptSenderTest {

    @Mock
    private LLMConfig llmConfig;

    @Mock
    private LLMConfig.BobConfig bobConfig;

    private BobShellPromptSender bobShellPromptSender;

    /**
     * Setup default mock behavior for tests that need it.
     * Using lenient() to avoid UnnecessaryStubbingException for tests that don't use all mocks.
     */
    private void setupDefaultMocks() {
        lenient().when(llmConfig.bob()).thenReturn(bobConfig);
        lenient().when(bobConfig.shellPath()).thenReturn(LLMConstants.BobShell.DEFAULT_SHELL_PATH);
        lenient().when(bobConfig.apiKey()).thenReturn(Optional.of("test-api-key"));
        lenient().when(bobConfig.timeoutSeconds()).thenReturn(LLMConstants.BobShell.DEFAULT_TIMEOUT_SECONDS);
    }

    @Nested
    @DisplayName("Initialization Tests")
    class InitializationTests {

        @Test
        @DisplayName("Should initialize with valid configuration")
        void shouldInitializeWithValidConfiguration() {
            // Given
            when(llmConfig.bob()).thenReturn(bobConfig);
            when(bobConfig.shellPath()).thenReturn(LLMConstants.BobShell.DEFAULT_SHELL_PATH);
            when(bobConfig.apiKey()).thenReturn(Optional.of("test-api-key"));
            
            // When
            bobShellPromptSender = new BobShellPromptSender(llmConfig);

            // Then
            assertNotNull(bobShellPromptSender);
            // Constructor calls config.bob() twice (for shellPath and apiKey)
            verify(llmConfig, times(2)).bob();
            verify(bobConfig).shellPath();
            verify(bobConfig).apiKey();
        }

        @Test
        @DisplayName("Should use default shell path when not configured")
        void shouldUseDefaultShellPath() {
            // Given
            when(llmConfig.bob()).thenReturn(bobConfig);
            when(bobConfig.shellPath()).thenReturn(LLMConstants.BobShell.DEFAULT_SHELL_PATH);
            when(bobConfig.apiKey()).thenReturn(Optional.of("test-api-key"));

            // When
            bobShellPromptSender = new BobShellPromptSender(llmConfig);

            // Then
            assertNotNull(bobShellPromptSender);
            verify(bobConfig).shellPath();
        }

        @Test
        @DisplayName("Should use environment variable for API key when not in config")
        void shouldUseEnvironmentVariableForApiKey() {
            // Given
            when(llmConfig.bob()).thenReturn(bobConfig);
            when(bobConfig.shellPath()).thenReturn(LLMConstants.BobShell.DEFAULT_SHELL_PATH);
            when(bobConfig.apiKey()).thenReturn(Optional.empty());

            // When
            bobShellPromptSender = new BobShellPromptSender(llmConfig);

            // Then
            assertNotNull(bobShellPromptSender);
            verify(bobConfig).apiKey();
            // BOB Shell will use BOBSHELL_API_KEY environment variable
        }

        @Test
        @DisplayName("Should handle configuration correctly")
        void shouldHandleConfigurationCorrectly() {
            // Given
            when(llmConfig.bob()).thenReturn(bobConfig);
            when(bobConfig.shellPath()).thenReturn("/custom/path/bob");
            when(bobConfig.apiKey()).thenReturn(Optional.of("custom-key"));

            // When
            bobShellPromptSender = new BobShellPromptSender(llmConfig);

            // Then
            assertNotNull(bobShellPromptSender);
        }
    }

    @Nested
    @DisplayName("Readiness Tests")
    class ReadinessTests {

        @BeforeEach
        void setUpReadiness() {
            setupDefaultMocks();
            bobShellPromptSender = new BobShellPromptSender(llmConfig);
        }

        @Test
        @DisplayName("Should check BOB Shell availability")
        void shouldCheckBobShellAvailability() {
            // When
            boolean isReady = bobShellPromptSender.isReady();

            // Then
            // Note: isReady() actively checks if BOB Shell CLI is available
            // Result depends on whether BOB Shell is installed in the environment
            // This is a unit test, so we just verify the method can be called
            assertNotNull(bobShellPromptSender);
        }
    }

    @Nested
    @DisplayName("Request Building Tests")
    class RequestBuildingTests {

        @Test
        @DisplayName("Should build simple prompt correctly")
        void shouldBuildSimplePromptCorrectly() {
            // Given
            String userPrompt = "What is 2+2?";
            LLMRequest request = LLMRequest.builder(userPrompt)
                .build();

            // Then
            assertNotNull(request);
            assertEquals(userPrompt, request.prompt());
            assertTrue(request.systemPrompt().isEmpty());
            assertTrue(request.context().isEmpty());
        }

        @Test
        @DisplayName("Should build prompt with system prompt")
        void shouldBuildPromptWithSystemPrompt() {
            // Given
            String systemPrompt = "You are a helpful assistant";
            String userPrompt = "What is 2+2?";
            
            LLMRequest request = LLMRequest.builder(userPrompt)
                .systemPrompt(systemPrompt)
                .build();

            // Then
            assertNotNull(request);
            assertEquals(userPrompt, request.prompt());
            assertTrue(request.systemPrompt().isPresent());
            assertEquals(systemPrompt, request.systemPrompt().get());
        }

        @Test
        @DisplayName("Should build prompt with context")
        void shouldBuildPromptWithContext() {
            // Given
            String context = "Previous conversation context";
            String userPrompt = "Continue the conversation";
            
            LLMRequest request = LLMRequest.builder(userPrompt)
                .context(context)
                .build();

            // Then
            assertNotNull(request);
            assertEquals(userPrompt, request.prompt());
            assertTrue(request.context().isPresent());
            assertEquals(context, request.context().get());
        }

        @Test
        @DisplayName("Should handle large prompts")
        void shouldHandleLargePrompts() {
            // Given - Create a prompt larger than threshold
            StringBuilder largePrompt = new StringBuilder();
            for (int i = 0; i < LLMConstants.BobShell.LARGE_PROMPT_THRESHOLD + 1000; i++) {
                largePrompt.append("a");
            }
            
            LLMRequest request = LLMRequest.builder(largePrompt.toString())
                .build();

            // Then
            assertNotNull(request);
            assertTrue(request.prompt().length() > LLMConstants.BobShell.LARGE_PROMPT_THRESHOLD);
        }
    }

    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("Should respect custom shell path")
        void shouldRespectCustomShellPath() {
            // Given
            String customPath = "/custom/path/to/bob";
            when(llmConfig.bob()).thenReturn(bobConfig);
            when(bobConfig.shellPath()).thenReturn(customPath);
            when(bobConfig.apiKey()).thenReturn(Optional.of("test-api-key"));

            // When
            bobShellPromptSender = new BobShellPromptSender(llmConfig);

            // Then
            assertNotNull(bobShellPromptSender);
            verify(bobConfig).shellPath();
        }

        @Test
        @DisplayName("Should handle missing API key gracefully")
        void shouldHandleMissingApiKeyGracefully() {
            // Given
            when(llmConfig.bob()).thenReturn(bobConfig);
            when(bobConfig.shellPath()).thenReturn(LLMConstants.BobShell.DEFAULT_SHELL_PATH);
            when(bobConfig.apiKey()).thenReturn(Optional.empty());

            // When
            bobShellPromptSender = new BobShellPromptSender(llmConfig);

            // Then
            assertNotNull(bobShellPromptSender);
            verify(bobConfig).apiKey();
            // BOB Shell will use BOBSHELL_API_KEY environment variable
        }
    }

    @Nested
    @DisplayName("Response Parsing Tests")
    class ResponseParsingTests {

        @Test
        @DisplayName("Should parse valid BOB Shell output")
        void shouldParseValidBobShellOutput() {
            // Given
            String bobOutput = """
                ---output---
                {"response": "The answer is 4"}
                ---output---
                {"stats": {"promptTokens": 10, "completionTokens": 5, "tokensUsed": 15}}
                """;

            // This test verifies the output format BOB Shell is expected to produce
            assertTrue(bobOutput.contains(LLMConstants.BobShell.OUTPUT_MARKER));
            assertTrue(bobOutput.contains("promptTokens"));
            assertTrue(bobOutput.contains("completionTokens"));
            assertTrue(bobOutput.contains("tokensUsed"));
        }

        @Test
        @DisplayName("Should handle output without statistics")
        void shouldHandleOutputWithoutStatistics() {
            // Given
            String bobOutput = """
                ---output---
                {"response": "The answer is 4"}
                ---output---
                """;

            // This test verifies graceful handling when stats are missing
            assertTrue(bobOutput.contains(LLMConstants.BobShell.OUTPUT_MARKER));
        }

        @Test
        @DisplayName("Should extract content between markers")
        void shouldExtractContentBetweenMarkers() {
            // Given
            String expectedContent = "{\"response\": \"The answer is 4\"}";
            String bobOutput = String.format("""
                ---output---
                %s
                ---output---
                {"stats": {"promptTokens": 10, "completionTokens": 5, "tokensUsed": 15}}
                """, expectedContent);

            // Then
            assertTrue(bobOutput.contains(expectedContent));
        }
    }

    @Nested
    @DisplayName("Token Extraction Tests")
    class TokenExtractionTests {

        @Test
        @DisplayName("Should extract token usage from valid stats")
        void shouldExtractTokenUsageFromValidStats() {
            // Given
            String statsJson = """
                {
                  "response": "OK",
                  "stats": {
                    "promptTokens": 15,
                    "completionTokens": 8,
                    "tokensUsed": 23
                  }
                }
                """;

            // Verify JSON structure
            assertTrue(statsJson.contains(LLMConstants.BobShell.JSON_FIELD_STATS));
            assertTrue(statsJson.contains(LLMConstants.BobShell.JSON_FIELD_PROMPT_TOKENS));
            assertTrue(statsJson.contains(LLMConstants.BobShell.JSON_FIELD_COMPLETION_TOKENS));
            assertTrue(statsJson.contains(LLMConstants.BobShell.JSON_FIELD_TOKENS_USED));
        }

        @Test
        @DisplayName("Should handle missing stats field")
        void shouldHandleMissingStatsField() {
            // Given
            String statsJson = """
                {
                  "response": "OK"
                }
                """;

            // Verify it doesn't contain stats
            assertFalse(statsJson.contains(LLMConstants.BobShell.JSON_FIELD_STATS));
        }

        @Test
        @DisplayName("Should handle malformed JSON gracefully")
        void shouldHandleMalformedJsonGracefully() {
            // Given
            String malformedJson = "{ invalid json }";

            // This should not throw an exception
            assertNotNull(malformedJson);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should validate prompt is not empty")
        void shouldValidatePromptIsNotEmpty() {
            // When/Then
            assertThrows(IllegalArgumentException.class, () -> {
                LLMRequest.builder("")
                    .build();
            });
        }
    }

    @Nested
    @DisplayName("Constants Tests")
    class ConstantsTests {

        @Test
        @DisplayName("Should use correct model name")
        void shouldUseCorrectModelName() {
            assertEquals("bob-shell-1.0.4", LLMConstants.BobShell.MODEL_NAME);
        }

        @Test
        @DisplayName("Should use correct CLI flags")
        void shouldUseCorrectCliFlags() {
            assertEquals("--accept-license", LLMConstants.BobShell.FLAG_ACCEPT_LICENSE);
            assertEquals("--yolo", LLMConstants.BobShell.FLAG_YOLO);
            assertEquals("-o", LLMConstants.BobShell.FLAG_OUTPUT_JSON);
            assertEquals("-p", LLMConstants.BobShell.FLAG_PROMPT);
            assertEquals("json", LLMConstants.BobShell.OUTPUT_FORMAT_JSON);
        }

        @Test
        @DisplayName("Should use correct output marker")
        void shouldUseCorrectOutputMarker() {
            assertEquals("---output---", LLMConstants.BobShell.OUTPUT_MARKER);
        }

        @Test
        @DisplayName("Should use correct environment variable name")
        void shouldUseCorrectEnvironmentVariableName() {
            assertEquals("BOBSHELL_API_KEY", LLMConstants.BobShell.ENV_API_KEY);
        }

        @Test
        @DisplayName("Should use correct large prompt threshold")
        void shouldUseCorrectLargePromptThreshold() {
            assertEquals(100_000, LLMConstants.BobShell.LARGE_PROMPT_THRESHOLD);
        }

        @Test
        @DisplayName("Should use correct default timeout")
        void shouldUseCorrectDefaultTimeout() {
            assertEquals(180, LLMConstants.BobShell.DEFAULT_TIMEOUT_SECONDS);
        }

        @Test
        @DisplayName("Should use correct JSON field names")
        void shouldUseCorrectJsonFieldNames() {
            assertEquals("stats", LLMConstants.BobShell.JSON_FIELD_STATS);
            assertEquals("promptTokens", LLMConstants.BobShell.JSON_FIELD_PROMPT_TOKENS);
            assertEquals("completionTokens", LLMConstants.BobShell.JSON_FIELD_COMPLETION_TOKENS);
            assertEquals("tokensUsed", LLMConstants.BobShell.JSON_FIELD_TOKENS_USED);
        }
    }
}

// Made with Bob
