---
name: kruize-optimization
description: Activate whenever RESOURCE COST RECOMMENDATIONS (Kruize) or RESOURCE PERFORMANCE RECOMMENDATIONS (Kruize) sections are present in the diagnostic context. Interprets Kruize CPU and memory right-sizing recommendations for containers.
compatibility: Requires Causa diagnostic context collected from Kubernetes/OpenShift pods.
metadata:
  category: resource-optimization
  domain: kubernetes, jvm
  mcp_server: kruize-mcp-server
  context_sections:
    - RESOURCE COST RECOMMENDATIONS (Kruize)
    - RESOURCE PERFORMANCE RECOMMENDATIONS (Kruize)
---

# Kruize Optimization Skill

Interprets the Kruize context already collected by Causa. The context contains two sections from the Kruize MCP server — **RESOURCE COST RECOMMENDATIONS (Kruize)** and **RESOURCE PERFORMANCE RECOMMENDATIONS (Kruize)**.

## What to Expect in the Context

Two JSON sections are present in the diagnostic context:

- **RESOURCE PERFORMANCE RECOMMENDATIONS (Kruize)** — CPU sized to the 98th percentile of observed usage; primary source for incident response
- **RESOURCE COST RECOMMENDATIONS (Kruize)** — CPU sized to the 60th percentile of observed usage; lower bound for safe sizing
- Both sections share the same JSON schema with `current`, `recommendation_terms` (`short_term` 24h / `medium_term` 7d / `long_term` 15d), optional `runtime_recommendations`, and `notifications`

Before interpreting either section, refer to `kruize-reference.md` for the full schema, recommendation engines, runtime layers, and notification code reference.

---

## Interpreting RESOURCE PERFORMANCE RECOMMENDATIONS (Kruize)

Use `long_term` (15 days) values — they use the most data and are most reliable. Fall back to `short_term` only when the incident is very recent and long_term data predates a known configuration change.

**`No Data Available`**
Kruize had no data for this container. Do not cite this section as evidence.

**`current` memory limit below recommended**
Container is under-provisioned for observed peak usage. If POD EVENTS also show `OOMKilling`, this gap is the direct cause of the kill. Since requests and limits are unified, both values move together when sizing is corrected.

**`current` memory limit at or above recommended**
Memory sizing is not the problem. A code-level cause is more likely — look at POD LOGS for `OutOfMemoryError` and Cryostat JFR sections for heap or GC evidence.

**`current` CPU limit below recommended**
CPU throttling is likely. Corroborate with `Unhealthy` probe events in POD EVENTS — throttling slows GC threads and can cause probe timeouts even when the application is otherwise healthy.

**`runtime_recommendations` present**
Kruize detected JVM or framework metrics and recommends specific tunables (e.g. `MaxRAMPercentage`, `GCPolicy`, Quarkus thread pool). Read this alongside the container memory limit: a correct limit with an oversized `-Xmx` (MaxRAMPercentage too high) can still produce `OutOfMemoryError` in logs while the container limit appears sufficient.

---

## Interpreting RESOURCE COST RECOMMENDATIONS (Kruize)

Use this section to bound the range of safe sizing, not as the primary recommendation for incident response. When cost values are also below current limits, resource exhaustion is almost certainly configuration-driven rather than a code defect — both engines agree the container is under-provisioned.

---

## What Each Finding Indicates

| Kruize finding                                    | What it indicates                                                                                                                           |
|---------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| Memory limit below recommended                    | Container is under-provisioned for observed peak memory usage; OOM risk is configuration-driven                                             |
| CPU limit below recommended                       | Container is CPU-throttled; probe timeouts and GC slowdowns are likely caused by insufficient CPU headroom                                  |
| Both at or above recommended                      | Resource sizing is adequate; the incident is not caused by under-provisioning                                                               |
| `runtime_recommendations` present (code `112104`) | JVM or framework tunables are misaligned — heap allocation (e.g. `MaxRAMPercentage`) may be too high relative to the container memory limit |
| Insufficient data (`120001`)                      | Recommendation confidence is low; fewer than 24 hours of data were available when the recommendation was computed                           |
