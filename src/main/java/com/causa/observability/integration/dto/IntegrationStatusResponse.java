package com.causa.observability.integration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class IntegrationStatusResponse {
    @JsonProperty("integrationId")
    private String integrationId;
    
    @JsonProperty("provider")
    private String provider;
    
    @JsonProperty("installed")
    private boolean installed;
    
    @JsonProperty("agentHealthy")
    private boolean agentHealthy;
    
    @JsonProperty("metricsDiscovered")
    private int metricsDiscovered;
    
    @JsonProperty("monitorsCreated")
    private int monitorsCreated;
    
    @JsonProperty("lastScrape")
    private Instant lastScrape;
    
    @JsonProperty("scrapeStatus")
    private String scrapeStatus;
    
    @JsonProperty("monitors")
    private List<MonitorInfo> monitors = new ArrayList<>();
    
    public IntegrationStatusResponse() {}
    
    public String getIntegrationId() { return integrationId; }
    public void setIntegrationId(String integrationId) { this.integrationId = integrationId; }
    
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    
    public boolean isInstalled() { return installed; }
    public void setInstalled(boolean installed) { this.installed = installed; }
    
    public boolean isAgentHealthy() { return agentHealthy; }
    public void setAgentHealthy(boolean agentHealthy) { this.agentHealthy = agentHealthy; }
    
    public int getMetricsDiscovered() { return metricsDiscovered; }
    public void setMetricsDiscovered(int metricsDiscovered) { this.metricsDiscovered = metricsDiscovered; }
    
    public int getMonitorsCreated() { return monitorsCreated; }
    public void setMonitorsCreated(int monitorsCreated) { this.monitorsCreated = monitorsCreated; }
    
    public Instant getLastScrape() { return lastScrape; }
    public void setLastScrape(Instant lastScrape) { this.lastScrape = lastScrape; }
    
    public String getScrapeStatus() { return scrapeStatus; }
    public void setScrapeStatus(String scrapeStatus) { this.scrapeStatus = scrapeStatus; }
    
    public List<MonitorInfo> getMonitors() { return monitors; }
    public void setMonitors(List<MonitorInfo> monitors) { this.monitors = monitors; }
}
