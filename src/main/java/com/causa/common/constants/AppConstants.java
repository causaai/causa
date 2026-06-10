package com.causa.common.constants;

/**
 * Application Constants
 *
 * <p>Contains application-wide constants.
 *
 * @since 1.0.0
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

        /** Database connection pool initialization priority. */
        public static final int DATABASE_PRIORITY = 20;
    }

    /**
     * Health Status Enum
     *
     * <p>Represents the overall health status of the system or a component.
     *
     * @since 1.0.0
     */
    public enum HealthStatus {
        /**
         * System or component is operating normally.
         */
        UP,

        /**
         * System or component is experiencing issues or unavailable.
         */
        DOWN
    }
}
