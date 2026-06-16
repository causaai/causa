package com.causa.observability.integration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * Response containing status of a configured observability integration.
 * Shows monitor configuration status, not agent status (user manages agent).
 *
 * @since 1.0.0
 */
public class IntegrationStatusResponse {
    @JsonProperty("integrationId")
    private String integrationId;

    @JsonProperty("provider")
    private String provider;

    @JsonProperty("installed")
    private boolean installed; // Monitors configured

    @JsonProperty("monitorsCreated")
    private int monitorsCreated;

    @JsonProperty("metricsEndpoint")
    private String metricsEndpoint; // Where agent should scrape

    @JsonProperty("monitors")
    private List<MonitorInfo> monitors = new ArrayList<>();

    public IntegrationStatusResponse() {}

    public String getIntegrationId() { return integrationId; }
    public void setIntegrationId(String integrationId) { this.integrationId = integrationId; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public boolean isInstalled() { return installed; }
    public void setInstalled(boolean installed) { this.installed = installed; }

    public int getMonitorsCreated() { return monitorsCreated; }
    public void setMonitorsCreated(int monitorsCreated) { this.monitorsCreated = monitorsCreated; }

    public String getMetricsEndpoint() { return metricsEndpoint; }
    public void setMetricsEndpoint(String metricsEndpoint) { this.metricsEndpoint = metricsEndpoint; }

    public List<MonitorInfo> getMonitors() { return monitors; }
    public void setMonitors(List<MonitorInfo> monitors) { this.monitors = monitors; }

    // Convenience alias
    public void setMonitorCount(int count) {
        this.monitorsCreated = count;
    }
}

// Made with Bob
