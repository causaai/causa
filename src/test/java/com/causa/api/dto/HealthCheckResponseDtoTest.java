package com.causa.api.dto;

import com.causa.common.constants.AppConstants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link HealthCheckResponseDto}.
 * 
 * <p>Tests the builder pattern, component management, and timestamp handling
 * for the health check response DTO. Following Hexagonal Architecture principles,
 * these tests focus on the domain model without external dependencies.
 *
 * @since 0.0.1
 */
@DisplayName("HealthCheckResponseDto Tests")
class HealthCheckResponseDtoTest {

    @Nested
    @DisplayName("Builder Pattern Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should build DTO with all fields")
        void shouldBuildDtoWithAllFields() {
            // Given
            String status = AppConstants.HealthStatus.UP.getValue();
            String timestamp = "2026-06-10T10:00:00Z";
            String version = "1.0.0";

            // When
            HealthCheckResponseDto dto = HealthCheckResponseDto.builder()
                    .status(status)
                    .timestamp(timestamp)
                    .version(version)
                    .build();

            // Then
            assertNotNull(dto);
            assertEquals(status, dto.getStatus());
            assertEquals(timestamp, dto.getTimestamp());
            assertEquals(version, dto.getVersion());
            assertNotNull(dto.getComponents());
            assertTrue(dto.getComponents().isEmpty());
        }

        @Test
        @DisplayName("Should build DTO with timestampNow")
        void shouldBuildDtoWithTimestampNow() {
            // Given
            Instant before = Instant.now();

            // When
            HealthCheckResponseDto dto = HealthCheckResponseDto.builder()
                    .status(AppConstants.HealthStatus.UP.getValue())
                    .timestampNow()
                    .version("1.0.0")
                    .build();

            // Then
            Instant after = Instant.now();
            assertNotNull(dto.getTimestamp());
            // Timestamp should be in ISO 8601 format
            assertFalse(dto.getTimestamp().isEmpty());
            assertTrue(dto.getTimestamp().contains("T"));
            assertTrue(dto.getTimestamp().contains("Z"));
            
            // Parse and verify it's between before and after
            Instant parsedTimestamp = Instant.parse(dto.getTimestamp());
            assertTrue(parsedTimestamp.isAfter(before.minusSeconds(1)));
            assertTrue(parsedTimestamp.isBefore(after.plusSeconds(1)));
        }

