package com.causa.common.constants;

/**
 * Database Constants
 *
 * <p>Contains database-related constants including validation queries,
 * pool configuration, and log field keys.
 *
 * @since 1.0.0
 */
public final class DatabaseConstants {

    private DatabaseConstants() {
        // Prevent instantiation
    }

    /** SQL query used to validate database connections. */
    public static final String VALIDATION_QUERY = "SELECT 1";

    /** Component name used in log fields for database operations. */
    public static final String COMPONENT_NAME = "database";

    /** Field key for connection pool related log entries. */
    public static final String POOL_FIELD = "pool";

    /** Agroal pool name used as field value. */
    public static final String POOL_NAME = "agroal";

    /** Field key for the database kind. */
    public static final String DB_KIND_FIELD = "dbKind";

    /** The database kind value. */
    public static final String DB_KIND_VALUE = "postgresql";

    /**
     * Database Health check constants.
     */
    public static final class Health {
        private Health() {}

        public static final String DB_HEALTH_NAME = "database";
        public static final String DB_UP_MESSAGE = "Database is ready";
        public static final String DB_DOWN_MESSAGE = "Database is not ready";
    }
}
