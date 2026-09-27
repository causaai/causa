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
    private final LlmConfigCache llmConfigCache;
    private final ExternalConfigCache externalConfigCache;

    @Inject
    public ConfigStartup(ConfigService configService,
                         LlmConfigCache llmConfigCache,
                         ExternalConfigCache externalConfigCache) {
        this.configService       = configService;
        this.llmConfigCache      = llmConfigCache;
        this.externalConfigCache = externalConfigCache;
    }

    void onStartup(@Observes @Priority(AppConstants.StartupConstants.CONFIG_PRIORITY) StartupEvent event) {
        log.info("Config startup: loading from DB and environment").log();

        try {
            configService.loadFromDbAndEnv();
            log.info("Config startup: generic configs loaded successfully").log();
        } catch (Exception e) {
            log.warn("Config startup: generic config load failed (non-fatal)")
                .field("error", e.getClass().getSimpleName())
                .field("message", e.getMessage())
                .log();
        }

        try {
            llmConfigCache.refresh();
            log.info("Config startup: LLM config cache loaded successfully").log();
        } catch (Exception e) {
            log.warn("Config startup: LLM config cache load failed (non-fatal)")
                .field("error", e.getClass().getSimpleName())
                .field("message", e.getMessage())
                .log();
        }

        try {
            externalConfigCache.refresh();
            log.info("Config startup: external config cache loaded successfully").log();
        } catch (Exception e) {
            log.warn("Config startup: external config cache load failed (non-fatal)")
                .field("error", e.getClass().getSimpleName())
                .field("message", e.getMessage())
                .log();
        }
    }
}
