# Causa RCA Skill

Causa ships an AI agent skill — `causa-rca` — that lets developers trigger and query root cause analyses directly from their AI assistant (Bob, Cursor, Claude Desktop, or any MCP-compatible agent), without manually calling the Causa API.

The skill is bundled at `docs/skills/SKILL.md` and is loaded automatically when the agent connects to the [Causa MCP server](https://github.com/causaai/causa-mcp).

---

## What it does

When a developer asks something like *"Why is my application crashing?"* or *"Run RCA on my app"*, the skill:

1. Discovers live pods in the target namespace via Kubernetes MCP tools.
2. Checks whether a recent completed RCA already exists — avoids redundant analyses.
3. Calls `initiate_rca` on the Causa MCP server if a fresh analysis is needed.
4. Polls until the analysis completes, then retrieves and presents the full result.

---

## Prerequisites

| Requirement | Notes |
|---|---|
| [Causa MCP server](https://github.com/causaai/causa-mcp) | Must be registered in the agent's MCP configuration |
| Kubernetes MCP server or `kubectl` access | Required for pod discovery |

---

## MCP tools used

| Tool | Purpose |
|---|---|
| `initiate_rca` | Start a new root cause analysis |
| `get_rca_status` | Poll analysis progress |
| `get_rca_result` | Retrieve completed RCA result |

---

## Skill behaviour reference

| Intent | Trigger phrases | Behaviour |
|---|---|---|
| `QUERY` | "Show me the last RCA", "Any RCA available?" | Returns the most recent completed result; no new analysis started |
| `INVESTIGATE` | "Why is my app failing?", "Check for OOM" | Uses existing recent result if available; starts fresh if stale or absent |
| `FORCE_RUN` | "Run RCA", "Analyze from scratch" | Always starts a new analysis, skips existing result check |

For the full workflow, trigger phrase list, and output format, see [`docs/skills/SKILL.md`](../../docs/skills/SKILL.md).
