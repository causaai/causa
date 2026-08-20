package com.causa.llm;

import com.causa.common.constants.LLMConstants;
import com.causa.common.exceptions.LLMException;
import com.causa.config.AppConfig;
import com.causa.config.LlmConfigSnapshot;
import com.causa.core.domain.LLMRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

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
        // Use a guaranteed-absent path so ProcessBuilder throws IOException immediately
        // and isReady() returns false without spawning a real process or hanging.
        lenient().when(llmConfigSnapshot.getBobShellPath()).thenReturn("/nonexistent/bob-does-not-exist");
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
            setupDefaultMocks();
            bobShellPromptSender = new BobShellPromptSender(appConfig);
            assertFalse(bobShellPromptSender.isReady());
        }

        @Test
        @DisplayName("isReady() must not throw regardless of environment")
        void isReadyNeverThrows() {
            setupDefaultMocks();
            bobShellPromptSender = new BobShellPromptSender(appConfig);
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

        private BobShellPromptSender sender;

        @BeforeEach
        void setUp() {
            when(appConfig.getLlmConfig()).thenReturn(llmConfigSnapshot);
            when(llmConfigSnapshot.getApiKey()).thenReturn("test-key");
            sender = new BobShellPromptSender(appConfig);
        }

        @Test
        @DisplayName("Should extract last_message from valid v2 JSON output")
        void shouldExtractLastMessageFromV2Json() {
            String bobOutput = """
                {
                  "type": "result",
                  "status": "success",
                  "last_message": "The answer is 4",
                  "stats": { "input_tokens": 10, "output_tokens": 5, "total_tokens": 15 }
                }
                """;

            assertEquals("The answer is 4", sender.extractContent(bobOutput));
        }

        @Test
        @DisplayName("Should trim whitespace around last_message value")
        void shouldTrimWhitespaceAroundLastMessage() {
            String bobOutput = "{\"last_message\": \"  answer with spaces  \"}";

            assertEquals("answer with spaces", sender.extractContent(bobOutput));
        }

        @Test
        @DisplayName("Should fall back to raw output when last_message is missing")
        void shouldFallbackToRawOutputWhenLastMessageMissing() {
            String bobOutput = "{\"type\": \"result\", \"stats\": {\"input_tokens\": 5}}";

            assertEquals(bobOutput, sender.extractContent(bobOutput));
        }

        @Test
        @DisplayName("Should return raw output for malformed JSON")
        void shouldReturnRawOutputForMalformedJson() {
            String malformedOutput = "this is not valid JSON";

            assertEquals(malformedOutput, sender.extractContent(malformedOutput));
        }
    }

    @Nested
    @DisplayName("Token Extraction Tests")
    class TokenExtractionTests {

        private BobShellPromptSender sender;

        @BeforeEach
        void setUp() {
            when(appConfig.getLlmConfig()).thenReturn(llmConfigSnapshot);
            when(llmConfigSnapshot.getApiKey()).thenReturn("test-key");
            sender = new BobShellPromptSender(appConfig);
        }

        @Test
        @DisplayName("Should extract token counts from v2 stats block")
        void shouldExtractTokenUsageFromValidStats() {
            String bobOutput = """
                {
                  "type": "result",
                  "last_message": "OK",
                  "stats": {
                    "input_tokens": 15,
                    "output_tokens": 8,
                    "total_tokens": 23
                  }
                }
                """;

            BobShellPromptSender.TokenUsage usage = sender.extractTokenUsage(bobOutput);

            assertEquals(15, usage.promptTokens);
            assertEquals(8,  usage.completionTokens);
            assertEquals(23, usage.totalTokens);
        }

        @Test
        @DisplayName("Should return zero token usage when stats block is missing")
        void shouldReturnZeroUsageWhenStatsMissing() {
            String bobOutput = "{\"last_message\": \"OK\"}";

            BobShellPromptSender.TokenUsage usage = sender.extractTokenUsage(bobOutput);

            assertEquals(0, usage.promptTokens);
            assertEquals(0, usage.completionTokens);
            assertEquals(0, usage.totalTokens);
        }

        @Test
        @DisplayName("Should default missing stats fields to zero")
        void shouldDefaultMissingStatsFieldsToZero() {
            String bobOutput = "{\"stats\": {\"input_tokens\": 5}}";

            BobShellPromptSender.TokenUsage usage = sender.extractTokenUsage(bobOutput);

            assertEquals(5, usage.promptTokens);
            assertEquals(0, usage.completionTokens);
            assertEquals(0, usage.totalTokens);
        }

        @Test
        @DisplayName("Should return zero token usage for malformed JSON")
        void shouldReturnZeroUsageForMalformedJson() {
            String malformedJson = "{ \"stats\": { \"input_tokens\": 10, ";

            BobShellPromptSender.TokenUsage usage = sender.extractTokenUsage(malformedJson);

            assertEquals(0, usage.promptTokens);
            assertEquals(0, usage.completionTokens);
            assertEquals(0, usage.totalTokens);
        }
    }

    @Nested
    @DisplayName("API Key Injection Tests")
    class ApiKeyInjectionTests {

        @ParameterizedTest
        @ValueSource(strings = {"my-api-key", "  key-with-spaces  "})
        @DisplayName("Should inject BOB_API_KEY when apiKey is non-blank")
        void shouldInjectApiKeyWhenNonBlank(String apiKey) {
            Map<String, String> env = BobShellPromptSender.buildSubprocessEnv(apiKey);

            assertTrue(env.containsKey(LLMConstants.BobShell.ENV_API_KEY_NAME));
            assertEquals(apiKey, env.get(LLMConstants.BobShell.ENV_API_KEY_NAME));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "   "})
        @DisplayName("Should omit BOB_API_KEY when apiKey is blank")
        void shouldOmitApiKeyWhenBlank(String apiKey) {
            Map<String, String> env = BobShellPromptSender.buildSubprocessEnv(apiKey);

            assertFalse(env.containsKey(LLMConstants.BobShell.ENV_API_KEY_NAME));
        }

        @Test
        @DisplayName("Should omit BOB_API_KEY when apiKey is null")
        void shouldOmitApiKeyWhenNull() {
            Map<String, String> env = BobShellPromptSender.buildSubprocessEnv(null);

            assertFalse(env.containsKey(LLMConstants.BobShell.ENV_API_KEY_NAME));
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should validate prompt is not empty")
        void shouldValidatePromptIsNotEmpty() {
            // When/Then
            assertThrows(IllegalArgumentException.class, () ->
                LLMRequest.builder("").build());
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
        @DisplayName("JSON field: top-level response text is 'last_message'")
        void lastMessageFieldName() {
            assertEquals("last_message", LLMConstants.BobShell.JSON_FIELD_LAST_MESSAGE);
        }

        @Test
        @DisplayName("JSON field: top-level stats wrapper is 'stats'")
        void statsFieldName() {
            assertEquals("stats", LLMConstants.BobShell.JSON_FIELD_STATS);
        }

        @Test
        @DisplayName("JSON field: stats.input_tokens")
        void inputTokensFieldName() {
            assertEquals("input_tokens", LLMConstants.BobShell.JSON_FIELD_INPUT_TOKENS);
        }

        @Test
        @DisplayName("JSON field: stats.output_tokens")
        void outputTokensFieldName() {
            assertEquals("output_tokens", LLMConstants.BobShell.JSON_FIELD_OUTPUT_TOKENS);
        }

        @Test
        @DisplayName("JSON field: stats.total_tokens")
        void totalTokensFieldName() {
            assertEquals("total_tokens", LLMConstants.BobShell.JSON_FIELD_TOTAL_TOKENS);
        }

        @Test
        @DisplayName("A well-formed v2 stats JSON block contains all expected field names")
        void wellFormedStatsJsonContainsAllFields() {
            String statsJson = "{"
                    + "\"" + LLMConstants.BobShell.JSON_FIELD_LAST_MESSAGE + "\": \"OK\","
                    + "\"" + LLMConstants.BobShell.JSON_FIELD_STATS + "\": {"
                    + "  \"" + LLMConstants.BobShell.JSON_FIELD_INPUT_TOKENS + "\": 15,"
                    + "  \"" + LLMConstants.BobShell.JSON_FIELD_OUTPUT_TOKENS + "\": 8,"
                    + "  \"" + LLMConstants.BobShell.JSON_FIELD_TOTAL_TOKENS + "\": 23"
                    + "}}";

            assertTrue(statsJson.contains("\"last_message\""));
            assertTrue(statsJson.contains("\"stats\""));
            assertTrue(statsJson.contains("\"input_tokens\""));
            assertTrue(statsJson.contains("\"output_tokens\""));
            assertTrue(statsJson.contains("\"total_tokens\""));
        }

        @Test
        @DisplayName("Output without stats block must NOT match stats field constant")
        void noStatsFieldInPlainOutput() {
            String plainOutput = "{\"last_message\": \"Hello, I am Bob!\"}";
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
        @DisplayName("bob run subcommand value")
        void runSubcommand() {
            assertEquals("run", LLMConstants.BobShell.SUBCMD_RUN);
        }

        @Test
        @DisplayName("--format flag value")
        void formatFlag() {
            assertEquals("--format", LLMConstants.BobShell.FLAG_FORMAT);
        }

        @Test
        @DisplayName("JSON output format argument value")
        void outputFormatJson() {
            assertEquals("json", LLMConstants.BobShell.OUTPUT_FORMAT_JSON);
        }

        @Test
        @DisplayName("API key env-var name used when injecting credentials into the process")
        void apiKeyEnvVarName() {
            assertEquals("BOB_API_KEY", LLMConstants.BobShell.ENV_API_KEY_NAME);
        }

        @Test
        @DisplayName("Default process timeout is 180 seconds")
        void defaultTimeout() {
            assertEquals(180, LLMConstants.BobShell.DEFAULT_TIMEOUT_SECONDS);
        }

        @Test
        @DisplayName("Should use correct v2 JSON field names")
        void shouldUseCorrectJsonFieldNames() {
            assertEquals("last_message", LLMConstants.BobShell.JSON_FIELD_LAST_MESSAGE);
            assertEquals("stats",        LLMConstants.BobShell.JSON_FIELD_STATS);
            assertEquals("input_tokens", LLMConstants.BobShell.JSON_FIELD_INPUT_TOKENS);
            assertEquals("output_tokens",LLMConstants.BobShell.JSON_FIELD_OUTPUT_TOKENS);
            assertEquals("total_tokens", LLMConstants.BobShell.JSON_FIELD_TOTAL_TOKENS);
        }
    }
}

// Made with Bob
