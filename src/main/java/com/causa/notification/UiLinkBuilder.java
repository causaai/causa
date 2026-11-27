package com.causa.notification;

import com.causa.common.constants.ConfigConstants;
import com.causa.common.logging.CausaLogger;
import com.causa.core.services.ConfigService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Builds links to pages of the Causa UI.
 *
 * <p>The public address of the UI is the config key {@code UI_BASE_URL}. The installer saves it through the
 * config API, and it can be seeded from {@code causa.ui.base-url} (env {@code CAUSA_UI_BASE_URL}) at startup.
 * The value is read on every call, so a change takes effect without a restart. When it is not set, or is not an
 * absolute http(s) URL with a host, no link is built.
 *
 * @since 0.0.4
 */
@ApplicationScoped
public class UiLinkBuilder {

    private static final CausaLogger log = CausaLogger.getLogger(UiLinkBuilder.class);

    private final ConfigService configService;

    @Inject
    public UiLinkBuilder(ConfigService configService) {
        this.configService = configService;
    }

    /**
     * Returns {@code {base}/active-issues/{diagnosticId}}, or empty when the base address or the id is blank, or
     * when the base address is not an absolute http(s) URL with a host (a warning is logged in that case).
     * The id is percent-encoded so it stays a single path segment.
     */
    public Optional<String> diagnosisLink(String diagnosticId) {
        if (diagnosticId == null || diagnosticId.isBlank()) {
            return Optional.empty();
        }
        String encodedId = encodePathSegment(diagnosticId);
        return configService.get(ConfigConstants.UI_BASE_URL_KEY)
                .map(String::strip)
                .map(s -> s.replaceAll("/+$", ""))
                .filter(s -> !s.isEmpty())
                .filter(this::isHttpUrlWithHost)
                .map(base -> base + "/active-issues/" + encodedId);
    }

    private boolean isHttpUrlWithHost(String base) {
        try {
            URI uri = new URI(base);
            String scheme = uri.getScheme();
            boolean valid = scheme != null
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null;
            if (!valid) {
                warnInvalidBase();
            }
            return valid;
        } catch (URISyntaxException e) {
            warnInvalidBase();
            return false;
        }
    }

    private void warnInvalidBase() {
        log.warn("UI_BASE_URL is not an absolute http(s) URL with a host, notification is sent without a link")
                .field(ConfigConstants.LogFields.CONFIG_KEY, ConfigConstants.UI_BASE_URL_KEY)
                .log();
    }

    /** URLEncoder targets form data and writes a space as '+', which is not valid in a path, so use %20. */
    private static String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
