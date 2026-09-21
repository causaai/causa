package com.causa.core.services;

import com.causa.api.dto.request.ExternalConfigRequest;
import com.causa.common.constants.ConfigConstants.PlatformCategory;
import com.causa.core.domain.ExternalConfig;

import java.util.List;

/**
 * Primary port for external config management (Observability + Integrations settings tabs).
 *
 * <p>Covers both {@code OBSERVABILITY} (DATADOG, INSTANA) and {@code INTEGRATION}
 * (SLACK, JIRA, GITHUB) categories, discriminated by {@link PlatformCategory}.
 * Handles platform validation, auth-config field validation, AES-256-GCM
 * encryption on write, and decryption+masking on read.
 *
 * @since 0.0.4
 */
public interface ExternalConfigService {

    List<ExternalConfig> listByCategory(PlatformCategory category);

    ExternalConfig getByPlatformAndName(String platform, String name);

    ExternalConfig upsert(PlatformCategory category, String platform, ExternalConfigRequest request);

    void delete(String platform, String name);
}
