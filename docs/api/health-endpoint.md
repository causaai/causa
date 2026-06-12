# Health Check Endpoint

## Overview

The health check endpoint provides comprehensive monitoring of all system components including database connectivity, LLM providers, and MCP servers. This endpoint is designed for monitoring systems, load balancers, and operations teams.

## Endpoint

```
GET /api/v1/healthz
```

## Response Format

### Success Response (200 OK)

When all components are healthy:

```json
{
  "status": "UP",
  "timestamp": "2026-06-09T06:45:00Z",
  "version": "0.0.1",
  "components": {
    "database": {
      "status": "UP",
      "message": "Connected to PostgreSQL",
      "latency_ms": 12
    },
    "llm_provider": {
      "status": "UP",
      "message": "Connected to LangChain4J with claude-sonnet-4-6",
      "latency_ms": 245
    },
    "mcp_kubernetes": {
      "status": "UP",
      "message": "Connected successfully",
      "latency_ms": 89
    }
  }
}
```

### Failure Response (503 Service Unavailable)

When critical components are down:

```json
{
  "status": "DOWN",
  "timestamp": "2026-06-09T06:45:00Z",
  "version": "0.0.1",
  "components": {
    "database": {
      "status": "DOWN",
      "message": "Database connection not available"
    }
  }
}
```

## Response Fields

| Field | Type | Description |
|-------|------|-------------|
| `status` | string | Overall system status: `UP`, `DOWN`, or `DEGRADED` |
| `timestamp` | string | ISO 8601 formatted UTC timestamp |
| `version` | string | Application version from `application.yml` |
| `components` | object | Map of component names to their health status |

### Component Health Fields

| Field | Type | Description |
|-------|------|-------------|
| `status` | string | Component status: `UP` or `DOWN` |
| `message` | string | Human-readable status message |
| `latency_ms` | number | Response latency in milliseconds (optional) |

## HTTP Status Codes

| Code | Description |
|------|-------------|
| 200 | All critical components are healthy |
| 503 | One or more critical components are down |

## Components Monitored

### Current Components

1. **Database (PostgreSQL)**
   - Checks connection pool availability
   - Measures query latency
   - Critical component (failure causes 503)

2. **LLM Provider**
   - Provider: Configurable via LangChain4J (Anthropic Claude, Vertex AI, IBM Bob, Ollama)
   - Default model: claude-sonnet-4-6
   - Checks API connectivity with test prompt
   - Measures response latency
   - Non-critical component (failure causes DEGRADED status)

3. **MCP Kubernetes**
   - Checks k8s-mcp-server connectivity on OpenShift
   - Verifies MCP tool availability
   - Measures response latency
   - Non-critical component (failure causes DEGRADED status)

### Future Components (TODO)

4. **MCP Cryostat**
   - Checks Cryostat MCP server connectivity
   - Measures response latency

5. **MCP Kruize**
   - Checks Kruize MCP server connectivity
   - Measures response latency

## Usage Examples

### cURL

```bash
curl -X GET http://localhost:8080/api/v1/healthz
```

### HTTPie

```bash
http GET http://localhost:8080/api/v1/healthz
```

### Using in Kubernetes

```yaml
livenessProbe:
  httpGet:
    path: /api/v1/healthz
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10

readinessProbe:
  httpGet:
    path: /api/v1/healthz
    port: 8080
  initialDelaySeconds: 5
  periodSeconds: 5
```

## Local Development Setup

### 1. Start PostgreSQL Database

```bash
docker run -d \
  --name causa-postgres \
  -e POSTGRES_USER=causa_backend \
  -e POSTGRES_PASSWORD=dev_password \
  -e POSTGRES_DB=diagnostics-tool-db \
  -p 5432:5432 \
  postgres:17
```

### 2. Configure Database Connection

Set environment variables for local development:

```bash
export CAUSA_DB_URL=jdbc:postgresql://localhost:5432/diagnostics-tool-db
export CAUSA_DB_USERNAME=causa_backend
export CAUSA_DB_PASSWORD=dev_password
```

Or update `application.yml` defaults:

```yaml
quarkus:
  datasource:
    username: ${CAUSA_DB_USERNAME:causa_backend}
    password: ${CAUSA_DB_PASSWORD:dev_password}
    jdbc:
      url: ${CAUSA_DB_URL:jdbc:postgresql://localhost:5432/diagnostics-tool-db}
```

### 3. Start the Application

```bash
./mvnw quarkus:dev
```

### 4. Test the Health Endpoint

```bash
curl http://localhost:8080/api/v1/healthz | jq
```

Expected output:

```json
{
  "status": "UP",
  "timestamp": "2026-06-09T06:45:00.123Z",
  "version": "0.0.1",
  "components": {
    "database": {
      "status": "UP",
      "message": "Connected to PostgreSQL",
      "latency_ms": 8
    },
    "llm_provider": {
      "status": "UP",
      "message": "Connected to LangChain4J with claude-sonnet-4-6",
      "latency_ms": 245
    },
    "mcp_kubernetes": {
      "status": "UP",
      "message": "Connected successfully",
      "latency_ms": 89
    }
  }
}
```

## Architecture

### Components

1. **HealthCheckController** (`api/controllers/HealthCheckController.java`)
   - REST endpoint handler
   - Returns appropriate HTTP status codes
   - Handles exceptions gracefully

2. **HealthCheckService** (`core/services/HealthCheckService.java`)
   - Aggregates component health checks
   - Determines overall system status
   - Extensible for future components

3. **DatabaseConnectionService** (`infrastructure/persistence/DatabaseConnectionService.java`)
   - Manages database connection pool
   - Provides `isReady()` method
   - Verifies connectivity on startup

4. **DTOs** (`api/dto/`)
   - `HealthCheckResponseDto`: Overall health response
   - `ComponentHealthDto`: Individual component health

### Status Determination Logic

- **UP**: All components are healthy
- **DOWN**: Critical components (database) are down
- **DEGRADED**: Non-critical components (LLM provider, MCP servers) are down but database is up

## Monitoring Integration

### Prometheus

The endpoint can be scraped by Prometheus for metrics:

```yaml
scrape_configs:
  - job_name: 'causa-backend'
    metrics_path: '/api/v1/healthz'
    static_configs:
      - targets: ['causa-backend:8080']
```

### Grafana Dashboard

Create alerts based on the health status:

```
alert: CausaBackendDown
expr: causa_health_status != 1
for: 5m
labels:
  severity: critical
annotations:
  summary: "Causa Backend is down"
```

## Troubleshooting

### Database Connection Issues

If the database component shows `DOWN`:

1. Check database is running:
   ```bash
   docker ps | grep causa-postgres
   ```

2. Verify connection parameters:
   ```bash
   psql -h localhost -U causa_backend -d diagnostics-tool-db
   ```

3. Check application logs:
   ```bash
   tail -f target/quarkus.log
   ```

### High Latency

If latency is consistently high (>100ms):

1. Check database connection pool settings in `application.yml`
2. Monitor database performance
3. Review network connectivity

## Related Documentation

- [Database Configuration](../development/database.md)
- [Deployment Guide](../deployment/deployment.md)
- [Logging Guide](../development/logging-guide.md)