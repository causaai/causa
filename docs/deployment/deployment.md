# Kubernetes Deployment Guide

This directory contains Kubernetes manifests for deploying Causa Backend using Kustomize.

## Directory Structure

```
deployment/kubernetes/
├── base/                    # Base Kubernetes resources
│   ├── deployment.yaml      # Main application deployment
│   ├── service.yaml         # ClusterIP service
│   ├── configmap.yaml       # Configuration
│   ├── serviceaccount.yaml  # Service account
│   ├── namespace.yaml       # Base namespace
│   └── kustomization.yaml   # Base kustomization
│
└── overlays/
    ├── kind/                # Kind (local) environment
    │   ├── configmap-patch.yaml
    │   ├── ingress.yaml     # NGINX Ingress
    │   └── kustomization.yaml
    │
    └── openshift/           # OpenShift environment
        ├── configmap-patch.yaml
        ├── deployment-patch.yaml  # Security contexts
        ├── route.yaml       # OpenShift Route
        └── kustomization.yaml
```

## Prerequisites

### For All Deployments
- `kubectl` CLI installed
- `kustomize` installed (or kubectl 1.14+)
- Access to a Kubernetes cluster

### For Kind (Local Development)
- [kind](https://kind.sigs.kubernetes.io/) installed
- For Vertex AI: `gcloud` CLI installed

### For OpenShift (Production)
- `oc` CLI installed
- Access to an OpenShift cluster
- For Vertex AI: `gcloud` CLI installed

## LLM Setup (Required First)

Causa Backend requires LLM (Claude/ Ollama/ BoB) integration. You **must** create secrets **before** deploying.

**IMPORTANT:** Secrets are managed **separately** from kustomize. Never add secrets to kustomization.yaml.

Refer to [Kubernetes Secrets Guide](kubernetes-secrets.md) for more details.

---

### CASE 1: Direct Anthropic API (Simplest)

#### Step 1: Create Secret

```bash
# Recommended: Create secret directly with kubectl
kubectl create secret generic causa-llm-secrets \
  --from-literal=LLM_API_KEY=sk-ant-api03-xxxxxxxx \
  -n diagnostics-tool
```

**Alternative:** Use template from `deployment/kubernetes/secrets/secret.yaml`

#### Step 2: Update ConfigMap

Edit `deployment/kubernetes/base/configmap.yaml`:
```yaml
data:
  LLM_PROVIDER: "anthropic"
  LLM_MODEL_NAME: "claude-sonnet-4-6"
```

**Done!** Proceed to deployment steps below.

---

### CASE 2: Claude via Google Vertex AI

⚠️ **IMPORTANT: Vertex AI requires a specific deployment order!**

#### Automated Setup (Recommended)

**Generate Configuration**
```bash
./scripts/llm/setup-vertex-ai.sh --env [kind|openshift] --project YOUR_GCP_PROJECT_ID
```

This generates deployment files in `deployment/kubernetes/vertex-ai/generated/`


**See detailed guides:**
- **Development/POC:** [Vertex AI Non-Production Guide](../llm/vertex-ai-non-production-guide.md)
- **Production:** [Vertex AI Production Guide](../llm/vertex-ai-production-guide.md)

#### Manual Setup

**For Development (Local/KIND/OpenShift):** See [Vertex AI Non-Production Guide](../llm/vertex-ai-non-production-guide.md)
**For Production:** See [Vertex AI Production Guide](../llm/vertex-ai-production-guide.md)

---

### Secret Templates

A template file with placeholders is available in `deployment/kubernetes/secrets/`:
- [secret.yaml](../../deployment/kubernetes/secrets/secret.yaml) - LLM API key template

**For Vertex AI:** Use the automated setup script instead:
```bash
./scripts/llm/setup-vertex-ai.sh --env [local|kind|openshift] --project <GCP_PROJECT_ID>
```

**See:**
- [Kubernetes Secrets Guide](kubernetes-secrets.md) for Anthropic secrets
---

## Quick Start Deployment

### 1. Deploy to Kind (Local Development)

#### Create Kind Cluster (if needed)
```bash
# Create simple KIND cluster (no ingress needed)
kind create cluster --name causa-dev

# Verify cluster
kubectl cluster-info --context kind-causa-dev
```

#### Deploy Causa

**If using Vertex AI:**
```bash
# Use the generated apply script
cd deployment/kubernetes/vertex-ai/generated
./apply.sh

# This handles the correct deployment order automatically


"OR"


# 1. Deploy base deployment FIRST (if not already deployed)
kubectl apply -k deployment/kubernetes/overlays/kind  # or overlays/openshift

# 2. Apply ADC secrets
cd deployment/kubernetes/vertex-ai/generated
kubectl apply -f causa-llm-secrets.yaml
kubectl apply -f gcp-adc-credentials.yaml

# 3. Patch deployment to mount secrets
kubectl patch deployment causa-backend -n diagnostics-tool \
  --patch-file deployment-adc-patch.yaml

# 4. Restart deployment to pick up changes
kubectl rollout restart deployment/causa-backend -n diagnostics-tool
kubectl rollout status deployment/causa-backend -n diagnostics-tool

```

**If using Anthropic Direct API:**
```bash
# IMPORTANT: Create secrets first (see LLM Setup above)

# From repository root
kubectl apply -k deployment/kubernetes/overlays/kind

# Verify deployment
kubectl get all -n diagnostics-tool

# Check logs
kubectl logs -n diagnostics-tool -l app.kubernetes.io/name=causa-backend -f
```

#### Access Application

**Using Port-Forward:**
```bash
# Forward service port to localhost
kubectl port-forward -n diagnostics-tool svc/causa-backend 8080:8080

# In another terminal, access endpoints
curl http://localhost:8080/q/health/live
curl http://localhost:8080/q/health/ready

# Check LLM health specifically
curl http://localhost:8080/q/health/ready | jq '.checks[] | select(.name=="llm")'
```

**Keep port-forward running in background:**
```bash
# Run in background
kubectl port-forward -n diagnostics-tool svc/causa-backend 8080:8080 &

# Stop later with: fg then Ctrl+C
```

### 2. Deploy to OpenShift

#### Login to OpenShift
```bash
oc login --server=https://api.your-cluster.com:6443
```

#### Deploy Causa

**If using Vertex AI:**
```bash
# Use the generated apply script
cd deployment/kubernetes/vertex-ai/generated
./apply.sh

# This handles the correct deployment order automatically


"OR"


# 1. Deploy base deployment FIRST (if not already deployed)
kubectl apply -k deployment/kubernetes/overlays/openshift  # or overlays/openshift

# 2. Apply ADC secrets
cd deployment/kubernetes/vertex-ai/generated
kubectl apply -f causa-llm-secrets.yaml
kubectl apply -f gcp-adc-credentials.yaml

# 3. Patch deployment to mount secrets
kubectl patch deployment causa-backend -n diagnostics-tool \
  --patch-file deployment-adc-patch.yaml

# 4. Restart deployment to pick up changes
kubectl rollout restart deployment/causa-backend -n diagnostics-tool
kubectl rollout status deployment/causa-backend -n diagnostics-tool

```

**If using Anthropic Direct API:**
```bash
# IMPORTANT: Create secrets first (see LLM Setup above)

# From repository root
oc apply -k deployment/kubernetes/overlays/openshift

# Verify deployment
oc get all -n diagnostics-tool

# Check logs
oc logs -n diagnostics-tool -l app.kubernetes.io/name=causa-backend -f

# Get route URL
oc get route -n diagnostics-tool ocp-causa-backend -o jsonpath='{.spec.host}'
```

#### Access Application
```bash
# Get the route
ROUTE_URL=$(oc get route -n diagnostics-tool ocp-causa-backend -o jsonpath='{.spec.host}')

# Access endpoints (HTTPS)
curl https://$ROUTE_URL/q/health/live
curl https://$ROUTE_URL/q/health/ready
```

## Configuration

All configuration is managed via ConfigMaps and can be customized per environment.

### Base Configuration (all environments)

Edit `deployment/kubernetes/base/configmap.yaml`:
```yaml
data:
  CAUSA_PORT: "8080"
  CAUSA_LOG_LEVEL: "INFO"
```

### Kind-Specific Configuration

Edit `deployment/kubernetes/overlays/kind/configmap-patch.yaml`:
```yaml
data:
  CAUSA_LOG_LEVEL: "DEBUG"  # More verbose for local dev
  CLUSTER_TYPE: "kind"
```

### OpenShift-Specific Configuration

Edit `deployment/kubernetes/overlays/openshift/configmap-patch.yaml`:
```yaml
data:
  CAUSA_LOG_LEVEL: "INFO"
  CLUSTER_TYPE: "openshift"
```

## Environment Variables

| Variable | Description | Default | Kind | OpenShift |
|----------|-------------|---------|------|-----------|
| `CAUSA_PORT` | HTTP port | `8080` | `8080` | `8080` |
| `CAUSA_LOG_LEVEL` | Log level | `INFO` | `DEBUG` | `INFO` |
| `CAUSA_SWAGGER_UI_PATH` | Swagger UI path | `/swagger-ui` | `/swagger-ui` | `/swagger-ui` |
| `CLUSTER_TYPE` | Cluster type | - | `kind` | `openshift` |

## Customization

### Change Image Tag

#### For Kind
Edit `deployment/kubernetes/overlays/kind/kustomization.yaml`:
```yaml
images:
  - name: quay.io/rh-ee-shesaxen/causa-backend
    newTag: v1.0.0  # Change this
```

#### For OpenShift
Edit `deployment/kubernetes/overlays/openshift/kustomization.yaml`:
```yaml
images:
  - name: quay.io/rh-ee-shesaxen/causa-backend
    newTag: v1.0.0  # Change this
```

### Change Replica Count

#### For Kind
Edit `deployment/kubernetes/overlays/kind/kustomization.yaml`:
```yaml
replicas:
  - name: causa-backend
    count: 2  # Change this
```

#### For OpenShift
Edit `deployment/kubernetes/overlays/openshift/kustomization.yaml`:
```yaml
replicas:
  - name: causa-backend
    count: 3  # Change this
```

### Resource Limits

#### For Kind
Uses base resources (defined in `deployment/kubernetes/base/deployment.yaml`):
- Requests: 100m CPU, 256Mi memory
- Limits: 500m CPU, 512Mi memory

#### For OpenShift
Override in `deployment/kubernetes/overlays/openshift/deployment-patch.yaml`:
- Requests: 200m CPU, 512Mi memory
- Limits: 1000m CPU, 1Gi memory

## Health Checks

The deployment includes two types of health probes:

### Liveness Probe
- **Path**: `/q/health/live`
- **Purpose**: Determines if pod should be restarted
- **Initial Delay**: 30s
- **Period**: 10s
- **Timeout**: 3s
- **Failure Threshold**: 3

### Readiness Probe
- **Path**: `/q/health/ready`
- **Purpose**: Determines if pod should receive traffic
- **Initial Delay**: 30s
- **Period**: 5s
- **Timeout**: 3s
- **Failure Threshold**: 3

## Monitoring

### Prometheus Metrics

Metrics are exposed at `/q/metrics` on port 8080.

Pods are annotated for Prometheus auto-discovery:
```yaml
annotations:
  prometheus.io/scrape: "true"
  prometheus.io/port: "8080"
  prometheus.io/path: "/q/metrics"
```

## Cleanup

### Kind
```bash
# Delete Causa
kubectl delete -k deployment/kubernetes/overlays/kind

# Delete entire cluster
kind delete cluster --name causa-dev
```

### OpenShift
```bash
# Delete Causa
oc delete -k deployment/kubernetes/overlays/openshift

# Or delete namespace (removes everything)
oc delete namespace diagnostics-tool
```

## Advanced Usage

### Dry Run (Preview Changes)

#### Kind
```bash
kubectl kustomize deployment/kubernetes/overlays/kind
```

#### OpenShift
```bash
oc kustomize deployment/kubernetes/overlays/openshift
```

## Security

### Kind
- Uses default Kubernetes security context
- No specific security constraints

### OpenShift
- Uses `restricted` SCC (Security Context Constraint)
- `runAsNonRoot: true`
- Drops all capabilities
- `allowPrivilegeEscalation: false`
- TLS termination at Route level