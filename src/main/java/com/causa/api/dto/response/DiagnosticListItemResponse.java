package com.causa.api.dto.response;

import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.RootCauseAnalysis;
import com.fasterxml.jackson.annotation.JsonProperty;

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
    public static DiagnosticListItemResponse from(Diagnostic diagnostic, Alert alert, String clusterName) {
        RootCauseAnalysis rca = diagnostic.getRca();
        String issueTitle  = rca != null ? rca.issueTitle() : null;
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
}
