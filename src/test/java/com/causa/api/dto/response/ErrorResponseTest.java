package com.causa.api.dto.response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ErrorResponse}.
 *
 * @since 0.0.1
 */
@DisplayName("ErrorResponse Tests")
class ErrorResponseTest {

    @Nested
    @DisplayName("of() Factory Method Tests")
    class OfFactoryTests {

        @Test
        @DisplayName("Should create ErrorResponse with all fields set")
        void shouldCreateWithAllFields() {
            Instant before = Instant.now();

            ErrorResponse response = ErrorResponse.of(404, "Not Found", "No alert found with id: abc");

            Instant after = Instant.now();

            assertEquals(404, response.status());
            assertEquals("Not Found", response.error());
            assertEquals("No alert found with id: abc", response.message());
            assertNotNull(response.timestamp());
            // Timestamp should be set to approximately now
            assertTrue(response.timestamp().isAfter(before.minusSeconds(1)));
            assertTrue(response.timestamp().isBefore(after.plusSeconds(1)));
        }

        @Test
        @DisplayName("Should create 400 Bad Request error")
        void shouldCreate400Error() {
            ErrorResponse response = ErrorResponse.of(400, "Bad Request", "Validation failed");

            assertEquals(400, response.status());
            assertEquals("Bad Request", response.error());
            assertEquals("Validation failed", response.message());
        }

        @Test
        @DisplayName("Should create 500 Internal Server Error")
        void shouldCreate500Error() {
            ErrorResponse response = ErrorResponse.of(500, "Internal Server Error", "Unexpected failure");

            assertEquals(500, response.status());
            assertEquals("Internal Server Error", response.error());
        }

        @Test
        @DisplayName("Should create 503 Service Unavailable error")
        void shouldCreate503Error() {
            ErrorResponse response = ErrorResponse.of(503, "Service Unavailable", "Health check failed");

            assertEquals(503, response.status());
        }

        @Test
        @DisplayName("Should handle null error and message fields")
        void shouldHandleNullFields() {
            ErrorResponse response = ErrorResponse.of(400, null, null);

            assertEquals(400, response.status());
            assertNull(response.error());
            assertNull(response.message());
            assertNotNull(response.timestamp());
        }

        @Test
        @DisplayName("Timestamp should be an ISO-8601 instant (parseable)")
        void timestampShouldBeParseable() {
            ErrorResponse response = ErrorResponse.of(404, "Not Found", "test");

            // Confirm toString() produces an ISO-8601 string without throwing
            assertDoesNotThrow(() -> Instant.parse(response.timestamp().toString()));
        }
    }
}
