---
name: prometheus-diagnostics
description: Activate whenever PROMETHEUS_METRICS or PROMETHEUS_RANGE_METRICS (Prometheus MCP) sections are present in the diagnostic context. Interprets live PromQL query results for CPU pressure, memory saturation, error rates, and request latency root cause analysis.
compatibility: Requires Causa diagnostic context collected from a Kind cluster with the Prometheus MCP Server deployed alongside kube-prometheus-stack.
metadata:
  category: diagnostics
  domain: kubernetes, prometheus
  mcp_server: prometheus-mcp-server
  tools: [query, range_query]
  context_sections:
    - PROMETHEUS_METRICS
    - PROMETHEUS_RANGE_METRICS
---

# Prometheus Diagnostics Skill

Interprets the Prometheus context already collected by Causa. The context contains up to two sections from the Prometheus MCP server — **PROMETHEUS_METRICS** (instant query) and **PROMETHEUS_RANGE_METRICS** (range query) — produced by the `query` and `range_query` tools respectively.

## What the Context Contains

Each section is a JSON string returned by the Prometheus HTTP API. The result format depends on the query type:

| Field | Type | Description |
|---|---|---|
| `result` | string | Space-separated series with label sets and sampled values |
| `warnings` | null or array | Any warnings returned by Prometheus alongside the result |

Each metric series is formatted as:
```
metric_name{label1="value1", label2="value2"} => <value> @[<unix_timestamp>]
```

For range queries, multiple `@[timestamp]` samples appear per series — one per step interval.

---

## Key Metrics for RCA

### CPU pressure

| PromQL expression | What it measures |
|---|---|
| `rate(container_cpu_usage_seconds_total[5m])` | Per-container CPU cores consumed — compare against `kube_pod_container_resource_limits{resource="cpu"}` |
| `container_cpu_cfs_throttled_seconds_total` | Cumulative seconds the container was CPU-throttled by the cgroup — elevated values mean the CPU limit is too low |
| `node_load1` | 1-minute node load average — high values indicate node-level CPU saturation affecting all pods |

### Memory pressure

| PromQL expression | What it measures |
|---|---|
| `container_memory_working_set_bytes` | Active memory in use — the value compared against the container memory limit for OOM risk |
| `kube_pod_container_resource_limits{resource="memory"}` | Configured memory limit for the container |
| `container_oom_events_total` | Cumulative OOM events for the container — non-zero confirms OOM kills |

**Memory utilisation formula:**
```
utilisation% = container_memory_working_set_bytes / kube_pod_container_resource_limits{resource="memory"} × 100
```

### Restart and error signals

| PromQL expression | What it measures |
|---|---|
| `kube_pod_container_status_restarts_total` | Total container restarts — a rising counter signals a crash loop |
| `rate(kube_pod_container_status_restarts_total[15m])` | Restart rate — non-zero means the container has been crashing within the window |

### HTTP error rates (if application exposes Prometheus metrics)

| PromQL expression | What it measures |
|---|---|
| `rate(http_server_requests_seconds_count{status=~"5.."}[5m])` | Rate of 5xx HTTP responses |
| `rate(http_server_requests_seconds_count{status=~"4.."}[5m])` | Rate of 4xx HTTP responses |
| `histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))` | 99th-percentile request latency |

---

## Interpretation Rules

### CPU throttling

**`container_cpu_cfs_throttled_seconds_total` is rising**
The container's CPU limit is too low. The kernel is throttling the process, which manifests as slow request handling, probe timeouts, and artificially long GC pauses even when heap pressure is low. Corroborate with POD EVENTS `Unhealthy` (probe timeout).

**`rate(container_cpu_usage_seconds_total[5m])` is near the CPU limit**
The container is consuming close to its allowed CPU ceiling. Combined with throttling metrics, this confirms CPU starvation. Fix: increase `resources.limits.cpu` or reduce request rate.

### Memory pressure

**`container_memory_working_set_bytes` ≥ 90% of memory limit**
The container is close to its OOM boundary. At 100% the kernel sends SIGKILL (exit code 137). Corroborate with POD EVENTS `OOMKilling` and `container_oom_events_total > 0`.

**`container_memory_working_set_bytes` is stable but OOM events occur**
The baseline footprint fits within the limit, but a transient spike triggers the kill. Look at range query data to see if usage spikes coincide with high request rates or GC events.

**`container_oom_events_total > 0`**
Confirms at least one OOM kill. Cross-reference with POD EVENTS `OOMKilling` and POD LOGS for in-process `OutOfMemoryError` vs. silent kernel kill (empty logs).

### Restart rate

**`rate(kube_pod_container_status_restarts_total[15m]) > 0`**
The container has been crashing within the last 15 minutes. This is the Prometheus-side confirmation of what POD EVENTS `BackOff` shows. Check POD LOGS for the first error before the crash loop began.

### Latency and errors

**`histogram_quantile(0.99, ...) > 2s`**
P99 latency above 2 seconds indicates the application is struggling. Combined with CPU throttling or GC overhead metrics (from Quarkus MCP if available), this identifies the bottleneck.

**Rising `rate(http_server_requests_seconds_count{status=~"5.."})` with no CPU/memory pressure**
Application-level errors unrelated to resource exhaustion. Check POD LOGS for stack traces or downstream dependency failures.

---

## Diagnostic Approach

### 1. Check memory utilisation first
- Compute `container_memory_working_set_bytes` / memory limit
- ≥ 90% → imminent OOM risk; corroborate with `container_oom_events_total` and POD EVENTS `OOMKilling`
- If limit is not in Prometheus data, check `kube_pod_container_resource_limits` or POD STATUS `Resource Limits Memory`

### 2. Check CPU throttling
- If `container_cpu_cfs_throttled_seconds_total` is non-zero and rising, CPU starvation is contributing
- Corroborate with POD EVENTS `Unhealthy` (probe timeouts caused by a throttled process)

### 3. Check restart rate
- `rate(kube_pod_container_status_restarts_total[15m]) > 0` confirms the crash loop window
- Cross-reference with POD EVENTS `BackOff` timestamps to confirm the loop is active

### 4. Check error rates and latency (if application metrics are available)
- Rising 5xx rate with normal CPU/memory → application-level root cause; check POD LOGS
- High P99 latency + CPU throttling → CPU starvation is slowing request processing

### 5. Correlate with other signals
- **POD EVENTS `OOMKilling`** + high `container_memory_working_set_bytes` → confirms OOM_KILLED
- **Quarkus MCP `jvm_gc_overhead`** + CPU throttling → confirms GC pauses amplified by throttling
- **Kruize recommendations** memory limit below current working set → confirms under-provisioning
- **POD LOGS `OutOfMemoryError`** + high memory working set → confirms heap exhaustion path

### 6. Note what is absent
- If both sections are `"No Data Available"`, state this and do not reference Prometheus metrics in `evidences`
- If the container does not expose application metrics, CPU and memory container metrics are still available from `cadvisor` — resource pressure signals remain valid
