package com.causa.common.constants;

/**
 * LLM Constants
 *
 * <p>Contains LLM-related constants for structured logging, health checks, and configuration.
 * <p><strong>NO MAGIC STRINGS POLICY:</strong> All LLM-related string constants must be defined here.
 *
 * @since 0.0.1
 */
public final class LLMConstants {

    private LLMConstants() {
        // Prevent instantiation
    }

    /**
     * Structured log field keys for LLM operations.
     */
    public static final class Fields {
        private Fields() {}

        public static final String PROVIDER = "provider";
        public static final String MODEL = "model";
        public static final String AUTH_TYPE = "authType";
        public static final String TEMPERATURE = "temperature";
        public static final String MAX_TOKENS = "maxTokens";
        public static final String TIMEOUT_SECONDS = "timeoutSeconds";
        public static final String INPUT_TOKENS = "inputTokens";
        public static final String OUTPUT_TOKENS = "outputTokens";
        public static final String CACHE_CREATION_TOKENS = "cacheCreationTokens";
        public static final String CACHE_READ_TOKENS = "cacheReadTokens";
        public static final String TOTAL_INPUT_TOKENS = "totalInputTokens";
        public static final String LATENCY_MS = "latencyMs";
        public static final String CACHE_HIT = "cacheHit";
        public static final String ERROR_TYPE = "errorType";
        public static final String VERTEX_PROJECT_ID = "vertexProjectId";
        public static final String VERTEX_LOCATION = "vertexLocation";
        public static final String CACHE_ENABLED = "cacheEnabled";
    }

    /**
     * LLM provider identifiers.
     */
    public static final class Provider {
        private Provider() {}

        public static final String ANTHROPIC = "anthropic";
        public static final String VERTEX_AI_ANTHROPIC = "vertex-ai-anthropic";
        public static final String IBM_BOB = "bob";
        public static final String OLLAMA = "ollama";
    }

    /**
     * Model name identifiers for detection.
     */
    public static final class ModelNames {
        private ModelNames() {}

        // IBM BOB models
        public static final String BOB = "bob";
        public static final String GRANITE = "granite";

        // Claude models (Anthropic)
        public static final String CLAUDE_OPUS_4 = "claude-opus-4";
        public static final String CLAUDE_SONNET_4_6 = "claude-sonnet-4-6";
        public static final String CLAUDE_SONNET_4 = "claude-sonnet-4";
        public static final String CLAUDE_HAIKU_4 = "claude-haiku-4";

        // Legacy Claude models
        public static final String CLAUDE_3_5_SONNET = "claude-3-5-sonnet-20241022";
        public static final String CLAUDE_3_OPUS = "claude-3-opus-20240229";
        public static final String CLAUDE_3_SONNET = "claude-3-sonnet-20240229";
        public static final String CLAUDE_3_HAIKU = "claude-3-haiku-20240307";
    }

    /**
     * BOB Shell specific constants.
     */
    public static final class BobShell {
        private BobShell() {}
        
        // BOB Shell configuration
        public static final int DEFAULT_TIMEOUT_SECONDS = 180;
        
        // BOB Shell CLI flags
        public static final String FLAG_ACCEPT_LICENSE = "--accept-license";
        public static final String FLAG_YOLO = "--yolo";
        public static final String FLAG_OUTPUT_JSON = "-o";
        public static final String FLAG_PROMPT = "-p";
        public static final String OUTPUT_FORMAT_JSON = "json";
        
        // BOB Shell output markers
        public static final String OUTPUT_MARKER = "---output---";
        
        // Environment variables
        public static final String ENV_API_KEY_NAME = "BOBSHELL_API_KEY";  // BOB Shell internally uses this env var
        
        // Health check
        public static final String VERSION_FLAG = "--version";
        public static final int VERSION_CHECK_TIMEOUT_SECONDS = 5;
        
        // JSON field names — top-level stats block
        public static final String JSON_FIELD_RESPONSE = "response";
        public static final String JSON_FIELD_STATS = "stats";
        // Nested path: stats.models.premium.tokens
        public static final String JSON_FIELD_MODELS = "models";
        public static final String JSON_FIELD_PREMIUM = "premium";
        public static final String JSON_FIELD_TOKENS = "tokens";
        public static final String JSON_FIELD_PROMPT_TOKENS = "prompt";
        public static final String JSON_FIELD_COMPLETION_TOKENS = "candidates";
        public static final String JSON_FIELD_TOKENS_USED = "total";
        
        // Log field names
        public static final String LOG_FIELD_SHELL_PATH = "shell_path";
        public static final String LOG_FIELD_EXIT_CODE = "exit_code";
        public static final String LOG_FIELD_OUTPUT = "output";
        public static final String LOG_FIELD_PARTS_COUNT = "parts_count";
        public static final String LOG_FIELD_PROMPT_TOKENS = "promptTokens";
        public static final String LOG_FIELD_COMPLETION_TOKENS = "completionTokens";
        public static final String LOG_FIELD_TOTAL_TOKENS = "totalTokens";
        
