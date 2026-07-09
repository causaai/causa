package com.causa.common.constants;

/**
 * Configuration Constants
 *
 * <p>Single source-of-truth for every configuration key name stored in the
 * {@code configurations} database table. All categories mirror the existing
 * deployment/kubernetes ConfigMap and secrets-template files.
 *
 * <p><strong>NO STRING LITERALS POLICY:</strong> Any code that reads or writes
 * a configuration key must reference a constant from this class.
 *
 * <p>Key naming convention follows the ENV variable names used in the deployment
 * ConfigMap and Kubernetes secrets so that ENV-fallback lookup works transparently.
 *
 * @since 0.0.1
 */
public final class ConfigConstants {

    private ConfigConstants() {
        // Prevent instantiation
    }

    // =========================================================================
    // Category identifiers  (used for getAll-by-category queries)
    // =========================================================================

    /** Category label for all LLM-related configuration keys. */
    public static final String CATEGORY_LLM   = "LLM";

    /** Category label for all Alert-related configuration keys. */
    public static final String CATEGORY_ALERT = "ALERT";

    /** Category label for all MCP-related configuration keys. */
    public static final String CATEGORY_MCP     = "MCP";

    /** Category label for all Cluster-related configuration keys. */
    public static final String CATEGORY_CLUSTER = "CLUSTER";

    // =========================================================================
    // LLM configuration keys  (configmap.yaml + causa-llm-secrets.yaml + vertex-ai patch)
    // =========================================================================

    /**
     * LLM configuration key constants.
     *
     * <p>Maps directly to {@code causa.llm.*} in application.yml and to the
     * {@code LLM_*} / {@code VERTEX_*} environment variables consumed by the
     * deployment ConfigMap and secrets.
     */
    public static final class LLM {
        private LLM() {}

        /** LLM provider identifier — e.g. {@code vertex-ai-anthropic}, {@code anthropic}, {@code ollama}, {@code bob}. */
        public static final String PROVIDER          = "LLM_PROVIDER";

        /** Model name — e.g. {@code claude-sonnet-4-6}. */
        public static final String MODEL_NAME        = "LLM_MODEL_NAME";

        /** Custom base URL — overrides provider default endpoints. */
        public static final String BASE_URL          = "LLM_BASE_URL";

        /** Authentication type — {@code API_KEY} or {@code ADC}. */
        public static final String AUTH_TYPE         = "LLM_AUTH_TYPE";

        /** Custom HTTP headers as a JSON object string for gateway/proxy routing. */
        public static final String CUSTOM_HEADERS    = "LLM_CUSTOM_HEADERS";

        /** Sampling temperature (0.0 – 1.0). */
        public static final String TEMPERATURE       = "LLM_TEMPERATURE";

        /** Maximum tokens to generate per response. */
        public static final String MAX_TOKENS        = "LLM_MAX_TOKENS";

        /** API key for direct-API providers (Anthropic direct). */
        public static final String API_KEY           = "LLM_API_KEY";

        /** Request timeout in seconds. */
        public static final String TIMEOUT_SECONDS   = "LLM_TIMEOUT_SECONDS";

        /** Number of previous messages to retain in conversational context. */
        public static final String CHAT_MEMORY_SIZE  = "LLM_CHAT_MEMORY_SIZE";

        // --- Vertex AI ---

        /** Google Cloud project ID for Vertex AI (from causa-llm-secrets.yaml). */
        public static final String VERTEX_PROJECT_ID = "VERTEX_PROJECT_ID";

        /** Google Cloud region for Vertex AI — e.g. {@code us-east5} (from configmap.yaml). */
        public static final String VERTEX_LOCATION   = "VERTEX_LOCATION";

        // --- BOB Shell ---

        /** Filesystem path or name of the BOB Shell executable. */
        public static final String BOB_SHELL_PATH    = "BOB_SHELL_PATH";

        // --- Google ADC ---

        /**
         * Base64-encoded Google Application Default Credentials JSON
         * (output of {@code gcloud auth application-default login}).
         * Stored encrypted. Decoded in-memory at startup and passed directly to
         * {@code GoogleCredentials.fromStream()} — no pod file mount required.
         */
        public static final String GOOGLE_APPLICATION_CREDENTIALS = "GOOGLE_APPLICATION_CREDENTIALS";

