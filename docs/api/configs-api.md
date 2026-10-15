# Configs API

Runtime configuration management for Causa Backend. All endpoints live under `/api/v1/configs`.

**Base URL:** `http://localhost:8080` (local) or `{{BASE_URL}}` (production)

---

## Endpoint Overview

| Method | Path | Description | Success | Errors |
|---|---|---|---|---|
| `GET` | `/api/v1/configs` | Combined snapshot of all config categories | `200` | — |
| `GET` | `/api/v1/configs/generic` | List generic key-value configs (optional `?category=` filter) | `200` | `400` |
| `GET` | `/api/v1/configs/generic/{key}` | Fetch a single generic config by key | `200` | `400` |
| `PUT` | `/api/v1/configs/generic` | Upsert one or more generic config values | `200` | `400` |
| `DELETE` | `/api/v1/configs/generic/{key}` | Delete a generic config entry | `204` | `400` |
| `GET` | `/api/v1/configs/observability` | List observability platform configs | `200` | — |
| `PUT` | `/api/v1/configs/observability/{platform}` | Upsert an observability platform config | `200` | `400` |
| `DELETE` | `/api/v1/configs/observability/{platform}/{name}` | Delete an observability config by platform and name | `204` | `400` |
| `GET` | `/api/v1/configs/llm` | List LLM provider configs | `200` | — |
| `PUT` | `/api/v1/configs/llm/{provider}` | Upsert an LLM provider config | `200` | `400` |
| `DELETE` | `/api/v1/configs/llm/{provider}` | Delete an LLM provider config | `204` | `400` |
| `GET` | `/api/v1/configs/integrations` | List integration platform configs | `200` | — |
| `PUT` | `/api/v1/configs/integrations/{platform}` | Upsert an integration platform config | `200` | `400` |
| `DELETE` | `/api/v1/configs/integrations/{platform}/{name}` | Delete an integration config by platform and name | `204` | `400` |

---

## GET `/api/v1/configs`

Returns a combined snapshot of all four configuration categories in a single call.

```bash
curl http://localhost:8080/api/v1/configs
```

### Response

```json
{
  "observability": [
    {
      "id": "ext_cnf_abc123",
      "platform": "DATADOG",
      "name": "datadog-prod",
      "url": "https://api.datadoghq.com",
      "is_active": true,
      "auth_config": {
        "authType": "API_KEY",
        "apiKey": "********",
        "appKey": "********"
      },
      "created_at": "2025-01-15T10:00:00Z",
      "updated_at": "2025-01-15T10:00:00Z"
    }
  ],
  "integrations": [
    {
      "id": "ext_cnf_def456",
      "platform": "SLACK",
      "name": "slack-alerts",
      "url": "https://hooks.slack.com/services/...",
      "is_active": true,
      "auth_config": {
        "authType": "WEBHOOK",
        "token": "********"
      },
      "additional_config": {
        "channel": "#alerts"
      },
      "created_at": "2025-01-15T10:00:00Z",
      "updated_at": "2025-01-15T10:00:00Z"
    }
  ],
  "llm": [
    {
      "id": "llm_cnf_ghi789",
      "provider": "ANTHROPIC",
      "url": "https://api.anthropic.com",
      "models": ["claude-sonnet-4-6"],
      "temperature": 0.10,
      "max_tokens": 8192,
      "timeout_ms": 30000,
      "is_active": true,
      "auth_config": {
        "authType": "API_KEY",
        "apiKey": "********"
      },
      "created_at": "2025-01-15T10:00:00Z",
      "updated_at": "2025-01-15T10:00:00Z"
    }
  ],
  "generic": [
    { "key": "ALERT_FILTER_SEVERITY", "value": "critical", "category": "alerts", "encrypted": false },
    { "key": "ALERT_COOLDOWN_MINUTES", "value": "15", "category": "alerts", "encrypted": false },
    { "key": "CLUSTER_NAME", "value": "my-cluster", "category": "cluster", "encrypted": false }
  ]
}
```

---

## Generic key-value configs

Stored in the `generic_configs` table. Keys are from a fixed registry — unknown keys are rejected.

### GET `/api/v1/configs/generic`

```bash
# All generic configs
curl http://localhost:8080/api/v1/configs/generic

# Filtered by category
curl 'http://localhost:8080/api/v1/configs/generic?category=alerts'
```

**Valid `category` values:** `alerts`, `cluster`

#### Response

```json
[
  { "key": "ALERT_FILTER_SEVERITY", "value": "critical", "category": "alerts", "encrypted": false },
  { "key": "ALERT_COOLDOWN_MINUTES", "value": "15", "category": "alerts", "encrypted": false },
  { "key": "ALERT_IGNORE_NAMESPACES", "value": "kube-system,istio-system", "category": "alerts", "encrypted": false },
  { "key": "ALERT_COOLDOWN_CLEANUP_INTERVAL", "value": "5m", "category": "alerts", "encrypted": false }
]
```

