# RCA Generation Module

**Version**: 0.0.1  
**Last Updated**: 2026-06-24

## Purpose

Generate Root Cause Analysis (RCA) for Kubernetes pod issues using LLM-based analysis.

**What it does**:
- Collects diagnostic context from MCP servers (Kubernetes, Kruize, Cryostat)
- Builds provider-specific prompts from YAML templates
- Calls LLM (Vertex AI, Direct Anthropic, Bob/Granite, Ollama) for analysis
- Parses structured RCA with confidence scoring
- Categorizes anomalies: OOM_KILLED, POSSIBLE_OOM_KILLED, POSSIBLE_GC_PAUSE, HEALTHY

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    PRIMARY ADAPTER (Inbound)                    │
│                                                                 │
│   AlertWebhookController                                        │
│   POST /api/v1/webhooks/alerts                                  │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                         CORE DOMAIN                             │
│                                                                 │
│   AlertService                                                  │
│        ↓                                                        │
│   DiagnosticService ──────┐                                     │
│        ↓                  │                                     │
│   buildContextForLLM()    │                                     │
│        ↓                  │                                     │
│   performRootCauseAnalysis()                                    │
│        │                  │                                     │
│        ├─→ RcaPromptBuilder (builds prompt from YAML)          │
│        │        ↓                                               │
│        │   PromptTemplateLoader (caches YAML templates)        │
│        │                                                        │
│        └─→ PromptSender (port interface)                       │
│                 ↓                                               │
│            parseRcaResponse()                                   │
│                 ↓                                               │
│            RootCauseAnalysis (domain model)                     │
└────────────────────────────┬────────────────────────────────────┘
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                   SECONDARY ADAPTERS (Outbound)                 │
│                                                                 │
│   LangChainPromptSender (implements PromptSender)               │
│        ↓                                                        │
│   Vertex AI Anthropic / Direct Anthropic / Bob Shell / Ollama  │
└─────────────────────────────────────────────────────────────────┘
```

**Architecture Pattern**: Hexagonal (Ports & Adapters)

---

## Data Flow

### High-Level Flow

```
1. Alert received via webhook
   ↓
2. DiagnosticService.performRootCauseAnalysis(alert)
   ↓
3. buildContextForLLM(alert)
   └─→ mcpContextCollector.collectContextAsString(alert)
   └─→ Returns diagnostic signals (status, events, logs, metrics, JFR, recommendations)
   ↓
4. RcaPromptBuilder.buildPrompt(alert, context)
   ├─→ determineModelType() → VERTEX_AI_ANTHROPIC / DIRECT_ANTHROPIC / BOB / OLLAMA
   ├─→ PromptTemplateLoader.loadTemplate(modelType)
   └─→ template.render(alertDetails, context) → user prompt
   ↓
5. RcaPromptBuilder.getSystemPrompt()
   └─→ Returns system prompt from YAML template
   ↓
6. Build LLMRequest
   ├─ userPrompt (from step 4)
   ├─ systemPrompt (from step 5)
   ├─ temperature: 0.1 (deterministic)
   └─ maxTokens: 4096
   ↓
7. PromptSender.send(llmRequest)
   └─→ LLM processes prompt
   ↓
8. LLMResponse received
   ├─ responseText (JSON)
   ├─ inputTokens
   ├─ outputTokens
   └─ latencyMs
   ↓
9. parseRcaResponse(responseText)
   ├─→ Strip Markdown code blocks
   ├─→ Parse JSON to RootCauseAnalysis
   └─→ Validate structure
   ↓
10. Return RootCauseAnalysis
    ├─ anomalyType
    ├─ rcaConfidence
    ├─ solutionConfidence
    └─ solutions with probabilities
```

---

## Configuration

### Application Properties

```yaml
# LLM Configuration
causa:
  llm:
    provider: "vertex-ai-anthropic"  # or "anthropic", "bob-shell", "ollama"
    model-name: "claude-sonnet-4-6"
    temperature: 0.1
    max-tokens: 4096
  # RCA Template Configuration
  rca:
    template:
      path: "/prompts/rca-prompt-template.yml"  # default

  # Vertex AI Configuration (if provider = vertex-ai-anthropic)
  vertex:
    project-id: "your-gcp-project"
    location: "us-east5"

  # Direct Anthropic Configuration (if provider = anthropic)
  anthropic:
    api-key: "${ANTHROPIC_API_KEY}"
```

### YAML Template Structure

**File**: `src/main/resources/prompts/rca-prompt-template.yml`

```yaml
# Provider-specific prompts
vertex-ai-anthropic:
  name: "vertex-ai-anthropic-rca"
  version: "1.0"
  description: "RCA for Vertex AI Anthropic"
  system_prompt: "You are an expert RCA engine..."
  user_prompt: |
    # ROOT CAUSE ANALYSIS TASK
    {{context}}

direct-anthropic:
  name: "direct-anthropic-rca"
  version: "1.0"
  system_prompt: "..."
  user_prompt: "..."

bob:
  name: "bob-rca"
  version: "1.0"
  system_prompt: "..."
  user_prompt: "..."

ollama:
  name: "ollama-rca"
  version: "1.0"
  system_prompt: "..."
  user_prompt: "..."
```

---

## Model Type Selection

```java
// RcaPromptBuilder determines model type:
if (modelName.contains("bob") || modelName.contains("granite")) 
    → BOB
else if (provider.equals("vertex-ai-anthropic")) 
    → VERTEX_AI_ANTHROPIC
else if (provider.equals("anthropic")) 
    → DIRECT_ANTHROPIC
else if (provider.equals("ollama")) 
    → OLLAMA
else 
    → VERTEX_AI_ANTHROPIC (default)
```

---

## Future Enhancements

1. **Database Persistence**: Store RCA results in database
2. **RCA Validation**: Validate confidence scores and evidence quality
3. **Async Pipeline**: Move RCA generation to async executor
4. **Multi-Model Comparison**: Generate RCA with multiple models and compare
5. **Custom Templates**: Allow users to upload custom YAML templates

---

**Status**: ✅ **Production Ready**
