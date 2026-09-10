package com.causa.common.utils;

import org.apache.commons.lang3.RandomStringUtils;

/**
 * Application-layer ID generation utilities.
 *
 * <p>All entity IDs follow the convention {@code {prefix}_{16-char-alphanumeric}},
 * which is exactly 21 characters — matching the {@code VARCHAR(21)} PK columns.
 *
 * <p>Uses {@link RandomStringUtils#secure()} which is backed by {@link java.security.SecureRandom}
 * — cryptographically strong and thread-safe. Produces 16 characters from a 62-char alphabet
 * (0-9, a-z, A-Z), giving ~95 bits of entropy per ID (vs ~64 bits from a UUID hex substring).
 *
 * <p>ID prefixes per table:
 * <pre>
 *   alerts         → alrt_&lt;16&gt;
 *   diagnostics    → diag_&lt;16&gt;
 *   context_data   → ctxd_&lt;16&gt;
 *   feedback       → fdbk_&lt;16&gt;
 *   configurations → cnfg_&lt;16&gt;
 *   integrations   → intg_&lt;16&gt;
 *   health_checks  → hchk_&lt;16&gt;
 *   auth_configurations → auth_&lt;16&gt;
 *   llm_configurations → llmc_&lt;16&gt;
 *   mcp_configurations → mcpc_&lt;16&gt;
 *   skills_configurations → sklc_&lt;16&gt;
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
     */
    public static String generateAlertId() {
        return "alrt_" + randomAlphanumeric16();
    }

    /**
     * Generates a unique diagnostic ID: {@code diag_<16-char-alphanumeric>}.
     * Total length = 21 chars.
     */
    public static String generateDiagnosticId() {
        return "diag_" + randomAlphanumeric16();
    }

    /**
     * Generates a unique configuration ID: {@code cnfg_<16-char-alphanumeric>}.
     * Total length = 21 chars.
     */
    public static String generateConfigurationId() {
        return "cnfg_" + randomAlphanumeric16();
    }

    /**
     * Generates a unique auth configuration ID: {@code auth_<16-char-alphanumeric>}.
     * Total length = 21 chars.
     */
    public static String generateAuthId() {
        return "auth_" + randomAlphanumeric16();
    }

    /**
     * Generates a unique LLM configuration ID: {@code llmc_<16-char-alphanumeric>}.
     * Total length = 21 chars.
     */
    public static String generateLlmProviderId() {
        return "llmc_" + randomAlphanumeric16();
    }

    /**
     * Generates a unique MCP configuration ID: {@code mcpc_<16-char-alphanumeric>}.
     * Total length = 21 chars.
     */
    public static String generateMcpServerId() {
        return "mcpc_" + randomAlphanumeric16();
    }

    /**
     * Generates a unique skill configuration ID: {@code sklc_<16-char-alphanumeric>}.
     * Total length = 21 chars.
     */
    public static String generateSkillId() {
        return "sklc_" + randomAlphanumeric16();
    }

    /** Returns 16 alphanumeric characters from a SecureRandom source. */
    private static String randomAlphanumeric16() {
        return RandomStringUtils.secure().nextAlphanumeric(16);
    }
}
