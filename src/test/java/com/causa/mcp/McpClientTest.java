package com.causa.mcp;

import com.causa.api.dto.ComponentHealthDto;
import com.causa.common.constants.AppConstants;
import com.causa.mcp.config.McpSettings;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link McpClient#checkHealth()}.
 *
 * <p>Uses a real (JDK built-in) {@link HttpServer} instead of mocking {@code java.net.http.HttpClient}
 * directly, so the actual HTTP round trip — status-code handling, timeouts, connection failure — is
 * exercised end to end.
 *
 * @since 0.0.1
 */
@DisplayName("McpClient Tests")
class McpClientTest {

    private static HttpServer server;
    private static int port;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/healthy", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.createContext("/broken", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    private static McpClient clientFor(String path, int timeoutMs) {
        McpSettings.ServerConfig config = new McpSettings.ServerConfig(
                "streamable-http",
                "http://localhost:" + port + "/mcp",
                Map.of(),
                false,
                new McpSettings.HealthCheckConfig("http://localhost:" + port + path, timeoutMs),
                5000,
                Map.of(),
                null,
                List.of());
        return new McpClient("test-server", config);
    }

    @Test
    @DisplayName("UP — 2xx response")
    void upOn2xxResponse() {
        ComponentHealthDto health = clientFor("/healthy", 5000).checkHealth();

        assertEquals(AppConstants.HealthStatus.UP.getValue(), health.getStatus());
        assertNotNull(health.getLatencyMs());
        assertTrue(health.getLatencyMs() >= 0);
    }

    @Test
    @DisplayName("DOWN — non-2xx response")
    void downOnNon2xxResponse() {
        ComponentHealthDto health = clientFor("/broken", 5000).checkHealth();

        assertEquals(AppConstants.HealthStatus.DOWN.getValue(), health.getStatus());
    }

    @Test
    @DisplayName("DOWN — connection refused (nothing listening on the port)")
    void downOnConnectionFailure() throws IOException {
        int deadPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            deadPort = socket.getLocalPort();
        }
        McpSettings.ServerConfig config = new McpSettings.ServerConfig(
                "streamable-http", "http://localhost:" + deadPort + "/mcp", Map.of(), false,
                new McpSettings.HealthCheckConfig("http://localhost:" + deadPort + "/healthz", 2000),
                5000, Map.of(), null, List.of());

        ComponentHealthDto health = new McpClient("test-server", config).checkHealth();

        assertEquals(AppConstants.HealthStatus.DOWN.getValue(), health.getStatus());
    }

    @Test
    @DisplayName("DOWN — malformed health-check URL (URI.create() throws IllegalArgumentException)")
    void downOnMalformedUrl() {
        McpSettings.ServerConfig config = new McpSettings.ServerConfig(
                "streamable-http", "http://example:8080/mcp", Map.of(), false,
                new McpSettings.HealthCheckConfig("http://bad host with spaces/healthz", 2000),
                5000, Map.of(), null, List.of());

        ComponentHealthDto health = new McpClient("test-server", config).checkHealth();

        assertEquals(AppConstants.HealthStatus.DOWN.getValue(), health.getStatus());
    }

    @Test
    @DisplayName("DOWN — request timeout")
    void downOnTimeout() {
        ComponentHealthDto health = clientFor("/slow", 200).checkHealth();

        assertEquals(AppConstants.HealthStatus.DOWN.getValue(), health.getStatus());
    }

    @Test
    @DisplayName("getServerName/getConfig expose constructor values")
    void exposesServerNameAndConfig() {
        McpClient client = clientFor("/healthy", 5000);

        assertEquals("test-server", client.getServerName());
        assertNotNull(client.getConfig());
        assertFalse(client.getConfig().optional());
    }
}