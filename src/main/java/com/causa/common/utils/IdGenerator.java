package com.causa.common.utils;

import java.security.SecureRandom;

/**
 * ID Generator Utility
 *
 * <p>Generates application-layer primary keys that match the
 * {@code {prefix}_{16-char-alphanumeric}} format enforced by the
 * CHECK constraints in the Flyway migration.
 *
 * <p>Example: {@code cnfg_aB3xZ9qL1mRt7yWs}
 *
 * <p><b>Thread Safety:</b> {@link SecureRandom} is thread-safe; this class
 * may be called from any context without synchronisation.
 *
 * @since 0.0.1
 */
public final class IdGenerator {

    private static final int          RAND_LEN  = 16;
    private static final String       ALPHANUM  = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final SecureRandom RANDOM    = new SecureRandom();

    // Table-specific prefixes — sourced from V1__initial_schema.sql comments
    public static final String PREFIX_ALERT         = "alrt_";
    public static final String PREFIX_DIAGNOSTIC    = "diag_";
    public static final String PREFIX_CONTEXT_DATA  = "ctxd_";
    public static final String PREFIX_FEEDBACK      = "fdbk_";
    public static final String PREFIX_CONFIGURATION = "cnfg_";
    public static final String PREFIX_INTEGRATION   = "intg_";
    public static final String PREFIX_HEALTH_CHECK  = "hchk_";

    private IdGenerator() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    /**
     * Generates an ID for the {@code alerts} table: {@code alrt_<16>}.
     *
     * @return a new unique alert ID
     */
    public static String alertId() {
        return generate(PREFIX_ALERT);
    }

    /**
     * Generates an ID for the {@code diagnostics} table: {@code diag_<16>}.
     *
     * @return a new unique diagnostic ID
     */
    public static String diagnosticId() {
        return generate(PREFIX_DIAGNOSTIC);
    }

    /**
     * Generates an ID for the {@code context_data} table: {@code ctxd_<16>}.
     *
     * @return a new unique context-data ID
     */
    public static String contextDataId() {
        return generate(PREFIX_CONTEXT_DATA);
    }

    /**
     * Generates an ID for the {@code feedback} table: {@code fdbk_<16>}.
     *
     * @return a new unique feedback ID
     */
    public static String feedbackId() {
        return generate(PREFIX_FEEDBACK);
    }

    /**
     * Generates an ID for the {@code configurations} table: {@code cnfg_<16>}.
     *
     * @return a new unique configuration ID
     */
    public static String configurationId() {
        return generate(PREFIX_CONFIGURATION);
    }

    /**
     * Generates an ID for the {@code integrations} table: {@code intg_<16>}.
     *
     * @return a new unique integration ID
     */
    public static String integrationId() {
        return generate(PREFIX_INTEGRATION);
    }

    /**
     * Generates an ID for the {@code health_checks} table: {@code hchk_<16>}.
     *
     * @return a new unique health check ID
     */
    public static String healthCheckId() {
        return generate(PREFIX_HEALTH_CHECK);
    }

    /**
     * Generates a {@code {prefix}<16-char-alphanumeric>} ID for the given prefix.
     *
     * @param prefix the table prefix including the trailing underscore
     * @return the generated ID
     */
    public static String generate(String prefix) {
        StringBuilder sb = new StringBuilder(prefix.length() + RAND_LEN);
        sb.append(prefix);
        for (int i = 0; i < RAND_LEN; i++) {
            sb.append(ALPHANUM.charAt(RANDOM.nextInt(ALPHANUM.length())));
        }
        return sb.toString();
    }
}
