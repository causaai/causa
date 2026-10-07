# Health Endpoint

This document covers the custom aggregated health endpoint exposed by the backend.

**Base URLs:** `{{BASE_URL}}` or `http://localhost:8080`

## Endpoint

| Endpoint | Purpose | Success response code | Error response codes |
|---|---|---|---|
| `GET /api/v1/healthz` | Return aggregated application health across core and integration components | `200 OK` | `503 Service Unavailable/ Degraded/ Down`, `500 Internal Server Error` |

### Request Example (cURL)

**Using `{{BASE_URL}}`:**

```bash
curl {{BASE_URL}}/api/v1/healthz
```

**Using `localhost:8080`:**

```bash
curl http://localhost:8080/api/v1/healthz
```

### Response Example

```json
{
  "status": "DOWN",
  "timestamp": "2026-09-23T20:35:38.762528Z",
  "version": "0.0.4-SNAPSHOT",
  "components": {
    "database": {
      "status": "UP",
      "message": "Connected to PostgreSQL",
      "latency_ms": 2
    },
    "llm_provider": {
      "status": "DOWN",
      "message": "LLM health check failed: LLM request failed: VERTEX_PROJECT_ID is required for provider: vertex-ai-anthropic",
      "latency_ms": 35
    },
    "mcp_config": {
      "status": "UP",
      "servers": {
        "kubernetes": {
          "status": "UP",
          "message": "Connected successfully",
          "latency_ms": 245,
          "optional": false
        },
        "kruize": {
          "status": "UP",
          "message": "Connected successfully",
          "latency_ms": 1247,
          "optional": false
        },
        "cryostat": {
          "status": "DOWN",
          "message": "MCP server not available",
          "latency_ms": 504,
          "optional": true
        }
      }
    }
  }
}
```

## Response notes

- The endpoint returns the overall health in `status` and a component map in `components`.
- In the current local environment the app is `DEGRADED`, not fully `UP`, because optional integrations are unavailable.
- The controller returns `503` for `DEGRADED` and `DOWN`, and `200` only when the status is `UP`.

## Component meanings

| Component | Meaning |
|---|---|
| `database` | Backend database connectivity and latency |
| `llm_provider` | LLM provider readiness |
| `mcp_config` | MCP server connectivity (one entry per server in `mcp.json`) |

### Core components (always present)

| Component | Meaning |
|---|---|
| `database` | Backend database connectivity and latency |
| `llm_provider` | LLM provider readiness |
| `mcp_config` | MCP server readiness |

### MCP components (dynamic)

MCP health components are discovered dynamically from the `McpRegistry` at runtime. Each
server declared in the active `mcp.json` profile generates an `mcp_<name>` component — for
example, loading `mcp-cluster-default.json` produces `mcp_kubernetes`, `mcp_kruize`, and
`mcp_cryostat`; loading `mcp-developer-default.json` adds `mcp_quarkus` and
`mcp_async-profiler` instead of `mcp_cryostat`.

Servers marked `"optional": true` in `mcp.json` (e.g. Cryostat) report `DOWN` without
degrading the overall system status. Non-optional servers being `DOWN` causes the endpoint to
return `DEGRADED` (HTTP 503).

If `McpRegistry` fails to initialize (e.g. invalid `mcp.json`), a single
`mcp_config` component appears with status `DOWN` and the initialization error message.
