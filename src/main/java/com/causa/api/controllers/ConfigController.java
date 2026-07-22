package com.causa.api.controllers;

import com.causa.api.dto.request.ConfigUpdateRequest;
import com.causa.api.dto.response.ConfigResponse;
import com.causa.api.dto.response.ConfigUpdateResponse;
import com.causa.common.constants.ApiConstants;
import com.causa.common.constants.ConfigConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.common.utils.ValidationUtils;
import com.causa.core.ports.ConfigurationRepository;
import com.causa.core.services.ConfigService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration Management REST Controller
 *
 * <p>Endpoints for managing runtime-configurable application settings.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code GET /api/v1/configs} — list all configs (optional ?category filter)</li>
 *   <li>{@code GET /api/v1/configs/{key}} — single config by key</li>
 *   <li>{@code POST /api/v1/configs} — upsert config values</li>
 * </ul>
 *
 * @since 0.0.1
 */
@Path(ApiConstants.Paths.Configs.BASE)
@Produces(MediaType.APPLICATION_JSON)
public class ConfigController {

    private static final CausaLogger log = CausaLogger.getLogger(ConfigController.class);

    private final ConfigService configService;

    @Inject
    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    /**
     * GET /api/v1/configs
     * Lists all configuration entries, optionally filtered by category.
     *
     * @param category optional category filter (llm, alerts, cluster)
     * @return list of config entries (sensitive values masked), or 400 for an unknown category
     */
    @GET
    public Response listConfigs(@QueryParam(ApiConstants.Paths.Configs.QUERY_CATEGORY) String category) {
        log.info("GET /api/v1/configs")
            .field(ConfigConstants.LogFields.CATEGORY, category)
            .log();

        if (category != null && !category.isBlank()
                && !ConfigConstants.VALID_CATEGORIES.contains(category.toLowerCase())) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Unknown category: " + category +
                        ". Valid categories: " + ConfigConstants.VALID_CATEGORIES)
                .build();
        }

        List<ConfigurationRepository.ConfigEntry> entries = category != null && !category.isBlank()
            ? configService.getByCategory(category.toLowerCase())
            : configService.getAll();

        List<ConfigResponse> response = entries.stream()
            .map(e -> ConfigResponse.of(e.key(), e.value(), e.encrypted()))
            .toList();

        return Response.ok(response).build();
    }

    /**
     * GET /api/v1/configs/{key}
     * Retrieves a single configuration value by key.
     *
     * @param key the configuration key
     * @return the config entry (sensitive values masked)
     */
    @GET
    @Path(ApiConstants.Paths.Configs.BY_KEY)
    public Response getConfig(@PathParam(ApiConstants.Paths.Configs.PATH_PARAM_KEY) String key) {
        log.info("GET /api/v1/configs/{key}")
            .field(ConfigConstants.LogFields.CONFIG_KEY, key)
            .log();

        if (!ConfigConstants.isValidKey(key)) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("Unknown config key: " + key)
                .build();
        }

        String value = configService.get(key).orElse(null);
        ConfigResponse response = ConfigResponse.of(key, value);

        return Response.ok(response).build();
    }

    /**
     * POST /api/v1/configs
     * Upserts configuration values.
     * Entries are validated and applied individually: valid entries are persisted and returned
     * in {@code updated}, invalid entries are returned in {@code rejected} and skipped.
     *
     * @param request the config update request
     * @return updated keys that were applied and rejected keys that failed validation
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public Response updateConfigs(ConfigUpdateRequest request) {
        if (request.configs() == null || request.configs().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                .entity("No configs provided")
                .build();
        }

        log.info("POST /api/v1/configs")
            .field("keys_count", request.configs().size())
            .log();

        List<ConfigResponse> updated = new ArrayList<>();
        List<ConfigUpdateResponse.RejectedConfig> rejected = new ArrayList<>();

        for (var entry : request.configs().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            // Check if key is known
            if (!ConfigConstants.isValidKey(key)) {
                rejected.add(new ConfigUpdateResponse.RejectedConfig(key, "Unknown config key"));
                continue;
            }

            // Check if value is blank
            if (value == null || value.isBlank()) {
                rejected.add(new ConfigUpdateResponse.RejectedConfig(key, "Value must not be blank"));
                continue;
            }

            // Type validation
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

        ConfigUpdateResponse response = new ConfigUpdateResponse(updated, rejected);
        return Response.ok(response).build();
    }
}
