package com.causa.api.dto.response;

import com.causa.core.domain.JiraUser;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * One entry of {@code GET /api/v1/diagnostics/jira/users}.
 *
 * @since 0.0.5
 */
public record JiraUserResponse(

    String id,

    @JsonProperty("display_name")
    String displayName,

    String email

) {
    public static JiraUserResponse from(JiraUser user) {
        return new JiraUserResponse(user.id(), user.displayName(), user.email());
    }
}
