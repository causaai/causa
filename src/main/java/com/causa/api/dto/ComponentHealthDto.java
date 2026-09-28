package com.causa.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Component Health DTO
 *
 * <p>Represents the health status of an individual system component
 * (database, LLM provider, MCP server, etc.).
 *
 * <p>Used as part of the overall system health response to provide
 * detailed status information for each monitored component.
 *
 * @since 0.0.1
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ComponentHealthDto {

    @JsonProperty("status")
    private String status;

    @JsonProperty("message")
    private String message;

    @JsonProperty("latency_ms")
    private Long latencyMs;

    @JsonProperty("optional")
    private Boolean optional;

    /** Nested per-server breakdown — only populated for the aggregate mcp_config component. */
    @JsonProperty("servers")
    private Map<String, ComponentHealthDto> servers;

    /**
     * Default constructor for JSON deserialization
     */
    public ComponentHealthDto() {
    }

    /**
     * Constructor with all fields
     *
     * @param status the component status (UP, DOWN, DEGRADED)
     * @param message descriptive message about the component state
     * @param latencyMs response latency in milliseconds
     */
    public ComponentHealthDto(String status, String message, Long latencyMs) {
        this.status = status;
        this.message = message;
        this.latencyMs = latencyMs;
    }

    /**
     * Constructor without latency (for components that don't measure latency)
     *
     * @param status the component status (UP, DOWN, DEGRADED)
     * @param message descriptive message about the component state
     */
    public ComponentHealthDto(String status, String message) {
        this.status = status;
        this.message = message;
    }

    // Getters and Setters

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(Long latencyMs) {
        this.latencyMs = latencyMs;
    }

    /**
     * Whether this component is optional — i.e. it being down never affects overall system
     * health. Only populated for MCP server components; {@code null} (omitted from JSON) for
     * non-MCP components like database/LLM.
     *
     * @return {@code true}/{@code false} for MCP components, {@code null} otherwise
     */
    public Boolean getOptional() {
        return optional;
    }

    public void setOptional(Boolean optional) {
        this.optional = optional;
    }

    public Map<String, ComponentHealthDto> getServers() {
        return servers;
    }

    public void setServers(Map<String, ComponentHealthDto> servers) {
        this.servers = servers;
    }

    /**
     * Builder for fluent construction
     */
    public static class Builder {
        private String status;
        private String message;
        private Long latencyMs;
        private Boolean optional;
        private Map<String, ComponentHealthDto> servers;

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder latencyMs(Long latencyMs) {
            this.latencyMs = latencyMs;
            return this;
        }

        public Builder optional(Boolean optional) {
            this.optional = optional;
            return this;
        }

        public Builder servers(Map<String, ComponentHealthDto> servers) {
            this.servers = servers;
            return this;
        }

        public ComponentHealthDto build() {
            ComponentHealthDto dto = new ComponentHealthDto(status, message, latencyMs);
            dto.setOptional(optional);
            dto.setServers(servers);
            return dto;
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
