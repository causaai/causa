# Vertex AI Production Guide (NOT TESTED YET, PLANNED)

**Service Account-Based Authentication for Production Deployments**

✅ **RECOMMENDED FOR PRODUCTION ENVIRONMENTS**

---

## Overview

This guide covers setting up Claude via Google Vertex AI for **production deployments** using GCP Service Accounts.

### Why Service Accounts for Production?

- ✅ **Dedicated identity** - Not tied to individual developers
- ✅ **Long-lived credentials** - No expiration (with proper rotation)
- ✅ **Auditable** - Clear attribution in logs and billing
- ✅ **Scalable** - Works for multiple pods and environments
- ✅ **Automated** - Suitable for CI/CD pipelines
- ✅ **Least privilege** - Granular IAM permissions

### Why NOT Application Default Credentials?

❌ **Personal identity** - Tied to an individual developer  
❌ **Token expiration** - Requires frequent re-authentication  
❌ **Not scalable** - Cannot be shared across pods  
❌ **Poor auditing** - Usage attributed to individual, not service  
❌ **Security risk** - Developer credentials may have excessive permissions

**For development**, see: [Vertex AI Development Guide](vertex-ai-development-guide.md)

---

## Authentication Flow

```
GCP Service Account
  ↓
Service Account Key (JSON file)
  ↓
Kubernetes/OpenShift Secret
  ↓
Mounted in Pod as /var/secrets/google/key.json
  ↓
GOOGLE_APPLICATION_CREDENTIALS env var
  ↓
Vertex AI API
```

---

## Prerequisites

- ✅ Google Cloud project with Vertex AI API enabled
- ✅ Claude model access granted to your project
- ✅ Billing enabled on the GCP project
- ✅ `gcloud` CLI installed
- ✅ Permissions to create service accounts in GCP
- ✅ Permissions to grant IAM roles
- ✅ `kubectl` or `oc` CLI for Kubernetes/OpenShift

---

## Step 1: Create GCP Service Account

### Using gcloud CLI

```bash
export GCP_PROJECT_ID="your-production-gcp-project"
export GCP_SA_NAME="causa-backend-prod"
export K8S_NAMESPACE="diagnostics-tool"

# Create service account
gcloud iam service-accounts create $GCP_SA_NAME \
  --display-name="Causa Backend Production (OpenShift)" \
  --description="Service account for Causa Backend Vertex AI access in production" \
  --project=$GCP_PROJECT_ID
```

**Verify:**
```bash
gcloud iam service-accounts list --project=$GCP_PROJECT_ID | grep $GCP_SA_NAME
```

Expected output:
```
causa-backend-prod@your-project.iam.gserviceaccount.com
```

### Using GCP Console

1. Navigate to: **IAM & Admin** → **Service Accounts**
2. Click **Create Service Account**
3. Enter:
   - **Name**: `causa-backend-prod`
   - **Description**: `Production service account for Causa Backend Vertex AI`
