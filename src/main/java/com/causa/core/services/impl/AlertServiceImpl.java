package com.causa.core.services.impl;

import com.causa.common.constants.AlertConstants.AlertSeverity;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.config.AlertConfig;
import com.causa.core.domain.Alert;
import com.causa.core.ports.AlertRepository;
import com.causa.core.services.AlertService;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Alert Service Implementation
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class AlertServiceImpl implements AlertService {

    private static final CausaLogger log = CausaLogger.getLogger(AlertServiceImpl.class);

    private final AlertConfig alertConfig;
    private final AlertRepository alertRepository;
    private final ConcurrentHashMap<String, Instant> cooldownCache = new ConcurrentHashMap<>();

    private AlertSeverity minimumSeverity;
    private Set<String> ignoredNamespaces;

    @Inject
    public AlertServiceImpl(AlertConfig alertConfig, AlertRepository alertRepository) {
        this.alertConfig = alertConfig;
        this.alertRepository = alertRepository;
    }

    @PostConstruct
    void init() {
        this.minimumSeverity = AlertSeverity.fromString(alertConfig.filterSeverity());
        this.ignoredNamespaces = alertConfig.ignoreNamespaces()
            .map(Set::copyOf)
            .orElse(Set.of());

        log.info("Alert service initialized")
            .field("minimumSeverity", minimumSeverity.getValue())
            .field("cooldownMinutes", alertConfig.cooldownMinutes())
            .field("ignoredNamespaces", ignoredNamespaces)
            .log();
    }

    @Override
    public ProcessedAlerts processAlerts(List<Alert> alerts) {
        List<Alert> accepted = new ArrayList<>();
        Map<Alert, String> rejected = new LinkedHashMap<>();

        for (Alert alert : alerts) {
            if (!passesSeverityFilter(alert)) {
                log.debug(LogMessages.Alert.ALERT_FILTERED_SEVERITY)
                    .field("alertName", alert.getAlertName())
                    .field("severity", alert.getSeverity().getValue())
                    .field("minimum", minimumSeverity.getValue())
                    .log();
                rejected.put(alertRepository.saveRejected(alert, "severity"), "severity");
                continue;
            }

            if (!passesNamespaceFilter(alert)) {
                log.debug(LogMessages.Alert.ALERT_FILTERED_NAMESPACE)
                    .field("alertName", alert.getAlertName())
                    .field("namespace", alert.getWorkloadInfo().namespace())
                    .log();
                rejected.put(alertRepository.saveRejected(alert, "namespace"), "namespace");
                continue;
            }

            if (isInCooldown(alert)) {
                log.debug(LogMessages.Alert.ALERT_FILTERED_COOLDOWN)
                    .field("alertName", alert.getAlertName())
                    .field("podName", alert.getWorkloadInfo().podName())
                    .field("cooldownKey", alert.getCooldownKey())
                    .log();
                rejected.put(alertRepository.saveRejected(alert, "cooldown"), "cooldown");
                continue;
            }

            cooldownCache.put(alert.getCooldownKey(), Instant.now());
            Alert saved = alertRepository.save(alert);
            accepted.add(saved);

            log.info(LogMessages.Alert.ALERT_ACCEPTED)
                .field("alertId", saved.getAlertId())
                .field("alertName", saved.getAlertName())
                .field("severity", saved.getSeverity().getValue())
                .field("namespace", saved.getWorkloadInfo().namespace())
                .field("podName", saved.getWorkloadInfo().podName())
                .log();

            log.debug(LogMessages.Alert.ALERT_PERSISTED)
                .field("alertId", saved.getAlertId())
                .log();
        }

        return new ProcessedAlerts(accepted, rejected);
    }

    @Override
    public boolean isInCooldown(Alert alert) {
        String key = alert.getCooldownKey();
        Instant lastSeen = cooldownCache.get(key);
        if (lastSeen == null) return false;
        long cooldownSeconds = alertConfig.cooldownMinutes() * 60L;
        return Instant.now().isBefore(lastSeen.plusSeconds(cooldownSeconds));
    }

    @Override
    public Optional<Alert> getAlert(String alertId) {
        return alertRepository.findById(alertId);
    }

    @Override
    public List<Alert> getAlerts(String workloadName, String namespace) {
        // Delegates to repository which applies all non-blank filters with AND logic
        return alertRepository.findByFilters(workloadName, namespace);
    }

    private boolean passesSeverityFilter(Alert alert) {
        return alert.getSeverity().isAtLeast(minimumSeverity);
    }

    private boolean passesNamespaceFilter(Alert alert) {
        return !ignoredNamespaces.contains(alert.getWorkloadInfo().namespace());
    }

    @Scheduled(every = "{causa.alerts.cooldown-cleanup-interval}")
    void cleanupCooldownCache() {
        int before = cooldownCache.size();
        long cooldownSeconds = alertConfig.cooldownMinutes() * 60L;
        Instant cutoff = Instant.now().minusSeconds(cooldownSeconds);
        cooldownCache.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));

        log.debug(LogMessages.Alert.COOLDOWN_CACHE_CLEANUP)
            .field("removedEntries", before - cooldownCache.size())
            .field("remainingEntries", cooldownCache.size())
            .log();
    }
}
