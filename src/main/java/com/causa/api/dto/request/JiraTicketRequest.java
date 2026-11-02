package com.causa.api.dto.request;

import com.causa.core.domain.JiraTicketOptions;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Optional body of {@code POST /api/v1/diagnostics/{id}/ticket}; every field may be omitted.
 *
 * @since 0.0.5
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record JiraTicketRequest(

    @JsonProperty("assignee_id")
    String assigneeId,

    @JsonProperty("include_recommendations")
    boolean includeRecommendations,

    @JsonProperty("include_implementation_steps")
    boolean includeImplementationSteps,

    @JsonProperty("include_issue_detail")
    boolean includeIssueDetail,

    @JsonProperty("additional_notes")
    String additionalNotes

) {
    public JiraTicketOptions toOptions() {
        return new JiraTicketOptions(assigneeId, includeRecommendations,
            includeImplementationSteps, includeIssueDetail, additionalNotes);
    }
}
