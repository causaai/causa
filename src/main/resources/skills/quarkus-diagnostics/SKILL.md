---
name: quarkus-diagnostics
description: Activate whenever QUARKUS RAW METRICS (Quarkus MCP) section is present in the diagnostic context. Interprets live Micrometer/Prometheus metrics scraped from a Quarkus pod for JVM memory, GC, thread, and CPU root cause analysis.
compatibility: Requires Causa diagnostic context collected from Kubernetes/OpenShift pods running Quarkus with the Quarkus MCP server sidecar.
metadata:
  category: diagnostics
  domain: quarkus, jvm
  mcp_server: quarkus-mcp-server
  tool: fetch_raw_metrics_from_endpoint
  context_sections:
    - QUARKUS RAW METRICS (Quarkus MCP)
---

# Quarkus Diagnostics Skill

Interprets the Quarkus context already collected by Causa. The context contains one section from the Quarkus MCP server — **QUARKUS RAW METRICS (Quarkus MCP)** — produced by the `fetch_raw_metrics_from_endpoint` tool.

## What the Context Contains

The section is a JSON object with the following top-level fields:

| Field | Type | Description |
|---|---|---|
| `source` | string | Always `"metrics_endpoint"` |
| `base_url` | string | The Quarkus metrics endpoint that was scraped |
| `timestamp` | ISO-8601 string | When the snapshot was taken |
| `metric_count` | integer | Number of metric series in the snapshot |
| `metrics` | object | Flat map of `metric_name{labels}` → numeric value |

### Heap memory metrics

| Metric key (label pattern) | What it measures |
|---|---|
| `jvm_memory_used_bytes{area="heap",id="G1 Eden Space"}` | Young-gen Eden usage — bytes currently occupied |
| `jvm_memory_used_bytes{area="heap",id="G1 Old Gen"}` | Old-gen heap usage in bytes — the primary OOM pressure indicator |
| `jvm_memory_used_bytes{area="heap",id="G1 Survivor Space"}` | Survivor space usage — elevated after high young-gen allocation rate |
| `jvm_memory_committed_bytes{area="heap",id="G1 Old Gen"}` | Bytes the JVM has committed (reserved from OS) for old-gen |
| `jvm_memory_max_bytes{area="heap",id="G1 Old Gen"}` | Hard ceiling for old-gen (−1 means unbounded) |
| `jvm_gc_live_data_size_bytes` | Bytes of live objects after the last full GC — baseline memory footprint |
| `jvm_gc_max_data_size_bytes` | Max old-gen size used by GC tuning algorithms |

**Heap utilisation formula:**
```
utilisation% = jvm_memory_used_bytes{id="G1 Old Gen"} / jvm_memory_max_bytes{id="G1 Old Gen"} × 100
```
If `jvm_memory_max_bytes` is `−1`, use `jvm_gc_max_data_size_bytes` as the ceiling.

### Non-heap / Metaspace metrics

| Metric key | What it measures |
|---|---|
| `jvm_memory_used_bytes{area="nonheap",id="Metaspace"}` | Metaspace usage — high values indicate excessive class loading |
| `jvm_memory_committed_bytes{area="nonheap",id="Metaspace"}` | Committed Metaspace |
| `jvm_memory_used_bytes{area="nonheap",id="CodeCache"}` | JIT-compiled code cache — can OOM independently of heap |

### GC metrics

| Metric key | What it measures |
|---|---|
| `jvm_gc_overhead` | Fraction of wall-clock time spent in GC (0.0–1.0). Values above `0.10` indicate significant GC pressure. |
| `jvm_gc_memory_allocated_bytes_total` | Cumulative bytes allocated since JVM start |
| `jvm_gc_memory_promoted_bytes_total` | Cumulative bytes promoted from young-gen to old-gen |

### Thread metrics

| Metric key | What it measures |
|---|---|
| `jvm_threads_live_threads` | Total live threads |
| `jvm_threads_daemon_threads` | Daemon threads |
| `jvm_threads_peak_threads` | Peak thread count since JVM start |
| `jvm_threads_states_threads{state="blocked"}` | Threads blocked on a monitor lock — elevated means lock contention |
| `jvm_threads_states_threads{state="runnable"}` | Threads actively running on CPU |
| `jvm_threads_states_threads{state="waiting"}` | Threads in `Object.wait()` — normal for idle pool threads |
| `jvm_threads_states_threads{state="timed-waiting"}` | Threads in `Thread.sleep()` or timed waits |

### CPU and process metrics

| Metric key | What it measures |
|---|---|
| `process_cpu_usage` | Process CPU utilisation (0.0–1.0 fraction of one core) |
| `system_cpu_usage` | Host CPU utilisation (0.0–1.0 across all cores) |
| `system_cpu_count` | Number of logical CPUs available |
| `system_load_average_1m` | 1-minute system load average |
| `process_uptime_seconds` | JVM uptime since last restart |

### Vert.x worker pool metrics

