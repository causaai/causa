---
name: causa-rca
description: Activate when a developer asks about application health, diagnostics, root cause analysis, existing RCA results, or why their application is failing. Checks for existing diagnostics before starting new ones.
compatibility: Requires the Causa MCP server to be configured in Bob with tools initiate_rca and get_rca_result.
metadata:
  category: diagnostics
  domain: kubernetes, jvm
  mcp_server: causa-mcp-server
  mcp_tools:
    - initiate_rca
    - get_rca_result
---

# Causa RCA Skill

An intelligent RCA assistant that checks for existing diagnostics before starting new analyses. Never show raw JSON unless the developer explicitly asks for it.

---

## Trigger Phrases

Activate this skill when the developer says anything related to RCA or application diagnostics. Classify their intent into one of three categories:

### QUERY — developer wants to see existing results

- "Any RCA available?"
- "Show me the last RCA"
- "Do we already have an RCA?"
- "What did the last analysis find?"
- "Is there any existing analysis?"
- "Show diagnostics"

### INVESTIGATE — developer has a problem and wants answers

- "Why is my application failing?"
- "Why is my application crashing?"
- "What's wrong with my app?"
- "My application looks unhealthy"
- "Check what's wrong with my app"
- "Diagnose my service"
- "Find the root cause"
- "Is there any memory issue?"
- "Check for OOM"
- "Check for CPU issues"
- "Investigate this failure"

### FORCE_RUN — developer explicitly wants a fresh analysis

- "Run RCA"
- "Run Causa RCA"
- "Run RCA again"
- "Analyze my application"
- "Start a new analysis"
- "Analyze from scratch"

---

## Application Identification

Before proceeding with any workflow path, determine the target application:

1. **Check conversation context** — if the developer previously mentioned an app name, namespace, or pod name, reuse it.
2. **Infer from project context** — if the developer is working in a known project (e.g., the `chaos-lab` repository suggests workloads like `heap-oom`, `system-oom`, `perf-impact`), propose it.
3. **Ask if ambiguous** — if multiple applications could match or no context is available, ask:
   *"Which application should I check? Please provide the deployment name, namespace, and pod name."*

Remember the `app_name`, `namespace`, and `pod_name` across the conversation so the developer does not need to repeat them.

---

## Workflow

### Step 1 — Classify Intent

Determine the developer's intent from their message: **QUERY**, **INVESTIGATE**, or **FORCE_RUN** (see Trigger Phrases above).

- If intent is **FORCE_RUN**, skip directly to **Step 3** (Initiate New RCA).
- If intent is **QUERY** or **INVESTIGATE**, proceed to **Step 2**.

---

### Step 2 — Check Existing Diagnostics

Call `get_rca_result` with the container and pod name to list all existing diagnoses for this application:

```
get_rca_result(container="<app_name>", pod_name="<pod_name>")
```

Evaluate the results using this decision matrix:

| Intent | What is found | Action |
|--------|--------------|--------|
| QUERY | COMPLETED RCA exists | Present the most recent COMPLETED result using the Output Format (Step 4). |
| QUERY | Only IN_PROGRESS or PENDING | *"An analysis for `<app>` is already in progress. I'll wait for it to complete."* Then poll using Step 3b. |
| QUERY | Nothing found | *"No existing RCA found for `<app>`. Would you like me to start one?"* Wait for confirmation. |
| INVESTIGATE | Recent COMPLETED RCA exists | Use the existing result to answer the developer's specific question (Step 4). Mention: *"Using the existing RCA for `<app>`."* |
| INVESTIGATE | Stale or outdated COMPLETED RCA | Inform: *"The last RCA may be outdated. Starting a fresh analysis."* Then proceed to Step 3. |
| INVESTIGATE | IN_PROGRESS or PENDING RCA exists | *"An analysis for `<app>` is already running. Waiting for it to complete."* Then poll using Step 3b. Do **not** start a duplicate. |
| INVESTIGATE | Nothing found | Proceed to Step 3 (start new analysis). |

**If the `get_rca_result` listing call fails**, fall back to Step 3 (start new analysis). Mention: *"Could not check existing diagnostics. Starting a new analysis."*

---

### Step 3 — Initiate New RCA

Call `initiate_rca` with the application name, namespace, and pod name:

```
initiate_rca(app_name="<app>", namespace="<namespace>", pod_name="<pod>")
```

