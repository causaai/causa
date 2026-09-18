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
 *   LLM — API_KEY:             apiKey*
 *   LLM — VERTEX_AI:           projectId, location, credentialsJson*  (ANTHROPIC via Google Cloud)
 *   LLM — CUSTOM_HEADERS:      headers*
 *
 *   Integrations — SLACK:      token*
 *   Integrations — JIRA:       username, token*
 *   Integrations — GITHUB:     token*
 *
 *   Behavioural config (channel, projectName, issueType, ownerRepo)
 *   goes in additional_config JSONB — not here.
 *   URL for each platform goes in the external_configs.url typed column — not here.
 * </pre>
 *
 * @see com.causa.common.constants.ConfigConstants#SENSITIVE_AUTH_FIELDS
 * @since 0.0.3
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthConfig(

    String apiKey,              // encrypted — DATADOG (ingest), LLM (API_KEY)
    String appKey,              // encrypted — DATADOG only (read/query access)
    String token,               // encrypted — INSTANA, OTHER, SLACK, JIRA, GITHUB: bearer/api/jwt token

    String projectId,           // plain     — VERTEX_AI: GCP project ID
    String location,            // plain     — VERTEX_AI: GCP region (e.g. us-central1)
    String credentialsJson,     // encrypted — VERTEX_AI auth: service-account JSON blob
    Map<String, String> headers,// encrypted — CUSTOM_HEADERS auth: full header map

    String username,            // plain     — JIRA: account username / email
    String password             // encrypted — basic auth password
) {}