| Metric key | What it measures |
|---|---|
| `worker_pool_active{pool_name="vert.x-worker-thread"}` | Threads currently executing tasks |
| `worker_pool_idle{pool_name="vert.x-worker-thread"}` | Idle threads available for work |
| `worker_pool_queue_size{pool_name="vert.x-worker-thread"}` | Pending tasks queued but not yet executing |
| `worker_pool_usage_seconds_max{pool_name="vert.x-worker-thread"}` | Longest task execution time in the current window |
| `worker_pool_ratio{pool_name="vert.x-worker-thread"}` | Active/total ratio (NaN if pool is fully idle) |

---

## Interpretation Rules

### Heap pressure

**Old-gen utilisation ≥ 85%**
The heap is nearly full. Combined with `jvm_gc_overhead > 0.05`, this indicates a GC spiral — GC is running frequently but reclaiming little, which is a precursor to OOM. Corroborate with Cryostat JFR MEMORY ANALYSIS and POD EVENTS for `OOMKilling`.

**Old-gen utilisation ≥ 95%**
Imminent OOM. If `jvm_gc_overhead` is also elevated, the JVM is spending most of its time in GC with almost no application throughput. Treat as equivalent evidence to `OOM_KILLED` category.

**`jvm_gc_live_data_size_bytes` is large relative to old-gen max**
The baseline memory footprint of live objects is high. This leaves little headroom for transient allocations and means the container memory limit may need to increase, or application state needs to be reduced.

**`jvm_gc_live_data_size_bytes` is 0.0**
No full GC has occurred yet — old-gen data is not available. Do not draw conclusions from this field alone.

### GC pressure

**`jvm_gc_overhead > 0.10`**
More than 10% of wall-clock time is in GC. This causes application latency spikes and, if combined with high heap utilisation, is the root of `POSSIBLE_GC_PAUSE` categorisation.

**`jvm_gc_overhead = 0.0`**
No GC pressure at snapshot time. If a crash still occurred, the cause is not GC-driven — look at POD EVENTS for OOM kill or logs for application-level errors.

**`jvm_gc_memory_promoted_bytes_total` is high**
High promotion rate means objects created in young-gen are surviving into old-gen instead of being collected. Common causes: long-lived caches, sessions, or connection pools.

### Thread health

**`jvm_threads_states_threads{state="blocked"} > 0`**
One or more threads are blocked on a monitor lock. Combined with `worker_pool_active` near maximum and a growing `worker_pool_queue_size`, this indicates lock contention causing request queuing.

**`worker_pool_queue_size > 0` consistently**
The worker pool is saturated. Requests are queuing faster than they are being processed. Causes: slow downstream I/O, long GC pauses, or CPU throttling.

**`jvm_threads_live_threads` >> `jvm_threads_peak_threads` at time of alert**
Thread count has grown since start — possible thread leak. Corroborate with Cryostat THREAD ANALYSIS.

### CPU

**`process_cpu_usage` is very low but `system_cpu_usage` is high**
The host is CPU-saturated by other processes. The pod may be CPU-throttled at the cgroup level even if its Kubernetes CPU limit appears sufficient.

**`process_cpu_usage` > `system_cpu_usage`**
Unusual — the process is using a disproportionate share of total CPU. Combined with high `worker_pool_active`, the application may be in a CPU-intensive loop (e.g., infinite retry, serialisation storm).

---

## Diagnostic Approach

### 1. Compute heap utilisation first
- Calculate `jvm_memory_used_bytes{id="G1 Old Gen"}` / `jvm_memory_max_bytes{id="G1 Old Gen"}`
- If max is −1, use `jvm_gc_max_data_size_bytes` as the denominator
- Corroborate elevated utilisation with `jvm_gc_overhead`, Cryostat JFR MEMORY ANALYSIS, and POD EVENTS for `OOMKilling`

### 2. Check GC overhead
- `jvm_gc_overhead = 0.0` — no current GC pressure (may have recovered before snapshot)
- `0.01–0.10` — moderate, monitor
- `> 0.10` — significant; corroborate with Cryostat JFR GC ANALYSIS for pause durations

### 3. Inspect thread states
- Non-zero `blocked` threads → lock contention → check Cryostat THREAD ANALYSIS for deadlocks
- High `worker_pool_queue_size` → saturation → determine if cause is I/O latency, GC pauses, or CPU throttle

### 4. Correlate with other signals
- **POD EVENTS `OOMKilling`** + high old-gen utilisation → confirms OOM_KILLED
- **Cryostat GC ANALYSIS** long pauses + `jvm_gc_overhead > 0.10` → confirms POSSIBLE_GC_PAUSE
- **Kruize recommendations** memory limit below current old-gen committed → confirms under-provisioning
- **POD LOGS `OutOfMemoryError`** + high old-gen → confirms heap exhaustion path

### 5. Note what is absent
- If `jvm_gc_overhead = 0.0` and heap utilisation is low, Quarkus metrics do not support a memory-related root cause; look to other signals
- If the section is `"No Data Available"`, state this in `llm_notes` and do not reference Quarkus metrics in `evidences`
