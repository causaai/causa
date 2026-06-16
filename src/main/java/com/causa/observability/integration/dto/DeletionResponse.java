package com.causa.observability.integration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class DeletionResponse {
    @JsonProperty("integrationId")
    private String integrationId;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("message")
    private String message;
    
    public DeletionResponse() {}
    
    public DeletionResponse(String integrationId, String status, String message) {
        this.integrationId = integrationId;
        this.status = status;
        this.message = message;
    }
    
    public String getIntegrationId() { return integrationId; }
    public void setIntegrationId(String integrationId) { this.integrationId = integrationId; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
