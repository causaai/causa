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
        entity.setDiagnosticId(diagnostic.getDiagnosticId());

        // Create AlertEntity reference for the foreign key relationship
        AlertEntity alertEntity = new AlertEntity();
        alertEntity.setAlertId(diagnostic.getAlertId());
        entity.setAlert(alertEntity);

        entity.setStatus(diagnostic.getStatus().getValue());
        entity.setGeneratedAt(diagnostic.getGeneratedAt());
        entity.setConfidenceScore(diagnostic.getConfidenceScore());

        if (diagnostic.getFaultDomain() != null) {
            entity.setFaultDomain(diagnostic.getFaultDomain().getValue());
        }

        // Convert String JSON to JsonNode
        if (diagnostic.getRootCauseAnalysis() != null) {
            try {
                JsonNode jsonNode = objectMapper.readTree(diagnostic.getRootCauseAnalysis());
                entity.setRootCauseAnalysis(jsonNode);
            } catch (Exception e) {
                // If parsing fails, store null (invalid JSON)
                entity.setRootCauseAnalysis(null);
            }
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

        Diagnostic.Builder builder = Diagnostic.builder()
            .diagnosticId(entity.getDiagnosticId())
            .alertId(entity.getAlertId())  // Uses helper method to avoid loading full Alert
            .status(DiagnosticStatus.fromString(entity.getStatus()))
            .generatedAt(entity.getGeneratedAt())
            .confidenceScore(entity.getConfidenceScore());

        if (entity.getFaultDomain() != null) {
            builder.faultDomain(FaultDomain.fromString(entity.getFaultDomain()));
        }

        // Convert JsonNode to String JSON
        if (entity.getRootCauseAnalysis() != null) {
            try {
                String jsonString = objectMapper.writeValueAsString(entity.getRootCauseAnalysis());
                builder.rootCauseAnalysis(jsonString);
            } catch (Exception e) {
                // If serialization fails, store null
                builder.rootCauseAnalysis(null);
            }
        }

        return builder.build();
    }
}
