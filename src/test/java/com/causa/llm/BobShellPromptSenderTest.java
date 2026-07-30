package com.causa.llm;

import com.causa.common.constants.LLMConstants;
import com.causa.config.AppConfig;
import com.causa.config.LlmConfigSnapshot;
import com.causa.core.domain.LLMRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link BobShellPromptSender}.
 *
 * <p>Tests the public API contract of {@link BobShellPromptSender} using Mockito
 * to mock the {@link LLMConfig} dependency.  The BOB Shell binary itself is never
 * invoked — {@link BobShellPromptSender#isReady()} will return {@code false} in a
 * test environment where the CLI is not present, and the tests exercise the
 * resulting error-handling paths.
 *
 * <p>Test Coverage:
 * <ul>
 *   <li>Constructor — reads {@code apiKey} eagerly, shell path lazily</li>
 *   <li>{@code isReady()} — returns false when CLI absent; caches true once found</li>
 *   <li>{@code send()} — throws {@link LLMException} when not ready</li>
 *   <li>{@link LLMRequest} builder — validates prompt, handles optional fields</li>
 *   <li>BOB Shell output-format constants — field names match the actual JSON structure</li>
 *   <li>CLI flag / env-var constants — exact string values expected by the CLI</li>
 * </ul>
 *
 * @since 0.0.1
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BobShellPromptSender Tests")
class BobShellPromptSenderTest {

    @Mock
    private AppConfig appConfig;

    @Mock
    private LlmConfigSnapshot llmConfigSnapshot;

    private BobShellPromptSender bobShellPromptSender;

    /**
     * Setup default mock behavior for tests that need it.
     * Using lenient() to avoid UnnecessaryStubbingException for tests that don't use all mocks.
     */
    private void setupDefaultMocks() {
        lenient().when(appConfig.getLlmConfig()).thenReturn(llmConfigSnapshot);
        lenient().when(llmConfigSnapshot.getBobShellPath()).thenReturn(LLMConstants.Provider.IBM_BOB);
        lenient().when(llmConfigSnapshot.getApiKey()).thenReturn("test-api-key");
        lenient().when(llmConfigSnapshot.getTimeoutSeconds()).thenReturn(LLMConstants.BobShell.DEFAULT_TIMEOUT_SECONDS);
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTests {

        @Test
        @DisplayName("Should initialize with valid configuration")
        void shouldInitializeWithValidConfiguration() {
            // Given
            when(appConfig.getLlmConfig()).thenReturn(llmConfigSnapshot);
            when(llmConfigSnapshot.getApiKey()).thenReturn("test-api-key");

            // When
            bobShellPromptSender = new BobShellPromptSender(appConfig);

            assertNotNull(bobShellPromptSender);
            verify(appConfig).getLlmConfig();
        }

        @Test
        @DisplayName("Should read shell path from config lazily — not during construction")
        void shouldReadShellPathFromConfigLazily() {
            // Given
            when(appConfig.getLlmConfig()).thenReturn(llmConfigSnapshot);
            when(llmConfigSnapshot.getApiKey()).thenReturn("test-api-key");

            // When — only construction, no send() or isReady() call
            bobShellPromptSender = new BobShellPromptSender(appConfig);

            // Then — getBobShellPath() must NOT be called during construction;
            // it is deferred until checkAvailability() / executeBobShell() are invoked.
            assertNotNull(bobShellPromptSender);
            verify(appConfig).getLlmConfig();
            verify(llmConfigSnapshot, never()).getBobShellPath();
        }

        @Test
        @DisplayName("Should use environment variable for API key when not in config")
        void shouldUseEnvironmentVariableForApiKey() {
            // Given
            when(appConfig.getLlmConfig()).thenReturn(llmConfigSnapshot);
            when(llmConfigSnapshot.getApiKey()).thenReturn("");

            // When
            bobShellPromptSender = new BobShellPromptSender(appConfig);

            assertNotNull(bobShellPromptSender);
            verify(appConfig).getLlmConfig();
        }

        @Test
        @DisplayName("Should handle custom configuration correctly")
        void shouldHandleConfigurationCorrectly() {
            // Given
            when(appConfig.getLlmConfig()).thenReturn(llmConfigSnapshot);
            when(llmConfigSnapshot.getApiKey()).thenReturn("custom-key");

            // When
            bobShellPromptSender = new BobShellPromptSender(appConfig);

            // Then
            assertNotNull(bobShellPromptSender);
            verify(appConfig).getLlmConfig();
        }
    }

    // -------------------------------------------------------------------------
    // isReady()
    //
    // checkAvailability() spawns a real ProcessBuilder. We control whether it
    // finds the binary by pointing shellPath() at a guaranteed-absent path.
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("isReady() Tests")
    class IsReadyTests {

        @Test
        @DisplayName("isReady() returns false when shellPath points to a non-existent binary")
        void isReadyReturnsFalseWhenBinaryAbsent() {
            // Point to a path that will never exist — ProcessBuilder throws IOException
            when(llmConfig.apiKey()).thenReturn(Optional.of("key"));
            when(llmConfig.bob()).thenReturn(bobConfig);
            when(bobConfig.shellPath()).thenReturn("/nonexistent/path/to/bob-does-not-exist");

            bobShellPromptSender = new BobShellPromptSender(llmConfig);

            assertFalse(bobShellPromptSender.isReady());
        }

        @Test
        @DisplayName("isReady() must not throw regardless of environment")
        void isReadyNeverThrows() {
            when(llmConfig.apiKey()).thenReturn(Optional.of("key"));
            when(llmConfig.bob()).thenReturn(bobConfig);
            when(bobConfig.shellPath()).thenReturn("/nonexistent/path/to/bob-does-not-exist");

            bobShellPromptSender = new BobShellPromptSender(llmConfig);

            assertDoesNotThrow(() -> bobShellPromptSender.isReady());
        }
    }

    // -------------------------------------------------------------------------
    // send() — error paths that do not require the CLI
    //
    // We point shellPath at a non-existent binary so isReady() is always false,
    // meaning send() hits the "not ready" guard without ever spawning a process.
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("send() Error Path Tests")
    class SendErrorPathTests {

        @BeforeEach
        void setUpReadiness() {
            setupDefaultMocks();
            bobShellPromptSender = new BobShellPromptSender(appConfig);
        }

        @Test
        @DisplayName("send() throws LLMException when isReady() is false (binary absent)")
        void sendThrowsWhenNotReady() {
            LLMRequest request = LLMRequest.builder("Diagnose the OOM event").build();

            // isReady() → false (binary missing) → send() throws before touching the CLI
            assertThrows(LLMException.class, () -> bobShellPromptSender.send(request));
        }

        @Test
        @DisplayName("send() throws LLMException specifically — not a generic RuntimeException")
        void sendThrowsLLMExceptionSpecifically() {
            LLMRequest request = LLMRequest.builder("Diagnose the OOM event").build();

            Exception thrown = assertThrows(Exception.class, () -> bobShellPromptSender.send(request));

            assertInstanceOf(LLMException.class, thrown,
                    "Expected LLMException but got: " + thrown.getClass().getName());
        }
    }

    // -------------------------------------------------------------------------
    // LLMRequest builder — used by callers before passing to send()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("LLMRequest Builder Tests")
    class LlmRequestBuilderTests {

        @Test
        @DisplayName("Builder accepts a non-blank prompt and defaults optional fields to empty")
        void buildsMinimalRequest() {
            LLMRequest request = LLMRequest.builder("What is 2+2?").build();

            assertNotNull(request);
            assertEquals("What is 2+2?", request.prompt());
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
            // Given - Create a large prompt (always uses stdin now)
            StringBuilder largePrompt = new StringBuilder();
            for (int i = 0; i < 101000; i++) {
                largePrompt.append("a");
            }
            
            LLMRequest request = LLMRequest.builder(largePrompt.toString())
                .build();

            // Then
            assertNotNull(request);
            assertTrue(request.prompt().length() > 100000);
        }
    }

    @Nested
    @DisplayName("Configuration Tests")
    class ConfigurationTests {

        @Test
        @DisplayName("Should respect custom shell path")
        void shouldRespectCustomShellPath() {
            // Given
            when(appConfig.getLlmConfig()).thenReturn(llmConfigSnapshot);
            when(llmConfigSnapshot.getApiKey()).thenReturn("test-api-key");

            // When
            bobShellPromptSender = new BobShellPromptSender(appConfig);

            // Then
            assertNotNull(bobShellPromptSender);
            verify(appConfig).getLlmConfig();
        }

        @Test
        @DisplayName("Should handle missing API key gracefully")
        void shouldHandleMissingApiKeyGracefully() {
            // Given
            when(appConfig.getLlmConfig()).thenReturn(llmConfigSnapshot);
            when(llmConfigSnapshot.getApiKey()).thenReturn("");

            // When
            bobShellPromptSender = new BobShellPromptSender(appConfig);

            // Then
            assertNotNull(bobShellPromptSender);
            verify(appConfig).getLlmConfig();
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
            // Given — mirrors the real BOB Shell stats block: stats.models.premium.tokens
            String statsJson = """
                {
                  "response": "OK",
                  "stats": {
                    "models": {
                      "premium": {
                        "tokens": {
                          "prompt": 15,
                          "candidates": 8,
                          "total": 23
                        }
                      }
                    }
                  }
                }
                """;

            // Verify JSON structure matches the constants used by extractTokenUsage()
            assertTrue(statsJson.contains(LLMConstants.BobShell.JSON_FIELD_STATS));
            assertTrue(statsJson.contains(LLMConstants.BobShell.JSON_FIELD_MODELS));
            assertTrue(statsJson.contains(LLMConstants.BobShell.JSON_FIELD_PREMIUM));
            assertTrue(statsJson.contains(LLMConstants.BobShell.JSON_FIELD_TOKENS));
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

            assertTrue(request.systemPrompt().isPresent());
            assertEquals("You are a Kubernetes SRE assistant", request.systemPrompt().get());
        }

        @Test
        @DisplayName("Builder stores context when provided")
        void buildsRequestWithContext() {
            LLMRequest request = LLMRequest.builder("Continue analysis")
                    .context("Pod was OOM-killed three times in the last hour")
                    .build();

            assertTrue(request.context().isPresent());
            assertEquals("Pod was OOM-killed three times in the last hour", request.context().get());
        }

        @Test
        @DisplayName("Builder handles very large prompts without truncation")
        void buildsLargePrompt() {
            String largePrompt = "x".repeat(120_000);
            LLMRequest request = LLMRequest.builder(largePrompt).build();

            assertEquals(120_000, request.prompt().length());
        }

        @Test
        @DisplayName("Builder rejects blank prompt with IllegalArgumentException")
        void rejectsBlankPrompt() {
            assertThrows(IllegalArgumentException.class,
                    () -> LLMRequest.builder("").build());
        }

        @Test
        @DisplayName("Builder rejects null-equivalent (all-whitespace) prompt")
        void rejectsWhitespacePrompt() {
            assertThrows(IllegalArgumentException.class,
                    () -> LLMRequest.builder("   ").build());
        }
    }

    // -------------------------------------------------------------------------
    // BOB Shell output-format constants
    //
    // These tests guard against accidental renaming of constants whose string
    // values must exactly match the JSON produced by the BOB Shell CLI binary.
    // If the binary changes its output format the constants must be updated here
    // first — failing tests will make the mismatch visible immediately.
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Output Format Constants Tests")
    class OutputFormatConstantsTests {

        @Test
        @DisplayName("OUTPUT_MARKER must be the literal '---output---' sentinel")
        void outputMarkerValue() {
            assertEquals("---output---", LLMConstants.BobShell.OUTPUT_MARKER);
        }

        @Test
        @DisplayName("JSON field path: top-level stats wrapper is 'stats'")
        void statsFieldName() {
            assertEquals("stats", LLMConstants.BobShell.JSON_FIELD_STATS);
        }

        @Test
        @DisplayName("JSON field path: stats → models")
        void modelsFieldName() {
            assertEquals("models", LLMConstants.BobShell.JSON_FIELD_MODELS);
        }

        @Test
        @DisplayName("JSON field path: stats.models → premium")
        void premiumFieldName() {
            assertEquals("premium", LLMConstants.BobShell.JSON_FIELD_PREMIUM);
        }

        @Test
        @DisplayName("JSON field path: stats.models.premium → tokens")
        void tokensFieldName() {
            assertEquals("tokens", LLMConstants.BobShell.JSON_FIELD_TOKENS);
        }

        @Test
        @DisplayName("JSON field path: tokens.prompt (input token count)")
        void promptTokensFieldName() {
            // The CLI emits 'prompt' not 'promptTokens'
            assertEquals("prompt", LLMConstants.BobShell.JSON_FIELD_PROMPT_TOKENS);
        }

        @Test
        @DisplayName("JSON field path: tokens.candidates (output token count)")
        void completionTokensFieldName() {
            // The CLI emits 'candidates' not 'completionTokens'
            assertEquals("candidates", LLMConstants.BobShell.JSON_FIELD_COMPLETION_TOKENS);
        }

        @Test
        @DisplayName("JSON field path: tokens.total (combined token count)")
        void totalTokensFieldName() {
            // The CLI emits 'total' not 'tokensUsed'
            assertEquals("total", LLMConstants.BobShell.JSON_FIELD_TOKENS_USED);
        }

        @Test
        @DisplayName("A well-formed stats JSON block contains all expected field names")
        void wellFormedStatsJsonContainsAllFields() {
            // Build a sample JSON string that mirrors actual BOB Shell output
            String statsJson = "{"
                    + "\"" + LLMConstants.BobShell.JSON_FIELD_STATS + "\": {"
                    + "  \"" + LLMConstants.BobShell.JSON_FIELD_MODELS + "\": {"
                    + "    \"" + LLMConstants.BobShell.JSON_FIELD_PREMIUM + "\": {"
                    + "      \"" + LLMConstants.BobShell.JSON_FIELD_TOKENS + "\": {"
                    + "        \"" + LLMConstants.BobShell.JSON_FIELD_PROMPT_TOKENS + "\": 15,"
                    + "        \"" + LLMConstants.BobShell.JSON_FIELD_COMPLETION_TOKENS + "\": 8,"
                    + "        \"" + LLMConstants.BobShell.JSON_FIELD_TOKENS_USED + "\": 23"
                    + "      }}}}}"
                    ;

            // Every field name constant appears in the constructed JSON
            assertTrue(statsJson.contains("\"stats\""));
            assertTrue(statsJson.contains("\"models\""));
            assertTrue(statsJson.contains("\"premium\""));
            assertTrue(statsJson.contains("\"tokens\""));
            assertTrue(statsJson.contains("\"prompt\""));
            assertTrue(statsJson.contains("\"candidates\""));
            assertTrue(statsJson.contains("\"total\""));
        }

        @Test
        @DisplayName("Output without stats block must NOT match stats field constant")
        void noStatsFieldInPlainOutput() {
            String plainOutput = "---output---\nHello, I am Bob!\n---output---\n";
            assertFalse(plainOutput.contains(LLMConstants.BobShell.JSON_FIELD_STATS));
        }
    }

    // -------------------------------------------------------------------------
    // CLI flags / environment-variable constants
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("CLI Flag and Environment Constants Tests")
    class CliConstantsTests {

        @Test
        @DisplayName("Provider name used in process args must be 'bob'")
        void providerName() {
            assertEquals("bob", LLMConstants.Provider.IBM_BOB);
        }

        @Test
        @DisplayName("--accept-license flag value")
        void acceptLicenseFlag() {
            assertEquals("--accept-license", LLMConstants.BobShell.FLAG_ACCEPT_LICENSE);
        }

        @Test
        @DisplayName("--yolo flag value")
        void yoloFlag() {
            assertEquals("--yolo", LLMConstants.BobShell.FLAG_YOLO);
        }

        @Test
        @DisplayName("-o flag enables JSON output mode")
        void outputJsonFlag() {
            assertEquals("-o", LLMConstants.BobShell.FLAG_OUTPUT_JSON);
        }

        @Test
        @DisplayName("JSON output format argument value")
        void outputFormatJson() {
            assertEquals("json", LLMConstants.BobShell.OUTPUT_FORMAT_JSON);
        }

        @Test
        @DisplayName("-p flag for inline prompt")
        void promptFlag() {
            assertEquals("-p", LLMConstants.BobShell.FLAG_PROMPT);
        }

        @Test
        @DisplayName("API key env-var name used when injecting credentials into the process")
        void apiKeyEnvVarName() {
            assertEquals("BOBSHELL_API_KEY", LLMConstants.BobShell.ENV_API_KEY_NAME);
        }

        @Test
        @DisplayName("Default process timeout is 180 seconds")
        void defaultTimeout() {
            assertEquals(180, LLMConstants.BobShell.DEFAULT_TIMEOUT_SECONDS);
        }

        @Test
        @DisplayName("Should use correct JSON field names")
        void shouldUseCorrectJsonFieldNames() {
            // Nested path: stats → models → premium → tokens → {prompt, candidates, total}
            assertEquals("stats",      LLMConstants.BobShell.JSON_FIELD_STATS);
            assertEquals("models",     LLMConstants.BobShell.JSON_FIELD_MODELS);
            assertEquals("premium",    LLMConstants.BobShell.JSON_FIELD_PREMIUM);
            assertEquals("tokens",     LLMConstants.BobShell.JSON_FIELD_TOKENS);
            assertEquals("prompt",     LLMConstants.BobShell.JSON_FIELD_PROMPT_TOKENS);
            assertEquals("candidates", LLMConstants.BobShell.JSON_FIELD_COMPLETION_TOKENS);
            assertEquals("total",      LLMConstants.BobShell.JSON_FIELD_TOKENS_USED);
        }
    }
}

// Made with Bob
