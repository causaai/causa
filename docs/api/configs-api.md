# Configs API

This document covers runtime configuration endpoints.

**Base URLs:** `{{BASE_URL}}` or `http://localhost:8080`

## Endpoints

| Endpoint | Purpose | Success response code | Error response codes |
|---|---|---|---|
| `GET /api/v1/configs` | List runtime config entries, optionally by category | `200 OK` | `400 Bad Request` |
| `GET /api/v1/configs/{key}` | Fetch one runtime config by key | `200 OK` | `400 Bad Request` |
| `POST /api/v1/configs` | Upsert runtime config entries and return updated and rejected items | `200 OK` | `400 Bad Request` |

---

## GET `/api/v1/configs`

Returns all runtime configuration entries. You can filter by category using `category=llm`, `category=alerts`, or `category=cluster`.

### Request Example (cURL)

**Using `{{BASE_URL}}`:**

```bash
curl {{BASE_URL}}/api/v1/configs
```

**Using `localhost:8080`:**

```bash
curl http://localhost:8080/api/v1/configs
```

### Response Example

```json
[
  {
    "key": "LLM_MODEL_NAME",
    "value": "claude-sonnet-4-6",
    "category": "llm",
    "encrypted": false
  },
  {
    "key": "LLM_CHAT_MEMORY_SIZE",
    "value": "10",
    "category": "llm",
    "encrypted": false
  },
  {
    "key": "LLM_CUSTOM_HEADERS",
    "value": "{}",
    "category": "llm",
    "encrypted": false
  },
  {
    "key": "VERTEX_LOCATION",
    "value": "us-east5",
    "category": "llm",
    "encrypted": false
  },
  {
    "key": "LLM_BASE_URL",
    "value": "",
    "category": "llm",
    "encrypted": false
  },
  {
    "key": "LLM_TEMPERATURE",
    "value": "0.3",
    "category": "llm",
    "encrypted": false
  },
  {
    "key": "ALERT_IGNORE_NAMESPACES",
    "value": "kube-system,istio-system",
    "category": "alerts",
    "encrypted": false
  },
  {
    "key": "ALERT_FILTER_SEVERITY",
    "value": "critical",
    "category": "alerts",
    "encrypted": false
  },
  {
    "key": "BOB_SHELL_PATH",
    "value": "bob",
    "category": "llm",
    "encrypted": false
  },
  {
    "key": "LLM_PROVIDER",
    "value": "vertex-ai-anthropic",
    "category": "llm",
    "encrypted": false
  },
  {
    "key": "LLM_SKILLS_DIR",
    "value": "",
    "category": "llm",
    "encrypted": false
  },
  {
    "key": "ALERT_COOLDOWN_MINUTES",
    "value": "15",
    "category": "alerts",
    "encrypted": false
  },
  {
    "key": "LLM_TIMEOUT_SECONDS",
    "value": "180",
    "category": "llm",
    "encrypted": false
  },
  {
    "key": "VERTEX_PROJECT_ID",
    "value": "********",
    "category": "llm",
    "encrypted": true
  },
  {
    "key": "LLM_API_KEY",
    "value": "********",
    "category": "llm",
    "encrypted": true
  },
  {
    "key": "CLUSTER_NAME",
    "value": "default",
    "category": "cluster",
    "encrypted": false
  },
  {
    "key": "LLM_MAX_TOKENS",
    "value": "8192",
    "category": "llm",
    "encrypted": false
  },
  {
    "key": "LLM_SKILLS_ENABLED",
    "value": "false",
    "category": "llm",
    "encrypted": false
  },
  {
    "key": "ALERT_COOLDOWN_CLEANUP_INTERVAL",
    "value": "5m",
    "category": "alerts",
    "encrypted": false
  },
  {
    "key": "GOOGLE_APPLICATION_CREDENTIALS",
    "value": "********",
    "category": "llm",
    "encrypted": true
  },
  {
    "key": "CLUSTER_TYPE",
    "value": "vm",
    "category": "cluster",
    "encrypted": false
  },
  {
    "key": "LLM_AUTH_TYPE",
    "value": "",
    "category": "llm",
    "encrypted": false
  }
]
```

### Filtered example

**Using `{{BASE_URL}}`:**

```bash
curl '{{BASE_URL}}/api/v1/configs?category=alerts'
```

**Using `localhost:8080`:**

```bash
curl 'http://localhost:8080/api/v1/configs?category=alerts'
```

```json
[
  {
    "key": "ALERT_IGNORE_NAMESPACES",
    "value": "kube-system,istio-system",
    "category": "alerts",
    "encrypted": false
  },
  {
    "key": "ALERT_FILTER_SEVERITY",
    "value": "critical",
    "category": "alerts",
    "encrypted": false
  },
  {
    "key": "ALERT_COOLDOWN_MINUTES",
    "value": "15",
    "category": "alerts",
    "encrypted": false
  },
  {
    "key": "ALERT_COOLDOWN_CLEANUP_INTERVAL",
    "value": "5m",
    "category": "alerts",
    "encrypted": false
  }
]
```

---

## GET `/api/v1/configs/{key}`

Returns a single runtime config entry.

### Request Example (cURL)

**Using `{{BASE_URL}}`:**

