package com.causa.common.constants;

/**
 * Application Constants
 *
 * <p>Contains application-wide constants.
 *
 * @since 0.0.1
 */
public final class AppConstants {

    private AppConstants() {
        // Prevent instantiation
    }

    /**
     * Startup priority constants for CDI observer ordering.
     *
     * @since 0.0.1
     */
    public static final class StartupConstants {
        private StartupConstants() {}

        /** LLM initialization priority. */
        public static final int LLM_PRIORITY = 10;

        /** Database connection pool initialization priority. */
        public static final int DATABASE_PRIORITY = 20;
    }

    /**
     * Health Status Enum
     *
     * <p>Represents the overall health status of the system or a component.
     *
     * @since 0.0.1
     */
    public enum HealthStatus {
        /**
         * System or component is fully operational.
         */
        UP("UP"),

        /**
         * System or component is not operational.
         */
        DOWN("DOWN"),

        /**
         * System is operational but some non-critical components are down.
         */
        DEGRADED("DEGRADED");

        private final String value;

        HealthStatus(String value) {
            this.value = value;
        }

        /**
         * Get the string value of the status.
         *
         * @return the status value
         */
        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
