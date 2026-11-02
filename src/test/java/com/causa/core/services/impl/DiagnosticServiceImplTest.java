package com.causa.core.services.impl;

import com.causa.common.constants.AlertConstants.AlertSeverity;
import com.causa.common.constants.AlertConstants.AlertStatus;
import com.causa.common.constants.ConfigConstants.PlatformCategory;
import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import com.causa.config.ExternalConfigCache;
import com.causa.core.domain.Alert;
import com.causa.common.exceptions.DiagnosticException;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.ExternalConfig;
import com.causa.core.domain.JiraTicketOptions;
import com.causa.core.domain.PageRequest;
import com.causa.core.domain.PageResult;
import com.causa.core.ports.AlertRepository;
import com.causa.core.ports.DiagnosticRepository;
import com.causa.core.services.DiagnosticService;
import com.causa.core.services.RcaPromptBuilder;
import com.causa.notification.jira.JiraClient;
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
    private com.causa.mcp.McpRegistry mcpRegistry;

    @Mock
    private RcaPromptBuilder rcaPromptBuilder;

    @Mock
    private com.causa.core.ports.llm.PromptSender promptSender;

    @Mock
    private com.causa.config.LlmConfigCache llmConfigCache;

    @Mock
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Mock
    private jakarta.validation.Validator validator;

    @Mock
    private jakarta.enterprise.inject.Instance<com.causa.core.services.validation.RcaValidator> rcaValidatorInstance;

    @Mock
    private jakarta.enterprise.inject.Instance<com.causa.core.services.evidence.EvidenceHarvester> evidenceHarvesters;

    @Mock
    private ExternalConfigCache externalConfigCache;

    @Mock
    private JiraClient jiraClient;

    private DiagnosticServiceImpl diagnosticService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.when(rcaValidatorInstance.isResolvable()).thenReturn(false);
        // Return empty list by default — no Jira configured unless a specific test overrides this
        lenient().when(externalConfigCache.getByCategory(PlatformCategory.INTEGRATION)).thenReturn(List.of());
        diagnosticService = new DiagnosticServiceImpl(
                diagnosticRepository,
                alertRepository,
                mcpRegistry,
                rcaPromptBuilder,
                promptSender,
                llmConfigCache,
                objectMapper,
                validator,
                rcaValidatorInstance,
                evidenceHarvesters,
                externalConfigCache,
                jiraClient
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
        @DisplayName("Should delegate to repository and return paginated result")
        void shouldDelegateToRepository() {
            Diagnostic d1 = buildDiagnostic("d1", "a1", DiagnosticStatus.COMPLETED);
            Diagnostic d2 = buildDiagnostic("d2", "a2", DiagnosticStatus.PENDING);
            PageResult<Diagnostic> page = PageResult.of(List.of(d1, d2), 2L, PageRequest.of(1, 0));
            when(diagnosticRepository.search(any(), any())).thenReturn(page);

            PageResult<Diagnostic> result = diagnosticService.listDiagnostics(Diagnostic.Filter.empty(), PageRequest.of(1, 0));

            assertEquals(2, result.items().size());
            assertEquals(2L, result.total());
            verify(diagnosticRepository).search(any(), any());
        }

        @Test
        @DisplayName("Should return empty page when no diagnostics")
        void shouldReturnEmptyListWhenNoDiagnostics() {
            PageResult<Diagnostic> page = PageResult.of(List.of(), 0L, PageRequest.of(1, 0));
            when(diagnosticRepository.search(any(), any())).thenReturn(page);

            PageResult<Diagnostic> result = diagnosticService.listDiagnostics(Diagnostic.Filter.empty(), PageRequest.of(1, 0));

            assertTrue(result.items().isEmpty());
            assertEquals(0L, result.total());
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

    // -------------------------------------------------------------------------
    // createOrGetJiraTicket
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("createOrGetJiraTicket Tests")
    class CreateOrGetJiraTicketTests {

        private static final String TICKET_URL = "https://acme.atlassian.net/browse/OPS-1";
        private static final JiraTicketOptions OPTIONS = JiraTicketOptions.defaults();

        private ExternalConfig jiraConfig;

        @BeforeEach
        void configureJira() {
            jiraConfig = ExternalConfig.builder()
                    .id("cfg-1")
                    .name("jira")
                    .authConfig(new com.causa.core.domain.AuthConfig(
                            null, null, null, null, null, null, null, null))
                    .platform("JIRA")
                    .category(PlatformCategory.INTEGRATION)
                    .isActive(true)
                    .build();
            when(externalConfigCache.getByCategory(PlatformCategory.INTEGRATION)).thenReturn(List.of(jiraConfig));
        }

        @Test
        @DisplayName("Should lock the alert before checking for an existing ticket")
        void shouldLockAlertBeforeExistenceCheck() {
            when(diagnosticRepository.findById("d1"))
                    .thenReturn(Optional.of(buildDiagnostic("d1", "alert-1", DiagnosticStatus.COMPLETED)));
            when(diagnosticRepository.findTicketLinkByAlertId("alert-1")).thenReturn(Optional.of(TICKET_URL));

            DiagnosticService.JiraTicket ticket = diagnosticService.createOrGetJiraTicket("d1", OPTIONS);

            assertEquals(new DiagnosticService.JiraTicket(TICKET_URL, true), ticket);
            var order = inOrder(diagnosticRepository);
            order.verify(diagnosticRepository).lockAlertForTicketCreation("alert-1");
            order.verify(diagnosticRepository).findTicketLinkByAlertId("alert-1");
            verifyNoInteractions(jiraClient);
        }

        @Test
        @DisplayName("Should adopt a ticket that already exists in Jira instead of creating a duplicate")
        void shouldRecoverTicketAlreadyInJira() {
            when(diagnosticRepository.findById("d1"))
                    .thenReturn(Optional.of(buildDiagnostic("d1", "alert-1", DiagnosticStatus.COMPLETED)));
            when(diagnosticRepository.findTicketLinkByAlertId("alert-1")).thenReturn(Optional.empty());
            when(jiraClient.findTicketByAlertId(jiraConfig, "alert-1")).thenReturn(Optional.of(TICKET_URL));

            assertEquals(new DiagnosticService.JiraTicket(TICKET_URL, true),
                    diagnosticService.createOrGetJiraTicket("d1", OPTIONS));

            verify(diagnosticRepository).updateTicketLink("d1", TICKET_URL);
            verify(jiraClient, never()).createTicket(any(), any(), any());
        }

        @Test
        @DisplayName("Should create and persist a ticket when none exists in the DB or Jira")
        void shouldCreateTicketWhenNoneExists() {
            Diagnostic diagnostic = buildDiagnostic("d1", "alert-1", DiagnosticStatus.COMPLETED);
            when(diagnosticRepository.findById("d1")).thenReturn(Optional.of(diagnostic));
            when(diagnosticRepository.findTicketLinkByAlertId("alert-1")).thenReturn(Optional.empty());
            when(jiraClient.findTicketByAlertId(jiraConfig, "alert-1")).thenReturn(Optional.empty());
            when(jiraClient.createTicket(jiraConfig, diagnostic, OPTIONS)).thenReturn(TICKET_URL);

            assertEquals(new DiagnosticService.JiraTicket(TICKET_URL, false),
                    diagnosticService.createOrGetJiraTicket("d1", OPTIONS));

            verify(diagnosticRepository).updateTicketLink("d1", TICKET_URL);
        }

        @Test
        @DisplayName("Should not create a ticket when the Jira lookup fails")
        void shouldNotCreateTicketWhenLookupFails() {
            when(diagnosticRepository.findById("d1"))
                    .thenReturn(Optional.of(buildDiagnostic("d1", "alert-1", DiagnosticStatus.COMPLETED)));
            when(diagnosticRepository.findTicketLinkByAlertId("alert-1")).thenReturn(Optional.empty());
            when(jiraClient.findTicketByAlertId(jiraConfig, "alert-1"))
                    .thenThrow(new DiagnosticException("Jira API error: HTTP 500", "JIRA_API_ERROR"));

            assertThrows(DiagnosticException.class, () -> diagnosticService.createOrGetJiraTicket("d1", OPTIONS));

            verify(jiraClient, never()).createTicket(any(), any(), any());
            verify(diagnosticRepository, never()).updateTicketLink(any(), any());
        }

        @Test
        @DisplayName("Should reject oversized notes with INVALID_REQUEST before touching the DB or Jira")
        void shouldRejectOversizedNotes() {
            JiraTicketOptions tooLong = new JiraTicketOptions(
                    null, false, false, false, "x".repeat(JiraTicketOptions.MAX_NOTES_LENGTH + 1));

            DiagnosticException ex = assertThrows(DiagnosticException.class,
                    () -> diagnosticService.createOrGetJiraTicket("d1", tooLong));

            assertEquals("INVALID_REQUEST", ex.getErrorType());
            verify(diagnosticRepository, never()).lockAlertForTicketCreation(any());
            verifyNoInteractions(jiraClient);
        }

        @Test
        @DisplayName("Should throw JIRA_NOT_CONFIGURED when no active Jira integration exists")
        void shouldThrowWhenJiraNotConfigured() {
            when(externalConfigCache.getByCategory(PlatformCategory.INTEGRATION)).thenReturn(List.of());

            DiagnosticException ex = assertThrows(DiagnosticException.class,
                    () -> diagnosticService.createOrGetJiraTicket("d1", OPTIONS));

            assertEquals("JIRA_NOT_CONFIGURED", ex.getErrorType());
            verify(diagnosticRepository, never()).lockAlertForTicketCreation(any());
        }
    }

    @Nested
    @DisplayName("searchJiraUsers Tests")
    class SearchJiraUsersTests {

        @Test
        @DisplayName("Should search through the Jira client using the active Jira config")
        void shouldSearchUsers() {
            ExternalConfig jiraConfig = ExternalConfig.builder()
                    .id("cfg-1").name("jira").platform("JIRA")
                    .authConfig(new com.causa.core.domain.AuthConfig(null, null, null, null, null, null, null, null))
                    .category(PlatformCategory.INTEGRATION).isActive(true).build();
            when(externalConfigCache.getByCategory(PlatformCategory.INTEGRATION)).thenReturn(List.of(jiraConfig));
            List<com.causa.core.domain.JiraUser> users = List.of(new com.causa.core.domain.JiraUser("u1", "Ann", null));
            when(jiraClient.searchAssignableUsers(jiraConfig, "an")).thenReturn(users);

            assertEquals(users, diagnosticService.searchJiraUsers("an"));
        }

        @Test
        @DisplayName("Should throw JIRA_NOT_CONFIGURED when no active Jira integration exists")
        void shouldThrowWhenJiraNotConfigured() {
            when(externalConfigCache.getByCategory(PlatformCategory.INTEGRATION)).thenReturn(List.of());

            DiagnosticException ex = assertThrows(DiagnosticException.class,
                    () -> diagnosticService.searchJiraUsers("an"));

            assertEquals("JIRA_NOT_CONFIGURED", ex.getErrorType());
            verifyNoInteractions(jiraClient);
        }
    }
}
