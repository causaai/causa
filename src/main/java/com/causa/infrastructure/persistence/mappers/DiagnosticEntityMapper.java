package com.causa.infrastructure.persistence.mappers;

import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import com.causa.common.constants.DiagnosticConstants.FaultDomain;
import com.causa.core.domain.Diagnostic;
import com.causa.infrastructure.persistence.entity.AlertEntity;
import com.causa.infrastructure.persistence.entity.DiagnosticEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Diagnostic Entity Mapper
 *
 * <p>Maps between Diagnostic domain model and DiagnosticEntity JPA entity.
 * <p>Handles JSON conversion between String (domain) and JsonNode (entity).
 *
 * @since 0.0.1
 */
public final class DiagnosticEntityMapper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private DiagnosticEntityMapper() {
        // Prevent instantiation
    }

    /**
     * Converts domain Diagnostic to DiagnosticEntity.
     *
     * @param diagnostic the domain diagnostic
     * @return the diagnostic entity
     */
    public static DiagnosticEntity toEntity(Diagnostic diagnostic) {
        if (diagnostic == null) {
            return null;
        }

        DiagnosticEntity entity = new DiagnosticEntity();
        entity.setId(diagnostic.getDiagnosticId());

        // Create AlertEntity reference for the foreign key relationship
        AlertEntity alertEntity = new AlertEntity();
        alertEntity.setId(diagnostic.getAlertId());
        entity.setAlert(alertEntity);

        entity.setStatus(diagnostic.getStatus().getValue());

        // Map rootCauseAnalysis to rootCauseSummary for now
        if (diagnostic.getRootCauseAnalysis() != null) {
            entity.setRootCauseSummary(diagnostic.getRootCauseAnalysis());
        }

        // Map confidenceScore to confidenceInfo JSON
        if (diagnostic.getConfidenceScore() != null) {
            com.fasterxml.jackson.databind.node.ObjectNode confidenceInfo =
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
            confidenceInfo.put("rca_score", diagnostic.getConfidenceScore());
            entity.setConfidenceInfo(confidenceInfo);
        }

        return entity;
    }

    /**
     * Converts DiagnosticEntity to domain Diagnostic.
     *
     * @param entity the diagnostic entity
     * @return the domain diagnostic
     */
    public static Diagnostic toDomain(DiagnosticEntity entity) {
        if (entity == null) {
            return null;
        }

        // Extract confidence score from confidenceInfo JSON
        Float confidenceScore = null;
        if (entity.getConfidenceInfo() != null && entity.getConfidenceInfo().has("rca_score")) {
            confidenceScore = (float) entity.getConfidenceInfo().get("rca_score").asDouble();
        }

        Diagnostic.Builder builder = Diagnostic.builder()
            .diagnosticId(entity.getId())
            .alertId(entity.getAlertId())  // Uses helper method to avoid loading full Alert
            .status(DiagnosticStatus.fromString(entity.getStatus()))
            .generatedAt(entity.createdAt != null ?
                entity.createdAt.toInstant() : java.time.Instant.now())
            .confidenceScore(confidenceScore);

        // Map rootCauseSummary back to rootCauseAnalysis
        if (entity.getRootCauseSummary() != null) {
            builder.rootCauseAnalysis(entity.getRootCauseSummary());
        }

        return builder.build();
    }
}
