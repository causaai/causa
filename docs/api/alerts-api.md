# Alerts API

This document covers the webhook ingestion endpoint and alert retrieval endpoints.

**Base URLs:** `{{BASE_URL}}` or `http://localhost:8080`

## Endpoints

| Endpoint | Purpose | Success response code | Error response codes |
|---|---|---|---|
| `POST /api/v1/webhooks/alerts` | Ingest Alertmanager webhook payloads and trigger diagnostics for accepted alerts | `200 OK` | `400 Bad Request`, `500 Internal Server Error` |
| `GET /api/v1/alerts` | List all alerts or filter by `workload_name` and `namespace` | `200 OK` | `400 Bad Request`, `500 Internal Server Error` |
| `GET /api/v1/alerts/{id}` | Fetch one alert by id | `200 OK` | `404 Not Found` |

---

## POST `/api/v1/webhooks/alerts`

Receives a Prometheus Alertmanager webhook payload, persists alerts, applies filtering rules, and triggers diagnostics for accepted alerts.

### Request Example (cURL)

**Using `{{BASE_URL}}`:**

```bash
curl -X POST {{BASE_URL}}/api/v1/webhooks/alerts \
  -H 'Content-Type: application/json' \
  -d '{{PAYLOAD_JSON_HERE}}'
```

**Using `localhost:8080`:**

```bash
curl -X POST http://localhost:8080/api/v1/webhooks/alerts \
  -H 'Content-Type: application/json' \
  -d '{{PAYLOAD_JSON_HERE}}'
```

### Request Payload Example

```json
{
  "receiver": "Critical",
  "status": "firing",
  "alerts": [
    {
      "status": "firing",
      "labels": {
        "alertname": "causa-high-memory",
        "app": "${APP_NAME}",
        "container": "${CONTAINER_NAME}",
        "namespace": "${APP_NAMESPACE}",
        "openshift_io_alert_source": "platform",
        "pod": "${POD_NAME}",
        "prometheus": "openshift-monitoring/k8s",
        "severity": "critical"
      },
      "annotations": {
        "app_name": "${APP_NAME}",
        "container_name": "${CONTAINER_NAME}",
        "description": "Pod currently using more memory than threshold.",
        "diagnostic_hint": "possible_memory_pressure",
        "memory_threshold": "${MEMORY_THRESHOLD}",
        "namespace": "${APP_NAMESPACE}",
        "pod_name": "${POD_NAME}",
        "summary": "Container is using more than ${MEMORY_THRESHOLD} of its memory limit.",
        "workload_name": "${CONTAINER_NAME}"
      },
      "startsAt": "2026-08-05T10:24:19.001Z",
      "endsAt": "0001-01-01T00:00:00Z",
      "generatorURL": "https://console-openshift-console.apps.cluster-9lsk4.9lsk4.sandbox2138.opentlc.com/monitoring/",
      "fingerprint": "bdb17c2f6ad077fc"
    }
  ],
  "groupLabels": {
    "namespace": "${APP_NAMESPACE}"
  },
  "commonLabels": {
    "alertname": "causa-high-memory",
    "app": "${APP_NAME}",
    "container": "${CONTAINER_NAME}",
    "namespace": "${APP_NAMESPACE}",
    "openshift_io_alert_source": "platform",
    "pod": "${POD_NAME}",
    "prometheus": "openshift-monitoring/k8s",
    "severity": "critical"
  },
  "commonAnnotations": {
    "app_name": "${APP_NAME}",
    "container_name": "${CONTAINER_NAME}",
    "description": "App: ${APP_NAME}\nContainer ${CONTAINER_NAME} in pod ${POD_NAME} (namespace ${APP_NAMESPACE})\n is currently using more memory than threshold ${MEMORY_THRESHOLD}.\n",
    "diagnostic_hint": "possible_memory_pressure",
    "memory_threshold": "${MEMORY_THRESHOLD}",
    "namespace": "${APP_NAMESPACE}",
    "pod_name": "${POD_NAME}",
    "summary": "Container ${CONTAINER_NAME} in pod ${POD_NAME} (${APP_NAME}) is using more than ${MEMORY_THRESHOLD} of its memory limit.",
    "workload_name": "${CONTAINER_NAME}"
  },
  "externalURL": "https://console-openshift-console.apps.cluster-9lsk4.9lsk4.sandbox2138.opentlc.com/monitoring",
  "version": "4",
  "groupKey": "{}/{alertname=~\".*causa.*\"}:{namespace=\"${APP_NAMESPACE}\"}",
  "truncatedAlerts": 0
}
```

### Response Example - Accepted Alert

```json
{
  "status": "accepted",
  "message": "All 1 alerts accepted and diagnostics initiated",
  "totalReceived": 1,
  "totalAccepted": 1,
  "totalRejected": 0,
  "accepted": {
    "alrt_wXhKJWpfPoJTWMZN": "diag_6YuCDWBMfwzWoMhY"
  },
  "rejected": {},
  "timestamp": "2026-08-06T07:18:22.848519Z"
}
```

### Duplicate Alert Example

The same `causa-high-memory` payload was sent two more times to verify cooldown behavior.

```json
{
  "status": "rejected",
  "message": "All 1 alerts rejected (severity/namespace/cooldown)",
  "totalReceived": 1,
  "totalAccepted": 0,
  "totalRejected": 1,
  "accepted": {},
  "rejected": {
    "alrt_HXPyTnGdKXexU0ql": "Alert is in cooldown — next alert from this workload will be processed at: 2026-08-06 08:07:53 UTC"
  },
  "timestamp": "2026-08-06T07:52:53.536062Z"
}
```

