package com.causa.notification.jira;

import com.causa.common.constants.ConfigConstants.ExternalAuthType;
import com.causa.common.exceptions.DiagnosticException;
import com.causa.common.logging.CausaLogger;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.ExternalConfig;
import com.causa.core.domain.JiraTicketOptions;
import com.causa.core.domain.JiraUser;
import com.causa.core.domain.RootCauseAnalysis;
import com.causa.core.domain.RootCauseAnalysis.Recommendation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Jira REST client (outbound adapter). Stateless: duplicate prevention is the caller's job.
 *
 * <p>The supplied {@link ExternalConfig} must carry decrypted credentials, as served by
 * {@code ExternalConfigCache}. The base URL must be HTTPS (plain HTTP only for loopback hosts),
 * so credentials are never sent unencrypted over the network.
 *
 * <p>The deployment flavour follows {@code auth_config.authType}:
 * {@code PAT} = Server/Data Center (Bearer token, REST v2, plain-text description);
 * anything else (e.g. {@code API_TOKEN}) = Atlassian Cloud (Basic username:token, REST v3, ADF description).
 *
 * @since 0.0.5
 */
@ApplicationScoped
public class JiraClient {

    private static final CausaLogger log = CausaLogger.getLogger(JiraClient.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration     TIMEOUT = Duration.ofSeconds(10);
    private static final String       ALERT_LABEL_PREFIX = "causa-alert-";
    private static final String       DEFAULT_ISSUE_TYPE = "Task";
    private static final Pattern      PROJECT_KEY_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,254}");
    private static final int          USER_SEARCH_LIMIT = 20;

    private enum Flavor {
        CLOUD("/rest/api/3", "/search/jql", "query", "accountId"),
        SERVER("/rest/api/2", "/search", "username", "name");

        final String apiPath;
        final String searchPath;
        final String userQueryParam;
        final String userIdField;

        Flavor(String apiPath, String searchPath, String userQueryParam, String userIdField) {
            this.apiPath = apiPath;
            this.searchPath = searchPath;
            this.userQueryParam = userQueryParam;
            this.userIdField = userIdField;
        }

        static Flavor of(ExternalConfig config) {
            return config.getAuthConfig() != null
                && ExternalAuthType.PAT.name().equalsIgnoreCase(config.getAuthConfig().authType())
                ? SERVER : CLOUD;
        }
    }

    private final HttpClient httpClient;