**Expected response**: an object containing a unique `diagnostic_id` for this analysis run.

**If the call fails**:
- Server unreachable: *"The Causa RCA service is not available right now. Verify that the Causa MCP server is running and reachable."* Stop.
- Unexpected response: *"RCA could not be initiated — the server returned an unexpected response. Check server logs for details."* Stop.

After a successful initiation, proceed to Step 3b.

---

### Step 3b — Poll for Completion

Immediately print exactly once:

> **RCA analysis in progress...**

Then call `get_rca_result(diagnostic_id="<id>")` repeatedly until a terminal status is reached.

**Polling rules**:
- Wait **5 seconds** between polls.
- Do **not** print any further status messages while polling.
- After **24 attempts** (2 minutes) with no terminal status, stop and report:
  *"RCA is taking longer than expected (id: `<id>`). The job is still running — check back later or ask me again in a few minutes."* Stop.

**Terminal statuses**:

| Status | Action |
|--------|--------|
| `COMPLETED` | Proceed to Step 4 |
| `FAILED` | Report: *"RCA generation failed. [error if present]. Review Causa server logs for details."* Stop. |

---

### Step 4 — Retrieve and Render the Result

If you do not already have the full result, call:

```
get_rca_result(diagnostic_id="<id>")
```

Present the result using the **Output Format** below.

**If the intent was INVESTIGATE**: answer the developer's specific question first, then show the structured output. For example, if they asked *"Is there a memory issue?"*, lead with the memory-related findings before the full breakdown.

**If `root_cause` and `recommendations` are both absent**:
*"RCA completed but the result is incomplete — insufficient diagnostic data may have been available."* Show any partial fields that are present.

---

## Output Format

Use these sections. Omit any section whose data is absent.

### Executive Summary

Two to three sentences: what happened, why, and whether it requires immediate action. Written for a developer who needs to decide in 10 seconds whether to drop everything.

### Impact Assessment

- **Severity**: from the `severity` field
- **Affected workload**: `workload_name` in `namespace`
- **Status**: whether the issue is ongoing or resolved

### Root Cause

One to three sentences drawn from `root_cause` or `issue_summary`.

### Key Evidence

Bullet list of concrete observations from `evidences` and `supporting_logs`.
Example: *"Exit code 137 — container killed by the kernel"*, *"OOMKilling event at 14:32:01"*

### Confidence

`rca_confidence_score` as a percentage, with `confidence_summary` if present.
Example: *"Confidence: 92% — Strong OOM signal from kernel events and memory limit gap."*

### Recommended Fix

For each entry in `recommendations`:

**[solution_type]: [solution_title]**
[solution_description]
> Implementation note: [implementation_notes] *(only if present)*

Order: `Immediate Mitigation` then `Root Cause Fix` then `Validate & Monitor`.

### Suggested Code Changes

Only if `implementation_notes` contains config values, flag names, YAML, or code fragments. Render as a fenced code block with the appropriate language tag.

### Next Steps

Numbered list of immediate actions from the `Immediate Mitigation` recommendation and any `solution_alerts`.

---

## Failure Handling

| Scenario | Response |
|----------|----------|
| MCP server unreachable | *"The Causa RCA service is not available. Verify the MCP server is configured and reachable."* |
| `initiate_rca` error | *"RCA could not be started: [error]. Check that the app name, namespace, and pod name are correct."* |
| Polling timeout (> 2 min) | *"RCA is taking longer than expected (id: `<id>`). Still running — check back later."* |
| Status `FAILED` | *"RCA generation failed. [error if available]. Review Causa server logs for details."* |
| Incomplete result | *"RCA completed but returned an incomplete result. Insufficient diagnostic data may have been available."* Show any partial fields. |
| Unexpected response shape | *"The RCA result has an unexpected format. Here is the raw response:"* then show the raw JSON. |
| Duplicate in-progress analysis | Do not start a new RCA. Inform: *"An analysis for `<app>` is already in progress. Waiting for it to complete."* Then poll the existing one. |
| Stale completed RCA | Inform: *"The last RCA may be outdated. Starting a fresh analysis for current state."* Then proceed with a new RCA. |
| Listing call fails | Fall back to starting a new analysis. Mention: *"Could not check existing diagnostics. Starting a new analysis."* |
| No diagnostics + QUERY intent | *"No existing RCA found for `<app>`. Would you like me to start one?"* Wait for confirmation. |
