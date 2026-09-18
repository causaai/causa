package com.causa.mcp;

import com.causa.common.constants.AppConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.mcp.config.McpSettingsLoader;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * MCP Startup Handler
 *
 * <p>Observes application startup to load {@code mcp.json} and populate the {@link McpRegistry}.
 * Runs at priority {@link AppConstants.StartupConstants#MCP_PRIORITY} (25), after config loading
 * (20).
 *
 * <p>Failure is non-fatal — the application starts even if MCP config fails to load; the registry
 * retains the failure reason so it can be surfaced later (e.g. via the health check endpoint).
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class McpStartup {

    private static final CausaLogger log = CausaLogger.getLogger(McpStartup.class);

    private final McpSettingsLoader settingsLoader;
    private final McpRegistry registry;

    @Inject
    public McpStartup(McpSettingsLoader settingsLoader, McpRegistry registry) {
        this.settingsLoader = settingsLoader;
        this.registry = registry;
    }

    void onStartup(@Observes @Priority(AppConstants.StartupConstants.MCP_PRIORITY) StartupEvent event) {
        log.info("MCP startup: loading mcp.json").log();

        try {
            registry.init(settingsLoader.load());
            log.info("MCP startup: completed successfully")
                    .field("servers", registry.allClients().size())
                    .field("serverNames", registry.allClients().stream()
                            .map(McpClient::getServerName)
                            .toList())
                    .log();
        } catch (Exception e) {
            log.warn("MCP startup: failed (non-fatal)")
                    .field("error", e.getClass().getSimpleName())
                    .field("message", e.getMessage())
                    .log();
            registry.markInitFailed(e.getMessage());
        }
    }
}
