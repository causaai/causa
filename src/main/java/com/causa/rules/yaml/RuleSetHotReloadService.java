package com.causa.rules.yaml;

import com.causa.common.logging.CausaLogger;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.Set;

/**
 * Rule Set Hot-Reload Service.
 *
 * <p>Monitors YAML rule set files and automatically reloads them when changed.
 * Runs periodically to check for file modifications.
 *
 * <p>Hot-reload can be configured via:
 * <ul>
 *   <li><code>causa.rules.hotreload.enabled</code> - Enable/disable hot-reload (default: true)</li>
 *   <li><code>causa.rules.hotreload.interval</code> - Check interval in seconds (default: 30)</li>
 * </ul>
 *
 * @since 1.0.0
 */
@ApplicationScoped
public class RuleSetHotReloadService {

    private static final CausaLogger log = CausaLogger.getLogger(RuleSetHotReloadService.class);

    private static final boolean HOT_RELOAD_ENABLED =
        Boolean.parseBoolean(System.getProperty("causa.rules.hotreload.enabled", "true"));

    @Inject
    YamlRuleSetLoader ruleSetLoader;

    @Inject
    YamlRuleSetRegistry registry;

    /**
     * Load all rule sets on application startup.
     */
    void onStart(@Observes StartupEvent event) {
        log.info("Initializing YAML-based rule sets")
            .field("hotReloadEnabled", HOT_RELOAD_ENABLED)
            .log();

        // Load all rule sets
        registry.loadAllRuleSets();

        log.info("YAML rule sets initialized")
            .field("loadedRuleSets", registry.getAllHypotheses().size())
            .log();
    }

    /**
     * Periodically check for modified rule sets and reload them.
     * Runs every 30 seconds by default (configurable).
     */
    @Scheduled(every = "${causa.rules.hotreload.interval:30s}", identity = "rule-hot-reload")
    void checkForModifications() {
        if (!HOT_RELOAD_ENABLED) {
            return;
        }

        try {
            Set<String> reloaded = ruleSetLoader.reloadModifiedRuleSets();

            if (!reloaded.isEmpty()) {
                // Re-register reloaded rule sets
                for (String hypothesis : reloaded) {
                    ruleSetLoader.getRuleSet(hypothesis).ifPresent(yamlDef -> {
                        YamlBasedRuleSet ruleSet = new YamlBasedRuleSet(yamlDef);
                        registry.registerRuleSet(hypothesis, ruleSet);
                    });
                }

                log.info("Hot-reloaded rule sets")
                    .field("count", reloaded.size())
                    .field("hypotheses", reloaded)
                    .log();
            }
        } catch (Exception e) {
            log.error("Failed to check for rule set modifications")
                .exception(e)
                .log();
        }
    }

    /**
     * Manually trigger a reload of all rule sets.
     * Useful for testing or admin endpoints.
     */
    public void forceReload() {
        log.info("Force reloading all YAML rule sets");
        registry.loadAllRuleSets();
    }
}
