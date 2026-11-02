package com.causa.notification.jira;

import com.causa.common.constants.ConfigConstants.PlatformCategory;
import com.causa.common.constants.DiagnosticConstants.DiagnosticStatus;
import com.causa.common.exceptions.DiagnosticException;
import com.causa.core.domain.AuthConfig;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.ExternalConfig;
import com.causa.core.domain.JiraTicketOptions;
import com.causa.core.domain.JiraUser;
import com.causa.core.domain.RootCauseAnalysis;
import com.causa.core.domain.RootCauseAnalysis.Recommendation;
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
import java.util.List;
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
    private final AtomicReference<String> query = new AtomicReference<>();
    private final AtomicReference<String> authHeader = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String responseJson = "{\"key\":\"KRUIZE-1\"}";

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            query.set(exchange.getRequestURI().getRawQuery());
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
        String url = client.createTicket(config("API_TOKEN", "me@example.com"), diagnostic(), JiraTicketOptions.defaults());

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
        String url = client.createTicket(config("PAT", null), diagnostic(), JiraTicketOptions.defaults());

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
        client.createTicket(config(null, "me@example.com"), diagnostic(), JiraTicketOptions.defaults());

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
                () -> client.createTicket(config("PAT", null), diagnostic(), JiraTicketOptions.defaults()));

        assertEquals("JIRA_API_ERROR", ex.getErrorType());
    }

    @Test
    @DisplayName("Cloud config without username is rejected")
    void cloudRequiresUsername() {
        DiagnosticException ex = assertThrows(DiagnosticException.class,
                () -> client.createTicket(config("API_TOKEN", null), diagnostic(), JiraTicketOptions.defaults()));

        assertEquals("JIRA_CONFIG_INCOMPLETE", ex.getErrorType());
    }

    @Test
    @DisplayName("Cloud user search sends 'query' and maps accountId")
    void cloudUserSearch() {
        responseJson = "[{\"accountId\":\"abc\",\"displayName\":\"Ann Lee\",\"emailAddress\":\"ann@x.com\"}]";

        List<JiraUser> users = client.searchAssignableUsers(config("API_TOKEN", "me@example.com"), "ann lee");

        assertEquals(List.of(new JiraUser("abc", "Ann Lee", "ann@x.com")), users);
        assertEquals("/rest/api/3/user/assignable/search", path.get());
        assertEquals("project=KRUIZE&query=ann+lee&maxResults=20", query.get());
    }

    @Test
    @DisplayName("Server user search sends 'username' and maps name; blank query lists everyone")
    void serverUserSearch() {
        responseJson = "[{\"name\":\"alee\",\"displayName\":\"Ann Lee\"}]";

        List<JiraUser> users = client.searchAssignableUsers(config("PAT", null), null);

        assertEquals(List.of(new JiraUser("alee", "Ann Lee", null)), users);
        assertEquals("/rest/api/2/user/assignable/search", path.get());
        assertEquals("project=KRUIZE&username=&maxResults=20", query.get());
        assertEquals("Bearer tok", authHeader.get());
    }

    @Test
    @DisplayName("HTTP failures carry a specific, actionable message")
    void failureMessages() {
        status = 401;
        assertTrue(assertThrows(DiagnosticException.class,
                () -> client.searchAssignableUsers(config("PAT", null), "a")).getMessage().contains("credentials"));

        status = 404;
        assertTrue(assertThrows(DiagnosticException.class,
                () -> client.searchAssignableUsers(config("PAT", null), "a")).getMessage().contains("projectName"));
    }

    private static Diagnostic diagnosticWithRca() {
        RootCauseAnalysis rca = new RootCauseAnalysis("Title", "Summary text", "Issue description", null, null,
                "Root cause text", null, null,
                List.of(new Recommendation("Root Cause Fix", "Raise limit", "Bump memory to 2Gi", "kubectl set resources", 0.9, null)),
                null, null);
        return Diagnostic.builder()
                .diagnosticId("diag-1").alertId("alrt-1")
                .status(DiagnosticStatus.COMPLETED).generatedAt(Instant.now()).rca(rca).build();
    }

    @Test
    @DisplayName("Default options keep the description as the root cause only and set no assignee")
    void defaultDescriptionIsRootCauseOnly() throws Exception {
        client.createTicket(config("PAT", null), diagnosticWithRca(), JiraTicketOptions.defaults());

        JsonNode fields = MAPPER.readTree(body.get()).path("fields");
        assertEquals("Root cause text", fields.path("description").asText());
        assertTrue(fields.path("assignee").isMissingNode());
    }

    @Test
    @DisplayName("Server: selected sections, notes and assignee name are included")
    void serverIncludesSelectedSections() throws Exception {
        client.createTicket(config("PAT", null), diagnosticWithRca(),
                new JiraTicketOptions("alee", true, true, true, "please look today"));

        JsonNode fields = MAPPER.readTree(body.get()).path("fields");
        String description = fields.path("description").asText();
        assertTrue(description.contains("h3. Issue detail\nSummary text\nIssue description"));
        assertTrue(description.contains("h3. Recommendations\nRaise limit - Bump memory to 2Gi"));
        assertTrue(description.contains("h3. Implementation steps\nRaise limit: kubectl set resources"));
        assertTrue(description.contains("h3. Additional notes\nplease look today"));
        assertEquals("alee", fields.path("assignee").path("name").asText());
    }

    @Test
    @DisplayName("Cloud: sections become ADF headings and the assignee uses accountId")
    void cloudIncludesSelectedSections() throws Exception {
        client.createTicket(config("API_TOKEN", "me@example.com"), diagnosticWithRca(),
                new JiraTicketOptions("abc", true, false, false, null));

        JsonNode fields = MAPPER.readTree(body.get()).path("fields");
        JsonNode content = fields.path("description").path("content");
        assertEquals("paragraph", content.get(0).path("type").asText());
        assertEquals("heading", content.get(1).path("type").asText());
        assertEquals("Recommendations", content.get(1).path("content").get(0).path("text").asText());
        assertEquals("abc", fields.path("assignee").path("accountId").asText());
    }

    private static String errorType(Runnable call) {
        return assertThrows(DiagnosticException.class, call::run).getErrorType();
    }

    private ExternalConfig configWith(String url, String token, Map<String, Object> additional) {
        return ExternalConfig.builder()
                .id("cfg-1").name("jira").category(PlatformCategory.INTEGRATION).platform("JIRA")
                .url(url)
                .authConfig(new AuthConfig("PAT", null, null, token, null, null, null, null))
                .additionalConfig(additional)
                .build();
    }

    @Test
    @DisplayName("Incomplete config is rejected: missing url, token or projectName")
    void incompleteConfigIsRejected() {
        Map<String, Object> project = Map.of("projectName", "KRUIZE");

        assertEquals("JIRA_CONFIG_INCOMPLETE", errorType(
                () -> client.createTicket(configWith(null, "tok", project), diagnostic(), JiraTicketOptions.defaults())));
        assertEquals("JIRA_CONFIG_INCOMPLETE", errorType(
                () -> client.createTicket(configWith(baseUrl, " ", project), diagnostic(), JiraTicketOptions.defaults())));
        assertEquals("JIRA_CONFIG_INCOMPLETE", errorType(
                () -> client.createTicket(configWith(baseUrl, "tok", Map.of()), diagnostic(), JiraTicketOptions.defaults())));
        assertEquals("JIRA_CONFIG_INCOMPLETE", errorType(
                () -> client.createTicket(configWith(baseUrl, "tok", null), diagnostic(), JiraTicketOptions.defaults())));
    }

    @Test
    @DisplayName("403, 400 and other statuses each get their own message")
    void statusSpecificMessages() {
        status = 403;
        assertTrue(assertThrows(DiagnosticException.class,
                () -> client.searchAssignableUsers(config("PAT", null), "a")).getMessage().contains("not allowed"));

        status = 400;
        assertTrue(assertThrows(DiagnosticException.class,
                () -> client.createTicket(config("PAT", null), diagnostic(), JiraTicketOptions.defaults()))
                .getMessage().contains("required fields"));

        status = 500;
        assertTrue(assertThrows(DiagnosticException.class,
                () -> client.searchAssignableUsers(config("PAT", null), "a")).getMessage().contains("HTTP 500"));
    }

    @Test
    @DisplayName("Unreachable Jira raises JIRA_API_ERROR")
    void unreachableJira() {
        ExternalConfig cfg = config("PAT", null);
        server.stop(0);

        assertEquals("JIRA_API_ERROR", errorType(() -> client.searchAssignableUsers(cfg, "a")));
    }

    @Test
    @DisplayName("A create response without an issue key, or with invalid JSON, raises JIRA_API_ERROR")
    void badCreateResponse() {
        responseJson = "{}";
        assertEquals("JIRA_API_ERROR", errorType(
                () -> client.createTicket(config("PAT", null), diagnostic(), JiraTicketOptions.defaults())));

        responseJson = "not json";
        assertEquals("JIRA_API_ERROR", errorType(
                () -> client.createTicket(config("PAT", null), diagnostic(), JiraTicketOptions.defaults())));
    }

    @Test
    @DisplayName("Search treats a missing issues array or a blank key as 'not found' and invalid JSON as an error")
    void searchEdgeCases() {
        responseJson = "{}";
        assertTrue(client.findTicketByAlertId(config("PAT", null), "a").isEmpty());

        responseJson = "{\"issues\":[{\"key\":\"\"}]}";
        assertTrue(client.findTicketByAlertId(config("PAT", null), "a").isEmpty());

        responseJson = "not json";
        assertEquals("JIRA_API_ERROR", errorType(() -> client.findTicketByAlertId(config("PAT", null), "a")));
    }

    @Test
    @DisplayName("User search skips entries without an id and rejects invalid JSON")
    void userSearchEdgeCases() {
        responseJson = "[{\"displayName\":\"No Id\"},{\"name\":\"bob\",\"displayName\":\"Bob\"}]";
        assertEquals(List.of(new JiraUser("bob", "Bob", null)), client.searchAssignableUsers(config("PAT", null), "b"));

        responseJson = "not json";
        assertEquals("JIRA_API_ERROR", errorType(() -> client.searchAssignableUsers(config("PAT", null), "b")));
    }

    @Test
    @DisplayName("Without an RCA the summary falls back to the diagnostic id, and selected sections add nothing")
    void noRcaFallsBack() throws Exception {
        client.createTicket(config("PAT", null), diagnostic(), new JiraTicketOptions(" ", true, true, true, " "));

        JsonNode fields = MAPPER.readTree(body.get()).path("fields");
        assertEquals("Causa diagnostic: diag-1", fields.path("summary").asText());
        assertEquals("See Causa diagnostic: diag-1", fields.path("description").asText());
        assertTrue(fields.path("assignee").isMissingNode());
    }

    @Test
    @DisplayName("Recommendations without implementation notes are left out of the implementation steps")
    void blankImplementationNotesSkipped() throws Exception {
        RootCauseAnalysis rca = new RootCauseAnalysis("T", null, null, null, null, "cause", null, null,
                List.of(new Recommendation("Fix", "Only title", null, null, 0.5, null)), null, null);
        Diagnostic d = Diagnostic.builder().diagnosticId("diag-1").alertId("alrt-1")
                .status(DiagnosticStatus.COMPLETED).generatedAt(Instant.now()).rca(rca).build();

        client.createTicket(config("PAT", null), d, new JiraTicketOptions(null, true, true, true, null));

        String description = MAPPER.readTree(body.get()).path("fields").path("description").asText();
        assertTrue(description.contains("h3. Recommendations\nOnly title"));
        assertFalse(description.contains("Implementation steps"));
        assertFalse(description.contains("Issue detail"));
    }
}
