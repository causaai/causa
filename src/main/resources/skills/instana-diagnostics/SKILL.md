---
name: instana-mcp
description: Activate whenever INSTANA APM, KUBERNETES EVENTS, INFRASTRUCTURE, RELEASES, SYNTHETICS, or SLO CONTEXT is present or needed. Provides complete operational instructions, diagnostic decision trees, metric schemas, and RCA synthesis recipes for the Instana MCP Server.
compatibility: Requires Instana MCP server configured in ~/.bob/settings/mcp.json connected to an active Instana instance.
metadata:
  category: diagnostics
  domain: apm, distributed-tracing, infrastructure, kubernetes, jvm, slo, synthetics, releases
  mcp_server: instana-mcp
  tools:
    - manage_events (get_events, get_kubernetes_info_events, get_event)
    - manage_releases (get_all_releases, get_release)
    - manage_applications (get_grouped_calls_metrics, get_all_traces)
    - manage_infrastructure (analyze/get_entities, resources/get_snapshots, resources/get_snapshot)
    - manage_maintenance_windows (window/list_active, window/list_scheduled)
    - manage_synthetics (test_playback/get_test_summary_list)
    - manage_slo (report/get, configuration/get_all)
---

# Instana MCP Diagnostics & Root Cause Analysis Skill

This skill guides LLM agents through diagnosing application incidents, infrastructure saturation, Kubernetes pod failures, and service degradation using the **Instana MCP Server**. It defines how to route tool calls, apply mandatory filters, correlate releases, isolate synthetic traffic, validate maintenance windows, and synthesize high-fidelity RCA reports.

---

## 1. Architecture & Smart Router Calling Rules

The Instana MCP Server consolidates hundreds of REST endpoints into **11 domain-specific Smart Router tools**. Every call requires specifying the `resource_type`, `operation`, and operation-specific `params`.

```
LLM Request ──► [Smart Router Tool] ──► (resource_type + operation) ──► Instana REST Backend
```

### Critical Calling Rules & Filter Constraints:
1. **Mandatory `entity` Field in Tag Filters (`tag_filter_expression`)**:
   - Use `"entity": "DESTINATION"` or `"SOURCE"` when filtering infrastructure/service components (e.g., `service.name`, `container.name`, `kubernetes.namespace`).
   - Use `"entity": "NOT_APPLICABLE"` for call metadata, latencies, synthetic flags, or geo tags (e.g., `call.is_synthetic`, `call.latency`, `endpoint.name`).
   - *Never omit or set `entity` to null—this causes an immediate HTTP 422 error.*
2. **Time Frame Parameters**:
   - Never pass `"to": 0` (which evaluates to Unix Epoch 1970). When querying up to the current moment, **omit the `"to"` field completely** and supply only `"windowSize": <milliseconds>` (e.g., `3600000` for 1 hour).
3. **Avoid Unnecessary Discovery Chaining**:
   - Do **not** call `get_metric_catalog` or `get_services` before querying metrics. Core metric names (`calls`, `errors`, `latency`, `memory.used`, `threads.count`) are static.
   - Calling `get_grouped_calls_metrics` with `groupbyTag: "endpoint.name"` retrieves endpoint names, call counts, error rates, and P95 latency in **one single call**.

---

## 2. The 6-Phase Diagnostic Decision Tree

When an alert or incident investigation begins, execute the following diagnostic workflow in order:

```mermaid
flowchart TD
    Start[Incident Alert Received] --> Phase1[Phase 1: Check Maintenance Windows]
    Phase1 -->|Active Window Found| Cue[Note it as an extra clue about infra patching]
    Phase1 -->|No Active Window| Phase2[Phase 2: Triage Events & K8s Issues]
    Cue --> Phase2
    
    Phase2 --> Phase3[Phase 3: Correlate Recent Deployments / Releases]
    Phase3 --> Phase4[Phase 4: Isolate Synthetic vs. Real User Traffic]
    Phase4 --> Phase5[Phase 5: Golden Signals & JVM / Infra Deep-Dive]
    Phase5 --> Phase6[Phase 6: Quantify Business Impact via SLO]
    Phase6 --> Report[Generate Comprehensive RCA Report]
```

