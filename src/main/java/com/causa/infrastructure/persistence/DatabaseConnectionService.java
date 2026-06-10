package com.causa.infrastructure.persistence;

import com.causa.common.constants.AppConstants;
import com.causa.common.constants.DatabaseConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Database Connection Service
 *
 * <p>Manages the database connection pool lifecycle and provides connection
 * readiness verification. This service verifies connectivity to PostgreSQL
 * on application startup.
 *
 * <p>The underlying connection pool is managed by Agroal, Quarkus's built-in
 * connection pool implementation. Pool configuration is defined in
 * {@code application.yml} under {@code quarkus.datasource.jdbc.*}.
 *
 * <h2>Startup Behavior</h2>
 * <p>On application startup (with priority {@link AppConstants.StartupConstants#DATABASE_PRIORITY}):
 * <ol>
 *   <li>Acquires a connection from the Agroal pool</li>
 *   <li>Executes a validation query ({@code SELECT 1})</li>
 *   <li>Logs success or failure using structured logging</li>
 *   <li>Returns connection to pool (startup failure is NOT fatal)</li>
 * </ol>
 *
 * @since 1.0.0
 */
@ApplicationScoped
public class DatabaseConnectionService {

    private static final CausaLogger log = CausaLogger.getLogger(DatabaseConnectionService.class);

    private final DataSource dataSource;
    private final AtomicBoolean ready = new AtomicBoolean(false);

    @Inject
    DatabaseConnectionService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Verifies database connectivity on application startup.
     *
     * <p>Called automatically by the CDI container when the application starts,
     * with priority {@link AppConstants.StartupConstants#DATABASE_PRIORITY} to ensure
     * database initialization happens early in the startup sequence.
     *
     * <p>Acquires a connection from the pool and executes a validation query to
     * confirm the database is reachable. Logs the result but does not prevent
     * application startup on failure.
     *
     * @param event the startup event (unused, required by CDI observer pattern)
     */
    void onStartup(@Observes @Priority(AppConstants.StartupConstants.DATABASE_PRIORITY) StartupEvent event) {
        log.info(LogMessages.Database.CONNECTION_VERIFYING)
            .field(DatabaseConstants.COMPONENT_NAME, DatabaseConstants.POOL_NAME)
            .log();

        boolean isReady = verifyConnection();
        setReady(isReady);

        if (isReady) {
            log.info(LogMessages.Database.CONNECTION_SUCCESS)
                .field(DatabaseConstants.DB_KIND_FIELD, DatabaseConstants.DB_KIND_VALUE)
                .field(DatabaseConstants.POOL_FIELD, DatabaseConstants.POOL_NAME)
                .log();
        } else {
            log.error(LogMessages.Database.CONNECTION_FAILED)
                .field(DatabaseConstants.DB_KIND_FIELD, DatabaseConstants.DB_KIND_VALUE)
                .field(DatabaseConstants.POOL_FIELD, DatabaseConstants.POOL_NAME)
                .log();
        }
    }

    /**
     * Check if the database is ready.
     *
     * <p>Returns the cached readiness state set during startup.
     * Used by the health check to determine database readiness.
     *
     * @return {@code true} if database is ready, {@code false} otherwise
     */
    public boolean isReady() {
        return ready.get();
    }

    /**
     * Set the database readiness state.
     *
     * @param ready the readiness state
     */
    public void setReady(boolean ready) {
        this.ready.set(ready);
    }

    /**
     * Verifies database connectivity by executing a validation query.
     *
     * <p>Acquires a connection from the Agroal pool and executes
     * {@link DatabaseConstants#VALIDATION_QUERY}. The connection is returned
     * to the pool immediately via try-with-resources.
     *
     * @return {@code true} if connection succeeds and query executes;
     *         {@code false} if any SQL exception occurs
     */
    private boolean verifyConnection() {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(DatabaseConstants.VALIDATION_QUERY);
            return true;
        } catch (SQLException e) {
            log.debug("Database connection verification failed")
                .field(DatabaseConstants.COMPONENT_NAME, DatabaseConstants.POOL_NAME)
                .exception(e)
                .log();
            return false;
        }
    }
}
