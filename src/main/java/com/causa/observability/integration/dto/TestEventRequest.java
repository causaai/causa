package com.causa.observability.integration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class TestEventRequest {
    @JsonProperty("analysisType")
    private String analysisType;
    
    @JsonProperty("severity")
    private String severity;
    
    public TestEventRequest() {}
    
    public String getAnalysisType() { return analysisType; }
    public void setAnalysisType(String analysisType) { this.analysisType = analysisType; }
    
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
}
