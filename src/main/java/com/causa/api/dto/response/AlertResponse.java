package com.causa.api.dto.response;

import com.causa.common.constants.AlertConstants;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Webhook response DTO — returned by POST /api/v1/webhooks/alerts.
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

    /** Per-alert result entry. */
    public record AlertEntry(
        String alertId,
        String status,
        String diagnosticId,
        String rejectionReason
    ) {}

    public static AlertResponse of(Map<String, String> accepted, Map<String, String> rejected) {
        List<AlertEntry> entries = new ArrayList<>();
        accepted.forEach((id, diagId) -> entries.add(new AlertEntry(id, "ACCEPTED", diagId, null)));
        rejected.forEach((id, reason) -> entries.add(new AlertEntry(id, "REJECTED", null, reason)));

        int acc = accepted.size();
        int rej = rejected.size();
        int total = acc + rej;

        String status;
        String message;
        if (acc == total) {
            status  = AlertConstants.Response.ACCEPTED;
            message = String.format("All %d alerts accepted and diagnostics initiated", acc);
        } else if (acc > 0) {
            status  = AlertConstants.Response.PARTIAL;
            message = String.format("%d accepted, %d rejected (severity/namespace/cooldown)", acc, rej);
        } else {
            status  = AlertConstants.Response.REJECTED;
            message = String.format("All %d alerts rejected (severity/namespace/cooldown)", rej);
        }

        return new AlertResponse(status, message, total, acc, rej, entries, Instant.now());
    }
}
