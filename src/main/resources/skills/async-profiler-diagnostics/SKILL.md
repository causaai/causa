---
name: async-profiler-diagnostics
description: Activate whenever any ASYNC PROFILER section is present in the diagnostic context. Interprets JVM profiling data collected from a Jafra-sidecar pod via the Async Profiler MCP server for CPU hotspot, memory, GC, and flame-graph root cause analysis.
compatibility: Requires Causa diagnostic context collected from Kubernetes/OpenShift pods running the Jafra async-profiler sidecar with the Async Profiler MCP server.
metadata:
  category: diagnostics
  domain: jvm, async-profiler, jfr
  mcp_server: async-profiler-mcp-server
  tools:
    - list_profiled_pods
    - get_pod_jvm_status
    - get_jvm_statistics
    - get_recording
    - get_recording_report
    - get_jfr_summary
    - get_flame_graph
  context_sections:
    - ASYNC PROFILER — PROFILED PODS
    - ASYNC PROFILER — JVM STATUS
    - ASYNC PROFILER — JVM STATISTICS
    - ASYNC PROFILER — LATEST RECORDING
    - ASYNC PROFILER — RECORDING REPORT (CPU/JVM)
    - ASYNC PROFILER — JFR SUMMARY (LLM-ready)
    - ASYNC PROFILER — FLAME GRAPH (call stack)
---

# Async Profiler Diagnostics Skill

Interprets the Async Profiler context already collected by Causa. The context may contain up to seven sections from the Async Profiler MCP server (Jafra backend).

## What the Context Contains

### Section 1 — ASYNC PROFILER — PROFILED PODS (`list_profiled_pods`)

JSON array of registered pods. Each entry contains:

| Field | Description |
|---|---|
| `podName` | Kubernetes pod name |
| `namespace` | Pod namespace |
| `latestRecordingId` | ID of the most recent JFR recording (null if none) |
| `profilerStatus` | `RUNNING`, `STOPPED`, or `UNKNOWN` |

**Key check**: If `latestRecordingId` is null for the alerting pod, tools 4–7 were skipped. Note this in `llm_notes`.

### Section 2 — ASYNC PROFILER — JVM STATUS (`get_pod_jvm_status`)

Profiler readiness and live JVM health for the pod:

| Field | Description |
|---|---|
| `profilerAttached` | Whether async-profiler agent is attached |
| `jvmPid` | JVM process ID inside the container |
| `heapUsedBytes` / `heapMaxBytes` | Live heap snapshot |
| `gcCollectionCount` | Total GC collections since JVM start |
| `threadCount` | Current live thread count |

### Section 3 — ASYNC PROFILER — JVM STATISTICS (`get_jvm_statistics`)

Live heap/GC/thread snapshot at collection time:

| Field | Description |
|---|---|
| `heapUsedMb` / `heapMaxMb` | Heap usage and ceiling in MiB |
| `heapUtilisationPct` | `heapUsedMb / heapMaxMb × 100` |
| `gcPauseTimeMs` | Latest GC pause duration in ms |
| `gcCollections` | GC collection count |
| `liveThreads` | Current thread count |
| `daemonThreads` | Daemon thread count |

### Section 4 — ASYNC PROFILER — LATEST RECORDING (`get_recording`)

Metadata for the latest JFR recording:

| Field | Description |
|---|---|
| `recordingId` | Recording identifier |
| `state` | `RUNNING`, `STOPPED`, `CLOSED` |
| `startTime` / `duration` | Recording window |
| `sizeBytes` | Approximate recording file size |

### Section 5 — ASYNC PROFILER — RECORDING REPORT (`get_recording_report`)

JMC rule-based analysis of the live JFR stream (Jafra `/report`). Contains structured findings for:
- **Memory leaks** — objects growing without bound across GC cycles
- **GC pauses** — pause duration, GC algorithm, promotion failures
- **CPU bottlenecks** — hot methods, lock contention
- **Thread stalls** — threads blocked/parked longer than expected

### Section 6 — ASYNC PROFILER — JFR SUMMARY (`get_jfr_summary`)

LLM-ready compact narrative from the live JFR stream (Jafra `/summary`). Contains:
- Top CPU-consuming methods and their stack depth
- Heap allocation hot paths
- Lock contention summary
- GC activity overview with pause durations

### Section 7 — ASYNC PROFILER — FLAME GRAPH (`get_flame_graph`)

JSON call-stack frame tree. Each node:

