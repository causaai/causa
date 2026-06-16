package com.causa.observability.integration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class InstallationResponse {
    @JsonProperty("integrationId")
    private String integrationId;
    
    @JsonProperty("provider")
    private String provider;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("agentInstalled")
    private boolean agentInstalled;
    
    @JsonProperty("agentStatus")
    private String agentStatus;
    
    @JsonProperty("monitorsCreated")
    private int monitorsCreated;
    
    @JsonProperty("metricsDiscovered")
    private int metricsDiscovered;
    
    @JsonProperty("metricsEndpoint")
    private String metricsEndpoint;
    
    @JsonProperty("scrapeInterval")
    private String scrapeInterval;
    
    @JsonProperty("createdAt")
    private Instant createdAt;
    
    @JsonProperty("monitors")
    private List<MonitorInfo> monitors = new ArrayList<>();
    
    @JsonProperty("notes")
    private String notes;
    
    public InstallationResponse() {}
    
    // Getters and setters
    public String getIntegrationId() { return integrationId; }
    public void setIntegrationId(String integrationId) { this.integrationId = integrationId; }
    
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public boolean isAgentInstalled() { return agentInstalled; }
    public void setAgentInstalled(boolean agentInstalled) { this.agentInstalled = agentInstalled; }
    
    public String getAgentStatus() { return agentStatus; }
    public void setAgentStatus(String agentStatus) { this.agentStatus = agentStatus; }
    
    public int getMonitorsCreated() { return monitorsCreated; }
    public void setMonitorsCreated(int monitorsCreated) { this.monitorsCreated = monitorsCreated; }
    
    public int getMetricsDiscovered() { return metricsDiscovered; }
    public void setMetricsDiscovered(int metricsDiscovered) { this.metricsDiscovered = metricsDiscovered; }
    
    public String getMetricsEndpoint() { return metricsEndpoint; }
    public void setMetricsEndpoint(String metricsEndpoint) { this.metricsEndpoint = metricsEndpoint; }
    
    public String getScrapeInterval() { return scrapeInterval; }
    public void setScrapeInterval(String scrapeInterval) { this.scrapeInterval = scrapeInterval; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public List<MonitorInfo> getMonitors() { return monitors; }
    public void setMonitors(List<MonitorInfo> monitors) { this.monitors = monitors; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
