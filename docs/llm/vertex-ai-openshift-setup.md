# Vertex AI Setup - OpenShift Production

Guide for setting up Claude via Google Vertex AI for **production** on OpenShift clusters.

---

## Overview

For OpenShift production, we use **GCP Service Account Keys** mounted as Kubernetes Secrets.

**Authentication Flow:**
```
OpenShift Pod
  → Reads /var/secrets/google/key.json (mounted Secret)
  → GOOGLE_APPLICATION_CREDENTIALS env var points to key file
  → Java app uses service account credentials
  → Calls Vertex AI API
```

---

## Prerequisites

- ✅ OpenShift cluster access
- ✅ `oc` CLI configured
- ✅ `gcloud` CLI installed
- ✅ Google Cloud project with Vertex AI + Claude enabled
- ✅ Permissions to create GCP service accounts

---

## Quick Setup (Automated Script)

**Recommended:** Use our automated setup script:

```bash
./scripts/llm/setup-vertex-ai.sh --env production --project your-gcp-project-id
```

The script will:
1. Create GCP service account
2. Grant Vertex AI permissions
3. Download service account key
4. Create Kubernetes secrets
5. Apply deployment patches for key mount

**Continue reading for manual setup.**

---

## Manual Setup

### Step 1: Create GCP Service Account

```bash
export GCP_PROJECT_ID="your-gcp-project-id"
export GCP_SA_NAME="causa-backend-prod"
export K8S_NAMESPACE="diagnostics-tool"

# Create service account
gcloud iam service-accounts create $GCP_SA_NAME \
  --display-name="Causa Backend Production (OpenShift)" \
  --description="Service account for Causa Backend Vertex AI access" \
  --project=$GCP_PROJECT_ID
```

**Verify:**
```bash
gcloud iam service-accounts list --project=$GCP_PROJECT_ID | grep $GCP_SA_NAME
```

---

### Step 2: Grant Vertex AI Permissions

```bash
# Grant aiplatform.user role (Vertex AI access)
gcloud projects add-iam-policy-binding $GCP_PROJECT_ID \
  --member="serviceAccount:$GCP_SA_NAME@$GCP_PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/aiplatform.user"
```

**Verify:**
```bash
gcloud projects get-iam-policy $GCP_PROJECT_ID \
  --flatten="bindings[].members" \
  --filter="bindings.members:$GCP_SA_NAME@$GCP_PROJECT_ID.iam.gserviceaccount.com"
```

---

### Step 3: Download Service Account Key

```bash
# Generate and download key
gcloud iam service-accounts keys create causa-backend-sa-key.json \
  --iam-account=$GCP_SA_NAME@$GCP_PROJECT_ID.iam.gserviceaccount.com \
  --project=$GCP_PROJECT_ID
```

⚠️ **CRITICAL SECURITY:**
- Store this file securely (password manager, vault)
- NEVER commit to Git
- Delete local copy after creating K8s secret
- Rotate keys every 90 days

---

### Step 4: Create Kubernetes Secrets

```bash
# Secret 1: GCP Service Account Key
oc create secret generic gcp-sa-key \
  --from-file=key.json=causa-backend-sa-key.json \
  -n $K8S_NAMESPACE

# Secret 2: Vertex Project ID  
oc create secret generic causa-llm-secrets \
  --from-literal=VERTEX_PROJECT_ID=$GCP_PROJECT_ID \
  -n $K8S_NAMESPACE

# Verify
oc get secrets -n $K8S_NAMESPACE | grep -E 'gcp-sa-key|causa-llm-secrets'
```

---

### Step 5: Update ConfigMap

Edit `deployment/kubernetes/base/configmap.yaml`:

```yaml
data:
  LLM_PROVIDER: "vertex-ai-anthropic"
  LLM_MODEL_NAME: "claude-sonnet-4-6"
  VERTEX_LOCATION: "global"
  LLM_TEMPERATURE: "0.1"
  LLM_MAX_TOKENS: "4096"
```

---

### Step 6: Create Deployment Patch for Key Mount

