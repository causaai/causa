package com.causa.core.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Unified auth-config record serialised into the {@code auth_config} JSONB column on both
 * {@code external_configs} and {@code llm_configs}.
 *
 * <p>All fields are nullable; only the fields relevant to the selected platform are populated.
 * Fields marked <em>encrypted</em> are AES-256-GCM encrypted individually before storage and
 * masked as {@code ********} in GET responses. Null fields are omitted from the serialised JSON.
 *
 * <p>Fields by platform (* = AES-256-GCM encrypted):
 * <pre>
 *   Observability — DATADOG:   apiKey*, applicationKey*
 *   Observability — INSTANA:   apiToken*
 *   Observability — OTHER:     apiToken*
 *
 *   LLM — API_KEY:             baseUrl, apiKey*
 *   LLM — VERTEX_AI:           projectId, location, credentialsJson*  (ANTHROPIC via Google Cloud)
 *   LLM — CUSTOM_HEADERS:      baseUrl, headers*
 *
 *   Integrations — SLACK:      webhookUrl*, apiToken*
 *   Integrations — JIRA:       usernameEmail, apiToken*
 *   Integrations — GITHUB:     apiToken*
 *
 *   Behavioural config (channel, projectName, issueType, ownerRepo, baseUrl for Jira)
 *   goes in additional_config JSONB — not here.
 * </pre>
 *
 * @see com.causa.common.constants.ConfigConstants#SENSITIVE_AUTH_FIELDS
 * @since 0.0.3
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthConfig(

    String apiKey,              // encrypted — DATADOG (ingest), LLM (API_KEY, CUSTOM)
    String applicationKey,      // encrypted — DATADOG only (read/query access)
    String apiToken,            // encrypted — INSTANA, OTHER, SLACK, JIRA, GITHUB

    String baseUrl,             // plain     — API_KEY, CUSTOM_HEADERS: endpoint URL
    String projectId,           // plain     — VERTEX_AI: GCP project ID
    String location,            // plain     — VERTEX_AI: GCP region (e.g. us-central1)
    String credentialsJson,     // encrypted — VERTEX_AI auth: service-account JSON blob
    Map<String, String> headers,// encrypted — CUSTOM_HEADERS auth: full header map

    String webhookUrl,          // encrypted — SLACK: secret inbound webhook URL
    String usernameEmail        // plain     — JIRA: account email (auth identity for API token)
) {}
