package com.causa.observability.integration.provider.datadog;

import com.causa.common.logging.CausaLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Client for interacting with Datadog API
 */
@ApplicationScoped
public class DatadogApiClient {

    private static final CausaLogger log = CausaLogger.getLogger(DatadogApiClient.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    @Inject
    ObjectMapper objectMapper;

    private final HttpClient httpClient;

    public DatadogApiClient() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }

    /**
     * Validate Datadog credentials by making a test API call
     */
    public boolean validateCredentials(String apiKey, String appKey, String site) {
        try {
            String apiUrl = String.format("https://api.%s/api/v1/validate", site);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .header("DD-API-KEY", apiKey)
                    .header("DD-APPLICATION-KEY", appKey)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            log.info("Datadog credential validation response")
                    .field("statusCode", response.statusCode())
                    .field("site", site)
                    .log();

            return response.statusCode() == 200;
        } catch (Exception e) {
            log.error("Failed to validate Datadog credentials")
                    .field("error", e.getMessage())
                    .field("site", site)
                    .exception(e)
                    .log();
            return false;
        }
    }

    /**
     * Search for existing monitor by name
     */
    public String searchMonitor(String apiKey, String appKey, String site, String monitorName) {
        try {
            String searchUrl = String.format("https://api.%s/api/v1/monitor/search?query=%s",
                    site, monitorName.replace(" ", "%20"));
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(searchUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .header("DD-API-KEY", apiKey)
                    .header("DD-APPLICATION-KEY", appKey)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode monitors = root.get("monitors");
                if (monitors != null && monitors.isArray() && monitors.size() > 0) {
                    return monitors.get(0).get("id").asText();
                }
            }
            
            return null;
        } catch (Exception e) {
            log.error("Failed to search for monitor")
                    .field("error", e.getMessage())
                    .field("monitorName", monitorName)
                    .exception(e)
                    .log();
            return null;
        }
    }

    /**
     * Create a new monitor
     */
    public String createMonitor(String apiKey, String appKey, String site, String monitorConfig) {
        try {
            String apiUrl = String.format("https://api.%s/api/v1/monitor", site);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .header("DD-API-KEY", apiKey)
                    .header("DD-APPLICATION-KEY", appKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(monitorConfig))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200 || response.statusCode() == 201) {
                JsonNode root = objectMapper.readTree(response.body());
                String monitorId = root.get("id").asText();
                
                log.info("Monitor created successfully")
                        .field("monitorId", monitorId)
                        .log();
                
                return monitorId;
            } else {
                log.warn("Failed to create monitor")
                        .field("statusCode", response.statusCode())
                        .field("response", response.body())
                        .log();
                return null;
            }
        } catch (Exception e) {
            log.error("Exception creating monitor")
                    .field("error", e.getMessage())
                    .exception(e)
                    .log();
            return null;
        }
    }

    /**
     * Update an existing monitor
     */
    public boolean updateMonitor(String apiKey, String appKey, String site, String monitorId, String monitorConfig) {
        try {
            String apiUrl = String.format("https://api.%s/api/v1/monitor/%s", site, monitorId);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .header("DD-API-KEY", apiKey)
                    .header("DD-APPLICATION-KEY", appKey)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(monitorConfig))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            boolean success = response.statusCode() == 200;
            
            log.info("Monitor update result")
                    .field("monitorId", monitorId)
                    .field("success", success)
                    .field("statusCode", response.statusCode())
                    .log();
            
            return success;
        } catch (Exception e) {
            log.error("Exception updating monitor")
                    .field("error", e.getMessage())
                    .field("monitorId", monitorId)
                    .exception(e)
                    .log();
            return false;
        }
    }

    /**
     * Delete a monitor
     */
    public boolean deleteMonitor(String apiKey, String appKey, String site, String monitorId) {
        try {
            String apiUrl = String.format("https://api.%s/api/v1/monitor/%s", site, monitorId);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .header("DD-API-KEY", apiKey)
                    .header("DD-APPLICATION-KEY", appKey)
                    .DELETE()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            boolean success = response.statusCode() == 200 || response.statusCode() == 204;
            
            log.info("Monitor deletion result")
                    .field("monitorId", monitorId)
                    .field("success", success)
                    .log();
            
            return success;
        } catch (Exception e) {
            log.error("Exception deleting monitor")
                    .field("error", e.getMessage())
                    .field("monitorId", monitorId)
                    .exception(e)
                    .log();
            return false;
        }
    }

    /**
     * Get monitor details
     */
    public JsonNode getMonitor(String apiKey, String appKey, String site, String monitorId) {
        try {
            String apiUrl = String.format("https://api.%s/api/v1/monitor/%s", site, monitorId);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .header("DD-API-KEY", apiKey)
                    .header("DD-APPLICATION-KEY", appKey)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            }
            
            return null;
        } catch (Exception e) {
            log.error("Exception getting monitor")
                    .field("error", e.getMessage())
                    .field("monitorId", monitorId)
                    .exception(e)
                    .log();
            return null;
        }
    }

    /**
     * Send a test metric to Datadog
     */
    public boolean sendTestMetric(String apiKey, String site, String metricName, double value, Map<String, String> tags) {
        try {
            String apiUrl = String.format("https://api.%s/api/v1/series", site);
            
            long timestamp = System.currentTimeMillis() / 1000;
            
            // Build metric payload
            StringBuilder tagsArray = new StringBuilder("[");
            if (tags != null && !tags.isEmpty()) {
                tags.forEach((key, val) -> tagsArray.append("\"").append(key).append(":").append(val).append("\","));
                tagsArray.setLength(tagsArray.length() - 1); // Remove trailing comma
            }
            tagsArray.append("]");
            
            String payload = String.format(
                    "{\"series\":[{\"metric\":\"%s\",\"points\":[[%d,%f]],\"type\":\"gauge\",\"tags\":%s}]}",
                    metricName, timestamp, value, tagsArray.toString()
            );
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(REQUEST_TIMEOUT)
                    .header("DD-API-KEY", apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            boolean success = response.statusCode() == 202;
            
            log.info("Test metric sent")
                    .field("metricName", metricName)
                    .field("success", success)
                    .field("statusCode", response.statusCode())
                    .log();
            
            return success;
        } catch (Exception e) {
            log.error("Exception sending test metric")
                    .field("error", e.getMessage())
                    .field("metricName", metricName)
                    .exception(e)
                    .log();
            return false;
        }
    }
}

// Made with Bob
