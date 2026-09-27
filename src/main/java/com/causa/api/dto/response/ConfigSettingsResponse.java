package com.causa.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Combined response for {@code GET /api/v1/configs} — one object per settings category.
 *
 * <p>Reuses the typed per-resource response records directly — no duplication.
 *
 * @since 0.0.4
 */
public record ConfigSettingsResponse(

    @JsonProperty("observability")
    List<ExternalConfigResponse> observability,

    @JsonProperty("integrations")
    List<ExternalConfigResponse> integrations,

    @JsonProperty("llm")
    List<LlmConfigResponse> llm,

    @JsonProperty("generic")
    List<ConfigResponse> generic
) {}
