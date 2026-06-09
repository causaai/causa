package com.causa.infrastructure.health;

import com.causa.common.constants.ApiConstants;
import com.causa.common.constants.DatabaseConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.infrastructure.persistence.DatabaseConnectionService;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * Database Health Check
 *
 * <p>MicroProfile readiness health check for database connectivity.
 * Reports UP when the database connection pool is available and responsive.
 *
 * <p>Kubernetes uses this to determine if the pod should receive traffic.
 *
 * @since 1.0.0
 */
@Readiness
public class DbHealthCheck implements HealthCheck {

    private static final CausaLogger log = CausaLogger.getLogger(DbHealthCheck.class);

    private final DatabaseConnectionService databaseConnectionService;

    @Inject
    public DbHealthCheck(DatabaseConnectionService databaseConnectionService) {
        this.databaseConnectionService = databaseConnectionService;
    }

    @Override
    public HealthCheckResponse call() {
        boolean isReady = databaseConnectionService.isReady();

        if (isReady) {
            log.debug(LogMessages.Database.READINESS_CHECK_PASSED)
                .field(ApiConstants.Response.STATUS_KEY, ApiConstants.Status.READY)
                .log();

            return HealthCheckResponse.named(DatabaseConstants.Health.DB_HEALTH_NAME)
                    .up()
                    .withData(ApiConstants.Response.STATUS_KEY, ApiConstants.Status.READY)
                    .withData(ApiConstants.Response.MESSAGE_KEY, DatabaseConstants.Health.DB_UP_MESSAGE)
                    .build();
        } else {
            log.warn(LogMessages.Database.READINESS_CHECK_FAILED)
                .field(ApiConstants.Response.STATUS_KEY, ApiConstants.Status.NOT_READY)
                .log();

            return HealthCheckResponse.named(DatabaseConstants.Health.DB_HEALTH_NAME)
                    .down()
                    .withData(ApiConstants.Response.STATUS_KEY, ApiConstants.Status.NOT_READY)
                    .withData(ApiConstants.Response.MESSAGE_KEY, DatabaseConstants.Health.DB_DOWN_MESSAGE)
                    .build();
        }
    }
}


