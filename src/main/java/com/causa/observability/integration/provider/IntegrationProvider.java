package com.causa.observability.integration.provider;

import com.causa.observability.integration.dto.*;

/**
 * Interface for observability platform integration providers.
 * Each provider (Datadog, Grafana, etc.) implements this interface.
 *
 * <p><b>Important:</b> This interface is designed for API-based integration with existing
 * observability platforms. Implementations should NOT install or manage observability agents.
 * Users must have their observability agent (Datadog Agent, Grafana Agent, etc.) already
 * deployed and configured to scrape Causa's metrics endpoint.</p>
 *
 * <p>The agent can run in the same cluster as Causa or in a different cluster/infrastructure,
 * as long as it can reach Causa's metrics endpoint.</p>
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
     * Connect to observability platform and configure monitors via API.
     *
     * <p>This establishes connection to the platform using provided API credentials
     * and automatically creates monitors for Causa RCA metrics.
     * Actions performed:</p>
     * <ul>
     *   <li>Validate API credentials</li>
     *   <li>Create/update monitors for RCA metrics</li>
     *   <li>Configure alert thresholds</li>
     *   <li>Set up notification channels (if provided)</li>
     * </ul>
     *
     * <p><b>Prerequisites:</b> User's observability agent (Datadog Agent, Grafana Agent, etc.)
     * must already be running and configured to scrape Causa's /q/metrics endpoint.
     * The agent can be in the same cluster or a different cluster.</p>
     *
     * @param request connection request containing API credentials (API key, App key, etc.)
     * @return connection response with created monitors and configuration details
     */
    ObservabilityConnectionResponse connect(ObservabilityConnectionRequest request);

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
