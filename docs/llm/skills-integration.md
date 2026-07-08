# Skills Integration

Skills extend the LLM with on-demand knowledge loaded at runtime. The LLM receives a catalogue of available skills (name + description) in the system message and calls `activate_skill("skill-name")` to load the full content when needed.

## Configuration

### Global toggle

| Property | Env var | Default | Description |
|---|---|---|---|
| `causa.llm.skills.enabled` | `LLM_SKILLS_ENABLED` | `true` | Enable or disable skills globally |

Set in `deployment/kubernetes/base/configmap.yaml`:
```yaml
LLM_SKILLS_ENABLED: "false"   # disable skills cluster-wide
```

### Per-request override

Pass `enableSkills` in the request body to override the global setting for a single call:
```json
{ "prompt": "...", "enableSkills": false }
```

**Precedence:** per-request `enableSkills` → `LLM_SKILLS_ENABLED` → default `true`.

## Adding a skill

Place a `SKILL.md` under `src/main/resources/skills/<skill-name>/`. It is picked up automatically at startup — no code changes required.

## Provider support

Only `LangChainPromptSender` supports the full tool-call loop (skill catalogue injection + `activate_skill` execution).
`BobShellPromptSender` does not currently support skills.
