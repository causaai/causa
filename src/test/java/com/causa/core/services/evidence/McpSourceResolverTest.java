package com.causa.core.services.evidence;

import com.causa.common.constants.EvidenceConstants.Sources;
import com.causa.mcp.McpRegistry;
import com.causa.mcp.config.McpSettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The resolver reads {@code mcp.json} rather than a hardcoded table, so a server absent from
 * the deployment must never claim a label, and a server added to the config must resolve with
 * no code change.
 */
class McpSourceResolverTest {

    private final McpSourceResolver resolver = new McpSourceResolver(registry());

    @Test
    void resolvesAContextKeyToTheServerThatDeclaresIt() {
        assertThat(resolver.resolve("POD_LOGS")).isEqualTo(Sources.KUBERNETES);
        assertThat(resolver.resolve("LIBERTY_LOGS_DIRECTORY_LISTING")).isEqualTo(Sources.FILESYSTEM);
    }

    /** The LLM echoes the label with its own decoration attached. */
    @Test
    void resolvesADecoratedLabel() {
        assertThat(resolver.resolve("POD_LOGS - sequence 124")).isEqualTo(Sources.KUBERNETES);
        assertThat(resolver.resolve("  pod_logs_previous  ")).isEqualTo(Sources.KUBERNETES);
    }

    /** McpRegistry puts Liberty log content under a shortened form of the configured key. */
    @Test
    void resolvesAKeyShortenedByTheCollector() {
        assertThat(resolver.resolve("LIBERTY_LOGS")).isEqualTo(Sources.FILESYSTEM);
    }

    /** POD_LOGS_PREVIOUS is longer than POD_LOGS, so it must not lose to the prefix match. */
    @Test
    void prefersTheLongestMatchingKey() {
        assertThat(resolver.resolve("POD_LOGS_PREVIOUS")).isEqualTo(Sources.KUBERNETES);
    }

    @Test
    void fallsBackToUnknownForALabelNoConfiguredServerEmits() {
        assertThat(resolver.resolve("GC ANALYSIS (Cryostat JFR)")).isEqualTo(Sources.UNKNOWN);
        assertThat(resolver.resolve(null)).isEqualTo(Sources.UNKNOWN);
        assertThat(resolver.resolve("   ")).isEqualTo(Sources.UNKNOWN);
    }

    @Test
    void resolvesNothingWhenTheRegistryIsEmpty() {
        McpRegistry empty = new McpRegistry(null);
        empty.init(new McpSettings(Map.of()));
        assertThat(new McpSourceResolver(empty).resolve("POD_LOGS")).isEqualTo(Sources.UNKNOWN);
    }

    private static McpRegistry registry() {
        McpRegistry registry = new McpRegistry(null);
        registry.init(new McpSettings(Map.of(
            Sources.KUBERNETES, server("POD_STATUS", "POD_LOGS", "POD_LOGS_PREVIOUS"),
            Sources.FILESYSTEM, server("LIBERTY_LOGS_DIRECTORY_LISTING"))));
        return registry;
    }

    private static McpSettings.ServerConfig server(String... contextKeys) {
        return new McpSettings.ServerConfig(
            "streamable-http", "http://localhost/mcp", Map.of(), false,
            new McpSettings.HealthCheckConfig("http://localhost/healthz", 1000), 1000,
            Map.of(), null,
            List.of(contextKeys).stream()
                .map(key -> new McpSettings.ToolConfig("tool", key, null, Map.of()))
                .toList());
    }
}
