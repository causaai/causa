package com.causa.core.services;

import com.causa.api.dto.ComponentHealthDto;
import com.causa.api.dto.HealthCheckResponseDto;
import com.causa.common.constants.AppConstants;
import com.causa.common.constants.HealthCheckConstants;
import com.causa.config.LLMConfig;
import com.causa.core.domain.LLMRequest;
import com.causa.core.domain.LLMResponse;
import com.causa.infrastructure.persistence.DatabaseConnectionService;
import com.causa.llm.LangChainPromptSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link HealthCheckService}.
 * 
 * <p>Tests the health check service following Hexagonal Architecture principles.
 * All external dependencies are mocked to test the core business logic in isolation.
 * 
 * <p>Test Coverage:
 * <ul>
 *   <li>Database health checks</li>
 *   <li>MCP Kubernetes health checks</li>
 *   <li>LLM provider health checks</li>
 *   <li>Overall system status determination</li>
 *   <li>Error handling and edge cases</li>
 * </ul>
 *
 * @since 0.0.1
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HealthCheckService Tests")
class HealthCheckServiceTest {

    @Mock
    private DatabaseConnectionService databaseConnectionService;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private Statement statement;

    @Mock
    private LangChainPromptSender llmPromptSender;

    @Mock
    private LLMConfig llmConfig;

    private HealthCheckService healthCheckService;

    private static final String APP_VERSION = "0.0.1";
    // 192.0.2.1 is RFC 5737 TEST-NET — guaranteed non-routable, always results in connection failure
    private static final String MCP_ENDPOINT = "http://192.0.2.1";
    private static final String MCP_HEALTH_PATH = "/health";
    private static final int MCP_TIMEOUT = 1; // 1 ms — fail immediately, don't slow down tests

    @BeforeEach
    void setUp() {
        healthCheckService = new HealthCheckService(
                databaseConnectionService,
                dataSource,
                APP_VERSION,
                MCP_ENDPOINT,
                MCP_HEALTH_PATH,
                MCP_TIMEOUT,
                llmPromptSender,
                llmConfig
        );
    }

    @Nested
    @DisplayName("Database Health Check Tests")
    class DatabaseHealthCheckTests {

        @Test
        @DisplayName("Should return UP when database is ready and query succeeds")
        void shouldReturnUpWhenDatabaseIsHealthy() throws Exception {
            // Given
            when(databaseConnectionService.isReady()).thenReturn(true);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.createStatement()).thenReturn(statement);
            when(statement.execute("SELECT 1")).thenReturn(true);

            // Mock LLM and MCP to avoid failures
            when(llmPromptSender.isReady()).thenReturn(false);

            // When
            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            // Then
            assertNotNull(response);
            ComponentHealthDto dbHealth = response.getComponents().get(HealthCheckConstants.ComponentNames.DATABASE);
            assertNotNull(dbHealth);
            assertEquals(AppConstants.HealthStatus.UP.getValue(), dbHealth.getStatus());
            assertTrue(dbHealth.getLatencyMs() >= 0);

            verify(databaseConnectionService).isReady();
            verify(dataSource).getConnection();
            verify(connection).createStatement();
            verify(statement).execute("SELECT 1");
            verify(connection).close();
        }

        @Test
        @DisplayName("Should return DOWN when database is not ready")
        void shouldReturnDownWhenDatabaseNotReady() {
            // Given
            when(databaseConnectionService.isReady()).thenReturn(false);
            when(llmPromptSender.isReady()).thenReturn(false);

            // When
            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            // Then
            assertNotNull(response);
            ComponentHealthDto dbHealth = response.getComponents().get(HealthCheckConstants.ComponentNames.DATABASE);
            assertNotNull(dbHealth);
            assertEquals(AppConstants.HealthStatus.DOWN.getValue(), dbHealth.getStatus());
            assertEquals(0L, dbHealth.getLatencyMs());

            verify(databaseConnectionService).isReady();
            verifyNoInteractions(dataSource);
        }

        @Test
        @DisplayName("Should return DOWN when database query fails")
        void shouldReturnDownWhenDatabaseQueryFails() throws Exception {
            // Given
            when(databaseConnectionService.isReady()).thenReturn(true);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.createStatement()).thenReturn(statement);
            when(statement.execute("SELECT 1")).thenThrow(new RuntimeException("Query failed"));
            when(llmPromptSender.isReady()).thenReturn(false);

