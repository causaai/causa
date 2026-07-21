package com.causa.common.constants;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Configuration Constants Registry
 *
 * <p>Central source of truth for all runtime-configurable application settings.
 * Defines 21 known configuration keys with their metadata (category, type, sensitivity).
 *
 * <p>Keys are seeded from DB at startup. Missing keys are resolved from MicroProfile Config
 * (ENV → system props → application.yml) and persisted to the DB for subsequent boots.
 *
 * <p>Categories:
 * <ul>
 *   <li><b>llm</b> — LLM provider configuration (15 keys)</li>
 *   <li><b>alerts</b> — Alert filtering and cooldown (4 keys)</li>
 *   <li><b>cluster</b> — Cluster identity (2 keys)</li>
 * </ul>
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
        // LLM Configuration (15 keys)
        key("LLM_PROVIDER",                    "llm", ValueType.STRING,  false, "causa.llm.provider"),
        key("LLM_MODEL_NAME",                  "llm", ValueType.STRING,  false, "causa.llm.model-name"),
        key("LLM_BASE_URL",                    "llm", ValueType.STRING,  false, "causa.llm.base-url"),
        key("LLM_AUTH_TYPE",                   "llm", ValueType.STRING,  false, "causa.llm.auth-type"),
        key("LLM_CUSTOM_HEADERS",              "llm", ValueType.STRING,  false, "causa.llm.custom-headers"),
        key("LLM_TEMPERATURE",                 "llm", ValueType.DOUBLE,  false, "causa.llm.temperature"),
        key("LLM_MAX_TOKENS",                  "llm", ValueType.INTEGER, false, "causa.llm.max-tokens"),
        key("LLM_API_KEY",                     "llm", ValueType.STRING,  true,  "causa.llm.api-key"),
        key("LLM_TIMEOUT_SECONDS",             "llm", ValueType.INTEGER, false, "causa.llm.timeout-seconds"),
        key("LLM_CHAT_MEMORY_SIZE",            "llm", ValueType.INTEGER, false, "causa.llm.chat-memory-size"),
        key("VERTEX_PROJECT_ID",               "llm", ValueType.STRING,  true,  "causa.llm.vertex.project-id"),
        key("VERTEX_LOCATION",                 "llm", ValueType.STRING,  false, "causa.llm.vertex.location"),
        key("BOB_SHELL_PATH",                  "llm", ValueType.STRING,  false, "causa.llm.bob.shell-path"),
        key("GOOGLE_APPLICATION_CREDENTIALS",  "llm", ValueType.STRING,  true,  "causa.llm.google-application-credentials"),
        key("SKILLS_ENABLED",                  "llm", ValueType.BOOLEAN, false, "causa.llm.skills-enabled"),

        // Alert Configuration (4 keys)
        key("ALERT_FILTER_SEVERITY",           "alerts", ValueType.STRING,  false, "causa.alerts.filter-severity"),
        key("ALERT_COOLDOWN_MINUTES",          "alerts", ValueType.INTEGER, false, "causa.alerts.cooldown-minutes"),
        key("ALERT_IGNORE_NAMESPACES",         "alerts", ValueType.STRING,  false, "causa.alerts.ignore-namespaces"),
        key("ALERT_COOLDOWN_CLEANUP_INTERVAL", "alerts", ValueType.STRING,  false, "causa.alerts.cooldown-cleanup-interval"),

        // Cluster Configuration (2 keys)
        key("CLUSTER_NAME",                    "cluster", ValueType.STRING, false, "causa.cluster.name"),
        key("CLUSTER_TYPE",                    "cluster", ValueType.STRING, false, "causa.cluster.target-cluster-type")
    );

    /** All known config keys as an immutable set. */
    public static final Set<String> ALL_KNOWN_KEYS = REGISTRY.stream()
        .map(KeyDef::name)
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
     * Returns the category for a given config key.
     *
     * @param key the config key
     * @return the category (llm, alerts, cluster), or null if key is unknown
     */
    public static String categoryOf(String key) {
        return REGISTRY.stream()
            .filter(k -> k.name().equals(key))
            .map(KeyDef::category)
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
