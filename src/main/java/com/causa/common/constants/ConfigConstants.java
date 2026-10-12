package com.causa.common.constants;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Map.entry;

/**
 * Configuration Constants Registry
 *
 * <p>Central source of truth for all runtime-configurable application settings stored in the
 * {@code generic_configs} table (formerly {@code configurations}).
 *
 * <p>Keys are seeded from DB at startup. Missing keys are resolved from MicroProfile Config
 * (ENV → system props → application.yml) and persisted to the DB for subsequent boots.
 *
 * <p>Categories:
 * <ul>
 *   <li><b>alerts</b> — Alert filtering and cooldown (4 keys)</li>
 *   <li><b>cluster</b> — Cluster identity (2 keys)</li>
 * </ul>
 *
 * <p>LLM configuration has been moved to the {@code llm_configs} table and is managed via
 * {@link com.causa.config.LlmConfigCache}. Use {@code GET /api/v1/settings/llm} to read it
 * and {@code PUT /api/v1/settings/llm/{provider}} to update it.
 *
 * @since 0.0.1
 */
public final class ConfigConstants {

    private ConfigConstants() {
        // Prevent instantiation
    }

    /** Masked placeholder for sensitive config values in API responses. */
    public static final String MASKED_VALUE = "********";

    /**
     * Value types for config entries — used for validation.
     */
    public enum ValueType {
        STRING, INTEGER, DOUBLE, BOOLEAN
    }

    /**
     * Key definition record — holds all metadata for a single config key.
     *
     * @param name           The config key (e.g., "LLM_PROVIDER")
     * @param category       Category (llm, alerts, cluster)
     * @param type           Value type (STRING, INTEGER, DOUBLE)
     * @param sensitive      Whether this key holds sensitive data (masks in API, encrypts in DB)
     * @param mpConfigPath   MicroProfile Config property path (e.g., "causa.llm.provider")
     */
    public record KeyDef(String name, String category, ValueType type, boolean sensitive, String mpConfigPath) {}

    /**
     * Central registry of all known configuration keys.
     * Adding a new key = one line here.
     */
    private static final List<KeyDef> REGISTRY = List.of(
        // Alert Configuration (4 keys)
        key("ALERT_FILTER_SEVERITY",           "alerts", ValueType.STRING,  false, "causa.alerts.filter-severity"),
        key("ALERT_COOLDOWN_MINUTES",          "alerts", ValueType.INTEGER, false, "causa.alerts.cooldown-minutes"),
        key("ALERT_IGNORE_NAMESPACES",         "alerts", ValueType.STRING,  false, "causa.alerts.ignore-namespaces"),
        key("ALERT_COOLDOWN_CLEANUP_INTERVAL", "alerts", ValueType.STRING,  false, "causa.alerts.cooldown-cleanup-interval"),

        // Cluster Configuration (2 keys)
        key("CLUSTER_NAME",                    "cluster", ValueType.STRING, false, "causa.cluster.name"),
        key("CLUSTER_TYPE",                    "cluster", ValueType.STRING, false, "causa.cluster.target-cluster-type")
    );

    /** Keys that cannot be updated at runtime via the API — must be set via ENV/application.yml. */
    private static final Set<String> ENV_ONLY_KEYS = Set.of("CLUSTER_TYPE");

    /** All known config keys as an immutable set. */
    public static final Set<String> ALL_KNOWN_KEYS = REGISTRY.stream()
        .map(KeyDef::name)
        .collect(Collectors.toUnmodifiableSet());

