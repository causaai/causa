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
  "status": "DEGRADED",
  "timestamp": "2026-08-06T07:40:55.917237785Z",
  "version": "0.0.1-SNAPSHOT",
  "components": {
  "database": {
    "status": "UP",
    "message": "Connected to PostgreSQL",
    "latency_ms": 1
  },
  "mcp_kubernetes": {
    "status": "UP",
    "message": "Connected successfully",
    "latency_ms": 11
  },
  "llm_provider": {
    "status": "UP",
    "message": "Connected to LangChain4J with vertex-ai-anthropic / claude-sonnet-4-6",
    "latency_ms": 1087
  },
  "mcp_kruize": {
    "status": "UP",
    "message": "Connected successfully",
    "latency_ms": 96
  },
  "mcp_cryostat": {
    "status": "DOWN",
    "message": "MCP server not available",
    "latency_ms": 35
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
| `mcp_kubernetes` | Kubernetes MCP connectivity |
| `mcp_kruize` | Kruize MCP connectivity |
| `mcp_cryostat` | Cryostat MCP connectivity |