        /** Immutable set of all valid LLM key names — used for key validation. */
        public static final java.util.Set<String> ALL_KEYS = java.util.Set.of(
                PROVIDER, MODEL_NAME, BASE_URL, AUTH_TYPE, CUSTOM_HEADERS,
                TEMPERATURE, MAX_TOKENS, API_KEY, TIMEOUT_SECONDS, CHAT_MEMORY_SIZE,
                VERTEX_PROJECT_ID, VERTEX_LOCATION, BOB_SHELL_PATH,
                GOOGLE_APPLICATION_CREDENTIALS
        );
    }

    // =========================================================================
    // Alert configuration keys  (configmap.yaml)
    // =========================================================================

    /**
     * Alert configuration key constants.
     *
     * <p>Maps to {@code causa.alerts.*} in application.yml and to the
     * {@code CAUSA_ALERT_*} environment variables in the ConfigMap.
     */
    public static final class Alert {
        private Alert() {}

        /** Minimum severity to trigger the diagnostic pipeline ({@code critical} / {@code warning} / {@code info}). */
        public static final String FILTER_SEVERITY           = "CAUSA_ALERT_SEVERITY";

        /** Cooldown period in minutes before re-processing an alert for the same pod. */
        public static final String COOLDOWN_MINUTES          = "CAUSA_ALERT_COOLDOWN";

        /** Comma-separated list of Kubernetes namespaces to ignore. */
        public static final String IGNORE_NAMESPACES         = "CAUSA_ALERT_IGNORE_NS";

        /** Cooldown cache cleanup interval string — e.g. {@code 5m}. */
        public static final String COOLDOWN_CLEANUP_INTERVAL = "CAUSA_ALERT_COOLDOWN_CLEANUP_INTERVAL";

        /** Immutable set of all valid Alert key names — used for key validation. */
        public static final java.util.Set<String> ALL_KEYS = java.util.Set.of(
                FILTER_SEVERITY, COOLDOWN_MINUTES, IGNORE_NAMESPACES, COOLDOWN_CLEANUP_INTERVAL
        );
    }

    // =========================================================================
    // MCP configuration keys  (configmap.yaml)
    // =========================================================================

    /**
     * MCP (Model Context Protocol) configuration key constants.
     *
     * <p>Maps to {@code causa.mcp.*} in application.yml and to the
     * {@code CAUSA_MCP_*} environment variables in the ConfigMap.
     * Sub-classes mirror the three server categories: Kubernetes, Kruize, Cryostat.
     */
    public static final class MCP {
        private MCP() {}

        /**
         * Kubernetes MCP server configuration key constants.
         */
        public static final class Kubernetes {
            private Kubernetes() {}

            /** Kubernetes MCP server endpoint URL. */
            public static final String ENDPOINT    = "CAUSA_MCP_K8S_ENDPOINT";

            /** Health check path for the Kubernetes MCP server. */
            public static final String HEALTH_PATH = "CAUSA_MCP_K8S_HEALTH_PATH";

            /** HTTP timeout in milliseconds for the Kubernetes MCP server. */
            public static final String TIMEOUT     = "CAUSA_MCP_K8S_TIMEOUT";

            /** Immutable set of all valid Kubernetes MCP key names. */
            public static final java.util.Set<String> ALL_KEYS = java.util.Set.of(
                    ENDPOINT, HEALTH_PATH, TIMEOUT
            );
        }

        /**
         * Kruize MCP server configuration key constants.
         */
        public static final class Kruize {
            private Kruize() {}

            /** Kruize MCP server endpoint URL. */
            public static final String ENDPOINT    = "CAUSA_MCP_KRUIZE_ENDPOINT";

            /** Health check path for the Kruize MCP server. */
            public static final String HEALTH_PATH = "CAUSA_MCP_KRUIZE_HEALTH_PATH";

            /** HTTP timeout in milliseconds for the Kruize MCP server. */
            public static final String TIMEOUT     = "CAUSA_MCP_KRUIZE_TIMEOUT";

            /** Immutable set of all valid Kruize MCP key names. */
            public static final java.util.Set<String> ALL_KEYS = java.util.Set.of(
                    ENDPOINT, HEALTH_PATH, TIMEOUT
            );
        }

        /**
         * Cryostat MCP server configuration key constants.
         */
        public static final class Cryostat {
            private Cryostat() {}

