package com.causa.notification;

import com.causa.common.constants.ConfigConstants;
import com.causa.core.services.ConfigService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

/**
 * Builds links to pages of the Causa UI.
 *
 * <p>The public address of the UI is the config key {@code UI_BASE_URL}. The installer saves it through the
 * config API, and it can be seeded from {@code causa.ui.base-url} (env {@code CAUSA_UI_BASE_URL}) at startup.
 * The value is read on every call, so a change takes effect without a restart. When it is not set, no link is built.
 *
 * @since 0.0.4
 */
@ApplicationScoped
public class UiLinkBuilder {

    private final ConfigService configService;

    @Inject
    public UiLinkBuilder(ConfigService configService) {
        this.configService = configService;
    }

    /**
     * Returns {@code {base}/active-issues/{diagnosticId}}, or empty when the base address or the id is blank.
     */
    public Optional<String> diagnosisLink(String diagnosticId) {
        if (diagnosticId == null || diagnosticId.isBlank()) {
            return Optional.empty();
        }
        return configService.get(ConfigConstants.UI_BASE_URL_KEY)
                .map(String::strip)
                .map(s -> s.replaceAll("/+$", ""))
                .filter(s -> !s.isEmpty())
                .map(base -> base + "/active-issues/" + diagnosticId);
    }
}
