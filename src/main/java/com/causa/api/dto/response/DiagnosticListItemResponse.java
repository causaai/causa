package com.causa.api.dto.response;

import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Diagnostic List Item Response DTO
 *
 * <p>Returned by GET /api/v1/diagnostics — lightweight summary of each diagnostic.
 *
 * @param id            diagnostic ID
 * @param issue         issue title from RCA (null if still PENDING/FAILED)
 * @param containerName Kubernetes container name (from linked alert)
 * @param namespace     Kubernetes namespace (from linked alert)
 * @param severity      alert severity (from linked alert)
 * @param clusterName   cluster name from config, defaults to "default"
 * @param date          updated_at timestamp of the diagnostic record
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

    @JsonProperty("container_name")
    String containerName,

    @JsonProperty("namespace")
    String namespace,

    @JsonProperty("severity")
    String severity,

    @JsonProperty("cluster_name")
    String clusterName,

    @JsonProperty("date")
    Instant date
) {

    /**
     * Builds a list item from a Diagnostic, its linked Alert, and the configured cluster name.
     *
     * @param diagnostic  the diagnostic domain object
     * @param alert       the linked alert (may be null if alert was deleted)
     * @param clusterName cluster name from config; falls back to "default"
     * @return the response DTO
     */
    public static DiagnosticListItemResponse from(Diagnostic diagnostic, Alert alert, String clusterName) {
        String issueTitle = null;
        if (diagnostic.getRootCauseAnalysis() != null && !diagnostic.getRootCauseAnalysis().isBlank()) {
            issueTitle = extractIssueTitle(diagnostic.getRootCauseAnalysis());
        }

        String containerName = alert != null ? alert.getContainerName() : null;
        String namespace     = alert != null ? alert.getNamespace()     : null;
        String severity      = alert != null && alert.getSeverity() != null
                               ? alert.getSeverity().getValue() : null;

        return new DiagnosticListItemResponse(
            diagnostic.getDiagnosticId(),
            diagnostic.getStatus() != null ? diagnostic.getStatus().getValue() : null,
            issueTitle,
            containerName,
            namespace,
            severity,
            clusterName != null && !clusterName.isBlank() ? clusterName : "default",
            diagnostic.getGeneratedAt()
        );
    }

    /**
     * Extracts the {@code issue_title} field from the raw RCA JSON string
     * using a lightweight string scan (no full parse needed).
     */
    private static String extractIssueTitle(String rcaJson) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode node = om.readTree(rcaJson);
            com.fasterxml.jackson.databind.JsonNode titleNode = node.get("issue_title");
            return (titleNode != null && !titleNode.isNull()) ? titleNode.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
