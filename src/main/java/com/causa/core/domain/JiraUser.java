package com.causa.core.domain;

/**
 * A Jira user that can be assigned a ticket; {@code id} is the accountId on Cloud and the username on Server.
 *
 * @since 0.0.5
 */
public record JiraUser(String id, String displayName, String email) {}
