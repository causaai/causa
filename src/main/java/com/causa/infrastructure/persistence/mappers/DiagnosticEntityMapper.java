package com.causa.infrastructure.persistence.mappers;

import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import com.causa.common.constants.DiagnosticConstants.FaultDomain;
import com.causa.core.domain.Diagnostic;
import com.causa.infrastructure.persistence.entity.AlertEntity;
import com.causa.infrastructure.persistence.entity.DiagnosticEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;

/**
 * Diagnostic Entity Mapper
 *
 * <p>Maps between Diagnostic domain model and DiagnosticEntity JPA entity.
 * <p>Bridges field-name differences between the domain model and the DB schema.
 *
 * <ul>
 *   <li>{@code diagnosticId}   ↔ {@code id}</li>
 *   <li>{@code generatedAt}    ↔ stored in {@code diagnosticsMetadata.generatedAt}</li>
 *   <li>{@code confidenceScore}↔ stored in {@code confidenceInfo.score}</li>
 *   <li>{@code faultDomain}    ↔ {@code issueType}</li>
 *   <li>{@code rootCauseAnalysis} ↔ {@code rootCauseSummary}</li>
 * </ul>
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

        // Create AlertEntity reference for the FK relationship
        AlertEntity alertRef = new AlertEntity();
        alertRef.setId(diagnostic.getAlertId());
        entity.setAlert(alertRef);

        entity.setStatus(diagnostic.getStatus().getValue());

        // faultDomain → issueType
        if (diagnostic.getFaultDomain() != null) {
            entity.setIssueType(diagnostic.getFaultDomain().getValue());
        }

        // rootCauseAnalysis (JSON string) → rootCauseSummary + extract issueTitle and issueDescription
        if (diagnostic.getRootCauseAnalysis() != null) {
            entity.setRootCauseSummary(diagnostic.getRootCauseAnalysis());
            try {
                JsonNode rcaNode = objectMapper.readTree(diagnostic.getRootCauseAnalysis());
                JsonNode titleNode = rcaNode.get("issue_title");
                if (titleNode != null && !titleNode.isNull()) {
                    entity.setIssueTitle(titleNode.asText());
                }
                JsonNode descNode = rcaNode.get("issue_description");
                if (descNode != null && !descNode.isNull()) {
                    entity.setIssueDescription(descNode.asText());
                }
            } catch (Exception ignored) {
                // best-effort extraction — entity persists even if parse fails
            }
        }

        // confidenceScore → confidenceInfo JSONB
        if (diagnostic.getConfidenceScore() != null) {
            ObjectNode confidenceNode = objectMapper.createObjectNode();
            confidenceNode.put("score", diagnostic.getConfidenceScore());
            entity.setConfidenceInfo(confidenceNode);
        }

        // generatedAt → diagnosticsMetadata JSONB
        if (diagnostic.getGeneratedAt() != null) {
            ObjectNode meta = objectMapper.createObjectNode();
            meta.put("generatedAt", diagnostic.getGeneratedAt().toEpochMilli());
            entity.setDiagnosticsMetadata(meta);
        }

        // Set validation fields
        entity.setValidationResult(diagnostic.getValidationResult());

        // Convert validation data String to JsonNode
        if (diagnostic.getValidationData() != null) {
            try {
                JsonNode jsonNode = objectMapper.readTree(diagnostic.getValidationData());
                entity.setValidationData(jsonNode);
            } catch (Exception e) {
                entity.setValidationData(null);
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
            .diagnosticId(entity.getId())
            .alertId(entity.getAlertId())
            .status(DiagnosticStatus.fromString(entity.getStatus()));

        // issueType → faultDomain
        if (entity.getIssueType() != null) {
            builder.faultDomain(FaultDomain.fromString(entity.getIssueType()));
        }

        // rootCauseSummary → rootCauseAnalysis
        if (entity.getRootCauseSummary() != null) {
            builder.rootCauseAnalysis(entity.getRootCauseSummary());
        }

        // confidenceInfo JSONB → confidenceScore
        JsonNode ci = entity.getConfidenceInfo();
        if (ci != null && ci.has("score")) {
            builder.confidenceScore(ci.get("score").floatValue());
        }

        // diagnosticsMetadata JSONB → generatedAt
        JsonNode meta = entity.getDiagnosticsMetadata();
        if (meta != null && meta.has("generatedAt")) {
            builder.generatedAt(Instant.ofEpochMilli(meta.get("generatedAt").asLong()));
        } else {
            builder.generatedAt(Instant.now());
        }


        builder.validationResult(entity.getValidationResult());

        // Convert validation data JsonNode to String
        if (entity.getValidationData() != null) {
            try {
                String jsonString = objectMapper.writeValueAsString(entity.getValidationData());
                builder.validationData(jsonString);
            } catch (Exception e) {
                builder.validationData(null);
            }
        }

        return builder.build();
    }
}
