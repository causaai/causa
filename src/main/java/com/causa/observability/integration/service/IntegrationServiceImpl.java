package com.causa.observability.integration.service;

import com.causa.common.logging.CausaLogger;
import com.causa.observability.integration.dto.*;
import com.causa.observability.integration.provider.IntegrationProvider;
import com.causa.observability.integration.provider.IntegrationProviderFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of IntegrationService
 *
 * @since 1.0.0
 */
@ApplicationScoped
public class IntegrationServiceImpl implements IntegrationService {

    private static final CausaLogger log = CausaLogger.getLogger(IntegrationServiceImpl.class);

    @Inject
    IntegrationProviderFactory providerFactory;

    // In-memory storage for now (will be replaced with database)
    private final Map<String, IntegrationData> integrations = new ConcurrentHashMap<>();

    @Override
    public ValidationResponse validateCredentials(String provider, ValidationRequest request) {
        log.info("Validating credentials")
            .field("provider", provider)
            .log();

        try {
            IntegrationProvider integrationProvider = providerFactory.getProvider(provider);
            return integrationProvider.validateCredentials(request);
        } catch (Exception e) {
            log.error("Credential validation failed")
                .field("provider", provider)
                .exception(e)
                .log();
            
            ValidationResponse response = new ValidationResponse(false, "Validation failed: " + e.getMessage());
            response.setError(e.getMessage());
            return response;
        }
    }

    @Override
    public ObservabilityConnectionResponse connectObservability(ObservabilityConnectionRequest request) {
        log.info("Connecting to observability platform")
            .field("provider", request.getProvider())
            .log();

        String integrationId = UUID.randomUUID().toString();

        try {
            IntegrationProvider provider = providerFactory.getProvider(request.getProvider());

            // Connect to platform and configure monitors
            ObservabilityConnectionResponse response = provider.connect(request);
            response.setIntegrationId(integrationId);
            response.setCreatedAt(Instant.now());

            // Store integration data
            IntegrationData data = new IntegrationData();
            data.integrationId = integrationId;
            data.provider = request.getProvider().toString();
            data.status = "connected";
            data.createdAt = Instant.now();
            data.config = new java.util.HashMap<>(request.getConfig());

            integrations.put(integrationId, data);

            log.info("Connected to observability platform successfully")
                .field("integrationId", integrationId)
                .field("provider", request.getProvider())
                .log();

            return response;
        } catch (Exception e) {
            log.error("Failed to connect to observability platform")
                .field("provider", request.getProvider())
                .exception(e)
                .log();
            throw new RuntimeException("Connection failed: " + e.getMessage(), e);
        }
    }

    @Override
    public IntegrationStatusResponse getIntegrationStatus(String integrationId) {
        log.info("Getting integration status")
            .field("integrationId", integrationId)
            .log();

        IntegrationData data = integrations.get(integrationId);
        if (data == null) {
            throw new IllegalArgumentException("Integration not found: " + integrationId);
        }

        try {
            IntegrationProvider provider = providerFactory.getProvider(data.provider);
            return provider.getStatus(integrationId);
        } catch (Exception e) {
            log.error("Failed to get integration status")
                .field("integrationId", integrationId)
                .exception(e)
                .log();
            throw new RuntimeException("Failed to get status: " + e.getMessage(), e);
        }
    }

    @Override
    public IntegrationListResponse listIntegrations() {
        log.info("Listing integrations").log();

        IntegrationListResponse response = new IntegrationListResponse();
        
        for (IntegrationData data : integrations.values()) {
            IntegrationSummary summary = new IntegrationSummary();
            summary.setIntegrationId(data.integrationId);
            summary.setProvider(data.provider);
            summary.setStatus(data.status);
            summary.setCreatedAt(data.createdAt);
            summary.setLastHealthCheck(Instant.now());
            
            response.getIntegrations().add(summary);
        }
        
        return response;
    }

    @Override
    public DeletionResponse deleteIntegration(String integrationId) {
        log.info("Deleting integration")
            .field("integrationId", integrationId)
            .log();

        IntegrationData data = integrations.get(integrationId);
        if (data == null) {
            throw new IllegalArgumentException("Integration not found: " + integrationId);
        }

        try {
            IntegrationProvider provider = providerFactory.getProvider(data.provider);
            provider.cleanup(integrationId);
            
            integrations.remove(integrationId);
            
            log.info("Integration deleted successfully")
                .field("integrationId", integrationId)
                .log();
            
            return new DeletionResponse(integrationId, "deleted", 
                "Integration and all associated resources removed successfully");
        } catch (Exception e) {
            log.error("Failed to delete integration")
                .field("integrationId", integrationId)
                .exception(e)
                .log();
            throw new RuntimeException("Failed to delete integration: " + e.getMessage(), e);
        }
    }

    @Override
    public TestEventResponse sendTestEvent(String integrationId, TestEventRequest request) {
        log.info("Sending test event")
            .field("integrationId", integrationId)
            .field("analysisType", request.getAnalysisType())
            .log();

        IntegrationData data = integrations.get(integrationId);
        if (data == null) {
            throw new IllegalArgumentException("Integration not found: " + integrationId);
        }

        try {
            IntegrationProvider provider = providerFactory.getProvider(data.provider);
            return provider.sendTestEvent(integrationId, request);
        } catch (Exception e) {
            log.error("Failed to send test event")
                .field("integrationId", integrationId)
                .exception(e)
                .log();
            throw new RuntimeException("Failed to send test event: " + e.getMessage(), e);
        }
    }

    /**
     * Internal class to store integration data
     */
    private static class IntegrationData {
        String integrationId;
        String provider;
        String status;
        Instant createdAt;
        Map<String, Object> config;
    }
}

// Made with Bob
