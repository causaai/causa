package com.causa.api.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SKILL Test Request DTO
 *
 * <p>Request payload for testing SKILL.md integration with custom prompt and context.
 *
 * @since 0.0.1
 */
public class SkillTestRequest {

    @JsonProperty("prompt")
    private String prompt;

    @JsonProperty("context")
    private String context;

    @JsonProperty("systemPrompt")
    private String systemPrompt;

    @JsonProperty("enableSkills")
    private Boolean enableSkills;

    public SkillTestRequest() {
        // Default constructor for Jackson
    }

    public SkillTestRequest(String prompt, String context, String systemPrompt) {
        this.prompt = prompt;
        this.context = context;
        this.systemPrompt = systemPrompt;
    }

    public SkillTestRequest(String prompt, String context, String systemPrompt, Boolean enableSkills) {
        this.prompt = prompt;
        this.context = context;
        this.systemPrompt = systemPrompt;
        this.enableSkills = enableSkills;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public Boolean getEnableSkills() {
        return enableSkills;
    }

    public void setEnableSkills(Boolean enableSkills) {
        this.enableSkills = enableSkills;
    }
}
