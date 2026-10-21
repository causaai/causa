package com.causa.api.dto.response;

import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.RootCauseAnalysis;
import com.causa.core.domain.validation.EvidenceItem;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

            // Build ranked evidence list from allEvidence + rankedEvidenceIds
            List<Evidence> validationEvidences = buildRankedEvidenceList(diagnostic);

            diagnosisInfo = new DiagnosisInfo(
                rca.issueTitle(),
                rca.issueSummary(),
                rca.issueDescription(),
                rca.technicalDescription(),
                rca.anomalyType() != null ? rca.anomalyType().name() : null,
                rca.rootCause(),
                rca.evidences(),  // LLM-generated evidences (backward compatible)
                validationEvidences,  // Ranked validation evidences
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
    // Helper methods for ranked evidence
    // -------------------------------------------------------------------------

    /**
     * Builds ranked evidence list from allEvidence using rankedEvidenceIds.
     *
     * <p>Steps:
     * <ol>
     *   <li>Extract rankedEvidenceIds from validationData JSONB</li>
     *   <li>Parse allEvidence JSONB to List&lt;EvidenceItem&gt;</li>
     *   <li>Filter and order by ranked IDs</li>
     *   <li>Transform to API Evidence DTOs</li>
     * </ol>
     *
     * @param diagnostic the diagnostic containing validationData and allEvidence
     * @return ranked evidence list for UI, or null if no evidence/ranking available
     */
    private static List<Evidence> buildRankedEvidenceList(Diagnostic diagnostic) {
        // 1. Extract ranked IDs from validationData
        List<String> rankedIds = extractRankedIds(diagnostic.getValidationData());
        if (rankedIds == null || rankedIds.isEmpty()) {
            return null;  // No ranking available
        }

        // 2. Parse allEvidence JSON
        List<EvidenceItem> allEvidence = parseAllEvidence(diagnostic.getAllEvidence());
        if (allEvidence == null || allEvidence.isEmpty()) {
            return null;  // No evidence available
        }

        // 3. Build ID -> EvidenceItem map for fast lookup
        Map<String, EvidenceItem> evidenceById = allEvidence.stream()
            .collect(Collectors.toMap(
                EvidenceItem::id,
                item -> item,
                (existing, replacement) -> existing,  // Keep first if duplicates
                LinkedHashMap::new
            ));

        // 4. Filter and order by ranked IDs, transform to API Evidence
        List<Evidence> rankedEvidence = new ArrayList<>();
        for (String id : rankedIds) {
            EvidenceItem item = evidenceById.get(id);
            if (item != null) {
                rankedEvidence.add(toApiEvidence(item));
            }
        }

        return rankedEvidence.isEmpty() ? null : rankedEvidence;
    }

    /**
     * Extracts rankedEvidenceIds from validationData JSONB.
     *
     * @param validationDataJson the validationData JSONB string
     * @return list of ranked evidence IDs, or null if not available
     */
    private static List<String> extractRankedIds(String validationDataJson) {
        if (validationDataJson == null || validationDataJson.isBlank()) {
            return null;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(validationDataJson);
            JsonNode rankedIdsNode = root.get("rankedEvidenceIds");

            if (rankedIdsNode == null || !rankedIdsNode.isArray()) {
                return null;
            }

            return mapper.convertValue(
                rankedIdsNode,
                new TypeReference<List<String>>() {}
            );
        } catch (Exception e) {
            // Silently fail - ranking is optional
            return null;
        }
    }

    /**
     * Parses allEvidence JSONB to List of EvidenceItem.
     *
     * @param allEvidenceJson the allEvidence JSONB string
     * @return list of evidence items, or null if not available
     */
    private static List<EvidenceItem> parseAllEvidence(String allEvidenceJson) {
        if (allEvidenceJson == null || allEvidenceJson.isBlank()) {
            return null;
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(
                allEvidenceJson,
                new TypeReference<List<EvidenceItem>>() {}
            );
        } catch (Exception e) {
            // Silently fail - evidence parsing is optional
            return null;
        }
    }

    /**
     * Transforms EvidenceItem (internal model) to Evidence (API DTO).
     *
     * @param item the internal evidence item
     * @return API evidence DTO for UI rendering
     */
    private static Evidence toApiEvidence(EvidenceItem item) {
        return new Evidence(
            item.statement() != null ? item.statement() : item.rawSnippet(),  // supportingEvidence
            item.explanation(),                                                 // explanation
            item.source(),                                                      // source
            item.rawSnippet(),                                                  // rawSnippet
            item.strength() != null ? item.strength().name() : "UNKNOWN"       // reliability
        );
    }
}
