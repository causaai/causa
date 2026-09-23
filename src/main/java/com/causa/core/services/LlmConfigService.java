package com.causa.core.services;

import com.causa.api.dto.request.LlmConfigRequest;
import com.causa.core.domain.LlmConfig;

import java.util.List;
import java.util.Optional;

/**
 * Primary port for LLM provider config management (LLM settings tab).
 *
 * <p>At most one provider may be active at a time. When {@code isActive} is {@code true}
 * on an upsert, all other rows are deactivated within the same transaction.
 * Handles provider validation, auth-config field validation, AES-256-GCM encryption on write,
 * and decryption+masking on read.
 *
 * @since 0.0.4
 */
public interface LlmConfigService {

    List<LlmConfig> listAll();

    LlmConfig getByProvider(String provider);

    Optional<LlmConfig> getActive();

    /**
     * Upserts the config for the given provider string.
     * Provider resolution from string to {@link com.causa.common.constants.ConfigConstants.LlmProvider}
     * is handled internally, throwing {@link com.causa.common.exceptions.ConfigException} on an unknown value.
     */
    LlmConfig upsert(String provider, LlmConfigRequest request);

    /**
     * Deletes the config for the given provider.
     *
     * @throws com.causa.common.exceptions.ConfigException if the provider is currently active
     */
    void delete(String provider);
}
