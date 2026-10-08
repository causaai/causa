# MCP Tunables

Causa Backend calls MCP (Model Context Protocol) servers to gather diagnostic context before
passing it to the LLM. Each server is independently configurable and fails gracefully — a
down server never blocks the others.

## MCP Configuration (`mcp.json`) (Supported Version 0.0.4+)

MCP servers are configured via a JSON file that is loaded and validated at startup. Three
deployment profiles ship out of the box:

| Profile | File | Servers included |
|---|---|---|
| Cluster (default) | `mcp-cluster-default.json` | Kubernetes, Kruize, Cryostat |
| Developer Persona | `mcp-developer-default.json` | Kubernetes, Kruize, Quarkus, Async Profiler |
| VM | `mcp-vm-default.json` | JMX, Filesystem |

The config file path is set via the `causa.mcp.config-file` property (env var `MCP_CONFIG_FILE`).
At startup, `McpSettingsLoader` reads the file, deserializes it with Jackson, and validates it
with Jakarta Bean Validation. `McpStartup` then populates the `McpRegistry` — a
`ConcurrentHashMap<String, McpClient>` — with one client per server entry. Startup failure is
non-fatal: the registry records the error and health checks reflect it.

The JSON files live in `deployment/kubernetes/base/mcp-config/` and are mounted into the pod
via a ConfigMap volume.

### JSON structure

```json
{
  "mcpServers": {
    "<server-name>": {
      "type": "streamable-http",
      "url": "http://<server>:<port>/mcp",
      "headers": {},
      "optional": false,
      "healthCheck": { "url": "http://<server>:<port>/healthz", "timeoutMs": 5000 },
      "timeoutMs": 5000,
      "metadata": {},
      "description": "Short description for LLM context",
      "tools": [
        {
          "name": "tool_name",
          "contextKey": "CONTEXT_KEY",
          "description": "What the tool returns",
          "arguments": { "key": "value" }
        }
      ]
    }
  }
}
```

| Field | Required | Description |
|---|---|---|
| `type` | Yes | Transport type — currently always `streamable-http` |
| `url` | Yes | MCP endpoint URL (JSON-RPC 2.0 + SSE) |
| `headers` | No | Extra HTTP headers sent on every request |
| `optional` | No | If `true`, this server being down does not degrade overall system health |
| `healthCheck.url` | Yes | URL probed by `McpClient.checkHealth()` (may differ from `url` — e.g. Cryostat) |
| `healthCheck.timeoutMs` | Yes | Health probe timeout in milliseconds |
| `timeoutMs` | No | Default request timeout for tool calls |
| `metadata` | No | Server-wide tunables (e.g. `retryDelayMs`, `metricsBaseUrl`) |
| `description` | No | Plain-English summary of the server |
| `tools` | No | Ordered tool-invocation plan with `name`, `contextKey`, `description`, `arguments` |

### Environment variable

| Tunable | Env var | Default (prod) | Default (dev) | Description |
|---|---|---|---|---|
| Config file path | `MCP_CONFIG_FILE` | `/etc/causa/mcp.json` | `deployment/kubernetes/base/mcp-config/mcp-cluster-default.json` | Path to the `mcp.json` file loaded at startup |

---

## Kubernetes MCP

Provides pod status YAML, pod events, and pod logs (current + previous container).

| Tunable | Env var | Default | Description |
|---|---|---|---|
| Endpoint URL | `CAUSA_MCP_K8S_ENDPOINT` | `http://kubernetes-mcp-server:8080` | Base URL of the server |
| Health check path | `CAUSA_MCP_K8S_HEALTH_PATH` | `/healthz` | Path probed by the health checker |
| Request timeout | `CAUSA_MCP_K8S_TIMEOUT` | `5000` | Milliseconds |

Tools used: `pods_get` · `pods_log` · `events_list`

---

## Kruize MCP