---

### GET `/api/v1/configs/generic/{key}`

```bash
curl http://localhost:8080/api/v1/configs/generic/ALERT_FILTER_SEVERITY
```

#### Response

```json
{ "key": "ALERT_FILTER_SEVERITY", "value": "critical", "category": "alerts", "encrypted": false }
```

Returns `400` for an unknown key.

---

### PUT `/api/v1/configs/generic`

Upserts one or more generic config values. Valid entries are applied individually; invalid entries are returned in `rejected` and skipped — partial success is possible.

```bash
curl -X PUT http://localhost:8080/api/v1/configs/generic \
  -H 'Content-Type: application/json' \
  -d '{
    "configs": {
      "ALERT_FILTER_SEVERITY": "warning",
      "ALERT_COOLDOWN_MINUTES": "30",
      "UNKNOWN_KEY": "x"
    }
  }'
```

#### Response

```json
{
  "updated": [
    { "key": "ALERT_FILTER_SEVERITY", "value": "warning", "category": "alerts", "encrypted": false },
    { "key": "ALERT_COOLDOWN_MINUTES", "value": "30", "category": "alerts", "encrypted": false }
  ],
  "rejected": [
    { "key": "UNKNOWN_KEY", "reason": "Unknown config key" }
  ]
}
```

**Rejection reasons:**
- `Unknown config key` — key is not in the registry
- `<key> cannot be updated at runtime` — env-only key (e.g. `CLUSTER_TYPE`)
- `Value must not be blank`
- `Expected an integer value` / `Expected a numeric (double) value` / `Expected a boolean value`

---

### DELETE `/api/v1/configs/generic/{key}`

```bash
curl -X DELETE http://localhost:8080/api/v1/configs/generic/ALERT_IGNORE_NAMESPACES
```

Returns `204 No Content` on success. Returns `400` for unknown or env-only keys.

---

## Generic config key registry

| Key | Category | Type | Runtime updatable |
|---|---|---|---|
| `ALERT_FILTER_SEVERITY` | `alerts` | string | Yes |
| `ALERT_COOLDOWN_MINUTES` | `alerts` | integer | Yes |
| `ALERT_IGNORE_NAMESPACES` | `alerts` | string | Yes |
| `ALERT_COOLDOWN_CLEANUP_INTERVAL` | `alerts` | string | Yes |
| `CLUSTER_NAME` | `cluster` | string | Yes |
| `CLUSTER_TYPE` | `cluster` | string | **No** (env-only) |

`CLUSTER_TYPE` must be set via environment variable or ConfigMap before startup. It cannot be updated or deleted through the API.

---

## Observability configs

Stored in `external_configs` with `category = OBSERVABILITY`. Multiple named entries per platform are allowed (`UNIQUE(platform, name)`).

**Supported platforms:** `DATADOG`, `INSTANA`, `OTHER`

**Auth types:** `API_KEY`

**Sensitive fields masked in responses:**
- `DATADOG`: `apiKey`, `appKey`
- `INSTANA` / `OTHER`: `token`

### GET `/api/v1/configs/observability`

```bash
curl http://localhost:8080/api/v1/configs/observability
```

### PUT `/api/v1/configs/observability/{platform}`

```bash
curl -X PUT http://localhost:8080/api/v1/configs/observability/DATADOG \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "datadog-prod",
    "url": "https://api.datadoghq.com",
    "is_active": true,
    "auth_config": {
      "authType": "API_KEY",
      "apiKey": "dd-api-key-here",
      "appKey": "dd-app-key-here"
    }
  }'
```

#### Response

```json
{
  "id": "ext_cnf_abc123",
  "platform": "DATADOG",
  "name": "datadog-prod",
  "url": "https://api.datadoghq.com",
  "is_active": true,
  "auth_config": {
    "authType": "API_KEY",
    "apiKey": "********",
    "appKey": "********"
  },
  "created_at": "2025-01-15T10:00:00Z",
  "updated_at": "2025-01-15T10:00:00Z"
}
```

### DELETE `/api/v1/configs/observability/{platform}/{name}`

```bash
curl -X DELETE http://localhost:8080/api/v1/configs/observability/DATADOG/datadog-prod
```

Returns `204 No Content` on success. Returns `400` if the entry does not exist.

---

## LLM configs

Stored in `llm_configs`. One row per provider (`UNIQUE(provider)`). Only one provider can have `is_active = true` at a time — setting `is_active: true` on a provider deactivates all others in the same transaction. Deleting the currently active provider is rejected.

