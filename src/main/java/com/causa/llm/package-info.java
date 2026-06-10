/**
 * LLM Integration Layer
 *
 * <p>LangChain4J orchestration and multiple LLM provider support.
 *
 * <h2>Architecture Layer</h2>
 * Secondary Adapter (Outbound)
 *
 * <h2>Extensibility</h2>
 * Add new providers by adding a case in {@link com.causa.llm.ChatModelFactory}
 * and the corresponding LangChain4J dependency in pom.xml. The factory produces
 * a provider-agnostic {@link dev.langchain4j.model.chat.ChatLanguageModel} based
 * on the {@code LLM_PROVIDER} environment variable.
 *
 * <h2>Supported Providers</h2>
 * <ul>
 *   <li>{@code anthropic} - Claude via direct Anthropic API (requires LLM_API_KEY)</li>
 *   <li>{@code vertex-ai-anthropic} - Claude via Google Cloud Vertex AI (requires VERTEX_PROJECT_ID, uses ADC)</li>
 *   <li>{@code ibm-bob} - IBM Bob via OpenAI-compatible API (planned)</li>
 *   <li>{@code ollama} - Ollama local models (planned)</li>
 * </ul>
 *
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link com.causa.llm.ChatModelFactory} - Produces ChatLanguageModel based on provider</li>
 *   <li>{@link com.causa.llm.LangChainPromptSender} - Implements PromptSender port using LangChain4J</li>
 *   <li>{@link com.causa.llm.LLMStartup} - Verifies connectivity at startup</li>
 *   <li>{@link com.causa.llm.LLMHealthCheck} - MicroProfile readiness check</li>
 * </ul>
 *
 * @since 0.0.1
 */
package com.causa.llm;
