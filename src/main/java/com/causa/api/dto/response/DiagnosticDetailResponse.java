package com.causa.api.dto.response;

import com.causa.common.constants.EvidenceConstants.Reliability;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.RootCauseAnalysis;
import com.causa.core.domain.validation.EvidenceItem;
import com.causa.core.domain.validation.EvidenceItem.EvidenceHypothesisAlignment;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

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

    private static final CausaLogger log = CausaLogger.getLogger(DiagnosticDetailResponse.class);

    /**
     * Reader for the stored evidence JSON.
     *
     * <p>JavaTimeModule is not optional: {@code EvidenceItem.collectedAt} is an {@link Instant},
     * and a bare ObjectMapper cannot read one. Unknown properties are ignored so an evidence
     * model that gains a field stays readable against rows written before it existed.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private static final TypeReference<List<EvidenceItem>> EVIDENCE_ITEMS =
        new TypeReference<>() {};

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

    /**
     * Evidence - UI Model (5 fields).
     *
     * <p>User-friendly evidence for API response. Selected from top 3-5
     * EvidenceItems by priority and transformed for display.
     *
     * @since 0.0.1
     */
    public record Evidence(
        @JsonProperty("supportingEvidence") String supportingEvidence,
        @JsonProperty("explanation")        String explanation,
        @JsonProperty("source")             String source,
        @JsonProperty("rawSnippet")         String rawSnippet,
        @JsonProperty("reliability")        String reliability
    ) {}

    public record DiagnosisInfo(
        @JsonProperty("issue_title")           String issueTitle,
        @JsonProperty("issue_summary")         String issueSummary,
        @JsonProperty("issue_description")     String issueDescription,
        @JsonProperty("technical_description") String technicalDescription,
        @JsonProperty("anomaly_type")          String anomalyType,
        @JsonProperty("root_cause")            String rootCause,
        @JsonProperty("evidences")             List<String> evidences,  // LLM-generated evidences (existing)
        @JsonProperty("validation_evidences")  List<Evidence> validationEvidences,  // Structured evidences from validation pipeline (new)
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
                rca.evidences(),  // LLM-generated evidences (backward compatible)
                toValidationEvidences(diagnostic.getEvidence()),
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

    // -------------------------------------------------------------------------
    // Evidence rendering
    // -------------------------------------------------------------------------

    /**
     * Renders the stored evidence selection into the UI model.
     *
     * <p>Selection already happened when the diagnostic completed; this only reshapes the
     * chosen items. Malformed or absent stored JSON yields null rather than failing the
     * request — evidence is supplementary to a diagnosis, never a precondition for reading it.
     */
    private static List<Evidence> toValidationEvidences(String evidenceJson) {
        if (evidenceJson == null || evidenceJson.isBlank()) {
            return null;
        }
        try {
            List<EvidenceItem> items = MAPPER.readValue(evidenceJson, EVIDENCE_ITEMS);
            return items.stream().map(DiagnosticDetailResponse::toEvidence).toList();
        } catch (Exception e) {
            // Logged, not silent: a swallowed failure here is indistinguishable from a
            // diagnostic that collected no evidence at all.
            log.warn(LogMessages.Evidence.RENDERING_FAILED)
                .exception(e)
                .log();
            return null;
        }
    }

    /**
     * Maps one stored item onto the UI model.
     *
     * <p>{@code supportingEvidence} carries the one-line statement of what was observed, not the
     * assertion it was collected for, and {@code explanation} argues why that observation
     * settles the point. {@code rawSnippet} is the verbatim source text behind the statement, so
     * the reader can check it rather than take it on trust.
     *
     * <p>Rule-derived items have no statement — no narrator wrote one — and the snippet stands
     * in. Repeating it is honest: the snippet is all that was observed.
     */
    private static Evidence toEvidence(EvidenceItem item) {
        String statement = item.statement() != null && !item.statement().isBlank()
            ? item.statement()
            : item.rawSnippet();
        return new Evidence(
            statement,
            item.reasoning(),
            item.source(),
            item.rawSnippet(),
            reliabilityOf(item)
        );
    }

    /**
     * Turns strength and alignment into a label a reader can act on.
     *
     * <p>A contradiction keeps its strength — a decisive fact that refutes the finding is still
     * decisive — and is suffixed so the reader is not left thinking it backs the diagnosis.
     */
    private static String reliabilityOf(EvidenceItem item) {

        if (item.strength() == null) {
            return Reliability.LOW;
        }

        String base = switch (item.strength()) {
            case DEFINITIVE, STRONG -> Reliability.HIGH;
            case MODERATE -> Reliability.MEDIUM;
            case WEAK, CIRCUMSTANTIAL -> Reliability.LOW;
        };

        return item.evidenceHypothesisAlignment() == EvidenceHypothesisAlignment.REFUTES
            ? base + Reliability.REFUTES_SUFFIX
            : base;
    }
}
