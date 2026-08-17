# Health Checks

This document covers the Quarkus health endpoints exposed by the app.

**Base URLs:** `{{BASE_URL}}` or `http://localhost:8080`

## Endpoints

| Endpoint | Purpose | Success response code | Error response codes |
|---|---|---|---|
| `GET /q/health` | Return overall Quarkus health including liveness and readiness contributors | `200 OK` | `503 Service Unavailable` |
| `GET /q/health/live` | Return liveness status only | `200 OK` | `503 Service Unavailable` |
| `GET /q/health/ready` | Return readiness status only | `200 OK` | `503 Service Unavailable` |

---

## GET `/q/health`

Returns the overall Quarkus health response including liveness and readiness contributors.

### Request Example (cURL)

**Using `{{BASE_URL}}`:**

```bash
curl {{BASE_URL}}/q/health
```

**Using `localhost:8080`:**

```bash
curl http://localhost:8080/q/health
```

### Response Example

```json
{
  "status": "UP",
  "checks": [
    {
      "name": "causa-liveness",
      "status": "UP",
      "data": {
        "status": "UP",
        "message": "Causa is alive and running"
      }
    },
    {
      "name": "Database connections health check",
      "status": "UP",
      "data": {
        "<default>": "UP"
      }
    },
    {
      "name": "causa-readiness",
      "status": "UP",
      "data": {
        "status": "READY",
        "message": "Causa is ready to accept requests"
      }
    },
    {
      "name": "database",
      "status": "UP",
      "data": {
        "status": "READY",
        "message": "Database is ready"
      }
    }
  ]
}
```

---

## GET `/q/health/live`

Returns the liveness check only.

### Request Example (cURL)

**Using `{{BASE_URL}}`:**

```bash
curl {{BASE_URL}}/q/health/live
```

**Using `localhost:8080`:**

```bash
curl http://localhost:8080/q/health/live
```

### Response Example

```json
{
  "status": "UP",
  "checks": [
    {
      "name": "causa-liveness",
      "status": "UP",
      "data": {
        "status": "UP",
        "message": "Causa is alive and running"
      }
    }
  ]
}
```

---

## GET `/q/health/ready`

Returns readiness checks only.

### Request Example (cURL)

**Using `{{BASE_URL}}`:**

```bash
curl {{BASE_URL}}/q/health/ready
```

**Using `localhost:8080`:**

```bash
curl http://localhost:8080/q/health/ready
```

### Response Example

```json
{
  "status": "UP",
  "checks": [
    {
      "name": "Database connections health check",
      "status": "UP",
      "data": {
        "<default>": "UP"
      }
    },
    {
      "name": "causa-readiness",
      "status": "UP",
      "data": {
        "status": "READY",
        "message": "Causa is ready to accept requests"
      }
    },
    {
      "name": "database",
      "status": "UP",
      "data": {
        "status": "READY",
        "message": "Database is ready"
      }
    }
  ]
}
```
