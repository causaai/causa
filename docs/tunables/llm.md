# LLM Tunables

All settings that control how Causa Backend talks to the language model: provider selection,
model identity, authentication, inference parameters, prompt templates, and the skills layer.

---

## All LLM tunables

| Tunable | Config API key | Env var | Default | Sensitive |
|---|---|---|---|---|
| Provider | `LLM_PROVIDER` | `LLM_PROVIDER` | _(empty)_ | no |
| Model name | `LLM_MODEL_NAME` | `LLM_MODEL_NAME` | _(empty)_ | no |
| API base URL override | `LLM_BASE_URL` | `LLM_BASE_URL` | _(empty)_ | no |
| Auth type hint | `LLM_AUTH_TYPE` | `LLM_AUTH_TYPE` | _(empty)_ | no |
| Custom HTTP headers | `LLM_CUSTOM_HEADERS` | `LLM_CUSTOM_HEADERS` | `{}` | no |
| Temperature | `LLM_TEMPERATURE` | `LLM_TEMPERATURE` | `0.1` | no |
| Max tokens | `LLM_MAX_TOKENS` | `LLM_MAX_TOKENS` | `8192` | no |
| Request timeout | `LLM_TIMEOUT_SECONDS` | `LLM_TIMEOUT_SECONDS` | `180` | no |
| Chat memory size | `LLM_CHAT_MEMORY_SIZE` | `LLM_CHAT_MEMORY_SIZE` | `10` | no |
| Anthropic API key | `LLM_API_KEY` | `LLM_API_KEY` | _(empty)_ | **yes** |
| Vertex AI project ID | `VERTEX_PROJECT_ID` | `VERTEX_PROJECT_ID` | _(empty)_ | **yes** |
| Vertex AI region | `VERTEX_LOCATION` | `VERTEX_LOCATION` | _(empty)_ | no |
| BOB Shell path | `BOB_SHELL_PATH` | _(set in application.yml)_ | `bob` | no |
| GCP ADC file path | `GOOGLE_APPLICATION_CREDENTIALS` | `GOOGLE_APPLICATION_CREDENTIALS` | _(empty)_ | **yes** |
| Skills enabled | `LLM_SKILLS_ENABLED` | `LLM_SKILLS_ENABLED` | `false` | no |
| External skills directory | `LLM_SKILLS_DIR` | `LLM_SKILLS_DIR` | _(empty)_ | no |

**Sensitive** keys are AES-256-GCM encrypted in the database and masked (`********`) in all
Config API responses.

---

## Supported providers

| `LLM_PROVIDER` value | How it authenticates | Required keys |
|---|---|---|
| `anthropic` | Direct Anthropic API via API key | `LLM_API_KEY` |
| `vertex-ai-anthropic` | Google Cloud Vertex AI via ADC | `VERTEX_PROJECT_ID` + ADC credentials file |

---

## Switching provider

### Via Config API — no restart

```bash
# Switch to direct Anthropic
curl -X POST http://{{BASE_URL}}/api/v1/configs \
  -H "Content-Type: application/json" \
  -d '{
    "configs": {
      "LLM_PROVIDER":   "anthropic",
      "LLM_MODEL_NAME": "claude-sonnet-4-6",
      "LLM_API_KEY":    "sk-ant-api03-YOUR_KEY_HERE"
    }
  }'

# Switch to Vertex AI Anthropic
curl -X POST http://{{BASE_URL}}/api/v1/configs \
  -H "Content-Type: application/json" \
  -d '{
    "configs": {
      "LLM_PROVIDER":      "vertex-ai-anthropic",
      "LLM_MODEL_NAME":    "claude-sonnet-4-6",
      "VERTEX_PROJECT_ID": "my-gcp-project",
      "VERTEX_LOCATION":   "us-east5"
    }
  }'
```

### Via ConfigMap — requires pod restart

Edit [`deployment/kubernetes/base/configmap.yaml`](../../deployment/kubernetes/base/configmap.yaml):

```yaml
data:
  LLM_PROVIDER:   "anthropic"
  LLM_MODEL_NAME: "claude-sonnet-4-6"
```

Then apply secrets for the chosen provider (see **Credentials** section below) and restart:

```bash
kubectl apply -k deployment/kubernetes/overlays/openshift/
kubectl rollout restart deployment/causa-backend -n openshift-tuning
```

---

## Credentials and secrets

### Anthropic API key

Kubernetes — create the secret:

