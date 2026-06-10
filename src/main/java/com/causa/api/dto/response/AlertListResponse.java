package com.causa.api.dto.response;

import java.util.List;

/**
 * Alert List Response DTO
 *
 * <p>Wrapper for paginated alert query results.
 *
 * @param alerts list of alert details
 * @param totalCount total number of alerts
 * @param containerName optional filter applied (for container-specific queries)
 * @since 0.0.1
 */
public record AlertListResponse(
    List<AlertDetailsResponse> alerts,
    int totalCount,
    String containerName
) {

    /**
     * Creates a response for all alerts query.
     *
     * @param alerts the list of alerts
     * @return the response object
     */
    public static AlertListResponse of(List<AlertDetailsResponse> alerts) {
        return new AlertListResponse(alerts, alerts.size(), null);
    }

    /**
     * Creates a response for container-specific alerts query.
     *
     * @param alerts the list of alerts
     * @param containerName the container filter
     * @return the response object
     */
    public static AlertListResponse forContainer(List<AlertDetailsResponse> alerts, String containerName) {
        return new AlertListResponse(alerts, alerts.size(), containerName);
    }
}
