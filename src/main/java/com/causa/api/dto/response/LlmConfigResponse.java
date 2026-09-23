package com.causa.api.dto.response;

import com.causa.core.domain.AuthConfig;
import com.causa.core.domain.LlmConfig;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response DTO for a single LLM provider config row.
 * Sensitive auth fields are already masked to {@code ********} by the service before mapping.
 *
 * @since 0.0.4
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LlmConfigResponse(

    @JsonProperty("id")
    String id,

    @JsonProperty("provider")
    String provider,

    @JsonProperty("url")
    String url,

    @JsonProperty("models")
    List<String> models,

    @JsonProperty("temperature")
    BigDecimal temperature,

    @JsonProperty("max_tokens")
    Integer maxTokens,

    @JsonProperty("timeout_ms")
    Integer timeoutMs,

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
    public static LlmConfigResponse from(LlmConfig domain) {
        Map<String, Object> additional = domain.getAdditionalConfig();
        return new LlmConfigResponse(
            domain.getId(),
            domain.getProvider().name(),
            domain.getUrl(),
            domain.getModels(),
            domain.getTemperature(),
            domain.getMaxTokens(),
            domain.getTimeoutMs(),
            domain.isActive(),
            domain.getAuthConfig(),
            additional == null || additional.isEmpty() ? null : additional,
            domain.getCreatedAt(),
            domain.getUpdatedAt()
        );
    }
}
