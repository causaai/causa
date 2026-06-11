package com.causa.api.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * Alert Webhook Request DTO
 *
 * <p>Models the Prometheus Alertmanager webhook payload structure.
 * <p>Uses {@code @JsonIgnoreProperties(ignoreUnknown = true)} for forward compatibility
 * with future Alertmanager versions.
 *
 * @since 0.0.1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AlertWebhookRequest {

    private String version;
    private String status;
    private String receiver;

    @JsonProperty("groupKey")
    private String groupKey;

    @JsonProperty("truncatedAlerts")
    private int truncatedAlerts;

    @JsonProperty("groupLabels")
    private Map<String, String> groupLabels;

    @JsonProperty("commonLabels")
    private Map<String, String> commonLabels;

    @JsonProperty("commonAnnotations")
    private Map<String, String> commonAnnotations;

    @JsonProperty("externalURL")
    private String externalURL;

    private List<AlertItem> alerts;

    // Getters and setters

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    public String getGroupKey() {
        return groupKey;
    }

    public void setGroupKey(String groupKey) {
        this.groupKey = groupKey;
    }

    public int getTruncatedAlerts() {
        return truncatedAlerts;
    }

    public void setTruncatedAlerts(int truncatedAlerts) {
        this.truncatedAlerts = truncatedAlerts;
    }

    public Map<String, String> getGroupLabels() {
        return groupLabels;
    }

    public void setGroupLabels(Map<String, String> groupLabels) {
        this.groupLabels = groupLabels;
    }

    public Map<String, String> getCommonLabels() {
        return commonLabels;
    }

    public void setCommonLabels(Map<String, String> commonLabels) {
        this.commonLabels = commonLabels;
    }

    public Map<String, String> getCommonAnnotations() {
        return commonAnnotations;
    }

    public void setCommonAnnotations(Map<String, String> commonAnnotations) {
        this.commonAnnotations = commonAnnotations;
    }

    public String getExternalURL() {
        return externalURL;
    }

    public void setExternalURL(String externalURL) {
        this.externalURL = externalURL;
    }

    public List<AlertItem> getAlerts() {
        return alerts;
    }

    public void setAlerts(List<AlertItem> alerts) {
        this.alerts = alerts;
    }

    /**
     * Nested class representing a single alert item in the webhook payload.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AlertItem {

        private String status;
        private Map<String, String> labels;
        private Map<String, String> annotations;

        @JsonProperty("startsAt")
        private String startsAt;

        @JsonProperty("endsAt")
        private String endsAt;

        @JsonProperty("generatorURL")
        private String generatorURL;

        private String fingerprint;

        // Getters and setters

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public Map<String, String> getLabels() {
            return labels;
        }

        public void setLabels(Map<String, String> labels) {
            this.labels = labels;
        }

        public Map<String, String> getAnnotations() {
            return annotations;
        }

        public void setAnnotations(Map<String, String> annotations) {
            this.annotations = annotations;
        }

        public String getStartsAt() {
            return startsAt;
        }

        public void setStartsAt(String startsAt) {
            this.startsAt = startsAt;
        }

        public String getEndsAt() {
            return endsAt;
        }

        public void setEndsAt(String endsAt) {
            this.endsAt = endsAt;
        }

        public String getGeneratorURL() {
            return generatorURL;
        }

        public void setGeneratorURL(String generatorURL) {
            this.generatorURL = generatorURL;
        }

        public String getFingerprint() {
            return fingerprint;
        }

        public void setFingerprint(String fingerprint) {
            this.fingerprint = fingerprint;
        }
    }
}
