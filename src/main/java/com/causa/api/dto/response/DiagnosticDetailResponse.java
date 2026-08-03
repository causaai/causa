package com.causa.api.dto.response;

import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.RootCauseAnalysis;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Diagnostic detail DTO — returned by GET /api/v1/diagnostics/{id}.
 *
 * <p>Full diagnostic payload including workload info from the linked alert,
 * RCA diagnosis (with recommendations and llm_notes nested inside), and validation result.
 *
 * @since 0.0.1
 */
public record DiagnosticDetailResponse(

    @JsonProperty("id")
    String id,

    @JsonProperty("status")
    String status,

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

    @JsonProperty("validation_result")
    String validationResult

) {

    // -------------------------------------------------------------------------
    // Nested records
    // -------------------------------------------------------------------------

    public record WorkloadInfo(
        @JsonProperty("pod_name")      String podName,
        @JsonProperty("workload_name") String workloadName,
        @JsonProperty("namespace")     String namespace,
        @JsonProperty("cluster_name")  String clusterName,
        @JsonProperty("workload_type") String workloadType
    ) {}

    public record DiagnosisInfo(
        @JsonProperty("issue_title")           String issueTitle,
        @JsonProperty("issue_summary")         String issueSummary,
        @JsonProperty("issue_description")     String issueDescription,
        @JsonProperty("technical_description") String technicalDescription,
        @JsonProperty("anomaly_type")          String anomalyType,
        @JsonProperty("root_cause")            String rootCause,
        @JsonProperty("evidences")             List<String> evidences,
        @JsonProperty("supporting_logs")       List<String> supportingLogs,
        @JsonProperty("rca_confidence_score")  Double rcaConfidenceScore,
        @JsonProperty("confidence_summary")    String confidenceSummaryText,
        @JsonProperty("recommendations")       List<RecommendationInfo> recommendations,
        @JsonProperty("llm_notes")             String llmNotes
    ) {}

    public record RecommendationInfo(
        @JsonProperty("solution_type")             String solutionType,
        @JsonProperty("solution_title")            String solutionTitle,
        @JsonProperty("solution_description")      String solutionDescription,
        @JsonProperty("implementation_notes")      String implementationNotes,
        @JsonProperty("solution_confidence_score") Double solutionConfidenceScore,
        @JsonProperty("solution_alerts")           List<String> solutionAlerts
    ) {}

    // -------------------------------------------------------------------------
    // Factory
    // -------------------------------------------------------------------------

    public static DiagnosticDetailResponse from(Diagnostic diagnostic, Alert alert, String clusterName) {
        String cluster = (clusterName != null && !clusterName.isBlank()) ? clusterName : "default";

        // workload_info — all fields from Alert.WorkloadInfo; workload_name = denormalised column
        WorkloadInfo workloadInfo = null;
        if (alert != null) {
            Alert.WorkloadInfo wi = alert.getWorkloadInfo();
            workloadInfo = new WorkloadInfo(
                wi.podName(),
                alert.getWorkloadName(),
                wi.namespace(),
                cluster,
                wi.workloadType()
            );
        }

        // Typed RCA — null until pipeline reaches VALIDATING/COMPLETED
        RootCauseAnalysis rca = diagnostic.getRca();

        DiagnosisInfo diagnosisInfo = null;
        if (rca != null) {
            Double rcaScore    = rca.confidenceSummary() != null ? rca.confidenceSummary().rcaConfidenceScore() : null;
            String summaryText = rca.confidenceSummary() != null ? rca.confidenceSummary().summaryText()        : null;

            List<RecommendationInfo> recommendations = null;
            if (rca.recommendations() != null) {
                recommendations = rca.recommendations().stream()
                    .map(r -> new RecommendationInfo(
                        r.solutionType(),
                        r.solutionTitle(),
                        r.solutionDescription(),
                        r.implementationNotes(),
                        r.solutionConfidenceScore(),
                        r.solutionAlerts()
                    ))
                    .toList();
            }

            diagnosisInfo = new DiagnosisInfo(
                rca.issueTitle(),
                rca.issueSummary(),
                rca.issueDescription(),
                rca.technicalDescription(),
                rca.anomalyType() != null ? rca.anomalyType().name() : null,
                rca.rootCause(),
                rca.evidences(),
                rca.supportingLogs(),
                rcaScore,
                summaryText,
                recommendations,
                rca.llmNotes()
            );
        }

        return new DiagnosticDetailResponse(
            diagnostic.getDiagnosticId(),
            diagnostic.getStatus() != null ? diagnostic.getStatus().getValue() : null,
            diagnostic.getAlertId(),
            alert != null ? alert.getAlertName()                                          : null,
            alert != null && alert.getSeverity() != null ? alert.getSeverity().getValue() : null,
            alert != null ? alert.getAlertTimestamp()                                     : null,
            workloadInfo,
            diagnosisInfo,
            diagnostic.getValidationResult()
        );
    }
}
