package com.causa.infrastructure.persistence.mappers;

import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import com.causa.common.constants.DiagnosticConstants.FaultDomain;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.RootCauseAnalysis;
import com.causa.infrastructure.persistence.entity.AlertEntity;
import com.causa.infrastructure.persistence.entity.DiagnosticEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;

/**
 * Diagnostic Entity Mapper
 *
 * <p>Maps between the {@link Diagnostic} domain model and {@link DiagnosticEntity} JPA entity.
 *
 * <p>Field bridging:
 * <pre>
 *   diagnosticId       ↔  id
 *   faultDomain        ↔  issueType
 *   rootCauseAnalysis  ↔  rootCauseSummary  (+ issue_title / issue_description extracted on write)
 *   confidenceScore    ↔  confidenceInfo JSONB  { "score": 0.92 }
 *   generatedAt        ↔  diagnosticsMetadata JSONB  { "generatedAt": epochMillis }
 *   validationResult   ↔  validationResult
 *   validationData     ↔  validationData JSONB
 * </pre>
 *
 * @since 0.0.1
 */
public final class DiagnosticEntityMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DiagnosticEntityMapper() {}

    // -------------------------------------------------------------------------
    // Domain → Entity
    // -------------------------------------------------------------------------

    public static DiagnosticEntity toEntity(Diagnostic d) {
        if (d == null) return null;

        DiagnosticEntity entity = new DiagnosticEntity();
        entity.setId(d.getDiagnosticId());

        // FK reference — only the PK needs to be set for a merge/persist
        AlertEntity alertRef = new AlertEntity();
        alertRef.setId(d.getAlertId());
        entity.setAlert(alertRef);

        entity.setStatus(d.getStatus().getValue());

        // faultDomain → issueType
        if (d.getFaultDomain() != null) {
            entity.setIssueType(d.getFaultDomain().getValue());
        }

        // rca (typed) → rootCauseSummary TEXT (JSON serialisation happens only here)
        // Also extract issue_title / issue_description into their own indexed columns.
        if (d.getRca() != null) {
            try {
                String rcaJson = MAPPER.writeValueAsString(d.getRca());
                entity.setRootCauseSummary(rcaJson);
                entity.setIssueTitle(d.getRca().issueTitle());
                entity.setIssueDescription(d.getRca().issueDescription());
            } catch (Exception ignored) {
                // best-effort — entity persists even if serialisation fails
            }
        }

        // confidenceScore → confidenceInfo JSONB  { "score": 0.92 }
        if (d.getConfidenceScore() != null) {
            ObjectNode ci = MAPPER.createObjectNode();
            ci.put("score", d.getConfidenceScore());
            entity.setConfidenceInfo(ci);
        }

        // generatedAt → diagnosticsMetadata JSONB  { "generatedAt": epochMillis }
        if (d.getGeneratedAt() != null) {
            ObjectNode meta = MAPPER.createObjectNode();
            meta.put("generatedAt", d.getGeneratedAt().toEpochMilli());
            entity.setDiagnosticsMetadata(meta);
        }

        // validationResult — plain string column
        entity.setValidationResult(d.getValidationResult());

        // validationData — JSON string → JSONB
        if (d.getValidationData() != null) {
            try {
                entity.setValidationData(MAPPER.readTree(d.getValidationData()));
            } catch (Exception ignored) {
                // store null if string is not valid JSON
            }
        }

        return entity;
    }

    // -------------------------------------------------------------------------
    // Entity → Domain
    // -------------------------------------------------------------------------

    public static Diagnostic toDomain(DiagnosticEntity e) {
        if (e == null) return null;

        Diagnostic.Builder b = Diagnostic.builder()
            .diagnosticId(e.getId())
            .alertId(e.getAlertId())
            .status(DiagnosticStatus.fromString(e.getStatus()));

        // issueType → faultDomain
        if (e.getIssueType() != null) {
            try { b.faultDomain(FaultDomain.fromString(e.getIssueType())); }
            catch (IllegalArgumentException ignored) {}
        }

        // rootCauseSummary TEXT → rca (typed; deserialisation happens only here)
        if (e.getRootCauseSummary() != null) {
            try {
                b.rca(MAPPER.readValue(e.getRootCauseSummary(), RootCauseAnalysis.class));
            } catch (Exception ignored) {
                // malformed stored JSON — leave rca null rather than crashing
            }
        }

        // confidenceInfo JSONB { "score": ... } → confidenceScore
        JsonNode ci = e.getConfidenceInfo();
        if (ci != null && ci.has("score")) {
            b.confidenceScore(ci.get("score").floatValue());
        }

        // diagnosticsMetadata JSONB { "generatedAt": epochMillis } → generatedAt
        JsonNode meta = e.getDiagnosticsMetadata();
        if (meta != null && meta.has("generatedAt")) {
            b.generatedAt(Instant.ofEpochMilli(meta.get("generatedAt").asLong()));
        } else {
            b.generatedAt(e.createdAt != null ? e.createdAt.toInstant() : Instant.now());
        }

        // validationResult — plain string
        b.validationResult(e.getValidationResult());

        // validationData JSONB → JSON string
        if (e.getValidationData() != null) {
            try { b.validationData(MAPPER.writeValueAsString(e.getValidationData())); }
            catch (Exception ignored) {}
        }

        return b.build();
    }
}
