# Tunables — Configuration Reference

Runtime and deployment-time settings for Causa Backend, organised by feature area.

---

## Sections

| Module | What it covers |
|---|---|
| [mcp.md](mcp.md) | MCP server URLs, timeouts, health paths, platform toggle, adding a new server |
| [alerts.md](alerts.md) | Alert severity filter, cooldown, ignored namespaces |
| [llm.md](llm.md) | Provider, model, credentials, inference parameters, skills |
| [app.md](app.md) | HTTP, database, connection pool, logging, encryption, dev profile |

---

## How the configuration layers work

Settings are resolved through a **four-layer priority chain**, highest first:

```
┌─────────────────────────────────────────────────────────────────────┐
│  1. Config API  (PUT /api/v1/configs/…)                             │  ← persisted to DB, live-reload,
│     Generic key-value → generic_configs table                       │     no pod restart needed
│     LLM provider      → llm_configs table                           │
│     Observability /   → external_configs table                      │
│     Integrations      broadcast via PG LISTEN/NOTIFY to all pods    │
├─────────────────────────────────────────────────────────────────────┤
│  2. Environment variables                                           │  ← requires pod restart
│     • Kubernetes: ConfigMap  (non-sensitive)                        │
│                   Secret     (sensitive)                            │
│     • VM:         /opt/causa/.env                                   │
├─────────────────────────────────────────────────────────────────────┤
│  3. application.yml defaults                                        │  ← change needs rebuild
├─────────────────────────────────────────────────────────────────────┤
│  4. Hard-coded fallbacks in Java code                               │  ← lowest priority
└─────────────────────────────────────────────────────────────────────┘
```

Each tunable in the guides below lists **every layer it is available at** so you can choose the right mechanism for your situation.

### Config tables

| Table | Managed via | Contents |
|---|---|---|
| `generic_configs` | `PUT /api/v1/configs/generic` | Alert tuning, cluster identity |
| `llm_configs` | `PUT /api/v1/configs/llm/{provider}` | LLM provider settings, credentials, inference params |
| `external_configs` | `PUT /api/v1/configs/observability/{platform}` and `PUT /api/v1/configs/integrations/{platform}` | Observability (Datadog, Instana) and integration (Slack, Jira, GitHub) configs |

All three tables are backed by in-memory caches (`AppConfig`, `LlmConfigCache`, `ExternalConfigCache`). Any write triggers a PostgreSQL `LISTEN/NOTIFY` on `config_cache_channel`, which refreshes the cache on all running pods without a restart.