Provides CPU and memory resource optimisation recommendations (cost-optimised and performance-optimised).

| Tunable | Env var | Default | Description |
|---|---|---|---|
| Endpoint URL | `CAUSA_MCP_KRUIZE_ENDPOINT` | `http://kruize-mcp-server-service:8080` | Base URL of the server |
| Health check path | `CAUSA_MCP_KRUIZE_HEALTH_PATH` | `/q/health/ready` | Path probed by the health checker |
| Request timeout | `CAUSA_MCP_KRUIZE_TIMEOUT` | `10000` | Milliseconds |

Tools used: `getCostOptimizedRecommendations` · `getPerformanceOptimizedRecommendations`

---

## Cryostat MCP

The cluster diagnostic path calls the Cryostat Kubernetes mux through `mcp.json`
(`deployment/kubernetes/base/mcp-config/mcp-cluster-default.json`). Both tools must already be
exposed by that server (`toolLevel` `ALL`). Causa does not start a JFR recording.

### Prerequisites: Cryostat agent & discovery

**JFR analysis is only available for pods that are registered in Cryostat's discovery tree.**

1. **Deploy the Cryostat agent** on each target pod (typically via the Cryostat operator or a sidecar).
2. The agent registers the pod's JVM with Cryostat, making it appear in the discovery tree under its Kubernetes namespace → deployment → replica set → pod hierarchy.
3. When an alert fires, Causa calls `getDiscoveryTree` with the alert namespace and `mergeRealms=true`.
4. **If the alert pod is not found in the tree**, `CryostatContextCollector` records a not-found note in `CRYOSTAT_DISCOVERY` and **skips** the `getAnalysisReport` call entirely — no JFR analysis is available for that alert.
5. **If the pod is found**, the collector proceeds to call `getAnalysisReport` with the pod name and an ISO-8601 time window (alert timestamp ± `analysisLookbackMinutes`, default 15 min).

> **Note**: Installing the Cryostat MCP server alone is not sufficient. The target workloads must have the Cryostat agent running and be discoverable. Pods without the agent (or in namespaces not watched by Cryostat) will not receive automated JFR analysis.

#### Required pod labels / annotations

Add the following labels to your workload's pod template (`spec.template.metadata.labels`) so the Cryostat agent auto-configures and registers the JVM:

| Label | Required | Description |
|---|---|---|
| `cryostat.io/name` | Yes | Name of the Cryostat instance (e.g., `cryostat-sample`) |
| `cryostat.io/namespace` | Yes | Namespace where the Cryostat instance is running (e.g., `openshift-tuning`) |
| `cryostat.io/harvester-template` | Yes | Recording template — use `Continuous` for ongoing JFR recordings |
| `cryostat.io/harvester-period` | No | Harvester period (e.g., `5m`) — how often to rotate recordings |
| `cryostat.io/harvester-max-files` | No | Max archived recordings to retain (e.g., `"6"`) |
| `cryostat.io/log-level` | No | Agent log level (e.g., `debug`) |

**Example Deployment snippet:**

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: auth-cache
  namespace: openshift-tuning
  labels:
    app: auth-cache
spec:
  replicas: 1
  selector:
    matchLabels:
      app: auth-cache
  template:
    metadata:
      labels:
        app: auth-cache
        cryostat.io/name: cryostat-sample
        cryostat.io/namespace: openshift-tuning
        cryostat.io/harvester-template: Continuous
        cryostat.io/harvester-period: 5m
        cryostat.io/harvester-max-files: "6"
        cryostat.io/log-level: debug
    spec:
      containers:
        - name: auth-cache
          image: "quay.io/causa-ai-hub/quarkus-gc-pause:promotion-pressure-nd"
          imagePullPolicy: IfNotPresent
          resources:
            requests:
              memory: "650Mi"
            limits:
              memory: "650Mi"
          env:
            - name: JAVA_TOOL_OPTIONS
              value: "-XX:+UseContainerSupport -Xms256m -Xmx378m -Xlog:gc"
            - name: GC_SCENARIO
              value: "PROMOTION_PRESSURE"
          ports:
            - containerPort: 8080