        // Output truncation
        public static final int OUTPUT_TRUNCATE_LENGTH = 500;
    }

    /**
     * Health check constants for LLM.
     */
    public static final class Health {
        private Health() {}

        public static final String LLM_HEALTH_NAME = "llm";
        public static final String LLM_UP_MESSAGE = "LLM provider is connected and responsive";
        public static final String LLM_DOWN_MESSAGE = "LLM provider is not available";
    }

    /**
     * Default configuration values.
     */
    public static final class Defaults {
        private Defaults() {}

        public static final String PROVIDER = "anthropic";
        public static final String MODEL_NAME = "claude-sonnet-4-6";
        public static final double TEMPERATURE = 0.1;
        public static final int MAX_TOKENS = 8192;
        public static final int TIMEOUT_SECONDS = 60;
        public static final int CHAT_MEMORY_SIZE = 10;
        public static final int MAX_TOOL_ITERATIONS = 5;
        public static final String AUTH_TYPE = "API_KEY";
        // Valid regions for Claude on Vertex AI: us-east5, us-central1, europe-west1, asia-southeast1
        // Note: 'global' is NOT a valid location for Claude models
        public static final String VERTEX_LOCATION = "us-east5";
    }

    /**
     * Error type classifications.
     */
    public static final class ErrorTypes {
        private ErrorTypes() {}

        public static final String UNSUPPORTED_PROVIDER = "UnsupportedProvider";
        public static final String MISSING_CONFIGURATION = "MissingConfiguration";
        public static final String INVALID_CONFIGURATION = "InvalidConfiguration";
        public static final String MODEL_NOT_READY = "ModelNotReady";
        public static final String LLM_REQUEST_FAILED = "LLMRequestFailed";
        public static final String INVALID_REQUEST_PARAMETERS = "InvalidRequestParameters";
    }

    /**
     * Error message templates.
     */
    public static final class ErrorMessages {
        private ErrorMessages() {}

        public static final String UNSUPPORTED_PROVIDER_TEMPLATE = "Unsupported LLM provider: %s";
        public static final String API_KEY_REQUIRED = "LLM_API_KEY is required for provider: ";
        public static final String VERTEX_PROJECT_ID_REQUIRED = "VERTEX_PROJECT_ID is required for provider: ";
        public static final String MODEL_NOT_AVAILABLE = "LLM chat model not available";
        public static final String LLM_CONFIG_NOT_AVAILABLE = "LLM config not available";
        public static final String REQUEST_FAILED_TEMPLATE = "LLM request failed: %s";
        public static final String TEMPERATURE_RANGE_MESSAGE = "temperature must be between 0.0 and 1.0 inclusive";
        public static final String MAX_TOKENS_RANGE_MESSAGE = "maxTokens must be greater than 0";
        public static final String NO_USER_MESSAGE = "No UserMessage found in conversation";
        public static final String TOOL_NOT_FOUND_TEMPLATE = "Tool not found: %s";
        public static final String TOOL_EXECUTION_FAILED_TEMPLATE = "Tool execution failed: %s: %s";
        public static final String MAX_TOOL_ITERATIONS_TEMPLATE = "LLM did not produce a text response after %d tool iterations";
    }

    /**
     * Configuration key names (for logging/debugging).
     */
    public static final class ConfigKeys {
        private ConfigKeys() {}

        public static final String MISSING_CONFIG = "missingConfig";
        public static final String LLM_API_KEY = "LLM_API_KEY";
        public static final String VERTEX_PROJECT_ID = "VERTEX_PROJECT_ID";
    }

    /**
     * Authentication mode identifiers.
     */
    public static final class AuthModes {
        private AuthModes() {}

        public static final String API_KEY = "API_KEY";
        public static final String ADC = "ADC";
        public static final String ADC_JSON = "ADC_JSON";
    }

    /**
     * Validation constraints for LLM parameters.
     */
    public static final class Validation {
        private Validation() {}

        public static final double MIN_TEMPERATURE = 0.0;
        public static final double MAX_TEMPERATURE = 1.0;
        public static final int MIN_MAX_TOKENS = 1;
    }

    /**
     * Test data for connectivity checks.
     */
    public static final class TestData {
        private TestData() {}

        public static final String CONNECTIVITY_TEST_PROMPT = "Respond with OK";
        public static final int CONNECTIVITY_TEST_MAX_TOKENS = 10;
    }

    /**
     * Health check messages for LLM provider.
     */
    public static final class Messages {
        private Messages() {}

        public static final String LLM_NOT_READY = "LLM provider is not ready";
        public static final String LLM_CONNECTIVITY_FAILED = "LLM connectivity test failed";
        public static final String LLM_CONNECTED_FORMAT = "Connected to LangChain4J with %s";
        public static final String LLM_ERROR_FORMAT = "LLM health check failed: %s";
        public static final String LLM_CONFIG_NOT_AVAILABLE = "LLM config not available";
        public static final String LLM_CONNECTION_FAILED = "LLM connection failed";
    }
}
