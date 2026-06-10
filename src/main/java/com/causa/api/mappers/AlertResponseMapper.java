package com.causa.api.mappers;

import com.causa.api.dto.response.AlertDetailsResponse;
import com.causa.core.domain.Alert;

import java.util.List;

/**
 * Alert Response Mapper
 *
 * <p>Maps Alert domain objects to AlertDetailsResponse DTOs for query endpoints.
 *
 * @since 0.0.1
 */
public final class AlertResponseMapper {

    private AlertResponseMapper() {
        // Prevent instantiation
    }

    /**
     * Maps a domain Alert to AlertDetailsResponse DTO.
     *
     * @param alert the domain alert
     * @return the response DTO
     */
    public static AlertDetailsResponse toResponse(Alert alert) {
        if (alert == null) {
            return null;
        }

        return new AlertDetailsResponse(
            alert.getAlertId(),
            alert.getTimestamp(),
            alert.getAlertName(),
            alert.getSeverity().getValue(),
            alert.getStatus().getValue(),
            alert.getPodName(),
            alert.getContainerName(),
            alert.getNamespace(),
            alert.hasDiagnostics()
        );
    }

    /**
     * Maps a list of domain Alerts to a list of AlertDetailsResponse DTOs.
     *
     * @param alerts the list of domain alerts
     * @return the list of response DTOs
     */
    public static List<AlertDetailsResponse> toResponseList(List<Alert> alerts) {
        if (alerts == null) {
            return List.of();
        }

        return alerts.stream()
            .map(AlertResponseMapper::toResponse)
            .toList();
    }
}
