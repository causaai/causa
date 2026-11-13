package com.causa.core.services.impl;

import com.causa.common.constants.AlertConstants.AlertStatus;
import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.PageRequest;
import com.causa.core.ports.AlertRepository;
import com.causa.core.ports.DiagnosticRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiagnosticRecoveryBatchProcessorTest {

    private static final PageRequest FIRST_BATCH = PageRequest.of(1, DiagnosticRecoveryBatchProcessor.BATCH_SIZE);

    @Mock
    private DiagnosticRepository diagnosticRepository;

    @Mock
    private AlertRepository alertRepository;

    private DiagnosticRecoveryBatchProcessor batchProcessor;

    @BeforeEach
    void setUp() {
        batchProcessor = new DiagnosticRecoveryBatchProcessor(diagnosticRepository, alertRepository);
    }

    @Test
    void shouldRecoverActiveDiagnosticsAndFinalizeTerminalAlerts() {
        List<Diagnostic> diagnostics = List.of(
            diagnostic("pending", DiagnosticStatus.PENDING),
            diagnostic("in-progress", DiagnosticStatus.IN_PROGRESS),
            diagnostic("processing", DiagnosticStatus.PROCESSING),
            diagnostic("validating", DiagnosticStatus.VALIDATING),
            diagnostic("completed", DiagnosticStatus.COMPLETED),
            diagnostic("failed", DiagnosticStatus.FAILED));
        when(diagnosticRepository.findUnfinished(FIRST_BATCH)).thenReturn(diagnostics);
        when(diagnosticRepository.updateStatusIfCurrent(any(), any(), any())).thenReturn(true);

        DiagnosticRecoveryBatchProcessor.BatchResult result = batchProcessor.recoverNextBatch();

        assertEquals(6, result.selected());
        assertEquals(6, result.recovered());
        verify(diagnosticRepository).updateStatusIfCurrent(
            "pending", DiagnosticStatus.PENDING, DiagnosticStatus.FAILED);
        verify(diagnosticRepository).updateStatusIfCurrent(
            "in-progress", DiagnosticStatus.IN_PROGRESS, DiagnosticStatus.FAILED);
        verify(diagnosticRepository).updateStatusIfCurrent(
            "processing", DiagnosticStatus.PROCESSING, DiagnosticStatus.FAILED);
        verify(diagnosticRepository).updateStatusIfCurrent(
            "validating", DiagnosticStatus.VALIDATING, DiagnosticStatus.FAILED);
        verify(diagnosticRepository, never()).updateStatusIfCurrent(
            "completed", DiagnosticStatus.COMPLETED, DiagnosticStatus.FAILED);
        verify(diagnosticRepository, never()).updateStatusIfCurrent(
            "failed", DiagnosticStatus.FAILED, DiagnosticStatus.FAILED);
        for (Diagnostic diagnostic : diagnostics) {
            verify(alertRepository).updateProcessingStatus(
                diagnostic.getAlertId(), AlertStatus.PROCESSED.getValue());
        }
    }

    @Test
    void shouldLeaveAlertUntouchedWhenActiveDiagnosticChanged() {
        Diagnostic diagnostic = diagnostic("active", DiagnosticStatus.IN_PROGRESS);
        when(diagnosticRepository.findUnfinished(FIRST_BATCH)).thenReturn(List.of(diagnostic));
        when(diagnosticRepository.updateStatusIfCurrent(
            "active", DiagnosticStatus.IN_PROGRESS, DiagnosticStatus.FAILED)).thenReturn(false);

        DiagnosticRecoveryBatchProcessor.BatchResult result = batchProcessor.recoverNextBatch();

        assertEquals(1, result.selected());
        assertEquals(0, result.recovered());
        verify(alertRepository, never()).updateProcessingStatus(any(), any());
    }

    @Test
    void shouldPropagateAlertFailureFromTheAlertUpdate() {
        Diagnostic diagnostic = diagnostic("active", DiagnosticStatus.VALIDATING);
        IllegalStateException expected = new IllegalStateException("alert update failed");
        when(diagnosticRepository.findUnfinished(FIRST_BATCH)).thenReturn(List.of(diagnostic));
        when(diagnosticRepository.updateStatusIfCurrent(
            "active", DiagnosticStatus.VALIDATING, DiagnosticStatus.FAILED)).thenReturn(true);
        doThrow(expected).when(alertRepository).updateProcessingStatus(
            diagnostic.getAlertId(), AlertStatus.PROCESSED.getValue());

        IllegalStateException actual = assertThrows(
            IllegalStateException.class, () -> batchProcessor.recoverNextBatch());

        assertSame(expected, actual);
        verify(alertRepository).updateProcessingStatus(
            diagnostic.getAlertId(), AlertStatus.PROCESSED.getValue());
    }

    private static Diagnostic diagnostic(String diagnosticId, DiagnosticStatus status) {
        return Diagnostic.builder()
            .diagnosticId(diagnosticId)
            .alertId("alert-" + diagnosticId)
            .status(status)
            .generatedAt(Instant.EPOCH)
            .build();
    }
}
