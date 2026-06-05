# Kubernetes Secrets

**CRITICAL: Secrets are managed separately from kustomize deployments.**

This secrets directory in the deployment folder contains **TEMPLATE FILES ONLY** with placeholder values. Never commit real secrets to Git.

---

## Usage

### Option 1: For Vertex AI - Automated Script (Recommended)

**For Development Environment:**
```bash
./scripts/llm/setup-vertex-ai.sh --env [local|kind|openshift] --project YOUR_GCP_PROJECT_ID
```


### Option 2: Manual Creation

#### For Anthropic Direct API

```bash
kubectl create secret generic causa-llm-secrets \
  --from-literal=LLM_API_KEY=sk-ant-api03-xxxxxxxx \
  -n diagnostics-tool
```

#### For Vertex AI

Please refer to the following guides:
- **Development/POC:** [Vertex AI Non-Production Guide](../llm/vertex-ai-non-production-guide.md)
- **Production:** [Vertex AI Production Guide](../llm/vertex-ai-production-guide.md)

---

## Template Files

This directory contains a YAML template you can copy and modify:

- `secret.yaml` - Template for LLM API key

**To use the template:**

1. Copy template file to a temporary location
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
