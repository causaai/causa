package com.causa.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Health Check Response DTO
 *
 * <p>Represents the overall system health status including all monitored components.
 * This response provides a comprehensive view of the application's health state.
 *
 * <p>The overall status is determined by aggregating all component statuses:
 * <ul>
 *   <li>UP - All components are healthy</li>
 *   <li>DEGRADED - Some non-critical components are down</li>
 *   <li>DOWN - Critical components are down</li>
 * </ul>
 *
 * @since 0.0.1
 */
public class HealthCheckResponseDto {

    @JsonProperty("status")
    private String status;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("version")
    private String version;

    @JsonProperty("components")
    private Map<String, ComponentHealthDto> components;

    /**
     * Default constructor for JSON deserialization
     */
    public HealthCheckResponseDto() {
        this.components = new HashMap<>();
    }

    /**
     * Constructor with all fields
     *
     * @param status overall system status
     * @param timestamp ISO 8601 formatted timestamp
     * @param version application version
     * @param components map of component names to their health status
     */
    public HealthCheckResponseDto(String status, String timestamp, String version, 
                                   Map<String, ComponentHealthDto> components) {
        this.status = status;
        this.timestamp = timestamp;
        this.version = version;
        this.components = components != null ? components : new HashMap<>();
    }

    // Getters and Setters

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Get an unmodifiable view of the components map.
     * This ensures immutability of the DTO after construction.
     *
     * @return unmodifiable map of component health statuses
     */
    public Map<String, ComponentHealthDto> getComponents() {
        return Collections.unmodifiableMap(components);
    }

    public void setComponents(Map<String, ComponentHealthDto> components) {
        this.components = components;
    }

    /**
     * Builder for fluent construction
     */
    public static class Builder {
        private String status;
        private String timestamp;
        private String version;
        private Map<String, ComponentHealthDto> components = new HashMap<>();

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder timestamp(String timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder timestampNow() {
            this.timestamp = Instant.now().toString();
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder components(Map<String, ComponentHealthDto> components) {
            this.components = components;
            return this;
        }

        public Builder addComponent(String name, ComponentHealthDto component) {
            this.components.put(name, component);
            return this;
        }

        public HealthCheckResponseDto build() {
            return new HealthCheckResponseDto(status, timestamp, version, components);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
