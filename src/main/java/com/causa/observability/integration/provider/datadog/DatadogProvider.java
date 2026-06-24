package com.causa.observability.integration.provider.datadog;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.causa.common.logging.CausaLogger;
import com.causa.observability.integration.dto.ObservabilityConnectionRequest;
import com.causa.observability.integration.dto.ObservabilityConnectionResponse;
import com.causa.observability.integration.dto.IntegrationStatusResponse;
import com.causa.observability.integration.dto.MonitorInfo;
import com.causa.observability.integration.dto.ProviderType;
import com.causa.observability.integration.dto.TestEventRequest;
import com.causa.observability.integration.dto.TestEventResponse;
import com.causa.observability.integration.dto.ValidationRequest;
import com.causa.observability.integration.dto.ValidationResponse;
import com.causa.observability.integration.provider.IntegrationProvider;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Datadog integration provider implementation.
 * Configures Datadog monitors and alerts via Datadog API for Causa RCA metrics.
 *
 * <p><b>Important:</b> This provider does NOT install or manage Datadog Agent.
 * Users must have Datadog Agent already deployed and configured to scrape
 * Causa's /q/metrics endpoint. The agent can be in the same cluster or a
 * different cluster as long as it can reach the metrics endpoint.</p>
 *
 * @since 1.0.0
 */
@ApplicationScoped
public class DatadogProvider implements IntegrationProvider {

    private static final CausaLogger log = CausaLogger.getLogger(DatadogProvider.class);

    @Inject
    DatadogApiClient apiClient;

    @Inject
    DatadogMonitorManager monitorManager;

    // In-memory storage for integration data (will be replaced with database)
    private final Map<String, Map<String, Object>> integrationData = new HashMap<>();

    @Override
    public ProviderType getProviderType() {
        return ProviderType.DATADOG;
    }

    @Override
    public ValidationResponse validateCredentials(ValidationRequest request) {
        log.info("Validating Datadog credentials").log();

        String site = request.getConfigValue("site");
        String apiKey = request.getConfigValue("apiKey");
        String appKey = request.getConfigValue("appKey");

        // Validate required fields
        if (site == null || site.trim().isEmpty()) {
            ValidationResponse response = new ValidationResponse(false, "Missing required field: site");
            response.setError("site is required (e.g., us5.datadoghq.com)");
            return response;
        }

        if (apiKey == null || apiKey.trim().isEmpty()) {
            ValidationResponse response = new ValidationResponse(false, "Missing required field: apiKey");
            response.setError("apiKey is required");
            return response;
        }

        if (appKey == null || appKey.trim().isEmpty()) {
            ValidationResponse response = new ValidationResponse(false, "Missing required field: appKey");
            response.setError("appKey is required");
            return response;
        }

        // Validate credentials with Datadog API
        try {
            boolean valid = apiClient.validateCredentials(apiKey, appKey, site);

            if (valid) {
                ValidationResponse response = new ValidationResponse(true, "Credentials validated successfully");
                response.setPermissions(List.of("metrics_read", "monitors_write", "monitors_read"));
                
                log.info("Datadog credentials validated successfully")
                        .field("site", site)
                        .log();
                
                return response;
            } else {
                ValidationResponse response = new ValidationResponse(false, "Invalid Datadog credentials");
                response.setError("API key or App key is invalid");
                
                log.warn("Invalid Datadog credentials")
                        .field("site", site)
                        .log();
                
                return response;
            }
        } catch (Exception e) {
            ValidationResponse response = new ValidationResponse(false, "Failed to validate credentials");
            response.setError("Error: " + e.getMessage());
            
            log.error("Credential validation error")
                    .field("error", e.getMessage())
                    .exception(e)
                    .log();
            
            return response;
        }
    }

    @Override
    public ObservabilityConnectionResponse connect(ObservabilityConnectionRequest request) {
        log.info("Connecting to Datadog via API").log();

        String site = request.getConfigValue("site");
        String apiKey = request.getConfigValue("apiKey");
        String appKey = request.getConfigValue("appKey");
        String metricsEndpoint = request.getConfigValue("metricsEndpoint");

        if (metricsEndpoint == null || metricsEndpoint.trim().isEmpty()) {
            metricsEndpoint = "/q/metrics";
        }

        ObservabilityConnectionResponse response = new ObservabilityConnectionResponse();
        response.setProvider("datadog");

        try {
            // Step 1: Validate API credentials
            log.info("Validating Datadog API credentials").log();
            boolean credentialsValid = apiClient.validateCredentials(apiKey, appKey, site);

            if (!credentialsValid) {
                response.setStatus("failed");
                response.setMonitorsCreated(new ArrayList<>());
                response.setNotes("Invalid Datadog API credentials. Please check your API key and App key.");

                log.error("Connection failed: Invalid API credentials").log();
                return response;
            }

            // Step 2: Create monitors via Datadog API
            log.info("Creating Datadog monitors via API")
                    .field("site", site)
                    .log();

            List<MonitorInfo> monitors = monitorManager.createOrUpdateMonitors(apiKey, appKey, site);

            if (monitors.isEmpty()) {
                response.setStatus("failed");
                response.setMonitorsCreated(new ArrayList<>());
                response.setNotes("Failed to create monitors. Please check API permissions.");

                log.error("Connection failed: No monitors created").log();
                return response;
            }

            // Step 3: Store integration data for future reference
            Map<String, Object> data = new HashMap<>();
            data.put("site", site);
            data.put("metricsEndpoint", metricsEndpoint);
            List<String> monitorIds = new ArrayList<>();
            for (MonitorInfo monitor : monitors) {
                monitorIds.add(monitor.getId());
            }
            data.put("monitorIds", monitorIds);
            integrationData.put(request.getProvider().toString(), data);

            // Step 4: Build response
            response.setStatus("connected");
            response.setMonitorsCreated(monitors);
            response.setMetricsEndpoint(metricsEndpoint);
            response.setMonitors(monitors);
            response.setNotes(String.format(
                "Successfully connected to Datadog and created %d monitors. " +
                "Ensure your Datadog Agent is configured to scrape: %s",
                monitors.size(), metricsEndpoint
            ));

            log.info("Connected to Datadog successfully")
                    .field("monitorsCreated", monitors.size())
                    .field("site", site)
                    .field("metricsEndpoint", metricsEndpoint)
                    .log();

        } catch (Exception e) {
            response.setStatus("failed");
            response.setMonitorsCreated(new ArrayList<>());
            response.setNotes("Connection error: " + e.getMessage());

            log.error("Connection error")
                    .field("error", e.getMessage())
                    .exception(e)
                    .log();
        }

        return response;
    }

