package com.causa.api.dto.response;

import com.causa.common.constants.AlertConstants;

import java.time.Instant;
import java.util.List;

/**
 * Alert Response DTO
 *
 * <p>Response sent back to Prometheus Alertmanager after processing webhook alerts.
 *
 * @param status the overall status (accepted/partial/rejected)
 * @param message human-readable message
 * @param totalReceived total number of alerts received
 * @param totalAccepted number of alerts accepted for processing
 * @param totalFiltered number of alerts filtered out
 * @param acceptedAlertIds list of alert IDs that were accepted
 * @param diagnosticIds list of diagnostic IDs that were triggered
 * @param timestamp response timestamp
 * @since 0.0.1
 */
public record AlertResponse(
    String status,
    String message,
    int totalReceived,
    int totalAccepted,
    int totalFiltered,
    List<String> acceptedAlertIds,
    List<String> diagnosticIds,
    Instant timestamp
) {

    /**
     * Factory method to create an AlertResponse based on accepted alerts and diagnostics.
     *
     * <p>Status determination:
     * <ul>
     *   <li>All accepted → "accepted"</li>
     *   <li>Some accepted → "partial"</li>
     *   <li>None accepted → "rejected"</li>
     * </ul>
     *
     * @param alertIds the list of accepted alert IDs
     * @param diagnosticIds the list of diagnostic IDs triggered
     * @param totalReceived the total number of alerts received
     * @return the constructed AlertResponse
     */
    public static AlertResponse accepted(List<String> alertIds, List<String> diagnosticIds, int totalReceived) {
        int accepted = alertIds.size();
        String status;
        String message;

        if (accepted == totalReceived) {
            status = AlertConstants.Response.ACCEPTED;
            message = String.format("All %d alerts accepted and diagnostics initiated", accepted);
        } else if (accepted > 0) {
            status = AlertConstants.Response.PARTIAL;
            message = String.format("%d alerts accepted, %d filtered; diagnostics initiated for accepted alerts",
                                    accepted, totalReceived - accepted);
        } else {
            status = AlertConstants.Response.REJECTED;
            message = String.format("All %d alerts filtered (severity/namespace/cooldown)", totalReceived);
        }

        return new AlertResponse(
            status,
            message,
            totalReceived,
            accepted,
            totalReceived - accepted,
            alertIds,
            diagnosticIds,
            Instant.now()
        );
    }
}
