package com.causa.core.domain;

/**
 * What the user picked on the "Create Jira ticket" form. With nothing selected the description
 * is just the root cause; the selected sections are appended to it.
 *
 * @param assigneeId                Jira accountId (Cloud) or username (Server); null = unassigned
 * @param includeRecommendations    append the recommendations
 * @param includeImplementationSteps append each recommendation's implementation steps
 * @param includeIssueDetail        append the issue summary and description
 * @param additionalNotes           free text appended as-is
 * @since 0.0.5
 */
public record JiraTicketOptions(
    String assigneeId,
    boolean includeRecommendations,
    boolean includeImplementationSteps,
    boolean includeIssueDetail,
    String additionalNotes
) {
    public static final int MAX_NOTES_LENGTH = 2000;
    public static final int MAX_ASSIGNEE_LENGTH = 255;

    public static JiraTicketOptions defaults() {
        return new JiraTicketOptions(null, false, false, false, null);
    }
}
