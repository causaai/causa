package com.causa.mcp.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.Map;

/**
 * MCP settings — Jackson-deserialized, Bean-Validated model of {@code mcp.json}.
 *
 * <p>Mirrors the already-committed schema under
 * {@code deployment/kubernetes/base/mcp-config/*.json} (Claude-Desktop/Bob-style {@code mcpServers}
 * map) exactly — field names ({@code optional}, {@code contextKey}) match the real files, not the
 * {@code required}/{@code dataType} naming from earlier design prose. All record types for this
 * config live in this one file for easy maintenance.
 *
 * @param mcpServers server name → server config
 * @since 0.0.1
 */
public record McpSettings(@NotEmpty Map<String, @NotNull @Valid ServerConfig> mcpServers) {

    public McpSettings {
        // Map.copyOf() would throw NPE on a null-valued entry before Bean Validation ever runs;
        // @NotNull above reports it as a validation failure instead, so we just null-coalesce here.
        mcpServers = mcpServers != null ? mcpServers : Map.of();
    }

    /**
     * One MCP server entry.
     *
     * @param type        transport type (e.g. {@code streamable-http})
     * @param url         MCP endpoint URL
     * @param headers     extra HTTP headers to send on every request
     * @param optional    if {@code true}, this server being down never affects overall system health
     * @param healthCheck health-probe endpoint (may differ from {@code url} — e.g. Cryostat)
     * @param timeoutMs   default request timeout for this server (unused until tool-calling lands)
     * @param metadata    server-wide tunables referenced by future argument templating
     * @param description short plain-English summary of the server (LLM-context fallback, unused for now)
     * @param tools       ordered tool-invocation plan (unused until tool-calling lands)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ServerConfig(
            @NotBlank String type,
            @NotBlank String url,
            Map<String, String> headers,
            boolean optional,
            @NotNull @Valid HealthCheckConfig healthCheck,
            int timeoutMs,
            Map<String, Object> metadata,
            String description,
            @Valid List<ToolConfig> tools) {

        public ServerConfig {
            headers = headers != null ? Map.copyOf(headers) : Map.of();
            metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
            tools = tools != null ? List.copyOf(tools) : List.of();
        }
    }

    /**
     * One tool-invocation entry in a server's {@code tools} array. Deserialized so Jackson accepts
     * the field, but nothing reads it until the tool-calling phase lands.
     *
     * @param name        the MCP tool name to call
     * @param contextKey  stable label for this invocation's output
     * @param description plain-English guidance for this data type (LLM-context fallback)
     * @param arguments   argument key → template string
     */
    public record ToolConfig(
            @NotBlank String name,
            String contextKey,
            String description,
            Map<String, String> arguments) {

        public ToolConfig {
            arguments = arguments != null ? Map.copyOf(arguments) : Map.of();
        }
    }

    /**
     * Health-probe endpoint for one MCP server.
     *
     * @param url       health-check URL (may differ from the server's MCP {@code url} — e.g. Cryostat)
     * @param timeoutMs request timeout in milliseconds for the health probe
     */
    public record HealthCheckConfig(@NotBlank String url, @Positive int timeoutMs) {
    }
}
