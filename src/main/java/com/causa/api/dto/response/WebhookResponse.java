package com.causa.api.dto.response;

import com.causa.common.constants.AlertConstants;

import java.time.Instant;
import java.util.Map;

/**
 * Webhook response DTO — returned by POST /api/v1/webhooks/alerts.
 *
 * @since 0.0.1
 */
public record WebhookResponse(
    String status,
    String message,
    int totalReceived,
    int totalAccepted,
    int totalRejected,
    Map<String, String> accepted,
    Map<String, String> rejected,
    Instant timestamp
) {

    public static WebhookResponse of(Map<String, String> accepted, Map<String, String> rejected) {
        int acc   = accepted.size();
        int rej   = rejected.size();
        int total = acc + rej;

        String status;
        String message;
        if (total == 0) {
            status  = AlertConstants.Response.EMPTY;
            message = "No alerts in payload";
        } else if (acc == total) {
            status  = AlertConstants.Response.ACCEPTED;
            message = String.format("All %d alerts accepted and diagnostics initiated", acc);
        } else if (acc > 0) {
            status  = AlertConstants.Response.PARTIAL;
            message = String.format("%d accepted, %d rejected (severity/namespace/cooldown)", acc, rej);
        } else {
            status  = AlertConstants.Response.REJECTED;
            message = String.format("All %d alerts rejected (severity/namespace/cooldown)", rej);
        }

        return new WebhookResponse(status, message, total, acc, rej, accepted, rejected, Instant.now());
    }
}
