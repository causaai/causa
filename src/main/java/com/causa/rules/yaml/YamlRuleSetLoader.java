package com.causa.rules.yaml;

import com.causa.common.logging.CausaLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * YAML Rule Set Loader.
 *
 * <p>Loads rule sets from YAML files with hot-reload capability.
 * Scans both classpath resources and external directory for YAML rule definitions.
 *
 * @since 1.0.0
 */
@ApplicationScoped
public class YamlRuleSetLoader {

    private static final CausaLogger log = CausaLogger.getLogger(YamlRuleSetLoader.class);

    private static final String CLASSPATH_RULES_DIR = "rulesets/";
    private static final String EXTERNAL_RULES_DIR = System.getProperty("causa.rules.dir", "config/rulesets/");

    private final ObjectMapper yamlMapper;
    private final Map<String, YamlRuleSetDefinition> loadedRuleSets;
    private final Map<String, Long> fileModificationTimes;

    public YamlRuleSetLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.loadedRuleSets = new ConcurrentHashMap<>();
        this.fileModificationTimes = new ConcurrentHashMap<>();
    }

    /**
     * Load all rule sets from classpath and external directory.
     *
     * @return map of hypothesis name to rule set definition
     */
    public Map<String, YamlRuleSetDefinition> loadAllRuleSets() {
        log.info("Loading YAML rule sets")
            .field("classpathDir", CLASSPATH_RULES_DIR)
            .field("externalDir", EXTERNAL_RULES_DIR)
            .log();

        // Load from classpath
        loadFromClasspath();

        // Load from external directory
        loadFromExternalDirectory();

        log.info("YAML rule sets loaded")
            .field("totalRuleSets", loadedRuleSets.size())
            .field("hypotheses", loadedRuleSets.keySet())
            .log();

        return new HashMap<>(loadedRuleSets);
    }

    /**
     * Reload rule sets (hot-reload).
     * Only reloads files that have been modified.
     *
     * @return map of reloaded hypothesis names
     */
    public Set<String> reloadModifiedRuleSets() {
        Set<String> reloaded = new HashSet<>();

        // Check external directory for modifications
        try {
            Path externalPath = Paths.get(EXTERNAL_RULES_DIR);
            if (Files.exists(externalPath) && Files.isDirectory(externalPath)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(externalPath, "*.{yml,yaml}")) {
                    for (Path file : stream) {
                        String fileName = file.getFileName().toString();
                        long currentModTime = Files.getLastModifiedTime(file).toMillis();
                        Long lastModTime = fileModificationTimes.get(fileName);

                        if (lastModTime == null || currentModTime > lastModTime) {
                            log.info("Reloading modified rule set")
                                .field("file", fileName)
                                .log();

                            YamlRuleSetDefinition ruleSet = loadYamlFile(Files.newInputStream(file), fileName);
                            if (ruleSet != null) {
                                loadedRuleSets.put(ruleSet.getHypothesis(), ruleSet);
                                fileModificationTimes.put(fileName, currentModTime);
                                reloaded.add(ruleSet.getHypothesis());
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to check for modified rule sets")
                .exception(e)
                .log();
        }

        if (!reloaded.isEmpty()) {
            log.info("Rule sets reloaded")
                .field("count", reloaded.size())
                .field("hypotheses", reloaded)
                .log();
        }

        return reloaded;
    }

    /**
     * Get a specific rule set by hypothesis name.
     */
    public Optional<YamlRuleSetDefinition> getRuleSet(String hypothesis) {
        return Optional.ofNullable(loadedRuleSets.get(hypothesis));
    }

    /**
     * Load rule sets from classpath resources.
     */
    private void loadFromClasspath() {
        try {
            // Get all YAML files from classpath rulesets directory
            ClassLoader classLoader = getClass().getClassLoader();

            // Try to load from known locations
            String[] knownFiles = {
                "rulesets/oom-killed.yml",
                "rulesets/high-memory-pressure.yml",
                "rulesets/cpu-throttling.yml"
            };

            for (String resourcePath : knownFiles) {
                try (InputStream is = classLoader.getResourceAsStream(resourcePath)) {
                    if (is != null) {
                        YamlRuleSetDefinition ruleSet = loadYamlFile(is, resourcePath);
                        if (ruleSet != null) {
                            loadedRuleSets.put(ruleSet.getHypothesis(), ruleSet);
                            log.debug("Loaded classpath rule set")
                                .field("file", resourcePath)
                                .field("hypothesis", ruleSet.getHypothesis())
                                .log();
                        }
                    }
                } catch (IOException e) {
                    log.debug("Resource not found (this is OK): " + resourcePath);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to load some classpath rule sets (continuing anyway)")
                .exception(e)
                .log();
        }
    }

    /**
     * Load rule sets from external directory.
     */
    private void loadFromExternalDirectory() {
        try {
            Path externalPath = Paths.get(EXTERNAL_RULES_DIR);

            if (!Files.exists(externalPath)) {
                log.debug("External rules directory does not exist: " + EXTERNAL_RULES_DIR);
                return;
            }

            if (!Files.isDirectory(externalPath)) {
                log.warn("External rules path is not a directory: " + EXTERNAL_RULES_DIR);
                return;
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(externalPath, "*.{yml,yaml}")) {
                for (Path file : stream) {
                    try (InputStream is = Files.newInputStream(file)) {
                        String fileName = file.getFileName().toString();
                        YamlRuleSetDefinition ruleSet = loadYamlFile(is, fileName);

                        if (ruleSet != null) {
                            loadedRuleSets.put(ruleSet.getHypothesis(), ruleSet);
                            fileModificationTimes.put(fileName, Files.getLastModifiedTime(file).toMillis());

                            log.debug("Loaded external rule set")
                                .field("file", fileName)
                                .field("hypothesis", ruleSet.getHypothesis())
                                .log();
                        }
                    } catch (Exception e) {
                        log.error("Failed to load rule set file")
                            .field("file", file.getFileName().toString())
                            .exception(e)
                            .log();
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to load external rule sets")
                .exception(e)
                .log();
        }
    }

    /**
     * Load and parse a YAML file into a rule set definition.
     */
    private YamlRuleSetDefinition loadYamlFile(InputStream inputStream, String fileName) {
        try {
            YamlRuleSetDefinition ruleSet = yamlMapper.readValue(inputStream, YamlRuleSetDefinition.class);

            if (ruleSet.getHypothesis() == null || ruleSet.getHypothesis().isBlank()) {
                log.error("Rule set missing hypothesis field")
                    .field("file", fileName)
                    .log();
                return null;
            }

            return ruleSet;
        } catch (IOException e) {
            log.error("Failed to parse YAML rule set")
                .field("file", fileName)
                .exception(e)
                .log();
            return null;
        }
    }
}