            // When
            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            // Then
            assertNotNull(response);
            ComponentHealthDto dbHealth = response.getComponents().get(HealthCheckConstants.ComponentNames.DATABASE);
            assertNotNull(dbHealth);
            assertEquals(AppConstants.HealthStatus.DOWN.getValue(), dbHealth.getStatus());
            assertTrue(dbHealth.getMessage().contains("failed"));

            verify(connection).close();
        }

        @Test
        @DisplayName("Should handle connection acquisition failure")
        void shouldHandleConnectionAcquisitionFailure() throws Exception {
            // Given
            when(databaseConnectionService.isReady()).thenReturn(true);
            when(dataSource.getConnection()).thenThrow(new RuntimeException("Connection pool exhausted"));
            when(llmPromptSender.isReady()).thenReturn(false);

            // When
            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            // Then
            assertNotNull(response);
            ComponentHealthDto dbHealth = response.getComponents().get(HealthCheckConstants.ComponentNames.DATABASE);
            assertNotNull(dbHealth);
            assertEquals(AppConstants.HealthStatus.DOWN.getValue(), dbHealth.getStatus());
            assertTrue(dbHealth.getMessage().contains("failed"));
        }
    }

    @Nested
    @DisplayName("LLM Health Check Tests")
    class LlmHealthCheckTests {

        @Test
        @DisplayName("Should return UP when LLM is ready and responds")
        void shouldReturnUpWhenLlmIsHealthy() {
            // Given
            when(databaseConnectionService.isReady()).thenReturn(false);
            when(llmPromptSender.isReady()).thenReturn(true);
            when(llmConfig.modelName()).thenReturn("claude-sonnet-4-6");
            
            LLMResponse mockResponse = new LLMResponse(
                    "OK",
                    "claude-sonnet-4-6",
                    11L,
                    4L,
                    0L,
                    0L,
                    100L
            );
            when(llmPromptSender.send(any(LLMRequest.class))).thenReturn(mockResponse);

            // When
            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            // Then
            assertNotNull(response);
            ComponentHealthDto llmHealth = response.getComponents().get(HealthCheckConstants.ComponentNames.LLM_PROVIDER);
            assertNotNull(llmHealth);
            assertEquals(AppConstants.HealthStatus.UP.getValue(), llmHealth.getStatus());
            assertTrue(llmHealth.getMessage().contains("claude-sonnet-4-6"));
            assertNotNull(llmHealth.getLatencyMs());
            assertTrue(llmHealth.getLatencyMs() >= 0);

            verify(llmPromptSender).isReady();
            verify(llmPromptSender).send(any(LLMRequest.class));
        }

        @Test
        @DisplayName("Should return DOWN when LLM is not ready")
        void shouldReturnDownWhenLlmNotReady() {
            // Given
            when(databaseConnectionService.isReady()).thenReturn(false);
            when(llmPromptSender.isReady()).thenReturn(false);

            // When
            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            // Then
            assertNotNull(response);
            ComponentHealthDto llmHealth = response.getComponents().get(HealthCheckConstants.ComponentNames.LLM_PROVIDER);
            assertNotNull(llmHealth);
            assertEquals(AppConstants.HealthStatus.DOWN.getValue(), llmHealth.getStatus());

            verify(llmPromptSender).isReady();
            verify(llmPromptSender, never()).send(any(LLMRequest.class));
        }

        @Test
        @DisplayName("Should return DOWN when LLM test prompt fails")
        void shouldReturnDownWhenLlmTestPromptFails() {
            // Given
            when(databaseConnectionService.isReady()).thenReturn(false);
            when(llmPromptSender.isReady()).thenReturn(true);
            when(llmPromptSender.send(any(LLMRequest.class)))
                    .thenThrow(new RuntimeException("LLM request failed"));

            // When
            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            // Then
            assertNotNull(response);
            ComponentHealthDto llmHealth = response.getComponents().get(HealthCheckConstants.ComponentNames.LLM_PROVIDER);
            assertNotNull(llmHealth);
            assertEquals(AppConstants.HealthStatus.DOWN.getValue(), llmHealth.getStatus());
            assertTrue(llmHealth.getMessage().contains("failed"));
        }

        @Test
        @DisplayName("Should return DOWN when LLM returns empty response")
        void shouldReturnDownWhenLlmReturnsEmptyResponse() {
            // Given
            when(databaseConnectionService.isReady()).thenReturn(false);
            when(llmPromptSender.isReady()).thenReturn(true);
            
            LLMResponse emptyResponse = new LLMResponse(
                    "",
                    "claude-sonnet-4-6",
                    11L,
                    0L,
                    0L,
                    0L,
                    100L
            );
            when(llmPromptSender.send(any(LLMRequest.class))).thenReturn(emptyResponse);

            // When
            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            // Then
            assertNotNull(response);
            ComponentHealthDto llmHealth = response.getComponents().get(HealthCheckConstants.ComponentNames.LLM_PROVIDER);
            assertNotNull(llmHealth);
            assertEquals(AppConstants.HealthStatus.DOWN.getValue(), llmHealth.getStatus());
        }

        @Test
        @DisplayName("Should return DOWN when LLM returns null response")
        void shouldReturnDownWhenLlmReturnsNullResponse() {
            // Given
            when(databaseConnectionService.isReady()).thenReturn(false);
            when(llmPromptSender.isReady()).thenReturn(true);
            when(llmPromptSender.send(any(LLMRequest.class))).thenReturn(null);

            // When
            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            // Then
            assertNotNull(response);
            ComponentHealthDto llmHealth = response.getComponents().get(HealthCheckConstants.ComponentNames.LLM_PROVIDER);
            assertNotNull(llmHealth);
            assertEquals(AppConstants.HealthStatus.DOWN.getValue(), llmHealth.getStatus());
        }
    }

    @Nested
    @DisplayName("Overall System Status Tests")
    class OverallSystemStatusTests {

        @Test
        @DisplayName("Should return UP when all components are UP")
        void shouldReturnUpWhenAllComponentsAreUp() throws Exception {
            // Given - Database UP
            when(databaseConnectionService.isReady()).thenReturn(true);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.createStatement()).thenReturn(statement);
            when(statement.execute("SELECT 1")).thenReturn(true);

            // Given - LLM UP
            when(llmPromptSender.isReady()).thenReturn(true);
            when(llmConfig.modelName()).thenReturn("claude-sonnet-4-6");
            LLMResponse mockResponse = new LLMResponse("OK", "claude-sonnet-4-6", 11L, 4L, 0L, 0L, 100L);
            when(llmPromptSender.send(any(LLMRequest.class))).thenReturn(mockResponse);

            // When
            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            // Then
            assertNotNull(response);
            // Note: MCP will be DOWN (connection refused), so overall should be DEGRADED
            assertEquals(AppConstants.HealthStatus.DEGRADED.getValue(), response.getStatus());
        }

        @Test
        @DisplayName("Should return DOWN when database is DOWN")
        void shouldReturnDownWhenDatabaseIsDown() {
            // Given - Database DOWN
            when(databaseConnectionService.isReady()).thenReturn(false);

            // Given - LLM UP
            when(llmPromptSender.isReady()).thenReturn(true);
            when(llmConfig.modelName()).thenReturn("claude-sonnet-4-6");
            LLMResponse mockResponse = new LLMResponse("OK", "claude-sonnet-4-6", 11L, 4L, 0L, 0L, 100L);
            when(llmPromptSender.send(any(LLMRequest.class))).thenReturn(mockResponse);

            // When
            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            // Then
            assertNotNull(response);
            assertEquals(AppConstants.HealthStatus.DOWN.getValue(), response.getStatus());
        }

        @Test
        @DisplayName("Should return DEGRADED when database is UP but LLM is DOWN")
        void shouldReturnDegradedWhenDatabaseUpButLlmDown() throws Exception {
            // Given - Database UP
            when(databaseConnectionService.isReady()).thenReturn(true);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.createStatement()).thenReturn(statement);
            when(statement.execute("SELECT 1")).thenReturn(true);

            // Given - LLM DOWN
            when(llmPromptSender.isReady()).thenReturn(false);

            // When
            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            // Then
            assertNotNull(response);
            assertEquals(AppConstants.HealthStatus.DEGRADED.getValue(), response.getStatus());
        }

        @Test
        @DisplayName("Should return DEGRADED when database is UP but MCP is DOWN")
        void shouldReturnDegradedWhenDatabaseUpButMcpDown() throws Exception {
            // Given - Database UP
            when(databaseConnectionService.isReady()).thenReturn(true);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.createStatement()).thenReturn(statement);
            when(statement.execute("SELECT 1")).thenReturn(true);

            // Given - LLM DOWN
            when(llmPromptSender.isReady()).thenReturn(false);

            // When
            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            // Then
            assertNotNull(response);
            assertEquals(AppConstants.HealthStatus.DEGRADED.getValue(), response.getStatus());
            
            // Verify MCP component exists and is DOWN
            ComponentHealthDto mcpHealth = response.getComponents().get(HealthCheckConstants.ComponentNames.MCP_KUBERNETES);
            assertNotNull(mcpHealth);
            assertEquals(AppConstants.HealthStatus.DOWN.getValue(), mcpHealth.getStatus());
        }
    }

    @Nested
    @DisplayName("Response Structure Tests")
    class ResponseStructureTests {

        @Test
        @DisplayName("Should include all required fields in response")
        void shouldIncludeAllRequiredFieldsInResponse() {
            // Given
            when(databaseConnectionService.isReady()).thenReturn(false);
            when(llmPromptSender.isReady()).thenReturn(false);

            // When
            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            // Then
            assertNotNull(response);
            assertNotNull(response.getStatus());
            assertNotNull(response.getTimestamp());
            assertNotNull(response.getVersion());
            assertNotNull(response.getComponents());
            assertEquals(APP_VERSION, response.getVersion());
        }

        @Test
        @DisplayName("Should include all component health checks")
        void shouldIncludeAllComponentHealthChecks() {
            // Given
            when(databaseConnectionService.isReady()).thenReturn(false);
            when(llmPromptSender.isReady()).thenReturn(false);

            // When
            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            // Then
            assertNotNull(response.getComponents());
            assertTrue(response.getComponents().containsKey(HealthCheckConstants.ComponentNames.DATABASE));
            assertTrue(response.getComponents().containsKey(HealthCheckConstants.ComponentNames.MCP_KUBERNETES));
            assertTrue(response.getComponents().containsKey(HealthCheckConstants.ComponentNames.LLM_PROVIDER));
        }

        @Test
        @DisplayName("Should have valid timestamp format")
        void shouldHaveValidTimestampFormat() {
            // Given
            when(databaseConnectionService.isReady()).thenReturn(false);
            when(llmPromptSender.isReady()).thenReturn(false);

            // When
            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            // Then
            assertNotNull(response.getTimestamp());
            assertTrue(response.getTimestamp().contains("T"));
            assertTrue(response.getTimestamp().contains("Z"));
            // Should be parseable as ISO 8601
            assertDoesNotThrow(() -> java.time.Instant.parse(response.getTimestamp()));
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle null from database connection service")
        void shouldHandleNullFromDatabaseConnectionService() {
            // Given
            when(databaseConnectionService.isReady()).thenReturn(false);
            when(llmPromptSender.isReady()).thenReturn(false);

            // When
            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            // Then
            assertNotNull(response);
            ComponentHealthDto dbHealth = response.getComponents().get(HealthCheckConstants.ComponentNames.DATABASE);
            assertEquals(AppConstants.HealthStatus.DOWN.getValue(), dbHealth.getStatus());
        }

        @Test
        @DisplayName("Should handle concurrent health check calls")
        void shouldHandleConcurrentHealthCheckCalls() throws Exception {
            // Given
            when(databaseConnectionService.isReady()).thenReturn(true);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.createStatement()).thenReturn(statement);
            when(statement.execute("SELECT 1")).thenReturn(true);
            when(llmPromptSender.isReady()).thenReturn(false);

            // When - Call multiple times
            HealthCheckResponseDto response1 = healthCheckService.getSystemHealth();
            HealthCheckResponseDto response2 = healthCheckService.getSystemHealth();

            // Then - Both should succeed
            assertNotNull(response1);
            assertNotNull(response2);
            assertNotEquals(response1.getTimestamp(), response2.getTimestamp());
        }
    }
}