    public JiraClient() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Creates an issue labelled with the alert id and returns its browse URL,
     * e.g. {@code https://acme.atlassian.net/browse/OPS-123}.
     *
     * @throws DiagnosticException if the config is incomplete or the Jira call fails
     */
    public String createTicket(ExternalConfig jiraConfig, Diagnostic diagnostic, JiraTicketOptions options) {
        Flavor flavor     = Flavor.of(jiraConfig);
        String baseUrl    = resolveBaseUrl(jiraConfig);
        String authHeader = buildAuthHeader(jiraConfig, flavor);
        String projectKey = resolveProjectKey(jiraConfig);

        String requestBody = buildRequestBody(
            projectKey, resolveIssueType(jiraConfig), diagnostic, options, flavor);

        log.info("Creating Jira ticket")
            .field("project", projectKey)
            .field("diagnosticId", diagnostic.getDiagnosticId())
            .log();

        String responseBody = post(baseUrl + flavor.apiPath + "/issue", authHeader, requestBody);
        return extractBrowseUrl(baseUrl, responseBody);
    }

    /**
     * Finds an issue previously created for the alert (by label), so a ticket whose URL was
     * never persisted is reused instead of duplicated.
     */
    public Optional<String> findTicketByAlertId(ExternalConfig jiraConfig, String alertId) {
        Flavor flavor     = Flavor.of(jiraConfig);
        String baseUrl    = resolveBaseUrl(jiraConfig);
        String authHeader = buildAuthHeader(jiraConfig, flavor);
        String projectKey = resolveProjectKey(jiraConfig);

        ObjectNode body = MAPPER.createObjectNode();
        body.put("jql", "project = \"" + projectKey + "\" AND labels = \"" + alertLabel(alertId) + "\"");
        body.put("maxResults", 1);
        body.putArray("fields").add("key");

        String responseBody = post(baseUrl + flavor.apiPath + flavor.searchPath, authHeader, body.toString());
        try {
            JsonNode issues = MAPPER.readTree(responseBody).path("issues");
            if (!issues.isArray() || issues.isEmpty()) {
                return Optional.empty();
            }
            String issueKey = issues.get(0).path("key").asText();
            return issueKey.isBlank()
                ? Optional.empty()
                : Optional.of(baseUrl + "/browse/" + issueKey);
        } catch (Exception e) {
            throw new DiagnosticException(
                "Failed to parse Jira search response: " + e.getMessage(), "JIRA_API_ERROR", e);
        }
    }

    /**
     * Returns users assignable in the configured project whose name or email matches {@code query}
     * (all assignable users when blank), capped at {@value #USER_SEARCH_LIMIT}.
     */
    public List<JiraUser> searchAssignableUsers(ExternalConfig jiraConfig, String query) {
        Flavor flavor     = Flavor.of(jiraConfig);
        String baseUrl    = resolveBaseUrl(jiraConfig);
        String authHeader = buildAuthHeader(jiraConfig, flavor);
        String projectKey = resolveProjectKey(jiraConfig);

        String url = baseUrl.stripTrailing() + flavor.apiPath + "/user/assignable/search"
            + "?project=" + encode(projectKey)
            + "&" + flavor.userQueryParam + "=" + encode(query == null ? "" : query.trim())
            + "&maxResults=" + USER_SEARCH_LIMIT;

        String responseBody = send(HttpRequest.newBuilder().uri(URI.create(url)).GET(), authHeader);
        try {
            List<JiraUser> users = new ArrayList<>();
            for (JsonNode node : MAPPER.readTree(responseBody)) {
                String id = node.path(flavor.userIdField).asText();
                if (!id.isBlank()) {
                    users.add(new JiraUser(id, node.path("displayName").asText(), node.path("emailAddress").asText(null)));
                }
            }
            return users;
        } catch (Exception e) {
            throw new DiagnosticException(
                "Failed to parse Jira user search response: " + e.getMessage(), "JIRA_API_ERROR", e);
        }
    }

    /** Label put on every ticket; Jira labels cannot contain whitespace, so other characters are replaced. */
    private static String alertLabel(String alertId) {
        return ALERT_LABEL_PREFIX + alertId.replaceAll("[^A-Za-z0-9_.-]", "_");
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String post(String apiUrl, String authHeader, String requestBody) {
        return send(HttpRequest.newBuilder()
            .uri(URI.create(apiUrl))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8)), authHeader);
    }

