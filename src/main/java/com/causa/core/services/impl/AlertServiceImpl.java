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
import java.util.List;
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
    public List<Alert> processAlerts(List<Alert> alerts) {
        List<Alert> accepted = new ArrayList<>();

        for (Alert alert : alerts) {
            if (!passesSeverityFilter(alert)) {
                log.debug(LogMessages.Alert.ALERT_FILTERED_SEVERITY)
                    .field("alertName", alert.getAlertName())
                    .field("severity", alert.getSeverity().getValue())
                    .field("minimum", minimumSeverity.getValue())
                    .log();
                continue;
            }

            if (!passesNamespaceFilter(alert)) {
                log.debug(LogMessages.Alert.ALERT_FILTERED_NAMESPACE)
                    .field("alertName", alert.getAlertName())
                    .field("namespace", alert.getNamespace())
                    .log();
                continue;
            }

            if (isInCooldown(alert)) {
                log.debug(LogMessages.Alert.ALERT_FILTERED_COOLDOWN)
                    .field("alertName", alert.getAlertName())
                    .field("podName", alert.getPodName())
                    .field("cooldownKey", alert.getCooldownKey())
                    .log();
                continue;
            }

            // Record cooldown timestamp
            cooldownCache.put(alert.getCooldownKey(), Instant.now());

            // Persist alert to database
            Alert savedAlert = alertRepository.save(alert);

            accepted.add(savedAlert);

            log.info(LogMessages.Alert.ALERT_ACCEPTED)
                .field("alertId", alert.getAlertId())
                .field("alertName", alert.getAlertName())
                .field("severity", alert.getSeverity().getValue())
                .field("namespace", alert.getNamespace())
                .field("podName", alert.getPodName())
                .log();

            log.debug(LogMessages.Alert.ALERT_PERSISTED)
                .field("alertId", savedAlert.getAlertId())
                .log();
        }

        return accepted;
    }

    @Override
    public boolean isInCooldown(Alert alert) {
        String key = alert.getCooldownKey();
        Instant lastSeen = cooldownCache.get(key);

        if (lastSeen == null) {
            return false;
        }

        long cooldownMinutes = alertConfig.cooldownMinutes();
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
     * <p>Interval is configurable via causa.alert.cooldown-cleanup-interval (default: 5m).
     * <p>Prevents unbounded memory growth by removing expired entries.
     */
    @Scheduled(every = "{causa.alert.cooldown-cleanup-interval}")
    void cleanupCooldownCache() {
        int beforeSize = cooldownCache.size();
        long cooldownSeconds = alertConfig.cooldownMinutes() * 60L;
        Instant cutoff = Instant.now().minusSeconds(cooldownSeconds);

        cooldownCache.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));

        int removedEntries = beforeSize - cooldownCache.size();

        log.debug(LogMessages.Alert.COOLDOWN_CACHE_CLEANUP)
            .field("removedEntries", removedEntries)
            .field("remainingEntries", cooldownCache.size())
            .log();
    }
}
