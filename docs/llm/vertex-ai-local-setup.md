# Vertex AI Setup - Local Development (Kind)

Guide for setting up Claude via Google Vertex AI for **local development** on Kind clusters.

---

## Overview

For local development, we use **Application Default Credentials (ADC)** — no service account keys needed.

**Authentication Flow:**
```
Your laptop
  → gcloud auth application-default login
  → Credentials stored in ~/.config/gcloud/
  → Java app reads credentials automatically (ADC)
  → Calls Vertex AI API
```

---

## Prerequisites

- ✅ Google Cloud project with Vertex AI enabled
- ✅ Claude model access granted
- ✅ Billing enabled on the project
- ✅ `gcloud` CLI installed
- ✅ Kind cluster running locally

---

## Step 1: Install gcloud CLI

Please refer to: https://docs.cloud.google.com/sdk/docs/install-sdk 

**Verify:**
```bash
gcloud --version
```

---


> **NOTE: Please refer to your organisation specific installation guide for specific steps. You can skip STEP 2 and STEP 3 if you have already done the claude setup on your system. Verify ADC using: `gcloud auth application-default print-access-token`**

## Step 2: Authenticate with Google Cloud

```bash
# Initialize gcloud
gcloud init

# Follow prompts:
# 1. Log in with your Google account
# 2. Select your GCP project
```

**Set your project:**
```bash
export GCP_PROJECT_ID="your-gcp-project-id"
gcloud config set project $GCP_PROJECT_ID
```

---

## Step 3: Set Up Application Default Credentials

```bash
# Authenticate and create ADC credentials
gcloud auth application-default login

# Set quota project (prevents billing errors)
gcloud auth application-default set-quota-project $GCP_PROJECT_ID
```

**Verify ADC is working:**
```bash
gcloud auth application-default print-access-token
# Should output: ya29.xxx... (a valid access token)
```

---

## Step 4: Grant Yourself Vertex AI Permissions

```bash
# Get your email
export USER_EMAIL=$(gcloud config get-value account)

# Grant yourself aiplatform.user role
gcloud projects add-iam-policy-binding $GCP_PROJECT_ID \
  --member="user:$USER_EMAIL" \
  --role="roles/aiplatform.user"

# Choose None if asked for options when prompted.
```

---

## Step 5: Configure Causa Backend

Create `.env` file in project root:

```bash
# LLM Provider
LLM_PROVIDER=vertex-ai-anthropic
LLM_MODEL_NAME=claude-sonnet-4-6

# Vertex AI Configuration
VERTEX_PROJECT_ID=your-gcp-project-id
VERTEX_LOCATION=global

# Inference Parameters
LLM_TEMPERATURE=0.1
LLM_MAX_TOKENS=4096
LLM_TIMEOUT_SECONDS=90
```

---

## Step 6: Run Locally (Quarkus Dev Mode)

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

## Step 7: Deploy to Kind Cluster

### Update ConfigMap

Edit `deployment/kubernetes/base/configmap.yaml`:

```yaml
data:
  LLM_PROVIDER: "vertex-ai-anthropic"
  LLM_MODEL_NAME: "claude-sonnet-4-6"
  VERTEX_LOCATION: "global"
```

### Create Secret

```bash
kubectl create secret generic causa-llm-secrets \
  --from-literal=VERTEX_PROJECT_ID=$GCP_PROJECT_ID \
  -n diagnostics-tool
```

### Deploy

```bash
kubectl apply -k deployment/kubernetes/overlays/kind
```

### Verify

```bash
# Check pod logs
kubectl logs -f -n diagnostics-tool deployment/causa-backend | grep LLM

# Check health
kubectl exec -n diagnostics-tool deployment/causa-backend -- \
  curl -s http://localhost:8080/q/health/ready | jq '.checks[] | select(.name=="llm")'
```

---


## Cleanup

To remove local credentials:

```bash
# Revoke ADC
gcloud auth application-default revoke

# Delete .env file
rm .env
```
