package com.causa.infrastructure.persistence.mappers;

import com.causa.common.constants.AlertConstants.AlertSeverity;
import com.causa.common.constants.AlertConstants.AlertStatus;
import com.causa.common.utils.JsonUtils;
import com.causa.core.domain.Alert;
import com.causa.infrastructure.persistence.entity.AlertEntity;

/**
 * Alert Entity Mapper
 *
 * <p>Maps between Alert domain model and AlertEntity JPA entity.
 * <p>Uses JsonUtils for Map&lt;String, String&gt; ↔ JsonNode conversion.
 *
 * @since 0.0.1
 */
public final class AlertEntityMapper {

    private AlertEntityMapper() {
        // Prevent instantiation
    }

    /**
     * Converts domain Alert to AlertEntity.
     *
     * @param alert the domain alert
     * @return the alert entity
     */
    public static AlertEntity toEntity(Alert alert) {
        if (alert == null) {
            return null;
        }

        AlertEntity entity = new AlertEntity();
        entity.setAlertId(alert.getAlertId());
        entity.setTimestamp(alert.getTimestamp());
        entity.setAlertName(alert.getAlertName());
        entity.setSeverity(alert.getSeverity().getValue());
        entity.setPodName(alert.getPodName());
        entity.setContainerName(alert.getContainerName());
        entity.setNamespace(alert.getNamespace());
        entity.setStatus(alert.getStatus().getValue());
        entity.setHasDiagnostics(alert.hasDiagnostics());

        // Convert Map<String, String> to JsonNode for JSONB storage
        entity.setLabels(JsonUtils.mapToJsonNode(alert.getLabels()));
        entity.setAnnotations(JsonUtils.mapToJsonNode(alert.getAnnotations()));

        return entity;
    }

    /**
     * Converts AlertEntity to domain Alert.
     *
     * @param entity the alert entity
     * @return the domain alert
     */
    public static Alert toDomain(AlertEntity entity) {
        if (entity == null) {
            return null;
        }

        return Alert.builder()
            .alertId(entity.getAlertId())
            .timestamp(entity.getTimestamp())
            .alertName(entity.getAlertName())
            .severity(AlertSeverity.fromString(entity.getSeverity()))
            .podName(entity.getPodName())
            .containerName(entity.getContainerName())
            .namespace(entity.getNamespace())
            .status(AlertStatus.fromString(entity.getStatus()))
            .hasDiagnostics(entity.getHasDiagnostics())
            .labels(JsonUtils.jsonNodeToMap(entity.getLabels()))
            .annotations(JsonUtils.jsonNodeToMap(entity.getAnnotations()))
            .build();
    }

}
