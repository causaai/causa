package com.causa.observability.integration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

public class IntegrationListResponse {
    @JsonProperty("integrations")
    private List<IntegrationSummary> integrations = new ArrayList<>();
    
    public IntegrationListResponse() {}
    
    public List<IntegrationSummary> getIntegrations() { return integrations; }
    public void setIntegrations(List<IntegrationSummary> integrations) { this.integrations = integrations; }
}
