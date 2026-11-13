package com.causa.core.services.impl;

import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import com.causa.core.domain.Diagnostic;
import com.causa.core.ports.AlertRepository;
import com.causa.core.ports.DiagnosticRepository;
import com.causa.infrastructure.persistence.mappers.AlertEntityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiagnosticRecoveryServiceTest {

    @Mock
    private DiagnosticRepository diagnosticRepository;

    @Mock
    private AlertRepository alertRepository;

    private DiagnosticRecoveryService recoveryService;

    @BeforeEach
    void setUp() {
        recoveryService = new DiagnosticRecoveryService(diagnosticRepository, alertRepository);
    }

    @Test
    void shouldFailUnfinishedDiagnosticsAndFinalizeTheirAlerts() {
        List<Diagnostic> unfinished = List.of(
            diagnostic("pending", "alert-pending", DiagnosticStatus.PENDING),
            diagnostic("in-progress", "alert-in-progress", DiagnosticStatus.IN_PROGRESS),
            diagnostic("validating", "alert-validating", DiagnosticStatus.VALIDATING));
        when(diagnosticRepository.findUnfinished()).thenReturn(unfinished);
        when(diagnosticRepository.updateStatusIfCurrent(
                "pending", DiagnosticStatus.PENDING, DiagnosticStatus.FAILED)).thenReturn(true);
        when(diagnosticRepository.updateStatusIfCurrent(
                "in-progress", DiagnosticStatus.IN_PROGRESS, DiagnosticStatus.FAILED)).thenReturn(true);
        when(diagnosticRepository.updateStatusIfCurrent(
                "validating", DiagnosticStatus.VALIDATING, DiagnosticStatus.FAILED)).thenReturn(true);

        recoveryService.recoverUnfinishedDiagnostics(null);

        InOrder order = inOrder(diagnosticRepository, alertRepository);
        for (Diagnostic diagnostic : unfinished) {
            order.verify(diagnosticRepository).updateStatusIfCurrent(
                diagnostic.getDiagnosticId(), diagnostic.getStatus(), DiagnosticStatus.FAILED);
            order.verify(alertRepository).updateProcessingStatus(
                diagnostic.getAlertId(), AlertEntityMapper.STATUS_PROCESSED);
        }
    }

    @Test
    void shouldNotFinalizeAlertWhenDiagnosticStatusChangedBeforeRecovery() {
        Diagnostic diagnostic = diagnostic("active", "alert-active", DiagnosticStatus.IN_PROGRESS);
        when(diagnosticRepository.findUnfinished()).thenReturn(List.of(diagnostic));
        when(diagnosticRepository.updateStatusIfCurrent(
                "active", DiagnosticStatus.IN_PROGRESS, DiagnosticStatus.FAILED)).thenReturn(false);

        recoveryService.recoverUnfinishedDiagnostics(null);

        verify(alertRepository, never()).updateProcessingStatus(
            diagnostic.getAlertId(), AlertEntityMapper.STATUS_PROCESSED);
    }

    @Test
    void shouldDoNothingWhenThereAreNoUnfinishedDiagnostics() {
        when(diagnosticRepository.findUnfinished()).thenReturn(List.of());

        assertDoesNotThrow(() -> recoveryService.recoverUnfinishedDiagnostics(null));

        verify(diagnosticRepository, never()).updateStatusIfCurrent(
            anyString(), any(DiagnosticStatus.class), any(DiagnosticStatus.class));
        verify(alertRepository, never()).updateProcessingStatus(anyString(), anyString());
    }

    @Test
    void shouldPropagateAlertFailureAfterDiagnosticTransition() {
        Diagnostic diagnostic = diagnostic("active", "alert-active", DiagnosticStatus.VALIDATING);
        when(diagnosticRepository.findUnfinished()).thenReturn(List.of(diagnostic));
        when(diagnosticRepository.updateStatusIfCurrent(
                "active", DiagnosticStatus.VALIDATING, DiagnosticStatus.FAILED)).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("alert update failed"))
            .when(alertRepository).updateProcessingStatus(
                diagnostic.getAlertId(), AlertEntityMapper.STATUS_PROCESSED);

        assertThrows(IllegalStateException.class,
            () -> recoveryService.recoverUnfinishedDiagnostics(null));
    }

    private static Diagnostic diagnostic(String diagnosticId, String alertId, DiagnosticStatus status) {
        return Diagnostic.builder()
            .diagnosticId(diagnosticId)
            .alertId(alertId)
            .status(status)
            .generatedAt(Instant.EPOCH)
            .build();
    }
}
