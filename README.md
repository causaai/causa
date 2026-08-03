# Causa AI Agent

**Causa** is an automated, intelligent diagnostic agent designed to accelerate incident response for Java memory anomalies in Kubernetes environments. By bridging the gap between monitoring systems and advanced language models, Causa acts as an automated first responder.

When a memory-related alert fires, Causa intercepts the Prometheus Alertmanager webhook, aggregates relevant contextual data (pod status, logs, K8s events, JFR reports, and resource-optimization recommendations), and performs a dual-layered analysis — a fast triage pass followed by a structured root-cause analysis — producing prioritised, actionable remediation steps without human intervention.

Built with **Quarkus 3.36.1** on **Java 21**, powered by **LangChain4J 1.15.1**, and backed by **PostgreSQL + pgvector**.

---

## Key Features

- **Prometheus Alertmanager webhook receiver** — accepts the standard `POST /api/v1/webhooks/alerts` payload; filters by severity and cooldown before triggering diagnostics.
- **Dual-layer LLM analysis** — structured triage followed by full root-cause analysis with confidence scoring and prioritised remediation steps.
- **Multi-provider LLM support** — ships with drivers for Anthropic Claude (direct API), Google Cloud Vertex AI Anthropic, and a Bob shell bridge; OpenAI and Ollama paths are prepared.
- **MCP context aggregation** — collects pod status, logs, and K8s events via the Kubernetes MCP server; resource-optimisation data via the Kruize MCP server; and JFR-based runtime analysis via the Cryostat MCP server.
- **VM deployment mode** — alternative context pipeline using a Filesystem MCP server (Liberty logs) and a JMX MCP server (heap, GC, thread analysis) for non-Kubernetes workloads.
- **pgvector-backed context store** — diagnostic context embeddings stored with HNSW indexing for future RAG-based similarity retrieval.
- **Runtime-configurable settings** — all 22 operational keys (LLM provider, alert thresholds, cluster identity, MCP endpoints) are stored in PostgreSQL and reloadable at runtime via `POST /api/v1/configs` without a restart.
- **Encrypted secrets at rest** — sensitive config values (API keys, project IDs) are encrypted with AES-256-GCM before persistence.
- **Distributed cache invalidation** — PostgreSQL `LISTEN/NOTIFY` propagates config changes to all running instances instantly.
- **Flyway-managed schema** — zero-downtime schema migrations applied automatically on startup.
- **SmallRye Health endpoints** — liveness (`/q/health/live`) and readiness (`/q/health/ready`) probes used by the bundled Kubernetes `Deployment`.
- **OpenAPI / Swagger UI** — full API documentation available at `/swagger-ui` (disabled in production by default; enable with `quarkus.swagger-ui.always-include=true`).
- **Kustomize overlays** — dedicated overlays for vanilla Kubernetes (kind) and OpenShift deployments.

---

## Prerequisites

| Requirement | Minimum Version | Notes |
|---|---|---|
| Java (JDK) | 21 | OpenJDK or compatible distribution |
| Apache Maven | 3.9+ | or use the bundled `./mvnw` wrapper |
| Docker / Podman | any recent | required to build and push the container image |
| PostgreSQL | 14+ | must have the **pgvector** extension installed |
| Kubernetes MCP Server | latest | provides pod status, logs, and events via JSON-RPC 2.0 |
| Cryostat MCP Server | latest | provides JFR-based Java runtime analysis |
| Kruize MCP Server | latest | provides resource cost and performance recommendations |
| `kubectl` + `kustomize` | 1.27+ / 5+ | required for Kubernetes deployment |

> **pgvector** must be installed in your PostgreSQL instance. A ready-to-use `Dockerfile` and cluster manifest are provided under [`deployment/postgres/`](deployment/postgres/).

---

## Running the Application in Dev Mode

Dev mode enables live-code reload and autowires **Quarkus Dev Services** — if a running PostgreSQL instance is not detected on the configured URL, Quarkus will attempt to spin up a temporary container automatically.

```bash
./mvnw compile quarkus:dev
```

Or using the Quarkus CLI:

```bash
quarkus dev
```

