package com.causa.infrastructure.persistence.mappers;

import com.causa.core.domain.AuthConfig;
import com.causa.core.domain.LlmConfig;
import com.causa.infrastructure.persistence.entity.LlmConfigEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * LlmConfig Entity Mapper
 *
 * <p>Maps between the {@link LlmConfig} domain model and {@link LlmConfigEntity} JPA entity.
 *
 * <p>The {@code auth_config} JSONB column is serialised/deserialised via Jackson — the
 * {@link AuthConfig} record is mapped to/from {@link JsonNode}. Sensitive field
 * encryption and masking are the responsibility of the <em>service layer</em>; this mapper
 * performs only structural conversion.
 *
 * <p>The {@code models} column is stored as a PostgreSQL {@code TEXT[]} array and exposed as
 * {@code List<String>} on the domain model.
 *
 * @since 0.0.3
 */
public final class LlmConfigEntityMapper {

    private static final Logger LOG = Logger.getLogger(LlmConfigEntityMapper.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private LlmConfigEntityMapper() {}

    /**
     * Converts a domain {@link LlmConfig} to an {@link LlmConfigEntity} ready for persistence.
     *
     * <p>The caller is responsible for ensuring that sensitive fields in {@code authConfig} are
     * already encrypted before invoking this method.
     *
     * @param domain the domain model (non-null)
     * @return the corresponding JPA entity
     */
    public static LlmConfigEntity toEntity(LlmConfig domain) {
        if (domain == null) return null;

        LlmConfigEntity entity = new LlmConfigEntity();
        entity.setId(domain.getId());
        entity.setProvider(domain.getProvider());
        entity.setUrl(domain.getUrl());
        entity.setModels(domain.getModels() != null ? domain.getModels().toArray(new String[0]) : new String[0]);
        entity.setTemperature(domain.getTemperature());
        entity.setMaxTokens(domain.getMaxTokens());
        entity.setTimeoutMs(domain.getTimeoutMs());
        entity.setIsActive(domain.isActive());
        entity.setAuthConfig(serialiseAuthConfig(domain.getAuthConfig()));

        if (domain.getAdditionalConfig() != null && !domain.getAdditionalConfig().isEmpty()) {
            entity.setAdditionalConfig(serialiseAdditionalConfig(domain.getAdditionalConfig()));
        }

        return entity;
    }

    /**
     * Converts an {@link LlmConfigEntity} to a domain {@link LlmConfig}.
     *
     * <p>Deserialises the {@code auth_config} JSONB into an {@link AuthConfig} record. If
     * deserialisation fails (e.g. unexpected schema), the {@code authConfig} component is
     * constructed with all-null fields rather than throwing — preserving the rest of the data.
     *
     * @param entity the JPA entity (non-null)
     * @return the corresponding domain model
     */
    public static LlmConfig toDomain(LlmConfigEntity entity) {
        if (entity == null) return null;

        AuthConfig authConfig = deserialiseAuthConfig(entity.getAuthConfig());
        Map<String, Object> additionalConfig = deserialiseAdditionalConfig(entity.getAdditionalConfig());
        List<String> models = entity.getModels() != null ? Arrays.asList(entity.getModels()) : List.of();

        return LlmConfig.builder()
            .id(entity.getId())
            .provider(entity.getProvider())
            .url(entity.getUrl())
            .models(models)
            .temperature(entity.getTemperature())
            .maxTokens(entity.getMaxTokens())
            .timeoutMs(entity.getTimeoutMs())
            .isActive(Boolean.TRUE.equals(entity.getIsActive()))
            .authConfig(authConfig)
            .additionalConfig(additionalConfig)
            .createdAt(entity.createdAt)
            .updatedAt(entity.updatedAt)
            .build();
    }

    // -------------------------------------------------------------------------
    // Serialise helpers — used by the repository to avoid a second ObjectMapper
    // -------------------------------------------------------------------------

    /** Serialises an {@link AuthConfig} to a {@link JsonNode} for JSONB storage. */
    public static JsonNode serialiseAuthConfig(AuthConfig authConfig) {
        return MAPPER.valueToTree(authConfig);
    }

    /** Serialises an additional-config map to a {@link JsonNode} for JSONB storage. */
    public static JsonNode serialiseAdditionalConfig(Map<String, Object> additionalConfig) {
        return MAPPER.valueToTree(additionalConfig);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static AuthConfig deserialiseAuthConfig(JsonNode node) {
        if (node == null || node.isNull()) {
            return new AuthConfig(null, null, null, null, null, null, null, null);
        }
        try {
            return MAPPER.treeToValue(node, AuthConfig.class);
        } catch (Exception e) {
            LOG.warnf("Failed to deserialise auth_config for llm config — returning empty AuthConfig. Error: %s", e.getMessage());
            return new AuthConfig(null, null, null, null, null, null, null, null);
        }
    }

    private static Map<String, Object> deserialiseAdditionalConfig(JsonNode node) {
        if (node == null || node.isNull()) {
            return Map.of();
        }
        try {
            return MAPPER.convertValue(node, MAP_TYPE);
        } catch (Exception e) {
            LOG.warnf("Failed to deserialise additional_config for llm config — returning empty map. Error: %s", e.getMessage());
            return Map.of();
        }
    }
}
