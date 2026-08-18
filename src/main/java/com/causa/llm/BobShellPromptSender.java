package com.causa.llm;

import com.causa.common.constants.LLMConstants;
import com.causa.common.exceptions.LLMException;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.config.AppConfig;
import com.causa.core.domain.LLMRequest;
import com.causa.core.domain.LLMResponse;
import com.causa.core.ports.llm.PromptSender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * BOB Shell Prompt Sender
 *
 * <p>Implementation of {@link PromptSender} that executes IBM's BOB Shell CLI tool
 * directly via ProcessBuilder. This provides native BOB integration without requiring
 * a separate wrapper service.
 *
 * <p>BOB Shell is a Node.js CLI tool bundled with the application image.
 *
 * <p>Requires BOB_API_KEY environment variable for authentication (Bob v2+).
 *
 * <p>Invocation: {@code bob run --format json} (prompt written to stdin)
 *
 * <p>This is a plain class (not a CDI bean) instantiated by {@link UnifiedPromptSender}.
 *
 * @since 0.0.1
 */
public class BobShellPromptSender implements PromptSender {

    private static final CausaLogger log = CausaLogger.getLogger(BobShellPromptSender.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final AppConfig appConfig;
    private final AtomicBoolean ready = new AtomicBoolean(false);

    public BobShellPromptSender(AppConfig appConfig) {
        this.appConfig = appConfig;
        if (appConfig.getLlmConfig().getApiKey().isBlank()) {
            log.warn(LogMessages.LLM.MISSING_CONFIGURATION)
                .field(LLMConstants.ConfigKeys.MISSING_CONFIG, LLMConstants.ConfigKeys.LLM_API_KEY)
                .log();
        }
    }

    @Override
    public LLMResponse send(LLMRequest request) {
        if (!isReady()) {
            log.error(LogMessages.LLM.MODEL_NOT_AVAILABLE)
                .field(LLMConstants.Fields.PROVIDER, LLMConstants.Provider.IBM_BOB)
                .log();
            throw new LLMException(
                LLMConstants.ErrorMessages.MODEL_NOT_AVAILABLE,
                LLMConstants.ErrorTypes.MODEL_NOT_READY
            );
        }

        log.info(LogMessages.LLM.PROMPT_SEND_START)
            .field(LLMConstants.Fields.PROVIDER, LLMConstants.Provider.IBM_BOB)
            .field(LLMConstants.Fields.MODEL, LLMConstants.Provider.IBM_BOB)
            .log();

        long startNanos = System.nanoTime();

        try {
            // Build complete prompt from request
            String prompt = buildPrompt(request);

            // Execute BOB Shell
            String bobOutput = executeBobShell(prompt);

            // Parse response
            String responseText = extractContent(bobOutput);
            TokenUsage tokenUsage = extractTokenUsage(bobOutput);

            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;

            LLMResponse llmResponse = new LLMResponse(
                responseText,
                LLMConstants.Provider.IBM_BOB,
                tokenUsage.promptTokens,
                tokenUsage.completionTokens,
                0, // BOB doesn't support cache creation tokens
                0, // BOB doesn't support cache read tokens
                latencyMs
            );

            log.info(LogMessages.LLM.PROMPT_SEND_SUCCESS)
                .field(LLMConstants.Fields.MODEL, llmResponse.modelUsed())
                .field(LLMConstants.Fields.INPUT_TOKENS, llmResponse.inputTokens())
                .field(LLMConstants.Fields.OUTPUT_TOKENS, llmResponse.outputTokens())
                .field(LLMConstants.Fields.LATENCY_MS, llmResponse.latencyMs())
                .log();

            return llmResponse;

        } catch (Exception e) {
            long latencyMs = (System.nanoTime() - startNanos) / 1_000_000;
            log.error(LogMessages.LLM.LLM_ERROR)
                .field(LLMConstants.Fields.ERROR_TYPE, e.getClass().getSimpleName())
                .field(LLMConstants.Fields.LATENCY_MS, latencyMs)
                .exception(e)
                .log();
            throw new LLMException(
                String.format(LLMConstants.ErrorMessages.REQUEST_FAILED_TEMPLATE, e.getMessage()),
                LLMConstants.ErrorTypes.LLM_REQUEST_FAILED,
                e
            );
        }
    }

    @Override
    public boolean isReady() {
        // If already marked as ready, return immediately
        if (ready.get()) {
            return true;
        }

        // Otherwise, check availability and cache the result
        boolean available = checkAvailability();
        if (available) {
            ready.set(true);
        }
        return available;
    }

    /**
     * Checks if BOB Shell is available by running 'bob --version'.
     *
     * @return true if BOB Shell is available and executable
     */
    private boolean checkAvailability() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                appConfig.getLlmConfig().getBobShellPath(),
                LLMConstants.BobShell.VERSION_FLAG
            );