```bash
kubectl create secret generic causa-llm-secrets \
  --from-literal=LLM_API_KEY=sk-ant-api03-YOUR_KEY \
  -n openshift-tuning
```

Or update live via Config API (encrypted in DB, no restart needed):

```bash
curl -X POST http://{{BASE_URL}}/api/v1/configs \
  -H "Content-Type: application/json" \
  -d '{"configs": {"LLM_API_KEY": "sk-ant-api03-NEW_KEY"}}'
```

VM — set in `/opt/causa/.env`:

```env
LLM_API_KEY=sk-ant-api03-YOUR_KEY
```

### Vertex AI — GCP ADC credentials

The ADC JSON file is mounted as a volume from a Kubernetes Secret and pointed to by
`GOOGLE_APPLICATION_CREDENTIALS`.

**Recommended — use the setup script:**

```bash
./scripts/llm/setup-vertex-ai.sh --env openshift --project my-gcp-project
```

**Manual:**

```bash
kubectl create secret generic gcp-adc-credentials \
  --from-file=application_default_credentials.json=$HOME/.config/gcloud/application_default_credentials.json \
  -n openshift-tuning

# Apply the volume-mount patch
kubectl apply -f deployment/kubernetes/vertex-ai/deployment-adc-patch.yaml -n openshift-tuning
kubectl rollout restart deployment/causa-backend -n openshift-tuning
```

The patch mounts the file at `/var/secrets/google/` and sets:

```
GOOGLE_APPLICATION_CREDENTIALS=/var/secrets/google/application_default_credentials.json
```

Valid Vertex AI regions for Claude (as of 2025):

| Region | `VERTEX_LOCATION` value |
|---|---|
| US East | `us-east5` |
| US Central | `us-central1` |
| Europe West | `europe-west1` |
| Asia Southeast | `asia-southeast1` |

> `global` is **not** a valid location for Claude on Vertex AI.

---

## Inference parameters

All tunable at runtime via Config API — no restart needed.

| Key | Default | Guidance |
|---|---|---|
| `LLM_TEMPERATURE` | `0.1` | Keep low (0.0–0.2) for RCA — deterministic output reduces hallucinations |
| `LLM_MAX_TOKENS` | `8192` | Increase if RCA responses are truncated; Claude supports up to 64k |
| `LLM_TIMEOUT_SECONDS` | `180` | BOB Shell needs the full 180 s; Anthropic direct is typically under 30 s |
| `LLM_CHAT_MEMORY_SIZE` | `10` | Prior conversation turns retained per session |

```bash
curl -X POST http://{{BASE_URL}}/api/v1/configs \
  -H "Content-Type: application/json" \
  -d '{"configs": {"LLM_TEMPERATURE": "0.0", "LLM_MAX_TOKENS": "16384"}}'
```
---

## Prompt templates

RCA prompts live in:

```
src/main/resources/prompts/rca-prompt-template.yml
```

Each LLM provider block has a `system_prompt` and a `user_prompt`. The `{{context}}`
placeholder in `user_prompt` is replaced at runtime with the assembled diagnostic context.

| `LLM_PROVIDER` value | YAML key used |
|---|---|
| `vertex-ai-anthropic` | `vertex-ai-anthropic` |
| `anthropic` | `direct-anthropic` |


### Editing the system prompt

Controls the model's analytical role and output format. Kept stable for prompt caching.

```yaml
vertex-ai-anthropic:
  system_prompt: |
    You are an expert RCA engine specialising in Kubernetes pod memory issues.
    Always respond in valid JSON.
```

### Deploying prompt changes

Prompts are bundled into the JAR — a rebuild and redeploy is required:

```bash
./mvnw package -DskipTests
kubectl rollout restart deployment/causa-backend -n openshift-tuning
```

Use [build script](../../scripts/development/build_and_push.sh) to build a new container image. 

---

## Skills layer [IN PROGRESS]

Skills let the LLM invoke pre-defined external tools during analysis.

| Key | Default | Description |
|---|---|---|
| `LLM_SKILLS_ENABLED` | `false` | Toggle skills globally on/off |
| `LLM_SKILLS_DIR` | _(empty)_ | Path to external skills directory. Each subdirectory must contain a `SKILL.md`. External skills override bundled ones on name collision |

Bundled skills are loaded from `src/main/resources/skills/` at build time.

```bash
curl -X POST http://{{BASE_URL}}/api/v1/configs \
  -H "Content-Type: application/json" \
  -d '{"configs": {"LLM_SKILLS_ENABLED": "true"}}'
```
