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
 *   Observability — DATADOG:   apiKey*, appKey*
 *   Observability — INSTANA:   token*
 *   Observability — OTHER:     token*
 *
 *   LLM — API_KEY:             authType, apiKey*
 *   LLM — SA_JSON_KEY:         authType, credentialsJson*  (projectId, location → additional_config)
 *   LLM — CUSTOM_HEADERS:      authType, headers*
 *
 *   Integrations — SLACK:      authType, token*
 *   Integrations — JIRA:       authType, username, token*
 *   Integrations — GITHUB:     authType, token*
 *
 *   url for all platforms stored in respective table url column — not here.
 *   Behavioural config (channel, projectName, issueType, ownerRepo, projectId, location)
 *   goes in additional_config JSONB — not here.
 * </pre>
 *
 * @see com.causa.common.constants.ConfigConstants#SENSITIVE_AUTH_FIELDS
 * @since 0.0.3
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthConfig(

    String authType,            // plain     — auth strategy (API_KEY | SA_JSON_KEY | CUSTOM_HEADERS | API_TOKEN | WEBHOOK | PAT)

    String apiKey,              // encrypted — DATADOG (ingest), LLM (API_KEY)
    String appKey,              // encrypted — DATADOG only (read/query access)
    String token,               // encrypted — INSTANA, OTHER, SLACK, JIRA, GITHUB: bearer/api/jwt token

    String credentialsJson,     // encrypted — SA_JSON_KEY: service-account JSON blob
    Map<String, String> headers,// encrypted — CUSTOM_HEADERS: full header map

    String username,            // plain     — JIRA: account username / email
    String password             // encrypted — basic auth password
) {}
