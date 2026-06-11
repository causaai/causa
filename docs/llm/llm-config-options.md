# LLM Configuration Options

Complete reference for configuring the LLM module in Causa Backend.

---

## Overview

The LLM module uses **environment variables** mapped through `application.yml` to Quarkus `@ConfigMapping`. All configuration is externalized for 12-factor app compliance.

**Configuration mapping chain:**
```
Environment Variables (LLM_*)
    ↓
application.yml (causa.llm.*)
    ↓
LLMConfig.java (@ConfigMapping)
    ↓
Injected into ChatModelFactory & LangChainPromptSender
```

---

## Provider Selection

### `LLM_PROVIDER`

**Description:** The AI provider integration to use via LangChain4J.

**Type:** String (enum-like)

**Default:** `anthropic`

**Valid Values:**
- `anthropic` — Claude via direct Anthropic API (**✅ Implemented**)
- `vertex-ai-anthropic` — Claude via Google Cloud Vertex AI (**✅ Implemented**)
- `ibm-bob` — IBM Bob via OpenAI-compatible API (**🚧 Planned**)
- `ollama` — Ollama local models (**🚧 Planned**)

**Example:**
```bash
export LLM_PROVIDER=vertex-ai-anthropic
```

**In ConfigMap:**
```yaml
LLM_PROVIDER: "anthropic"
```

**Notes:**
- Case-insensitive (converted to lowercase in `ChatModelFactory`)
- Unsupported values throw `LLMException` at startup
- Determines which `ChatModel` implementation is created

---

## Model Configuration

### `LLM_MODEL_NAME`

**Description:** The specific model version optimized for diagnostics.

**Type:** String

**Default:** `claude-sonnet-4-6`

**Note:** The setup script (`scripts/llm/setup-vertex-ai.sh`) uses `claude-sonnet-4-6` as the default model.

**Valid Values (Claude):**
- `claude-sonnet-4-6` — Latest Sonnet 4.6 (recommended)
- `claude-opus-4-6` — Opus 4.6 (highest quality, slower)
- `claude-haiku-4-5` — Haiku 4.5 (fastest, lower cost)

**Example:**
```bash
export LLM_MODEL_NAME=claude-sonnet-4-5-20250514
```

**Notes:**
- Model availability depends on provider (Vertex AI may have different names)
- Invalid model names cause runtime errors (400 Bad Request from API)
- Newer models may require dependency version upgrades

---

## Inference Parameters

### `LLM_TEMPERATURE`

**Description:** Controls the randomness of the output. Set low for deterministic, factual diagnostics.

**Type:** Double (0.0 - 1.0)

**Default:** `0.1`

**Recommended Values:**
- `0.0` — Deterministic (same input = same output)
- `0.1` — Near-deterministic with slight variation (recommended for diagnostics)
- `0.5` — Balanced creativity and consistency
- `1.0` — Maximum randomness (not recommended for diagnostics)

**Example:**
```bash
export LLM_TEMPERATURE=0.1
```

---

### `LLM_MAX_TOKENS`

**Description:** The maximum number of tokens generated in the response.

**Type:** Integer

**Default:** `4096`

**Valid Range:** `1` - `200000` (model-dependent)

**Model Limits (Claude):**
- Sonnet 4.6: 200,000 tokens
- Opus 4.6: 200,000 tokens
- Haiku 4.5: 200,000 tokens

**Example:**
```bash
export LLM_MAX_TOKENS=2048
```

**Notes:**
- Higher values = more detailed responses but slower + more expensive
- Does NOT limit input tokens (controlled separately by context window)
- Responses may be shorter than max_tokens (model stops when complete)

---

### `LLM_TIMEOUT_SECONDS`

**Description:** Network timeout for the LLM API call.

**Type:** Integer

**Default:** `60`

**Recommended Values:**
- `30` — Short responses (< 500 tokens)
- `60` — Standard (1000-2000 tokens)
- `120` — Long responses (4000+ tokens)

**Example:**
```bash
export LLM_TIMEOUT_SECONDS=90
```

**Notes:**
- Timeout includes HTTP connection + response streaming
- Too short = premature failures on slow networks
- Too long = hung requests block worker threads

---

## Authentication (Anthropic Direct API)

### `LLM_API_KEY`

**Description:** (Secret) The authentication key for the LLM provider.

**Type:** String (secret)

**Default:** (empty)

**Required For:** `LLM_PROVIDER=anthropic`

**Format:** Starts with `sk-ant-` (Anthropic)

**Example:**
```bash
export LLM_API_KEY=sk-ant-api03-xxxxxxxxxxxxxxxxxxx
```

**In Kubernetes:**
```yaml
# Store in Secret, NOT ConfigMap
apiVersion: v1
kind: Secret
metadata:
  name: causa-llm-secrets
type: Opaque
stringData:
  LLM_API_KEY: sk-ant-api03-xxxxxxxxxxxxxxxxxxx
```

