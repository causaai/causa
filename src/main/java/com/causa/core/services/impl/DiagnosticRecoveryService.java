package com.causa.core.services.impl;

import com.causa.common.constants.AppConstants;
import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import com.causa.common.logging.CausaLogger;
import com.causa.core.domain.Diagnostic;
import com.causa.core.ports.AlertRepository;
import com.causa.core.ports.DiagnosticRepository;
import com.causa.infrastructure.persistence.mappers.AlertEntityMapper;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class DiagnosticRecoveryService {

    private static final CausaLogger log = CausaLogger.getLogger(DiagnosticRecoveryService.class);

    private final DiagnosticRepository diagnosticRepository;
    private final AlertRepository alertRepository;

    @Inject
    public DiagnosticRecoveryService(DiagnosticRepository diagnosticRepository,
                                     AlertRepository alertRepository) {
        this.diagnosticRepository = diagnosticRepository;
        this.alertRepository = alertRepository;
    }

    @Transactional
    void recoverUnfinishedDiagnostics(
            @Observes @Priority(AppConstants.StartupConstants.DIAGNOSTIC_RECOVERY_PRIORITY) StartupEvent event) {
        int recovered = 0;
        for (Diagnostic diagnostic : diagnosticRepository.findUnfinished()) {
            boolean transitioned = diagnosticRepository.updateStatusIfCurrent(
                diagnostic.getDiagnosticId(), diagnostic.getStatus(), DiagnosticStatus.FAILED);
            if (!transitioned) {
                continue;
            }

            alertRepository.updateProcessingStatus(
                diagnostic.getAlertId(), AlertEntityMapper.STATUS_PROCESSED);
            recovered++;
        }

        if (recovered > 0) {
            log.info("Recovered unfinished diagnostics after process restart")
                .field("recoveredCount", recovered)
                .log();
        }
    }
}
