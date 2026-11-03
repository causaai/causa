package com.causa.api.dto.response;

import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.RootCauseAnalysis;
import com.causa.core.domain.validation.EvidenceItem;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
     * <p>User-friendly evidence for API response: every collected EvidenceItem, transformed
     * for display. Narrowing to the few that matter waits on the ranking design.
     *
     * @since 0.0.1
     */
    public record Evidence(
        @JsonProperty("supporting_evidence") String supportingEvidence,
        @JsonProperty("explanation")         String explanation,
        @JsonProperty("source")              String source,
        @JsonProperty("raw_snippet")         String rawSnippet,
        @JsonProperty("evidence_strength")   String evidenceStrength
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
            // Persisted score is the validation score once validation has run; before that (or if
            // validation failed) fall back to the score the LLM generated with the RCA.
            Double rcaScore;
            if (diagnostic.getConfidenceScore() != null) {
                // Float.toString avoids float-widening noise (0.85f -> 0.8500000238...)
                rcaScore = Double.parseDouble(Float.toString(diagnostic.getConfidenceScore()));
            } else {
                rcaScore = rca.confidenceSummary() != null ? rca.confidenceSummary().rcaConfidenceScore() : null;
            }
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

            // Prefer ranked ordering when the pipeline produced rankedEvidenceIds;
            // otherwise render all collected evidence in stored order.
            List<Evidence> validationEvidences = buildRankedEvidenceList(diagnostic);
            if (validationEvidences == null) {
                validationEvidences = toValidationEvidences(diagnostic.getAllEvidence());
            }

            diagnosisInfo = new DiagnosisInfo(
                rca.issueTitle(),
                rca.issueSummary(),
                rca.issueDescription(),
                rca.technicalDescription(),
                rca.anomalyType() != null ? rca.anomalyType().name() : null,
                rca.rootCause(),
                rca.evidences(),  // LLM-generated evidences (backward compatible)
                validationEvidences,  // Ranked (or all) validation evidences
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
     * Renders the stored evidence harvest into the UI model.
     *
     * <p>Malformed or absent stored JSON yields null rather than failing the request —
     * evidence is supplementary to a diagnosis, never a precondition for reading it.
     */
    private static List<Evidence> toValidationEvidences(String evidenceJson) {
        List<EvidenceItem> items = parseEvidenceItems(evidenceJson);
        if (items == null || items.isEmpty()) {
            return null;
        }
        return items.stream().map(DiagnosticDetailResponse::toEvidence).toList();
    }

    /**
     * Builds the ranked evidence list from the stored harvest using the {@code rankedEvidenceIds}
     * produced by the evidence ranking agent.
     *
     * <p>The ranker ranks all evidence from both paths (PATH A and PATH B), so its output is the
     * final, ordered set. This method simply renders {@code rankedEvidenceIds} in order; IDs with
     * no matching stored item are skipped. Returns null when no ranking is present, so the caller
     * can fall back to rendering all evidence in stored order.
     */
    private static List<Evidence> buildRankedEvidenceList(Diagnostic diagnostic) {
        List<String> rankedIds = extractRankedIds(diagnostic.getValidationData());
        if (rankedIds == null || rankedIds.isEmpty()) {
            return null;  // No ranking available — caller falls back to stored order
        }

        List<EvidenceItem> allEvidence = parseEvidenceItems(diagnostic.getAllEvidence());
        if (allEvidence == null || allEvidence.isEmpty()) {
            return null;  // No evidence available
        }

        Map<String, EvidenceItem> evidenceById = allEvidence.stream()
            .collect(Collectors.toMap(
                EvidenceItem::id,
                item -> item,
                (existing, replacement) -> existing,  // Keep first if duplicates
                LinkedHashMap::new
            ));

        List<Evidence> rankedEvidence = new ArrayList<>();
        for (String id : rankedIds) {
            EvidenceItem item = evidenceById.get(id);
            if (item != null) {
                rankedEvidence.add(toEvidence(item));
            }
        }

        return rankedEvidence.isEmpty() ? null : rankedEvidence;
    }

    /**
     * Extracts {@code rankedEvidenceIds} from the validationData JSONB, or null if absent.
     */
    private static List<String> extractRankedIds(String validationDataJson) {
        if (validationDataJson == null || validationDataJson.isBlank()) {
            return null;
        }
        try {
            JsonNode rankedIdsNode = MAPPER.readTree(validationDataJson).get("rankedEvidenceIds");
            if (rankedIdsNode == null || !rankedIdsNode.isArray()) {
                return null;
            }
            return MAPPER.convertValue(rankedIdsNode, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            // Silently fail - ranking is optional
            return null;
        }
    }

    /**
     * Parses the stored evidence harvest JSON to EvidenceItems, or null on absence/failure.
     *
     * <p>A parse failure is logged, not swallowed silently: it is otherwise indistinguishable
     * from a diagnostic that collected no evidence at all.
     */
    private static List<EvidenceItem> parseEvidenceItems(String evidenceJson) {
        if (evidenceJson == null || evidenceJson.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(evidenceJson, EVIDENCE_ITEMS);
        } catch (Exception e) {
            log.warn(LogMessages.Evidence.RENDERING_FAILED)
                .exception(e)
                .log();
            return null;
        }
    }

    /**
     * Maps one stored item onto the UI model.
     *
     * <p>{@code supportingEvidence} carries the one-line statement of what was observed and
     * {@code explanation} argues why that settles the point. {@code rawSnippet} is the verbatim
     * source text behind the statement, so the reader can check it rather than take it on trust.
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
            item.explanation(),
            item.type() != null ? item.type().name() : "OTHER",
            item.rawSnippet(),
            item.strength() == null ? "WEAK" : item.strength().name()
        );
    }
}