| Field | Description |
|---|---|
| `name` | Method/frame name |
| `value` | Self-sample count (CPU ticks) |
| `children` | Child frames |

The root frame's `value` is the total sample count. A method's weight = `node.value / root.value × 100 %`.

---

## Interpretation Rules

### Heap pressure

**`heapUtilisationPct ≥ 85 %`**
Heap is nearly exhausted. Combined with elevated `gcCollections` or a JFR SUMMARY showing frequent GC, this is a precursor to OOM. Corroborate with Cryostat MEMORY ANALYSIS and POD EVENTS for `OOMKilling`.

**`heapUsedMb > heapMaxMb × 0.95`**
Imminent OOM. If `gcPauseTimeMs` is also elevated, the JVM is spending most time in GC with minimal throughput. Treat as equivalent evidence to `POSSIBLE_OOM_KILLED`.

**`heapMaxMb` is significantly larger than the container memory limit**
The JVM heap ceiling exceeds the cgroup limit. The container will be OOMKilled before the JVM can trigger its own OOM handler. This is a critical misconfiguration — set `-Xmx` to ≤ 75 % of the container limit.

### GC pressure

**`gcPauseTimeMs > 100`**
GC pauses above 100 ms cause application latency spikes. Combined with high heap utilisation, this is the root of `POSSIBLE_GC_PAUSE` categorisation.

**`gcCollections` growing rapidly across context windows**
High GC frequency indicates the heap is too small for the workload or that objects are being promoted to old-gen faster than they are reclaimed.

### CPU hotspots (Flame Graph)

**A single method dominates ≥ 30 % of samples**
That method is a CPU hotspot. If it is in application code (non-JVM internals), flag it as a root cause candidate for CPU-related alerts.

**GC-related frames (`G1CollectedHeap`, `ParallelScavengeHeap`) appear in top frames**
The JVM is spending significant CPU time in garbage collection. Corroborate with `gcPauseTimeMs` and Cryostat GC ANALYSIS.

**Lock frames (`ObjectMonitor::enter`, `AbstractQueuedSynchronizer`) dominate**
Thread contention is causing CPU spin. Corroborate with Cryostat THREAD ANALYSIS for deadlock or blocked-thread evidence.

### JFR Summary signals

**Memory leak indicators in RECORDING REPORT**
If the report flags a `MemoryLeakRule` or `ObjectCountAfterGCRule`, the application has objects accumulating across GC cycles. Flag in `root_cause` and recommend heap dump analysis.

**`gcPauseTimeMs` reported in RECORDING REPORT > 500 ms**
Long-pause GC (e.g., SerialGC on G1-configured JVM, or humongous allocations triggering full GC). This causes application freezes visible as latency spikes or liveness probe failures.

---

## Diagnostic Approach

### 1. Establish heap headroom
- Compute `heapUtilisationPct` from JVM STATISTICS
- Compare `heapMaxMb` against the container memory limit from POD STATUS or Cryostat CONTAINER ANALYSIS
- Flag if JVM max heap > 75 % of container limit

### 2. Assess GC activity
- Check `gcPauseTimeMs` from JVM STATISTICS — values > 100 ms warrant investigation
- Cross-reference with Cryostat GC ANALYSIS for pause duration histograms

### 3. Analyse flame graph for CPU root cause
- Identify the top 3 frames by sample weight
- Distinguish application code from JVM internals
- Flag GC or lock frames that consume > 15 % of samples

### 4. Use JFR Summary and Recording Report
- RECORDING REPORT provides structured rule violations — treat these as high-confidence evidence
- JFR SUMMARY provides narrative context — use verbatim excerpts as `evidences` entries

### 5. Correlate with other signals
- **POD EVENTS `OOMKilling`** + `heapUtilisationPct ≥ 85 %` → confirms `OOM_KILLED`
- **Cryostat GC ANALYSIS** long pauses + `gcPauseTimeMs > 100` → confirms `POSSIBLE_GC_PAUSE`
- **Kruize memory limit recommendation** below current `heapMaxMb` → confirms under-provisioning
- **POD LOGS `OutOfMemoryError`** + high heap utilisation → confirms heap exhaustion path

### 6. Note what is absent
- If all Async Profiler sections are `"No Data Available"`, the MCP endpoint was not configured or the pod is not registered with Jafra — state this in `llm_notes` and do not reference Async Profiler data in `evidences`
- If only tools 1–3 ran (no `latestRecordingId`), note that JFR recording-based analysis (sections 4–7) was unavailable
