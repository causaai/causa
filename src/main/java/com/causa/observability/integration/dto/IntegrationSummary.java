package com.causa.observability.integration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public class IntegrationSummary {
    @JsonProperty("integrationId")
    private String integrationId;
    
    @JsonProperty("provider")
    private String provider;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("createdAt")
    private Instant createdAt;
    
    @JsonProperty("lastHealthCheck")
    private Instant lastHealthCheck;
    
    public IntegrationSummary() {}
    
    public String getIntegrationId() { return integrationId; }
    public void setIntegrationId(String integrationId) { this.integrationId = integrationId; }
    
    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Instant getLastHealthCheck() { return lastHealthCheck; }
    public void setLastHealthCheck(Instant lastHealthCheck) { this.lastHealthCheck = lastHealthCheck; }
}
