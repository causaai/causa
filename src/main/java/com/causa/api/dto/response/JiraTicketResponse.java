package com.causa.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response for {@code POST /api/v1/diagnostics/{id}/ticket}: 201 for a new ticket, 200 for an existing one.
 *
 * @since 0.0.5
 */
public record JiraTicketResponse(

    @JsonProperty("ticket_url")
    String ticketUrl,

    @JsonProperty("already_existed")
    boolean alreadyExisted

) {
    public static JiraTicketResponse created(String ticketUrl) {
        return new JiraTicketResponse(ticketUrl, false);
    }

    public static JiraTicketResponse existing(String ticketUrl) {
        return new JiraTicketResponse(ticketUrl, true);
    }
}
