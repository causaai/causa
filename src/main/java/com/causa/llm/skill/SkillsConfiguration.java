package com.causa.llm.skill;

import com.causa.common.logging.CausaLogger;
import dev.langchain4j.skills.ClassPathSkillLoader;
import dev.langchain4j.skills.Skill;
import dev.langchain4j.skills.Skills;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;

import java.util.List;

/**
 * Skills Configuration
 *
 * <p>Produces Skills CDI bean using ClassPathSkillLoader.
 * Loads skills from /skills directory in classpath.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class SkillsConfiguration {

    private static final CausaLogger log = CausaLogger.getLogger(SkillsConfiguration.class);
    private static final String SKILLS_CLASSPATH = "skills";

    /**
     * Produces the Skills CDI bean.
     *
     * @return Skills with activate_skill tool capabilities
     */
    @Produces
    @ApplicationScoped
    public Skills produceSkills() {
        log.info("Loading skills from classpath").log();

        try {
            // Load skills from classpath using ClassPathSkillLoader
            List<Skill> skillList = ClassPathSkillLoader.loadSkills(SKILLS_CLASSPATH);

            log.info("Skills loaded successfully")
                    .field("count", skillList.size())
                    .log();

            return Skills.from(skillList);

        } catch (Exception e) {
            log.error("Failed to load skills from classpath")
                    .exception(e)
                    .log();
            // Return empty skills
            return Skills.from(List.of());
        }
    }
}