    private String send(HttpRequest.Builder builder, String authHeader) {
        try {
            HttpRequest request = builder
                .header("Authorization", authHeader)
                .header("Accept", "application/json")
                .timeout(TIMEOUT)
                .build();

            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Jira API returned non-2xx status")
                    .field("status", response.statusCode())
                    .field("body", truncate(response.body(), 300))
                    .log();
                throw new DiagnosticException(describeFailure(response.statusCode()), "JIRA_API_ERROR");
            }
            return response.body();

        } catch (DiagnosticException de) {
            throw de;
        } catch (Exception e) {
            throw new DiagnosticException("Could not reach Jira: " + e.getMessage(), "JIRA_API_ERROR", e);
        }
    }

    private static String describeFailure(int status) {
        return switch (status) {
            case 401 -> "Jira rejected the credentials (HTTP 401). Check auth_config.authType "
                + "(PAT for Server/Data Center, API_TOKEN for Cloud), username and token.";
            case 403 -> "The Jira account is not allowed to perform this action (HTTP 403).";
            case 404 -> "Jira resource not found (HTTP 404). Check the Jira url and additional_config.projectName.";
            case 400 -> "Jira rejected the request (HTTP 400). Check the project, issue type and required fields.";
            default -> "Jira API error: HTTP " + status;
        };
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Returns the configured base URL without trailing slashes. Rejects non-HTTPS URLs so the
     * auth header is never sent in clear text; plain HTTP is tolerated only for loopback hosts.
     */
    private static String resolveBaseUrl(ExternalConfig config) {
        String url = config.getUrl();
        if (url == null || url.isBlank()) {
            throw new DiagnosticException(
                "Jira config is missing a base URL. "
                + "Set the 'url' field via PUT /api/v1/configs/integrations/JIRA.",
                "JIRA_CONFIG_INCOMPLETE");
        }
        String normalized = url.strip().replaceAll("/+$", "");

        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException e) {
            throw new DiagnosticException("Jira base URL is not a valid URL.", "JIRA_CONFIG_INVALID");
        }
        boolean https    = "https".equalsIgnoreCase(uri.getScheme());
        boolean loopback = "http".equalsIgnoreCase(uri.getScheme()) && isLoopbackHost(uri.getHost());
        if (!https && !loopback) {
            throw new DiagnosticException(
                "Jira base URL must use https:// so credentials are not sent unencrypted.",
                "JIRA_CONFIG_INVALID");
        }
        return normalized;
    }

    private static boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)
            || "[::1]".equals(host) || "::1".equals(host);
    }

    private static String buildAuthHeader(ExternalConfig config, Flavor flavor) {
        String username = config.getAuthConfig() != null ? config.getAuthConfig().username() : null;
        String token    = config.getAuthConfig() != null ? config.getAuthConfig().token()    : null;
        if (flavor == Flavor.SERVER) {
            if (isBlank(token)) {
                throw new DiagnosticException(
                    "Jira config is missing auth_config.token.", "JIRA_CONFIG_INCOMPLETE");
            }
            return "Bearer " + token;
        }
        if (isBlank(username) || isBlank(token)) {
            throw new DiagnosticException(
                "Jira config is missing auth_config.username or auth_config.token.",
                "JIRA_CONFIG_INCOMPLETE");
        }
        return "Basic " + Base64.getEncoder().encodeToString(
            (username + ":" + token).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Returns the Jira project key. It is interpolated into a JQL query, so it must match Jira's
     * project-key syntax (letters, digits, underscore; starts with a letter) — anything else,
     * e.g. quotes or JQL operators, is rejected.
     */
    private static String resolveProjectKey(ExternalConfig config) {
        Object key = config.getAdditionalConfig() != null
            ? config.getAdditionalConfig().get("projectName")
            : null;
        if (key == null || key.toString().isBlank()) {
            throw new DiagnosticException(
                "Jira config is missing additional_config.projectName.",
                "JIRA_CONFIG_INCOMPLETE");
        }
        String projectKey = key.toString().strip();
        if (!PROJECT_KEY_PATTERN.matcher(projectKey).matches()) {
            throw new DiagnosticException(
                "additional_config.projectName is not a valid Jira project key.",
                "JIRA_CONFIG_INVALID");
        }
        return projectKey;
    }

    /** Issue type from {@code additional_config.issueType}, defaulting to {@code Task}. */
    private static String resolveIssueType(ExternalConfig config) {
        Object type = config.getAdditionalConfig() != null
            ? config.getAdditionalConfig().get("issueType")
            : null;
        return type == null || type.toString().isBlank() ? DEFAULT_ISSUE_TYPE : type.toString().strip();
    }

    /** One block of the description; {@code heading} is null for the opening block. */
    private record Section(String heading, List<String> lines) {}

    /** Issue payload; Cloud needs the description as Atlassian Document Format, Server as wiki-markup text. */
    private static String buildRequestBody(String projectKey, String issueType, Diagnostic diagnostic,
                                           JiraTicketOptions options, Flavor flavor) {
        try {
            RootCauseAnalysis rca = diagnostic.getRca();
            String summary = rca != null && !isBlank(rca.issueTitle())
                ? rca.issueTitle()
                : "Causa diagnostic: " + diagnostic.getDiagnosticId();

            ObjectNode body   = MAPPER.createObjectNode();
            ObjectNode fields = body.putObject("fields");
            fields.putObject("project").put("key", projectKey);
            fields.put("summary", summary);
            fields.putArray("labels").add(alertLabel(diagnostic.getAlertId()));
            fields.putObject("issuetype").put("name", issueType);

            List<Section> sections = descriptionSections(diagnostic, options);
            if (flavor == Flavor.CLOUD) {
                fields.set("description", adfDocument(sections));
            } else {
                fields.put("description", wikiText(sections));
            }
            if (!isBlank(options.assigneeId())) {
                fields.putObject("assignee").put(flavor.userIdField, options.assigneeId().trim());
            }

            return MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            throw new DiagnosticException("Failed to build Jira request body: " + e.getMessage(),
                "JIRA_PAYLOAD_ERROR", e);
        }
    }

    private static List<Section> descriptionSections(Diagnostic diagnostic, JiraTicketOptions options) {
        RootCauseAnalysis rca = diagnostic.getRca();
        List<Section> sections = new ArrayList<>();

        String rootCause = rca != null && !isBlank(rca.rootCause())
            ? rca.rootCause()
            : "See Causa diagnostic: " + diagnostic.getDiagnosticId();
        sections.add(new Section(null, List.of(rootCause)));

        if (rca != null && options.includeIssueDetail()) {
            addSection(sections, "Issue detail", nonBlank(rca.issueSummary(), rca.issueDescription()));
        }
        if (rca != null && options.includeRecommendations() && rca.recommendations() != null) {
            List<String> lines = new ArrayList<>();
            for (Recommendation r : rca.recommendations()) {
                lines.add(joinNonBlank(" - ", r.solutionTitle(), r.solutionDescription()));
            }
            addSection(sections, "Recommendations", lines);
        }
        if (rca != null && options.includeImplementationSteps() && rca.recommendations() != null) {
            List<String> lines = new ArrayList<>();
            for (Recommendation r : rca.recommendations()) {
                if (!isBlank(r.implementationNotes())) {
                    lines.add(joinNonBlank(": ", r.solutionTitle(), r.implementationNotes()));
                }
            }
            addSection(sections, "Implementation steps", lines);
        }
        if (!isBlank(options.additionalNotes())) {
            addSection(sections, "Additional notes", List.of(options.additionalNotes().trim()));
        }
        return sections;
    }

    private static void addSection(List<Section> sections, String heading, List<String> lines) {
        List<String> kept = lines.stream().filter(l -> !isBlank(l)).toList();
        if (!kept.isEmpty()) {
            sections.add(new Section(heading, kept));
        }
    }

    private static List<String> nonBlank(String... values) {
        List<String> out = new ArrayList<>();
        for (String v : values) {
            if (!isBlank(v)) out.add(v);
        }
        return out;
    }

    private static String joinNonBlank(String separator, String first, String second) {
        return String.join(separator, nonBlank(first, second));
    }

    private static String wikiText(List<Section> sections) {
        StringBuilder sb = new StringBuilder();
        for (Section s : sections) {
            if (sb.length() > 0) sb.append("\n\n");
            if (s.heading() != null) sb.append("h3. ").append(s.heading()).append("\n");
            sb.append(String.join("\n", s.lines()));
        }
        return sb.toString();
    }

    private static ObjectNode adfDocument(List<Section> sections) {
        ObjectNode doc = MAPPER.createObjectNode();
        doc.put("type", "doc");
        doc.put("version", 1);
        var content = doc.putArray("content");
        for (Section s : sections) {
            if (s.heading() != null) {
                ObjectNode heading = content.addObject();
                heading.put("type", "heading");
                heading.putObject("attrs").put("level", 3);
                addText(heading, s.heading());
            }
            for (String line : s.lines()) {
                ObjectNode para = content.addObject();
                para.put("type", "paragraph");
                addText(para, line);
            }
        }
        return doc;
    }

    private static void addText(ObjectNode parent, String text) {
        ObjectNode textNode = parent.putArray("content").addObject();
        textNode.put("type", "text");
        textNode.put("text", text);
    }

    private static String extractBrowseUrl(String baseUrl, String responseBody) {
        try {
            JsonNode root      = MAPPER.readTree(responseBody);
            String   issueKey  = root.path("key").asText();
            if (issueKey.isBlank()) {
                throw new DiagnosticException(
                    "Jira response did not contain an issue key: " + truncate(responseBody, 200),
                    "JIRA_API_ERROR");
            }
            return baseUrl + "/browse/" + issueKey;
        } catch (DiagnosticException de) {
            throw de;
        } catch (Exception e) {
            throw new DiagnosticException(
                "Failed to parse Jira response: " + e.getMessage(), "JIRA_API_ERROR", e);
        }
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    /** Truncates and strips control characters (CR/LF etc.) so remote text cannot forge log lines. */
    private static String truncate(String s, int max) {
        if (s == null) return "";
        String clean = s.replaceAll("\\p{Cntrl}", " ");
        return clean.length() <= max ? clean : clean.substring(0, max) + "…";
    }
}