**Security:**
- ⚠️ **NEVER** commit to Git
- ⚠️ **NEVER** put in ConfigMap (use Secret)
- ✅ Use secret management (Vault, GCP Secret Manager, AWS Secrets Manager)
- ✅ Rotate regularly

---

## Authentication (Vertex AI)

### `VERTEX_PROJECT_ID`

**Description:** Google Cloud project ID with Vertex AI enabled.

**Type:** String

**Default:** (empty)

**Required For:** `LLM_PROVIDER=vertex-ai-anthropic`

**Format:** GCP project ID (lowercase, hyphens, numbers)

**Example:**
```bash
export VERTEX_PROJECT_ID=my-gcp-project-123456
```

**How to find:**
```bash
gcloud projects list
```

**Notes:**
- Project must have Vertex AI API enabled
- Project must have Claude model access granted
- Billing must be enabled on the project

---

### `VERTEX_LOCATION`

**Description:** Google Cloud region for Vertex AI endpoint.

**Type:** String

**Default:** `us-east5`

**Script Default:** `us-east5` (in `scripts/llm/setup-vertex-ai.sh`)

**Valid Values:**
- `us-east5` — US East (South Carolina) ✅ **Recommended, default**
- `us-central1` — US Central (Iowa)
- `europe-west1` — Europe West (Belgium)
- `asia-southeast1` — Asia Southeast (Singapore)

**Example:**
```bash
export VERTEX_LOCATION=us-east5
```

**Notes:**
- ⚠️ **`global` is NOT a valid location for Claude on Vertex AI** (will return 404 errors)
- Regional endpoints required for Claude models
- Choose region closest to your deployment for lower latency
- All regions have same pricing for Claude via Vertex AI

---

### Google Cloud Authentication (Vertex AI)

**Vertex AI uses Google Cloud credentials, NOT `LLM_API_KEY`.**

**Authentication methods (in order of precedence):**

1. **Application Default Credentials (ADC)** — For development (local/KIND/OpenShift)
   ```bash
   gcloud auth application-default login
   gcloud auth application-default set-quota-project <PROJECT_ID>
   ```
   📖 **See:** [Vertex AI Non-Production Guide](vertex-ai-non-production-guide.md) for detailed ADC configuration

2. **Service Account Key File** — For production (OpenShift/Kubernetes)
   ```bash
   export GOOGLE_APPLICATION_CREDENTIALS=/var/secrets/google/key.json
   ```
   📖 **See:** [Vertex AI Production Guide](vertex-ai-production-guide.md) for production deployment (reference only, not yet fully tested)

**Quick Setup:**
- **Automated Script:** Use [setup-vertex-ai.sh](../../scripts/llm/setup-vertex-ai.sh) for development setups (local/kind/openshift)
- **Manual Setup:** Follow the [Vertex AI Non-Production Guide](vertex-ai-non-production-guide.md)

---

## Optional Configuration

### `LLM_BASE_URL`

**Description:** The HTTP/HTTPS target root endpoint. Overrides default cloud endpoints to redirect traffic to local runtimes or proxies.

**Type:** String (URL)

**Default:** (empty — uses provider defaults)

**Use Cases:**
- Proxy/gateway routing (e.g., corporate proxy)
- Local model serving (Ollama, vLLM)
- Custom Anthropic-compatible endpoints

**Example:**
```bash
# Ollama local server
export LLM_BASE_URL=http://localhost:11434

# Corporate proxy
export LLM_BASE_URL=https://llm-proxy.company.com
```

**Notes:**
- Must be a valid HTTP/HTTPS URL
- No trailing slash
- Provider-specific path suffixes appended automatically

---

### `LLM_AUTH_TYPE`

**Description:** The authentication protocol used by the HTTP client.

**Type:** String (enum-like)

**Default:** `API_KEY`

**Valid Values:**
- `API_KEY` — Header-based API key (Anthropic default)
- `BEARER_TOKEN` — OAuth2 Bearer token
- `MTLS` — Mutual TLS (certificate-based)
- `NONE` — No authentication (local/dev)

**Example:**
```bash
export LLM_AUTH_TYPE=API_KEY
```

**Notes:**
- Currently informational only (logged, not enforced)
- Future: custom auth handler selection

---

### `LLM_CUSTOM_HEADERS`

**Description:** A JSON object of injected key-value pairs used for custom gateway routing, tenancy tags, or proxy handshakes.

**Type:** String (JSON object)

**Default:** `{}`

**Example:**
```bash
export LLM_CUSTOM_HEADERS='{"X-Tenant-ID":"diagnostics","X-Request-Source":"causa-backend"}'
```

**In ConfigMap (YAML-escaped):**
```yaml
LLM_CUSTOM_HEADERS: '{"X-Tenant-ID":"diagnostics"}'
```