**Supported providers:** `OPENAI`, `ANTHROPIC`, `AZURE_OPENAI`, `WATSONX`, `VERTEX_AI`

**Auth types and sensitive fields:**

| `authType` | Sensitive fields encrypted/masked |
|---|---|
| `API_KEY` | `apiKey` |
| `SA_JSON_KEY` | `credentialsJson` |
| `CUSTOM_HEADERS` | `headers` |

### GET `/api/v1/configs/llm`

```bash
curl http://localhost:8080/api/v1/configs/llm
```

### PUT `/api/v1/configs/llm/{provider}`

```bash
# Anthropic with API key
curl -X PUT http://localhost:8080/api/v1/configs/llm/ANTHROPIC \
  -H 'Content-Type: application/json' \
  -d '{
    "url": "https://api.anthropic.com",
    "models": ["claude-sonnet-4-6"],
    "temperature": 0.1,
    "max_tokens": 8192,
    "timeout_ms": 30000,
    "is_active": true,
    "auth_config": {
      "authType": "API_KEY",
      "apiKey": "sk-ant-api03-..."
    }
  }'
```

```bash
# Vertex AI with service-account JSON key
curl -X PUT http://localhost:8080/api/v1/configs/llm/VERTEX_AI \
  -H 'Content-Type: application/json' \
  -d '{
    "url": "https://us-east5-aiplatform.googleapis.com",
    "models": ["claude-sonnet-4-6"],
    "temperature": 0.1,
    "max_tokens": 8192,
    "timeout_ms": 180000,
    "is_active": true,
    "auth_config": {
      "authType": "SA_JSON_KEY",
      "credentialsJson": "<base64-encoded-sa-json>"
    },
    "additional_config": {
      "project": "my-gcp-project",
      "location": "us-east5",
      "skillsEnabled": true
    }
  }'
```

#### Response

```json
{
  "id": "llm_cnf_ghi789",
  "provider": "ANTHROPIC",
  "url": "https://api.anthropic.com",
  "models": ["claude-sonnet-4-6"],
  "temperature": 0.10,
  "max_tokens": 8192,
  "timeout_ms": 30000,
  "is_active": true,
  "auth_config": {
    "authType": "API_KEY",
    "apiKey": "********"
  },
  "created_at": "2025-01-15T10:00:00Z",
  "updated_at": "2025-01-15T10:00:00Z"
}
```

### DELETE `/api/v1/configs/llm/{provider}`

```bash
curl -X DELETE http://localhost:8080/api/v1/configs/llm/ANTHROPIC
```

Returns `204 No Content` on success. Returns `400` if the provider does not exist or is currently active.

---

## Integration configs

Stored in `external_configs` with `category = INTEGRATION`. Same `UNIQUE(platform, name)` constraint as observability configs.

**Supported platforms:** `SLACK`, `JIRA`, `GITHUB`

**Sensitive fields masked in responses:**
- `SLACK`: `token`
- `JIRA`: `token`, `password`
- `GITHUB`: `token`

### GET `/api/v1/configs/integrations`

```bash
curl http://localhost:8080/api/v1/configs/integrations
```

### PUT `/api/v1/configs/integrations/{platform}`

```bash
curl -X PUT http://localhost:8080/api/v1/configs/integrations/SLACK \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "slack-alerts",
    "url": "https://hooks.slack.com/services/...",
    "is_active": true,
    "auth_config": {
      "authType": "WEBHOOK",
      "token": "xoxb-..."
    },
    "additional_config": {
      "channel": "#rca-alerts"
    }
  }'
```

```bash
curl -X PUT http://localhost:8080/api/v1/configs/integrations/JIRA \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "jira-prod",
    "url": "https://your-org.atlassian.net",
    "is_active": true,
    "auth_config": {
      "authType": "API_TOKEN",
      "username": "bot@example.com",
      "token": "jira-api-token"
    },
    "additional_config": {
      "projectName": "OPS",
      "issueType": "Bug"
    }
  }'
```

### DELETE `/api/v1/configs/integrations/{platform}/{name}`

```bash
curl -X DELETE http://localhost:8080/api/v1/configs/integrations/SLACK/slack-alerts
```

Returns `204 No Content` on success. Returns `400` if the entry does not exist.

---

## Cache and live-reload

All three config tables (`generic_configs`, `llm_configs`, `external_configs`) are backed by in-memory caches. Writes trigger a PostgreSQL `LISTEN/NOTIFY` event on `config_cache_channel`, which refreshes the cache on all running pods without a restart.

The `ConfigCacheListener` subscribes to this channel and invalidates `LlmConfigCache` and `ExternalConfigCache` on any change to their respective tables. Generic config changes are handled by `AppConfig` via the same channel.
