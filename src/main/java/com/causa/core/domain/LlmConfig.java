package com.causa.core.domain;

import com.causa.common.constants.ConfigConstants.LlmAuthType;
import com.causa.common.constants.ConfigConstants.LlmProvider;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * LLM Config Domain Model — maps to the {@code llm_configs} table.
 *
 * <p>One instance per LLM provider. At most one may have {@code isActive = true} —
 * enforced by the service layer.
 *
 * @since 0.0.3
 */
public final class LlmConfig {

    private final String id;
    private final String name;
    private final LlmProvider provider;
    private final String model;
    private final LlmAuthType authType;
    private final BigDecimal temperature;
    private final Integer maxTokens;
    private final Integer timeoutMs;
    private final boolean isActive;
    /** Sensitive fields encrypted on write, decrypted+masked on read by the service layer. */
    private final AuthConfig authConfig;
    /** Free-form map for future provider-specific fields. */
    private final Map<String, Object> additionalConfig;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    private LlmConfig(Builder builder) {
        this.id               = Objects.requireNonNull(builder.id, "id cannot be null");
        this.name             = Objects.requireNonNull(builder.name, "name cannot be null");
        this.provider         = Objects.requireNonNull(builder.provider, "provider cannot be null");
        this.model            = Objects.requireNonNull(builder.model, "model cannot be null");
        this.authType         = Objects.requireNonNull(builder.authType, "authType cannot be null");
        this.temperature      = builder.temperature;
        this.maxTokens        = builder.maxTokens;
        this.timeoutMs        = builder.timeoutMs;
        this.isActive         = builder.isActive;
        this.authConfig       = Objects.requireNonNull(builder.authConfig, "authConfig cannot be null");
        this.additionalConfig = builder.additionalConfig != null ? builder.additionalConfig : Map.of();
        this.createdAt        = builder.createdAt;
        this.updatedAt        = builder.updatedAt;
    }

    public String getId()                            { return id; }
    public String getName()                          { return name; }
    public LlmProvider getProvider()                 { return provider; }
    public String getModel()                         { return model; }
    public LlmAuthType getAuthType()                 { return authType; }
    public BigDecimal getTemperature()               { return temperature; }
    public Integer getMaxTokens()                    { return maxTokens; }
    public Integer getTimeoutMs()                    { return timeoutMs; }
    public boolean isActive()                        { return isActive; }
    public AuthConfig getAuthConfig()                { return authConfig; }
    public Map<String, Object> getAdditionalConfig() { return additionalConfig; }
    public OffsetDateTime getCreatedAt()             { return createdAt; }
    public OffsetDateTime getUpdatedAt()             { return updatedAt; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private String name;
        private LlmProvider provider;
        private String model;
        private LlmAuthType authType;
        private BigDecimal temperature;
        private Integer maxTokens;
        private Integer timeoutMs;
        private boolean isActive = false;
        private AuthConfig authConfig;
        private Map<String, Object> additionalConfig;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;

        private Builder() {}

        public Builder id(String v)                            { this.id = v; return this; }
        public Builder name(String v)                          { this.name = v; return this; }
        public Builder provider(LlmProvider v)                 { this.provider = v; return this; }
        public Builder model(String v)                         { this.model = v; return this; }
        public Builder authType(LlmAuthType v)                 { this.authType = v; return this; }
        public Builder temperature(BigDecimal v)               { this.temperature = v; return this; }
        public Builder maxTokens(Integer v)                    { this.maxTokens = v; return this; }
        public Builder timeoutMs(Integer v)                    { this.timeoutMs = v; return this; }
        public Builder isActive(boolean v)                     { this.isActive = v; return this; }
        public Builder authConfig(AuthConfig v)                { this.authConfig = v; return this; }
        public Builder additionalConfig(Map<String, Object> v) { this.additionalConfig = v; return this; }
        public Builder createdAt(OffsetDateTime v)             { this.createdAt = v; return this; }
        public Builder updatedAt(OffsetDateTime v)             { this.updatedAt = v; return this; }

        public LlmConfig build() { return new LlmConfig(this); }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(id, ((LlmConfig) o).id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "LlmConfig{id='" + id + "', provider=" + provider
            + ", model='" + model + "', authType=" + authType
            + ", isActive=" + isActive + "}";
    }
}
