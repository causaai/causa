package com.causa.core.services.impl;

import com.causa.common.constants.AlertConstants.AlertSeverity;
import com.causa.common.constants.AlertConstants.AlertStatus;
import com.causa.config.AlertConfig;
import com.causa.core.domain.Alert;
import com.causa.core.ports.AlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AlertServiceImpl}.
 *
 * @since 0.0.1
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AlertServiceImpl Tests")
class AlertServiceImplTest {

    @Mock
    private AlertConfig alertConfig;

    @Mock
    private AlertRepository alertRepository;

    private AlertServiceImpl alertService;

    @BeforeEach
    void setUp() {
        lenient().when(alertConfig.filterSeverity()).thenReturn("warning");
        lenient().when(alertConfig.cooldownMinutes()).thenReturn(15);
        lenient().when(alertConfig.ignoreNamespaces()).thenReturn(
                Optional.of(List.of("kube-system", "istio-system")));

        alertService = new AlertServiceImpl(alertConfig, alertRepository);
        alertService.init(); // package-private, accessible from same package
    }

    private static Alert buildAlert(String id, AlertSeverity severity, String namespace, String pod) {
        return Alert.builder()
                .alertId(id)
                .alertName("TestAlert")
                .severity(severity)
                .status(AlertStatus.PROCESSING)
                .workloadInfo(Alert.WorkloadInfo.of(pod, "container-1", namespace, "cluster-1", "Deployment"))
                .workloadName("container-1")
                .build();
    }

    // -------------------------------------------------------------------------
    // processAlerts — severity filtering
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Severity Filter Tests")
    class SeverityFilterTests {

        @Test
        @DisplayName("Should accept alert that meets minimum severity (CRITICAL >= WARNING)")
        void shouldAcceptCriticalAlertWhenMinimumIsWarning() {
            Alert alert = buildAlert("a1", AlertSeverity.CRITICAL, "default", "pod-1");
            when(alertRepository.save(any())).thenReturn(alert);

            Map<String, String> rejected = new LinkedHashMap<>();
            List<Alert> accepted = alertService.processAlerts(List.of(alert), rejected);

            assertEquals(1, accepted.size());
            assertTrue(rejected.isEmpty());
            verify(alertRepository).save(alert);
        }

        @Test
        @DisplayName("Should accept alert that exactly meets minimum severity (WARNING == WARNING)")
        void shouldAcceptAlertAtExactMinimumSeverity() {
            Alert alert = buildAlert("a1", AlertSeverity.WARNING, "default", "pod-1");
            when(alertRepository.save(any())).thenReturn(alert);

            Map<String, String> rejected = new LinkedHashMap<>();
            List<Alert> accepted = alertService.processAlerts(List.of(alert), rejected);

            assertEquals(1, accepted.size());
        }

        @Test
        @DisplayName("Should reject alert below minimum severity (INFO < WARNING)")
        void shouldRejectAlertBelowMinimumSeverity() {
            Alert alert = buildAlert("a1", AlertSeverity.INFO, "default", "pod-1");
            when(alertRepository.saveRejected(any(), anyString())).thenReturn(alert);

            Map<String, String> rejected = new LinkedHashMap<>();
            List<Alert> accepted = alertService.processAlerts(List.of(alert), rejected);

            assertEquals(0, accepted.size());
            assertEquals(1, rejected.size());
            verify(alertRepository).saveRejected(eq(alert), contains("Severity too low"));
        }

        @Test
        @DisplayName("Should not call save for severity-rejected alerts")
        void shouldNotCallSaveForSeverityRejectedAlerts() {
            Alert alert = buildAlert("a1", AlertSeverity.INFO, "default", "pod-1");
            when(alertRepository.saveRejected(any(), anyString())).thenReturn(alert);

            alertService.processAlerts(List.of(alert), new HashMap<>());

            verify(alertRepository, never()).save(any());
        }
    }

    // -------------------------------------------------------------------------
    // processAlerts — namespace filtering
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Namespace Filter Tests")
    class NamespaceFilterTests {

        @Test
        @DisplayName("Should reject alert from ignored namespace (kube-system)")
        void shouldRejectAlertFromIgnoredNamespace() {
            Alert alert = buildAlert("a1", AlertSeverity.CRITICAL, "kube-system", "pod-1");
            when(alertRepository.saveRejected(any(), anyString())).thenReturn(alert);

            Map<String, String> rejected = new LinkedHashMap<>();
            List<Alert> accepted = alertService.processAlerts(List.of(alert), rejected);

            assertEquals(0, accepted.size());
            assertEquals(1, rejected.size());
            verify(alertRepository).saveRejected(eq(alert), contains("kube-system"));
        }

