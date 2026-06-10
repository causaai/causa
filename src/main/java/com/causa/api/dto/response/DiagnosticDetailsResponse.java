package com.causa.api.dto.response;

import java.time.Instant;

/**
 * Diagnostic Details Response DTO
 *
 * <p>Represents the detailed LLM root-cause analysis and recommendations for an alert.
 *
 * @param diagnosticId unique diagnostic identifier
 * @param alertId the associated alert ID
 * @param status diagnostic status (PENDING, IN_PROGRESS, COMPLETED, FAILED)
 * @param generatedAt when the diagnostic was generated
 * @param confidenceScore LLM confidence score (0.0-1.0, nullable if PENDING)
 * @param faultDomain categorized fault domain (APP_CODE, K8S_CONFIG, JVM_CONFIG, UNKNOWN)
 * @param rootCauseAnalysis the detailed LLM analysis (JSON string, nullable if PENDING)
 * @since 0.0.1
 */
public record DiagnosticDetailsResponse(
    String diagnosticId,
    String alertId,
    String status,
    Instant generatedAt,
    Float confidenceScore,
    String faultDomain,
    String rootCauseAnalysis
) {
}
