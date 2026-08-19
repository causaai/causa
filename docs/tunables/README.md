# Tunables — Configuration Reference

List of configurables in Causa Backend, organised by **feature area**.

---

## Sections

| Module | What it covers |
|---|---|
| [mcp.md](mcp.md) | MCP server URLs, timeouts, health paths, platform toggle, adding a new server |
| [alerts.md](alerts.md) | Alert severity filter, cooldown, ignored namespaces |
| [llm.md](llm.md) | Provider, model, API keys, Vertex AI, BOB Shell, prompts, skills |
| [app.md](app.md) | HTTP, database, connection pool, logging, encryption, dev profile |

---

## How the configuration layers work

Every setting is resolved through a **four-layer priority chain**, highest first:

```
┌─────────────────────────────────────────────────────┐
│  1. Config API  (POST /api/v1/configs)              │  ← persisted to DB, live-reload,
│     persisted to PostgreSQL, broadcast via          │     no pod restart needed
│     LISTEN/NOTIFY to all pods                       │
├─────────────────────────────────────────────────────┤
│  2. Environment variables                           │  ← requires pod restart
│     • Kubernetes: ConfigMap  (non-sensitive)        │
│                   Secret     (sensitive)            │
│     • VM:         /opt/causa/.env                   │
├─────────────────────────────────────────────────────┤
│  3. application.yml defaults                        │  ← change needs rebuild
├─────────────────────────────────────────────────────┤
│  4. Hard-coded fallbacks in Java code               │  ← lowest priority
└─────────────────────────────────────────────────────┘
```

Each tunable in the guides below lists **every layer it is available at** so you can
choose the right mechanism for your situation.

