package com.causa.api.dto.response;

import com.causa.core.domain.AuthConfig;
import com.causa.core.domain.ExternalConfig;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * Response DTO for a single external config row (observability or integration platform).
 * Sensitive auth fields are already masked to {@code ********} by the service before mapping.
 *
 * @since 0.0.4
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExternalConfigResponse(

    @JsonProperty("id")
    String id,

    @JsonProperty("platform")
    String platform,

    @JsonProperty("name")
    String name,

    @JsonProperty("url")
    String url,

    @JsonProperty("is_active")
    boolean isActive,

    @JsonProperty("auth_config")
    AuthConfig authConfig,

    @JsonProperty("additional_config")
    Map<String, Object> additionalConfig,

    @JsonProperty("created_at")
    OffsetDateTime createdAt,

    @JsonProperty("updated_at")
    OffsetDateTime updatedAt
) {

    /** Maps a domain model whose sensitive fields have already been masked by the service. */
    public static ExternalConfigResponse from(ExternalConfig domain) {
        Map<String, Object> additional = domain.getAdditionalConfig();
        return new ExternalConfigResponse(
            domain.getId(),
            domain.getPlatform(),
            domain.getName(),
            domain.getUrl(),
            domain.isActive(),
            domain.getAuthConfig(),
            additional == null || additional.isEmpty() ? null : additional,
            domain.getCreatedAt(),
            domain.getUpdatedAt()
        );
    }
}
