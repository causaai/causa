package com.causa.config;

import com.causa.common.constants.AppConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.core.services.ConfigService;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Configuration Startup Handler
 *
 * <p>Observes application startup to load configuration from database and environment.
 * Runs at priority {@link AppConstants.StartupConstants#CONFIG_PRIORITY} (20),
 * after database pool initialization (10) and before LLM initialization (30).
 *
 * <p>Failure is non-fatal — the application starts but config may be incomplete.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class ConfigStartup {

    private static final CausaLogger log = CausaLogger.getLogger(ConfigStartup.class);

    private final ConfigService configService;

    @Inject
    public ConfigStartup(ConfigService configService) {
        this.configService = configService;
    }

    void onStartup(@Observes @Priority(AppConstants.StartupConstants.CONFIG_PRIORITY) StartupEvent event) {
        log.info("Config startup: loading from DB and environment").log();

        try {
            configService.loadFromDbAndEnv();
            log.info("Config startup: completed successfully").log();
        } catch (Exception e) {
            log.warn("Config startup: failed (non-fatal)")
                .field("error", e.getClass().getSimpleName())
                .field("message", e.getMessage())
                .log();
        }
    }
}
