package com.causa.core.services.impl;

import com.causa.common.constants.AppConstants;
import com.causa.common.logging.CausaLogger;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class DiagnosticRecoveryService {

    private static final CausaLogger log = CausaLogger.getLogger(DiagnosticRecoveryService.class);

    private final DiagnosticRecoveryBatchProcessor batchProcessor;

    @Inject
    public DiagnosticRecoveryService(DiagnosticRecoveryBatchProcessor batchProcessor) {
        this.batchProcessor = batchProcessor;
    }

    void recoverUnfinishedDiagnostics(
            @Observes @Priority(AppConstants.StartupConstants.DIAGNOSTIC_RECOVERY_PRIORITY) StartupEvent event) {
        int recovered = 0;
        while (true) {
            DiagnosticRecoveryBatchProcessor.BatchResult result;
            try {
                result = batchProcessor.recoverNextBatch();
            } catch (RuntimeException e) {
                log.warn("Diagnostic restart recovery failed; it will be retried on the next restart")
                    .exception(e)
                    .log();
                return;
            }

            recovered += result.recovered();
            if (result.selected() == 0 || result.recovered() == 0) {
                break;
            }
        }

        if (recovered > 0) {
            log.info("Recovered unfinished diagnostics after process restart")
                .field("recoveredCount", recovered)
                .log();
        }
    }
}