### Rejected example - Low Severity

This payload used `severity: info` while the current minimum accepted severity is `critical`.

```json
{
  "status": "rejected",
  "message": "All 1 alerts rejected (severity/namespace/cooldown)",
  "totalReceived": 1,
  "totalAccepted": 0,
  "totalRejected": 1,
  "accepted": {},
  "rejected": {
    "alrt_pu9Hr9NUVvHxScJe": "Severity too low — received: info, minimum required: critical"
  },
  "timestamp": "2026-08-06T07:54:18.882145Z"
}
```

### Notes

- Accepted alerts are returned as a map of `alertId -> diagnosticId`.
- Rejected alerts are returned as a map of `source identifier -> rejection reason`.
- The payload above is the provided `causa-high-memory` webhook payload.
- Re-sending the same alert can trigger cooldown rejection, as shown above.

---

## GET `/api/v1/alerts`

Returns all alerts, or filters alerts using `workload_name` and `namespace` query parameters.

### Query parameters

| Name | Required | Description |
|---|---|---|
| `workload_name` | No | Filter alerts by workload name |
| `namespace` | No | Filter alerts by namespace |

### Request Example (cURL)

**Using `{{BASE_URL}}`:**

```bash
curl {{BASE_URL}}/api/v1/alerts
```

**Using `localhost:8080`:**

```bash
curl http://localhost:8080/api/v1/alerts
```

### Response Example

```json
[
  {
    "id": "alrt_bh7V9aOriq48ltRM",
    "source_alert_id": "abc123def456",
    "alert_name": "HighMemoryUsage212",
    "alert_timestamp": "2026-07-10T00:00:00Z",
    "severity": "critical",
    "status": "PROCESSED",
    "workload_info": {
      "pod_name": "heap-oom-prom-75d49588db-tqhrg",
      "container_name": "heap-oom-prom",
      "namespace": "chaos-test",
      "cluster_name": null,
      "workload_type": null
    },
    "workload_name": "heap-oom-prom",
    "labels": {
      "severity": "critical",
      "container": "heap-oom-prom",
      "pod": "heap-oom-prom-75d49588db-tqhrg",
      "alertname": "HighMemoryUsage212",
      "namespace": "chaos-test"
    },
    "annotations": {
      "summary": "Memory usage above 90%",
      "description": "Pod heap-oom-prom-5f5889d58c-lx4ww memory usage is at 93%",
      "workload_name": "hulala"
    },
    "alert_source": "prometheus"
  },
  {
    "id": "alrt_9iV3Ph0LiheZUB6p",
    "source_alert_id": "partial-crit-fp-001",
    "alert_name": "CriticalAlert",
    "alert_timestamp": "2026-07-10T01:00:00Z",
    "severity": "critical",
    "status": "PROCESSED",
    "workload_info": {
      "pod_name": null,
      "container_name": "api",
      "namespace": "prod",
      "cluster_name": null,
      "workload_type": null
    },
    "workload_name": "api-server",
    "labels": {
      "severity": "critical",
      "container": "api",
      "alertname": "CriticalAlert",
      "namespace": "prod",
      "workload_name": "api-server"
    },
    "annotations": {
      "summary": "Critical",
      "workload_name": "api-server"
    },
    "alert_source": "prometheus"
  }
]
```

### Filtered example

**Using `{{BASE_URL}}`:**

```bash
curl '{{BASE_URL}}/api/v1/alerts?workload_name=heap-oom-prom&namespace=chaos-test'
```

**Using `localhost:8080`:**

```bash
curl '{{BASE_URL}}/api/v1/alerts?workload_name=heap-oom-prom&namespace=chaos-test'
```

---

## GET `/api/v1/alerts/{id}`

Returns one alert by id.

### Request Example (cURL)

**Using `{{BASE_URL}}`:**

```bash
curl {{BASE_URL}}/api/v1/alerts/alrt_wXhKJWpfPoJTWMZN
```

**Using `localhost:8080`:**

```bash
curl http://localhost:8080/api/v1/alerts/alrt_wXhKJWpfPoJTWMZN
```

### Response Example

```json
{
  "id": "alrt_Pq9niv5mucnfsdCO",
  "source_alert_id": "cluster-fp-abc123def456",
  "alert_name": "HighMemoryUsage",
  "alert_timestamp": "2026-07-10T00:00:00Z",
  "severity": "critical",
  "status": "PROCESSED",
  "workload_info": {
    "pod_name": "liberty-perf-74c9f57d5-dbtcl",
    "container_name": "liberty-perf",
    "namespace": "chaos-test",
    "cluster_name": null,
    "workload_type": null
  },
  "workload_name": "liberty-perf",
  "labels": {
    "severity": "critical",
    "container": "liberty-perf",
    "pod": "liberty-perf-74c9f57d5-dbtcl",
    "alertname": "HighMemoryUsage",
    "namespace": "chaos-test",
    "workload_name": "liberty-perf"
  },
  "annotations": {
    "summary": "Memory usage above 90%",
    "description": "Pod liberty-perf-74c9f57d5-dbtcl memory usage is at 93%",
    "workload_name": "liberty-perf"
  },
  "alert_source": "prometheus"
}
```

### Error example

If the id does not exist, the API returns `404` with this shape:

```json
{
  "statusCode": 404,
  "error": "Not Found",
  "message": "No alert found with id: <id>"
}
```