```

> **Reference**: [Effortless Java Observability: Cryostat Agent Autoconfiguration](https://developers.redhat.com/blog/2025/03/20/effortless-java-observability-cryostat-agent-autoconfiguration) — Red Hat blog with full setup walkthrough.

`getDiscoveryTree` is called first with the alert namespace and `mergeRealms=true`.
`getAnalysisReport` runs only when that tree contains the alert namespace and pod. The report
call passes the pod name plus an ISO-8601 window ending at the alert timestamp.

| Tunable | Where | Default | Description |
|---|---|---|---|
| MCP endpoint | `mcp.json` `url` | `http://cryostat-mcp:8000/mcp` | Mux MCP endpoint |
| Health endpoint | `mcp.json` `healthCheck.url` | `http://cryostat-mcp-api:8080/healthz` | Health probe |
| Request timeout | `mcp.json` `timeoutMs` | `60000` | Milliseconds — report generation can take about 30s |
| Analysis lookback | `mcp.json` `metadata.analysisLookbackMinutes` | `15` | Minutes before the alert timestamp used as `fromTimestamp` |

Tools used: `getDiscoveryTree` · `getAnalysisReport`

---

## Quarkus MCP _(Developer profile)_

Scrapes raw Prometheus-format metrics from the target application's `/q/metrics` endpoint.
Included in the **developer** deployment profile (`mcp-developer-default.json`).

| Tunable | Env var | Default | Description |
|---|---|---|---|
| Endpoint URL | `CAUSA_MCP_QUARKUS_ENDPOINT` | _(empty — opt-in)_ | Base URL of the Quarkus MCP server |
| Health check path | `CAUSA_MCP_QUARKUS_HEALTH_PATH` | `/healthz` | Path probed by the health checker |
| Request timeout | `CAUSA_MCP_QUARKUS_TIMEOUT` | `10000` | Milliseconds |
| Metrics base URL | `CAUSA_MCP_QUARKUS_METRICS_BASE_URL` | _(empty — opt-in)_ | Application base URL passed as `baseUrl` argument to `fetch_raw_metrics_from_endpoint` |

In `mcp.json`, the metrics base URL is set via `metadata.metricsBaseUrl`:

```json
"quarkus": {
  "type": "streamable-http",
  "url": "http://quarkus-mcp-server:8080/mcp",
  "metadata": { "metricsBaseUrl": "http://quarkus-app.default.svc.cluster.local:8080/q/metrics" },
  "tools": [
    { "name": "fetch_raw_metrics_from_endpoint", "contextKey": "QUARKUS_RAW_METRICS", "arguments": { "baseUrl": "${metadata.metricsBaseUrl}" } }
  ]
}
```

Tools used: `fetch_raw_metrics_from_endpoint`

---

## Async Profiler MCP _(Developer profile)_

JVM profiling via the async-profiler agent — flame graphs, JFR summaries, recording reports,
and JVM statistics. Included in the **developer** deployment profile (`mcp-developer-default.json`).

| Tunable | Env var | Default | Description |
|---|---|---|---|
| Endpoint URL | `CAUSA_MCP_ASYNC_PROFILER_ENDPOINT` | _(empty — opt-in)_ | Base URL of the Async Profiler MCP server |
| Health check path | `CAUSA_MCP_ASYNC_PROFILER_HEALTH_PATH` | `/healthz` | Path probed by the health checker |
| Request timeout | `CAUSA_MCP_ASYNC_PROFILER_TIMEOUT` | `15000` | Milliseconds |

Tools used: `list_profiled_pods` · `get_pod_jvm_status` · `get_jvm_statistics` ·
`get_recording` · `get_recording_report` · `get_jfr_summary` · `get_flame_graph`

