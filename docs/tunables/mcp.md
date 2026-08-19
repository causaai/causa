# MCP Tunables

Causa Backend calls MCP (Model Context Protocol) servers to gather diagnostic context before
passing it to the LLM. Each server is independently configurable and fails gracefully — a
down server never blocks the others.

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

## Cryostat MCP _(Planned)_

Provides JFR (Java Flight Recorder) analysis: GC behaviour, memory pools, threads, exceptions,
and container resource metrics. Cryostat runs its MCP listener on port **8000** and its own
health API on port **8080** — both URLs must be set independently.

| Tunable | Env var | Default | Description |
|---|---|---|---|
| MCP endpoint | `CAUSA_MCP_CRYOSTAT_ENDPOINT` | `http://cryostat-mcp:8000` | MCP port (8000) |
| Health endpoint | `CAUSA_MCP_CRYOSTAT_HEALTH_ENDPOINT` | `http://cryostat-mcp-api:8080` | Health API port (8080) |
| Health check path | `CAUSA_MCP_CRYOSTAT_HEALTH_PATH` | `/healthz` | Path probed by the health checker |
| Request timeout | `CAUSA_MCP_CRYOSTAT_TIMEOUT` | `15000` | Milliseconds — JFR recording takes time |
| Retry delay | `CAUSA_MCP_CRYOSTAT_RETRY_DELAY` | `5000` | ms to wait after `RECORDING_CREATED` status before retry |
| Max retries | `CAUSA_MCP_CRYOSTAT_MAX_RETRIES` | `3` | Maximum retry attempts per tool call |

Tools used: `get_gc_analysis` · `get_memory_analysis` · `get_thread_analysis` ·
`get_exception_analysis` · `get_container_analysis`

---

## Filesystem MCP _(VM platform, Planned_)_

Provides Liberty `messages.log` and FFDC directory content, filtered to a time window around
the alert timestamp.

| Tunable | Env var | Default | Description |
|---|---|---|---|
| Endpoint URL | `CAUSA_MCP_FILESYSTEM_ENDPOINT` | `http://filesystem-mcp-server:8080` | Base URL of the server |
| Health check path | `CAUSA_MCP_FILESYSTEM_HEALTH_PATH` | `/healthz` | Path probed by the health checker |
| Request timeout | `CAUSA_MCP_FILESYSTEM_TIMEOUT` | `10000` | Milliseconds |
| Liberty logs root | `CAUSA_MCP_FILESYSTEM_LIBERTY_LOGS_DIR` | `/logs` | Root directory of Liberty log files on the host |
| Alert time window | `CAUSA_MCP_FILESYSTEM_ALERT_WINDOW_MINUTES` | `5` | Minutes before alert timestamp used to filter log files. Reduce to 2–3 to narrow to the immediate incident |

Tools used: `list_directory` · `list_directory_with_sizes` · `read_text_file`

---

## JMX MCP _(VM platform, Planned_)_

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

All MCP variables are deployment-time. They require a pod restart to take effect.

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

### 1 — Add config properties to `application.yml`

```yaml
causa:
  mcp:
    prometheus:
      endpoint: ${CAUSA_MCP_PROMETHEUS_ENDPOINT:http://prometheus-mcp-server:8080}
      health-path: ${CAUSA_MCP_PROMETHEUS_HEALTH_PATH:/healthz}
      timeout-ms: ${CAUSA_MCP_PROMETHEUS_TIMEOUT:10000}
```

### 2 — Add the config interface to `McpConfig.java`

In [`McpConfig`](../../src/main/java/com/causa/config/McpConfig.java) add an inner interface
and a method:

```java
@WithName("prometheus")
PrometheusConfig prometheus();

interface PrometheusConfig {
    @WithName("endpoint")   String endpoint();
    @WithName("health-path") @WithDefault("/healthz") String healthPath();
    @WithName("timeout-ms")  @WithDefault("10000")    int timeoutMs();
}
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

### 6 — Register the env vars in the ConfigMap

```yaml
# deployment/kubernetes/base/configmap.yaml
CAUSA_MCP_PROMETHEUS_ENDPOINT:   "http://prometheus-mcp-server:8080"
CAUSA_MCP_PROMETHEUS_HEALTH_PATH: "/healthz"
CAUSA_MCP_PROMETHEUS_TIMEOUT:    "10000"
```

### 7 — Update the RCA prompt

Describe the new context section in
[`src/main/resources/prompts/rca-prompt-template.yml`](../../src/main/resources/prompts/rca-prompt-template.yml)
so the LLM knows how to interpret it. See [llm.md — Prompt templates](llm.md#prompt-templates).
