package com.causa.mcp;

import com.causa.mcp.config.McpSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link McpRegistry}.
 *
 * @since 0.0.1
 */
@DisplayName("McpRegistry Tests")
class McpRegistryTest {

    private static McpSettings.ServerConfig serverConfig() {
        return new McpSettings.ServerConfig(
                "streamable-http",
                "http://example:8080/mcp",
                Map.of(),
                false,
                new McpSettings.HealthCheckConfig("http://example:8080/healthz", 5000),
                5000,
                Map.of(),
                null,
                List.of());
    }

    @Test
    @DisplayName("init() populates one client per configured server")
    void initPopulatesClients() {
        McpRegistry registry = new McpRegistry(null);
        McpSettings settings = new McpSettings(Map.of(
                "kubernetes", serverConfig(),
                "kruize", serverConfig()));

        registry.init(settings);

        assertTrue(registry.isInitialized());
        assertTrue(registry.getInitializationError().isEmpty());
        assertEquals(2, registry.allClients().size());
        assertTrue(registry.getClient("kubernetes").isPresent());
        assertEquals("kubernetes", registry.getClient("kubernetes").get().getServerName());
        assertTrue(registry.getClient("does-not-exist").isEmpty());
    }

    @Test
    @DisplayName("markInitFailed() clears clients and records the failure reason")
    void markInitFailedClearsState() {
        McpRegistry registry = new McpRegistry(null);
        registry.init(new McpSettings(Map.of("kubernetes", serverConfig())));
        assertTrue(registry.isInitialized());

        registry.markInitFailed("mcp.json not found");

        assertFalse(registry.isInitialized());
        assertTrue(registry.allClients().isEmpty());
        assertEquals(Optional.of("mcp.json not found"), registry.getInitializationError());
    }

    @Test
    @DisplayName("A fresh registry starts uninitialized with no clients")
    void freshRegistryIsUninitialized() {
        McpRegistry registry = new McpRegistry(null);

        assertFalse(registry.isInitialized());
        assertTrue(registry.allClients().isEmpty());
        assertTrue(registry.getInitializationError().isEmpty());
    }
}