---

## Filesystem MCP _(VM platform)_

Provides Liberty `messages.log` and FFDC directory content, filtered to a time window around
the alert timestamp.

| Tunable | Env var | Default | Description |
|---|---|---|---|
| Endpoint URL | `CAUSA_MCP_FILESYSTEM_ENDPOINT` | `http://filesystem-mcp-server:8080` | Base URL of the server |
| Health check path | `CAUSA_MCP_FILESYSTEM_HEALTH_PATH` | `/healthz` | Path probed by the health checker |
| Request timeout | `CAUSA_MCP_FILESYSTEM_TIMEOUT` | `10000` | Milliseconds |
| Liberty logs root | `CAUSA_MCP_FILESYSTEM_LIBERTY_LOGS_DIR` | `/logs` | Root directory of Liberty log files on the host |
| Alert time window | `CAUSA_MCP_FILESYSTEM_ALERT_WINDOW_MINUTES` | `5` | Minutes before alert timestamp used to filter log files. Reduce to 2–3 to narrow to the immediate incident |

In `mcp.json`, the Liberty-specific settings are in `metadata`:

```json
"filesystem": {
  "metadata": { "libertyLogsDir": "/logs", "alertWindowMinutes": 5 }
}
```

Tools used: `list_directory` · `list_directory_with_sizes` · `read_text_file`

---

## JMX MCP _(VM platform)_

Provides JVM heap status, GC activity, thread state, GC pressure analysis, memory leak
indicators, thread contention analysis, and JVM runtime metadata.

| Tunable | Env var | Default | Description |
|---|---|---|---|
| Endpoint URL | `CAUSA_MCP_JMX_ENDPOINT` | `http://jmx-mcp-server:8080` | Base URL of the server |
| Health check path | `CAUSA_MCP_JMX_HEALTH_PATH` | `/healthz` | Path probed by the health checker |
| Request timeout | `CAUSA_MCP_JMX_TIMEOUT` | `10000` | Milliseconds |

Tools used: `getHeapStatus` · `getGcActivity` · `getThreadState` · `getGcPressureAnalysis` ·
`getMemoryLeakIndicators` · `getThreadContentionAnalysis` · `getJvmRuntimeInfo`

---

## How to apply changes

### mcp.json changes

Edit the appropriate JSON file in `deployment/kubernetes/base/mcp-config/`, then apply and
restart:

```bash
kubectl apply -k deployment/kubernetes/overlays/openshift/
kubectl rollout restart deployment/causa-backend -n openshift-tuning
```

To use a different profile, set the `MCP_CONFIG_FILE` env var in the ConfigMap to point to the
desired JSON file.

### Environment variable changes

All `CAUSA_MCP_*` variables are deployment-time. They require a pod restart to take effect.

**Kubernetes / OpenShift** — edit [`deployment/kubernetes/base/configmap.yaml`](../../deployment/kubernetes/base/configmap.yaml)
(or the relevant overlay patch), then apply and restart:

```bash
kubectl apply -k deployment/kubernetes/overlays/openshift/
kubectl rollout restart deployment/causa-backend -n openshift-tuning
```

**VM** — edit `/opt/causa/.env`, then:

```bash
sudo systemctl restart causa-backend
```

---

## Adding a new MCP server

### 1 — Add the server entry to `mcp.json`

Add a new key under `mcpServers` in the appropriate profile JSON
(`deployment/kubernetes/base/mcp-config/`):

