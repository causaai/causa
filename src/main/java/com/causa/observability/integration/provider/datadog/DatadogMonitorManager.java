package com.causa.observability.integration.provider.datadog;

import com.causa.common.logging.CausaLogger;
import com.causa.observability.integration.dto.MonitorInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages Datadog monitor configurations and creation
 */
@ApplicationScoped
public class DatadogMonitorManager {

    private static final CausaLogger log = CausaLogger.getLogger(DatadogMonitorManager.class);

    @Inject
    DatadogApiClient apiClient;

    @Inject
    ObjectMapper objectMapper;

    // Monitor configuration paths
    private static final String[] MONITOR_CONFIGS = {
            "/com/causa/observability/datadog/monitors/failure/monitor-config.json",
            "/com/causa/observability/datadog/monitors/latency/monitor-config.json",
            "/com/causa/observability/datadog/monitors/high-failure-rate/monitor-config.json",
            "/com/causa/observability/datadog/monitors/success-rate/monitor-config.json",
            "/com/causa/observability/datadog/monitors/no-rca-generated/monitor-config.json",
            "/com/causa/observability/datadog/monitors/slow-rca-trend/monitor-config.json"
    };

    /**
     * Create or update all Causa RCA monitors
     */
    public List<MonitorInfo> createOrUpdateMonitors(String apiKey, String appKey, String site) {
        List<MonitorInfo> monitors = new ArrayList<>();

        log.info("Creating/updating Datadog monitors")
                .field("monitorCount", MONITOR_CONFIGS.length)
                .log();

        for (String configPath : MONITOR_CONFIGS) {
            try {
                String config = loadMonitorConfig(configPath);
                JsonNode configJson = objectMapper.readTree(config);
                String monitorName = configJson.get("name").asText();

                log.info("Processing monitor")
                        .field("monitorName", monitorName)
                        .log();

                // Check if monitor already exists
                String existingMonitorId = apiClient.searchMonitor(apiKey, appKey, site, monitorName);

                String monitorId;
                if (existingMonitorId != null) {
                    // Update existing monitor
                    log.info("Updating existing monitor")
                            .field("monitorId", existingMonitorId)
                            .field("monitorName", monitorName)
                            .log();

                    boolean updated = apiClient.updateMonitor(apiKey, appKey, site, existingMonitorId, config);
                    if (updated) {
                        monitorId = existingMonitorId;
                    } else {
                        log.warn("Failed to update monitor")
                                .field("monitorName", monitorName)
                                .log();
                        continue;
                    }
                } else {
                    // Create new monitor
                    log.info("Creating new monitor")
                            .field("monitorName", monitorName)
                            .log();

                    monitorId = apiClient.createMonitor(apiKey, appKey, site, config);
                    if (monitorId == null) {
                        log.warn("Failed to create monitor")
                                .field("monitorName", monitorName)
                                .log();
                        continue;
                    }
                }

                // Add to result list
                MonitorInfo monitorInfo = new MonitorInfo();
                monitorInfo.setId(monitorId);
                monitorInfo.setName(monitorName);
                monitorInfo.setType(configJson.get("type").asText());
                monitorInfo.setUrl(String.format("https://app.%s/monitors/%s", site, monitorId));
                monitors.add(monitorInfo);

                log.info("Monitor processed successfully")
                        .field("monitorId", monitorId)
                        .field("monitorName", monitorName)
                        .log();

            } catch (Exception e) {
                log.error("Failed to process monitor config")
                        .field("configPath", configPath)
                        .field("error", e.getMessage())
                        .exception(e)
                        .log();
            }
        }

        log.info("Monitor creation/update completed")
                .field("successCount", monitors.size())
                .field("totalCount", MONITOR_CONFIGS.length)
                .log();

        return monitors;
    }

    /**
     * Delete all Causa RCA monitors
     */
    public boolean deleteMonitors(String apiKey, String appKey, String site, List<String> monitorIds) {
        log.info("Deleting Datadog monitors")
                .field("monitorCount", monitorIds.size())
                .log();

        boolean allDeleted = true;
        for (String monitorId : monitorIds) {
            boolean deleted = apiClient.deleteMonitor(apiKey, appKey, site, monitorId);
            if (!deleted) {
                log.warn("Failed to delete monitor")
                        .field("monitorId", monitorId)
                        .log();
                allDeleted = false;
            }
        }

        return allDeleted;
    }

    /**
     * Get status of monitors
     */
    public List<MonitorInfo> getMonitorStatus(String apiKey, String appKey, String site, List<String> monitorIds) {
        List<MonitorInfo> monitors = new ArrayList<>();

        for (String monitorId : monitorIds) {
            try {
                JsonNode monitor = apiClient.getMonitor(apiKey, appKey, site, monitorId);
                if (monitor != null) {
                    MonitorInfo info = new MonitorInfo();
                    info.setId(monitorId);
                    info.setName(monitor.get("name").asText());
                    info.setType(monitor.get("type").asText());
                    info.setUrl(String.format("https://app.%s/monitors/%s", site, monitorId));
                    
                    // Get overall state if available
                    JsonNode overallState = monitor.get("overall_state");
                    if (overallState != null) {
                        info.setStatus(overallState.asText());
                    }
                    
                    monitors.add(info);
                }
            } catch (Exception e) {
                log.error("Failed to get monitor status")
                        .field("monitorId", monitorId)
                        .field("error", e.getMessage())
                        .exception(e)
                        .log();
            }
        }

        return monitors;
    }

    /**
     * Load monitor configuration from classpath
     */
    private String loadMonitorConfig(String path) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Monitor config not found: " + path);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}

// Made with Bob
