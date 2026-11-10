package com.causa.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Response for {@code POST /api/v1/diagnostics/{id}/ticket}: 201 for a new ticket, 200 for an existing one.
 *
 * @since 0.0.5
 */
public record JiraTicketResponse(

    @JsonProperty("ticket_link")
    String ticketLink,

    @JsonProperty("already_existed")
    boolean alreadyExisted

) {
    public static JiraTicketResponse created(String ticketLink) {
        return new JiraTicketResponse(ticketLink, false);
    }

    public static JiraTicketResponse existing(String ticketLink) {
        return new JiraTicketResponse(ticketLink, true);
    }
}
