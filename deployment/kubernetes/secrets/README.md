# Kubernetes Secrets

**CRITICAL: Secrets are managed separately from kustomize deployments.**

This directory contains **TEMPLATE FILES ONLY** with placeholder values. Never commit real secrets to Git.

---

## Usage

### Option 1: Automated Script (Recommended)

Use the automated setup script to create secrets:

**For Local (Kind):**
```bash
./scripts/llm/setup-vertex-ai.sh --env local --project YOUR_GCP_PROJECT_ID
```

**For Production (OpenShift):**
```bash
./scripts/llm/setup-vertex-ai.sh --env production --project YOUR_GCP_PROJECT_ID
```

### Option 2: Manual Creation

#### For Anthropic Direct API

```bash
kubectl create secret generic causa-llm-secrets \
  --from-literal=LLM_API_KEY=sk-ant-api03-xxxxxxxx \
  -n diagnostics-tool
```

#### For Vertex AI (Local)

```bash
kubectl create secret generic causa-llm-secrets \
  --from-literal=VERTEX_PROJECT_ID=your-gcp-project-id \
  -n diagnostics-tool
```

#### For Vertex AI (Production)

```bash
# Secret 1: GCP Service Account Key
kubectl create secret generic gcp-sa-key \
  --from-file=key.json=path/to/service-account-key.json \
  -n diagnostics-tool

# Secret 2: Vertex Project ID
kubectl create secret generic causa-llm-secrets \
  --from-literal=VERTEX_PROJECT_ID=your-gcp-project-id \
  -n diagnostics-tool
```

---

## Template Files

This directory contains YAML templates you can copy and modify:

- `anthropic-secret.yaml` - Template for Anthropic API key
- `vertex-ai-secret.yaml` - Template for Vertex AI project ID
- `gcp-sa-key-secret.yaml` - Template for GCP service account key (production only)

**To use templates:**

1. Copy template file
2. Replace placeholder values with real secrets
3. Apply directly with `kubectl apply -f <file>`
4. **DELETE the file with real values** (never commit!)

---

## Best Practices

### ✅ DO
- Use secret management tools (Sealed Secrets, External Secrets Operator, Vault)
- Create secrets manually via `kubectl create secret`
- Use environment-specific secret values
- Rotate secrets regularly
- Delete local copies of secret files after applying

### ❌ DON'T
- Commit real secrets to Git
- Add secret.yaml to kustomization.yaml
- Store secrets in ConfigMaps
- Reuse secrets across environments
- Share secret values in plain text

---

## Production Secret Management

For production, use one of these approaches:

### 1. Sealed Secrets (Recommended for GitOps)

```bash
# Install Sealed Secrets controller
kubectl apply -f https://github.com/bitnami-labs/sealed-secrets/releases/download/v0.18.0/controller.yaml

# Seal a secret
kubeseal --format yaml < secret.yaml > sealed-secret.yaml

# Commit sealed secret to Git (safe!)
git add sealed-secret.yaml
```

### 2. External Secrets Operator

```bash
# Install ESO
helm install external-secrets external-secrets/external-secrets -n external-secrets-system

# Create SecretStore pointing to GCP Secret Manager / AWS Secrets Manager / Vault
# Create ExternalSecret CRD that syncs from external store
```

### 3. HashiCorp Vault

```bash
# Use Vault Agent Injector to inject secrets at runtime
# Secrets never touch disk
```

---

## Verification

After creating secrets:

```bash
# List secrets
kubectl get secrets -n diagnostics-tool

# Verify secret exists (DO NOT decode in production)
kubectl describe secret causa-llm-secrets -n diagnostics-tool

# Check deployment can read secret
kubectl exec -n diagnostics-tool deployment/causa-backend -- \
  env | grep -E 'LLM|VERTEX'
```
