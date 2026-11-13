package com.causa.core.services.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiagnosticRecoveryServiceTest {

    @Mock
    private DiagnosticRecoveryBatchProcessor batchProcessor;

    private DiagnosticRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        recoveryService = new DiagnosticRecoveryService(batchProcessor);
    }

    @Test
    void shouldProcessBatchesUntilNoDiagnosticsRemain() {
        when(batchProcessor.recoverNextBatch())
            .thenReturn(new DiagnosticRecoveryBatchProcessor.BatchResult(100, 100))
            .thenReturn(new DiagnosticRecoveryBatchProcessor.BatchResult(2, 2))
            .thenReturn(new DiagnosticRecoveryBatchProcessor.BatchResult(0, 0));

        recoveryService.recoverUnfinishedDiagnostics(null);

        verify(batchProcessor, org.mockito.Mockito.times(3)).recoverNextBatch();
    }

    @Test
    void shouldKeepStartupRunningWhenRecoveryBatchFails() {
        when(batchProcessor.recoverNextBatch())
            .thenThrow(new IllegalStateException("database unavailable"));

        assertDoesNotThrow(() -> recoveryService.recoverUnfinishedDiagnostics(null));

        verify(batchProcessor).recoverNextBatch();
    }
}
