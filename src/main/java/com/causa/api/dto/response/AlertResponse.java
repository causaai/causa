package com.causa.api.dto.response;

import com.causa.common.constants.AlertConstants;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Alert Response DTO
 *
 * <p>Response sent back after processing a Prometheus Alertmanager webhook payload.
 *
 * @param status            overall batch status: {@code accepted} / {@code partial} / {@code rejected}
 * @param message           human-readable summary
 * @param totalReceived     total alerts in the webhook payload
 * @param totalAccepted     alerts that passed all filters and had diagnostics initiated
 * @param totalRejected     alerts filtered out (severity / namespace / cooldown)
 * @param alerts            per-alert result entries (id, status, diagnosticId, reason)
 * @param timestamp         response timestamp
 *
 * @since 0.0.1
 */
public record AlertResponse(
    String status,
    String message,
    int totalReceived,
    int totalAccepted,
    int totalRejected,
    List<AlertEntry> alerts,
    Instant timestamp
) {

    /**
     * Per-alert result entry returned in the webhook response.
     *
     * @param alertId        application-generated alert ID
     * @param status         Causa processing status: {@code ACCEPTED} or {@code REJECTED}
     * @param diagnosticId   diagnostic ID if diagnostics were triggered; {@code null} for rejected alerts
     * @param rejectionReason reason the alert was filtered; {@code null} for accepted alerts
     */
    public record AlertEntry(
        String alertId,
        String status,
        String diagnosticId,
        String rejectionReason
    ) {}

    /**
     * Builds an AlertResponse from the accepted/rejected alert maps and diagnosticId map.
     *
     * @param acceptedEntries map of alertId → diagnosticId for accepted alerts
     * @param rejectedEntries map of alertId → rejectionReason for rejected alerts
     * @return the constructed AlertResponse
     */
    public static AlertResponse of(
            Map<String, String> acceptedEntries,
            Map<String, String> rejectedEntries) {

        List<AlertEntry> entries = new java.util.ArrayList<>();

        acceptedEntries.forEach((alertId, diagnosticId) ->
            entries.add(new AlertEntry(alertId, "ACCEPTED", diagnosticId, null)));

        rejectedEntries.forEach((alertId, reason) ->
            entries.add(new AlertEntry(alertId, "REJECTED", null, reason)));

        int totalAccepted = acceptedEntries.size();
        int totalRejected = rejectedEntries.size();
        int totalReceived = totalAccepted + totalRejected;

        String status;
        String message;
        if (totalAccepted == totalReceived) {
            status  = AlertConstants.Response.ACCEPTED;
            message = String.format("All %d alerts accepted and diagnostics initiated", totalAccepted);
        } else if (totalAccepted > 0) {
            status  = AlertConstants.Response.PARTIAL;
            message = String.format("%d accepted (diagnostics initiated), %d rejected (severity/namespace/cooldown)",
                                    totalAccepted, totalRejected);
        } else {
            status  = AlertConstants.Response.REJECTED;
            message = String.format("All %d alerts rejected (severity/namespace/cooldown)", totalRejected);
        }

        return new AlertResponse(status, message, totalReceived, totalAccepted, totalRejected, entries, Instant.now());
    }
}
