package com.causa.llm.skill;

import com.causa.common.logging.CausaLogger;
import com.causa.common.logging.LogMessages;
import com.causa.config.AppConfig;
import dev.langchain4j.skills.ClassPathSkillLoader;
import dev.langchain4j.skills.FileSystemSkillLoader;
import dev.langchain4j.skills.Skill;
import dev.langchain4j.skills.Skills;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Skills Configuration
 *
 * <p>Produces a merged Skills CDI bean by combining:
 * <ol>
 *   <li>Bundled classpath skills loaded from {@code /skills} via {@link ClassPathSkillLoader}.</li>
 *   <li>Optional filesystem skills loaded from the directory configured via
 *       {@code causa.llm.skills.skills-dir} (env: {@code LLM_SKILLS_DIR}).</li>
 * </ol>
 *
 * <p>External skills are overlaid on top of bundled skills; when a name collision occurs
 * the external skill wins. Startup is never failed by a missing or misconfigured directory.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class SkillsConfiguration {

    private static final CausaLogger log = CausaLogger.getLogger(SkillsConfiguration.class);
    private static final String SKILLS_CLASSPATH = "skills";

    private final AppConfig appConfig;

    @Inject
    public SkillsConfiguration(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    /**
     * Produces the merged Skills CDI bean.
     *
     * @return Skills combining bundled and external skill sources
     */
    @Produces
    @ApplicationScoped
    public Skills produceSkills() {

        // --- 0. Short-circuit when skills are globally disabled ---
        if (!appConfig.getLlmConfig().isSkillsEnabled()) {
            log.info(LogMessages.Skills.SKILLS_DISABLED).log();
            return Skills.from(List.of());
        }

        // --- 1. Load bundled classpath skills ---
        List<Skill> bundled = new ArrayList<>();
        try {
            bundled = ClassPathSkillLoader.loadSkills(SKILLS_CLASSPATH);
            log.info(LogMessages.Skills.CLASSPATH_SKILLS_LOADED)
                    .field("bundled_count", bundled.size())
                    .log();
        } catch (Exception e) {
            log.error(LogMessages.Skills.CLASSPATH_SKILLS_FAILED)
                    .exception(e)
                    .log();
        }

        // --- 2. Load optional filesystem skills ---
        List<Skill> external = new ArrayList<>();
        String skillsDir = appConfig.getLlmConfig().getSkillsDir();
        if (skillsDir == null || skillsDir.isBlank()) {
            log.info(LogMessages.Skills.SKILLS_DIR_NOT_SET).log();
        } else {
            Path dir = Path.of(skillsDir);
            if (!Files.isDirectory(dir)) {
                log.info(LogMessages.Skills.FS_SKILLS_DIR_MISSING)
                        .field("skills_dir", skillsDir)
                        .log();
            } else {
                try {
                    external = new ArrayList<>(FileSystemSkillLoader.loadSkills(dir));
                    log.info(LogMessages.Skills.FS_SKILLS_LOADED)
                            .field("skills_dir", skillsDir)
                            .field("external_count", external.size())
                            .log();
                } catch (Exception e) {
                    log.error(LogMessages.Skills.FS_SKILLS_FAILED)
                            .field("skills_dir", skillsDir)
                            .exception(e)
                            .log();
                }
            }
        }

        // --- 3. Merge: bundled first, external wins on name collision ---
        Map<String, Skill> merged = new LinkedHashMap<>();
        for (Skill s : bundled) {
            merged.put(s.name(), s);
        }
        for (Skill s : external) {
            merged.put(s.name(), s);
        }

        log.info(LogMessages.Skills.SKILLS_MERGED)
                .field("bundled_count", bundled.size())
                .field("external_count", external.size())
                .field("merged_count", merged.size())
                .log();

        return Skills.from(merged.values());
    }
}
