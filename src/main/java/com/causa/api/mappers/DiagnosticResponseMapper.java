package com.causa.api.mappers;

import com.causa.api.dto.response.DiagnosticDetailsResponse;
import com.causa.core.domain.Diagnostic;

/**
 * Diagnostic Response Mapper
 *
 * <p>Maps Diagnostic domain objects to DiagnosticDetailsResponse DTOs.
 *
 * @since 0.0.1
 */
public final class DiagnosticResponseMapper {

    private DiagnosticResponseMapper() {
        // Prevent instantiation
    }

    /**
     * Maps a domain Diagnostic to DiagnosticDetailsResponse DTO.
     *
     * @param diagnostic the domain diagnostic
     * @return the response DTO
     */
    public static DiagnosticDetailsResponse toResponse(Diagnostic diagnostic) {
        if (diagnostic == null) {
            return null;
        }

        return new DiagnosticDetailsResponse(
            diagnostic.getDiagnosticId(),
            diagnostic.getAlertId(),
            diagnostic.getStatus().getValue(),
            diagnostic.getGeneratedAt(),
            diagnostic.getConfidenceScore(),
            diagnostic.getFaultDomain() != null ? diagnostic.getFaultDomain().getValue() : null,
            diagnostic.getRootCauseAnalysis()
        );
    }
}