```json
{
  "mcpServers": {
    "prometheus": {
      "type": "streamable-http",
      "url": "http://prometheus-mcp-server:8080/mcp",
      "headers": {},
      "optional": false,
      "healthCheck": { "url": "http://prometheus-mcp-server:8080/healthz", "timeoutMs": 10000 },
      "timeoutMs": 10000,
      "description": "Prometheus MCP — PromQL queries for time-series metrics",
      "tools": [
        {
          "name": "query",
          "contextKey": "PROMETHEUS_INSTANT_QUERY",
          "description": "Instant PromQL query result.",
          "arguments": { "query": "${promqlExpression}" }
        },
        {
          "name": "query_range",
          "contextKey": "PROMETHEUS_RANGE_QUERY",
          "description": "Range PromQL query result over a time window.",
          "arguments": { "query": "${promqlExpression}", "start": "${start}", "end": "${end}", "step": "${step}" }
        }
      ]
    }
  }
}
```

The server is automatically picked up by `McpSettingsLoader` → `McpRegistry` at startup and
health checks will appear in the `/api/v1/healthz` response.

### 2 — (If needed) Add config properties to `McpConfig.java`

If the MCP context collector uses per-server env vars for tool calls, add an inner interface
and method in [`McpConfig`](../../src/main/java/com/causa/config/McpConfig.java):

```java
@WithName("prometheus")
PrometheusConfig prometheus();

interface PrometheusConfig {
    @WithName("endpoint")   String endpoint();
    @WithName("health-path") @WithDefault("/healthz") String healthPath();
    @WithName("timeout-ms")  @WithDefault("10000")    int timeoutMs();
}
```

And add matching properties to `application.yml`:

```yaml
causa:
  mcp:
    prometheus:
      endpoint: ${CAUSA_MCP_PROMETHEUS_ENDPOINT:http://prometheus-mcp-server:8080}
      health-path: ${CAUSA_MCP_PROMETHEUS_HEALTH_PATH:/healthz}
      timeout-ms: ${CAUSA_MCP_PROMETHEUS_TIMEOUT:10000}
```

### 3 — Add tool name constants

In [`McpConstants.Tools`](../../src/main/java/com/causa/common/constants/McpConstants.java):

```java
public static final String PROMETHEUS_QUERY       = "query";
public static final String PROMETHEUS_QUERY_RANGE = "query_range";
```

### 4 — Add context fields to `DiagnosticContext`

In [`DiagnosticContext`](../../src/main/java/com/causa/core/domain/DiagnosticContext.java)
add the new field, a builder setter, and a `hasPrometheusContext()` helper.

### 5 — Call the server in `McpContextCollector`

In [`McpContextCollector`](../../src/main/java/com/causa/mcp/McpContextCollector.java) add a
`collectPrometheusContext(Builder, Alert)` method following the same pattern as the existing
collectors, and call it from `collectContextFromCluster()` (or `collectContextFromVm()` for VM).

### 6 — (Optional) Add a response formatter

If the raw tool response benefits from post-processing, register a formatter in
[`McpResponseFormatter`](../../src/main/java/com/causa/mcp/util/McpResponseFormatter.java):

```java
private static final Map<String, Function<String, String>> FORMATTERS = Map.of(
    key("kubernetes", "pods_get"),  McpResponseFormatter::formatPodStatus,
    key("kubernetes", "events_list"), McpResponseFormatter::formatEvents,
    key("kubernetes", "pods_log"),  McpResponseFormatter::formatLogs,
    key("prometheus", "query"),     McpResponseFormatter::formatPrometheusResult
);
```

Servers/tools without a registered formatter pass their raw text through unchanged.

### 7 — Register env vars in the ConfigMap

```yaml
# deployment/kubernetes/base/configmap.yaml
CAUSA_MCP_PROMETHEUS_ENDPOINT:   "http://prometheus-mcp-server:8080"
CAUSA_MCP_PROMETHEUS_HEALTH_PATH: "/healthz"
CAUSA_MCP_PROMETHEUS_TIMEOUT:    "10000"
```

### 8 — Update the RCA prompt

Describe the new context section in
[`src/main/resources/prompts/rca-prompt-template.yml`](../../src/main/resources/prompts/rca-prompt-template.yml)
so the LLM knows how to interpret it. See [llm.md — Prompt templates](llm.md#prompt-templates).