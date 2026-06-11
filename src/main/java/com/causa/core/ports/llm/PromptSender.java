package com.causa.core.ports.llm;

import com.causa.core.domain.LLMRequest;
import com.causa.core.domain.LLMResponse;

/**
 * Prompt Sender Port
 *
 * <p>Port interface for sending prompts to an LLM provider. This is the primary
 * contract between the business logic layer and LLM integration adapters.
 *
 * <p>Implementations are secondary (outbound) adapters in the hexagonal architecture.
 * Business logic in {@code core.services} depends only on this interface, never on
 * a specific LLM SDK or provider.
 *
 * @since 0.0.1
 */
public interface PromptSender {

    /**
     * Sends a prompt to the LLM and returns the response.
     *
     * @param request the LLM request containing prompt, system instructions, and parameters
     * @return the LLM response with text, token usage, and latency
     * @throws com.causa.common.exceptions.LLMException if the LLM call fails
     */
    LLMResponse send(LLMRequest request);

    /**
     * Returns whether the underlying LLM client is initialized and ready to accept requests.
     *
     * @return true if the LLM provider is connected and responsive, false otherwise
     */
    boolean isReady();
}
