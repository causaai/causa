package com.causa.core.domain;

import com.causa.common.constants.ConfigConstants.PlatformCategory;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Objects;

/**
 * External Config Domain Model — maps to the {@code external_configs} table.
 *
 * <p>Covers Observability (DATADOG / INSTANA) and Integration
 * (SLACK / JIRA / GITHUB) configs discriminated by {@code category}.
 *
 * @since 0.0.3
 */
public final class ExternalConfig {

    private final String id;
    private final PlatformCategory category;
    /** Plain String — holds values from {@code ObservabilityPlatform} (DATADOG / INSTANA) or {@code IntegrationPlatform} (SLACK / JIRA / GITHUB) depending on category. */
    private final String platform;
    private final String name;
    private final String url;
    private final boolean isActive;
    /** Sensitive fields encrypted on write, decrypted+masked on read by the service layer. */
    private final AuthConfig authConfig;
    /** Free-form map for platform-specific behavioural fields (channel, projectName, etc.). */
    private final Map<String, Object> additionalConfig;
    private final OffsetDateTime createdAt;
    private final OffsetDateTime updatedAt;

    private ExternalConfig(Builder builder) {
        this.id               = Objects.requireNonNull(builder.id, "id cannot be null");
        this.category         = Objects.requireNonNull(builder.category, "category cannot be null");
        this.platform         = Objects.requireNonNull(builder.platform, "platform cannot be null");
        this.name             = Objects.requireNonNull(builder.name, "name cannot be null");
        this.url              = builder.url;
        this.isActive         = builder.isActive;
        this.authConfig       = Objects.requireNonNull(builder.authConfig, "authConfig cannot be null");
        this.additionalConfig = builder.additionalConfig != null ? builder.additionalConfig : Map.of();
        this.createdAt        = builder.createdAt;
        this.updatedAt        = builder.updatedAt;
    }

    public String getId()                            { return id; }
    public PlatformCategory getCategory()            { return category; }
    public String getPlatform()                      { return platform; }
    public String getName()                          { return name; }
    public String getUrl()                           { return url; }
    public boolean isActive()                        { return isActive; }
    public AuthConfig getAuthConfig()                { return authConfig; }
    public Map<String, Object> getAdditionalConfig() { return additionalConfig; }
    public OffsetDateTime getCreatedAt()             { return createdAt; }
    public OffsetDateTime getUpdatedAt()             { return updatedAt; }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String id;
        private PlatformCategory category;
        private String platform;
        private String name;
        private String url;
        private boolean isActive = true;
        private AuthConfig authConfig;
        private Map<String, Object> additionalConfig;
        private OffsetDateTime createdAt;
        private OffsetDateTime updatedAt;

        private Builder() {}

        public Builder id(String v)                            { this.id = v; return this; }
        public Builder category(PlatformCategory v)            { this.category = v; return this; }
        public Builder platform(String v)                      { this.platform = v; return this; }
        public Builder name(String v)                          { this.name = v; return this; }
        public Builder url(String v)                           { this.url = v; return this; }
        public Builder isActive(boolean v)                     { this.isActive = v; return this; }
        public Builder authConfig(AuthConfig v)                { this.authConfig = v; return this; }
        public Builder additionalConfig(Map<String, Object> v) { this.additionalConfig = v; return this; }
        public Builder createdAt(OffsetDateTime v)             { this.createdAt = v; return this; }
        public Builder updatedAt(OffsetDateTime v)             { this.updatedAt = v; return this; }

        public ExternalConfig build() { return new ExternalConfig(this); }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(id, ((ExternalConfig) o).id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() {
        return "ExternalConfig{id='" + id + "', category=" + category
            + ", platform='" + platform + "', name='" + name
            + "', isActive=" + isActive + "}";
    }
}
