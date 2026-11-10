package com.causa.notification.jira;

import com.causa.common.constants.ConfigConstants.PlatformCategory;
import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import com.causa.common.exceptions.DiagnosticException;
import com.causa.core.domain.AuthConfig;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.ExternalConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JiraClient Tests")
class JiraClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final JiraClient client = new JiraClient();

    private HttpServer server;
    private String baseUrl;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> authHeader = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseJson = "{\"key\":\"KRUIZE-1\"}";

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, out.length);
            exchange.getResponseBody().write(out);
            exchange.close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private ExternalConfig config(String authType, String username) {
        return ExternalConfig.builder()
                .id("cfg-1")
                .name("jira")
                .category(PlatformCategory.INTEGRATION)
                .platform("JIRA")
                .url(baseUrl)
                .authConfig(new AuthConfig(authType, null, null, "tok", null, null, username, null))
                .additionalConfig(Map.of("projectName", "KRUIZE"))
                .build();
    }

    private static Diagnostic diagnostic() {
        return Diagnostic.builder()
                .diagnosticId("diag-1")
                .alertId("alrt 1")
                .status(DiagnosticStatus.COMPLETED)
                .generatedAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("Cloud (API_TOKEN): Basic auth, REST v3, ADF description, Task type, alert label")
    void cloudCreatesTicketWithV3() throws Exception {
        String url = client.createTicket(config("API_TOKEN", "me@example.com"), diagnostic());

        assertEquals(baseUrl + "/browse/KRUIZE-1", url);
        assertEquals("/rest/api/3/issue", path.get());
        assertEquals("Basic " + Base64.getEncoder()
                .encodeToString("me@example.com:tok".getBytes(StandardCharsets.UTF_8)), authHeader.get());

        JsonNode fields = MAPPER.readTree(body.get()).path("fields");
        assertEquals("Task", fields.path("issuetype").path("name").asText());
        assertEquals("causa-alert-alrt_1", fields.path("labels").get(0).asText());
        assertEquals("doc", fields.path("description").path("type").asText());
    }

    @Test
    @DisplayName("Server (PAT): Bearer auth, REST v2, plain-text description, no username needed")
    void serverCreatesTicketWithV2() throws Exception {
        String url = client.createTicket(config("PAT", null), diagnostic());

        assertEquals(baseUrl + "/browse/KRUIZE-1", url);
        assertEquals("/rest/api/2/issue", path.get());
        assertEquals("Bearer tok", authHeader.get());

        JsonNode fields = MAPPER.readTree(body.get()).path("fields");
        assertEquals("Task", fields.path("issuetype").path("name").asText());
        assertTrue(fields.path("description").isTextual());
    }

    @Test
    @DisplayName("Missing authType falls back to Cloud")
    void missingAuthTypeIsCloud() {
        client.createTicket(config(null, "me@example.com"), diagnostic());

        assertEquals("/rest/api/3/issue", path.get());
        assertTrue(authHeader.get().startsWith("Basic "));
    }

    @Test
    @DisplayName("Cloud search uses /rest/api/3/search/jql and returns the browse URL")
    void cloudSearchFindsTicket() throws Exception {
        responseJson = "{\"issues\":[{\"key\":\"KRUIZE-7\"}]}";

        Optional<String> found = client.findTicketByAlertId(config("API_TOKEN", "me@example.com"), "alrt 1");

        assertEquals(Optional.of(baseUrl + "/browse/KRUIZE-7"), found);
        assertEquals("/rest/api/3/search/jql", path.get());
        assertTrue(MAPPER.readTree(body.get()).path("jql").asText().contains("labels = \"causa-alert-alrt_1\""));
    }

    @Test
    @DisplayName("Server search uses /rest/api/2/search with Bearer auth")
    void serverSearchUsesV2() {
        responseJson = "{\"issues\":[]}";

        Optional<String> found = client.findTicketByAlertId(config("PAT", null), "alrt-1");

        assertTrue(found.isEmpty());
        assertEquals("/rest/api/2/search", path.get());
        assertEquals("Bearer tok", authHeader.get());
    }

    @Test
    @DisplayName("Non-2xx response raises JIRA_API_ERROR")
    void nonSuccessRaisesApiError() {
        status = 401;
        responseJson = "unauthorized";

        DiagnosticException ex = assertThrows(DiagnosticException.class,
                () -> client.createTicket(config("PAT", null), diagnostic()));

        assertEquals("JIRA_API_ERROR", ex.getErrorType());
    }

    @Test
    @DisplayName("Trailing slashes on the base URL are stripped from API and browse URLs")
    void trailingSlashStripped() {
        ExternalConfig cfg = ExternalConfig.builder()
                .id("cfg-1").name("jira").category(PlatformCategory.INTEGRATION).platform("JIRA")
                .url(baseUrl + "//")
                .authConfig(new AuthConfig("PAT", null, null, "tok", null, null, null, null))
                .additionalConfig(Map.of("projectName", "KRUIZE"))
                .build();

        String url = client.createTicket(cfg, diagnostic());

        assertEquals(baseUrl + "/browse/KRUIZE-1", url);
        assertEquals("/rest/api/2/issue", path.get());
    }

    @Test
    @DisplayName("additional_config.issueType overrides the default issue type")
    void configuredIssueTypeIsUsed() throws Exception {
        ExternalConfig cfg = ExternalConfig.builder()
                .id("cfg-1").name("jira").category(PlatformCategory.INTEGRATION).platform("JIRA")
                .url(baseUrl)
                .authConfig(new AuthConfig("PAT", null, null, "tok", null, null, null, null))
                .additionalConfig(Map.of("projectName", "KRUIZE", "issueType", "Bug"))
                .build();

        client.createTicket(cfg, diagnostic());

        assertEquals("Bug", MAPPER.readTree(body.get()).path("fields").path("issuetype").path("name").asText());
    }

    @Test
    @DisplayName("Non-HTTPS, non-loopback base URL is rejected before any request is sent")
    void nonHttpsRemoteUrlRejected() {
        ExternalConfig cfg = ExternalConfig.builder()
                .id("cfg-1").name("jira").category(PlatformCategory.INTEGRATION).platform("JIRA")
                .url("http://jira.example.com")
                .authConfig(new AuthConfig("PAT", null, null, "tok", null, null, null, null))
                .additionalConfig(Map.of("projectName", "KRUIZE"))
                .build();

        DiagnosticException ex = assertThrows(DiagnosticException.class,
                () -> client.createTicket(cfg, diagnostic()));

        assertEquals("JIRA_CONFIG_INVALID", ex.getErrorType());
        assertNull(path.get());
    }

    @Test
    @DisplayName("Project name that is not a valid Jira project key is rejected before any request is sent")
    void invalidProjectKeyRejected() {
        ExternalConfig cfg = ExternalConfig.builder()
                .id("cfg-1").name("jira").category(PlatformCategory.INTEGRATION).platform("JIRA")
                .url(baseUrl)
                .authConfig(new AuthConfig("PAT", null, null, "tok", null, null, null, null))
                .additionalConfig(Map.of("projectName", "OPS\" OR project = \"SECRET"))
                .build();

        DiagnosticException ex = assertThrows(DiagnosticException.class,
                () -> client.findTicketByAlertId(cfg, "alrt 1"));

        assertEquals("JIRA_CONFIG_INVALID", ex.getErrorType());
        assertNull(path.get());
    }

    @Test
    @DisplayName("Cloud config without username is rejected")
    void cloudRequiresUsername() {
        DiagnosticException ex = assertThrows(DiagnosticException.class,
                () -> client.createTicket(config("API_TOKEN", null), diagnostic()));

        assertEquals("JIRA_CONFIG_INCOMPLETE", ex.getErrorType());
    }
}
