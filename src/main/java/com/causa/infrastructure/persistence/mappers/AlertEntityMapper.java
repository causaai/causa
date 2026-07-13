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
        entity.setId(alert.getAlertId());
        entity.setSourceAlertId(alert.getAlertId());  // Using alertId as sourceAlertId for now
        entity.setAlertName(alert.getAlertName());
        entity.setAlertTimestamp(alert.getTimestamp() != null ?
            java.time.OffsetDateTime.ofInstant(alert.getTimestamp(), java.time.ZoneOffset.UTC) : null);
        entity.setSeverity(alert.getSeverity().getValue());
        entity.setStatus(alert.getStatus().getValue());
        entity.setContainerName(alert.getContainerName());

        // Build containerInfo JSON from pod/namespace/container
        com.fasterxml.jackson.databind.node.ObjectNode containerInfo =
            com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        if (alert.getPodName() != null) {
            containerInfo.put("pod", alert.getPodName());
        }
        if (alert.getNamespace() != null) {
            containerInfo.put("namespace", alert.getNamespace());
        }
        if (alert.getContainerName() != null) {
            containerInfo.put("name", alert.getContainerName());
        }
        entity.setContainerInfo(containerInfo);

        // Build alertMetadata JSON from labels and annotations
        com.fasterxml.jackson.databind.node.ObjectNode metadata =
            com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        metadata.set("labels", JsonUtils.mapToJsonNode(alert.getLabels()));
        metadata.set("annotations", JsonUtils.mapToJsonNode(alert.getAnnotations()));
        entity.setAlertMetadata(metadata);

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

        // Extract pod and namespace from containerInfo JSON
        String podName = null;
        String namespace = null;
        if (entity.getContainerInfo() != null) {
            com.fasterxml.jackson.databind.JsonNode containerInfo = entity.getContainerInfo();
            if (containerInfo.has("pod")) {
                podName = containerInfo.get("pod").asText();
            }
            if (containerInfo.has("namespace")) {
                namespace = containerInfo.get("namespace").asText();
            }
        }

        // Extract labels and annotations from alertMetadata JSON
        java.util.Map<String, String> labels = java.util.Map.of();
        java.util.Map<String, String> annotations = java.util.Map.of();
        if (entity.getAlertMetadata() != null) {
            com.fasterxml.jackson.databind.JsonNode metadata = entity.getAlertMetadata();
            if (metadata.has("labels")) {
                labels = JsonUtils.jsonNodeToMap(metadata.get("labels"));
            }
            if (metadata.has("annotations")) {
                annotations = JsonUtils.jsonNodeToMap(metadata.get("annotations"));
            }
        }

        return Alert.builder()
            .alertId(entity.getId())
            .timestamp(entity.getAlertTimestamp() != null ?
                entity.getAlertTimestamp().toInstant() : null)
            .alertName(entity.getAlertName())
            .severity(AlertSeverity.fromString(entity.getSeverity()))
            .podName(podName)
            .containerName(entity.getContainerName())
            .namespace(namespace)
            .status(AlertStatus.fromString(entity.getStatus()))
            .hasDiagnostics(false)  // This field is not in the new entity structure
            .labels(labels)
            .annotations(annotations)
            .build();
    }

}
