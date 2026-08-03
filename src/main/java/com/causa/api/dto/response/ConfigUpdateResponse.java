package com.causa.api.dto.response;

import java.util.List;

/**
 * Config Update Response DTO
 *
 * <p>Response format for {@code POST /api/v1/configs}.
 *
 * @param updated  list of successfully updated config entries (full detail, sensitive values masked)
 * @param rejected list of rejected config entries with the reason for rejection
 * @since 0.0.1
 */
public record ConfigUpdateResponse(List<ConfigResponse> updated, List<ConfigUpdateResponse.RejectedConfig> rejected) {

    /**
     * Represents a single rejected config entry.
     *
     * @param key    the configuration key that was rejected
     * @param reason human-readable explanation of why the key was rejected
     */
    public record RejectedConfig(String key, String reason) {}
}
