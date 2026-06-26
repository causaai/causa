---
name: kubernetes-diagnostics
description: Expert knowledge for diagnosing Kubernetes pod failures, analyzing events, and interpreting container logs.
compatibility: Reference knowledge for Kubernetes troubleshooting
metadata:
  category: diagnostics
  domain: kubernetes
---

# Kubernetes Diagnostics Skill

Expert diagnostic knowledge for Kubernetes pod failures. Provides diagnostic guidance and root cause analysis - MCP tool execution handled by backend infrastructure.

## Exit Code Reference

**Critical Exit Codes**:
- **0**: Success
- **1**: Application error
- **137**: OOMKilled (SIGKILL = 128 + 9) - Container exceeded memory limit
- **139**: Segmentation fault
- **143**: SIGTERM (graceful shutdown)
- **255**: Exit status out of range

## Common Failure Patterns

### OOMKilled (Exit 137)

**Signals**:
- `exitCode: 137`, `reason: OOMKilled`
- Event: `Warning OOMKilling`
- Logs: `OutOfMemoryError` or empty (killed mid-write)
- High restart count

**Root Cause**: Container exceeded `resources.limits.memory`

**Fix**:
```yaml
resources:
  requests:
    memory: <CURRENT_LIMIT / 2>
  limits:
    memory: <CURRENT_LIMIT * 2>  # Double current limit or use observed peak + buffer
```

**Prevent**: Profile memory usage, fix leaks, tune JVM heap

---

### CrashLoopBackOff

**Signals**:
- `state: waiting`, `reason: CrashLoopBackOff`
- Event: `BackOff restarting failed container`
- Exponential backoff timing increases with each restart

**Root Cause**: Application exits immediately after startup

**Common Triggers**:
- Missing environment variables or configuration
- Dependency unavailable (database, external service)
- Port conflict
- Unhandled startup exception

**Diagnosis**: Check logs for first error before restart pattern begins

**Fix**: Address startup failure (add config, ensure dependencies, fix code)

---

### ImagePullBackOff

**Signals**:
- `state: waiting`, `reason: ImagePullBackOff`
- Event: `Failed to pull image`

**Root Causes**:
- Image doesn't exist or tag incorrect
- Registry auth failed (check `imagePullSecrets`)
- Network policy blocking registry access

**Quick Test**: `docker pull <image:tag>` to verify accessibility

---

### Pending Pod

**Signals**:
- `phase: Pending`
- Event: `FailedScheduling`

**Root Causes**:
- Insufficient node resources (CPU/memory)
- Node selector/affinity mismatch
- Taints without matching tolerations
- PVC not bound

**Fix**: Scale cluster, adjust resource requests, or fix scheduling constraints

---

## Event Pattern Recognition

**Warning Events** (actionable):
- `OOMKilling` → Increase memory
- `BackOff` → Fix application startup
- `FailedScheduling` → Add resources or fix constraints
- `Unhealthy` → Liveness/readiness probe failing
- `ImagePullBackOff` → Fix image/registry access

**Pattern Signals**:
- Repeated `Killing` → Resource limits too tight
- Multiple `BackOff` → Application initialization bug
- `FailedScheduling` + `Pending` → Insufficient cluster capacity

---

## Log Analysis Patterns

**Memory Issues**:
- Java: `OutOfMemoryError`, `java.lang.OutOfMemoryError`
- Go: `fatal error: runtime: out of memory`
- Python: `MemoryError`

**Startup Failures**:
- `Connection refused` → Dependency not ready
- `bind: address already in use` → Port conflict
- `FATAL` / `panic:` during init → Startup bug

**Network Issues**:
- `timeout` → Service unreachable
- `connection reset` → Network instability
- `no such host` → DNS/service discovery failure

---

## Diagnostic Approach

### 1. Identify Symptom
- Not running? → Check `phase` and `containerStatuses[].state`
- Restarting? → Check `restartCount` and `exitCode`
- Stuck pending? → Check events for `FailedScheduling`

### 2. Correlate Data
- Match `exitCode` to known patterns (137=OOM, 1=error)
- Link event timestamps to restart times
- Find first error in logs before crash pattern

### 3. Form Hypothesis
**Example**: Exit 137 + `OOMKilling` event + `OutOfMemoryError` log = memory limit exceeded

### 4. Recommend Fix
- Specific: YAML patch, kubectl command, or code change
- Explain root cause, not just symptom
- Suggest monitoring to verify fix

---

## Pod Status Fields

**`status.phase`**:
- `Running` → Normal operation
- `Pending` → Waiting to schedule/start
- `Failed` → Terminated with error
- `Succeeded` → Completed successfully
- `Unknown` → Status cannot be determined

**`containerStatuses[].state`**:
- `running` → Active
- `waiting` → Not started (check `reason`)
  - Common reasons: `CrashLoopBackOff`, `ImagePullBackOff`, `ContainerCreating`
- `terminated` → Exited (check `exitCode` and `reason`)

**`restartCount`**: High values indicate instability (crash loop or probe failures)

**`ready`**: `false` = readiness probe failing or container not started

---

## Quick Reference: Diagnosis Steps by Symptom

| Symptom | Check | Look For |
|---------|-------|----------|
| High restart count | Exit code + logs | 137=OOM, 1=error, startup exceptions |
| Pending pod | Events | `FailedScheduling`, resource constraints |
| Image issues | Events | `ImagePullBackOff`, auth failures |
| Slow startup | Readiness probe logs | Timeouts, probe failures |
| Intermittent failures | Events over time | Pattern of `Unhealthy` or `Killing` |

---

## Best Practices

1. **Start with exit code** - Maps directly to failure type (137=OOM, 1=error)
2. **Check events chronologically** - Shows timeline of what Kubernetes observed
3. **Focus on first error in logs** - Before crash loop pattern begins
4. **Correlate restart times** - Match log errors to event timestamps
5. **Provide concrete fixes** - YAML changes, kubectl commands, not just "check logs"
