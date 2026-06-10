package com.causa.core.domain;

/**
 * LLM Response
 *
 * <p>Immutable domain model representing a response from an LLM provider.
 * Includes token usage and latency metrics for observability.
 *
 * @param responseText The generated text response
 * @param modelUsed The actual model that processed the request
 * @param inputTokens Number of input tokens consumed
 * @param outputTokens Number of output tokens generated
 * @param cacheCreationTokens Tokens written to cache (0 if caching not used)
 * @param cacheReadTokens Tokens read from cache (0 if no cache hit)
 * @param latencyMs End-to-end latency in milliseconds
 * @since 0.0.1
 */
public record LLMResponse(
    String responseText,
    String modelUsed,
    long inputTokens,
    long outputTokens,
    long cacheCreationTokens,
    long cacheReadTokens,
    long latencyMs
) {
    /**
     * Returns true if any tokens were served from cache.
     *
     * @return true if cache was hit, false otherwise
     */
    public boolean wasCacheHit() {
        return cacheReadTokens > 0;
    }

    /**
     * Returns the total input tokens (standard + cache creation + cache read).
     *
     * @return total input tokens
     */
    public long totalInputTokens() {
        return inputTokens + cacheCreationTokens + cacheReadTokens;
    }
}