**Notes:**
- Must be valid JSON
- Keys are case-sensitive HTTP headers
- Sent with every LLM request
- Useful for gateway routing, tracing, multi-tenancy

---

### `LLM_CHAT_MEMORY_SIZE`

**Description:** Number of previous messages retained if conversational follow-ups are enabled.

**Type:** Integer

**Default:** `10`

**Example:**
```bash
export LLM_CHAT_MEMORY_SIZE=20
```

**Notes:**
- Currently unused (single-turn requests only)
- Reserved for future multi-turn conversation support
- Larger values = more context but higher token cost

---

## Kubernetes Deployment Example

### ⚠️ Security: ConfigMap vs Secret

**Rule:** Public config → ConfigMap, Sensitive data → Secret

| Variable | Location | Reason |
|----------|----------|--------|
| `LLM_PROVIDER` | ✅ ConfigMap | Not sensitive |
| `LLM_MODEL_NAME` | ✅ ConfigMap | Not sensitive |
| `LLM_TEMPERATURE` | ✅ ConfigMap | Not sensitive |
| `LLM_MAX_TOKENS` | ✅ ConfigMap | Not sensitive |
| `LLM_TIMEOUT_SECONDS` | ✅ ConfigMap | Not sensitive |
| `VERTEX_LOCATION` | ✅ ConfigMap | Not sensitive |
| `LLM_BASE_URL` | ✅ ConfigMap | Not sensitive |
| `LLM_AUTH_TYPE` | ✅ ConfigMap | Not sensitive |
| `LLM_CUSTOM_HEADERS` | ✅ ConfigMap | Not sensitive |
| **`LLM_API_KEY`** | ⚠️ **Secret** | **Anthropic API key - NEVER in ConfigMap** |
| **`VERTEX_PROJECT_ID`** | ⚠️ **Secret** | **GCP project ID - NEVER in ConfigMap** |


---

## Configuration by Provider

### Anthropic Direct API (✅ Implemented)

**Required:**
- `LLM_PROVIDER=anthropic`
- `LLM_API_KEY=sk-ant-...`

**Optional:**
- `LLM_MODEL_NAME` (default: `claude-sonnet-4-6`)
- `LLM_TEMPERATURE` (default: `0.1`)
- `LLM_MAX_TOKENS` (default: `4096`)
- `LLM_TIMEOUT_SECONDS` (default: `60`)

**Example `.env`:**
```bash
LLM_PROVIDER=anthropic
LLM_API_KEY=sk-ant-api03-xxxxxxxxxxxxxxxx
LLM_MODEL_NAME=claude-sonnet-4-6
LLM_TEMPERATURE=0.1
LLM_MAX_TOKENS=4096
LLM_TIMEOUT_SECONDS=60
```

---

### Vertex AI Anthropic (✅ Implemented)

**Required:**
- `LLM_PROVIDER=vertex-ai-anthropic`
- `VERTEX_PROJECT_ID=my-gcp-project`
- Google Cloud credentials configured (ADC or service account)

**Optional:**
- `VERTEX_LOCATION` (default: `us-east5`)
- `LLM_MODEL_NAME` (default: `claude-sonnet-4-6`)
- `LLM_TEMPERATURE` (default: `0.1`)
- `LLM_MAX_TOKENS` (default: `4096`)
- `LLM_TIMEOUT_SECONDS` (default: `60`)

**Example `.env`:**
```bash
LLM_PROVIDER=vertex-ai-anthropic
VERTEX_PROJECT_ID=my-gcp-project-123456
VERTEX_LOCATION=us-east5
LLM_MODEL_NAME=claude-sonnet-4-6
```

---

### IBM Bob (🚧 Planned)

**Required:**
- `LLM_PROVIDER=ibm-bob`
- `LLM_API_KEY=<bob-api-key>`
- `LLM_BASE_URL=https://base-url/api/v1`

**Optional:**
- `LLM_MODEL_NAME` (TBD)
- Standard inference parameters

**Status:** Not yet implemented. Will use `langchain4j-open-ai` with custom base URL.

---

### Ollama (🚧 Planned)

**Required:**
- `LLM_PROVIDER=ollama`
- `LLM_BASE_URL=http://localhost:11434`

**Optional:**
- `LLM_MODEL_NAME=llama3`
- Standard inference parameters

**Status:** Not yet implemented. Will use `langchain4j-ollama`.


---

## References

- [LangChain4J Configuration](https://docs.langchain4j.dev/)
- [Claude API Documentation](https://docs.anthropic.com/en/api)
- [Google Vertex AI Claude](https://cloud.google.com/vertex-ai/generative-ai/docs/partner-models/use-claude)
- [Quarkus Config Reference](https://quarkus.io/guides/config-reference)