            String apiKey = appConfig.getLlmConfig().getApiKey().trim();
            if (!apiKey.isBlank()) {
                pb.environment().put(LLMConstants.BobShell.ENV_API_KEY_NAME, apiKey);
            }

            Process process = pb.start();
            boolean completed = process.waitFor(
                LLMConstants.BobShell.VERSION_CHECK_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            );

            if (!completed) {
                process.destroyForcibly();
                log.warn(LogMessages.LLM.BOB_VERSION_CHECK_TIMEOUT).log();
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode == 0) {
                log.info(LogMessages.LLM.BOB_SHELL_AVAILABLE)
                    .field(LLMConstants.BobShell.LOG_FIELD_SHELL_PATH, appConfig.getLlmConfig().getBobShellPath())
                    .log();
                return true;
            } else {
                log.warn(LogMessages.LLM.BOB_SHELL_NOT_AVAILABLE)
                    .field(LLMConstants.BobShell.LOG_FIELD_EXIT_CODE, exitCode)
                    .log();
                return false;
            }
        } catch (IOException | InterruptedException e) {
            log.warn(LogMessages.LLM.BOB_AVAILABILITY_CHECK_FAILED)
                .exception(e)
                .log();
            return false;
        }
    }

    /**
     * Builds the complete prompt from LLMRequest.
     */
    private String buildPrompt(LLMRequest request) {
        StringBuilder prompt = new StringBuilder();

        // Add system prompt and context if present
        if (request.systemPrompt().isPresent() || request.context().isPresent()) {
            request.systemPrompt().ifPresent(sys -> prompt.append(sys).append(System.lineSeparator()).append(System.lineSeparator()));
            request.context().ifPresent(ctx -> prompt.append(ctx).append(System.lineSeparator()).append(System.lineSeparator()));
        }

        // Add user prompt
        prompt.append(request.prompt());

        return prompt.toString();
    }

    /**
     * Executes BOB Shell CLI using {@code bob run --format json}.
     *
     * <p>Prompt is written to stdin to avoid OS ARG_MAX limits on large prompts.
     */
    private String executeBobShell(String prompt) throws LLMException, InterruptedException {
        try {
            // Fail fast — there is no point spawning a process without a valid API key
            String apiKey = appConfig.getLlmConfig().getApiKey().trim();
            if (apiKey.isBlank()) {
                throw new LLMException(
                    LLMConstants.ErrorMessages.API_KEY_REQUIRED + LLMConstants.Provider.IBM_BOB,
                    LLMConstants.ErrorTypes.MISSING_CONFIGURATION
                );
            }

            ProcessBuilder pb = new ProcessBuilder(
                appConfig.getLlmConfig().getBobShellPath(),
                LLMConstants.BobShell.FLAG_ACCEPT_LICENSE,
                LLMConstants.BobShell.SUBCMD_RUN,
                LLMConstants.BobShell.FLAG_FORMAT,
                LLMConstants.BobShell.OUTPUT_FORMAT_JSON
            );

            pb.environment().put(LLMConstants.BobShell.ENV_API_KEY_NAME, apiKey);

            pb.redirectErrorStream(true);

            Process process = pb.start();

            // Write prompt to stdin
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    process.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(prompt);
            }

            // Wait for completion with timeout
            int timeoutSeconds = appConfig.getLlmConfig().getTimeoutSeconds();
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

            if (!completed) {
                process.destroyForcibly();
                throw new LLMException(
                    String.format(LogMessages.LLM.BOB_TIMEOUT_TEMPLATE, timeoutSeconds),
                    LLMConstants.ErrorTypes.LLM_REQUEST_FAILED
                );
            }

            // Read output
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
            }

            int exitCode = process.exitValue();
            String responseText = output.toString().trim();

            if (exitCode != 0) {
                log.error(LogMessages.LLM.BOB_SHELL_FAILED)
                    .field(LLMConstants.BobShell.LOG_FIELD_EXIT_CODE, exitCode)
                    .field(LLMConstants.BobShell.LOG_FIELD_OUTPUT, responseText.length() > LLMConstants.BobShell.OUTPUT_TRUNCATE_LENGTH ?
                        responseText.substring(0, LLMConstants.BobShell.OUTPUT_TRUNCATE_LENGTH) : responseText)
                    .log();
                throw new LLMException(
                    String.format(LogMessages.LLM.BOB_EXIT_CODE_TEMPLATE, exitCode),
                    LLMConstants.ErrorTypes.LLM_REQUEST_FAILED
                );
            }

            if (responseText.isEmpty()) {
                throw new LLMException(
                    LogMessages.LLM.BOB_EMPTY_RESPONSE,
                    LLMConstants.ErrorTypes.LLM_REQUEST_FAILED
                );
            }

            return responseText;
        } catch (IOException e) {
            throw new LLMException(
                String.format(LLMConstants.ErrorMessages.REQUEST_FAILED_TEMPLATE, e.getMessage()),
                LLMConstants.ErrorTypes.LLM_REQUEST_FAILED,
                e
            );
        }
    }

    /**
     * Extracts the response text from {@code bob run --format json} output.
     *
     * <p>Bob v2 JSON shape:
     * <pre>
     * {
     *   "type": "result",
     *   "status": "success",
     *   "last_message": "actual response text here",
     *   "stats": { "task_id": "...", "duration_ms": N, "session_costs": N, "tool_calls": N }
     * }
     * </pre>
     */
    private String extractContent(String bobOutput) {
        try {
            JsonNode root = objectMapper.readTree(bobOutput.trim());
            JsonNode lastMessage = root.path(LLMConstants.BobShell.JSON_FIELD_LAST_MESSAGE);
            if (!lastMessage.isMissingNode() && !lastMessage.isNull()) {
                return lastMessage.asText().trim();
            }
        } catch (Exception e) {
            log.warn(LogMessages.LLM.BOB_OUTPUT_MARKERS_NOT_FOUND).log();
        }
        // Fallback: return raw output if JSON parsing fails
        return bobOutput;
    }

    /**
     * Extracts token usage from {@code bob run --format json} output.
     *
     * <p>Bob v2 does not expose per-token counts in the {@code --format json} output;
     * only {@code session_costs} and {@code duration_ms} are available under {@code stats}.
     * Token counts are returned as zero — cost tracking is not affected.
     */
    private TokenUsage extractTokenUsage(String bobOutput) {
        try {
            JsonNode root = objectMapper.readTree(bobOutput.trim());
            JsonNode stats = root.path(LLMConstants.BobShell.JSON_FIELD_STATS);
            if (!stats.isMissingNode()) {
                // Bob v2 does not expose per-token counts; return zeros
                return new TokenUsage(0, 0, 0);
            }
        } catch (Exception e) {
            log.warn(LogMessages.LLM.BOB_TOKEN_PARSE_FAILED).exception(e).log();
        }
        return new TokenUsage(0, 0, 0);
    }

    /**
     * Token usage data class.
     */
    private static class TokenUsage {
        final long promptTokens;
        final long completionTokens;
        final long totalTokens;

        TokenUsage(long promptTokens, long completionTokens, long totalTokens) {
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
        }
    }
}
