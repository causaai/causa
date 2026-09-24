package com.causa.core.services.impl;

import com.causa.api.dto.request.ExternalConfigRequest;
import com.causa.api.validators.ConfigRequestValidator;
import com.causa.common.constants.ConfigConstants;
import com.causa.common.constants.ConfigConstants.PlatformCategory;
import com.causa.common.exceptions.ConfigException;
import com.causa.common.logging.CausaLogger;
import com.causa.common.utils.EncryptionUtils;
import com.causa.config.ExternalConfigCache;
import com.causa.core.domain.AuthConfig;
import com.causa.core.domain.ExternalConfig;
import com.causa.core.ports.ExternalConfigRepository;
import com.causa.core.services.ExternalConfigService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * External config service implementation covering both Observability and Integration platforms.
 *
 * <p>Write path: validate → encrypt sensitive auth fields → repository upsert.
 * Read path: repository fetch → decrypt sensitive auth fields → mask as {@code ********}.
 *
 * @since 0.0.4
 */
@ApplicationScoped
public class ExternalConfigServiceImpl implements ExternalConfigService {

    private static final CausaLogger log = CausaLogger.getLogger(ExternalConfigServiceImpl.class);

    private final ExternalConfigRepository repository;
    private final ExternalConfigCache cache;
    private final ConfigRequestValidator validator;

    @Inject
    public ExternalConfigServiceImpl(ExternalConfigRepository repository,
                                     ExternalConfigCache cache,
                                     ConfigRequestValidator validator) {
        this.repository = repository;
        this.cache      = cache;
        this.validator  = validator;
    }

    @Override
    public List<ExternalConfig> listByCategory(PlatformCategory category) {
        return cache.getByCategory(category).stream()
            .map(this::maskSensitiveFields)
            .toList();
    }

    @Override
    public ExternalConfig getByPlatformAndName(String platform, String name) {
        return repository.findByPlatformAndName(platform.toUpperCase(), name)
            .map(this::maskSensitiveFields)
            .orElseThrow(() -> new ConfigException(
                "No config found for platform '" + platform + "' with name '" + name + "'",
                "NOT_FOUND"));
    }

    @Override
    @Transactional
    public ExternalConfig upsert(PlatformCategory category, String platform, ExternalConfigRequest request) {
        String platformKey = platform.toUpperCase();
        validator.validateExternal(platformKey, request);

        AuthConfig encrypted = encryptSensitiveFields(platformKey, request.getAuthConfig());

        ExternalConfig toSave = ExternalConfig.builder()
            .id("NEW")                  // repository replaces this with a generated ID on insert
            .category(category)
            .platform(platformKey)
            .name(request.getName())
            .url(request.getUrl())
            .isActive(Boolean.TRUE.equals(request.getIsActive()))
            .authConfig(encrypted)
            .additionalConfig(request.getAdditionalConfig())
            .build();

        ExternalConfig saved = repository.save(toSave);

        // Refresh cache immediately so subsequent listByCategory() reads are consistent
        cache.refresh();

        log.info("External config upserted")
            .field("platform", platformKey)
            .field("name", request.getName())
            .field("category", category)
            .log();

        return maskSensitiveFields(saved);
    }

    @Override
    public void delete(String platform, String name) {
        boolean deleted = repository.deleteByPlatformAndName(platform.toUpperCase(), name);
        if (!deleted) {
            throw new ConfigException(
                "No config found for platform '" + platform + "' with name '" + name + "'",
                "NOT_FOUND");
        }

        // Refresh cache immediately so subsequent reads reflect the deletion
        cache.refresh();

        log.info("External config deleted")
            .field("platform", platform)
            .field("name", name)
            .log();
    }

    // -------------------------------------------------------------------------
    // Encrypt / decrypt / mask
    // -------------------------------------------------------------------------

    private AuthConfig encryptSensitiveFields(String platformKey, AuthConfig auth) {
        if (auth == null) return null;
        Set<String> sensitive = ConfigConstants.SENSITIVE_AUTH_FIELDS.getOrDefault(platformKey, Set.of());
        if (sensitive.isEmpty()) return auth;

        return new AuthConfig(
            auth.authType(),
            sensitive.contains("apiKey")          && auth.apiKey()         != null ? EncryptionUtils.encrypt(auth.apiKey())         : auth.apiKey(),
            sensitive.contains("appKey")          && auth.appKey()         != null ? EncryptionUtils.encrypt(auth.appKey())         : auth.appKey(),
            sensitive.contains("token")           && auth.token()          != null ? EncryptionUtils.encrypt(auth.token())          : auth.token(),
            sensitive.contains("credentialsJson") && auth.credentialsJson()!= null ? EncryptionUtils.encrypt(auth.credentialsJson()): auth.credentialsJson(),
            encryptHeaders(sensitive, auth.headers()),
            auth.username(),
            sensitive.contains("password")        && auth.password()       != null ? EncryptionUtils.encrypt(auth.password())       : auth.password()
        );
    }

    private Map<String, String> encryptHeaders(Set<String> sensitive, Map<String, String> headers) {
        if (!sensitive.contains("headers") || headers == null || headers.isEmpty()) return headers;
        return headers.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> EncryptionUtils.encrypt(e.getValue())));
    }

    private ExternalConfig maskSensitiveFields(ExternalConfig config) {
        if (config.getAuthConfig() == null) return config;
        Set<String> sensitive = ConfigConstants.SENSITIVE_AUTH_FIELDS
            .getOrDefault(config.getPlatform(), Set.of());
        if (sensitive.isEmpty()) return config;

        AuthConfig masked = maskAuth(sensitive, config.getAuthConfig());
        return ExternalConfig.builder()
            .id(config.getId())
            .category(config.getCategory())
            .platform(config.getPlatform())
            .name(config.getName())
            .url(config.getUrl())
            .isActive(config.isActive())
            .authConfig(masked)
            .additionalConfig(config.getAdditionalConfig())
            .createdAt(config.getCreatedAt())
            .updatedAt(config.getUpdatedAt())
            .build();
    }

    private AuthConfig maskAuth(Set<String> sensitive, AuthConfig auth) {
        if (auth == null) return null;
        String mask = ConfigConstants.MASKED_VALUE;
        return new AuthConfig(
            auth.authType(),
            sensitive.contains("apiKey")          && auth.apiKey()          != null ? mask : auth.apiKey(),
            sensitive.contains("appKey")          && auth.appKey()          != null ? mask : auth.appKey(),
            sensitive.contains("token")           && auth.token()           != null ? mask : auth.token(),
            sensitive.contains("credentialsJson") && auth.credentialsJson() != null ? mask : auth.credentialsJson(),
            sensitive.contains("headers")         && auth.headers()         != null ? maskHeaders(auth.headers()) : auth.headers(),
            auth.username(),
            sensitive.contains("password")        && auth.password()        != null ? mask : auth.password()
        );
    }

    private Map<String, String> maskHeaders(Map<String, String> headers) {
        return headers.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> ConfigConstants.MASKED_VALUE));
    }
}
