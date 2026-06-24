package com.causa.llm;

import com.causa.common.logging.CausaLogger;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.InputStream;

/**
 * Skill Loader
 *
 * <p>Loads SKILL.md files from the classpath and makes their content available
 * for injection into LLM system messages.
 *
 * <p>SKILL.md files follow the Agent Skills specification with YAML frontmatter:
 * <pre>
 * ---
 * name: skill-name
 * description: Brief description
 * ---
 * [Skill content here]
 * </pre>
 *
 * <p>This service extracts the skill body (content after frontmatter) and
 * provides it to {@link LangChainPromptSender} for inclusion in system messages.
 *
 * @since 0.0.1
 */
@ApplicationScoped
public class SkillLoader {

    private static final CausaLogger log = CausaLogger.getLogger(SkillLoader.class);

    private static final String KUBERNETES_SKILL_PATH = "/skills/kubernetes-diagnostics/SKILL.md";

    private String kubernetesSkillContent;

    /**
     * Loads skills from classpath on startup.
     */
    @PostConstruct
    void loadSkills() {
        kubernetesSkillContent = loadSkill(KUBERNETES_SKILL_PATH);

        if (!kubernetesSkillContent.isEmpty()) {
            log.info("Loaded kubernetes-diagnostics skill")
                .field("contentLength", kubernetesSkillContent.length())
                .log();
        }
    }

    /**
     * Get the Kubernetes diagnostics skill content.
     *
     * @return skill content (without YAML frontmatter), or empty string if not loaded
     */
    public String getKubernetesSkill() {
        return kubernetesSkillContent;
    }

    /**
     * Loads a skill file from classpath and extracts the body content.
     *
     * @param resourcePath classpath resource path (e.g., "/skills/my-skill/SKILL.md")
     * @return skill body content, or empty string if not found or error
     */
    private String loadSkill(String resourcePath) {
        try {
            InputStream is = getClass().getResourceAsStream(resourcePath);

            if (is == null) {
                log.warn("Skill file not found")
                    .field("path", resourcePath)
                    .log();
                return "";
            }

            String fullContent = new String(is.readAllBytes());
            String skillBody = extractSkillBody(fullContent);

            log.debug("Loaded skill from classpath")
                .field("path", resourcePath)
                .field("bodyLength", skillBody.length())
                .log();

            return skillBody;

        } catch (Exception e) {
            log.error("Failed to load skill")
                .field("path", resourcePath)
                .exception(e)
                .log();
            return "";
        }
    }

    /**
     * Extracts the skill body content by removing YAML frontmatter.
     *
     * <p>SKILL.md format:
     * <pre>
     * ---
     * name: skill-name
     * description: Brief description
     * ---
     * [Skill content here]
     * </pre>
     *
     * @param content full SKILL.md content with frontmatter
     * @return skill body (content after the second "---"), or original content if no frontmatter
     */
    private String extractSkillBody(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        // Split on "---" markers
        String[] parts = content.split("---", 3);

        // If we have at least 3 parts, the body is after the 2nd "---"
        if (parts.length >= 3) {
            return parts[2].trim();
        }

        // No frontmatter found, return original content
        log.debug("No YAML frontmatter found in skill file, using full content");
        return content.trim();
    }
}