4. Click **Create and Continue**
5. Skip "Grant access to this service account" (we'll do this next)
6. Click **Done**

---

## Step 2: Grant Vertex AI Permissions

### Using gcloud CLI

```bash
# Grant aiplatform.user role to the service account
gcloud projects add-iam-policy-binding $GCP_PROJECT_ID \
  --member="serviceAccount:$GCP_SA_NAME@$GCP_PROJECT_ID.iam.gserviceaccount.com" \
  --role="roles/aiplatform.user" \
  --condition=None
```

**Verify:**
```bash
gcloud projects get-iam-policy $GCP_PROJECT_ID \
  --flatten="bindings[].members" \
  --filter="bindings.members:$GCP_SA_NAME@$GCP_PROJECT_ID.iam.gserviceaccount.com"
```

### Using GCP Console

1. Navigate to: **IAM & Admin** → **IAM**
2. Find your service account: `causa-backend-prod@...`
3. Click **Edit** (pencil icon)
4. Click **Add Another Role**
5. Select: **Vertex AI User** (`roles/aiplatform.user`)
6. Click **Save**

### Least Privilege Principle

⚠️ **Only grant the minimum required permissions:**

| Role | Purpose | Required? |
|------|---------|-----------|
| `roles/aiplatform.user` | Call Vertex AI models | ✅ Yes |
| `roles/owner` | Full project access | ❌ Never |
| `roles/editor` | Broad write access | ❌ Never |
| `roles/iam.serviceAccountKeyAdmin` | Manage SA keys | ❌ No |

---

## Step 3: Create and Download Service Account Key

### Using gcloud CLI

```bash
# Create key and download
gcloud iam service-accounts keys create causa-backend-sa-key.json \
  --iam-account=$GCP_SA_NAME@$GCP_PROJECT_ID.iam.gserviceaccount.com \
  --project=$GCP_PROJECT_ID
```

Expected output:
```
created key [KEY_ID] of type [json] as [causa-backend-sa-key.json]
```

### Using GCP Console

1. Navigate to: **IAM & Admin** → **Service Accounts**
2. Click on: `causa-backend-prod@...`
3. Go to **Keys** tab
4. Click **Add Key** → **Create new key**
5. Select **JSON** format
6. Click **Create**
7. Key file downloads automatically

---

## ⚠️ CRITICAL SECURITY STEPS

### Secure the Key File

**Immediately after downloading:**

1. **Store in password manager or vault**
   - 1Password
   - LastPass
   - Google Secret Manager
   - AWS Secrets Manager
   - HashiCorp Vault

2. **NEVER commit to Git**
   - Already in `.gitignore`: `*-key.json`, `causa-backend-sa-key.json`
   - Double-check: `git status` should NOT show key file

3. **Limit access**
   - Only platform/SRE team members
   - Use secret management system with audit logs

4. **Delete local copy after deployment**
   ```bash
   # Securely delete (macOS/Linux)
   shred -u causa-backend-sa-key.json
   
   # Or overwrite then delete
   cat /dev/urandom > causa-backend-sa-key.json
   rm causa-backend-sa-key.json
   ```

---

## Step 4: Create Kubernetes/OpenShift Secret

### For Kubernetes (KIND, GKE, etc.)

```bash
# Create namespace (if needed)
kubectl create namespace $K8S_NAMESPACE

# Create secret from service account key
kubectl create secret generic gcp-sa-key \
  --from-file=key.json=causa-backend-sa-key.json \
  -n $K8S_NAMESPACE

# Create Vertex project ID secret
kubectl create secret generic causa-llm-secrets \
  --from-literal=VERTEX_PROJECT_ID=$GCP_PROJECT_ID \
  -n $K8S_NAMESPACE

# Verify secrets created
kubectl get secrets -n $K8S_NAMESPACE | grep -E 'gcp-sa-key|causa-llm-secrets'
```

### For OpenShift

```bash
# Create namespace/project (if needed)
oc new-project $K8S_NAMESPACE

# Create secret from service account key
oc create secret generic gcp-sa-key \
  --from-file=key.json=causa-backend-sa-key.json \
  -n $K8S_NAMESPACE

# Create Vertex project ID secret
oc create secret generic causa-llm-secrets \
  --from-literal=VERTEX_PROJECT_ID=$GCP_PROJECT_ID \
  -n $K8S_NAMESPACE

# Verify
oc get secrets -n $K8S_NAMESPACE | grep -E 'gcp-sa-key|causa-llm-secrets'
```

---

## Step 5: Update Deployment to Mount Service Account Key

### Create Deployment Patch

For **production OpenShift**, the existing deployment patch already supports service account keys:

**File:** `deployment/kubernetes/overlays/openshift/deployment-patch.yaml`

This patch is already configured in the base setup. If you need to add the service account key mount:

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

### Apply the Deployment

**For Kubernetes:**
```bash
# Apply kustomization
kubectl apply -k deployment/kubernetes/overlays/openshift

# Watch rollout
kubectl rollout status deployment/ocp-causa-backend -n $K8S_NAMESPACE
```

**For OpenShift:**
```bash
# Apply kustomization
oc apply -k deployment/kubernetes/overlays/openshift

# Watch rollout
oc rollout status deployment/ocp-causa-backend -n $K8S_NAMESPACE
```

---

## Step 6: Verify Deployment

### Check Pod Status

```bash
# Kubernetes
kubectl get pods -n $K8S_NAMESPACE -l app.kubernetes.io/name=causa-backend

# OpenShift
oc get pods -n $K8S_NAMESPACE -l app.kubernetes.io/name=causa-backend
```

### Verify Secret Mount

```bash
# Kubernetes
kubectl exec -n $K8S_NAMESPACE deployment/ocp-causa-backend -- \
  ls -la /var/secrets/google/

# OpenShift
oc exec -n $K8S_NAMESPACE deployment/ocp-causa-backend -- \
  ls -la /var/secrets/google/
```

Expected output:
```
-rw-r--r-- 1 root root 2345 Jun 05 12:34 key.json
```

### Verify Environment Variable

```bash
# Kubernetes
kubectl exec -n $K8S_NAMESPACE deployment/ocp-causa-backend -- \
  env | grep GOOGLE_APPLICATION_CREDENTIALS

# OpenShift
oc exec -n $K8S_NAMESPACE deployment/ocp-causa-backend -- \
  env | grep GOOGLE_APPLICATION_CREDENTIALS
```

Expected output:
```
GOOGLE_APPLICATION_CREDENTIALS=/var/secrets/google/key.json
```

### Check Application Logs

```bash
# Kubernetes
kubectl logs -f deployment/ocp-causa-backend -n $K8S_NAMESPACE | grep LLM

# OpenShift
oc logs -f deployment/ocp-causa-backend -n $K8S_NAMESPACE | grep LLM
```

Expected logs:
```
INFO  Initializing LLM chat model factory | provider="vertex-ai-anthropic"
INFO  LLM provider detected | provider="vertex-ai-anthropic", authType="ADC"
INFO  Verifying LLM connectivity
INFO  LLM connectivity verified | latencyMs=1234
INFO  LLM ready | provider="vertex-ai-anthropic", model="claude-sonnet-4-6"
```

### Check Health Endpoint

```bash
# Kubernetes
kubectl exec -n $K8S_NAMESPACE deployment/ocp-causa-backend -- \
  curl -s http://localhost:8080/q/health/ready | jq '.checks[] | select(.name=="llm")'

# OpenShift
oc exec -n $K8S_NAMESPACE deployment/ocp-causa-backend -- \
  curl -s http://localhost:8080/q/health/ready | jq '.checks[] | select(.name=="llm")'
```

Expected output:
```json
{
  "name": "llm",
  "status": "UP",
  "data": {
    "status": "READY",
    "message": "LLM provider is connected and responsive"
  }
}
```

---

## Security Best Practices

### Key Rotation (Every 90 Days)

**Why rotate?** Reduces impact of potential key compromise.

**Process:**

1. **Create new key:**
   ```bash
   gcloud iam service-accounts keys create new-key.json \
     --iam-account=$GCP_SA_NAME@$GCP_PROJECT_ID.iam.gserviceaccount.com
   ```

2. **Update Kubernetes secret:**
   ```bash
   kubectl create secret generic gcp-sa-key \
     --from-file=key.json=new-key.json \
     -n $K8S_NAMESPACE \
     --dry-run=client -o yaml | kubectl apply -f -
   ```

3. **Restart deployment:**
   ```bash
   kubectl rollout restart deployment/ocp-causa-backend -n $K8S_NAMESPACE
   ```

4. **Verify new key works** (check logs and health endpoint)

5. **Delete old key:**
   ```bash
   # List keys
   gcloud iam service-accounts keys list \
     --iam-account=$GCP_SA_NAME@$GCP_PROJECT_ID.iam.gserviceaccount.com
   
   # Delete old key by ID
   gcloud iam service-accounts keys delete <OLD_KEY_ID> \
     --iam-account=$GCP_SA_NAME@$GCP_PROJECT_ID.iam.gserviceaccount.com
   ```

6. **Delete local key files securely:**
   ```bash
   shred -u new-key.json causa-backend-sa-key.json
   ```

### Audit Key Usage

```bash
# Check when keys were created
gcloud iam service-accounts keys list \
  --iam-account=$GCP_SA_NAME@$GCP_PROJECT_ID.iam.gserviceaccount.com \
  --format="table(name,validAfterTime,validBeforeTime)"
```

### Monitor Service Account Activity

Use GCP Cloud Logging to track service account usage:

```bash
# View recent API calls
gcloud logging read \
  "protoPayload.authenticationInfo.principalEmail=$GCP_SA_NAME@$GCP_PROJECT_ID.iam.gserviceaccount.com" \
  --limit 50 \
  --project=$GCP_PROJECT_ID
```

### Implement Least Privilege

- ✅ Only grant `roles/aiplatform.user`
- ❌ Never grant `roles/owner` or `roles/editor`
- ✅ Use separate service accounts per environment (dev, staging, prod)
- ✅ Use separate service accounts per service
- ✅ Review permissions quarterly

---

## Alternative Authentication Methods

### Workload Identity (GKE)

**Recommended for GKE clusters** - no key files needed.

1. **Enable Workload Identity on cluster**
2. **Create Kubernetes service account**
3. **Bind to GCP service account**
4. **Annotate pod with service account**

**Benefits:**
- No key files to manage
- Automatic token rotation
- Better security posture

**Setup Guide:** [GKE Workload Identity](https://cloud.google.com/kubernetes-engine/docs/how-to/workload-identity)

### Service Account Impersonation

**For CI/CD pipelines** - developers impersonate service account.

```bash
# Grant user permission to impersonate service account
gcloud iam service-accounts add-iam-policy-binding \
  $GCP_SA_NAME@$GCP_PROJECT_ID.iam.gserviceaccount.com \
  --member="user:developer@example.com" \
  --role="roles/iam.serviceAccountTokenCreator"

# Use impersonation
gcloud auth application-default login --impersonate-service-account=$GCP_SA_NAME@$GCP_PROJECT_ID.iam.gserviceaccount.com
```

---

## Summary Checklist

**Setup:**
- ✅ Create GCP service account
- ✅ Grant `roles/aiplatform.user` IAM role
- ✅ Create and download service account key
- ✅ Store key securely in vault
- ✅ Create Kubernetes secret from key
- ✅ Update deployment to mount secret
- ✅ Set `GOOGLE_APPLICATION_CREDENTIALS` env var
- ✅ Verify pod can read mounted key
- ✅ Verify Vertex AI connectivity
- ✅ Delete local key file

**Ongoing:**
- ✅ Rotate keys every 90 days
- ✅ Monitor service account usage
- ✅ Review IAM permissions quarterly
- ✅ Track costs per service account
- ✅ Audit key creation/deletion
