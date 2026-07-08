package com.causa.api.dto.response;

import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.RootCauseAnalysis;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

/**
 * Diagnostic Detail Response DTO
 *
 * <p>Returned by GET /api/v1/diagnostics/{id} — full diagnostic payload.
 * Shape matches the user-defined sample including nested workload_info and diagnosis objects.
 *
 * @since 0.0.1
 */
public record DiagnosticDetailResponse(
    @JsonProperty("diagnostics_id")
    String diagnosticsId,

    @JsonProperty("alert_id")
    String alertId,

    @JsonProperty("alert_name")
    String alertName,

    @JsonProperty("severity")
    String severity,

    @JsonProperty("alert_received_at")
    Instant alertReceivedAt,

    @JsonProperty("workload_info")
    WorkloadInfo workloadInfo,

    @JsonProperty("diagnosis")
    DiagnosisInfo diagnosis,

    @JsonProperty("recommendations")
    List<RecommendationInfo> recommendations,

    @JsonProperty("llm_notes")
    String llmNotes
) {

    // -------------------------------------------------------------------------
    // Nested records
    // -------------------------------------------------------------------------

    public record WorkloadInfo(
        @JsonProperty("container_name")
        String containerName,

        @JsonProperty("workload_name")
        String workloadName,

        @JsonProperty("namespace")
        String namespace,

        @JsonProperty("cluster_name")
        String clusterName
    ) {}

    public record DiagnosisInfo(
        @JsonProperty("issue_title")
        String issueTitle,

        @JsonProperty("issue_description")
        String issueDescription,

        @JsonProperty("technical_description")
        String technicalDescription,

        @JsonProperty("anomaly_type")
        String anomalyType,

        @JsonProperty("root_cause")
        String rootCause,

        @JsonProperty("evidences")
        List<String> evidences,

        @JsonProperty("supporting_logs")
        List<String> supportingLogs,

        @JsonProperty("rca_confidence_score")
        Double rcaConfidenceScore,

        @JsonProperty("confidence_summary")
        String confidenceSummary
    ) {}

    public record RecommendationInfo(
        @JsonProperty("solution_type")
        String solutionType,

        @JsonProperty("solution_title")
        String solutionTitle,

        @JsonProperty("solution_description")
        String solutionDescription,

        @JsonProperty("confidence_score")
        Double confidenceScore,

        @JsonProperty("implementation_notes")
        String implementationNotes
    ) {}

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Builds a full detail response from a Diagnostic, its linked Alert,
     * the parsed RCA, and the configured cluster name.
     *
     * @param diagnostic  the diagnostic domain object
     * @param alert       the linked alert (may be null)
     * @param clusterName cluster name from config; falls back to "default"
     * @return the response DTO
     */
    public static DiagnosticDetailResponse from(Diagnostic diagnostic, Alert alert, String clusterName) {
        String resolvedCluster = (clusterName != null && !clusterName.isBlank()) ? clusterName : "default";

        // Build workload_info from alert
        WorkloadInfo workloadInfo = null;
        if (alert != null) {
            workloadInfo = new WorkloadInfo(
                alert.getContainerName(),
                alert.getContainerName(),   // workload_name = container_name (same pod workload)
                alert.getNamespace(),
                resolvedCluster
            );
        }

        // Parse RCA JSON
        RootCauseAnalysis rca = parseRca(diagnostic.getRootCauseAnalysis());

        DiagnosisInfo diagnosisInfo = null;
        List<RecommendationInfo> recommendations = null;
        String llmNotes = null;

        if (rca != null) {
            Double rcaScore = rca.confidenceSummary() != null
                ? rca.confidenceSummary().rcaConfidenceScore() : null;
            String summaryText = rca.confidenceSummary() != null
                ? rca.confidenceSummary().summaryText() : null;

            diagnosisInfo = new DiagnosisInfo(
                rca.issueTitle(),
                rca.issueDescription(),
                rca.technicalDescription(),
                rca.anomalyType() != null ? rca.anomalyType().name() : null,
                rca.rootCause(),
                rca.evidences(),
                rca.supportingLogs(),
                rcaScore,
                summaryText
            );

            if (rca.recommendations() != null) {
                recommendations = rca.recommendations().stream()
                    .map(r -> new RecommendationInfo(
                        r.solutionType(),
                        r.solutionTitle(),
                        r.solutionDescription(),
                        r.solutionConfidenceScore(),
                        r.implementationNotes()
                    ))
                    .toList();
            }

            llmNotes = rca.llmNotes();
        }

        return new DiagnosticDetailResponse(
            diagnostic.getDiagnosticId(),
            diagnostic.getAlertId(),
            alert != null ? alert.getAlertName() : null,
            alert != null && alert.getSeverity() != null ? capitalise(alert.getSeverity().getValue()) : null,
            alert != null ? alert.getTimestamp() : null,
            workloadInfo,
            diagnosisInfo,
            recommendations,
            llmNotes
        );
    }

    private static RootCauseAnalysis parseRca(String rcaJson) {
        if (rcaJson == null || rcaJson.isBlank()) return null;
        try {
            return MAPPER.readValue(rcaJson, RootCauseAnalysis.class);
        } catch (Exception e) {
            return null;
        }
    }

    /** Capitalises first letter only — e.g. "critical" → "Critical". */
    private static String capitalise(String value) {
        if (value == null || value.isBlank()) return value;
        return Character.toUpperCase(value.charAt(0)) + value.substring(1).toLowerCase();
    }
}
