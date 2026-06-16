package com.causa.observability.integration.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MonitorInfo {
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("status")
    private String status;
    
    @JsonProperty("type")
    private String type;
    
    @JsonProperty("url")
    private String url;
    
    @JsonProperty("lastTriggered")
    private String lastTriggered;
    
    public MonitorInfo() {}
    
    public MonitorInfo(String id, String name, String status) {
        this.id = id;
        this.name = name;
        this.status = status;
    }
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    
    public String getLastTriggered() { return lastTriggered; }
    public void setLastTriggered(String lastTriggered) { this.lastTriggered = lastTriggered; }
}