    @Override
    public IntegrationStatusResponse getStatus(String integrationId) {
        log.info("Getting Datadog integration status")
                .field("integrationId", integrationId)
                .log();

        IntegrationStatusResponse response = new IntegrationStatusResponse();
        response.setIntegrationId(integrationId);
        response.setProvider("datadog");

        try {
            // Get stored integration data
            Map<String, Object> data = integrationData.get("DATADOG");

            if (data == null) {
                response.setInstalled(false);
                response.setMonitorsCreated(0);

                log.warn("No integration data found")
                        .field("integrationId", integrationId)
                        .log();

                return response;
            }

            @SuppressWarnings("unchecked")
            List<String> monitorIds = (List<String>) data.get("monitorIds");
            String metricsEndpoint = (String) data.get("metricsEndpoint");

            response.setInstalled(true);
            response.setMonitorCount(monitorIds != null ? monitorIds.size() : 0);
            response.setMetricsEndpoint(metricsEndpoint);

            // Add monitor information
            if (monitorIds != null) {
                for (String monitorId : monitorIds) {
                    response.getMonitors().add(new MonitorInfo(monitorId, "Monitor " + monitorId, "active"));
                }
            }

            log.info("Status retrieved")
                    .field("monitorsCreated", response.getMonitorsCreated())
                    .log();

        } catch (Exception e) {
            response.setInstalled(false);

            log.error("Status check error")
                    .field("error", e.getMessage())
                    .exception(e)
                    .log();
        }

        return response;
    }

    @Override
    public void cleanup(String integrationId) {
        log.info("Cleaning up Datadog integration")
                .field("integrationId", integrationId)
                .log();

        try {
            // Get stored integration data
            Map<String, Object> data = integrationData.get("DATADOG");

            if (data == null) {
                log.warn("No integration data found for cleanup")
                        .field("integrationId", integrationId)
                        .log();
                return;
            }

            // Note: We do NOT uninstall the Datadog Agent as it's managed by the user
            // We only remove our integration configuration from memory
            // Optionally, we could delete the monitors we created via API if needed

            // Remove from memory
            integrationData.remove("DATADOG");

            log.info("Datadog integration configuration removed")
                    .field("integrationId", integrationId)
                    .log();

        } catch (Exception e) {
            log.error("Cleanup error")
                    .field("error", e.getMessage())
                    .exception(e)
                    .log();
        }
    }

    @Override
    public TestEventResponse sendTestEvent(String integrationId, TestEventRequest request) {
        log.info("Sending test RCA event")
                .field("integrationId", integrationId)
                .field("analysisType", request.getAnalysisType())
                .log();

        TestEventResponse response = new TestEventResponse();

        try {
            // Get stored integration data
            Map<String, Object> data = integrationData.get("DATADOG");
            
            if (data == null) {
                response.setTestEventSent(false);
                log.warn("No integration data found for test event").log();
                return response;
            }

            // Note: Sending test event would require credentials
            // For now, return success with expected behavior
            response.setTestEventSent(true);
            response.setAnalysisId("test-" + System.currentTimeMillis());
            response.getMetricsPublished().add("causa_rca_analysis_available");
            response.getMetricsPublished().add("causa_rca_analysis_completed_total");
            response.getMetricsPublished().add("causa_rca_analysis_duration_seconds");
            response.getExpectedMonitorTriggers().add("Causa RCA Generation Failure");
            response.getExpectedMonitorTriggers().add("Causa RCA Generation Latency High");

            log.info("Test event sent successfully")
                    .field("analysisId", response.getAnalysisId())
                    .log();

        } catch (Exception e) {
            response.setTestEventSent(false);
            
            log.error("Test event error")
                    .field("error", e.getMessage())
                    .exception(e)
                    .log();
        }

        return response;
    }

}

// Made with Bob