        @Test
        @DisplayName("Should reject alert from another ignored namespace (istio-system)")
        void shouldRejectAlertFromIstioSystem() {
            Alert alert = buildAlert("a1", AlertSeverity.CRITICAL, "istio-system", "pod-1");
            when(alertRepository.saveRejected(any(), anyString())).thenReturn(alert);

            Map<String, String> rejected = new LinkedHashMap<>();
            alertService.processAlerts(List.of(alert), rejected);

            assertEquals(1, rejected.size());
        }

        @Test
        @DisplayName("Should accept alert from non-ignored namespace")
        void shouldAcceptAlertFromNonIgnoredNamespace() {
            Alert alert = buildAlert("a1", AlertSeverity.CRITICAL, "production", "pod-1");
            when(alertRepository.save(any())).thenReturn(alert);

            Map<String, String> rejected = new LinkedHashMap<>();
            List<Alert> accepted = alertService.processAlerts(List.of(alert), rejected);

            assertEquals(1, accepted.size());
            assertTrue(rejected.isEmpty());
        }

        @Test
        @DisplayName("Should accept alert with null namespace")
        void shouldAcceptAlertWithNullNamespace() {
            Alert alert = buildAlert("a1", AlertSeverity.CRITICAL, null, "pod-1");
            when(alertRepository.save(any())).thenReturn(alert);

            List<Alert> accepted = alertService.processAlerts(List.of(alert), new HashMap<>());

            assertEquals(1, accepted.size());
        }
    }

    // -------------------------------------------------------------------------
    // processAlerts — cooldown filtering
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Cooldown Filter Tests")
    class CooldownFilterTests {

        @Test
        @DisplayName("Should accept first alert (no cooldown)")
        void shouldAcceptFirstAlert() {
            Alert alert = buildAlert("a1", AlertSeverity.CRITICAL, "default", "pod-1");
            when(alertRepository.save(any())).thenReturn(alert);

            List<Alert> accepted = alertService.processAlerts(List.of(alert), new HashMap<>());

            assertEquals(1, accepted.size());
        }

        @Test
        @DisplayName("Should reject duplicate alert within cooldown window")
        void shouldRejectDuplicateAlertWithinCooldown() {
            Alert firstAlert = buildAlert("a1", AlertSeverity.CRITICAL, "default", "pod-1");
            Alert secondAlert = buildAlert("a2", AlertSeverity.CRITICAL, "default", "pod-1");
            when(alertRepository.save(any())).thenReturn(firstAlert);
            when(alertRepository.saveRejected(any(), anyString())).thenReturn(secondAlert);

            // First: accepted
            alertService.processAlerts(List.of(firstAlert), new HashMap<>());
            // Second (same key): should be in cooldown
            Map<String, String> rejected = new LinkedHashMap<>();
            List<Alert> accepted = alertService.processAlerts(List.of(secondAlert), rejected);

            assertEquals(0, accepted.size());
            assertEquals(1, rejected.size());
            verify(alertRepository).saveRejected(eq(secondAlert), contains("cooldown"));
        }

        @Test
        @DisplayName("Different pods should not share cooldown")
        void shouldNotShareCooldownBetweenDifferentPods() {
            Alert alert1 = buildAlert("a1", AlertSeverity.CRITICAL, "default", "pod-A");
            Alert alert2 = buildAlert("a2", AlertSeverity.CRITICAL, "default", "pod-B");
            when(alertRepository.save(any())).thenReturn(alert1, alert2);

            Map<String, String> rejected = new HashMap<>();
            List<Alert> accepted = alertService.processAlerts(List.of(alert1, alert2), rejected);

            assertEquals(2, accepted.size());
            assertTrue(rejected.isEmpty());
        }
    }

    // -------------------------------------------------------------------------
    // processAlerts — mixed batch
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Mixed Batch Processing Tests")
    class MixedBatchTests {

