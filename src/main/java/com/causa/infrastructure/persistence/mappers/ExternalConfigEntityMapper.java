package com.causa.infrastructure.persistence.mappers;

import com.causa.core.domain.AuthConfig;
import com.causa.core.domain.ExternalConfig;
import com.causa.infrastructure.persistence.entity.ExternalConfigEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.logging.Logger;

import java.util.Map;

/**
 * ExternalConfig Entity Mapper
 *
 * <p>Maps between the {@link ExternalConfig} domain model and {@link ExternalConfigEntity} JPA entity.
 *
 * <p>The {@code auth_config} JSONB column is serialised/deserialised via Jackson — the
 * {@link AuthConfig} record is mapped to/from {@link JsonNode}. Sensitive field
 * encryption and masking are the responsibility of the <em>service layer</em>; this mapper
 * performs only structural conversion.
 *
 * @since 0.0.3
 */
public final class ExternalConfigEntityMapper {

    private static final Logger LOG = Logger.getLogger(ExternalConfigEntityMapper.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private ExternalConfigEntityMapper() {}

    /**
     * Converts a domain {@link ExternalConfig} to an {@link ExternalConfigEntity} ready for persistence.
     *
     * <p>The caller is responsible for ensuring that sensitive fields in {@code authConfig} are
     * already encrypted before invoking this method.
     *
     * @param domain the domain model (non-null)
     * @return the corresponding JPA entity
     */
    public static ExternalConfigEntity toEntity(ExternalConfig domain) {
        if (domain == null) return null;

        ExternalConfigEntity entity = new ExternalConfigEntity();
        entity.setId(domain.getId());
        entity.setCategory(domain.getCategory());
        entity.setPlatform(domain.getPlatform());
        entity.setName(domain.getName());
        entity.setUrl(domain.getUrl());
        entity.setIsActive(domain.isActive());
        entity.setAuthConfig(serialiseAuthConfig(domain.getAuthConfig()));

        if (domain.getAdditionalConfig() != null && !domain.getAdditionalConfig().isEmpty()) {
            entity.setAdditionalConfig(serialiseAdditionalConfig(domain.getAdditionalConfig()));
        }

        return entity;
    }

    /**
     * Converts an {@link ExternalConfigEntity} to a domain {@link ExternalConfig}.
     *
     * <p>Deserialises the {@code auth_config} JSONB into an {@link AuthConfig} record. If
     * deserialisation fails (e.g. unexpected schema), the {@code authConfig} component is
     * constructed with all-null fields rather than throwing — preserving the rest of the data.
     *
     * @param entity the JPA entity (non-null)
     * @return the corresponding domain model
     */
    public static ExternalConfig toDomain(ExternalConfigEntity entity) {
        if (entity == null) return null;

        AuthConfig authConfig = deserialiseAuthConfig(entity.getAuthConfig());
        Map<String, Object> additionalConfig = deserialiseAdditionalConfig(entity.getAdditionalConfig());

        return ExternalConfig.builder()
            .id(entity.getId())
            .category(entity.getCategory())
            .platform(entity.getPlatform())
            .name(entity.getName())
            .url(entity.getUrl())
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
            LOG.warnf("Failed to deserialise auth_config for external config — returning empty AuthConfig. Error: %s", e.getMessage());
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
            LOG.warnf("Failed to deserialise additional_config for external config — returning empty map. Error: %s", e.getMessage());
            return Map.of();
        }
    }
}
