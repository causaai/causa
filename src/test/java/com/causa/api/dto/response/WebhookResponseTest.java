package com.causa.api.dto.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link WebhookResponse}.
 *
 * @since 0.0.1
 */
@DisplayName("WebhookResponse Tests")
class WebhookResponseTest {

    @Nested
    @DisplayName("of() Factory Method Tests")
    class OfFactoryTests {

        @Test
        @DisplayName("Should return 'empty' status when both accepted and rejected are empty")
        void shouldReturnEmptyStatusWhenBothEmpty() {
            WebhookResponse response = WebhookResponse.of(Map.of(), Map.of());

            assertEquals("empty", response.status());
            assertEquals("No alerts in payload", response.message());
            assertEquals(0, response.totalReceived());
            assertEquals(0, response.totalAccepted());
            assertEquals(0, response.totalRejected());
        }

        @Test
        @DisplayName("Should return 'accepted' status when all alerts accepted")
        void shouldReturnAcceptedStatusWhenAllAccepted() {
            Map<String, String> accepted = Map.of("alert-1", "diag-1", "alert-2", "diag-2");
            WebhookResponse response = WebhookResponse.of(accepted, Map.of());

            assertEquals("accepted", response.status());
            assertEquals(2, response.totalAccepted());
            assertEquals(0, response.totalRejected());
            assertEquals(2, response.totalReceived());
            assertTrue(response.message().contains("2"));
        }

        @Test
        @DisplayName("Should return 'rejected' status when all alerts rejected")
        void shouldReturnRejectedStatusWhenAllRejected() {
            Map<String, String> rejected = Map.of("alert-1", "Severity too low");
            WebhookResponse response = WebhookResponse.of(Map.of(), rejected);

            assertEquals("rejected", response.status());
            assertEquals(0, response.totalAccepted());
            assertEquals(1, response.totalRejected());
            assertEquals(1, response.totalReceived());
        }

        @Test
        @DisplayName("Should return 'partial' status when some accepted and some rejected")
        void shouldReturnPartialStatusWhenMixed() {
            Map<String, String> accepted = Map.of("alert-1", "diag-1");
            Map<String, String> rejected = Map.of("alert-2", "Severity too low");
            WebhookResponse response = WebhookResponse.of(accepted, rejected);

            assertEquals("partial", response.status());
            assertEquals(1, response.totalAccepted());
            assertEquals(1, response.totalRejected());
            assertEquals(2, response.totalReceived());
            assertTrue(response.message().contains("1 accepted"));
            assertTrue(response.message().contains("1 rejected"));
        }

        @Test
        @DisplayName("Should include accepted map in response")
        void shouldIncludeAcceptedMapInResponse() {
            Map<String, String> accepted = Map.of("alert-1", "diag-1");
            WebhookResponse response = WebhookResponse.of(accepted, Map.of());

            assertNotNull(response.accepted());
            assertEquals("diag-1", response.accepted().get("alert-1"));
        }

        @Test
        @DisplayName("Should include rejected map in response")
        void shouldIncludeRejectedMapInResponse() {
            Map<String, String> rejected = Map.of("alert-1", "Severity too low");
            WebhookResponse response = WebhookResponse.of(Map.of(), rejected);

            assertNotNull(response.rejected());
            assertEquals("Severity too low", response.rejected().get("alert-1"));
        }

        @Test
        @DisplayName("Should set timestamp to approximately now")
        void shouldSetTimestampToNow() {
            java.time.Instant before = java.time.Instant.now();
            WebhookResponse response = WebhookResponse.of(Map.of(), Map.of());
            java.time.Instant after = java.time.Instant.now();

            assertNotNull(response.timestamp());
            assertTrue(response.timestamp().isAfter(before.minusSeconds(1)));
            assertTrue(response.timestamp().isBefore(after.plusSeconds(1)));
        }

        @Test
        @DisplayName("Should calculate totalReceived as accepted + rejected")
        void shouldCalculateTotalReceivedCorrectly() {
            Map<String, String> accepted = Map.of("a1", "d1", "a2", "d2", "a3", "d3");
            Map<String, String> rejected = Map.of("a4", "r1", "a5", "r2");

            WebhookResponse response = WebhookResponse.of(accepted, rejected);

            assertEquals(5, response.totalReceived());
            assertEquals(3, response.totalAccepted());
            assertEquals(2, response.totalRejected());
        }
    }
}