```bash
curl {{BASE_URL}}/api/v1/configs/ALERT_FILTER_SEVERITY
```

**Using `localhost:8080`:**

```bash
curl http://localhost:8080/api/v1/configs/ALERT_FILTER_SEVERITY
```

### Response Example

```json
{
  "key": "ALERT_FILTER_SEVERITY",
  "value": "critical",
  "category": "alerts",
  "encrypted": false
}
```

### Invalid key example

**Using `{{BASE_URL}}`:**

```bash
curl {{BASE_URL}}/api/v1/configs/causa.alert.min-severity
```

**Using `localhost:8080`:**

```bash
curl http://localhost:8080/api/v1/configs/causa.alert.min-severity
```

```text
Unknown config key: causa.alert.min-severity
```

---

## Runtime updatable config keys

The following config keys are currently accepted by [`POST /api/v1/configs`](./configs-api.md) for runtime updates.


| Key | Category | Type | Sensitive | Runtime updatable |
|---|---|---|---|---|
| `LLM_PROVIDER` | `llm` | `string` | No | Yes |
| `LLM_MODEL_NAME` | `llm` | `string` | No | Yes |
| `LLM_BASE_URL` | `llm` | `string` | No | Yes |
| `LLM_AUTH_TYPE` | `llm` | `string` | No | Yes |
| `LLM_CUSTOM_HEADERS` | `llm` | `string` | No | Yes |
| `LLM_TEMPERATURE` | `llm` | `double` | No | Yes |
| `LLM_MAX_TOKENS` | `llm` | `integer` | No | Yes |
| `LLM_API_KEY` | `llm` | `string` | Yes | Yes |
| `LLM_TIMEOUT_SECONDS` | `llm` | `integer` | No | Yes |
| `LLM_CHAT_MEMORY_SIZE` | `llm` | `integer` | No | Yes |
| `VERTEX_PROJECT_ID` | `llm` | `string` | Yes | Yes |
| `VERTEX_LOCATION` | `llm` | `string` | No | Yes |
| `BOB_SHELL_PATH` | `llm` | `string` | No | Yes |
| `GOOGLE_APPLICATION_CREDENTIALS` | `llm` | `string` | Yes | Yes |
| `LLM_SKILLS_ENABLED` | `llm` | `boolean` | No | Yes |
| `LLM_SKILLS_DIR` | `llm` | `string` | No | Yes |
| `ALERT_FILTER_SEVERITY` | `alerts` | `string` | No | Yes |
| `ALERT_COOLDOWN_MINUTES` | `alerts` | `integer` | No | Yes |
| `ALERT_IGNORE_NAMESPACES` | `alerts` | `string` | No | Yes |
| `ALERT_COOLDOWN_CLEANUP_INTERVAL` | `alerts` | `string` | No | Yes |
| `CLUSTER_NAME` | `cluster` | `string` | No | Yes |
| `CLUSTER_TYPE` | `cluster` | `string` | No | No |

### Not runtime updatable

- `CLUSTER_TYPE` cannot be updated through [`POST /api/v1/configs`](./configs-api.md).
- It is environment-only and must be set before startup.

## ADC credentials note

For Vertex AI, Application Default Credentials support is not available yet through the config API.

### Encode ADC file as base64

```bash
base64 -i ~/.config/gcloud/application_default_credentials.json
```

### Current limitation

- ADC support via config value is not available yet.
- For now, mount the JSON credentials file into the runtime environment and point [`GOOGLE_APPLICATION_CREDENTIALS`](docs/api/configs-api.md) to that mounted file path.
- Planned availability: `0.0.2`.

## POST `/api/v1/configs`

Upserts config entries. Valid keys are updated individually, while invalid or badly typed keys are rejected and listed in the response.

### Request Example (cURL)

**Using `{{BASE_URL}}`:**

```bash
curl -X POST {{BASE_URL}}/api/v1/configs \
  -H 'Content-Type: application/json' \
  -d '{
    "configs": {
      "ALERT_FILTER_SEVERITY": "critical",
      "VERTEX_PROJECT_ID": "test-project",
      "UNKNOWN_KEY": "x",
      "LLM_TIMEOUT_SECONDS": "abc"
    }
  }'
```

**Using `localhost:8080`:**

```bash
curl -X POST http://localhost:8080/api/v1/configs \
  -H 'Content-Type: application/json' \
  -d '{
    "configs": {
      "ALERT_FILTER_SEVERITY": "critical",
      "VERTEX_PROJECT_ID": "test-project",
      "UNKNOWN_KEY": "x",
      "LLM_TIMEOUT_SECONDS": "abc"
    }
  }'
```


### Response Example

```json
{
  "updated": [
    {
      "key": "ALERT_FILTER_SEVERITY",
      "value": "critical",
      "category": "alerts",
      "encrypted": false
    },
    {
      "key": "VERTEX_PROJECT_ID",
      "value": "********",
      "category": "llm",
      "encrypted": true
    }
  ],
  "rejected": [
    {
      "key": "UNKNOWN_KEY",
      "reason": "Unknown config key"
    },
    {
      "key": "LLM_TIMEOUT_SECONDS",
      "reason": "Expected an integer value"
    }
  ]
}
```