    /** All known categories derived from the registry (llm, alerts, cluster). */
    public static final Set<String> VALID_CATEGORIES = REGISTRY.stream()
        .map(k -> k.category().toLowerCase(Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());

    /** Sensitive keys that require encryption in DB and masking in API responses. */
    private static final Set<String> SENSITIVE_KEYS = REGISTRY.stream()
        .filter(KeyDef::sensitive)
        .map(KeyDef::name)
        .collect(Collectors.toUnmodifiableSet());

    /** Integer-type keys for validation. */
    private static final Set<String> INTEGER_KEYS = REGISTRY.stream()
        .filter(k -> k.type() == ValueType.INTEGER)
        .map(KeyDef::name)
        .collect(Collectors.toUnmodifiableSet());

    /** Double-type keys for validation. */
    private static final Set<String> DOUBLE_KEYS = REGISTRY.stream()
        .filter(k -> k.type() == ValueType.DOUBLE)
        .map(KeyDef::name)
        .collect(Collectors.toUnmodifiableSet());

    /** Boolean-type keys for validation. */
    private static final Set<String> BOOLEAN_KEYS = REGISTRY.stream()
        .filter(k -> k.type() == ValueType.BOOLEAN)
        .map(KeyDef::name)
        .collect(Collectors.toUnmodifiableSet());

    /**
     * Checks if a config key is valid (known to the system).
     *
     * @param key the config key to check
     * @return true if the key is in the registry
     */
    public static boolean isValidKey(String key) {
        return ALL_KNOWN_KEYS.contains(key);
    }

    /**
     * Checks if a config key cannot be updated at runtime via the API.
     * Such keys must be set via ENV/application.yml.
     *
     * @param key the config key to check
     * @return true if the key is not updatable at runtime
     */
    public static boolean isEnvOnly(String key) {
        return ENV_ONLY_KEYS.contains(key);
    }

    /**
     * Returns the category for a given config key.
     *
     * @param key the config key
     * @return the category (llm, alerts, cluster), or null if key is unknown
     */
    public static String categoryOf(String key) {
        return REGISTRY.stream()
            .filter(k -> k.name().equals(key))
            .map(k -> k.category().toLowerCase(Locale.ROOT))
            .findFirst()
            .orElse(null);
    }

    /**
     * Checks if a config key holds sensitive data.
     *
     * @param key the config key
     * @return true if the key is sensitive (requires encryption/masking)
     */
    public static boolean isSensitive(String key) {
        return SENSITIVE_KEYS.contains(key);
    }

    /**
     * Checks if a config key expects an integer value.
     *
     * @param key the config key
     * @return true if the key's value type is INTEGER
     */
    public static boolean isIntegerKey(String key) {
        return INTEGER_KEYS.contains(key);
    }

    /**
     * Checks if a config key expects a double value.
     *
     * @param key the config key
     * @return true if the key's value type is DOUBLE
     */
    public static boolean isDoubleKey(String key) {
        return DOUBLE_KEYS.contains(key);
    }

    /**
     * Checks if a config key expects a boolean value (true/false).
     *
     * @param key the config key
     * @return true if the key's value type is BOOLEAN
     */
    public static boolean isBooleanKey(String key) {
        return BOOLEAN_KEYS.contains(key);
    }

    /**
     * Returns the MicroProfile Config property path for a given config key.
     * Used to resolve values from ENV variables and application.yml at startup.
     *
     * @param key the config key
     * @return the MP Config property path, or null if key is unknown
     */
    public static String mpPropertyFor(String key) {
        return REGISTRY.stream()
            .filter(k -> k.name().equals(key))
            .map(KeyDef::mpConfigPath)
            .findFirst()
            .orElse(null);
    }

    /**
     * Returns all key definitions (for startup seeding).
     *
     * @return immutable list of all key definitions
     */
    public static List<KeyDef> allKeys() {
        return List.copyOf(REGISTRY);
    }

    /** Factory method for key definitions (cleaner registry syntax). */
    private static KeyDef key(String name, String category, ValueType type, boolean sensitive, String mpConfigPath) {
        return new KeyDef(name, category, type, sensitive, mpConfigPath);
    }

    /** Discriminator for {@code external_configs} rows and settings tabs. */
    public enum PlatformCategory { OBSERVABILITY, INTEGRATION }

    /** Observability platforms — used when {@code category = OBSERVABILITY}. */
    public enum ObservabilityPlatform { DATADOG, INSTANA }

    /** Integration platforms — used when {@code category = INTEGRATION}. */
    public enum IntegrationPlatform { SLACK, JIRA, GITHUB }

    /** LLM providers stored in {@code llm_configs.provider}. */
    public enum LlmProvider { BOB, ANTHROPIC, VERTEX_AI }

    /** LLM authentication strategies — determines which AuthConfig fields are required. */
    public enum LlmAuthType { API_KEY, SA_JSON_KEY, CUSTOM_HEADERS }

    /** Auth type values stored in {@code external_configs.auth_type}. */
    public enum ExternalAuthType { API_KEY, API_TOKEN, WEBHOOK, PAT }

    /**
     * Sensitive {@link com.causa.core.domain.AuthConfig} fields per platform/provider.
     *
     * <p>The service encrypts these before serialising to JSONB and masks them in GET responses.
     * Keyed by the enum {@code .name()} string (e.g. {@code "DATADOG"}, {@code "API_KEY"}, {@code "SLACK"}).
     * LLM entries are keyed by {@link LlmAuthType} name; all others by platform name.
     */
    public static final Map<String, Set<String>> SENSITIVE_AUTH_FIELDS = Map.ofEntries(
        // Observability
        entry("DATADOG",      Set.of("apiKey", "appKey")),
        entry("INSTANA",      Set.of("token")),
        entry("OTHER",        Set.of("token")),
        // LLM — keyed by auth_type (sensitive fields differ by how you authenticate)
        entry("API_KEY",        Set.of("apiKey")),
        entry("SA_JSON_KEY",    Set.of("credentialsJson")),
        entry("CUSTOM_HEADERS", Set.of("headers")),
        // Integrations
        entry("SLACK",        Set.of("token")),
        entry("JIRA",         Set.of("token", "password")),
        entry("GITHUB",       Set.of("token"))
    );

    /**
     * Structured logging field names for config operations.
     */
    public static final class LogFields {
        private LogFields() {}

        public static final String CONFIG_KEY = "config_key";
        public static final String CATEGORY = "category";
        public static final String KEYS_FROM_DB = "keys_from_db";
        public static final String KEYS_FROM_ENV = "keys_from_env";
        public static final String KEYS_NULL = "keys_null";
    }
}
