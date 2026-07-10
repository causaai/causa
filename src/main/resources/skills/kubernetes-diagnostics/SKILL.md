---
name: kubernetes-diagnostics
description: Activate whenever Kubernetes MCP context is present. Interprets POD STATUS, POD EVENTS, and POD LOGS for container root cause analysis.
compatibility: Requires Causa diagnostic context collected from Kubernetes/OpenShift pods.
metadata:
  category: diagnostics
  domain: kubernetes
---

# Kubernetes Diagnostics Skill

Interprets the Kubernetes context already collected by Causa. The context contains three sections from the MCP server — **POD STATUS**, **POD EVENTS**, and **POD LOGS**.

## What the Context Contains

### POD STATUS fields
- `State` — container state: `Running`, `Waiting`, or `Terminated`
- `Started At` — timestamp the container last started
- `Restart Count` — number of times the container has restarted
- `Resource Limits` — CPU and Memory hard limits set on the container
- `Resource Requests` — CPU and Memory requests set on the container

### POD EVENTS format
Each event is formatted as: `[Type] timestamp: Reason - Message`
- `Type` is `Normal` or `Warning`
- `Reason` is the short event keyword (e.g. `OOMKilling`, `BackOff`, `Unhealthy`)
- `Message` is the human-readable detail — the exit code is embedded in the `Message` text (e.g. `"exit code 137"`), not as a standalone field

### Exit codes in event Message text

| Exit Code | Meaning |
|-----------|---------|
| **137** | Container killed by the kernel (SIGKILL) — process memory exceeded `Resource Limits Memory` |
| **143** | Container received SIGTERM — graceful shutdown by Kubernetes (rolling restart, scale down) |
| **1** | Process exited with a general error — check POD LOGS for a stack trace or startup error |
| **2** | Process argument or flag error — check POD LOGS for initialisation errors |

### POD LOGS
Last 25 lines of container stdout. May be empty or truncated if the container was killed mid-write.

---

## Interpreting POD STATUS

**`State: Terminated` + `Restart Count > 0`**
The container exited and Kubernetes restarted it. Read POD EVENTS for the exit reason and POD LOGS for the last output before the crash.

**`State: Waiting`**
The container has not started. Check POD EVENTS for `BackOff` (crash loop) or image pull failures.

**`State: Running` + `Restart Count > 0`**
The pod is currently running but has crashed before. Correlate with POD EVENTS and POD LOGS from previous restarts.

**`Resource Limits Memory`**
The memory limit is the hard ceiling for the entire container process. If the process exceeds it, the kernel sends SIGKILL (exit code 137) with no application-level error in logs.

**`Resource Limits CPU`**
A very low CPU limit throttles the process and can cause slow startup or probe timeouts even when the application itself is functioning.

---

## Interpreting POD EVENTS

| Event Reason | What it means |
|---|---|
| `OOMKilling` | Kernel killed the container — total process memory exceeded `Resource Limits Memory`. POD LOGS may be empty (killed mid-write) or contain an out-of-memory error if the process detected it first. |
| `BackOff` | Container is exiting repeatedly on startup. Check POD LOGS for the first error before the restart pattern begins. |
| `Unhealthy` | Liveness or readiness probe failed. Can be caused by a slow startup, saturated threads, or a long GC pause. |
| `Killing` (repeated) | Kubernetes is killing the container due to probe failure or OOM. Repeated `Killing` with no `OOMKilling` usually points to probe configuration. |
| `Pulled` / `Started` | Normal lifecycle events — useful for correlating with log timestamps. |

---

## Interpreting POD LOGS

**OOM kill with logs present**
An out-of-memory error in the logs means the process exhausted its in-process memory limit before the container limit was hit. Fix: increase the configured memory limit or reduce usage.

**OOM kill with empty or truncated logs**
The kernel killed the process before it could write. The container limit was hit by total process memory. Fix: increase `Resource Limits Memory`.

**Startup exceptions in logs**
The first error before a `BackOff` event is the root cause. Subsequent restarts repeat the same error. Look for `FATAL`, `ERROR`, or connection errors on the earliest log lines.

---

## Diagnostic Approach

### 1. Read POD STATUS first
- Note `State`, `Restart Count`, and `Resource Limits`
- High `Restart Count` means the container has been crashing — the current state may not reflect the failure

### 2. Read POD EVENTS chronologically
- `OOMKilling` → container memory limit hit; check POD LOGS for in-process OOM vs silent kernel kill
- `BackOff` → container crashing on startup; find first log error before the loop begins
- `Unhealthy` → probe failure; check if slow startup or resource exhaustion is the cause

### 3. Read POD LOGS
- Find the **first** error line — not the last — to identify root cause before crash loop noise
- Empty logs after an OOM event mean the container was killed before the process could write

### 4. Correlate resource limits
- Compare `Resource Limits Memory` with any memory pressure evidence in logs or events
- If limits appear sufficient, check whether the total process footprint (including non-heap memory) is the cause

### 5. Recommend a concrete fix
- Specify whether the fix is a container limit change, a process configuration change, or both
- Reference the specific POD STATUS field, event, or log line that supports the recommendation
