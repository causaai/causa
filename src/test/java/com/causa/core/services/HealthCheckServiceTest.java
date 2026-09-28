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
import com.causa.mcp.McpClient;
import com.causa.mcp.McpRegistry;
import com.causa.mcp.config.McpSettings;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link HealthCheckService}.
 *
 * <p>Pure unit tests — all dependencies are mocked via Mockito, including {@link McpRegistry} and
 * {@link McpClient} — since {@code McpClient} is a plain class (not raw {@code HttpClient} usage
 * inline), MCP health can now be fully mocked and deterministic, unlike the previous
 * implementation.
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

    @Mock
    private McpRegistry mcpRegistry;

    private HealthCheckService healthCheckService;

    private static final String APP_VERSION = "0.0.1-TEST";

    @BeforeEach
    void setUp() {
        healthCheckService = new HealthCheckService(
                databaseConnectionService,
                dataSource,
                APP_VERSION,
                mcpRegistry,
                llmPromptSender,
                appConfig
        );
    }

    // -------------------------------------------------------------------------
    // Test helpers
    // -------------------------------------------------------------------------

    private static ComponentHealthDto up() {
        return ComponentHealthDto.builder()
                .status(AppConstants.HealthStatus.UP.getValue())
                .message("Connected successfully")
                .latencyMs(5L)
                .build();
    }

    private static ComponentHealthDto down() {
        return ComponentHealthDto.builder()
                .status(AppConstants.HealthStatus.DOWN.getValue())
                .message("MCP server not available")
                .latencyMs(5L)
                .build();
    }

    private static McpSettings.ServerConfig serverConfig(boolean optional) {
        return new McpSettings.ServerConfig(
                "streamable-http",
                "http://example:8080/mcp",
                Map.of(),
                optional,
                new McpSettings.HealthCheckConfig("http://example:8080/healthz", 5000),
                5000,
                Map.of(),
                null,
                List.of());
    }

    private static McpClient mockClient(String name, boolean optional, ComponentHealthDto health) {
        McpClient client = mock(McpClient.class);
        lenient().when(client.getServerName()).thenReturn(name);
        lenient().when(client.getConfig()).thenReturn(serverConfig(optional));
        lenient().when(client.checkHealth()).thenReturn(health);
        return client;
    }

    /** Registry with no configured servers — isolates DB/LLM tests from MCP entirely. */
    private void mcpRegistryEmpty() {
        when(mcpRegistry.isInitialized()).thenReturn(true);
        when(mcpRegistry.allClients()).thenReturn(List.of());
    }

    // -------------------------------------------------------------------------
    // Database health
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Database Health Tests")
    class DatabaseHealthTests {

        @BeforeEach
        void mcpEmpty() {
            mcpRegistryEmpty();
        }

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

        @BeforeEach
        void mcpEmpty() {
            mcpRegistryEmpty();
        }

        @Test
        @DisplayName("UP — isReady true and send() returns non-empty response")
        void upWhenReadyAndResponds() {
            when(databaseConnectionService.isReady()).thenReturn(false);
            when(llmPromptSender.isReady()).thenReturn(true);
            when(appConfig.getLlmConfig()).thenReturn(llmConfigSnapshot);
            when(llmConfigSnapshot.getProvider()).thenReturn("bob");
            when(llmConfigSnapshot.getModelName()).thenReturn("bob");

            LLMResponse mockResponse = new LLMResponse("OK", "claude-sonnet-4-6", 11L, 4L, 0L, 0L, 100L);
            when(llmPromptSender.send(any(LLMRequest.class))).thenReturn(mockResponse);

            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

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
    // Dynamic MCP health (McpRegistry-driven)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("MCP Health Tests (dynamic, McpRegistry-driven)")
    class McpHealthTests {

        @Test
        @DisplayName("Each registered client produces an entry under mcp_config.servers")
        void eachClientProducesAComponent() {
            when(databaseConnectionService.isReady()).thenReturn(false);
            when(llmPromptSender.isReady()).thenReturn(false);
            List<McpClient> clients = List.of(
                    mockClient("kubernetes", false, up()),
                    mockClient("cryostat", true, down()));
            when(mcpRegistry.isInitialized()).thenReturn(true);
            when(mcpRegistry.allClients()).thenReturn(clients);

            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            ComponentHealthDto mcpConfig = response.getComponents().get(HealthCheckConstants.ComponentNames.MCP_CONFIG);
            assertNotNull(mcpConfig);
            assertNotNull(mcpConfig.getServers());
            assertEquals(AppConstants.HealthStatus.UP.getValue(), mcpConfig.getServers().get("kubernetes").getStatus());
            assertEquals(AppConstants.HealthStatus.DOWN.getValue(), mcpConfig.getServers().get("cryostat").getStatus());
        }

        @Test
        @DisplayName("mcp_config aggregates to UP when every server is up or only optional servers are down")
        void mcpConfigAggregatesToUpWhenInitialized() {
            when(databaseConnectionService.isReady()).thenReturn(false);
            when(llmPromptSender.isReady()).thenReturn(false);
            mcpRegistryEmpty();

            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            ComponentHealthDto mcpConfig = response.getComponents().get(HealthCheckConstants.ComponentNames.MCP_CONFIG);
            assertNotNull(mcpConfig);
            assertEquals(AppConstants.HealthStatus.UP.getValue(), mcpConfig.getStatus());
            assertTrue(mcpConfig.getServers().isEmpty());
        }

        @Test
        @DisplayName("mcp_config DOWN with the exact load-failure reason when the registry failed to initialize")
        void mcpConfigComponentWhenNotInitialized() {
            when(databaseConnectionService.isReady()).thenReturn(false);
            when(llmPromptSender.isReady()).thenReturn(false);
            when(mcpRegistry.isInitialized()).thenReturn(false);
            when(mcpRegistry.getInitializationError()).thenReturn(java.util.Optional.of("bad JSON at line 3"));

            HealthCheckResponseDto response = healthCheckService.getSystemHealth();

            ComponentHealthDto mcpConfig = response.getComponents().get(HealthCheckConstants.ComponentNames.MCP_CONFIG);
            assertNotNull(mcpConfig);
            assertEquals(AppConstants.HealthStatus.DOWN.getValue(), mcpConfig.getStatus());
            assertTrue(mcpConfig.getMessage().contains("bad JSON at line 3"));
            verify(mcpRegistry, never()).allClients();
        }
    }

    // -------------------------------------------------------------------------
    // Overall status aggregation
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("Overall Status Aggregation Tests")
    class OverallStatusTests {

        private void dbUp() throws Exception {
            when(databaseConnectionService.isReady()).thenReturn(true);
            when(dataSource.getConnection()).thenReturn(connection);
            when(connection.createStatement()).thenReturn(statement);
            when(statement.execute("SELECT 1")).thenReturn(true);
        }

        private void llmUp() {
            when(llmPromptSender.isReady()).thenReturn(true);
            when(appConfig.getLlmConfig()).thenReturn(llmConfigSnapshot);
            when(llmConfigSnapshot.getProvider()).thenReturn("bob");
            when(llmConfigSnapshot.getModelName()).thenReturn("bob");
            when(llmPromptSender.send(any(LLMRequest.class)))
                    .thenReturn(new LLMResponse("OK", "bob", 1L, 1L, 0L, 0L, 10L));
        }

        @Test
        @DisplayName("UP — database, LLM, and all MCP servers up")
        void upWhenEverythingUp() throws Exception {
            dbUp();
            llmUp();
            List<McpClient> clients = List.of(mockClient("kubernetes", false, up()));
            when(mcpRegistry.isInitialized()).thenReturn(true);
            when(mcpRegistry.allClients()).thenReturn(clients);

            assertEquals(AppConstants.HealthStatus.UP.getValue(),
                    healthCheckService.getSystemHealth().getStatus());
        }

        @Test
        @DisplayName("DOWN — database is down (regardless of everything else)")
        void downWhenDatabaseIsDown() {
            when(databaseConnectionService.isReady()).thenReturn(false);
            llmUp();
            mcpRegistryEmpty();

            assertEquals(AppConstants.HealthStatus.DOWN.getValue(),
                    healthCheckService.getSystemHealth().getStatus());
        }

        @Test
        @DisplayName("DOWN — a required (non-optional) MCP server is down")
        void downWhenRequiredMcpDown() throws Exception {
            dbUp();
            llmUp();
            List<McpClient> clients = List.of(mockClient("kubernetes", false, down()));
            when(mcpRegistry.isInitialized()).thenReturn(true);
            when(mcpRegistry.allClients()).thenReturn(clients);

            assertEquals(AppConstants.HealthStatus.DOWN.getValue(),
                    healthCheckService.getSystemHealth().getStatus());
        }

        @Test
        @DisplayName("UP — only an optional MCP server is down; it does not affect overall status")
        void upWhenOnlyOptionalMcpDown() throws Exception {
            dbUp();
            llmUp();
            List<McpClient> clients = List.of(
                    mockClient("kubernetes", false, up()),
                    mockClient("cryostat", true, down()));
            when(mcpRegistry.isInitialized()).thenReturn(true);
            when(mcpRegistry.allClients()).thenReturn(clients);

            assertEquals(AppConstants.HealthStatus.UP.getValue(),
                    healthCheckService.getSystemHealth().getStatus());
        }

        @Test
        @DisplayName("DEGRADED — database and MCP up, LLM down")
        void degradedWhenLlmDown() throws Exception {
            dbUp();
            when(llmPromptSender.isReady()).thenReturn(false);
            List<McpClient> clients = List.of(mockClient("kubernetes", false, up()));
            when(mcpRegistry.isInitialized()).thenReturn(true);
            when(mcpRegistry.allClients()).thenReturn(clients);

            assertEquals(AppConstants.HealthStatus.DEGRADED.getValue(),
                    healthCheckService.getSystemHealth().getStatus());
        }

        @Test
        @DisplayName("DOWN — MCP config failed to load, even though database and LLM are up")
        void downWhenMcpConfigFailedToLoad() throws Exception {
            dbUp();
            llmUp();
            when(mcpRegistry.isInitialized()).thenReturn(false);
            when(mcpRegistry.getInitializationError()).thenReturn(java.util.Optional.empty());

            assertEquals(AppConstants.HealthStatus.DOWN.getValue(),
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
            mcpRegistryEmpty();
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
        @DisplayName("Successive calls produce different timestamps")
        void successiveCallsProduceDifferentTimestamps() {
            String ts1 = healthCheckService.getSystemHealth().getTimestamp();
            String ts2 = healthCheckService.getSystemHealth().getTimestamp();
            assertNotNull(ts1);
            assertNotNull(ts2);
            assertDoesNotThrow(() -> java.time.Instant.parse(ts1));
            assertDoesNotThrow(() -> java.time.Instant.parse(ts2));
        }
    }
}