            /** Cryostat MCP server endpoint URL (port 8000). */
            public static final String ENDPOINT        = "CAUSA_MCP_CRYOSTAT_ENDPOINT";

            /** Cryostat health check endpoint URL (port 8080, separate from MCP port). */
            public static final String HEALTH_ENDPOINT = "CAUSA_MCP_CRYOSTAT_HEALTH_ENDPOINT";

            /** Health check path for the Cryostat server. */
            public static final String HEALTH_PATH     = "CAUSA_MCP_CRYOSTAT_HEALTH_PATH";

            /** HTTP timeout in milliseconds for the Cryostat MCP server. */
            public static final String TIMEOUT         = "CAUSA_MCP_CRYOSTAT_TIMEOUT";

            /** Retry delay in milliseconds before re-calling a Cryostat tool. */
            public static final String RETRY_DELAY     = "CAUSA_MCP_CRYOSTAT_RETRY_DELAY";

            /** Maximum retry attempts for Cryostat tool calls. */
            public static final String MAX_RETRIES     = "CAUSA_MCP_CRYOSTAT_MAX_RETRIES";

            /** Immutable set of all valid Cryostat MCP key names. */
            public static final java.util.Set<String> ALL_KEYS = java.util.Set.of(
                    ENDPOINT, HEALTH_ENDPOINT, HEALTH_PATH,
                    TIMEOUT, RETRY_DELAY, MAX_RETRIES
            );
        }

        /** Immutable set of ALL valid MCP key names across all sub-categories. */
        public static final java.util.Set<String> ALL_KEYS = java.util.Collections.unmodifiableSet(
                java.util.stream.Stream.of(
                        Kubernetes.ALL_KEYS,
                        Kruize.ALL_KEYS,
                        Cryostat.ALL_KEYS
                ).flatMap(java.util.Set::stream)
                 .collect(java.util.stream.Collectors.toSet())
        );
    }

    // =========================================================================
    // Cluster configuration keys
    // =========================================================================

    /**
     * Cluster configuration key constants.
     *
     * <p>Maps to {@code causa.cluster.*} in application.yml and to the
     * {@code CAUSA_CLUSTER_*} environment variables in the ConfigMap.
     */
    public static final class Cluster {
        private Cluster() {}

        /** Human-readable name of the Kubernetes cluster being monitored. */
        public static final String CLUSTER_NAME = "CAUSA_CLUSTER_NAME";

        /** Immutable set of all valid Cluster key names — used for key validation. */
        public static final java.util.Set<String> ALL_KEYS = java.util.Set.of(
                CLUSTER_NAME
        );
    }

    // =========================================================================
    // All-keys union — single source of truth for the complete key set
    // =========================================================================

    /**
     * Immutable set of ALL known configuration keys across every category.
     * Used by {@code AppConfig} and {@code ConfigServiceImpl} for iteration.
     */
    public static final java.util.Set<String> ALL_KNOWN_KEYS = java.util.Collections.unmodifiableSet(
            java.util.stream.Stream.of(
                    LLM.ALL_KEYS,
                    Alert.ALL_KEYS,
                    MCP.ALL_KEYS,
                    Cluster.ALL_KEYS
            ).flatMap(java.util.Set::stream)
             .collect(java.util.stream.Collectors.toSet())
    );

    // =========================================================================
    // Numeric key declarations  (validated on write; parsed on read)
    // =========================================================================

    /**
     * Set of configuration keys whose values must be parseable as a non-negative integer.
     * Enforced at the API layer; callers can safely use {@code Integer.parseInt} on them.
     */
    public static final java.util.Set<String> INTEGER_KEYS = java.util.Set.of(
            LLM.MAX_TOKENS,
            LLM.TIMEOUT_SECONDS,
            LLM.CHAT_MEMORY_SIZE,
            Alert.COOLDOWN_MINUTES,
            MCP.Kubernetes.TIMEOUT,
            MCP.Kruize.TIMEOUT,
            MCP.Cryostat.TIMEOUT,
            MCP.Cryostat.RETRY_DELAY,
            MCP.Cryostat.MAX_RETRIES
    );

    /**
     * Set of configuration keys whose values must be parseable as a non-negative double.
     * Enforced at the API layer; callers can safely use {@code Double.parseDouble} on them.
     */
    public static final java.util.Set<String> DOUBLE_KEYS = java.util.Set.of(
            LLM.TEMPERATURE
    );

