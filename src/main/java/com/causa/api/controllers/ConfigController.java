package com.causa.api.controllers;

import com.causa.api.dto.request.ConfigUpdateRequest;
import com.causa.api.dto.request.ExternalConfigRequest;
import com.causa.api.dto.request.LlmConfigRequest;
import com.causa.api.dto.response.ConfigResponse;
import com.causa.api.dto.response.ConfigSettingsResponse;
import com.causa.api.dto.response.ConfigUpdateResponse;
import com.causa.api.dto.response.ExternalConfigResponse;
import com.causa.api.dto.response.LlmConfigResponse;
import com.causa.common.constants.ApiConstants.Paths.Configs;
import com.causa.common.constants.ConfigConstants;
import com.causa.common.constants.ConfigConstants.PlatformCategory;
import com.causa.common.logging.CausaLogger;
import com.causa.common.utils.ValidationUtils;
import com.causa.core.domain.ExternalConfig;
import com.causa.core.domain.LlmConfig;
import com.causa.core.services.ConfigService;
import com.causa.core.services.ExternalConfigService;
import com.causa.core.services.LlmConfigService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration Management REST Controller
 *
 * <p>Single controller for all runtime-configurable application settings, organised
 * under {@code /api/v1/configs}.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET    /api/v1/configs}                                    — combined snapshot (observability + llm + integrations + generic)</li>
 *   <li>{@code GET    /api/v1/configs/generic}                            — list all generic key-value configs (optional ?category filter)</li>
 *   <li>{@code GET    /api/v1/configs/generic/{key}}                      — single generic config by key</li>
 *   <li>{@code PUT    /api/v1/configs/generic}                            — upsert one or multiple generic config values</li>
 *   <li>{@code DELETE /api/v1/configs/generic/{key}}                      — delete a generic config entry</li>
 *   <li>{@code GET    /api/v1/configs/observability}                      — list observability platform configs</li>
 *   <li>{@code PUT    /api/v1/configs/observability/{platform}}           — upsert observability config</li>
 *   <li>{@code DELETE /api/v1/configs/observability/{platform}/{name}}    — delete observability config</li>
 *   <li>{@code GET    /api/v1/configs/llm}                                — list LLM provider configs</li>
 *   <li>{@code PUT    /api/v1/configs/llm/{provider}}                     — upsert LLM provider config</li>
 *   <li>{@code DELETE /api/v1/configs/llm/{provider}}                     — delete LLM provider config</li>
 *   <li>{@code GET    /api/v1/configs/integrations}                       — list integration platform configs</li>
 *   <li>{@code PUT    /api/v1/configs/integrations/{platform}}            — upsert integration config</li>
 *   <li>{@code DELETE /api/v1/configs/integrations/{platform}/{name}}     — delete integration config</li>
 * </ul>
 *
 * <p>{@link com.causa.common.exceptions.ConfigException} is handled globally by
 * {@link com.causa.common.exceptions.GlobalExceptionMapper} — no try/catch needed here.
 *
 * @since 0.0.1
 */
