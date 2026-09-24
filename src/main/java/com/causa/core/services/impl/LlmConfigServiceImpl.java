package com.causa.core.services.impl;

import com.causa.api.dto.request.LlmConfigRequest;
import com.causa.api.validators.ConfigRequestValidator;
import com.causa.common.constants.ConfigConstants;
import com.causa.common.constants.ConfigConstants.LlmProvider;
import com.causa.common.exceptions.ConfigException;
import com.causa.common.logging.CausaLogger;
import com.causa.common.utils.EncryptionUtils;
import com.causa.core.domain.AuthConfig;
import com.causa.core.domain.LlmConfig;
import com.causa.core.ports.LlmConfigRepository;
import com.causa.core.services.LlmConfigService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * LLM config service implementation.
 *
 * <p>Write path: validate → encrypt sensitive auth fields → repository upsert.
 * If {@code isActive=true}, all other providers are deactivated in the same transaction.
 * Delete is blocked if the provider is currently active.
 * Read path: repository fetch → mask sensitive auth fields as {@code ********}.
 *
 * @since 0.0.4
 */
@ApplicationScoped
public class LlmConfigServiceImpl implements LlmConfigService {

    private static final CausaLogger log = CausaLogger.getLogger(LlmConfigServiceImpl.class);

    private final LlmConfigRepository repository;
    private final ConfigRequestValidator validator;

    @Inject
    public LlmConfigServiceImpl(LlmConfigRepository repository,
                                ConfigRequestValidator validator) {
        this.repository = repository;
        this.validator  = validator;
    }

    @Override
    public List<LlmConfig> listAll() {
        return repository.findAll().stream()
            .map(this::maskSensitiveFields)
            .toList();
    }

    @Override
    public LlmConfig getByProvider(String provider) {
        return repository.findByProvider(provider.toUpperCase())
            .map(this::maskSensitiveFields)
            .orElseThrow(() -> new ConfigException(
                "No LLM config found for provider: " + provider, "NOT_FOUND"));
    }

    @Override
    public Optional<LlmConfig> getActive() {
        return repository.findActive().map(this::maskSensitiveFields);
    }

    @Override
    @Transactional
    public LlmConfig upsert(String providerStr, LlmConfigRequest request) {
        LlmProvider provider;
        try {
            provider = LlmProvider.valueOf(providerStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ConfigException("Unknown LLM provider: " + providerStr, "UNKNOWN_PROVIDER");
        }

        String authType = request.getAuthConfig() != null && request.getAuthConfig().authType() != null
            ? request.getAuthConfig().authType().toUpperCase()
            : null;
        if (authType == null) {
            throw new ConfigException("auth_config.authType is required for LLM config", "VALIDATION_ERROR");
        }

        validator.validateLlm(authType, request);

        AuthConfig encrypted = encryptSensitiveFields(authType, request.getAuthConfig());

        if (Boolean.TRUE.equals(request.getIsActive())) {
            repository.deactivateAll();
        }

        LlmConfig toSave = LlmConfig.builder()
            .id("NEW")                  // repository replaces this with a generated ID on insert
            .provider(provider)
            .url(request.getUrl())
            .models(request.getModels())
            .temperature(request.getTemperature())
            .maxTokens(request.getMaxTokens())
            .timeoutMs(request.getTimeoutMs())
            .isActive(Boolean.TRUE.equals(request.getIsActive()))
            .authConfig(encrypted)
            .additionalConfig(request.getAdditionalConfig())
            .build();

        LlmConfig saved = repository.save(toSave);

        log.info("LLM config upserted")
            .field("provider", provider)
            .field("authType", authType)
            .field("isActive", saved.isActive())
            .log();

        return maskSensitiveFields(saved);
    }

    @Override
    public void delete(String provider) {
        String providerKey = provider.toUpperCase();
        LlmConfig existing = repository.findByProvider(providerKey)
            .orElseThrow(() -> new ConfigException(
                "No LLM config found for provider: " + provider, "NOT_FOUND"));

        if (existing.isActive()) {
            throw new ConfigException(
                "Cannot delete the currently active LLM provider: " + provider
                + ". Activate another provider first.", "CONFLICT");
        }

        repository.deleteByProvider(providerKey);

        log.info("LLM config deleted")
            .field("provider", provider)
            .log();
    }

    // -------------------------------------------------------------------------
    // Encrypt / mask
    // -------------------------------------------------------------------------

    private AuthConfig encryptSensitiveFields(String authType, AuthConfig auth) {
        if (auth == null) return null;
        Set<String> sensitive = ConfigConstants.SENSITIVE_AUTH_FIELDS.getOrDefault(authType, Set.of());
        if (sensitive.isEmpty()) return auth;

        return new AuthConfig(
            auth.authType(),
            sensitive.contains("apiKey")          && auth.apiKey()          != null ? EncryptionUtils.encrypt(auth.apiKey())          : auth.apiKey(),
            sensitive.contains("appKey")          && auth.appKey()          != null ? EncryptionUtils.encrypt(auth.appKey())          : auth.appKey(),
            sensitive.contains("token")           && auth.token()           != null ? EncryptionUtils.encrypt(auth.token())           : auth.token(),
            sensitive.contains("credentialsJson") && auth.credentialsJson() != null ? EncryptionUtils.encrypt(auth.credentialsJson()) : auth.credentialsJson(),
            encryptHeaders(sensitive, auth.headers()),
            auth.username(),
            sensitive.contains("password")        && auth.password()        != null ? EncryptionUtils.encrypt(auth.password())        : auth.password()
        );
    }

    private Map<String, String> encryptHeaders(Set<String> sensitive, Map<String, String> headers) {
        if (!sensitive.contains("headers") || headers == null || headers.isEmpty()) return headers;
        return headers.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, e -> EncryptionUtils.encrypt(e.getValue())));
    }

    private LlmConfig maskSensitiveFields(LlmConfig config) {
        String authType = config.getAuthConfig() != null ? config.getAuthConfig().authType() : null;
        Set<String> sensitive = authType != null
            ? ConfigConstants.SENSITIVE_AUTH_FIELDS.getOrDefault(authType.toUpperCase(), Set.of())
            : Set.of();
        if (sensitive.isEmpty()) return config;

        AuthConfig masked = maskAuth(sensitive, config.getAuthConfig());
        return LlmConfig.builder()
            .id(config.getId())
            .provider(config.getProvider())
            .url(config.getUrl())
            .models(config.getModels())
            .temperature(config.getTemperature())
            .maxTokens(config.getMaxTokens())
            .timeoutMs(config.getTimeoutMs())
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
