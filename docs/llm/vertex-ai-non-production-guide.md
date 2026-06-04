# Vertex AI Development Guide

**Application Default Credentials (ADC) Setup for Local, KIND, and OpenShift Development**

⚠️ **FOR DEVELOPMENT AND POC ONLY - DO NOT USE IN PRODUCTION**

---

## Overview

This guide covers setting up Claude via Google Vertex AI for **development environments** using Application Default Credentials (ADC).

### Why ADC for Development?

- ✅ **Quick setup** - No service account creation needed
- ✅ **Zero configuration** - Uses your personal Google Cloud credentials  
- ✅ **No key management** - No JSON keys to download or secure
- ✅ **Works everywhere** - Local machine, KIND, OpenShift development

### Authentication Flow

```
Developer's Laptop
  ↓
gcloud auth application-default login
  ↓
ADC credentials stored in ~/.config/gcloud/
  ↓
[Local] → App reads ADC directly
[KIND/OpenShift] → ADC exported to K8s Secret → Mounted in Pod
  ↓
Vertex AI API
```

### When NOT to Use ADC

❌ **Production deployments** - Use service accounts instead  
❌ **Automated CI/CD** - Use service accounts with Workload Identity  
❌ **Shared environments** - Use dedicated service accounts  
❌ **Long-running services** - ADC tokens expire (use service accounts)

**For production**, see: [Vertex AI Production Guide](vertex-ai-production-guide.md)

---

## Prerequisites

Before starting, ensure you have:

- ✅ Google Cloud project with Vertex AI API enabled
- ✅ Claude model access granted to your project
- ✅ Billing enabled on the GCP project
- ✅ \`gcloud\` CLI installed ([Installation Guide](https://cloud.google.com/sdk/docs/install))
- ✅ For KIND: \`kubectl\` installed and KIND cluster running
- ✅ For OpenShift: \`oc\` CLI installed and logged into cluster

---

## Quick Start (Automated Script)

### Step 1: Authenticate with Google Cloud

```bash
# Authenticate and create ADC
gcloud auth application-default login

# Set quota project
export GCP_PROJECT_ID="your-gcp-project-id"
gcloud auth application-default set-quota-project $GCP_PROJECT_ID
```

### Step 2: Run Setup Script

**For Local Development:**
```bash
./scripts/llm/setup-vertex-ai.sh --env local --project $GCP_PROJECT_ID
```

**For KIND Cluster:**
```bash
./scripts/llm/setup-vertex-ai.sh --env kind --project $GCP_PROJECT_ID
```

**For OpenShift:**
```bash
./scripts/llm/setup-vertex-ai.sh --env openshift --project $GCP_PROJECT_ID
```

The script will:
1. ✅ Detect and validate your ADC credentials
2. ✅ Enable Vertex AI API (if needed)
3. ✅ Grant you \`aiplatform.user\` role
4. ✅ Update ConfigMap with Vertex AI settings
5. ✅ [KIND/OpenShift] Export ADC to Kubernetes secret
6. ✅ [KIND/OpenShift] Patch deployment to mount credentials
7. ✅ [KIND/OpenShift] Restart and validate deployment

---

## Manual Setup

If you prefer to set up manually or need to troubleshoot:

### Local Development (No Kubernetes)

#### 1. Set Up ADC

```bash
# Authenticate
gcloud auth application-default login

# Set quota project
export GCP_PROJECT_ID="your-gcp-project-id"
gcloud auth application-default set-quota-project $GCP_PROJECT_ID

# Grant yourself permissions
export USER_EMAIL=$(gcloud config get-value account)
gcloud projects add-iam-policy-binding $GCP_PROJECT_ID \
  --member="user:$USER_EMAIL" \
  --role="roles/aiplatform.user"
```

#### 2. Configure Environment

Create \`.env\` file in project root:

```bash
LLM_PROVIDER=vertex-ai-anthropic
LLM_MODEL_NAME=claude-sonnet-4-6
VERTEX_PROJECT_ID=your-gcp-project-id
VERTEX_LOCATION=us-east5
LLM_TEMPERATURE=0.1
LLM_MAX_TOKENS=8192
```

#### 3. Run Locally

```bash
# Load environment variables
export $(cat .env | xargs)

# Run Quarkus dev mode
./mvnw quarkus:dev
```

**Expected logs:**
```
INFO  Initializing LLM chat model factory | provider="vertex-ai-anthropic"
INFO  LLM provider detected | provider="vertex-ai-anthropic", authType="ADC"
INFO  Verifying LLM connectivity
INFO  LLM connectivity verified | latencyMs=1234
INFO  LLM ready | provider="vertex-ai-anthropic", model="claude-sonnet-4-6"
```

---

### KIND Cluster Setup

#### 1. Set Up ADC (if not already done)

```bash
gcloud auth application-default login
export GCP_PROJECT_ID="your-gcp-project-id"
gcloud auth application-default set-quota-project $GCP_PROJECT_ID
```

#### 2. Create Secrets

```bash
# Project ID secret
kubectl create secret generic causa-llm-secrets \
  --from-literal=VERTEX_PROJECT_ID=$GCP_PROJECT_ID \
  -n diagnostics-tool

