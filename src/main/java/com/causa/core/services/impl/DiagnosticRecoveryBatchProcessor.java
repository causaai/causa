package com.causa.core.services.impl;

import com.causa.common.constants.AlertConstants.AlertStatus;
import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.PageRequest;
import com.causa.core.ports.AlertRepository;
import com.causa.core.ports.DiagnosticRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

/** Processes one bounded restart-recovery batch in its own transaction. */
@ApplicationScoped
public class DiagnosticRecoveryBatchProcessor {

    static final int BATCH_SIZE = 100;

    private final DiagnosticRepository diagnosticRepository;
    private final AlertRepository alertRepository;

    @Inject
    public DiagnosticRecoveryBatchProcessor(DiagnosticRepository diagnosticRepository,
                                            AlertRepository alertRepository) {
        this.diagnosticRepository = diagnosticRepository;
        this.alertRepository = alertRepository;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    BatchResult recoverNextBatch() {
        List<Diagnostic> diagnostics = diagnosticRepository.findUnfinished(
            PageRequest.of(1, BATCH_SIZE));
        int recovered = 0;

        for (Diagnostic diagnostic : diagnostics) {
            if (isActive(diagnostic.getStatus())
                    && !diagnosticRepository.updateStatusIfCurrent(
                        diagnostic.getDiagnosticId(), diagnostic.getStatus(), DiagnosticStatus.FAILED)) {
                continue;
            }

            alertRepository.updateProcessingStatus(
                diagnostic.getAlertId(), AlertStatus.PROCESSED.getValue());
            recovered++;
        }

        return new BatchResult(diagnostics.size(), recovered);
    }

    private static boolean isActive(DiagnosticStatus status) {
        return status == DiagnosticStatus.PENDING
            || status == DiagnosticStatus.IN_PROGRESS
            || status == DiagnosticStatus.PROCESSING
            || status == DiagnosticStatus.VALIDATING;
    }

    record BatchResult(int selected, int recovered) {}
}
