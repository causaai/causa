package com.causa.observability.integration.service;

import com.causa.observability.integration.dto.*;

/**
 * Service interface for managing observability platform integrations
 *
 * @since 1.0.0
 */
public interface IntegrationService {

    /**
     * Validate provider credentials
     *
     * @param provider the provider type
     * @param request validation request
     * @return validation response
     */
    ValidationResponse validateCredentials(String provider, ValidationRequest request);

    /**
     * Connect to observability platform and configure monitors
     *
     * @param request connection request with API credentials
     * @return connection response with created monitors
     */
    ObservabilityConnectionResponse connectObservability(ObservabilityConnectionRequest request);

    /**
     * Get integration status
     *
     * @param integrationId the integration ID
     * @return integration status
     */
    IntegrationStatusResponse getIntegrationStatus(String integrationId);

    /**
     * List all integrations
     *
     * @return list of integrations
     */
    IntegrationListResponse listIntegrations();

    /**
     * Delete an integration
     *
     * @param integrationId the integration ID
     * @return deletion response
     */
    DeletionResponse deleteIntegration(String integrationId);

    /**
     * Send test RCA event
     *
     * @param integrationId the integration ID
     * @param request test event request
     * @return test event response
     */
    TestEventResponse sendTestEvent(String integrationId, TestEventRequest request);
}

// Made with Bob