# ADC credentials secret
kubectl create secret generic gcp-adc-credentials \
  --from-file=application_default_credentials.json=$HOME/.config/gcloud/application_default_credentials.json \
  -n diagnostics-tool

# Add labels
kubectl label secret gcp-adc-credentials \
  app.kubernetes.io/name=causa-backend \
  causa.dev/auth-type=adc \
  causa.dev/purpose=development \
  -n diagnostics-tool
```

#### 3. Patch Deployment

```bash
# Apply the ADC patch
kubectl patch deployment causa-backend -n diagnostics-tool \
  --patch-file deployment/kubernetes/vertex-ai/deployment-adc-patch.yaml

# Restart deployment
kubectl rollout restart deployment/causa-backend -n diagnostics-tool
kubectl rollout status deployment/causa-backend -n diagnostics-tool
```

#### 4. Verify

```bash
# Check pod logs
kubectl logs -f deployment/causa-backend -n diagnostics-tool | grep LLM

# Verify secret mounted
kubectl exec -n diagnostics-tool deployment/causa-backend -- \
  ls -la /var/secrets/google/

# Check environment variable
kubectl exec -n diagnostics-tool deployment/causa-backend -- \
  env | grep GOOGLE_APPLICATION_CREDENTIALS

# Check health endpoint
kubectl exec -n diagnostics-tool deployment/causa-backend -- \
  curl -s http://localhost:8080/q/health/ready | jq '.checks[] | select(.name=="llm")'
```

---

### OpenShift Setup

Same as KIND but using \`oc\` commands instead of \`kubectl\`:

```bash
# Create secrets
oc create secret generic causa-llm-secrets \
  --from-literal=VERTEX_PROJECT_ID=$GCP_PROJECT_ID \
  -n diagnostics-tool

oc create secret generic gcp-adc-credentials \
  --from-file=application_default_credentials.json=$HOME/.config/gcloud/application_default_credentials.json \
  -n diagnostics-tool

# Patch deployment
oc patch deployment ocp-causa-backend -n diagnostics-tool \
  --patch-file deployment/kubernetes/vertex-ai/deployment-adc-patch.yaml

# Restart
oc rollout restart deployment/ocp-causa-backend -n diagnostics-tool
oc rollout status deployment/ocp-causa-backend -n diagnostics-tool

# Verify
oc logs -f deployment/ocp-causa-backend -n diagnostics-tool | grep LLM
```

---

## Refreshing Expired Credentials

ADC tokens expire periodically (typically after 1 hour). When they expire:

### Symptoms:
- \`401 Unauthorized\` errors in logs
- Health check fails with authentication error
- Pod logs show: \`Invalid Credentials\`

### Solution:

**For Local:**
```bash
# Re-authenticate
gcloud auth application-default login
gcloud auth application-default set-quota-project $GCP_PROJECT_ID

# Restart app
./mvnw quarkus:dev
```

**For KIND/OpenShift:**
```bash
# Re-run setup script to refresh credentials
./scripts/llm/setup-vertex-ai.sh --env [kind|openshift] --project $GCP_PROJECT_ID

# Then apply the refreshed configuration
cd deployment/kubernetes/vertex-ai/generated
./apply.sh
```

Or manually:
```bash
# Delete old secret
kubectl delete secret gcp-adc-credentials -n diagnostics-tool

# Create new secret with fresh ADC
kubectl create secret generic gcp-adc-credentials \
  --from-file=application_default_credentials.json=$HOME/.config/gcloud/application_default_credentials.json \
  -n diagnostics-tool

# Restart deployment
kubectl rollout restart deployment/causa-backend -n diagnostics-tool
```


---

## Limitations

### Development Use Only

⚠️ **This approach is NOT suitable for production because:**

1. **Personal Identity** - Workload runs under your Google account
2. **Token Expiration** - ADC tokens expire frequently (1 hour)
3. **Not Scalable** - Tied to a single developer's credentials
4. **Audit Issues** - Usage attributed to individual, not service
5. **No Automation** - Requires interactive login (not CI/CD friendly)
6. **Security Risk** - Developer credentials may have broader permissions

### When to Switch to Service Accounts

Switch to service account-based authentication when:

- ✅ Deploying to production
- ✅ Setting up CI/CD pipelines
- ✅ Running long-lived services
- ✅ Sharing environments across teams
- ✅ Need audit trails per service
- ✅ Implementing least-privilege security

**See:** [Vertex AI Production Guide](vertex-ai-production-guide.md)

---

## Cleanup

### Remove ADC Setup from KIND/OpenShift

```bash
# Delete secrets
kubectl delete secret gcp-adc-credentials -n diagnostics-tool
kubectl delete secret causa-llm-secrets -n diagnostics-tool

# Rollback deployment (removes ADC patch)
kubectl rollout undo deployment/causa-backend -n diagnostics-tool
```

### Revoke Local ADC

```bash
# Revoke ADC credentials
gcloud auth application-default revoke

# Delete local ADC file
rm ~/.config/gcloud/application_default_credentials.json

# Delete .env file
rm .env
```
