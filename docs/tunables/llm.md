# LLM Tunables

LLM provider settings are stored in the `llm_configs` database table and managed via the Configs API at `PUT /api/v1/configs/llm/{provider}`. There is one row per provider; only one can be active at a time.

Startup defaults (provider, model, credentials) are still seeded from environment variables / `application.yml` on first boot if no DB row exists. Once a row exists in `llm_configs`, the DB value takes precedence and changes applied via the API take effect without a restart.

---

## Switching provider — no restart

```bash
# Switch to direct Anthropic
curl -X PUT http://{{BASE_URL}}/api/v1/configs/llm/ANTHROPIC \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://api.anthropic.com",
    "models": ["claude-sonnet-4-6"],
    "temperature": 0.1,
    "max_tokens": 8192,
    "timeout_ms": 30000,
    "is_active": true,
    "auth_config": {
      "authType": "API_KEY",
      "apiKey": "sk-ant-api03-YOUR_KEY_HERE"
    }
  }'

# Switch to Vertex AI
curl -X PUT http://{{BASE_URL}}/api/v1/configs/llm/VERTEX_AI \
  -H "Content-Type: application/json" \
  -d '{
    "url": "https://us-east5-aiplatform.googleapis.com",
    "models": ["claude-sonnet-4-6"],
    "temperature": 0.1,
    "max_tokens": 8192,
    "timeout_ms": 180000,
    "is_active": true,
    "auth_config": {
      "authType": "SA_JSON_KEY",
      "credentialsJson": "<base64-encoded-sa-json>"
    },
    "additional_config": {
      "project": "my-gcp-project",
      "location": "us-east5"
    }
  }'
```

Setting `is_active: true` on a provider atomically deactivates all other providers. Deleting the currently active provider is rejected with `400`.

---

## Supported providers

| Provider value | Auth type | Sensitive credential field |
|---|---|---|
| `ANTHROPIC` | `API_KEY` | `apiKey` |
| `VERTEX_AI` | `SA_JSON_KEY` | `credentialsJson` |
| `OPENAI` | `API_KEY` | `apiKey` |
| `AZURE_OPENAI` | `API_KEY` | `apiKey` |
| `WATSONX` | `API_KEY` | `apiKey` |

Sensitive fields are AES-256-GCM encrypted in the database and returned as `********` in all GET responses.

---

## LLM config fields

| Field | Type | Description |
|---|---|---|
| `url` | string | LLM API endpoint URL |
| `models` | string[] | Ordered model list (e.g. `["claude-sonnet-4-6"]`) |
| `temperature` | decimal | Sampling temperature (0.0–1.0). Keep 0.0–0.2 for RCA |
| `max_tokens` | integer | Maximum output tokens. Claude supports up to 64k |
| `timeout_ms` | integer | Request timeout in milliseconds. BOB Shell needs ≥ 180 000 |
| `is_active` | boolean | Activates this provider; deactivates all others |
| `auth_config.authType` | string | Auth strategy — `API_KEY`, `SA_JSON_KEY`, or `CUSTOM_HEADERS` |
| `additional_config` | object | Provider-specific fields (e.g. `project`, `location` for Vertex AI; `skillsEnabled`) |

---

## Inference parameters

All tunable live via `PUT /api/v1/configs/llm/{provider}` — no restart needed.

| Field | Default | Guidance |
|---|---|---|
| `temperature` | `0.1` | Keep low (0.0–0.2) for RCA — deterministic output reduces hallucinations |
| `max_tokens` | `8192` | Increase if RCA responses are truncated; Claude supports up to 64k |
| `timeout_ms` | `30000` | BOB Shell needs 180 000; Anthropic direct is typically under 30 000 |

---

## Skills layer

Skills are enabled per-provider via `additional_config.skillsEnabled` in the LLM config row.

```bash
curl -X PUT http://{{BASE_URL}}/api/v1/configs/llm/ANTHROPIC \
  -H "Content-Type: application/json" \
  -d '{
    "additional_config": {
      "skillsEnabled": true,
      "skillsDir": "/opt/causa/skills"
    }
  }'
```

| `additional_config` key | Default | Description |
|---|---|---|
| `skillsEnabled` | `true` | Toggle skills globally for this provider |
| `skillsDir` | _(empty)_ | Filesystem path to external skills directory. Each subdirectory must contain a `SKILL.md`. External skills override bundled ones on name collision |

Bundled skills are loaded from `src/main/resources/skills/` at build time via classpath.

---

## Via ConfigMap — requires pod restart

Edit `deployment/kubernetes/base/configmap.yaml` for startup-time defaults (used before a DB row exists):

```yaml
data:
  LLM_PROVIDER:   "anthropic"
  LLM_MODEL_NAME: "claude-sonnet-4-6"
```

Then apply and restart:

```bash
kubectl apply -k deployment/kubernetes/overlays/openshift/
kubectl rollout restart deployment/causa-backend -n openshift-tuning
```

---

## Credentials and secrets

Sensitive credentials (`apiKey`, `credentialsJson`, `headers`) are encrypted at rest via AES-256-GCM using `CAUSA_ENCRYPTION_KEY` before being written to `llm_configs.auth_config` (JSONB).

### Anthropic API key

Write directly via the Config API (encrypted in DB, no restart needed):

```bash
curl -X PUT http://{{BASE_URL}}/api/v1/configs/llm/ANTHROPIC \
  -H "Content-Type: application/json" \
  -d '{"auth_config": {"authType": "API_KEY", "apiKey": "sk-ant-api03-NEW_KEY"}}'
```

Or set as a Kubernetes Secret for startup seeding:

```bash
kubectl create secret generic causa-llm-secrets \
  --from-literal=LLM_API_KEY=sk-ant-api03-YOUR_KEY \
  -n openshift-tuning
```

### Vertex AI — service-account JSON key

Encode the SA JSON file and pass it as `credentialsJson`:

```bash
base64 -i your-sa-key.json
```

```bash
curl -X PUT http://{{BASE_URL}}/api/v1/configs/llm/VERTEX_AI \
  -H "Content-Type: application/json" \
  -d '{
    "auth_config": {
      "authType": "SA_JSON_KEY",
      "credentialsJson": "<base64-output>"
    },
    "additional_config": {
      "project": "my-gcp-project",
      "location": "us-east5"
    }
  }'
```

Valid Vertex AI regions for Claude (as of 2025):

| Region | `location` value |
|---|---|
| US East | `us-east5` |
| US Central | `us-central1` |
| Europe West | `europe-west1` |
| Asia Southeast | `asia-southeast1` |

> `global` is **not** a valid location for Claude on Vertex AI.

---

## Prompt templates

RCA prompts live in `src/main/resources/prompts/rca-prompt-template.yml`. Each provider block has a `system_prompt` and a `user_prompt`. The `{{context}}` placeholder is replaced at runtime with the assembled diagnostic context.

| Provider | YAML key used |
|---|---|
| `VERTEX_AI` | `vertex-ai-anthropic` |
| `ANTHROPIC` | `direct-anthropic` |

Prompts are bundled into the JAR — a rebuild and redeploy is required to change them:

```bash
./mvnw package -DskipTests
kubectl rollout restart deployment/causa-backend -n openshift-tuning
```