---

### Phase 1: Maintenance Window Check (Extra Clue Only)
**Goal**: Find out if a maintenance window is active for the affected application or its infrastructure. Use this only as an extra clue. It does not decide anything by itself.

* **Tool**: `manage_maintenance_windows`
* **Operation**: `resource_type: "window", operation: "list_active"`
* **Request**:
  ```json
  {
    "resource_type": "window",
    "operation": "list_active"
  }
  ```
* **LLM Reasoning Rule**:
  - Maintenance windows are usually for infrastructure work, such as node or OS patching. This work normally does not interrupt application traffic. Application changes need a new release, which is checked in Phase 3.
  - If `active_windows` contains the service name or application ID, report it as extra context in the findings. Do **not** label the incident as planned maintenance, and do **not** suggest lowering its priority or skipping escalation. An active window does not prove the incident is related.
  - When a window is active, also look for signs of infrastructure patching in the Instana data (Phase 2 and Phase 5). Examples are node restarts, pod evictions or rescheduling, and host reboots around the same time.
  - Only say the incident may be linked to maintenance if the Instana data shows such signs. Even then, present it as a possible cause and leave the decision to the user.
  - Whether or not a window is active, continue to Phase 2.

---

### Phase 2: Incident & Kubernetes Event Triage
**Goal**: Retrieve Instana's pre-correlated root causes, issue severity, and cluster-level warnings.

* **Step 2A (Instana Correlated Incidents)**:
  - **Tool**: `manage_events`
  - **Operation**: `operation: "get_events"`
  - **Request**:
    ```json
    {
      "operation": "get_events",
      "params": {
        "time_range": "24h",
        "entity_name": "Kubernetes Pod",
        "entity_label": "openshift-tuning/causa-backend-6cccf69c84-rlrrh",
        "max_events": 5
      }
    }
    ```
  - **LLM Reasoning**: Check `severity == 10` (Critical) or `5` (Warning). Extract `probableCause.problem` and `fixSuggestion`.

* **Step 2B (Long-Term Kubernetes Cluster Events)**:
  - **Tool**: `manage_events`
  - **Operation**: `operation: "get_kubernetes_info_events"`
  - **Request**:
    ```json
    {
      "operation": "get_kubernetes_info_events",
      "params": {
        "time_range": "last 24 hours",
        "max_events": 50
      }
    }
    ```
  - **LLM Reasoning**: Bypasses the 1-hour K8s `event-ttl` limit. Inspect `problem_groups` for `OOMKilled` (Exit Code 137), `CrashLoopBackOff`, `PodEvictions`, or `Unhealthy` probe failures.

---

### Phase 3: Release & Deployment Regression Correlation
**Goal**: Determine if a recent CI/CD rollout or code change triggered the failure.

* **Tool**: `manage_releases`
* **Operation**: `operation: "get_all_releases"`
* **Request**:
  ```json
  {
    "operation": "get_all_releases",
    "params": {
      "name_filter": "causa-backend"
    }
  }
  ```
* **LLM Reasoning Rule**:
  - Compare `release.start` with the incident start time. A release shortly before the incident (for example, within 60 minutes) is only a lead. Timing alone does not make it the cause.
  - Check that the release affects the impacted service. Look at the `applications` and `services` fields of the release. If the release is for a different service, do not link it to this incident.
  - Look for supporting evidence in the Instana data from the other phases. For example, errors or latency on the impacted service start to rise after `release.start`, or they were normal before it.
  - If the release affects the impacted service and the Instana data supports it, report it as a **suspected cause (possible regression)**, with its name and start time.
  - If the release affects the service but there is no supporting evidence, report it as a lead only, and do not rank it as the main cause.

---

### Phase 4: Synthetic Load vs. Organic Traffic Isolation
**Goal**: Determine whether the failure was caused by external probe errors, synthetic test flooding, or genuine user traffic.

