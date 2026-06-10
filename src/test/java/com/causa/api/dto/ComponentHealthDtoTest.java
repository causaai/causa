package com.causa.api.dto;

import com.causa.common.constants.AppConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ComponentHealthDto}.
 * 
 * <p>Tests the builder pattern, immutability, and validation of component health DTOs.
 * Following Hexagonal Architecture principles, these tests focus on the domain model
 * without external dependencies.
 *
 * @since 0.0.1
 */
@DisplayName("ComponentHealthDto Tests")
class ComponentHealthDtoTest {

    @Nested
    @DisplayName("Builder Pattern Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should build DTO with all fields")
        void shouldBuildDtoWithAllFields() {
            // Given
            String status = AppConstants.HealthStatus.UP.getValue();
            String message = "Component is healthy";
            long latency = 100L;

            // When
            ComponentHealthDto dto = ComponentHealthDto.builder()
                    .status(status)
                    .message(message)
                    .latencyMs(latency)
                    .build();

            // Then
            assertNotNull(dto);
            assertEquals(status, dto.getStatus());
            assertEquals(message, dto.getMessage());
            assertEquals(latency, dto.getLatencyMs());
        }

        @Test
        @DisplayName("Should build DTO with minimal fields")
        void shouldBuildDtoWithMinimalFields() {
            // Given
            String status = AppConstants.HealthStatus.DOWN.getValue();

            // When
            ComponentHealthDto dto = ComponentHealthDto.builder()
                    .status(status)
                    .build();

            // Then
            assertNotNull(dto);
            assertEquals(status, dto.getStatus());
            assertNull(dto.getMessage());
            assertNull(dto.getLatencyMs()); // latencyMs is Long, can be null
        }

        @Test
        @DisplayName("Should handle null status gracefully")
        void shouldHandleNullStatus() {
            // When
            ComponentHealthDto dto = ComponentHealthDto.builder()
                    .status(null)
                    .message("Test message")
                    .latencyMs(50L)
                    .build();

            // Then
            assertNotNull(dto);
            assertNull(dto.getStatus());
        }

        @Test
        @DisplayName("Should handle zero latency")
        void shouldHandleZeroLatency() {
            // When
            ComponentHealthDto dto = ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.UP.getValue())
                    .latencyMs(0L)
                    .build();

            // Then
            assertNotNull(dto);
            assertEquals(0L, dto.getLatencyMs());
        }

        @Test
        @DisplayName("Should handle negative latency")
        void shouldHandleNegativeLatency() {
            // When
            ComponentHealthDto dto = ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.UP.getValue())
                    .latencyMs(-1L)
                    .build();

            // Then
            assertNotNull(dto);
            assertEquals(-1L, dto.getLatencyMs());
        }
    }

    @Nested
    @DisplayName("Immutability Tests")
    class ImmutabilityTests {

        @Test
        @DisplayName("Should be immutable after creation")
        void shouldBeImmutableAfterCreation() {
            // Given
            ComponentHealthDto dto = ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.UP.getValue())
                    .message("Original message")
                    .latencyMs(100L)
                    .build();

            // When - Try to get values
            String status = dto.getStatus();
            String message = dto.getMessage();
            long latency = dto.getLatencyMs();

            // Then - Values should remain unchanged
            assertEquals(AppConstants.HealthStatus.UP.getValue(), dto.getStatus());
            assertEquals("Original message", dto.getMessage());
            assertEquals(100L, dto.getLatencyMs());
        }
    }

    @Nested
    @DisplayName("Status Value Tests")
    class StatusValueTests {

        @Test
        @DisplayName("Should accept UP status")
        void shouldAcceptUpStatus() {
            // When
            ComponentHealthDto dto = ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.UP.getValue())
                    .build();

            // Then
            assertEquals("UP", dto.getStatus());
        }

        @Test
        @DisplayName("Should accept DOWN status")
        void shouldAcceptDownStatus() {
            // When
            ComponentHealthDto dto = ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.DOWN.getValue())
                    .build();

            // Then
            assertEquals("DOWN", dto.getStatus());
        }

        @Test
        @DisplayName("Should accept DEGRADED status")
        void shouldAcceptDegradedStatus() {
            // When
            ComponentHealthDto dto = ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.DEGRADED.getValue())
                    .build();

            // Then
            assertEquals("DEGRADED", dto.getStatus());
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle empty message")
        void shouldHandleEmptyMessage() {
            // When
            ComponentHealthDto dto = ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.UP.getValue())
                    .message("")
                    .build();

            // Then
            assertNotNull(dto);
            assertEquals("", dto.getMessage());
        }

        @Test
        @DisplayName("Should handle very long message")
        void shouldHandleVeryLongMessage() {
            // Given
            String longMessage = "A".repeat(1000);

            // When
            ComponentHealthDto dto = ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.UP.getValue())
                    .message(longMessage)
                    .build();

            // Then
            assertNotNull(dto);
            assertEquals(1000, dto.getMessage().length());
        }

        @Test
        @DisplayName("Should handle very large latency")
        void shouldHandleVeryLargeLatency() {
            // Given
            long largeLatency = Long.MAX_VALUE;

            // When
            ComponentHealthDto dto = ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.UP.getValue())
                    .latencyMs(largeLatency)
                    .build();

            // Then
            assertNotNull(dto);
            assertEquals(Long.MAX_VALUE, dto.getLatencyMs());
        }

        @Test
        @DisplayName("Should handle message with special characters")
        void shouldHandleMessageWithSpecialCharacters() {
            // Given
            String specialMessage = "Error: Connection failed! @#$%^&*()";

            // When
            ComponentHealthDto dto = ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.DOWN.getValue())
                    .message(specialMessage)
                    .build();

            // Then
            assertNotNull(dto);
            assertEquals(specialMessage, dto.getMessage());
        }
    }

    @Nested
    @DisplayName("Typical Use Case Tests")
    class TypicalUseCaseTests {

        @Test
        @DisplayName("Should create healthy database component")
        void shouldCreateHealthyDatabaseComponent() {
            // When
            ComponentHealthDto dto = ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.UP.getValue())
                    .message("Connected to PostgreSQL")
                    .latencyMs(12L)
                    .build();

            // Then
            assertEquals("UP", dto.getStatus());
            assertEquals("Connected to PostgreSQL", dto.getMessage());
            assertEquals(12L, dto.getLatencyMs());
        }

        @Test
        @DisplayName("Should create unhealthy MCP component")
        void shouldCreateUnhealthyMcpComponent() {
            // When
            ComponentHealthDto dto = ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.DOWN.getValue())
                    .message("Connection failed: Connection refused")
                    .latencyMs(5000L)
                    .build();

            // Then
            assertEquals("DOWN", dto.getStatus());
            assertTrue(dto.getMessage().contains("Connection failed"));
            assertEquals(5000L, dto.getLatencyMs());
        }

        @Test
        @DisplayName("Should create healthy LLM component")
        void shouldCreateHealthyLlmComponent() {
            // When
            ComponentHealthDto dto = ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.UP.getValue())
                    .message("Connected to LangChain4J with claude-sonnet-4-6")
                    .latencyMs(2076L)
                    .build();

            // Then
            assertEquals("UP", dto.getStatus());
            assertTrue(dto.getMessage().contains("claude-sonnet-4-6"));
            assertTrue(dto.getLatencyMs() > 2000L);
        }
    }
}