    /**
     * Returns {@code true} when the value stored for this key must be a valid integer.
     *
     * @param key the configuration key
     * @return {@code true} if the value must parse as an integer
     */
    public static boolean isIntegerKey(String key) {
        return key != null && INTEGER_KEYS.contains(key);
    }

    /**
     * Returns {@code true} when the value stored for this key must be a valid double.
     *
     * @param key the configuration key
     * @return {@code true} if the value must parse as a double
     */
    public static boolean isDoubleKey(String key) {
        return key != null && DOUBLE_KEYS.contains(key);
    }

    // =========================================================================
    // Sensitive key declarations
    // =========================================================================

    /**
     * Set of configuration keys whose values are sensitive and must be stored
     * encrypted in the DB and masked ({@link #MASKED_VALUE}) in API responses.
     */
    public static final java.util.Set<String> SENSITIVE_KEYS = java.util.Set.of(
            LLM.API_KEY,
            LLM.VERTEX_PROJECT_ID,
            LLM.GOOGLE_APPLICATION_CREDENTIALS
    );

    /** Placeholder returned in API responses instead of the real value for sensitive keys. */
    public static final String MASKED_VALUE = "********";

    /**
     * Returns {@code true} when the key's value must be stored encrypted.
     *
     * @param key the configuration key
     * @return {@code true} if sensitive
     */
    public static boolean isSensitive(String key) {
        return key != null && SENSITIVE_KEYS.contains(key);
    }

    // =========================================================================
    // Cross-category helpers
    // =========================================================================