        @Test
        @DisplayName("Should build DTO with minimal fields")
        void shouldBuildDtoWithMinimalFields() {
            // When
            HealthCheckResponseDto dto = HealthCheckResponseDto.builder()
                    .build();

            // Then
            assertNotNull(dto);
            assertNull(dto.getStatus());
            assertNull(dto.getTimestamp());
            assertNull(dto.getVersion());
            assertNotNull(dto.getComponents());
            assertTrue(dto.getComponents().isEmpty());
        }
    }

    @Nested
    @DisplayName("Component Management Tests")
    class ComponentManagementTests {

        @Test
        @DisplayName("Should add single component")
        void shouldAddSingleComponent() {
            // Given
            ComponentHealthDto dbHealth = ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.UP.getValue())
                    .message("Database is healthy")
                    .latencyMs(10L)
                    .build();

            // When
            HealthCheckResponseDto dto = HealthCheckResponseDto.builder()
                    .status(AppConstants.HealthStatus.UP.getValue())
                    .timestampNow()
                    .version("1.0.0")
                    .addComponent("database", dbHealth)
                    .build();

            // Then
            assertNotNull(dto.getComponents());
            assertEquals(1, dto.getComponents().size());
            assertTrue(dto.getComponents().containsKey("database"));
            assertEquals(dbHealth, dto.getComponents().get("database"));
        }

        @Test
        @DisplayName("Should add multiple components")
        void shouldAddMultipleComponents() {
            // Given
            ComponentHealthDto dbHealth = ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.UP.getValue())
                    .message("Database is healthy")
                    .build();

            ComponentHealthDto llmHealth = ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.UP.getValue())
                    .message("LLM is healthy")
                    .build();

            ComponentHealthDto mcpHealth = ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.DOWN.getValue())
                    .message("MCP is down")
                    .build();

            // When
            HealthCheckResponseDto dto = HealthCheckResponseDto.builder()
                    .status(AppConstants.HealthStatus.DEGRADED.getValue())
                    .timestampNow()
                    .version("1.0.0")
                    .addComponent("database", dbHealth)
                    .addComponent("llm_provider", llmHealth)
                    .addComponent("mcp_kubernetes", mcpHealth)
                    .build();

            // Then
            assertEquals(3, dto.getComponents().size());
            assertTrue(dto.getComponents().containsKey("database"));
            assertTrue(dto.getComponents().containsKey("llm_provider"));
            assertTrue(dto.getComponents().containsKey("mcp_kubernetes"));
        }

        @Test
        @DisplayName("Should handle null component name")
        void shouldHandleNullComponentName() {
            // Given
            ComponentHealthDto health = ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.UP.getValue())
                    .build();

            // When
            HealthCheckResponseDto dto = HealthCheckResponseDto.builder()
                    .addComponent(null, health)
                    .build();

            // Then
            assertNotNull(dto.getComponents());
            assertEquals(1, dto.getComponents().size());
            assertTrue(dto.getComponents().containsKey(null));
        }

        @Test
        @DisplayName("Should handle null component health")
        void shouldHandleNullComponentHealth() {
            // When
            HealthCheckResponseDto dto = HealthCheckResponseDto.builder()
                    .addComponent("database", null)
                    .build();

            // Then
            assertNotNull(dto.getComponents());
            assertEquals(1, dto.getComponents().size());
            assertNull(dto.getComponents().get("database"));
        }

        @Test
        @DisplayName("Should overwrite component with same name")
        void shouldOverwriteComponentWithSameName() {
            // Given
            ComponentHealthDto firstHealth = ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.UP.getValue())
                    .message("First")
                    .build();

            ComponentHealthDto secondHealth = ComponentHealthDto.builder()
                    .status(AppConstants.HealthStatus.DOWN.getValue())
                    .message("Second")
                    .build();

            // When
            HealthCheckResponseDto dto = HealthCheckResponseDto.builder()
                    .addComponent("database", firstHealth)
                    .addComponent("database", secondHealth)
                    .build();

            // Then
            assertEquals(1, dto.getComponents().size());
            assertEquals("Second", dto.getComponents().get("database").getMessage());
            assertEquals(AppConstants.HealthStatus.DOWN.getValue(), 
                        dto.getComponents().get("database").getStatus());
        }
    }

    @Nested
    @DisplayName("Immutability Tests")
    class ImmutabilityTests {

        @Test
        @DisplayName("Should return unmodifiable components map")
        void shouldReturnUnmodifiableComponentsMap() {
            // Given
            HealthCheckResponseDto dto = HealthCheckResponseDto.builder()
                    .addComponent("database", ComponentHealthDto.builder()
                            .status(AppConstants.HealthStatus.UP.getValue())
                            .build())
                    .build();

            // When/Then
            Map<String, ComponentHealthDto> components = dto.getComponents();
            assertThrows(UnsupportedOperationException.class, () -> {
                components.put("new_component", ComponentHealthDto.builder()
                        .status(AppConstants.HealthStatus.UP.getValue())
                        .build());
            });
        }

        @Test
        @DisplayName("Should maintain immutability after creation")
        void shouldMaintainImmutabilityAfterCreation() {
            // Given
            String originalStatus = AppConstants.HealthStatus.UP.getValue();
            String originalVersion = "1.0.0";
            String originalTimestamp = "2026-06-10T10:00:00Z";

            HealthCheckResponseDto dto = HealthCheckResponseDto.builder()
                    .status(originalStatus)
                    .timestamp(originalTimestamp)
                    .version(originalVersion)
                    .build();

            // When - Try to get values
            String status = dto.getStatus();
            String timestamp = dto.getTimestamp();
            String version = dto.getVersion();

            // Then - Values should remain unchanged
            assertEquals(originalStatus, dto.getStatus());
            assertEquals(originalTimestamp, dto.getTimestamp());
            assertEquals(originalVersion, dto.getVersion());
        }
    }

    @Nested
    @DisplayName("Status Value Tests")
    class StatusValueTests {

        @Test
        @DisplayName("Should accept UP status")
        void shouldAcceptUpStatus() {
            // When
            HealthCheckResponseDto dto = HealthCheckResponseDto.builder()
                    .status(AppConstants.HealthStatus.UP.getValue())
                    .build();

            // Then
            assertEquals("UP", dto.getStatus());
        }

        @Test
        @DisplayName("Should accept DOWN status")
        void shouldAcceptDownStatus() {
            // When
            HealthCheckResponseDto dto = HealthCheckResponseDto.builder()
                    .status(AppConstants.HealthStatus.DOWN.getValue())
                    .build();

            // Then
            assertEquals("DOWN", dto.getStatus());
        }

        @Test
        @DisplayName("Should accept DEGRADED status")
        void shouldAcceptDegradedStatus() {
            // When
            HealthCheckResponseDto dto = HealthCheckResponseDto.builder()
                    .status(AppConstants.HealthStatus.DEGRADED.getValue())
                    .build();

            // Then
            assertEquals("DEGRADED", dto.getStatus());
        }
    }

    @Nested
    @DisplayName("Typical Use Case Tests")
    class TypicalUseCaseTests {

        @Test
        @DisplayName("Should create healthy system response")
        void shouldCreateHealthySystemResponse() {
            // When
            HealthCheckResponseDto dto = HealthCheckResponseDto.builder()
                    .status(AppConstants.HealthStatus.UP.getValue())
                    .timestampNow()
                    .version("0.0.1")
                    .addComponent("database", ComponentHealthDto.builder()
                            .status(AppConstants.HealthStatus.UP.getValue())
                            .message("Connected to PostgreSQL")
                            .latencyMs(12L)
                            .build())
                    .addComponent("llm_provider", ComponentHealthDto.builder()
                            .status(AppConstants.HealthStatus.UP.getValue())
                            .message("Connected to LangChain4J with claude-sonnet-4-6")
                            .latencyMs(245L)
                            .build())
                    .addComponent("mcp_kubernetes", ComponentHealthDto.builder()
                            .status(AppConstants.HealthStatus.UP.getValue())
                            .message("Connected successfully")
                            .latencyMs(45L)
                            .build())
                    .build();

            // Then
            assertEquals("UP", dto.getStatus());
            assertEquals("0.0.1", dto.getVersion());
            assertEquals(3, dto.getComponents().size());
            assertNotNull(dto.getTimestamp());
        }

        @Test
        @DisplayName("Should create degraded system response")
        void shouldCreateDegradedSystemResponse() {
            // When
            HealthCheckResponseDto dto = HealthCheckResponseDto.builder()
                    .status(AppConstants.HealthStatus.DEGRADED.getValue())
                    .timestampNow()
                    .version("0.0.1")
                    .addComponent("database", ComponentHealthDto.builder()
                            .status(AppConstants.HealthStatus.UP.getValue())
                            .message("Connected to PostgreSQL")
                            .latencyMs(12L)
                            .build())
                    .addComponent("mcp_kubernetes", ComponentHealthDto.builder()
                            .status(AppConstants.HealthStatus.DOWN.getValue())
                            .message("Connection failed")
                            .latencyMs(5000L)
                            .build())
                    .build();

            // Then
            assertEquals("DEGRADED", dto.getStatus());
            assertEquals(2, dto.getComponents().size());
            assertEquals("UP", dto.getComponents().get("database").getStatus());
            assertEquals("DOWN", dto.getComponents().get("mcp_kubernetes").getStatus());
        }

        @Test
        @DisplayName("Should create down system response")
        void shouldCreateDownSystemResponse() {
            // When
            HealthCheckResponseDto dto = HealthCheckResponseDto.builder()
                    .status(AppConstants.HealthStatus.DOWN.getValue())
                    .timestampNow()
                    .version("0.0.1")
                    .addComponent("database", ComponentHealthDto.builder()
                            .status(AppConstants.HealthStatus.DOWN.getValue())
                            .message("Database connection not available")
                            .latencyMs(0L)
                            .build())
                    .build();

            // Then
            assertEquals("DOWN", dto.getStatus());
            assertEquals(1, dto.getComponents().size());
            assertEquals("DOWN", dto.getComponents().get("database").getStatus());
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle empty version")
        void shouldHandleEmptyVersion() {
            // When
            HealthCheckResponseDto dto = HealthCheckResponseDto.builder()
                    .version("")
                    .build();

            // Then
            assertEquals("", dto.getVersion());
        }

        @Test
        @DisplayName("Should handle very old timestamp")
        void shouldHandleVeryOldTimestamp() {
            // Given
            String oldTimestamp = "1970-01-01T00:00:00Z";

            // When
            HealthCheckResponseDto dto = HealthCheckResponseDto.builder()
                    .timestamp(oldTimestamp)
                    .build();

            // Then
            assertEquals(oldTimestamp, dto.getTimestamp());
        }

        @Test
        @DisplayName("Should handle future timestamp")
        void shouldHandleFutureTimestamp() {
            // Given
            String futureTimestamp = Instant.now().plusSeconds(3600).toString();

            // When
            HealthCheckResponseDto dto = HealthCheckResponseDto.builder()
                    .timestamp(futureTimestamp)
                    .build();

            // Then
            assertEquals(futureTimestamp, dto.getTimestamp());
        }

        @Test
        @DisplayName("Should handle many components")
        void shouldHandleManyComponents() {
            // Given
            HealthCheckResponseDto.Builder builder = HealthCheckResponseDto.builder()
                    .status(AppConstants.HealthStatus.UP.getValue())
                    .timestampNow()
                    .version("1.0.0");

            // Add 100 components
            for (int i = 0; i < 100; i++) {
                builder.addComponent("component_" + i, ComponentHealthDto.builder()
                        .status(AppConstants.HealthStatus.UP.getValue())
                        .message("Component " + i)
                        .latencyMs((long) i)
                        .build());
            }

            // When
            HealthCheckResponseDto dto = builder.build();

            // Then
            assertEquals(100, dto.getComponents().size());
            assertTrue(dto.getComponents().containsKey("component_0"));
            assertTrue(dto.getComponents().containsKey("component_99"));
        }
    }
}
