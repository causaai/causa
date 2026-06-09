package com.causa.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

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
     * Builder for fluent construction
     */
    public static class Builder {
        private String status;
        private String message;
        private Long latencyMs;

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

        public ComponentHealthDto build() {
            return new ComponentHealthDto(status, message, latencyMs);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
