package com.causa.core.services.impl;

import com.causa.common.constants.AlertConstants.AlertSeverity;
import com.causa.common.constants.AlertConstants.AlertStatus;
import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import com.causa.core.domain.Alert;
import com.causa.core.domain.Diagnostic;
import com.causa.core.ports.AlertRepository;
import com.causa.core.ports.DiagnosticRepository;
import com.causa.core.services.RcaPromptBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DiagnosticServiceImpl}.
 *
 * <p>The full async pipeline (MCP context + LLM) runs on a background thread and is
 * not tested here (it requires integration testing). These unit tests verify:
 * <ul>
 *   <li>{@link DiagnosticService#triggerDiagnostics} — creates a PENDING diagnostic, persists it, and returns immediately</li>
 *   <li>{@link DiagnosticService#listDiagnostics} — delegates to repository</li>
 *   <li>{@link DiagnosticService#getDiagnosticById} — delegates to repository</li>
 * </ul>
 *
 * @since 0.0.1
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DiagnosticServiceImpl Tests")
class DiagnosticServiceImplTest {

    @Mock
    private DiagnosticRepository diagnosticRepository;

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private com.causa.mcp.McpContextCollector mcpContextCollector;

    @Mock
    private RcaPromptBuilder rcaPromptBuilder;

    @Mock
    private com.causa.core.ports.llm.PromptSender promptSender;

    @Mock
    private com.causa.config.AppConfig appConfig;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Mock
    private jakarta.validation.Validator validator;

    @Mock
    private jakarta.enterprise.inject.Instance<com.causa.core.services.validation.RcaValidator> rcaValidatorInstance;

    private DiagnosticServiceImpl diagnosticService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.when(rcaValidatorInstance.isResolvable()).thenReturn(false);
        diagnosticService = new DiagnosticServiceImpl(
                diagnosticRepository,
                alertRepository,
                mcpContextCollector,
                rcaPromptBuilder,
                promptSender,
                appConfig,
                objectMapper,
                validator,
                rcaValidatorInstance
        );
    }

    private static Alert buildAlert(String id) {
        return Alert.builder()
                .alertId(id)
                .alertName("TestAlert")
                .severity(AlertSeverity.CRITICAL)
                .status(AlertStatus.ACCEPTED)
                .workloadInfo(Alert.WorkloadInfo.of("pod-1", "container-1", "default", "cluster-1", "Deployment"))
                .workloadName("container-1")
                .build();
    }

    private static Diagnostic buildDiagnostic(String id, String alertId, DiagnosticStatus status) {
        return Diagnostic.builder()
                .diagnosticId(id)
                .alertId(alertId)
                .status(status)
                .generatedAt(Instant.now())
                .build();
    }

    // -------------------------------------------------------------------------
    // triggerDiagnostics
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("triggerDiagnostics Tests")
    class TriggerDiagnosticsTests {

        @Test
        @DisplayName("Should return PENDING diagnostic immediately")
        void shouldReturnPendingDiagnosticImmediately() {
            Alert alert = buildAlert("alert-1");
            when(diagnosticRepository.save(any(Diagnostic.class))).thenAnswer(inv -> inv.getArgument(0));

            Diagnostic result = diagnosticService.triggerDiagnostics(alert);

            assertNotNull(result);
            assertEquals(DiagnosticStatus.PENDING, result.getStatus());
            assertEquals("alert-1", result.getAlertId());
        }

        @Test
        @DisplayName("Should generate a unique diagnostic ID")
        void shouldGenerateUniqueDiagnosticId() {
            Alert alert = buildAlert("alert-1");
            when(diagnosticRepository.save(any(Diagnostic.class))).thenAnswer(inv -> inv.getArgument(0));

            Diagnostic result = diagnosticService.triggerDiagnostics(alert);

            assertNotNull(result.getDiagnosticId());
            assertFalse(result.getDiagnosticId().isBlank());
        }

        @Test
        @DisplayName("Should persist the PENDING diagnostic stub")
        void shouldPersistPendingDiagnosticStub() {
            Alert alert = buildAlert("alert-1");
            when(diagnosticRepository.save(any(Diagnostic.class))).thenAnswer(inv -> inv.getArgument(0));

            diagnosticService.triggerDiagnostics(alert);

            verify(diagnosticRepository).save(any(Diagnostic.class));
        }

        @Test
        @DisplayName("Should return before pipeline completes (non-blocking)")
        void shouldReturnImmediately() {
            Alert alert = buildAlert("alert-1");
            when(diagnosticRepository.save(any(Diagnostic.class))).thenAnswer(inv -> inv.getArgument(0));

            // Should complete without waiting for background pipeline
            long start = System.currentTimeMillis();
            Diagnostic result = diagnosticService.triggerDiagnostics(alert);
            long elapsed = System.currentTimeMillis() - start;

            assertNotNull(result);
            assertTrue(elapsed < 5000, "triggerDiagnostics should return immediately (< 5s), took: " + elapsed + "ms");
        }

        @Test
        @DisplayName("Should generate different IDs for different alerts")
        void shouldGenerateDifferentIdsForDifferentAlerts() {
            Alert alert1 = buildAlert("alert-1");
            Alert alert2 = buildAlert("alert-2");
            when(diagnosticRepository.save(any(Diagnostic.class))).thenAnswer(inv -> inv.getArgument(0));

            Diagnostic d1 = diagnosticService.triggerDiagnostics(alert1);
            Diagnostic d2 = diagnosticService.triggerDiagnostics(alert2);

            assertNotEquals(d1.getDiagnosticId(), d2.getDiagnosticId());
        }

        @Test
        @DisplayName("Should set generatedAt to approximately now")
        void shouldSetGeneratedAtToNow() {
            Alert alert = buildAlert("alert-1");
            when(diagnosticRepository.save(any(Diagnostic.class))).thenAnswer(inv -> inv.getArgument(0));
            Instant before = Instant.now();

            Diagnostic result = diagnosticService.triggerDiagnostics(alert);

            Instant after = Instant.now();
            assertNotNull(result.getGeneratedAt());
            assertTrue(result.getGeneratedAt().isAfter(before.minusSeconds(1)));
            assertTrue(result.getGeneratedAt().isBefore(after.plusSeconds(1)));
        }
    }

    // -------------------------------------------------------------------------
    // listDiagnostics
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("listDiagnostics Tests")
    class ListDiagnosticsTests {

        @Test
        @DisplayName("Should delegate to repository")
        void shouldDelegateToRepository() {
            Diagnostic d1 = buildDiagnostic("d1", "a1", DiagnosticStatus.COMPLETED);
            Diagnostic d2 = buildDiagnostic("d2", "a2", DiagnosticStatus.PENDING);
            when(diagnosticRepository.findAll()).thenReturn(List.of(d1, d2));

            List<Diagnostic> result = diagnosticService.listDiagnostics();

            assertEquals(2, result.size());
            verify(diagnosticRepository).findAll();
        }

        @Test
        @DisplayName("Should return empty list when no diagnostics")
        void shouldReturnEmptyListWhenNoDiagnostics() {
            when(diagnosticRepository.findAll()).thenReturn(List.of());

            List<Diagnostic> result = diagnosticService.listDiagnostics();

            assertTrue(result.isEmpty());
        }
    }

    // -------------------------------------------------------------------------
    // getDiagnosticById
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getDiagnosticById Tests")
    class GetDiagnosticByIdTests {

        @Test
        @DisplayName("Should return diagnostic when found")
        void shouldReturnDiagnosticWhenFound() {
            Diagnostic d = buildDiagnostic("diag-xyz", "alert-xyz", DiagnosticStatus.COMPLETED);
            when(diagnosticRepository.findById("diag-xyz")).thenReturn(Optional.of(d));

            Optional<Diagnostic> result = diagnosticService.getDiagnosticById("diag-xyz");

            assertTrue(result.isPresent());
            assertEquals("diag-xyz", result.get().getDiagnosticId());
            verify(diagnosticRepository).findById("diag-xyz");
        }

        @Test
        @DisplayName("Should return empty when diagnostic not found")
        void shouldReturnEmptyWhenNotFound() {
            when(diagnosticRepository.findById("missing")).thenReturn(Optional.empty());

            Optional<Diagnostic> result = diagnosticService.getDiagnosticById("missing");

            assertTrue(result.isEmpty());
        }
    }
}
