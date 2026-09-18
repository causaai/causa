package com.causa.mcp;

import com.causa.mcp.config.McpSettings;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP Registry
 *
 * <p>Owns one {@link McpClient} per server declared in {@code mcp.json}, populated once at startup
 * via an explicit {@link #init(McpSettings)} call (mirrors {@code ConfigService}'s
 * init-after-construction pattern) rather than eager CDI construction.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class McpRegistry {

    private final Map<String, McpClient> clients = new ConcurrentHashMap<>();
    private volatile boolean initialized = false;
    private volatile String initializationError;

    /**
     * Builds one {@link McpClient} per server and marks the registry initialized.
     *
     * @param settings the loaded, validated MCP settings
     */
    public void init(McpSettings settings) {
        clients.clear();
        settings.mcpServers().forEach((name, config) -> clients.put(name, new McpClient(name, config)));
        initializationError = null;
        initialized = true;
    }

    /**
     * Marks the registry as failed to initialize, retaining the failure reason so it can be
     * surfaced later (e.g. via the health check endpoint).
     *
     * @param reason a human-readable description of why loading failed
     */
    public void markInitFailed(String reason) {
        clients.clear();
        initializationError = reason;
        initialized = false;
    }

    public Optional<McpClient> getClient(String name) {
        return Optional.ofNullable(clients.get(name));
    }

    public Collection<McpClient> allClients() {
        return List.copyOf(clients.values());
    }

    public boolean isInitialized() {
        return initialized;
    }

    public Optional<String> getInitializationError() {
        return Optional.ofNullable(initializationError);
    }
}
