package com.causa.config;

import com.causa.common.constants.AppConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Application Startup Handler
 *
 * <p>Runs at the highest startup priority ({@link AppConstants.StartupConstants#APP_VERSION_PRIORITY})
 * to log the application version as the very first line emitted after the Quarkus banner.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class AppStartup {

    private static final CausaLogger log = CausaLogger.getLogger(AppStartup.class);

    @ConfigProperty(name = "quarkus.application.version")
    String appVersion;

    void onStartup(@Observes @Priority(AppConstants.StartupConstants.APP_VERSION_PRIORITY) StartupEvent event) {
        log.info(LogMessages.APP_STARTED)
            .field("version", appVersion)
            .log();
    }
}