Create `deployment/kubernetes/overlays/openshift/deployment-vertex-patch.yaml`:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: causa-backend
spec:
  template:
    spec:
      containers:
      - name: causa-backend
        env:
        - name: GOOGLE_APPLICATION_CREDENTIALS
          value: /var/secrets/google/key.json
        volumeMounts:
        - name: gcp-sa-key
          mountPath: /var/secrets/google
          readOnly: true
      volumes:
      - name: gcp-sa-key
        secret:
          secretName: gcp-sa-key
          items:
          - key: key.json
            path: key.json
```

---

### Step 7: Update Kustomization

Edit `deployment/kubernetes/overlays/openshift/kustomization.yaml`:

```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization

bases:
  - ../../base

patchesStrategicMerge:
  - deployment-vertex-patch.yaml

namespace: diagnostics-tool
```

---

### Step 8: Deploy to OpenShift

```bash
# Deploy
oc apply -k deployment/kubernetes/overlays/openshift

# Watch rollout
oc rollout status deployment/causa-backend -n $K8S_NAMESPACE

# Check logs
oc logs -f deployment/causa-backend -n $K8S_NAMESPACE | grep LLM
```

**Expected logs:**
```
INFO  Initializing LLM chat model factory | provider="vertex-ai-anthropic"
INFO  LLM provider detected | provider="vertex-ai-anthropic", authType="ADC"
INFO  Verifying LLM connectivity
INFO  LLM connectivity verified
INFO  LLM ready | provider="vertex-ai-anthropic", model="claude-sonnet-4-6"
```

---

### Step 9: Verify Deployment

```bash
# Check pod is running
oc get pods -n $K8S_NAMESPACE -l app.kubernetes.io/name=causa-backend

# Check service account key is mounted
oc exec -n $K8S_NAMESPACE deployment/causa-backend -- \
  ls -la /var/secrets/google/

# Should show: key.json

# Check environment variable
oc exec -n $K8S_NAMESPACE deployment/causa-backend -- \
  env | grep GOOGLE_APPLICATION_CREDENTIALS

# Should show: /var/secrets/google/key.json

# Check health endpoint
oc exec -n $K8S_NAMESPACE deployment/causa-backend -- \
  curl -s http://localhost:8080/q/health/ready | jq '.checks[] | select(.name=="llm")'

# Should show: "status": "UP"
```

---

## Security Best Practices

### 1. Key Rotation (Every 90 Days)

```bash
# List existing keys
gcloud iam service-accounts keys list \
  --iam-account=$GCP_SA_NAME@$GCP_PROJECT_ID.iam.gserviceaccount.com

# Create new key
gcloud iam service-accounts keys create new-key.json \
  --iam-account=$GCP_SA_NAME@$GCP_PROJECT_ID.iam.gserviceaccount.com

# Update secret
oc create secret generic gcp-sa-key \
  --from-file=key.json=new-key.json \
  -n $K8S_NAMESPACE \
  --dry-run=client -o yaml | oc apply -f -

# Restart deployment
oc rollout restart deployment/causa-backend -n $K8S_NAMESPACE

# Delete old key (after verifying new one works)
gcloud iam service-accounts keys delete <OLD_KEY_ID> \
  --iam-account=$GCP_SA_NAME@$GCP_PROJECT_ID.iam.gserviceaccount.com
```

### 2. Audit Key Usage

```bash
# Check when keys were created
gcloud iam service-accounts keys list \
  --iam-account=$GCP_SA_NAME@$GCP_PROJECT_ID.iam.gserviceaccount.com \
  --format="table(name,validAfterTime,validBeforeTime)"
```

### 3. Limit Permissions

Only grant `roles/aiplatform.user` — do NOT grant:
- ❌ `roles/owner`
- ❌ `roles/editor`  
- ❌ `roles/iam.serviceAccountKeyAdmin`

### 4. Use Sealed Secrets (Optional)

For GitOps workflows:

```bash
# Install Sealed Secrets controller
oc apply -f https://github.com/bitnami-labs/sealed-secrets/releases/download/v0.18.0/controller.yaml

# Seal the secret
kubeseal --format yaml < gcp-sa-key-secret.yaml > gcp-sa-key-sealed.yaml

# Commit sealed secret to Git (safe!)
git add gcp-sa-key-sealed.yaml
```