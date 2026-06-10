# Causa Alerts API Documentation

## Overview

The Alerts API provides endpoints for ingesting Prometheus Alertmanager webhooks and querying historical alert data. It supports filtering, validation, and integration with the Causa diagnostic engine.

**Base URL**: `http://causa-backend:8080`  
**API Version**: v1  
**Last Updated**: 2026-06-11

---

## Table of Contents

1. [Authentication](#authentication)
2. [Endpoints](#endpoints)
   - [POST /api/v1/webhooks/alerts](#post-apiv1webhooksalerts)
   - [GET /api/v1/alerts](#get-apiv1alerts)
   - [GET /api/v1/containers/{containerName}/alerts](#get-apiv1containerscontainernamealerts)
   - [GET /api/v1/alerts/{alertId}/diagnostics](#get-apiv1alertsalertiddiagnostics)
3. [Data Models](#data-models)
4. [Error Responses](#error-responses)
5. [Examples](#examples)
6. [Integration Guide](#integration-guide)
7. [Rate Limiting](#rate-limiting)

---

## Authentication

**Current Version**: No authentication required


---

## Endpoints

### POST /api/v1/webhooks/alerts

Receives alert notifications from Prometheus Alertmanager and triggers diagnostic analysis for accepted alerts.

#### Request

**HTTP Method**: `POST`  
**Content-Type**: `application/json`  
**Path**: `/api/v1/webhooks/alerts`

**Headers**:
```http
Content-Type: application/json
```

**Request Body**: Alertmanager v4 Webhook Payload

```json
{
  "receiver": "webhook",
  "status": "firing",
  "alerts": [
    {
      "status": "firing",
      "labels": {
        "alertname": "JavaContainerHighMemory",
        "severity": "critical",
        "namespace": "production",
        "pod": "payment-service-6b587d5-xk9j",
        "container": "payment-app"
      },
      "annotations": {
        "summary": "Container memory usage > 75%",
        "description": "Container payment-app is using 78% of its memory limit"
      },
      "startsAt": "2026-05-20T17:00:00Z",
      "endsAt": "0001-01-01T00:00:00Z",
      "generatorURL": "http://prometheus:9090/graph?...",
      "fingerprint": "a1b2c3d4e5f6"
    }
  ],
  "groupLabels": {
    "alertname": "JavaContainerHighMemory"
  },
  "commonLabels": {
    "alertname": "JavaContainerHighMemory",
    "severity": "critical",
    "namespace": "production"
  },
  "commonAnnotations": {
    "summary": "Container memory usage > 75%"
  },
  "externalURL": "http://alertmanager:9093",
  "version": "4",
  "groupKey": "{}:{alertname=\"JavaContainerHighMemory\"}"
}
```

**Required Fields**:
- `alerts` (array, non-empty)
- `alerts[].status` (string: "firing" or "resolved")
- `alerts[].labels` (object, must contain "alertname")
- `version` (string: "4")

**Optional Fields**:
- `receiver` (string)
- `status` (string)
- `groupLabels` (object)
- `commonLabels` (object)
- `commonAnnotations` (object)
- `externalURL` (string)
- `groupKey` (string)
- `truncatedAlerts` (integer)

#### Response

**Success - All Alerts Accepted (HTTP 200)**:
```json
{
  "status": "accepted",
  "message": "All 1 alerts accepted and diagnostics initiated",
  "totalReceived": 1,
  "totalAccepted": 1,
  "totalFiltered": 0,
  "acceptedAlertIds": [
    "payment-app-1779296400"
  ],
  "diagnosticIds": [
    "diag-payment-app-1779296400-1779296405"
  ],
  "timestamp": "2026-06-11T01:23:45Z"
}
```

**Partial - Some Alerts Filtered (HTTP 200)**:
```json
{
  "status": "partial",
  "message": "2 alerts accepted, 3 filtered; diagnostics initiated for accepted alerts",
  "totalReceived": 5,
  "totalAccepted": 2,
  "totalFiltered": 3,
  "acceptedAlertIds": [
    "payment-app-1779296400",
    "auth-service-1779296401"
  ],
  "diagnosticIds": [
    "diag-payment-app-1779296400-1779296405",
    "diag-auth-service-1779296401-1779296406"
  ],
  "timestamp": "2026-06-11T01:23:45Z"
}
```

**Rejected - All Alerts Filtered (HTTP 200)**:
```json
{
  "status": "rejected",
  "message": "All 3 alerts filtered (severity/namespace/cooldown)",
  "totalReceived": 3,
  "totalAccepted": 0,
  "totalFiltered": 3,
  "acceptedAlertIds": [],
  "diagnosticIds": [],
  "timestamp": "2026-06-11T01:23:45Z"
}
```

**Validation Error (HTTP 400)**:
```json
{
  "statusCode": 400,
  "error": "Validation Failed",
  "message": "Alerts array is null or empty; alerts[0].labels must contain 'alertname'"
}
```

**Internal Error (HTTP 500)**:
```json
{
  "statusCode": 500,
  "error": "Internal Server Error",
  "message": "An unexpected error occurred processing the alert webhook"
}
```

#### Filtering Rules

Alerts are filtered based on configuration:

1. **Severity Filter**: Only alerts with severity >= configured minimum
   - Configured via `CAUSA_ALERT_SEVERITY` (default: "critical")
   - Order: CRITICAL > WARNING > INFO

2. **Namespace Filter**: Alerts from ignored namespaces are dropped
   - Configured via `CAUSA_ALERT_IGNORE_NS` (default: "kube-system,istio-system")

3. **Cooldown Filter**: Duplicate alerts within cooldown period are dropped
   - Configured via `CAUSA_ALERT_COOLDOWN` (default: 15 minutes)
   - Deduplication key: `{alertName}:{podName}` or `{alertName}:{namespace}`

#### Alert ID Generation

Causa generates deterministic alert IDs:

**Format**: `{containerName}-{epochMillis}`

**Examples**:
- `payment-app-1779296400`
- `auth-service-1779296401`
- `unknown-1779296402` (when container name is null/blank)

---

### GET /api/v1/alerts

Retrieves all historical alerts stored in the database.

#### Request

**HTTP Method**: `GET`  
**Path**: `/api/v1/alerts`

**Query Parameters**: None (pagination planned for future)

**Headers**: None required

#### Response

**Success (HTTP 200)**:
```json
{
  "alerts": [
    {
      "alert_id": "payment-app-1779296400",
      "timestamp": "2026-05-20T17:00:00Z",
      "alert_name": "JavaContainerHighMemory",
      "severity": "critical",
      "pod_name": "payment-service-6b587d5-xk9j",
      "container_name": "payment-app",
      "namespace": "production",
      "status": "firing",
      "has_diagnostics": true
    },
    {
      "alert_id": "auth-service-1779296401",
      "timestamp": "2026-05-20T17:00:01Z",
      "alert_name": "JavaContainerOOMKilled",
      "severity": "critical",
      "pod_name": "auth-service-7c9d8f2-p4zm",
      "container_name": "auth-service",
      "namespace": "production",
      "status": "firing",
      "has_diagnostics": true
    }
  ],
  "totalCount": 2,
  "containerName": null
}
```

**Empty Result (HTTP 200)**:
```json
{
  "alerts": [],
  "totalCount": 0,
  "containerName": null
}
```

#### Response Fields

| Field | Type | Description |
|-------|------|-------------|
| `alerts` | Array | List of alert objects |
| `totalCount` | Integer | Total number of alerts returned |
| `containerName` | String/Null | Container filter applied (null for all alerts) |

**Alert Object Fields**:

| Field | Type | Nullable | Description |
|-------|------|----------|-------------|
| `alert_id` | String | No | Unique alert identifier |
| `timestamp` | ISO 8601 | No | When the alert was triggered |
| `alert_name` | String | No | Prometheus alert rule name |
| `severity` | String | No | "critical", "warning", or "info" |
| `pod_name` | String | Yes | Kubernetes pod name |
| `container_name` | String | No | Container name |
| `namespace` | String | No | Kubernetes namespace |
| `status` | String | No | "firing" or "resolved" |
| `has_diagnostics` | Boolean | No | Whether diagnostic analysis is available |

---

### GET /api/v1/containers/{containerName}/alerts

Retrieves historical alerts for a specific container.

#### Request

**HTTP Method**: `GET`  
**Path**: `/api/v1/containers/{containerName}/alerts`

**Path Parameters**:

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `containerName` | String | Yes | Container name (alphanumeric, dots, hyphens, underscores) |

**Constraints**:
- Max length: 255 characters
- Allowed characters: `a-z`, `A-Z`, `0-9`, `.`, `-`, `_`
- Pattern: `^[a-zA-Z0-9_.-]+$`

**Example Paths**:
- `/api/v1/containers/payment-app/alerts` ✅
- `/api/v1/containers/auth.service/alerts` ✅
- `/api/v1/containers/payment app/alerts` ❌ (spaces not allowed)
- `/api/v1/containers/auth@service/alerts` ❌ (@ not allowed)

#### Response

**Success (HTTP 200)**:
```json
{
  "alerts": [
    {
      "alert_id": "payment-app-1779296400",
      "timestamp": "2026-05-20T17:00:00Z",
      "alert_name": "JavaContainerHighMemory",
      "severity": "critical",
      "pod_name": "payment-service-6b587d5-xk9j",
      "container_name": "payment-app",
      "namespace": "production",
      "status": "firing",
      "has_diagnostics": true
    }
  ],
  "totalCount": 1,
  "containerName": "payment-app"
}
```

**Validation Error (HTTP 400)**:
```json
{
  "statusCode": 400,
  "error": "Validation Failed",
  "message": "Container name contains invalid characters (allowed: alphanumeric, dot, hyphen, underscore)"
}
```

**No Results (HTTP 200)**:
```json
{
  "alerts": [],
  "totalCount": 0,
  "containerName": "unknown-container"
}
```

---

### GET /api/v1/alerts/{alertId}/diagnostics

Retrieves the LLM-generated diagnostic analysis for a specific alert.

#### Request

**HTTP Method**: `GET`  
**Path**: `/api/v1/alerts/{alertId}/diagnostics`

**Path Parameters**:

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `alertId` | String | Yes | Alert identifier (format: `{containerName}-{epochMillis}`) |

**Constraints**:
- Max length: 512 characters
- Pattern: `^[a-zA-Z0-9_.-]+-\\d+$`
- Format: Container name followed by hyphen and epoch milliseconds

**Example Paths**:
- `/api/v1/alerts/payment-app-1779296400/diagnostics` ✅
- `/api/v1/alerts/auth.service-1779296401/diagnostics` ✅
- `/api/v1/alerts/invalid-id/diagnostics` ❌ (missing timestamp)
- `/api/v1/alerts/1779296400/diagnostics` ❌ (missing container name)

#### Response

**Success (HTTP 200)**:
```json
{
  "diagnosticId": "diag-payment-app-1779296400-1779296405",
  "alertId": "payment-app-1779296400",
  "status": "COMPLETED",
  "generatedAt": "2026-05-20T17:00:05Z",
  "confidenceScore": 0.85,
  "faultDomain": "APP_CODE",
  "rootCauseAnalysis": "{\"summary\":\"Memory leak detected in user session cache\",\"evidence\":[\"Heap usage increasing linearly over 6 hours\",\"Old gen GC ineffective (95% full after GC)\"],\"recommendations\":[\"Implement cache eviction policy (LRU with max size)\",\"Review session timeout configuration\"],\"relatedMetrics\":{\"heapUsedPercent\":92,\"gcTimePercent\":15}}"
}
```

**Status Pending (HTTP 200)**:
```json
{
  "diagnosticId": "diag-payment-app-1779296400-1779296405",
  "alertId": "payment-app-1779296400",
  "status": "PENDING",
  "generatedAt": "2026-05-20T17:00:05Z",
  "confidenceScore": null,
  "faultDomain": null,
  "rootCauseAnalysis": null
}
```

**Validation Error (HTTP 400)**:
```json
{
  "statusCode": 400,
  "error": "Validation Failed",
  "message": "Alert ID has invalid format (expected: containerName-epochMillis)"
}
```

**Not Found (HTTP 404)**:
```json
{
  "statusCode": 404,
  "error": "Not Found",
  "message": "No diagnostic analysis found for alert: payment-app-1779296400"
}
```