@Path(Configs.BASE)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ConfigController {

    private static final CausaLogger log = CausaLogger.getLogger(ConfigController.class);

    private final ConfigService configService;
    private final ExternalConfigService externalConfigService;
    private final LlmConfigService llmConfigService;

    @Inject
    public ConfigController(ConfigService configService,
                            ExternalConfigService externalConfigService,
                            LlmConfigService llmConfigService) {
        this.configService         = configService;
        this.externalConfigService = externalConfigService;
        this.llmConfigService      = llmConfigService;
    }

    // -------------------------------------------------------------------------
    // Combined snapshot  —  GET /configs
    // -------------------------------------------------------------------------

    /**
     * GET /api/v1/configs
     * Returns a combined snapshot of all configuration categories.
     *
     * @return one object each for observability, integrations, llm, and generic
     */
    @GET
    public Response getAllConfigs() {
        log.info("GET /api/v1/configs").log();
        ConfigSettingsResponse snapshot = new ConfigSettingsResponse(
            toExternalResponses(externalConfigService.listByCategory(PlatformCategory.OBSERVABILITY)),
            toExternalResponses(externalConfigService.listByCategory(PlatformCategory.INTEGRATION)),
            toLlmResponses(llmConfigService.listAll()),
            toGenericResponses()
        );
        return Response.ok(snapshot).build();
    }

    // -------------------------------------------------------------------------
    // Generic key-value configs  —  /configs/generic
    // -------------------------------------------------------------------------

    /**
     * GET /api/v1/configs/generic
     * Lists generic (key-value) configuration entries, optionally filtered by category.
     *
     * @param category optional category filter (llm, alerts, cluster)
     * @return flat list of generic config entries (sensitive values masked), or 400 for unknown category
     */
    @GET
    @Path(Configs.GENERIC_SEGMENT)
    public Response listGeneric(@QueryParam(Configs.QUERY_CATEGORY) String category) {
        log.info("GET /api/v1/configs/generic")
            .field(ConfigConstants.LogFields.CATEGORY, category)
            .log();

        if (category != null && !category.isBlank()
                && !ConfigConstants.VALID_CATEGORIES.contains(category.toLowerCase())) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Unknown category: " + category +
                        ". Valid categories: " + ConfigConstants.VALID_CATEGORIES)
                .build();
        }

        List<ConfigResponse> response = (category != null && !category.isBlank()
                ? configService.getByCategory(category.toLowerCase())
                : configService.getAll())
            .stream()
            .map(e -> ConfigResponse.of(e.key(), e.value(), e.encrypted()))
            .toList();

        return Response.ok(response).build();
    }

    /**
     * GET /api/v1/configs/generic/{key}
     * Retrieves a single generic configuration value by key.
     *
     * @param key the configuration key
     * @return the config entry (sensitive values masked), or 400 for unknown key
     */
    @GET
    @Path(Configs.GENERIC_BY_KEY)
    public Response getGenericConfig(@PathParam(Configs.PATH_PARAM_KEY) String key) {
        log.info("GET /api/v1/configs/generic/{key}")
            .field(ConfigConstants.LogFields.CONFIG_KEY, key)
            .log();

        if (!ConfigConstants.isValidKey(key)) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Unknown config key: " + key)
                .build();
        }

        String value = configService.get(key).orElse(null);
        return Response.ok(ConfigResponse.of(key, value)).build();
    }

    /**
     * PUT /api/v1/configs/generic
     * Upserts one or multiple generic configuration values.
     * Valid entries are persisted and returned in {@code updated}; invalid entries
     * are returned in {@code rejected} and skipped.
     *
     * @param request map of config key-value pairs to upsert
     * @return updated keys that were applied and rejected keys that failed validation
     */
    @PUT
    @Path(Configs.GENERIC_SEGMENT)
    public Response upsertGenericConfigs(ConfigUpdateRequest request) {
        if (request.configs() == null || request.configs().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("No configs provided")
                .build();
        }

        log.info("PUT /api/v1/configs/generic")
            .field("keys_count", request.configs().size())
            .log();

        List<ConfigResponse> updated = new ArrayList<>();
        List<ConfigUpdateResponse.RejectedConfig> rejected = new ArrayList<>();

        for (var entry : request.configs().entrySet()) {
            String key   = entry.getKey();
            String value = entry.getValue();

            if (!ConfigConstants.isValidKey(key)) {
                rejected.add(new ConfigUpdateResponse.RejectedConfig(key, "Unknown config key"));
                continue;
            }

            if (ConfigConstants.isEnvOnly(key)) {
                rejected.add(new ConfigUpdateResponse.RejectedConfig(key,
                    key + " cannot be updated at runtime. Set it via environment variable or configmap during startup."));
                continue;
            }

            if (value == null || value.isBlank()) {
                rejected.add(new ConfigUpdateResponse.RejectedConfig(key, "Value must not be blank"));
                continue;
            }

            if (ConfigConstants.isIntegerKey(key) && !ValidationUtils.isValidInteger(value)) {
                rejected.add(new ConfigUpdateResponse.RejectedConfig(key, "Expected an integer value"));
                continue;
            }
            if (ConfigConstants.isDoubleKey(key) && !ValidationUtils.isValidDouble(value)) {
                rejected.add(new ConfigUpdateResponse.RejectedConfig(key, "Expected a numeric (double) value"));
                continue;
            }
            if (ConfigConstants.isBooleanKey(key) && !ValidationUtils.isValidBoolean(value)) {
                rejected.add(new ConfigUpdateResponse.RejectedConfig(key, "Expected a boolean value (true or false)"));
                continue;
            }

            configService.update(key, value);
            updated.add(ConfigResponse.of(key, value));
        }

        return Response.ok(new ConfigUpdateResponse(updated, rejected)).build();
    }

    /**
     * DELETE /api/v1/configs/generic/{key}
     * Deletes a generic configuration entry.
     *
     * @param key the configuration key
     * @return 204 No Content on success, 400 for unknown or env-only key
     */
    @DELETE
    @Path(Configs.GENERIC_BY_KEY)
    public Response deleteGenericConfig(@PathParam(Configs.PATH_PARAM_KEY) String key) {
        log.info("DELETE /api/v1/configs/generic/{key}")
            .field(ConfigConstants.LogFields.CONFIG_KEY, key)
            .log();

        if (!ConfigConstants.isValidKey(key)) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Unknown config key: " + key)
                .build();
        }

        if (ConfigConstants.isEnvOnly(key)) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity(key + " cannot be deleted. It is managed via environment variable or configmap.")
                .build();
        }

        configService.delete(key);
        return Response.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Observability  —  /configs/observability
    // -------------------------------------------------------------------------

    /**
     * GET /api/v1/configs/observability
     * Lists all observability platform configurations.
     */
    @GET
    @Path(Configs.OBSERVABILITY_SEGMENT)
    public Response listObservability() {
        log.info("GET /api/v1/configs/observability").log();
        return Response.ok(
            toExternalResponses(externalConfigService.listByCategory(PlatformCategory.OBSERVABILITY))
        ).build();
    }

    /**
     * PUT /api/v1/configs/observability/{platform}
     * Upserts an observability platform configuration.
     */
    @PUT
    @Path(Configs.OBSERVABILITY_SEGMENT + Configs.BY_PLATFORM)
    public Response upsertObservability(
            @PathParam(Configs.PATH_PARAM_PLATFORM) String platform,
            ExternalConfigRequest request) {

        log.info("PUT /api/v1/configs/observability/{platform}")
            .field("platform", platform).log();

        ExternalConfig saved = externalConfigService.upsert(PlatformCategory.OBSERVABILITY, platform, request);
        return Response.ok(ExternalConfigResponse.from(saved)).build();
    }

    /**
     * DELETE /api/v1/configs/observability/{platform}/{name}
     * Deletes an observability platform configuration by platform and name.
     */
    @DELETE
    @Path(Configs.OBSERVABILITY_SEGMENT + Configs.BY_PLATFORM_AND_NAME)
    public Response deleteObservability(
            @PathParam(Configs.PATH_PARAM_PLATFORM) String platform,
            @PathParam("name")                      String name) {

        log.info("DELETE /api/v1/configs/observability/{platform}/{name}")
            .field("platform", platform).field("name", name).log();

        externalConfigService.delete(platform, name);
        return Response.noContent().build();
    }

    // -------------------------------------------------------------------------
    // LLM  —  /configs/llm
    // -------------------------------------------------------------------------

    /**
     * GET /api/v1/configs/llm
     * Lists all LLM provider configurations.
     */
    @GET
    @Path(Configs.LLM_SEGMENT)
    public Response listLlm() {
        log.info("GET /api/v1/configs/llm").log();
        return Response.ok(toLlmResponses(llmConfigService.listAll())).build();
    }

    /**
     * PUT /api/v1/configs/llm/{provider}
     * Upserts an LLM provider configuration.
     */
    @PUT
    @Path(Configs.LLM_SEGMENT + Configs.BY_PROVIDER)
    public Response upsertLlm(
            @PathParam(Configs.PATH_PARAM_PROVIDER) String provider,
            LlmConfigRequest request) {

        log.info("PUT /api/v1/configs/llm/{provider}")
            .field("provider", provider).log();

        LlmConfig saved = llmConfigService.upsert(provider, request);
        return Response.ok(LlmConfigResponse.from(saved)).build();
    }

    /**
     * DELETE /api/v1/configs/llm/{provider}
     * Deletes an LLM provider configuration.
     */
    @DELETE
    @Path(Configs.LLM_SEGMENT + Configs.BY_PROVIDER)
    public Response deleteLlm(@PathParam(Configs.PATH_PARAM_PROVIDER) String provider) {
        log.info("DELETE /api/v1/configs/llm/{provider}")
            .field("provider", provider).log();
        llmConfigService.delete(provider);
        return Response.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Integrations  —  /configs/integrations
    // -------------------------------------------------------------------------

    /**
     * GET /api/v1/configs/integrations
     * Lists all integration platform configurations.
     */
    @GET
    @Path(Configs.INTEGRATIONS_SEGMENT)
    public Response listIntegrations() {
        log.info("GET /api/v1/configs/integrations").log();
        return Response.ok(
            toExternalResponses(externalConfigService.listByCategory(PlatformCategory.INTEGRATION))
        ).build();
    }

    /**
     * PUT /api/v1/configs/integrations/{platform}
     * Upserts an integration platform configuration.
     */
    @PUT
    @Path(Configs.INTEGRATIONS_SEGMENT + Configs.BY_PLATFORM)
    public Response upsertIntegration(
            @PathParam(Configs.PATH_PARAM_PLATFORM) String platform,
            ExternalConfigRequest request) {

        log.info("PUT /api/v1/configs/integrations/{platform}")
            .field("platform", platform).log();

        ExternalConfig saved = externalConfigService.upsert(PlatformCategory.INTEGRATION, platform, request);
        return Response.ok(ExternalConfigResponse.from(saved)).build();
    }

    /**
     * DELETE /api/v1/configs/integrations/{platform}/{name}
     * Deletes an integration platform configuration by platform and name.
     */
    @DELETE
    @Path(Configs.INTEGRATIONS_SEGMENT + Configs.BY_PLATFORM_AND_NAME)
    public Response deleteIntegration(
            @PathParam(Configs.PATH_PARAM_PLATFORM) String platform,
            @PathParam("name")                      String name) {

        log.info("DELETE /api/v1/configs/integrations/{platform}/{name}")
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

    private List<ConfigResponse> toGenericResponses() {
        return configService.getAll().stream()
            .map(e -> ConfigResponse.of(e.key(), e.value(), e.encrypted()))
            .toList();
    }
}
