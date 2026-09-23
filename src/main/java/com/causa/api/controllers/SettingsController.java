package com.causa.api.controllers;

import com.causa.api.dto.request.ExternalConfigRequest;
import com.causa.api.dto.request.LlmConfigRequest;
import com.causa.api.dto.response.ExternalConfigResponse;
import com.causa.api.dto.response.LlmConfigResponse;
import com.causa.api.dto.response.ConfigSettingsResponse;
import com.causa.common.constants.ApiConstants.Paths.Settings;
import com.causa.common.constants.ConfigConstants.PlatformCategory;
import com.causa.common.logging.CausaLogger;
import com.causa.core.domain.ExternalConfig;
import com.causa.core.domain.LlmConfig;
import com.causa.core.services.ExternalConfigService;
import com.causa.core.services.LlmConfigService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

/**
 * Settings REST controller — {@code /api/v1/settings}.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET  /api/v1/settings}                              — combined snapshot</li>
 *   <li>{@code GET  /api/v1/settings/observability}                — list observability configs</li>
 *   <li>{@code PUT  /api/v1/settings/observability/{platform}}     — upsert observability config</li>
 *   <li>{@code DELETE /api/v1/settings/observability/{platform}/{name}} — delete observability config</li>
 *   <li>{@code GET  /api/v1/settings/llm}                          — list LLM provider configs</li>
 *   <li>{@code PUT  /api/v1/settings/llm/{provider}}               — upsert LLM provider config</li>
 *   <li>{@code DELETE /api/v1/settings/llm/{provider}}             — delete LLM provider config</li>
 *   <li>{@code GET  /api/v1/settings/integrations}                 — list integration configs</li>
 *   <li>{@code PUT  /api/v1/settings/integrations/{platform}}      — upsert integration config</li>
 *   <li>{@code DELETE /api/v1/settings/integrations/{platform}/{name}} — delete integration config</li>
 * </ul>
 *
 * <p>{@link ConfigException} is handled globally by {@link com.causa.common.exceptions.GlobalExceptionMapper}
 * — no try/catch needed here.
 *
 * @since 0.0.4
 */
@Path(Settings.BASE)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SettingsController {

    private static final CausaLogger log = CausaLogger.getLogger(SettingsController.class);

    private final ExternalConfigService externalConfigService;
    private final LlmConfigService llmConfigService;

    @Inject
    public SettingsController(ExternalConfigService externalConfigService,
                              LlmConfigService llmConfigService) {
        this.externalConfigService = externalConfigService;
        this.llmConfigService      = llmConfigService;
    }

    // -------------------------------------------------------------------------
    // Combined
    // -------------------------------------------------------------------------

    @GET
    public Response getAll() {
        log.info("GET /api/v1/settings").log();
        ConfigSettingsResponse snapshot = new ConfigSettingsResponse(
            toExternalResponses(externalConfigService.listByCategory(PlatformCategory.OBSERVABILITY)),
            toExternalResponses(externalConfigService.listByCategory(PlatformCategory.INTEGRATION)),
            toLlmResponses(llmConfigService.listAll())
        );
        return Response.ok(snapshot).build();
    }

    // -------------------------------------------------------------------------
    // Observability
    // -------------------------------------------------------------------------

    @GET
    @Path(Settings.OBSERVABILITY_SEGMENT)
    public Response listObservability() {
        log.info("GET /api/v1/settings/observability").log();
        List<ExternalConfigResponse> configs =
            toExternalResponses(externalConfigService.listByCategory(PlatformCategory.OBSERVABILITY));
        return Response.ok(configs).build();
    }

    @PUT
    @Path(Settings.OBSERVABILITY_SEGMENT + Settings.BY_PLATFORM)
    public Response upsertObservability(
            @PathParam(Settings.PATH_PARAM_PLATFORM) String platform,
            ExternalConfigRequest request) {

        log.info("PUT /api/v1/settings/observability/{platform}")
            .field("platform", platform).log();

        ExternalConfig saved = externalConfigService.upsert(PlatformCategory.OBSERVABILITY, platform, request);
        return Response.ok(ExternalConfigResponse.from(saved)).build();
    }

    @DELETE
    @Path(Settings.OBSERVABILITY_SEGMENT + Settings.BY_PLATFORM_AND_NAME)
    public Response deleteObservability(
            @PathParam(Settings.PATH_PARAM_PLATFORM) String platform,
            @PathParam("name")                       String name) {

        log.info("DELETE /api/v1/settings/observability/{platform}/{name}")
            .field("platform", platform).field("name", name).log();

        externalConfigService.delete(platform, name);
        return Response.noContent().build();
    }

    // -------------------------------------------------------------------------
    // LLM
    // -------------------------------------------------------------------------

    @GET
    @Path(Settings.LLM_SEGMENT)
    public Response listLlm() {
        log.info("GET /api/v1/settings/llm").log();
        return Response.ok(toLlmResponses(llmConfigService.listAll())).build();
    }

    @PUT
    @Path(Settings.LLM_SEGMENT + Settings.BY_PROVIDER)
    public Response upsertLlm(
            @PathParam(Settings.PATH_PARAM_PROVIDER) String provider,
            LlmConfigRequest request) {

        log.info("PUT /api/v1/settings/llm/{provider}")
            .field("provider", provider).log();

        LlmConfig saved = llmConfigService.upsert(provider, request);
        return Response.ok(LlmConfigResponse.from(saved)).build();
    }

    @DELETE
    @Path(Settings.LLM_SEGMENT + Settings.BY_PROVIDER)
    public Response deleteLlm(@PathParam(Settings.PATH_PARAM_PROVIDER) String provider) {
        log.info("DELETE /api/v1/settings/llm/{provider}")
            .field("provider", provider).log();
        llmConfigService.delete(provider);
        return Response.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Integrations
    // -------------------------------------------------------------------------

    @GET
    @Path(Settings.INTEGRATIONS_SEGMENT)
    public Response listIntegrations() {
        log.info("GET /api/v1/settings/integrations").log();
        List<ExternalConfigResponse> configs =
            toExternalResponses(externalConfigService.listByCategory(PlatformCategory.INTEGRATION));
        return Response.ok(configs).build();
    }

    @PUT
    @Path(Settings.INTEGRATIONS_SEGMENT + Settings.BY_PLATFORM)
    public Response upsertIntegration(
            @PathParam(Settings.PATH_PARAM_PLATFORM) String platform,
            ExternalConfigRequest request) {

        log.info("PUT /api/v1/settings/integrations/{platform}")
            .field("platform", platform).log();

        ExternalConfig saved = externalConfigService.upsert(PlatformCategory.INTEGRATION, platform, request);
        return Response.ok(ExternalConfigResponse.from(saved)).build();
    }

    @DELETE
    @Path(Settings.INTEGRATIONS_SEGMENT + Settings.BY_PLATFORM_AND_NAME)
    public Response deleteIntegration(
            @PathParam(Settings.PATH_PARAM_PLATFORM) String platform,
            @PathParam("name")                       String name) {

        log.info("DELETE /api/v1/settings/integrations/{platform}/{name}")
            .field("platform", platform).field("name", name).log();

        externalConfigService.delete(platform, name);
        return Response.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<ExternalConfigResponse> toExternalResponses(List<ExternalConfig> configs) {
        return configs.stream().map(ExternalConfigResponse::from).toList();
    }

    private List<LlmConfigResponse> toLlmResponses(List<LlmConfig> configs) {
        return configs.stream().map(LlmConfigResponse::from).toList();
    }
}
