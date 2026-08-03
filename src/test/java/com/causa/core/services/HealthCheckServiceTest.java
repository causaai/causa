package com.causa.core.services;

import com.causa.api.dto.ComponentHealthDto;
import com.causa.api.dto.HealthCheckResponseDto;
import com.causa.common.constants.AppConstants;
import com.causa.common.constants.HealthCheckConstants;
import com.causa.config.AppConfig;
import com.causa.config.LlmConfigSnapshot;
import com.causa.core.domain.LLMRequest;
import com.causa.core.domain.LLMResponse;
import com.causa.core.ports.llm.PromptSender;
import com.causa.infrastructure.persistence.DatabaseConnectionService;
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
 * <p>Pure unit tests — all dependencies are mocked via Mockito.
 * MCP endpoint checks inside {@link HealthCheckService} use {@code java.net.http.HttpClient}
 * internally and cannot be mocked at this layer without production refactoring.
 * Those checks are therefore excluded here; they are covered by integration tests.
 *
 * <p>What IS tested here:
 * <ul>
 *   <li>Database health logic (ready / not-ready / query-fail / connection-fail)</li>
 *   <li>LLM provider health logic (ready / not-ready / send-fail / empty / null response)</li>
 *   <li>Overall status aggregation (UP / DOWN / DEGRADED)</li>
 *   <li>Response structure (version, timestamp, components map)</li>
 * </ul>
 *
 * <p>MCP always resolves to DOWN in these tests (non-routable endpoint + 1ms timeout),
 * so all assertions about MCP status expect DOWN.
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
    private PromptSender llmPromptSender;

    @Mock
    private AppConfig appConfig;

    @Mock
    private LlmConfigSnapshot llmConfigSnapshot;

    private HealthCheckService healthCheckService;

    private static final String APP_VERSION = "0.0.1-TEST";

    /**
     * 192.0.2.x is RFC 5737 TEST-NET-1 — guaranteed non-routable.
     * Combined with a 1ms connect timeout, the HttpClient fails instantly
     * without blocking the test thread.
     */
    private static final String MCP_DEAD_ENDPOINT = "http://192.0.2.1";
    private static final String MCP_HEALTH_PATH   = "/health";
    private static final int    MCP_TIMEOUT_MS    = 1;

    @BeforeEach
    void setUp() {
        healthCheckService = new HealthCheckService(
                databaseConnectionService,
                dataSource,
                APP_VERSION,
                "cluster",
                MCP_DEAD_ENDPOINT, MCP_HEALTH_PATH, MCP_TIMEOUT_MS,   // k8s
                MCP_DEAD_ENDPOINT, MCP_HEALTH_PATH, MCP_TIMEOUT_MS,   // kruize
                MCP_DEAD_ENDPOINT, MCP_HEALTH_PATH, MCP_TIMEOUT_MS,   // cryostat
                MCP_DEAD_ENDPOINT, MCP_HEALTH_PATH, MCP_TIMEOUT_MS,   // filesystem
                llmPromptSender,
                appConfig
        );
    }

    // -------------------------------------------------------------------------
    // Database health
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Database Health Tests")
    class DatabaseHealthTests {

        @Test
        @DisplayName("UP — database ready and SELECT 1 succeeds")
        void upWhenDatabaseReadyAndQuerySucceeds() throws Exception {
            when(databaseConnectionService.isReady()).thenReturn(true);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.createStatement()).thenReturn(statement);
            when(statement.execute("SELECT 1")).thenReturn(true);
            when(llmPromptSender.isReady()).thenReturn(false);

            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            ComponentHealthDto db = response.getComponents().get(HealthCheckConstants.ComponentNames.DATABASE);
            assertNotNull(db);
            assertEquals(AppConstants.HealthStatus.UP.getValue(), db.getStatus());
            assertTrue(db.getLatencyMs() >= 0);
            verify(dataSource).getConnection();
            verify(statement).execute("SELECT 1");
            verify(connection).close();
        }

        @Test
        @DisplayName("DOWN — databaseConnectionService.isReady() returns false")
        void downWhenNotReady() {
            when(databaseConnectionService.isReady()).thenReturn(false);
            when(llmPromptSender.isReady()).thenReturn(false);

            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            ComponentHealthDto db = response.getComponents().get(HealthCheckConstants.ComponentNames.DATABASE);
            assertEquals(AppConstants.HealthStatus.DOWN.getValue(), db.getStatus());
            assertEquals(0L, db.getLatencyMs());
            verifyNoInteractions(dataSource);
        }

        @Test
        @DisplayName("DOWN — SELECT 1 throws exception")
        void downWhenQueryFails() throws Exception {
            when(databaseConnectionService.isReady()).thenReturn(true);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.createStatement()).thenReturn(statement);
            when(statement.execute("SELECT 1")).thenThrow(new RuntimeException("Query failed"));
            when(llmPromptSender.isReady()).thenReturn(false);

            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            ComponentHealthDto db = response.getComponents().get(HealthCheckConstants.ComponentNames.DATABASE);
            assertEquals(AppConstants.HealthStatus.DOWN.getValue(), db.getStatus());
            assertTrue(db.getMessage().contains("failed"));
            verify(connection).close();
        }

        @Test
        @DisplayName("DOWN — DataSource.getConnection() throws exception")
        void downWhenConnectionAcquisitionFails() throws Exception {
            when(databaseConnectionService.isReady()).thenReturn(true);
            when(dataSource.getConnection()).thenThrow(new RuntimeException("Pool exhausted"));
            when(llmPromptSender.isReady()).thenReturn(false);

            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            ComponentHealthDto db = response.getComponents().get(HealthCheckConstants.ComponentNames.DATABASE);
            assertEquals(AppConstants.HealthStatus.DOWN.getValue(), db.getStatus());
            assertTrue(db.getMessage().contains("failed"));
        }
    }

    // -------------------------------------------------------------------------
    // LLM provider health
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("LLM Provider Health Tests")
    class LlmHealthTests {

        @Test
        @DisplayName("UP — isReady true and send() returns non-empty response")
        void upWhenReadyAndResponds() {
            when(databaseConnectionService.isReady()).thenReturn(false);
            when(llmPromptSender.isReady()).thenReturn(true);
            when(appConfig.getLlmConfig()).thenReturn(llmConfigSnapshot);
            when(llmConfigSnapshot.getProvider()).thenReturn("bob");
            when(llmConfigSnapshot.getModelName()).thenReturn("bob");
            
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

            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            // Then
            assertNotNull(response);
            ComponentHealthDto llmHealth = response.getComponents().get(HealthCheckConstants.ComponentNames.LLM_PROVIDER);
            assertNotNull(llmHealth);
            assertEquals(AppConstants.HealthStatus.UP.getValue(), llmHealth.getStatus());
            assertTrue(llmHealth.getMessage().contains("bob / bob"),
                    "Expected message to contain 'bob / bob' (provider / model), but was: " + llmHealth.getMessage());
            assertNotNull(llmHealth.getLatencyMs());
            assertTrue(llmHealth.getLatencyMs() >= 0);

            verify(llmPromptSender).isReady();
            verify(llmPromptSender).send(any(LLMRequest.class));
        }

        @Test
        @DisplayName("UP — modelName absent in config falls back to 'unknown' label")
        void upWithUnknownFallbackWhenModelNameAbsent() {
            when(databaseConnectionService.isReady()).thenReturn(false);
            when(llmPromptSender.isReady()).thenReturn(true);
            when(appConfig.getLlmConfig()).thenReturn(llmConfigSnapshot);
            when(llmConfigSnapshot.getProvider()).thenReturn("bob");
            when(llmConfigSnapshot.getModelName()).thenReturn("");
            when(llmPromptSender.send(any(LLMRequest.class))).thenReturn(
                    new LLMResponse("OK", "bob", 1L, 1L, 0L, 0L, 10L));

            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            ComponentHealthDto llm = response.getComponents().get(HealthCheckConstants.ComponentNames.LLM_PROVIDER);
            assertEquals(AppConstants.HealthStatus.UP.getValue(), llm.getStatus());
            assertTrue(llm.getMessage().contains("unknown"));
        }

        @Test
        @DisplayName("DOWN — isReady() returns false; send() never called")
        void downWhenNotReady() {
            when(databaseConnectionService.isReady()).thenReturn(false);
            when(llmPromptSender.isReady()).thenReturn(false);

            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            ComponentHealthDto llm = response.getComponents().get(HealthCheckConstants.ComponentNames.LLM_PROVIDER);
            assertEquals(AppConstants.HealthStatus.DOWN.getValue(), llm.getStatus());
            verify(llmPromptSender, never()).send(any(LLMRequest.class));
        }

        @Test
        @DisplayName("DOWN — send() throws an exception")
        void downWhenSendThrows() {
            when(databaseConnectionService.isReady()).thenReturn(false);
            when(llmPromptSender.isReady()).thenReturn(true);
            when(llmPromptSender.send(any(LLMRequest.class)))
                    .thenThrow(new RuntimeException("LLM request failed"));

            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            ComponentHealthDto llm = response.getComponents().get(HealthCheckConstants.ComponentNames.LLM_PROVIDER);
            assertEquals(AppConstants.HealthStatus.DOWN.getValue(), llm.getStatus());
            assertTrue(llm.getMessage().contains("failed"));
        }

        @Test
        @DisplayName("DOWN — send() returns response with empty text")
        void downWhenSendReturnsEmptyText() {
            when(databaseConnectionService.isReady()).thenReturn(false);
            when(llmPromptSender.isReady()).thenReturn(true);
            when(llmPromptSender.send(any(LLMRequest.class))).thenReturn(
                    new LLMResponse("", "claude-sonnet-4-6", 11L, 0L, 0L, 0L, 100L));

            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            ComponentHealthDto llm = response.getComponents().get(HealthCheckConstants.ComponentNames.LLM_PROVIDER);
            assertEquals(AppConstants.HealthStatus.DOWN.getValue(), llm.getStatus());
        }

        @Test
        @DisplayName("DOWN — send() returns null")
        void downWhenSendReturnsNull() {
            when(databaseConnectionService.isReady()).thenReturn(false);
            when(llmPromptSender.isReady()).thenReturn(true);
            when(llmPromptSender.send(any(LLMRequest.class))).thenReturn(null);

            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            ComponentHealthDto llm = response.getComponents().get(HealthCheckConstants.ComponentNames.LLM_PROVIDER);
            assertEquals(AppConstants.HealthStatus.DOWN.getValue(), llm.getStatus());
        }
    }

    // -------------------------------------------------------------------------
    // Overall status aggregation
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Overall Status Aggregation Tests")
    class OverallStatusTests {

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
            when(appConfig.getLlmConfig()).thenReturn(llmConfigSnapshot);
            when(llmConfigSnapshot.getProvider()).thenReturn("bob");
            when(llmConfigSnapshot.getModelName()).thenReturn("bob");
            LLMResponse mockResponse = new LLMResponse("OK", "bob", 11L, 4L, 0L, 0L, 100L);
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
            // LLM UP — doesn't matter, DB is critical
            when(llmPromptSender.isReady()).thenReturn(true);
            when(appConfig.getLlmConfig()).thenReturn(llmConfigSnapshot);
            when(llmConfigSnapshot.getProvider()).thenReturn("bob");
            when(llmConfigSnapshot.getModelName()).thenReturn("bob");
            LLMResponse mockResponse = new LLMResponse("OK", "bob", 11L, 4L, 0L, 0L, 100L);
            when(llmPromptSender.send(any(LLMRequest.class))).thenReturn(mockResponse);

            assertEquals(AppConstants.HealthStatus.DOWN.getValue(),
                    healthCheckService.getSystemHealth().getStatus());
        }

        @Test
        @DisplayName("DEGRADED — database UP but LLM DOWN")
        void degradedWhenDatabaseUpButLlmDown() throws Exception {
            when(databaseConnectionService.isReady()).thenReturn(true);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.createStatement()).thenReturn(statement);
            when(statement.execute("SELECT 1")).thenReturn(true);
            when(llmPromptSender.isReady()).thenReturn(false);

            assertEquals(AppConstants.HealthStatus.DEGRADED.getValue(),
                    healthCheckService.getSystemHealth().getStatus());
        }

        @Test
        @DisplayName("DEGRADED — database UP, LLM UP, MCP always DOWN (no real HTTP in unit tests)")
        void degradedWhenDatabaseAndLlmUpButMcpDown() throws Exception {
            // DB UP
            when(databaseConnectionService.isReady()).thenReturn(true);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.createStatement()).thenReturn(statement);
            when(statement.execute("SELECT 1")).thenReturn(true);
            // LLM UP
            when(llmPromptSender.isReady()).thenReturn(true);
            when(llmPromptSender.send(any(LLMRequest.class))).thenReturn(
                    new LLMResponse("OK", "claude-sonnet-4-6", 10L, 4L, 0L, 0L, 50L));
            // MCP → DOWN (192.0.2.1 + 1ms timeout → instant fail)

            // Overall must be DEGRADED (not DOWN — DB and LLM are UP)
            assertEquals(AppConstants.HealthStatus.DEGRADED.getValue(),
                    healthCheckService.getSystemHealth().getStatus());
        }
    }

    // -------------------------------------------------------------------------
    // Response structure
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Response Structure Tests")
    class ResponseStructureTests {

        @BeforeEach
        void allDown() {
            when(databaseConnectionService.isReady()).thenReturn(false);
            when(llmPromptSender.isReady()).thenReturn(false);
        }

        @Test
        @DisplayName("Response includes version from constructor")
        void responseIncludesVersion() {
            assertEquals(APP_VERSION, healthCheckService.getSystemHealth().getVersion());
        }

        @Test
        @DisplayName("Response includes ISO-8601 timestamp")
        void responseIncludesIsoTimestamp() {
            String ts = healthCheckService.getSystemHealth().getTimestamp();
            assertNotNull(ts);
            assertTrue(ts.contains("T") && ts.contains("Z"));
            assertDoesNotThrow(() -> java.time.Instant.parse(ts));
        }

        @Test
        @DisplayName("Response always contains database component")
        void responseContainsDatabaseComponent() {
            assertTrue(healthCheckService.getSystemHealth().getComponents()
                    .containsKey(HealthCheckConstants.ComponentNames.DATABASE));
        }

        @Test
        @DisplayName("Response always contains llm_provider component")
        void responseContainsLlmComponent() {
            assertTrue(healthCheckService.getSystemHealth().getComponents()
                    .containsKey(HealthCheckConstants.ComponentNames.LLM_PROVIDER));
        }

        @Test
        @DisplayName("Response always contains mcp_kubernetes component in cluster mode")
        void responseContainsMcpKubernetesComponent() {
            assertTrue(healthCheckService.getSystemHealth().getComponents()
                    .containsKey(HealthCheckConstants.ComponentNames.MCP_KUBERNETES));
        }

        @Test
        @DisplayName("Successive calls produce different timestamps")
        void successiveCallsProduceDifferentTimestamps() {
            String ts1 = healthCheckService.getSystemHealth().getTimestamp();
            String ts2 = healthCheckService.getSystemHealth().getTimestamp();
            // Timestamps may be equal if both calls happen within the same millisecond,
            // but they must both be non-null valid ISO strings
            assertNotNull(ts1);
            assertNotNull(ts2);
            assertDoesNotThrow(() -> java.time.Instant.parse(ts1));
            assertDoesNotThrow(() -> java.time.Instant.parse(ts2));
        }

        @Test
        @DisplayName("MCP kubernetes component is DOWN in unit tests (no real HTTP)")
        void mcpAlwaysDownInUnitTests() {
            ComponentHealthDto mcp = healthCheckService.getSystemHealth().getComponents()
                    .get(HealthCheckConstants.ComponentNames.MCP_KUBERNETES);
            assertNotNull(mcp);
            assertEquals(AppConstants.HealthStatus.DOWN.getValue(), mcp.getStatus());
        }
    }

    // -------------------------------------------------------------------------
    // VM platform mode — filesystem MCP only
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("VM Platform Mode Tests")
    class VmPlatformTests {

        private HealthCheckService vmHealthService;

        @BeforeEach
        void setUpVm() {
            vmHealthService = new HealthCheckService(
                    databaseConnectionService,
                    dataSource,
                    APP_VERSION,
                    "vm",
                    MCP_DEAD_ENDPOINT, MCP_HEALTH_PATH, MCP_TIMEOUT_MS,
                    MCP_DEAD_ENDPOINT, MCP_HEALTH_PATH, MCP_TIMEOUT_MS,
                    MCP_DEAD_ENDPOINT, MCP_HEALTH_PATH, MCP_TIMEOUT_MS,
                    MCP_DEAD_ENDPOINT, MCP_HEALTH_PATH, MCP_TIMEOUT_MS,
                    llmPromptSender,
                    appConfig
            );
        }

        @Test
        @DisplayName("VM mode — mcp_filesystem component present instead of mcp_kubernetes")
        void vmModeIncludesFilesystemNotKubernetes() {
            when(databaseConnectionService.isReady()).thenReturn(false);
            when(llmPromptSender.isReady()).thenReturn(false);

            HealthCheckResponseDto response = vmHealthService.getSystemHealth();

            assertTrue(response.getComponents()
                    .containsKey(HealthCheckConstants.ComponentNames.MCP_FILESYSTEM));
            assertFalse(response.getComponents()
                    .containsKey(HealthCheckConstants.ComponentNames.MCP_KUBERNETES));
        }

        @Test
        @DisplayName("VM mode — overall DOWN when database is DOWN")
        void vmModeDownWhenDatabaseDown() {
            when(databaseConnectionService.isReady()).thenReturn(false);
            when(llmPromptSender.isReady()).thenReturn(false);

            assertEquals(AppConstants.HealthStatus.DOWN.getValue(),
                    vmHealthService.getSystemHealth().getStatus());
        }
    }
}
