package com.causa.api.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Alert Trigger Request DTO
 *
 * <p>Payload for POST /api/v1/alerts — manually creates a synthetic alert
 * without requiring a full Prometheus Alertmanager webhook format.
 *
 * <p>For cluster environment {@code container}, {@code pod}, {@code namespace}
 * is required and for vm environment {@code workload_name} must be
 * provided.
 *
 * @since 0.0.1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlertTriggerRequest {

    @JsonProperty("namespace")
    private String namespace;

    @JsonProperty("container")
    private String container;

    @JsonProperty("pod")
    private String podName;

    @JsonProperty("workload_name")
    private String workloadName;

    @JsonProperty("workload_type")
    private String workloadType;

    @JsonProperty("cluster_name")
    private String clusterName;

    /**
     * Optional severity override. Defaults to {@code critical} when absent.
     */
    @JsonProperty("severity")
    private String severity;

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getContainer() {
        return container;
    }

    public void setContainer(String container) {
        this.container = container;
    }

    public String getPodName() {
        return podName;
    }

    public void setPodName(String podName) {
        this.podName = podName;
    }

    public String getWorkloadName() {
        return workloadName;
    }

    public void setWorkloadName(String workloadName) {
        this.workloadName = workloadName;
    }

    public String getWorkloadType() {
        return workloadType;
    }

    public void setWorkloadType(String workloadType) {
        this.workloadType = workloadType;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }
}
