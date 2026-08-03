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
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
        this.ignoredNamespaces = Set.copyOf(appConfig.getAlertConfig().getIgnoreNamespaces());

        log.info("Alert service initialized")
            .field("minimumSeverity", minimumSeverity.getValue())
            .field("cooldownMinutes", appConfig.getAlertConfig().getCooldownMinutes())
            .field("ignoredNamespaces", ignoredNamespaces)
            .log();
    }

    @Override
    public List<Alert> processAlerts(List<Alert> alerts, Map<String, String> rejectedReasons) {
        List<Alert> accepted = new ArrayList<>();

        for (Alert alert : alerts) {
            if (!passesSeverityFilter(alert)) {
                log.debug(LogMessages.Alert.ALERT_FILTERED_SEVERITY)
                    .field("alertName", alert.getAlertName())
                    .field("severity", alert.getSeverity().getValue())
                    .field("minimum", minimumSeverity.getValue())
                    .log();
                String reason = String.format(
                    "Severity too low — received: %s, minimum required: %s",
                    alert.getSeverity().getValue(), minimumSeverity.getValue());
                Alert saved = alertRepository.saveRejected(alert, reason);
                rejectedReasons.put(saved.getAlertId(), reason);
                continue;
            }

            if (!passesNamespaceFilter(alert)) {
                log.debug(LogMessages.Alert.ALERT_FILTERED_NAMESPACE)
                    .field("alertName", alert.getAlertName())
                    .field("namespace", alert.getWorkloadInfo().namespace())
                    .log();
                String reason = String.format(
                    "Namespace '%s' is in the ignore list",
                    alert.getWorkloadInfo().namespace());
                Alert saved = alertRepository.saveRejected(alert, reason);
                rejectedReasons.put(saved.getAlertId(), reason);
                continue;
            }

            if (isInCooldown(alert)) {
                String key = alert.getCooldownKey();
                Instant nextProcessAt = cooldownCache.get(key)
                    .plusSeconds(appConfig.getAlertConfig().getCooldownMinutes() * 60L);
                String nextTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss 'UTC'")
                    .withZone(ZoneOffset.UTC)
                    .format(nextProcessAt);
                log.debug(LogMessages.Alert.ALERT_FILTERED_COOLDOWN)
                    .field("alertName", alert.getAlertName())
                    .field("podName", alert.getWorkloadInfo().podName())
                    .field("cooldownKey", key)
                    .log();
                String reason = String.format(
                    "Alert is in cooldown — next alert from this workload will be processed at: %s",
                    nextTime);
                Alert saved = alertRepository.saveRejected(alert, reason);
                rejectedReasons.put(saved.getAlertId(), reason);
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

        return accepted;
    }

    @Override
    public boolean isInCooldown(Alert alert) {
        String key = alert.getCooldownKey();
        Instant lastSeen = cooldownCache.get(key);
        if (lastSeen == null) return false;
        long cooldownSeconds = appConfig.getAlertConfig().getCooldownMinutes() * 60L;
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
        String namespace = alert.getWorkloadInfo().namespace();
        if (namespace == null) {
            return true;
        }
        return !ignoredNamespaces.contains(namespace);
    }

    @Scheduled(every = "{causa.alerts.cooldown-cleanup-interval}")
    void cleanupCooldownCache() {
        int before = cooldownCache.size();
        long cooldownSeconds = appConfig.getAlertConfig().getCooldownMinutes() * 60L;
        Instant cutoff = Instant.now().minusSeconds(cooldownSeconds);
        cooldownCache.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));

        log.debug(LogMessages.Alert.COOLDOWN_CACHE_CLEANUP)
            .field("removedEntries", before - cooldownCache.size())
            .field("remainingEntries", cooldownCache.size())
            .log();
    }
}
