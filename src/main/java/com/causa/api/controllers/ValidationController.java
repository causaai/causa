package com.causa.api.controllers;

import com.causa.api.dto.response.SimplifiedValidationResponse;
import com.causa.api.dto.response.ErrorResponse;
import com.causa.api.dto.response.MockValidationData;
import com.causa.common.constants.ApiConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.core.domain.Diagnostic;
import com.causa.core.services.DiagnosticService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Optional;

/**
 * Validation API Controller
 *
 * <p>REST endpoint for accessing detailed validation information.
 * <ul>
 *   <li>GET /api/v1/validations?diagnosticId={id} — detailed validation results</li>
 * </ul>
 *
 * <p>The main diagnostic API returns only final verdict summary.
 * This validation API provides full validation schema including:
 * <ul>
 *   <li>Assertion-based validation results with evidence</li>
 *   <li>Rule-based validation results with matched rules</li>
 *   <li>Final verdict with aggregation details</li>
 *   <li>User-friendly explanation</li>
 * </ul>
 *
 * @since 0.0.1
 */
@Path("/")
@Produces(MediaType.APPLICATION_JSON)
public class ValidationController {

    private static final CausaLogger log = CausaLogger.getLogger(ValidationController.class);

    private final DiagnosticService diagnosticService;
    private final ObjectMapper objectMapper;

    @Inject
    public ValidationController(DiagnosticService diagnosticService, ObjectMapper objectMapper) {
        this.diagnosticService = diagnosticService;
        this.objectMapper = objectMapper;
    }

    /**
     * GET /api/v1/validations?diagnosticId={id}
     *
     * <p>Returns detailed validation information for a diagnostic.
     *
     * @param diagnosticId the diagnostic ID (query parameter)
     * @return 200 with ValidationDetailResponse, 404 if not found, 400 if validation not available
     */
    @GET
    @Path(ApiConstants.Paths.Validations.BASE)
    public Response getValidationDetails(@QueryParam("diagnosticId") String diagnosticId) {
        log.info("Validation detail request received")
            .field("diagnosticId", diagnosticId)
            .log();

        // Validate input
        if (diagnosticId == null || diagnosticId.isBlank()) {
            log.warn("Validation request missing diagnosticId parameter").log();
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.of(400, "Bad Request", "diagnosticId query parameter is required"))
                .build();
        }

        // Get diagnostic
        Optional<Diagnostic> diagnosticOpt = diagnosticService.getDiagnosticById(diagnosticId);
        if (diagnosticOpt.isEmpty()) {
            log.warn("Diagnostic not found")
                .field("diagnosticId", diagnosticId)
                .log();
            return Response.status(Response.Status.NOT_FOUND)
                .entity(ErrorResponse.of(404, "Not Found", "Diagnostic not found: " + diagnosticId))
                .build();
        }

        Diagnostic diagnostic = diagnosticOpt.get();

        // Check if validation data is available
        if (diagnostic.getValidationData() == null || diagnostic.getValidationData().isBlank()) {
            log.warn("Validation data not available for diagnostic")
                .field("diagnosticId", diagnosticId)
                .log();
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(ErrorResponse.of(400, "Bad Request", "Validation data not available for diagnostic: " + diagnosticId))
                .build();
        }

        // Build simplified validation response
        SimplifiedValidationResponse response;
        try {
            response = SimplifiedValidationResponse.from(
                diagnostic.getValidationData(),
                objectMapper
            );
        } catch (Exception e) {
            log.error("Failed to parse validation data")
                .field("diagnosticId", diagnosticId)
                .exception(e)
                .log();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ErrorResponse.of(500, "Internal Server Error", "Failed to parse validation data"))
                .build();
        }

        log.info("Validation detail retrieved")
            .field("diagnosticId", diagnosticId)
            .field("finalStatus", response.finalVerdict() != null ? response.finalVerdict().status() : "null")
            .log();

        return Response.ok(response).build();
    }

    /**
     * GET /api/v1/validations/mock
     *
     * <p>Returns mock validation data for UI testing.
     * Uses hardcoded OOM scenario with complete validation.
     *
     * @return 200 with SimplifiedValidationResponse
     */
    @GET
    @Path("/validations/mock")
    public Response getMockValidation() {
        log.info("Mock validation request received").log();

        try {
            SimplifiedValidationResponse response = SimplifiedValidationResponse.from(
                MockValidationData.getOomValidationJson(),
                objectMapper
            );

            log.info("Mock validation response created")
                .field("finalStatus", response.finalVerdict() != null ? response.finalVerdict().status() : "null")
                .log();

            return Response.ok(response).build();
        } catch (Exception e) {
            log.error("Failed to parse mock validation data")
                .exception(e)
                .log();
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ErrorResponse.of(500, "Internal Server Error", "Failed to generate mock validation data"))
                .build();
        }
    }
}