**Dev UI** is available at [`http://localhost:8080/q/dev/`](http://localhost:8080/q/dev/) and provides live access to:

- All registered CDI beans and their scopes
- Configuration sources and effective resolved values
- Flyway migration history
- SmallRye Health check status
- OpenAPI specification browser

> **Note:** The dev profile defaults the database password to `dev_password` and the LLM provider to `vertex-ai-anthropic`. Override via environment variables before starting (see [Configuration](#configuration-environment-variables)).

---

## Packaging and Running the Application

### Build a Fast-JAR

```bash
./mvnw package
```

The artifact is assembled at `target/quarkus-app/`. Run it with:

```bash
java -jar target/quarkus-app/quarkus-run.jar
```

### Build an Über-JAR (single executable JAR)

```bash
./mvnw package -Dquarkus.package.jar.type=uber-jar
java -jar target/*-runner.jar
```

### Build a Native Executable

```bash
./mvnw package -Pnative
./target/causa-backend-*-runner
```

---

## Building and Deploying the Container Image

Causa uses **Quarkus Jib** (`quarkus-container-image-jib`) to build and push a container image without a local Docker daemon.

### 1. Build and push the image

```bash
./mvnw package \
  -Dquarkus.container-image.build=true \
  -Dquarkus.container-image.push=true \
  -Dquarkus.container-image.registry=quay.io \
  -Dquarkus.container-image.group=<your-org> \
  -Dquarkus.container-image.name=causa-backend \
  -Dquarkus.container-image.tag=latest
```

Or use the convenience script:

```bash
./scripts/development/build_and_push.sh
```

### 2. Create required Kubernetes secrets

```bash
# Database credentials
kubectl create secret generic causa-db-secrets \
  --from-literal=CAUSA_DB_PASSWORD=<db-password> \
  --from-literal=CAUSA_DB_USERNAME=causa_backend \
  -n <namespace>

# LLM credentials (Anthropic direct API)
kubectl create secret generic causa-llm-secrets \
  --from-literal=LLM_API_KEY=sk-ant-api03-... \
  -n <namespace>

# LLM credentials (Vertex AI via Application Default Credentials)
# Use the automated setup script instead:
./scripts/llm/setup-vertex-ai.sh --env openshift --project <GCP_PROJECT_ID>
```

> Secret templates are available under [`deployment/kubernetes/secrets-templates/`](deployment/kubernetes/secrets-templates/). **Never commit real secrets to Git.**

### 3. Deploy with Kustomize

**Vanilla Kubernetes (kind):**

```bash
kubectl apply -k deployment/kubernetes/overlays/kind/
```

**OpenShift:**

```bash
kubectl apply -k deployment/kubernetes/overlays/openshift/
```

**Base manifests only:**

```bash
kubectl apply -k deployment/kubernetes/base/
```

The `Deployment` is pre-configured with liveness/readiness probes, a `ServiceAccount`, and a `ConfigMap` that externalises all non-sensitive configuration. The image tag is controlled by the `images` stanza in [`deployment/kubernetes/base/kustomization.yaml`](deployment/kubernetes/base/kustomization.yaml).

### 4. Deploy PostgreSQL with pgvector

```bash
# Build the pgvector-enabled PostgreSQL image
./scripts/postgres/build-postgres-image.sh

# Deploy the PostgreSQL cluster
./scripts/postgres/deploy-postgres.sh
```

A `CloudNativePG` cluster manifest is also available at [`deployment/postgres/postgres-cluster.yaml`](deployment/postgres/postgres-cluster.yaml).

---

## Configuration (Environment Variables)

Causa resolves configuration from three sources in priority order: **Kubernetes `Secret`/`ConfigMap` (env vars)** → **system properties** → **`application.yml` defaults**.

All keys are also manageable at runtime via the Config API (see [Runtime Config API](#runtime-configuration-api)).

> Generate a production encryption key: `openssl rand -base64 32`

### Database

| Environment Variable | Description | Default | Example / Notes |
|---|---|---|---|
| `CAUSA_DB_URL` | JDBC connection URL | `jdbc:postgresql://iri-db-rw:5432/iri-db` | Change to your host in production |
| `CAUSA_DB_USERNAME` | Database user | `causa_backend` | — |
| `CAUSA_DB_PASSWORD` | Database password (**secret**) | *(none)* | Set via Kubernetes secret |
| `CAUSA_ENCRYPTION_KEY` | AES-256-GCM key for encrypting sensitive config values at rest (Base64, 32 bytes) (**secret**) | Insecure dev key — **never use in prod** | `openssl rand -base64 32` |

### LLM Provider

| Environment Variable | Description | Default                         | Example / Notes                               |
|---|---|---------------------------------|-----------------------------------------------|
| `LLM_PROVIDER` | Active LLM provider | `vertex-ai-anthropic`           | `anthropic` \| `vertex-ai-anthropic` \| `bob` |
| `LLM_MODEL_NAME` | Model identifier | *(empty — must be set)*         | `claude-opus-4-5`                             |
| `LLM_API_KEY` | Anthropic API key — required when provider is `anthropic` (**secret**) | *(empty)*                       | `sk-ant-api03-...`                            |
| `LLM_TEMPERATURE` | Sampling temperature | `0.1`                           | Range `0.0`–`1.0`                             |
| `LLM_MAX_TOKENS` | Maximum output tokens | `8192`                          | —                                             |
| `LLM_TIMEOUT_SECONDS` | Per-request LLM timeout (seconds) | `180`                           | Increase for slow models                      |
| `LLM_CHAT_MEMORY_SIZE` | Conversation history window (messages) | `10`                            | `25` recommended for deep analysis            |
| `LLM_SKILLS_ENABLED` | Enable/disable bundled diagnostic skills | `false`                         | Set `true` to enable all skills               |
| `LLM_SKILLS_DIR` | Path to a directory of user-supplied skills | *(empty — uses bundled skills)* | `/etc/causa/skills`                           |
| `LLM_AUTH_TYPE` | Auth type override for custom providers | *(empty)*                       | Provider-specific                             |
| `LLM_BASE_URL` | Base URL override for OpenAI-compatible endpoints | *(empty)*                       | `https://api.openai.com/v1`                   |
| `LLM_CUSTOM_HEADERS` | Extra HTTP headers as JSON object | `{}`                            | `{"X-Custom": "value"}`                       |

### Vertex AI (when `LLM_PROVIDER=vertex-ai-anthropic`)

| Environment Variable | Description | Default | Example / Notes |
|---|---|---|---|
| `VERTEX_PROJECT_ID` | GCP project ID (**secret**) | *(empty — must be set)* | `my-gcp-project` |
| `VERTEX_LOCATION` | GCP region | *(empty)* | `us-east5` — `global` is **not** valid for Claude |
| `GOOGLE_APPLICATION_CREDENTIALS` | Path to ADC JSON key file (**secret**) | *(empty — uses workload identity if unset)* | `/var/secrets/gcp/key.json` |

### Alert Filtering

| Environment Variable | Description | Default | Example / Notes |
|---|---|---|---|
| `CAUSA_ALERT_SEVERITY` | Minimum severity to accept | `critical` | `warning` \| `critical` \| `info` |
| `CAUSA_ALERT_COOLDOWN` | Cooldown window in minutes before re-analysing the same pod | `15` | Set `0` to disable cooldown |
| `CAUSA_ALERT_IGNORE_NS` | Comma-separated namespaces to skip entirely | `kube-system,istio-system` | Add any system namespaces |
| `CAUSA_ALERT_COOLDOWN_CLEANUP_INTERVAL` | How often the in-memory cooldown map is pruned | `5m` | Cron-style duration |

### MCP Server Endpoints

| Environment Variable | Description | Default | Example / Notes |
|---|---|---|---|
| `CAUSA_MCP_K8S_ENDPOINT` | Kubernetes MCP server base URL | `http://kubernetes-mcp-server:8080` | — |
| `CAUSA_MCP_K8S_HEALTH_PATH` | Kubernetes MCP server health path | `/healthz` | — |
| `CAUSA_MCP_K8S_TIMEOUT` | Kubernetes MCP call timeout (ms) | `5000` | — |
| `CAUSA_MCP_KRUIZE_ENDPOINT` | Kruize MCP server base URL | `http://kruize-mcp-server-service:8080` | — |
| `CAUSA_MCP_KRUIZE_HEALTH_PATH` | Kruize MCP server health path | `/q/health/ready` | — |
| `CAUSA_MCP_KRUIZE_TIMEOUT` | Kruize MCP call timeout (ms) | `10000` | — |
| `CAUSA_MCP_CRYOSTAT_ENDPOINT` | Cryostat MCP server (tool calls) | `http://cryostat-mcp:8000` | — |
| `CAUSA_MCP_CRYOSTAT_HEALTH_ENDPOINT` | Cryostat MCP server (health checks) | `http://cryostat-mcp-api:8080` | — |
| `CAUSA_MCP_CRYOSTAT_HEALTH_PATH` | Cryostat MCP server health path | `/healthz` | — |
| `CAUSA_MCP_CRYOSTAT_TIMEOUT` | Cryostat MCP call timeout (ms) | `15000` | — |
| `CAUSA_MCP_CRYOSTAT_RETRY_DELAY` | Delay between Cryostat retries (ms) | `5000` | — |
| `CAUSA_MCP_CRYOSTAT_MAX_RETRIES` | Max Cryostat retry attempts | `3` | — |

**VM workloads only** — the following variables apply exclusively when `CAUSA_CLUSTER_TARGET_TYPE=vm`. They are ignored for container workloads (`cluster`).

| Environment Variable | Description | Default | Example / Notes |
|---|---|---|---|
| `CAUSA_MCP_FILESYSTEM_ENDPOINT` | Filesystem MCP server base URL | `http://filesystem-mcp-server:8080` | — |
| `CAUSA_MCP_FILESYSTEM_HEALTH_PATH` | Filesystem MCP server health path | `/healthz` | — |
| `CAUSA_MCP_FILESYSTEM_TIMEOUT` | Filesystem MCP call timeout (ms) | `10000` | — |
| `CAUSA_MCP_FILESYSTEM_LIBERTY_LOGS_DIR` | Liberty log directory path | `/logs` | Mount volume at this path |
| `CAUSA_MCP_FILESYSTEM_ALERT_WINDOW_MINUTES` | Minutes of logs to collect around alert time | `5` | — |
| `CAUSA_MCP_JMX_ENDPOINT` | JMX MCP server base URL | `http://jmx-mcp-server:8080` | — |
| `CAUSA_MCP_JMX_HEALTH_PATH` | JMX MCP server health path | `/healthz` | — |
| `CAUSA_MCP_JMX_TIMEOUT` | JMX MCP call timeout (ms) | `10000` | — |

### Cluster Identity

| Environment Variable | Description | Default | Example / Notes |
|---|---|---|---|
| `CAUSA_CLUSTER_NAME` | Human-readable cluster name shown in diagnostics | `default` | `production-eu` |
| `CAUSA_CLUSTER_TARGET_TYPE` | Deployment target — selects the context pipeline | `vm` | `cluster` (Kubernetes) \| `vm` |

---

## Configuring MCP Servers

Causa communicates with MCP servers using the **JSON-RPC 2.0 over HTTP + SSE** transport. Each MCP server must be deployed, reachable, and exposing a `/mcp` (or `/mcp/`) endpoint before Causa can aggregate context.

### Kubernetes MCP Server

Exposes pod status, pod logs, and event listing. Required for `CAUSA_CLUSTER_TARGET_TYPE=cluster`.

```bash
# Verify connectivity
curl http://<CAUSA_MCP_K8S_ENDPOINT>/healthz
```

Point Causa at the running instance:

```bash
# Via environment variable
export CAUSA_MCP_K8S_ENDPOINT=http://kubernetes-mcp-server.<namespace>.svc.cluster.local:8080

# Or via the Config API at runtime (no restart required)
curl -X POST http://localhost:8080/api/v1/configs \
  -H "Content-Type: application/json" \
  -d '{"configs": {"CAUSA_MCP_K8S_ENDPOINT": "http://kubernetes-mcp-server:8080"}}'
```

### Kruize MCP Server

Provides cost and performance resource-optimisation recommendations.

```bash
curl http://<CAUSA_MCP_KRUIZE_ENDPOINT>/q/health/ready
```

### Cryostat MCP Server

Provides JFR (Java Flight Recorder) based runtime analysis for JVM profiling.

```bash
curl http://<CAUSA_MCP_CRYOSTAT_HEALTH_ENDPOINT>/healthz
```

### JMX MCP Server (VM mode only)

Provides heap, GC, thread, and memory-leak indicators from JVM JMX when `CAUSA_CLUSTER_TARGET_TYPE=vm`.

```bash
curl http://<CAUSA_MCP_JMX_ENDPOINT>/healthz
```

---

## Runtime Configuration API

All 22 operational keys can be read or updated without a restart. Changes are persisted to PostgreSQL and broadcast to all instances via `pg_notify`.

### List all config entries

```bash
curl http://localhost:8080/api/v1/configs
```

### Filter by category (`llm`, `alerts`, `cluster`)

```bash
curl "http://localhost:8080/api/v1/configs?category=llm"
```

### Read a single key

```bash
curl http://localhost:8080/api/v1/configs/LLM_PROVIDER
```

### Update one or more keys

```bash
curl -X POST http://localhost:8080/api/v1/configs \
  -H "Content-Type: application/json" \
  -d '{
    "configs": {
      "LLM_PROVIDER":    "anthropic",
      "LLM_MODEL_NAME":  "claude-opus-4-5",
      "LLM_TEMPERATURE": "0.1",
      "LLM_MAX_TOKENS":  "8192"
    }
  }'
```

**Response:**

```json
{
  "updated": [
    { "key": "LLM_PROVIDER",    "value": "anthropic" },
    { "key": "LLM_MODEL_NAME",  "value": "claude-opus-4-5" },
    { "key": "LLM_TEMPERATURE", "value": "0.1" },
    { "key": "LLM_MAX_TOKENS",  "value": "8192" }
  ],
  "rejected": []
}
```

### Configure Vertex AI at runtime

```bash
curl -X POST http://localhost:8080/api/v1/configs \
  -H "Content-Type: application/json" \
  -d '{
    "configs": {
      "LLM_PROVIDER":     "vertex-ai-anthropic",
      "LLM_MODEL_NAME":   "claude-sonnet-4-6",
      "VERTEX_PROJECT_ID": "my-gcp-project",
      "VERTEX_LOCATION":  "us-east5"
    }
  }'
```

> Sensitive keys (`LLM_API_KEY`, `VERTEX_PROJECT_ID`, `GOOGLE_APPLICATION_CREDENTIALS`) are encrypted with AES-256-GCM before being written to the database and **masked** (`********`) in all API responses.

---

## Endpoint / Usage Examples

### Health Checks

```bash
# Liveness
curl http://localhost:8080/q/health/live

# Readiness
curl http://localhost:8080/q/health/ready

# Application-level health (DB + LLM components)
curl http://localhost:8080/api/v1/healthz
```

---

### Receive a Prometheus Alertmanager Webhook

Causa accepts the standard Alertmanager webhook payload. Configure Alertmanager to route memory-related alerts to this receiver.

**`POST /api/v1/webhooks/alerts`**

```bash
curl -X POST http://localhost:8080/api/v1/webhooks/alerts \
  -H "Content-Type: application/json" \
  -d '{
    "version":  "4",
    "status":   "firing",
    "receiver": "causa",
    "groupKey": "{}:{alertname=\"JvmMemoryFillingUp\"}",
    "groupLabels": {
      "alertname": "JvmMemoryFillingUp"
    },
    "commonLabels": {
      "alertname": "JvmMemoryFillingUp",
      "severity":  "critical"
    },
    "commonAnnotations": {
      "summary": "JVM memory usage exceeds 80% threshold"
    },
    "externalURL": "http://alertmanager:9093",
    "alerts": [
      {
        "status":      "firing",
        "fingerprint": "abc123def456",
        "startsAt":    "2025-01-15T10:30:00Z",
        "endsAt":      "0001-01-01T00:00:00Z",
        "generatorURL": "http://prometheus:9090/graph",
        "labels": {
          "alertname":  "JvmMemoryFillingUp",
          "severity":   "critical",
          "namespace":  "production",
          "pod":        "my-app-7d8f9b-xk2pl",
          "container":  "my-app"
        },
        "annotations": {
          "summary":     "JVM memory filling up on pod my-app-7d8f9b-xk2pl",
          "description": "JVM heap usage is at 87%"
        }
      }
    ]
  }'
```

**Response:**

```json
{
  "status":        "ACCEPTED",
  "totalReceived": 1,
  "totalAccepted": 1,
  "totalRejected": 0,
  "accepted": {
    "alrt_Xy7mNpQ3aBcDeFgH": "diag_Ab1Cd2Ef3Gh4Ij5K"
  },
  "rejected": {}
}
```

The response maps each accepted `alertId` → `diagnosticId`. Use the `diagnosticId` to poll for analysis results.

---

### Query Alerts

```bash
# All alerts
curl http://localhost:8080/api/v1/alerts

# Filter by workload and namespace
curl "http://localhost:8080/api/v1/alerts?workload_name=my-app&namespace=production"

# Single alert by ID
curl http://localhost:8080/api/v1/alerts/alrt_Xy7mNpQ3aBcDeFgH
```

---

### Query Diagnostics

```bash
# List all diagnostics (summary view)
curl http://localhost:8080/api/v1/diagnostics
```

**Sample summary response:**

```json
[
  {
    "diagnosticId": "diag_Ab1Cd2Ef3Gh4Ij5K",
    "alertId":      "alrt_Xy7mNpQ3aBcDeFgH",
    "status":       "COMPLETED",
    "issueTitle":   "JVM Heap Memory Exhaustion — Suspected Memory Leak",
    "issueType":    "MEMORY_LEAK",
    "clusterName":  "production-eu",
    "createdAt":    "2025-01-15T10:30:05Z"
  }
]
```

```bash
# Full diagnostic detail with root-cause analysis and recommendations
curl http://localhost:8080/api/v1/diagnostics/diag_Ab1Cd2Ef3Gh4Ij5K
```

**Sample detail response:**

```json
{
  "diagnosticId":     "diag_Ab1Cd2Ef3Gh4Ij5K",
  "alertId":          "alrt_Xy7mNpQ3aBcDeFgH",
  "status":           "COMPLETED",
  "issueTitle":       "JVM Heap Memory Exhaustion — Suspected Memory Leak",
  "issueDescription": "Heap utilization reached 87% with a monotonically increasing trend over 45 minutes. GC overhead exceeds 15%, indicating the collector is unable to reclaim sufficient memory.",
  "issueType":        "MEMORY_LEAK",
  "rootCauseSummary": "A static cache in `com.example.CacheManager` is growing unbounded. Objects inserted on each HTTP request are never evicted.",
  "recommendations": [
    {
      "solution":              "Implement an LRU eviction policy or size cap on CacheManager",
      "justification":         "Directly removes the unbounded growth root cause",
      "successProbability":    0.92,
      "implementationNotes":   "Use Caffeine or Guava Cache with `maximumSize` and `expireAfterAccess`"
    }
  ],
  "confidence": {
    "score": 0.88,
    "level": "HIGH"
  },
  "evidence": {
    "supportingLogs": ["OutOfMemoryError: Java heap space at ..."],
    "evidences":      ["GC overhead 17%", "Heap 87% — 45-min monotonic increase"]
  },
  "clusterName": "production-eu",
  "createdAt":   "2025-01-15T10:30:05Z",
  "updatedAt":   "2025-01-15T10:30:48Z"
}
```

---

### Alertmanager Receiver Configuration

Add the following to your `alertmanager.yml` to route memory alerts to Causa:

```yaml
receivers:
  - name: causa
    webhook_configs:
      - url: http://causa-backend.<namespace>.svc.cluster.local:8080/api/v1/webhooks/alerts
        send_resolved: false
        http_config:
          tls_config:
            insecure_skip_verify: false

route:
  receiver: causa
  group_by: [alertname, namespace, pod]
  group_wait:      30s
  group_interval:  5m
  repeat_interval: 4h
  routes:
    - match:
        severity: critical
      receiver: causa
```

Sample Prometheus alert rules are provided under [`deployment/alerts/`](deployment/alerts/).
