package com.causa.core.services.impl;

import com.causa.common.constants.AlertConstants.AlertSeverity;
import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.config.AppConfig;
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
 * <p>Implements alert processing with severity filtering, namespace filtering, and cooldown deduplication.
 * <p>Uses an in-memory cooldown cache with scheduled cleanup.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class AlertServiceImpl implements AlertService {

    private static final CausaLogger log = CausaLogger.getLogger(AlertServiceImpl.class);

    private final AppConfig appConfig;
    private final AlertRepository alertRepository;
    private final ConcurrentHashMap<String, Instant> cooldownCache = new ConcurrentHashMap<>();

    private AlertSeverity minimumSeverity;
    private Set<String> ignoredNamespaces;

    @Inject
    public AlertServiceImpl(AppConfig appConfig, AlertRepository alertRepository) {
        this.appConfig = appConfig;
        this.alertRepository = alertRepository;
    }

    @PostConstruct
    void init() {
        this.minimumSeverity = AlertSeverity.fromString(appConfig.getAlertConfig().getFilterSeverity());
        this.ignoredNamespaces = appConfig.getAlertConfig().getIgnoreNamespaces()
            .map(Set::copyOf)
            .orElse(Set.of());

        log.info("Alert service initialized")
            .field("minimumSeverity", minimumSeverity.getValue())
            .field("cooldownMinutes", appConfig.getAlertConfig().getCooldownMinutes())
            .field("ignoredNamespaces", ignoredNamespaces)
            .log();
    }

    @Override
    public AlertService.ProcessedAlerts processAlerts(List<Alert> alerts) {
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
                    .field("namespace", alert.getNamespace())
                    .log();
                rejected.put(alertRepository.saveRejected(alert, "namespace"), "namespace");
                continue;
            }

            if (isInCooldown(alert)) {
                log.debug(LogMessages.Alert.ALERT_FILTERED_COOLDOWN)
                    .field("alertName", alert.getAlertName())
                    .field("podName", alert.getPodName())
                    .field("cooldownKey", alert.getCooldownKey())
                    .log();
                rejected.put(alertRepository.saveRejected(alert, "cooldown"), "cooldown");
                continue;
            }

            // Record cooldown timestamp and persist with ACCEPTED status
            cooldownCache.put(alert.getCooldownKey(), Instant.now());
            Alert savedAlert = alertRepository.save(alert);
            accepted.add(savedAlert);

            log.info(LogMessages.Alert.ALERT_ACCEPTED)
                .field("alertId", savedAlert.getAlertId())
                .field("alertName", savedAlert.getAlertName())
                .field("severity", savedAlert.getSeverity().getValue())
                .field("namespace", savedAlert.getNamespace())
                .field("podName", savedAlert.getPodName())
                .log();

            log.debug(LogMessages.Alert.ALERT_PERSISTED)
                .field("alertId", savedAlert.getAlertId())
                .log();
        }

        return new AlertService.ProcessedAlerts(accepted, rejected);
    }

    @Override
    public boolean isInCooldown(Alert alert) {
        String key = alert.getCooldownKey();
        Instant lastSeen = cooldownCache.get(key);

        if (lastSeen == null) {
            return false;
        }

        long cooldownMinutes = appConfig.getAlertConfig().getCooldownMinutes();
        Instant cooldownExpiry = lastSeen.plusSeconds(cooldownMinutes * 60L);

        return Instant.now().isBefore(cooldownExpiry);
    }

    private boolean passesSeverityFilter(Alert alert) {
        return alert.getSeverity().isAtLeast(minimumSeverity);
    }

    private boolean passesNamespaceFilter(Alert alert) {
        return !ignoredNamespaces.contains(alert.getNamespace());
    }

    /**
     * Scheduled cleanup of expired cooldown entries.
     *
     * <p>Interval is configurable via causa.alerts.cooldown-cleanup-interval (default: 5m).
     * <p>Prevents unbounded memory growth by removing expired entries.
     */
    @Scheduled(every = "{causa.alerts.cooldown-cleanup-interval}")
    void cleanupCooldownCache() {
        int beforeSize = cooldownCache.size();
        long cooldownSeconds = appConfig.getAlertConfig().getCooldownMinutes() * 60L;
        Instant cutoff = Instant.now().minusSeconds(cooldownSeconds);

        cooldownCache.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));

        int removedEntries = beforeSize - cooldownCache.size();

        log.debug(LogMessages.Alert.COOLDOWN_CACHE_CLEANUP)
            .field("removedEntries", removedEntries)
            .field("remainingEntries", cooldownCache.size())
            .log();
    }

    @Override
    public Optional<Alert> getAlert(String alertId) {
        return alertRepository.findById(alertId);
    }

    @Override
    public List<Alert> getAlerts(String containerName) {
        if (containerName != null && !containerName.isBlank()) {
            return alertRepository.findByContainerName(containerName);
        }
        return alertRepository.findAll();
    }
}