* **Scenario A: Is the alert fired directly by a failing synthetic test?**
  - Check `manage_events` for `entityType: "SYNTHETIC_TEST"`.
  - If yes, query `manage_synthetics` (`resource_type: "test_playback", operation: "get_test_summary_list"`):
    ```json
    {
      "resource_type": "test_playback",
      "operation": "get_test_summary_list",
      "params": {
        "payload": {
          "metrics": [{ "metric": "synthetic.metricsStatus", "aggregation": "MEAN" }],
          "timeFrame": { "windowSize": 3600000 }
        }
      }
    }
    ```
  - Inspect `locationStatusList` to pinpoint which regional PoP (e.g., `ap-south-1`) failed and why (DNS timeout, SSL expiration, HTTP 504).

* **Scenario B: Did aggressive synthetic load crash the backend?**
  - Run `manage_applications` grouping by `call.is_synthetic`:
    ```json
    {
      "resource_type": "metrics",
      "operation": "get_grouped_calls_metrics",
      "params": {
        "metrics": [
          { "metric": "calls", "aggregation": "SUM" },
          { "metric": "errors", "aggregation": "SUM" }
        ],
        "group": {
          "groupbyTag": "call.is_synthetic",
          "groupbyTagEntity": "NOT_APPLICABLE"
        },
        "tag_filter_expression": {
          "type": "TAG_FILTER",
          "name": "service.name",
          "operator": "EQUALS",
          "entity": "DESTINATION",
          "value": "causa-backend"
        },
        "time_frame": { "windowSize": 3600000 }
      }
    }
    ```
  - **LLM Reasoning**: If `call.is_synthetic: true` accounts for >80% of total calls or error spikes, attribute the failure to **Synthetic Load Saturation** rather than organic user demand.

---

### Phase 5: Golden Signals & JVM / Infrastructure Deep-Dive
**Goal**: Identify the exact degraded API endpoints and determine if JVM memory leaks, GC pauses, or CPU starvation are present.

* **Step 5A (Endpoint Golden Signals in 1 Call)**:
  - **Tool**: `manage_applications`
  - **Operation**: `resource_type: "metrics", operation: "get_grouped_calls_metrics"`
  - **Request**:
    ```json
    {
      "resource_type": "metrics",
      "operation": "get_grouped_calls_metrics",
      "params": {
        "metrics": [
          { "metric": "calls", "aggregation": "SUM" },
          { "metric": "errors", "aggregation": "SUM" },
          { "metric": "latency", "aggregation": "P95" }
        ],
        "group": {
          "groupbyTag": "endpoint.name",
          "groupbyTagEntity": "NOT_APPLICABLE"
        },
        "tag_filter_expression": {
          "type": "TAG_FILTER",
          "name": "service.name",
          "operator": "EQUALS",
          "entity": "DESTINATION",
          "value": "causa-backend"
        },
        "time_frame": { "windowSize": 3600000 }
      }
    }
    ```
  - **LLM Reasoning**: Calculate `Error Rate % = (errors / calls) * 100`. Identify failing endpoints (e.g. `POST /api/v1/webhooks` with 500 status codes).

* **Step 5B (JVM Time-Series Metrics Curve)**:
  - **Tool**: `manage_infrastructure`
  - **Operation**: `resource_type: "analyze", operation: "get_entities"`
  - **Request**:
    ```json
    {
      "resource_type": "analyze",
      "operation": "get_entities",
      "params": {
        "payload": {
          "type": "jvmRuntimePlatform",
          "metrics": [
            { "metric": "memory.used", "granularity": 600000, "aggregation": "MAX" },
            { "metric": "gc.minor.time", "granularity": 600000, "aggregation": "SUM" },
            { "metric": "gc.major.time", "granularity": 600000, "aggregation": "SUM" },
            { "metric": "threads.count", "granularity": 600000, "aggregation": "MEAN" }
          ],
          "timeFrame": { "windowSize": 3600000 },
          "tagFilterExpression": {
            "type": "TAG_FILTER",
            "entity": "NOT_APPLICABLE",
            "name": "host.name",
            "operator": "CONTAINS",
            "value": "causa-backend"
          }
        }
      }
    }
    ```
  - **LLM Reasoning**: 
    - Continually rising `memory.used` without drops after GC indicates a **Java Memory Leak**.
    - High `gc.major.time` (>1000ms) indicates **Stop-The-World GC Pauses** causing latency spikes and probe timeouts.

