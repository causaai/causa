package com.causa.observability.integration.provider;

import com.causa.observability.integration.dto.*;

/**
 * Interface for observability platform integration providers
 * Each provider (Datadog, Grafana, etc.) implements this interface
 *
 * @since 1.0.0
 */
public interface IntegrationProvider {

    /**
     * Get the provider type
     *
     * @return provider type
     */
    ProviderType getProviderType();

    /**
     * Validate provider credentials
     *
     * @param request validation request
     * @return validation response
     */
    ValidationResponse validateCredentials(ValidationRequest request);

    /**
     * Install the integration
     * This includes:
     * - Installing agent
     * - Creating monitors
     * - Configuring scraping
     *
     * @param request installation request
     * @return installation response
     */
    InstallationResponse install(InstallationRequest request);

    /**
     * Get integration status
     *
     * @param integrationId the integration ID
     * @return status response
     */
    IntegrationStatusResponse getStatus(String integrationId);

    /**
     * Clean up integration resources
     *
     * @param integrationId the integration ID
     */
    void cleanup(String integrationId);

    /**
     * Send a test RCA event
     *
     * @param integrationId the integration ID
     * @param request test event request
     * @return test event response
     */
    TestEventResponse sendTestEvent(String integrationId, TestEventRequest request);
}

// Made with Bob
