package com.causa.notification.jira;

import com.causa.common.constants.ConfigConstants.ExternalAuthType;
import com.causa.common.exceptions.DiagnosticException;
import com.causa.common.logging.CausaLogger;
import com.causa.core.domain.Diagnostic;
import com.causa.core.domain.ExternalConfig;
import com.causa.core.domain.RootCauseAnalysis;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * Jira REST client (outbound adapter). Stateless: duplicate prevention is the caller's job.
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
    private static final String       ISSUE_TYPE = "Task";

    private enum Flavor {
        CLOUD("/rest/api/3", "/search/jql"),
        SERVER("/rest/api/2", "/search");

        final String apiPath;
        final String searchPath;

        Flavor(String apiPath, String searchPath) {
            this.apiPath = apiPath;
            this.searchPath = searchPath;
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
    public String createTicket(ExternalConfig jiraConfig, Diagnostic diagnostic) {
        Flavor flavor     = Flavor.of(jiraConfig);
        String baseUrl    = resolveBaseUrl(jiraConfig);
        String authHeader = buildAuthHeader(jiraConfig, flavor);
        String projectKey = resolveProjectKey(jiraConfig);

        String requestBody = buildRequestBody(projectKey, diagnostic, flavor);

        log.info("Creating Jira ticket")
            .field("project", projectKey)
            .field("diagnosticId", diagnostic.getDiagnosticId())
            .log();

        String responseBody = post(baseUrl.stripTrailing() + flavor.apiPath + "/issue", authHeader, requestBody);
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

        String responseBody = post(baseUrl.stripTrailing() + flavor.apiPath + flavor.searchPath, authHeader, body.toString());
        try {
            JsonNode issues = MAPPER.readTree(responseBody).path("issues");
            if (!issues.isArray() || issues.isEmpty()) {
                return Optional.empty();
            }
            String issueKey = issues.get(0).path("key").asText();
            return issueKey.isBlank()
                ? Optional.empty()
                : Optional.of(baseUrl.stripTrailing() + "/browse/" + issueKey);
        } catch (Exception e) {
            throw new DiagnosticException(
                "Failed to parse Jira search response: " + e.getMessage(), "JIRA_API_ERROR", e);
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
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Jira API returned non-2xx status")
                    .field("status", response.statusCode())
                    .field("body", truncate(response.body(), 300))
                    .log();
                throw new DiagnosticException(
                    "Jira API error: HTTP " + response.statusCode(),
                    "JIRA_API_ERROR");
            }
            return response.body();

        } catch (DiagnosticException de) {
            throw de;
        } catch (Exception e) {
            throw new DiagnosticException("Jira API call failed: " + e.getMessage(), "JIRA_API_ERROR", e);
        }
    }

    private static String resolveBaseUrl(ExternalConfig config) {
        String url = config.getUrl();
        if (url == null || url.isBlank()) {
            throw new DiagnosticException(
                "Jira config is missing a base URL. "
                + "Set the 'url' field via PUT /api/v1/configs/integrations/JIRA.",
                "JIRA_CONFIG_INCOMPLETE");
        }
        return url;
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

    private static String resolveProjectKey(ExternalConfig config) {
        Object key = config.getAdditionalConfig() != null
            ? config.getAdditionalConfig().get("projectName")
            : null;
        if (key == null || key.toString().isBlank()) {
            throw new DiagnosticException(
                "Jira config is missing additional_config.projectName.",
                "JIRA_CONFIG_INCOMPLETE");
        }
        return key.toString();
    }

    /** Issue payload; Cloud needs the description as Atlassian Document Format, Server as plain text. */
    private static String buildRequestBody(String projectKey, Diagnostic diagnostic, Flavor flavor) {
        try {
            RootCauseAnalysis rca = diagnostic.getRca();
            String summary     = rca != null && !isBlank(rca.issueTitle())
                ? rca.issueTitle()
                : "Causa diagnostic: " + diagnostic.getDiagnosticId();
            String description = rca != null && !isBlank(rca.rootCause())
                ? rca.rootCause()
                : "See Causa diagnostic: " + diagnostic.getDiagnosticId();

            ObjectNode body   = MAPPER.createObjectNode();
            ObjectNode fields = body.putObject("fields");
            fields.putObject("project").put("key", projectKey);
            fields.put("summary", summary);
            fields.putArray("labels").add(alertLabel(diagnostic.getAlertId()));
            fields.putObject("issuetype").put("name", ISSUE_TYPE);
            if (flavor == Flavor.CLOUD) {
                fields.set("description", adfDocument(description));
            } else {
                fields.put("description", description);
            }

            return MAPPER.writeValueAsString(body);
        } catch (Exception e) {
            throw new DiagnosticException("Failed to build Jira request body: " + e.getMessage(),
                "JIRA_PAYLOAD_ERROR", e);
        }
    }

    private static ObjectNode adfDocument(String text) {
        ObjectNode doc = MAPPER.createObjectNode();
        doc.put("type", "doc");
        doc.put("version", 1);
        ObjectNode para = doc.putArray("content").addObject();
        para.put("type", "paragraph");
        ObjectNode textNode = para.putArray("content").addObject();
        textNode.put("type", "text");
        textNode.put("text", text);
        return doc;
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
            return baseUrl.stripTrailing() + "/browse/" + issueKey;
        } catch (DiagnosticException de) {
            throw de;
        } catch (Exception e) {
            throw new DiagnosticException(
                "Failed to parse Jira response: " + e.getMessage(), "JIRA_API_ERROR", e);
        }
    }

    private static boolean isBlank(String s) { return s == null || s.isBlank(); }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
