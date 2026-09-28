package com.causa.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Combined response for {@code GET /api/v1/settings} — one object per settings category.
 *
 * <p>Reuses the typed per-resource response records directly — no duplication.
 * The {@code general} field is reserved for the Tooling tab and will be added
 * in a future PR without any change to this record's structure.
 *
 * @since 0.0.4
 */
public record ConfigSettingsResponse(

    @JsonProperty("observability")
    List<ExternalConfigResponse> observability,

    @JsonProperty("integrations")
    List<ExternalConfigResponse> integrations,

    @JsonProperty("llm")
    List<LlmConfigResponse> llm
) {}