* **Step 5C (JVM Startup Flags & Heap Limits - Chained)**:
  - If OOM or memory exhaustion is detected, fetch the JVM snapshot:
    1. Search snapshot: `manage_infrastructure (resource_type: "resources", operation: "get_snapshots", params: { plugin: "jvmRuntimePlatform", query: "causa-backend", size: 1 })`
    2. Read snapshot: `manage_infrastructure (resource_type: "resources", operation: "get_snapshot", params: { snapshot_id: "<id>" })`
  - Extract `data.jvm.args` (e.g. `-Xmx1024m`, `-XX:MaxRAMPercentage=80.0`, `-XX:+UseG1GC`) to verify if container memory limits match JVM maximum heap.

---

### Phase 6: Quantify Business Impact via SLO
**Goal**: Measure error budget burn rate and business SLA violation.

* **Tool**: `manage_slo`
* **Step 6A (Find the SLO ID)**: If the SLO ID is not already known from the incident context, look it up first. Do not call the report with a placeholder ID.
  - **Operation**: `resource_type: "configuration", operation: "get_all"`
  - **Request**:
    ```json
    {
      "resource_type": "configuration",
      "operation": "get_all",
      "params": {
        "query": "causa-backend"
      }
    }
    ```
  - Pick the SLO whose `entity` matches the impacted application or service. If none matches, skip the SLO report and say "SLO impact: not available (no matching SLO found)".
* **Step 6B (Get the SLO Report)**:
  - **Operation**: `resource_type: "report", operation: "get"`
  - **Request**:
    ```json
    {
      "resource_type": "report",
      "operation": "get",
      "params": {
        "slo_id": "<slo_id from Step 6A>",
        "var_from": "last 24 hours"
      }
    }
    ```
* **LLM Reasoning**: Extract `sliValue` vs `sloTarget` (e.g. 98.2% vs 99.5%), `remainingErrorBudgetPercentage`, and `burnRate`. If burn rate > 2.0x, flag as **Urgent SLA Breach**.

---

## 3. RCA Report Synthesis Framework

When presenting the final Root Cause Analysis to the user or writing to Causa's diagnostic record, do consider these things. 

```markdown

### Failure Timeline & Correlation
- **Incident Start**: `<timestamp>`
- **Recent Deployment**: `<Release tag / None>` (Deployed at `<timestamp>`)
- **Maintenance Status**: `<Active Window / None>`
- **Traffic Origin**: `<Real User Traffic (XX%) vs. Synthetic Probe Load (XX%)>`

### Deep-Dive Diagnostic Telemetry
- **Kubernetes Events**: `<OOMKilled / CrashLoopBackOff details>`
- **Degraded Endpoints**: `<Endpoint name, Call volume, Error Rate %, P95 Latency>`
- **JVM & Runtime State**: `<Heap Used vs Max, GC Pause duration, -Xmx flags>`
- **SLO Impact**: `<Current SLI %, Error Budget remaining %, Burn Rate>`

### Actionable Remediation Steps
1. **Immediate Action**: `<e.g., Roll back release v1.4.2 OR Increase pod memory limit to 2Gi>`
2. **Configuration Fix**: `<e.g., Set -XX:MaxRAMPercentage=75.0 in deployment env vars>`
3. **Long-Term Preventive**: `<e.g., Add memory leak regression tests for POST /api/v1/webhooks>`
```

---

## 4. Excluded Tools & Operations Reference

Do **NOT** call these tools during backend RCA (they waste tokens with zero diagnostic return):

| Excluded Tool / Operation | Reason for Exclusion |
|---|---|
| `manage_automation` | Remediation script orchestration, not root-cause diagnosis. |
| `manage_websites` | Browser EUM/RUM telemetry is irrelevant for backend/JVM microservices. |
| `manage_mobile_apps` | Mobile beacon data is irrelevant for Kubernetes workloads. |
| `manage_custom_dashboards` | UI layout CRUD operations provide no runtime diagnostic value. |
