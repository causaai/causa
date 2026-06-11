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
        public static final String IBM_BOB = "ibm-bob";
        public static final String OLLAMA = "ollama";
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
        public static final String REQUEST_FAILED_TEMPLATE = "LLM request failed: %s";
        public static final String TEMPERATURE_RANGE_MESSAGE = "temperature must be between 0.0 and 1.0 inclusive";
        public static final String MAX_TOKENS_RANGE_MESSAGE = "maxTokens must be greater than 0";
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
}
