package com.causa.core.services.evidence;

import com.causa.mcp.McpRegistry;
import com.causa.mcp.config.McpSettings;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Matching behaviour is covered by {@code PathAEvidenceMapperTest}; what is left here is the
 * two things only this class can go wrong at.
 */
class McpSourceResolverTest {

    private static final String SHIPPED_CLUSTER_CONFIG =
        "deployment/kubernetes/base/mcp-config/mcp-cluster-default.json";

    /**
     * Every {@code contextKey} the shipped config declares must resolve to the server declaring
     * it. A hardcoded table drifted away from this file once already.
     */
    @Test
    void resolvesEveryContextKeyTheShippedConfigDeclares() throws IOException {
        McpSettings settings = new ObjectMapper()
            .readValue(new File(SHIPPED_CLUSTER_CONFIG), McpSettings.class);

        McpRegistry registry = new McpRegistry(null);
        registry.init(settings);
        McpSourceResolver resolver = new McpSourceResolver(registry);

        settings.mcpServers().forEach((server, config) ->
            config.tools().forEach(tool ->
                assertThat(resolver.resolve(tool.contextKey()))
                    .as("contextKey %s declared by %s", tool.contextKey(), server)
                    .isEqualTo(server)));
    }

    /**
     * Resolving before startup has populated the registry must not pin every later lookup to
     * unknown — the empty table is a not-yet, not an answer.
     */
    @Test
    void doesNotCacheTheTableBuiltFromAnUninitialisedRegistry() {
        McpRegistry registry = new McpRegistry(null);
        McpSourceResolver resolver = new McpSourceResolver(registry);

        assertThat(resolver.resolve("POD_STATUS")).isEqualTo("unknown");

        registry.init(new McpSettings(Map.of("kubernetes", new McpSettings.ServerConfig(
            "streamable-http", "http://example:8080/mcp", Map.of(), false,
            new McpSettings.HealthCheckConfig("http://example:8080/healthz", 5000),
            5000, Map.of(), null,
            List.of(new McpSettings.ToolConfig("pods_get", "POD_STATUS", null, Map.of()))))));

        assertThat(resolver.resolve("POD_STATUS")).isEqualTo("kubernetes");
    }
}
