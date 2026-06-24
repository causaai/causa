package com.causa.observability.integration.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Response after connecting/configuring an observability platform integration
 */
public class ObservabilityConnectionResponse {

    private String integrationId;
    private ProviderType provider;
    private String status;
    private Instant connectedAt;
    private Instant createdAt;
    private List<MonitorInfo> monitorsCreated;
    private String message;
    private String notes;

    public ObservabilityConnectionResponse() {
        this.monitorsCreated = new ArrayList<>();
    }

    public String getIntegrationId() {
        return integrationId;
    }

    public void setIntegrationId(String integrationId) {
        this.integrationId = integrationId;
    }

    public ProviderType getProvider() {
        return provider;
    }

    public void setProvider(ProviderType provider) {
        this.provider = provider;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public void setConnectedAt(Instant connectedAt) {
        this.connectedAt = connectedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public List<MonitorInfo> getMonitorsCreated() {
        return monitorsCreated;
    }

    public void setMonitorsCreated(List<MonitorInfo> monitorsCreated) {
        this.monitorsCreated = monitorsCreated;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    // Convenience methods
    private String metricsEndpoint;
    private List<MonitorInfo> monitors;

    public String getMetricsEndpoint() {
        return metricsEndpoint;
    }

    public void setMetricsEndpoint(String metricsEndpoint) {
        this.metricsEndpoint = metricsEndpoint;
    }

    public List<MonitorInfo> getMonitors() {
        return monitors;
    }

    public void setMonitors(List<MonitorInfo> monitors) {
        this.monitors = monitors;
    }

    public void setProvider(String providerStr) {
        this.provider = ProviderType.valueOf(providerStr.toUpperCase());
    }
}