    /**
     * Returns {@code true} when the supplied key is a known configuration key
     * across all categories (LLM, Alert, MCP).
     *
     * @param key the key to validate; may be {@code null}
     * @return {@code true} if the key is valid
     */
    public static boolean isValidKey(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }
        return LLM.ALL_KEYS.contains(key)
                || Alert.ALL_KEYS.contains(key)
                || MCP.ALL_KEYS.contains(key)
                || Cluster.ALL_KEYS.contains(key);
    }

    /**
     * Returns the category label for the given key, or {@code null} if the key
     * is not recognised.
     *
     * @param key the configuration key
     * @return one of {@link #CATEGORY_LLM}, {@link #CATEGORY_ALERT}, {@link #CATEGORY_MCP},
     *         or {@code null}
     */
    public static String categoryOf(String key) {
        if (LLM.ALL_KEYS.contains(key))     return CATEGORY_LLM;
        if (Alert.ALL_KEYS.contains(key))   return CATEGORY_ALERT;
        if (MCP.ALL_KEYS.contains(key))     return CATEGORY_MCP;
        if (Cluster.ALL_KEYS.contains(key)) return CATEGORY_CLUSTER;
        return null;
    }

    // =========================================================================
    // MicroProfile Config property name mapping
    // (constant key  →  MicroProfile Config property read by the Config API)
    // This is the bridge between our storage key names and the application.yml
    // property hierarchy that carries defaults.
    // =========================================================================

    /**
     * Maps every constant key to its corresponding MicroProfile Config property name.
     *
     * <p>MicroProfile Config reads sources in priority order:
     * <ol>
     *   <li>OS environment variables (e.g. {@code LLM_PROVIDER})</li>
     *   <li>System properties</li>
     *   <li>{@code application.yml} — the {@code ${ENV_VAR:default}} expressions
     *       resolve here, so yaml-defined defaults ARE picked up</li>
     * </ol>
     *
     * <p>Usage: inject {@code org.eclipse.microprofile.config.Config} and call
     * {@code config.getOptionalValue(ConfigConstants.mpPropertyFor(key), String.class)}.
     */
    public static String mpPropertyFor(String key) {
        return MP_PROPERTY_MAP.getOrDefault(key, null);
    }

    private static final java.util.Map<String, String> MP_PROPERTY_MAP =
            java.util.Map.ofEntries(
                    // --- LLM ---
                    java.util.Map.entry(LLM.PROVIDER,          "causa.llm.provider"),
                    java.util.Map.entry(LLM.MODEL_NAME,        "causa.llm.model-name"),
                    java.util.Map.entry(LLM.BASE_URL,          "causa.llm.base-url"),
                    java.util.Map.entry(LLM.AUTH_TYPE,         "causa.llm.auth-type"),
                    java.util.Map.entry(LLM.CUSTOM_HEADERS,    "causa.llm.custom-headers"),
                    java.util.Map.entry(LLM.TEMPERATURE,       "causa.llm.temperature"),
                    java.util.Map.entry(LLM.MAX_TOKENS,        "causa.llm.max-tokens"),
                    java.util.Map.entry(LLM.API_KEY,           "causa.llm.api-key"),
                    java.util.Map.entry(LLM.TIMEOUT_SECONDS,   "causa.llm.timeout-seconds"),
                    java.util.Map.entry(LLM.CHAT_MEMORY_SIZE,  "causa.llm.chat-memory-size"),
                    java.util.Map.entry(LLM.VERTEX_PROJECT_ID, "causa.llm.vertex.project-id"),
                    java.util.Map.entry(LLM.VERTEX_LOCATION,   "causa.llm.vertex.location"),
                    java.util.Map.entry(LLM.BOB_SHELL_PATH,    "causa.llm.bob.shell-path"),
                    // GOOGLE_APPLICATION_CREDENTIALS has no application.yml entry —
                    // its value is stored only in the DB (Base64-encoded ADC JSON).
                    // --- Alert ---
                    java.util.Map.entry(Alert.FILTER_SEVERITY,           "causa.alerts.filter-severity"),
                    java.util.Map.entry(Alert.COOLDOWN_MINUTES,          "causa.alerts.cooldown-minutes"),
                    java.util.Map.entry(Alert.IGNORE_NAMESPACES,         "causa.alerts.ignore-namespaces"),
                    java.util.Map.entry(Alert.COOLDOWN_CLEANUP_INTERVAL, "causa.alerts.cooldown-cleanup-interval"),
                    // --- MCP: Kubernetes ---
                    java.util.Map.entry(MCP.Kubernetes.ENDPOINT,    "causa.mcp.kubernetes.endpoint"),
                    java.util.Map.entry(MCP.Kubernetes.HEALTH_PATH, "causa.mcp.kubernetes.health-path"),
                    java.util.Map.entry(MCP.Kubernetes.TIMEOUT,     "causa.mcp.kubernetes.timeout-ms"),
                    // --- MCP: Kruize ---
                    java.util.Map.entry(MCP.Kruize.ENDPOINT,    "causa.mcp.kruize.endpoint"),
                    java.util.Map.entry(MCP.Kruize.HEALTH_PATH, "causa.mcp.kruize.health-path"),
                    java.util.Map.entry(MCP.Kruize.TIMEOUT,     "causa.mcp.kruize.timeout-ms"),
                    // --- MCP: Cryostat ---
                    java.util.Map.entry(MCP.Cryostat.ENDPOINT,        "causa.mcp.cryostat.endpoint"),
                    java.util.Map.entry(MCP.Cryostat.HEALTH_ENDPOINT, "causa.mcp.cryostat.health-endpoint"),
                    java.util.Map.entry(MCP.Cryostat.HEALTH_PATH,     "causa.mcp.cryostat.health-path"),
                    java.util.Map.entry(MCP.Cryostat.TIMEOUT,         "causa.mcp.cryostat.timeout-ms"),
                    java.util.Map.entry(MCP.Cryostat.RETRY_DELAY,     "causa.mcp.cryostat.retry-delay-ms"),
                    java.util.Map.entry(MCP.Cryostat.MAX_RETRIES,     "causa.mcp.cryostat.max-retries"),
                    // --- Cluster ---
                    java.util.Map.entry(Cluster.CLUSTER_NAME, "causa.cluster.name")
            );

    // =========================================================================
    // Structured log field names  (no raw string literals elsewhere)
    // =========================================================================

    /**
     * Structured log field names for config-related log entries.
     *
     * <p>Log message strings live in {@link com.causa.common.logging.LogMessages.Config}.
     */
    public static final class LogFields {
        private LogFields() {}

        public static final String CONFIG_KEY      = "configKey";
        public static final String CONFIG_CATEGORY = "configCategory";
        public static final String CONFIG_SOURCE   = "configSource";
        public static final String KEYS_LOADED     = "keysLoaded";
        public static final String KEYS_FROM_ENV   = "keysFromEnv";
        public static final String KEYS_NULL       = "keysNull";
        public static final String SOURCE_DB       = "DB";
        public static final String SOURCE_ENV      = "ENV";
        public static final String SOURCE_DEFAULT  = "DEFAULT";
    }
}
