package com.causa.observability.integration.api;

import com.causa.common.logging.CausaLogger;
import com.causa.observability.integration.dto.*;
import com.causa.observability.integration.service.IntegrationService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST API for managing observability platform integrations
 *
 * @since 1.0.0
 */
@Path("/api/v1/integrations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class IntegrationController {

    private static final CausaLogger log = CausaLogger.getLogger(IntegrationController.class);

    @Inject
    IntegrationService integrationService;

    /**
     * Validate provider credentials
     *
     * @param provider the provider type (datadog, grafana, etc.)
     * @param request validation request with credentials
     * @return validation result
     */
    @POST
    @Path("/{provider}/validate")
    public Response validateCredentials(
            @PathParam("provider") String provider,
            ValidationRequest request) {
        
        log.info("Validating credentials for provider")
            .field("provider", provider)
            .log();
        
        try {
            ValidationResponse response = integrationService.validateCredentials(provider, request);
            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            log.error("Invalid provider")
                .field("provider", provider)
                .exception(e)
                .log();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse("Invalid provider: " + provider))
                    .build();
        } catch (Exception e) {
            log.error("Error validating credentials for provider")
                .field("provider", provider)
                .exception(e)
                .log();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Validation failed: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Install integration for a provider
     *
     * @param request installation request with provider details and credentials
     * @return installation result
     */
    @POST
    @Path("/install")
    public Response installIntegration(InstallationRequest request) {
        
        log.info("Installing integration for provider")
            .field("provider", request.getProvider())
            .log();
        
        try {
            InstallationResponse response = integrationService.installIntegration(request);
            return Response.status(Response.Status.CREATED).entity(response).build();
        } catch (IllegalArgumentException e) {
            log.error("Invalid installation request")
                .exception(e)
                .log();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Error installing integration")
                .exception(e)
                .log();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Installation failed: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get integration status
     *
     * @param integrationId the integration ID
     * @return integration status
     */
    @GET
    @Path("/{integrationId}/status")
    public Response getIntegrationStatus(@PathParam("integrationId") String integrationId) {
        
        log.info("Getting status for integration")
            .field("integrationId", integrationId)
            .log();
        
        try {
            IntegrationStatusResponse response = integrationService.getIntegrationStatus(integrationId);
            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            log.error("Integration not found")
                .field("integrationId", integrationId)
                .log();
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Integration not found: " + integrationId))
                    .build();
        } catch (Exception e) {
            log.error("Error getting integration status")
                .exception(e)
                .log();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to get status: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * List all integrations
     *
     * @return list of integrations
     */
    @GET
    public Response listIntegrations() {
        
        log.info("Listing all integrations").log();
        
        try {
            IntegrationListResponse response = integrationService.listIntegrations();
            return Response.ok(response).build();
        } catch (Exception e) {
            log.error("Error listing integrations")
                .exception(e)
                .log();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to list integrations: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Delete an integration
     *
     * @param integrationId the integration ID
     * @return deletion result
     */
    @DELETE
    @Path("/{integrationId}")
    public Response deleteIntegration(@PathParam("integrationId") String integrationId) {
        
        log.info("Deleting integration")
            .field("integrationId", integrationId)
            .log();
        
        try {
            DeletionResponse response = integrationService.deleteIntegration(integrationId);
            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            log.error("Integration not found")
                .field("integrationId", integrationId)
                .log();
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Integration not found: " + integrationId))
                    .build();
        } catch (Exception e) {
            log.error("Error deleting integration")
                .exception(e)
                .log();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to delete integration: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Send test RCA event
     *
     * @param integrationId the integration ID
     * @param request test event request
     * @return test event result
     */
    @POST
    @Path("/{integrationId}/test-event")
    public Response sendTestEvent(
            @PathParam("integrationId") String integrationId,
            TestEventRequest request) {
        
        log.info("Sending test event for integration")
            .field("integrationId", integrationId)
            .log();
        
        try {
            TestEventResponse response = integrationService.sendTestEvent(integrationId, request);
            return Response.ok(response).build();
        } catch (IllegalArgumentException e) {
            log.error("Integration not found")
                .field("integrationId", integrationId)
                .log();
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("Integration not found: " + integrationId))
                    .build();
        } catch (Exception e) {
            log.error("Error sending test event")
                .exception(e)
                .log();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(new ErrorResponse("Failed to send test event: " + e.getMessage()))
                    .build();
        }
    }
}

// Made with Bob
