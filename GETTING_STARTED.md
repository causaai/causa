# Getting Started with Causa

Debugging Java performance issues in Kubernetes — heap exhaustion, GC thrashing, memory leaks, OOMKills — is slow, manual, and requires deep expertise. A developer typically has to correlate pod logs, inspect Kubernetes events, figure out how to attach a profiler, collect JFR recordings, and then make sense of all of it under pressure. The result is high mean time to resolution (MTTR) and significant cognitive overhead every time something goes wrong.

**Causa** eliminates that toil. It is an open-source, AI-powered root-cause analysis agent for Java workloads on Kubernetes. When a Prometheus alert fires, Causa automatically aggregates context from your cluster — pod state, logs, Kubernetes events, JVM metrics via Quarkus MCP, and continuous profiling data via Jafra — and feeds that context through an AI analysis pipeline to produce prioritised, actionable remediation steps within seconds.

**Key benefits:**

- **Drastically reduced MTTR** — the time from alert firing to a root-cause explanation drops from hours of manual investigation to seconds of automated analysis.
- **No profiling expertise required** — Jafra automatically injects async-profiler into your Java pod and streams JFR recordings. You do not need to know how to attach a profiler or interpret raw flight recordings.
- **AI-native developer workflow** — Causa surfaces results through your existing AI assistant (Bob IDE, Claude Code, Cursor, Windsurf, or any MCP-capable tool). Ask your IDE "why is this app crashing?" and get a structured diagnosis back.
- **Fully open source** — every component from the RCA backend to the profiler agent is open source and available on [github.com/causaai](https://github.com/causaai).
- **Works locally** — the default installation provisions a local [Kind](https://kind.sigs.k8s.io/) cluster, so you can try Causa on your laptop with no cloud account required if you use the bundled Ollama LLM option.

This guide walks you through everything you need to do **from scratch** to get Causa installed, configured, and analysing your first workload.

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Prerequisites](#2-prerequisites)
3. [Install Causa](#3-install-causa)
   - [Option A — Kind (local, recommended for getting started)](#option-a--kind-local-recommended-for-getting-started)
   - [Port-forwarding on Kind](#port-forwarding-on-kind-causa-backend--causa-mcp)
   - [Option B — OpenShift (existing cluster)](#option-b--openshift-existing-cluster)
4. [Configure the LLM Provider](#4-configure-the-llm-provider)
   - [Option A — Vertex AI (Claude on Google Cloud)](#option-a--vertex-ai-claude-on-google-cloud)
   - [Option B — Anthropic Direct API](#option-b--anthropic-direct-api)
   - [Option C — IBM Bob](#option-c--ibm-bob)
5. [Onboard a Java Workload](#5-onboard-a-java-workload)
   - [Enable Prometheus alerting](#enable-prometheus-alerting)
   - [Enable Jafra continuous profiling](#enable-jafra-continuous-profiling)
6. [Connect Your AI Assistant](#6-connect-your-ai-assistant)
7. [Verify Everything Is Working](#7-verify-everything-is-working)
8. [Run a Demo End-to-End](#8-run-a-demo-end-to-end)
9. [Tear Down](#9-tear-down)

---

## 1. Architecture Overview

Causa is composed of several cooperating components. Understanding how they fit together helps you follow the installation steps.

```
Your Java App (pod)
    │
    │  jafra.io/enabled=true label triggers injection
    ▼
jafra-controller  ──→  injects async-profiler via mutating webhook
    │
    │  JFR chunks written to node-local hostPath
    ▼
jafra-agent (DaemonSet) ──gRPC──→  jafra-analyzer  ──HTTP──→  Jafra MCP Server
                                    (stores & stitches             (LLM-accessible)
                                     recordings)

Prometheus + Alertmanager
    │  memory alert fires
    ▼
causa  ←── gathers context from:
    │                - Kubernetes MCP Server (pod, logs, events)
    │                - Quarkus MCP Server (JVM metrics)
    │                - Jafra (Experimental) MCP Server (JFR analysis)
    │  runs AI analysis via LLM provider
    ▼
causa-mcp-server  ←──  Bob IDE / Claude Code / any MCP client
    │  returns structured RCA
    ▼
Developer sees root cause + prioritised remediation steps
```

**Component summary:**

| Component | Repo | What it does |
|---|---|---|
| `causa` | [causaai/causa](https://github.com/causaai/causa) | Quarkus-based AI RCA agent; receives Prometheus alerts and produces diagnoses |
| `causa-mcp` | [causaai/causa-mcp](https://github.com/causaai/causa-mcp) | MCP server bridging your IDE/agent to the Causa engine |
| `jafra-controller - Experimental` | [bharathappali/jafra-controller](https://github.com/bharathappali/jafra-controller) | Go mutating webhook; injects async-profiler into opted-in Java pods |
| `jafra-agent - Experimental` | [bharathappali/jafra-agent](https://github.com/bharathappali/jafra-agent) | Rust DaemonSet; streams JFR chunks from nodes to the analyzer |
| `jafra-analyzer - Experimental` | [bharathappali/jafra-analyzer](https://github.com/bharathappali/jafra-analyzer) | Quarkus service; stores recordings and serves automated JFR analysis |
| `installer` | [causaai/installer](https://github.com/causaai/installer) | Shell installer; deploys the full stack in one command |
| `causa-demos` | [causaai/causa-demos](https://github.com/causaai/causa-demos) | End-to-end demos with a pre-built chaos workload |

---

## 2. Prerequisites

### CLI tools

#### Kind (local Kubernetes — recommended for getting started)

| Tool | Purpose | Install |
|---|---|---|
| `docker` **or** `podman` | Container runtime for Kind | [docker](https://docs.docker.com/get-docker/) / [podman](https://podman.io/getting-started/installation) |
| `kind` | Local Kubernetes cluster | [kind.sigs.k8s.io](https://kind.sigs.k8s.io/docs/user/quick-start/#installation) |
| `kubectl` | Kubernetes CLI | [kubernetes.io](https://kubernetes.io/docs/tasks/tools/) |
| `helm` | Installs the Prometheus stack | [helm.sh](https://helm.sh/docs/intro/install/) |
| `git` | Cloning the installer | pre-installed on most systems |
| `curl`, `grep`, `sed`, `awk` | Script utilities | pre-installed on macOS and most Linux distributions |

> **Podman users:** Kind requires rootful mode. Run:
> ```bash
> podman machine init --rootful --cpus 4 --memory 4096
> podman machine start
> ```

#### OpenShift (existing cluster)

| Tool | Purpose | Install |
|---|---|---|
| `oc` (preferred) or `kubectl` | Cluster CLI | [OpenShift CLI](https://docs.openshift.com/container-platform/latest/cli_reference/openshift_cli/getting-started-cli.html) |
| `helm`, `curl`, `grep`, `sed`, `awk` | Script utilities | pre-installed on most systems |
| `python3` + `PyYAML` | Alertmanager config merge | `pip3 install pyyaml` |

**Additional OpenShift cluster requirements:**
- Active login to the cluster (`oc login <api-url>`)
- `cert-manager` installed and running in the cluster
- Cluster-admin permissions

### System resources (Kind)

| Resource | Minimum | Recommended |
|---|---|---|
| CPU | 4 vCPUs | 6+ vCPUs |
| RAM | 8 GB | 16 GB |
| Disk | 10 GB free | 20 GB free |

> The Jafra ecosystem and Prometheus stack together add meaningful resource pressure on a laptop. 16 GB RAM is strongly recommended.

### Verify cluster access (OpenShift only)

```bash
oc whoami
oc auth can-i create deployments -n default
```

---

## 3. Install Causa

The installer handles everything: provisioning a Kind cluster (if needed), deploying Prometheus, cert-manager, PostgreSQL, Jafra, and all Causa components.

### Option A — Kind (local, recommended for getting started)

```bash
# Clone the installer
git clone https://github.com/causaai/installer.git
cd installer

# Dry run — validates prerequisites without making any changes
./install.sh --dry-run

# Full install — provisions a Kind cluster named 'causa-rca' and deploys all components
./install.sh
```

When the installer completes, all components are running in the `causa-rca` namespace. All services are `ClusterIP` — use `kubectl port-forward` to reach them from your local machine (see [Port-forwarding on Kind](#port-forwarding-on-kind-causa-backend--causa-mcp) below).

| Local port | Service |
|---|---|
| `30000` | Kubernetes MCP Server |
| `30001` | Causa Backend |
| `30004` | Quarkus MCP Server |
| `30005` | Causa MCP Server |

**What gets installed on Kind:**
- Kind cluster + local registry
- Prometheus stack (kube-prometheus-stack) — for alerting
- cert-manager — required by the Jafra Controller webhook
- Kubernetes MCP Server
- Jafra Ecosystem - Experimental (Controller, Agent, Analyzer)
- Jafra MCP Server
- Quarkus MCP Server
- PostgreSQL with pgvector
- Causa
- Causa MCP Server

#### Set the target Quarkus app URL (optional at install time)

If you already know the in-cluster URL of the Java app you want to monitor, set it before running the installer so the Quarkus MCP server can start scraping metrics immediately:

```bash
export CAUSA_MCP_QUARKUS_METRICS_BASE_URL="http://my-app.my-namespace.svc.cluster.local:8080"
./install.sh
```

You can always set or update it after installation:

```bash
kubectl set env deployment/causa-backend -n causa-rca \
  CAUSA_MCP_QUARKUS_METRICS_BASE_URL="http://my-app.my-namespace.svc.cluster.local:8080"
kubectl rollout status deployment/causa-backend -n causa-rca --timeout=180s
```

---

### Option B — OpenShift (existing cluster)

```bash
git clone https://github.com/causaai/installer.git
cd installer

# Log in to your cluster first
oc login <api-url>

# Deploy into an existing cluster (default namespace: causa-rca)
./install.sh --target openshift
```

> **Note:** Jafra (Ecosystem + MCP Server) is not supported on OpenShift. The installer skips it automatically.

**What gets installed on OpenShift:**
- OpenShift User Workload Monitoring (enabled) + Alertmanager webhook configured
- Kubernetes MCP Server + Route
- Quarkus MCP Server
- PostgreSQL via CloudNativePG operator
- Causa + Route
- Causa MCP Server + Route

#### Custom namespace (both targets)

```bash
# Kind
./install.sh -n my-namespace

# OpenShift
./install.sh --target openshift -n my-namespace
```

---

## 4. Configure the LLM Provider

Causa uses an LLM to perform root-cause analysis. Three providers are currently implemented. Choose one and follow the steps below.

| Provider | `LLM_PROVIDER` value | When to use |
|---|---|---|
| Google Vertex AI (Claude) | `vertex-ai-anthropic` | You have a GCP project with Vertex AI and Claude access |
| Anthropic Direct API | `anthropic` | You have an Anthropic API key (simplest, no GCP required) |
| IBM Bob | `bob` | You have IBM Bob installed (IBM internal) |

Configuration is pushed to the running Causa Backend via its config API — no redeployment is required.

---

<details>
<summary><strong>Option A — Vertex AI (Claude on Google Cloud)</strong></summary>

#### Step 1 — Obtain GCP credentials

**Sub-option A: Application Default Credentials** *(quickest, personal dev)*

```bash
gcloud auth application-default login
gcloud auth application-default set-quota-project <your-gcp-project-id>
# Credentials are written to: ~/.config/gcloud/application_default_credentials.json
```

> ADC credentials are personal and short-lived. Use Sub-option B for shared environments.

**Sub-option B: Service account key** *(recommended for shared / CI environments)*

```bash
# Create the service account
gcloud iam service-accounts create causa-llm-sa \
  --project=<your-gcp-project-id>

# Grant Vertex AI access
gcloud projects add-iam-policy-binding <your-gcp-project-id> \
  --member="serviceAccount:causa-llm-sa@<your-gcp-project-id>.iam.gserviceaccount.com" \
  --role="roles/aiplatform.user" \
  --condition=None

# Download the key file
gcloud iam service-accounts keys create causa-gcp-key.json \
  --iam-account=causa-llm-sa@<your-gcp-project-id>.iam.gserviceaccount.com
```

> ⚠️ `causa-gcp-key.json` is a sensitive credential. Never commit it to Git.

#### Step 2 — Push the configuration to Causa

Replace `<causa-route>` with:
- **Kind:** `localhost:30001`
- **OpenShift:** `$(kubectl get route causa-backend -n causa-rca -o jsonpath='{.spec.host}')`

```bash
# Read and base64-encode your credentials file
CREDS_B64=$(base64 -w0 <path-to-credentials-file>)
# Sub-option A: ~/.config/gcloud/application_default_credentials.json
# Sub-option B: ./causa-gcp-key.json

curl -X POST http://<causa-route>/api/v1/configs \
  -H 'Content-Type: application/json' \
  -d "{
    \"configs\": {
      \"LLM_PROVIDER\": \"vertex-ai-anthropic\",
      \"LLM_MODEL_NAME\": \"claude-sonnet-4-6\",
      \"VERTEX_PROJECT_ID\": \"<your-gcp-project-id>\",
      \"VERTEX_LOCATION\": \"us-east5\",
      \"GOOGLE_APPLICATION_CREDENTIALS\": \"${CREDS_B64}\"
    }
  }"
```

> **Valid `VERTEX_LOCATION` values:** `us-east5`, `us-central1`, `europe-west1`, `asia-southeast1`
> `global` is **not** valid for Claude on Vertex AI.

</details>

---

<details>
<summary><strong>Option B — Anthropic Direct API</strong></summary>

#### Step 1 — Obtain an API key

Get your key at [https://console.anthropic.com/settings/keys](https://console.anthropic.com/settings/keys).

> ⚠️ Never commit API keys to Git or include them in shell history.

#### Step 2 — Push the configuration to Causa

```bash
curl -X POST http://<causa-route>/api/v1/configs \
  -H 'Content-Type: application/json' \
  -d '{
    "configs": {
      "LLM_PROVIDER": "anthropic",
      "LLM_MODEL_NAME": "claude-sonnet-4-6",
      "LLM_API_KEY": "<your-anthropic-api-key>"
    }
  }'
```

</details>

---

<details>
<summary><strong>Option C — IBM Bob</strong></summary>

IBM Bob must already be installed and configured on the host where `causa` runs. See the [Bob Shell Integration Guide](https://github.com/causaai/causa/blob/main/docs/llm/bob-shell-integration.md) for full prerequisites.

```bash
curl -X POST http://<causa-route>/api/v1/configs \
  -H 'Content-Type: application/json' \
  -d '{
    "configs": {
      "LLM_PROVIDER": "bob",
      "LLM_API_KEY": "<your-bob-api-key>"
    }
  }'
```

</details>

---

### Provider comparison

| | Vertex AI (`vertex-ai-anthropic`) | Anthropic Direct (`anthropic`) | IBM Bob (`bob`) |
|---|---|---|---|
| LLM access | GCP Vertex AI | Anthropic API | IBM Bob shell |
| Credentials needed | GCP credentials file + project ID | API key | Bob API key |
| GCP account required | ✅ Yes | ❌ No | ❌ No |

---

## 5. Onboard a Java Workload

To have Causa monitor and diagnose a workload you need to do two things:

1. Configure a **Prometheus alert rule** so Causa is notified when something goes wrong.
2. Optionally, **opt your pod into Jafra** so async-profiler continuously captures CPU, allocation, lock, and GC data that Causa can feed into its AI analysis.

### Enable Prometheus alerting

The installer ships a default PrometheusRule that fires when any pod in the `causa-rca` namespace exceeds 50% of its configured memory limit. If your workload is in a different namespace, apply the rule there:

```bash
# Apply the default alert rule (ships with the installer)
kubectl apply -f manifests/prometheus/prometheus-alert.yaml
```

For a custom threshold, use the template:

```bash
APP_NAME=<your-app-name> \
APP_NAMESPACE=<your-namespace> \
MEMORY_THRESHOLD=0.75 \
  envsubst < manifests/prometheus/causa-memory-alert-template.yaml | kubectl apply -f -
```

The alert must route to the Causa webhook. The installer configures Alertmanager automatically. Verify:

```bash
# Kind
kubectl get prometheusrule -n causa-rca

# OpenShift
oc get prometheusrule -n causa-rca
```

---

### Enable Jafra continuous profiling

Jafra is deployed on Kind by the installer. To start profiling a Java pod, add two labels and one annotation to it:

**Minimum required:**

```yaml
metadata:
  labels:
    jafra.io/enabled: "true"
    jafra.io/mode: "continuous"
  annotations:
    jafra.io/containers: "<your-container-name>"
```

The jafra-controller mutating webhook intercepts the pod creation, injects async-profiler, and configures JDK Flight Recorder. JFR chunks are collected by the jafra-agent DaemonSet and forwarded to jafra-analyzer automatically.

**Imperative patch on a running deployment:**

```bash
kubectl patch deployment <your-deployment> -n <your-namespace> \
  --type=merge \
  -p='{
    "spec": {
      "template": {
        "metadata": {
          "labels": {
            "jafra.io/enabled": "true",
            "jafra.io/mode": "continuous"
          },
          "annotations": {
            "jafra.io/containers": "<your-container-name>"
          }
        }
      }
    }
  }'
kubectl rollout status deployment/<your-deployment> -n <your-namespace>
```

**Advanced profiler annotations (all optional — production defaults apply if omitted):**

| Annotation | Default | Description |
|---|---|---|
| `jafra.io/event` | `ctimer` | Profiling event type (ctimer does not require extra pod privileges) |
| `jafra.io/interval` | `20ms` | CPU sampling interval |
| `jafra.io/alloc` | `1m` | Allocation sampling threshold |
| `jafra.io/lock` | `10ms` | Lock contention threshold |
| `jafra.io/nativemem` | `2m` | Native memory allocation threshold |
| `jafra.io/jfrsync` | `default` | Enables JDK Flight Recorder alongside async-profiler |
| `jafra.io/chunktime` | `5s` | JFR rotation interval (minimum 5s) |
| `jafra.io/memlimit` | `128m` | async-profiler internal memory limit |

> `jfrsync=default` is strongly recommended — it adds GC pauses, JIT compilation, socket I/O, and code cache events to the recording, enabling the full set of JMC diagnostic rules in jafra-analyzer.

**Verify injection succeeded:**

```bash
kubectl get pod -l app=<your-app> -o jsonpath='{.items[0].metadata.annotations.jafra\.io/injected}'
# Expected: true
```

**Query recordings from jafra-analyzer:**

```bash
kubectl -n causa-rca port-forward svc/jafra-analyzer 8080:8080
curl 'http://127.0.0.1:8080/api/v1/recordings'
curl 'http://127.0.0.1:8080/api/v1/recordings?namespace=<your-namespace>&pod=<pod-name>&container=<container-name>'
```

---

### Port-forwarding on Kind (Causa Backend & Causa MCP)

On Kind, `causa-backend` and `causa-mcp` are `ClusterIP` services — run these in a terminal before using them:

```bash
kubectl port-forward svc/causa-backend 30001:8080 -n causa-rca &
kubectl port-forward svc/causa-mcp     30005:8081 -n causa-rca &
```

Keep the sessions running. Replace `causa-rca` with your namespace if you installed with `-n`.

---

## 6. Connect Your AI Assistant

Causa exposes an MCP server so that any MCP-capable AI tool can trigger and retrieve root-cause analyses directly. Register the server in your IDE once and it is available for every project.

### Bob IDE

Add the following to `~/.bob/settings/mcp.json`:

```json
{
  "mcpServers": {
    "causa-rca": {
      "type": "http",
      "url": "http://localhost:30005/mcp"
    }
  }
}
```

### Claude Code

Add to `~/.claude.json`:

```json
{
  "mcpServers": {
    "causa-rca": {
      "type": "http",
      "url": "http://localhost:30005/mcp"
    }
  }
}
```

### Other MCP-compatible clients

Use `http://localhost:30005/mcp` as the streamable-HTTP MCP endpoint. On OpenShift, replace `localhost:30005` with the `causa-mcp` route hostname.

### Install the causa-rca skill (Bob and Claude Code)

The `causa-rca` skill tells your AI assistant how to use the Causa MCP tools. Copy it to your skills directory:

```bash
# Bob
mkdir -p ~/.bob/skills/causa-rca
cp causa-demos/skills/causa-rca/SKILL.md ~/.bob/skills/causa-rca/SKILL.md

# Claude Code
mkdir -p ~/.claude/skills/causa-rca
cp causa-demos/skills/causa-rca/SKILL.md ~/.claude/skills/causa-rca/SKILL.md
```

Once the skill is installed, you can trigger RCA with natural language:

```
Why is the quarkus-perf pod crashing in the causa-rca namespace?
```

### Available MCP tools

| Tool | Description |
|---|---|
| `initiate_rca` | Triggers an RCA for a named failing app; returns a `diagnostic_id` |
| `get_rca_status` | Polls progress — returns `PENDING`, `RUNNING`, `COMPLETED`, or `FAILED` |
| `get_rca_result` | Returns the full RCA including root cause, evidence, and fix recommendations |

---

## 7. Verify Everything Is Working

Run through these checks after completing the steps above.

**Check all pods are running:**

```bash
kubectl get pods -n causa-rca
```

All pods should be in `Running` state. A newly created cluster may take 2–3 minutes for all images to pull.

**Check Causa is healthy:**

```bash
# Kind
curl -s http://localhost:30001/q/health/ready | jq .

# OpenShift
curl -s https://$(oc get route causa-backend -n causa-rca -o jsonpath='{.spec.host}')/q/health/ready | jq .
```

**Verify MCP environment variables were stamped:**

```bash
kubectl set env deployment/causa-backend -n causa-rca --list | grep CAUSA_MCP
```

Expected output:
```
CAUSA_MCP_QUARKUS_ENDPOINT=http://mcp-metrics.causa-rca.svc.cluster.local:8080
CAUSA_MCP_ASYNC_PROFILER_ENDPOINT=http://jafra-mcp.causa-rca.svc.cluster.local:8083
```

**Verify Jafra is running (Kind only):**

```bash
kubectl get pods -n causa-rca -l app=jafra-controller
kubectl get pods -n causa-rca -l app=jafra-analyzer
kubectl get pods -n causa-rca -l app=jafra-agent
```

**Verify the PrometheusRule exists:**

```bash
kubectl get prometheusrule -n causa-rca
```

**Watch Causa analyse a failure** (after a pod triggers an alert):

```bash
kubectl logs -n causa-rca -l app=causa-backend -f
```

**Query RCA results directly:**

```bash
# Kind
curl http://localhost:30001/api/v1/diagnostics | jq .
```

---

## 8. Run a Demo End-to-End

The fastest way to see Causa in action is with the bundled Quarkus RCA demo. It deploys a `quarkus-perf` workload engineered for chaos testing, with a load generator that gradually fills the heap until an OOMKill occurs. Causa detects and analyses the failure autonomously.

### Set up LLM credentials

```bash
git clone https://github.com/causaai/causa-demos.git
cd causa-demos/quarkus-rca

# Copy the example env file and fill in your credentials
cp llm.env.example llm.env
# Edit llm.env — set your LLM_PROVIDER and matching credentials
```

The `llm.env.example` file documents every supported provider. At minimum, set one of:

```bash
# Vertex AI (Google Cloud)
LLM_PROVIDER=vertex-ai-anthropic
VERTEX_PROJECT_ID=<your-gcp-project-id>
VERTEX_LOCATION=us-east5
GOOGLE_APPLICATION_CREDENTIALS=~/.config/gcloud/application_default_credentials.json

# — OR —

# Anthropic Direct
LLM_PROVIDER=anthropic
LLM_MODEL_NAME=claude-sonnet-4-6
LLM_API_KEY=sk-ant-api03-...
```

> ⚠️ `llm.env` is git-ignored. Never commit it.

### Run the demo

```bash
# Full demo — provisions Kind cluster, deploys everything, registers MCP, installs the skill
./demo.sh

# Install the causa-rca skill to Bob at the same time
./demo.sh --skill-path ~/.bob/skills

# Install the causa-rca skill to Claude Code at the same time
./demo.sh --skill-path ~/.claude/skills

# Deploy against an existing OpenShift cluster
./demo.sh --target openshift -n my-rca
```

The demo script:
1. Runs the Causa installer (provisions Kind + deploys the full stack)
2. Deploys `quarkus-perf` with chaos scenarios (`large-response`, `idle-timeout`, `memory-cache`) and a load-gen job
3. Pushes your LLM credentials to Causa
4. Writes the Causa MCP config to `~/.bob/settings/mcp.json` and `~/.claude.json`
5. Optionally copies the `causa-rca` skill to the path you specified
6. Prints ready-to-paste RCA prompts

### Watch the demo in action

```bash
# Watch pod restarts as the heap fills and OOMKill fires
kubectl get pods -n causa-rca -w

# Watch Causa analyse the failure in real time
kubectl logs -n causa-rca -l app=causa-backend -f
```

The load generator fills the 512 Mi heap in approximately 3–5 minutes. Once the OOMKill fires, Prometheus triggers an alert, Alertmanager delivers it to the Causa webhook, and Causa produces a root-cause analysis automatically.

Then ask your AI assistant:

```
Why is the quarkus-perf app crashing?
```

---

## 9. Tear Down

### Causa stack only (keeps the Kind cluster)

```bash
cd installer
./install.sh --terminate
```

### Terminate port-forwards (Kind only)

```bash
pkill -f "port-forward svc/causa-backend"
pkill -f "port-forward svc/causa-mcp"
```

### Causa stack + Kind cluster

```bash
cd installer
./install.sh --terminate --delete-cluster
```

### OpenShift

```bash
cd installer
./install.sh --target openshift --terminate
```

### Quarkus RCA demo (via demo script)

```bash
cd causa-demos/quarkus-rca
./demo.sh -t                   # removes stack, keeps cluster
./demo.sh -t --delete-cluster  # removes everything
```

---

## Next Steps

- **Explore the API** — The Causa Backend exposes a full REST API. See [causa/docs/api](https://github.com/causaai/causa/tree/main/docs/api) for the full OpenAPI specification.
- **Tune LLM behaviour** — Temperature, max tokens, timeout, and model name are all configurable at runtime. See the [LLM Configuration Options](https://github.com/causaai/causa/blob/main/docs/llm/llm-config-options.md) reference.
- **Add more workloads** — Repeat [Section 5](#5-onboard-a-java-workload) for any additional Java deployment. Each newly labelled workload is automatically covered by the existing alert rule and Jafra injection with no further configuration.
- **Rotate LLM credentials** — Re-POST to `/api/v1/configs` at any time. Settings take effect immediately without restarting Causa.
- **Contribute** — All repos live under [github.com/causaai](https://github.com/causaai). Fork, open a feature branch, and raise a PR.
