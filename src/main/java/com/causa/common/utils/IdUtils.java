package com.causa.common.utils;

import java.util.UUID;

/**
 * Application-layer ID generation utilities.
 *
 * <p>All entity IDs follow the convention {@code {prefix}_{16-char-alphanumeric}},
 * which is exactly 21 characters — matching the {@code VARCHAR(21)} PK columns.
 *
 * <p>ID prefixes per table:
 * <pre>
 *   alerts        → alrt_&lt;16&gt;
 *   diagnostics   → diag_&lt;16&gt;
 *   context_data  → ctxd_&lt;16&gt;
 *   feedback      → fdbk_&lt;16&gt;
 *   configurations → cnfg_&lt;16&gt;
 *   integrations  → intg_&lt;16&gt;
 *   health_checks → hchk_&lt;16&gt;
 * </pre>
 *
 * @since 0.0.1
 */
public final class IdUtils {

    private IdUtils() {
        throw new AssertionError("Utility class cannot be instantiated");
    }

    /**
     * Generates a unique alert ID: {@code alrt_<16-char-alphanumeric>}.
     * Total length = 21 chars.
     *
     * @return a new unique alert ID
     */
    public static String generateAlertId() {
        return "alrt_" + randomAlphanumeric16();
    }

    /**
     * Generates a unique diagnostic ID: {@code diag_<16-char-alphanumeric>}.
     * Total length = 21 chars.
     *
     * @return a new unique diagnostic ID
     */
    public static String generateDiagnosticId() {
        return "diag_" + randomAlphanumeric16();
    }

    /** Returns 16 lowercase alphanumeric characters derived from a random UUID. */
    private static String randomAlphanumeric16() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
