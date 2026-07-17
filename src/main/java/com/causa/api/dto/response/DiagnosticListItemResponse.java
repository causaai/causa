package com.causa.api.dto.response;

import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;

/**
 * Diagnostic list-item DTO — returned by GET /api/v1/diagnostics.
 *
 * <p>Lightweight summary: id, status, issue title, workload info from the linked alert.
 *
 * @since 0.0.1
 */
public record DiagnosticListItemResponse(

    @JsonProperty("id")
    String id,

    @JsonProperty("status")
    String status,

    @JsonProperty("issue")
    String issue,

    @JsonProperty("workload_name")
    String workloadName,

    @JsonProperty("namespace")
    String namespace,

    @JsonProperty("severity")
    String severity,

    @JsonProperty("cluster_name")
    String clusterName,

    @JsonProperty("date")
    Instant date

) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static DiagnosticListItemResponse from(Diagnostic diagnostic, Alert alert, String clusterName) {
        String issueTitle  = extractIssueTitle(diagnostic.getRootCauseAnalysis());
        String workload    = alert != null ? alert.getWorkloadName()               : null;
        String namespace   = alert != null ? alert.getWorkloadInfo().namespace()   : null;
        String severity    = alert != null && alert.getSeverity() != null
                             ? alert.getSeverity().getValue() : null;
        String cluster     = (clusterName != null && !clusterName.isBlank()) ? clusterName : "default";

        return new DiagnosticListItemResponse(
            diagnostic.getDiagnosticId(),
            diagnostic.getStatus() != null ? diagnostic.getStatus().getValue() : null,
            issueTitle,
            workload,
            namespace,
            severity,
            cluster,
            diagnostic.getGeneratedAt()
        );
    }

    /** Extracts {@code issue_title} from the raw RCA JSON string. Returns null if absent or malformed. */
    private static String extractIssueTitle(String rcaJson) {
        if (rcaJson == null || rcaJson.isBlank()) return null;
        try {
            JsonNode node  = MAPPER.readTree(rcaJson);
            JsonNode title = node.get("issue_title");
            return (title != null && !title.isNull()) ? title.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
