package com.causa.infrastructure.persistence;

import com.causa.common.constants.AppConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.core.services.ConfigService;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * PostgreSQL LISTEN/NOTIFY Configuration Cache Listener
 *
 * <p>Maintains a long-lived unmanaged JDBC connection to PostgreSQL to listen for
 * configuration change notifications via {@code LISTEN config_cache_channel}.
 *
 * <p><b>Critical Design Choice:</b> This listener opens an <b>unmanaged</b> connection
 * via {@link DriverManager#getConnection(String, String, String)}, <b>NOT</b> via the
 * Agroal connection pool. This is required because:
 * <ul>
 *   <li>The LISTEN connection must stay open indefinitely (minutes to hours)</li>
 *   <li>Agroal's {@code max-lifetime} and {@code idle-removal-interval} policies would
 *       close the connection, breaking the LISTEN subscription</li>
 *   <li>Pooled connections are designed for short-lived transactional work, not
 *       long-lived subscriptions</li>
 * </ul>
 *
 * <p><b>CNPG Failover Resiliency:</b> If the CloudNativePG primary node switches,
 * the connection drops. The retry loop automatically reconnects to the new primary
 * via the {@code iri-db-rw} Kubernetes service DNS (which always points to the
 * current primary).
 *
 * <p>The listener runs on a Java Virtual Thread (lightweight, non-blocking).
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class ConfigCacheListener {

    private static final CausaLogger log = CausaLogger.getLogger(ConfigCacheListener.class);
    private static final String CHANNEL = "config_cache_channel";
    private static final int POLL_TIMEOUT_MS = 5000;
    private static final int RECONNECT_DELAY_MS = 5000;

    private final ConfigService configService;
    private final String jdbcUrl;
    private final String jdbcUser;
    private final String jdbcPassword;

    private volatile Thread listenerThread;
    private volatile Connection pgConnection;
    private volatile boolean running = false;

    @Inject
    public ConfigCacheListener(ConfigService configService, Config mpConfig) {
        this.configService = configService;
        this.jdbcUrl = mpConfig.getValue("quarkus.datasource.jdbc.url", String.class);
        this.jdbcUser = mpConfig.getValue("quarkus.datasource.username", String.class);
        this.jdbcPassword = mpConfig.getValue("quarkus.datasource.password", String.class);
    }

    /**
     * Starts the LISTEN/NOTIFY listener on startup.
     * Runs at priority CONFIG_PRIORITY + 1 (21), after config has been seeded.
     */
    void onStartup(@Observes @Priority(AppConstants.StartupConstants.CONFIG_PRIORITY + 1) StartupEvent event) {
        log.info("Starting PG LISTEN/NOTIFY config cache listener").log();
        running = true;

        // Launch virtual thread for the long-lived LISTEN connection
        listenerThread = Thread.ofVirtual()
            .name("pg-config-listener")
            .start(this::listenLoop);

        log.info("PG config listener started on virtual thread").log();
    }

    /**
     * Stops the listener on application shutdown.
     */
    void onShutdown(@Observes ShutdownEvent event) {
        log.info("Stopping PG config listener").log();
        running = false;

        if (listenerThread != null) {
            listenerThread.interrupt();
        }

        if (pgConnection != null) {
            try {
                pgConnection.close();
            } catch (Exception e) {
                log.warn("Error closing PG listen connection")
                    .field("error", e.getMessage())
                    .log();
            }
        }
    }

    /**
     * Main LISTEN loop. Runs on a virtual thread.
     * Automatically reconnects on connection loss (CNPG failover).
     */
    private void listenLoop() {
        while (running) {
            try {
                // Open unmanaged JDBC connection (bypasses Agroal pool)
                pgConnection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
                PGConnection pgConn = pgConnection.unwrap(PGConnection.class);

                // Subscribe to config change notifications
                try (Statement stmt = pgConnection.createStatement()) {
                    stmt.execute("LISTEN " + CHANNEL);
                    log.info("LISTEN subscribed to channel: " + CHANNEL).log();
                }

                // Poll for notifications
                while (running && !pgConnection.isClosed()) {
                    PGNotification[] notifications = pgConn.getNotifications(POLL_TIMEOUT_MS);

                    if (notifications != null && notifications.length > 0) {
                        for (PGNotification notification : notifications) {
                            log.info("Config change notification received")
                                .field("channel", notification.getName())
                                .field("payload", notification.getParameter())
                                .log();

                            // Reload cache — errors here must NOT break the LISTEN connection
                            try {
                                configService.refreshCache();
                            } catch (Exception refreshEx) {
                                log.warn("Cache refresh failed after config notification")
                                    .field("error", refreshEx.getClass().getSimpleName())
                                    .field("message", refreshEx.getMessage())
                                    .log();
                            }
                        }
                    }
                }

            } catch (SQLException e) {
                // Only a genuine connection failure triggers reconnect
                if (running) {
                    log.warn("PG LISTEN connection lost, reconnecting in " + RECONNECT_DELAY_MS + "ms")
                        .field("error", e.getClass().getSimpleName())
                        .field("message", e.getMessage())
                        .log();

                    try {
                        Thread.sleep(RECONNECT_DELAY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                } else {
                    break;
                }
            } finally {
                // Clean up connection on loop exit or before retry
                if (pgConnection != null) {
                    try {
                        pgConnection.close();
                    } catch (Exception e) {
                        // Ignore close errors during cleanup
                    }
                }
            }
        }

        log.info("PG config listener stopped").log();
    }
}
