package com.causa.observability.integration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

public class TestEventResponse {
    @JsonProperty("testEventSent")
    private boolean testEventSent;
    
    @JsonProperty("analysisId")
    private String analysisId;
    
    @JsonProperty("metricsPublished")
    private List<String> metricsPublished = new ArrayList<>();
    
    @JsonProperty("expectedMonitorTriggers")
    private List<String> expectedMonitorTriggers = new ArrayList<>();
    
    public TestEventResponse() {}
    
    public boolean isTestEventSent() { return testEventSent; }
    public void setTestEventSent(boolean testEventSent) { this.testEventSent = testEventSent; }
    
    public String getAnalysisId() { return analysisId; }
    public void setAnalysisId(String analysisId) { this.analysisId = analysisId; }
    
    public List<String> getMetricsPublished() { return metricsPublished; }
    public void setMetricsPublished(List<String> metricsPublished) { this.metricsPublished = metricsPublished; }
    
    public List<String> getExpectedMonitorTriggers() { return expectedMonitorTriggers; }
    public void setExpectedMonitorTriggers(List<String> expectedMonitorTriggers) { this.expectedMonitorTriggers = expectedMonitorTriggers; }
}