        @Test
        @DisplayName("Should handle mixed accepted/rejected batch")
        void shouldHandleMixedBatch() {
            Alert criticalAlert = buildAlert("a1", AlertSeverity.CRITICAL, "default", "pod-1");
            Alert infoAlert     = buildAlert("a2", AlertSeverity.INFO, "default", "pod-2");
            Alert ignoredNsAlert = buildAlert("a3", AlertSeverity.CRITICAL, "kube-system", "pod-3");

            when(alertRepository.save(any())).thenReturn(criticalAlert);
            when(alertRepository.saveRejected(eq(infoAlert), anyString())).thenReturn(infoAlert);
            when(alertRepository.saveRejected(eq(ignoredNsAlert), anyString())).thenReturn(ignoredNsAlert);

            Map<String, String> rejected = new LinkedHashMap<>();
            List<Alert> accepted = alertService.processAlerts(List.of(criticalAlert, infoAlert, ignoredNsAlert), rejected);

            assertEquals(1, accepted.size());
            assertEquals(2, rejected.size());
        }

        @Test
        @DisplayName("Should handle empty alert list gracefully")
        void shouldHandleEmptyAlertList() {
            List<Alert> accepted = alertService.processAlerts(List.of(), new HashMap<>());

            assertTrue(accepted.isEmpty());
            verifyNoInteractions(alertRepository);
        }
    }

    // -------------------------------------------------------------------------
    // isInCooldown
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("isInCooldown Tests")
    class IsInCooldownTests {

        @Test
        @DisplayName("Should return false when alert is not in cache")
        void shouldReturnFalseWhenNotInCache() {
            Alert alert = buildAlert("a1", AlertSeverity.CRITICAL, "default", "pod-fresh");

            assertFalse(alertService.isInCooldown(alert));
        }

        @Test
        @DisplayName("Should return true after alert is processed (within cooldown)")
        void shouldReturnTrueAfterProcessed() {
            Alert alert = buildAlert("a1", AlertSeverity.CRITICAL, "default", "pod-1");
            when(alertRepository.save(any())).thenReturn(alert);

            alertService.processAlerts(List.of(alert), new HashMap<>());

            assertTrue(alertService.isInCooldown(alert));
        }
    }

    // -------------------------------------------------------------------------
    // getAlert / getAlerts
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Query Tests")
    class QueryTests {

        @Test
        @DisplayName("Should delegate getAlert to repository")
        void shouldDelegateGetAlertToRepository() {
            Alert alert = buildAlert("a1", AlertSeverity.CRITICAL, "default", "pod-1");
            when(alertRepository.findById("a1")).thenReturn(Optional.of(alert));

            Optional<Alert> result = alertService.getAlert("a1");

            assertTrue(result.isPresent());
            assertEquals("a1", result.get().getAlertId());
            verify(alertRepository).findById("a1");
        }

        @Test
        @DisplayName("Should return empty Optional when alert not found")
        void shouldReturnEmptyWhenAlertNotFound() {
            when(alertRepository.findById("missing")).thenReturn(Optional.empty());

            Optional<Alert> result = alertService.getAlert("missing");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should delegate getAlerts with filters to repository")
        void shouldDelegateGetAlertsToRepository() {
            when(alertRepository.findByFilters("my-app", "production")).thenReturn(List.of());

            alertService.getAlerts("my-app", "production");

            verify(alertRepository).findByFilters("my-app", "production");
        }

        @Test
        @DisplayName("Should delegate getAlerts with null filters to repository")
        void shouldDelegateGetAlertsWithNullFilters() {
            when(alertRepository.findByFilters(null, null)).thenReturn(List.of());

            alertService.getAlerts(null, null);

            verify(alertRepository).findByFilters(null, null);
        }
    }

    // -------------------------------------------------------------------------
    // init — no ignored namespaces
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Initialization Tests")
    class InitializationTests {

        @Test
        @DisplayName("Should work with empty ignore namespaces list")
        void shouldWorkWithEmptyIgnoreNamespaces() {
            when(alertConfig.filterSeverity()).thenReturn("critical");
            when(alertConfig.cooldownMinutes()).thenReturn(10);
            when(alertConfig.ignoreNamespaces()).thenReturn(Optional.empty());

            AlertServiceImpl service = new AlertServiceImpl(alertConfig, alertRepository);
            service.init(); // package-private, accessible from same package

            Alert alert = buildAlert("a1", AlertSeverity.CRITICAL, "any-namespace", "pod-1");
            when(alertRepository.save(any())).thenReturn(alert);

            List<Alert> accepted = service.processAlerts(List.of(alert), new HashMap<>());

            assertEquals(1, accepted.size());
        }
    }
}
