---
name: kubernetes-diagnostics
description: Provides Kubernetes diagnostic context using MCP server. Retrieves pod status, events, and logs for root cause analysis.
compatibility: Requires kubernetes-mcp-server connection with cluster access.
metadata:
  mcp_server: kubernetes-mcp-server
  primary_tools: pods_get, pods_log, events_list
  use_case: kubernetes diagnostics, troubleshooting
---

# Kubernetes Diagnostics Skill

## Overview

Gather Kubernetes diagnostic context for root cause analysis: pod status, events, and logs.

## When to Use

- Analyzing Kubernetes alerts (OOMKilled, CrashLoopBackOff)
- Investigating pod failures or restarts
- Diagnosing container issues
- Performing RCA for workloads

## Available Tools

### 1. pods_get

**Purpose**: Get detailed pod status (returns YAML format)

**Input Schema**:
```json
{
  "type": "object",
  "properties": {
    "name": {
      "type": "string",
      "description": "Pod name"
    },
    "namespace": {
      "type": "string",
      "description": "Kubernetes namespace"
    }
  },
  "required": ["name", "namespace"]
}
```

**Attributes**:
- `name` (required): Pod name (e.g., "causa-backend-7d9f8b6c5-x7k2m")
- `namespace` (required): Kubernetes namespace (e.g., "causa-system")

**Key Output Fields**:
- `status.phase`: Running, Pending, Failed, Succeeded, Unknown
- `status.containerStatuses[].state`: running, waiting, terminated
- `status.containerStatuses[].state.terminated.exitCode`: 137=OOMKilled, 1=Error
- `status.containerStatuses[].restartCount`: Number of restarts
- `status.containerStatuses[].ready`: Container readiness

**Usage**: Always call first to understand current pod state.

### 2. pods_log

**Purpose**: Get container logs (returns plain text)

**Input Schema**:
```json
{
  "type": "object",
  "properties": {
    "name": {
      "type": "string",
      "description": "Pod name"
    },
    "namespace": {
      "type": "string",
      "description": "Kubernetes namespace"
    },
    "container": {
      "type": "string",
      "description": "Container name (optional, uses first container if not specified)"
    },
    "tailLines": {
      "type": "integer",
      "description": "Number of recent log lines to retrieve",
      "default": 25
    }
  },
  "required": ["name", "namespace"]
}
```

**Attributes**:
- `name` (required): Pod name
- `namespace` (required): Kubernetes namespace
- `container` (optional): Specific container name (defaults to first container)
- `tailLines` (optional): Number of log lines (default: 25, recommended max: 100)

**Output**: Last N lines of stdout/stderr logs

**Usage**: Call after pods_get to analyze application errors, exceptions, OOM messages.

### 3. events_list

**Purpose**: Get Kubernetes events (returns table format)

**Input Schema**:
```json
{
  "type": "object",
  "properties": {
    "fieldSelector": {
      "type": "string",
      "description": "Filter events by field (e.g., 'involvedObject.name=pod-name')"
    },
    "namespace": {
      "type": "string",
      "description": "Kubernetes namespace"
    }
  },
  "required": ["fieldSelector", "namespace"]
}
```

**Attributes**:
- `fieldSelector` (required): Filter expression (e.g., "involvedObject.name=pod-name")
- `namespace` (required): Kubernetes namespace

**Key Event Types**:
- **Warning**: OOMKilling, BackOff, Failed, Unhealthy
- **Normal**: Scheduled, Pulling, Started, Created

**Usage**: Correlate cluster-level events with pod status and logs.

## Data Format

**List operations** (events_list, pods_list):
- Returns **table format** (plain text columns)
- Parse columns: TYPE, REASON, MESSAGE, TIMESTAMP

**Get operations** (pods_get, resources_get):
- Returns **YAML format**
- Extract key fields: status.phase, containerStatuses, exitCode

**Log operations** (pods_log):
- Returns **plain text** (one line per log entry)
- Look for: ERROR, FATAL, OutOfMemoryError, exceptions

## Common Patterns

**OOMKilled**:
- pods_get: exitCode=137, reason="OOMKilled"
- events_list: Warning "OOMKilling"
- pods_log: OutOfMemoryError
- **Fix**: Increase memory limit

**CrashLoopBackOff**:
- pods_get: state="waiting", reason="CrashLoopBackOff"
- events_list: Warning "BackOff"
- pods_log: Fatal errors at startup
- **Fix**: Fix application startup issue

## Best Practices

1. **Call pods_get first** to understand current state
2. **Extract only relevant fields** from YAML (don't show full output)
3. **Limit logs to 25-50 lines** for efficiency
4. **Correlate timestamps** across status, events, logs
5. **Focus on errors**: exit codes, Warning events, ERROR logs
6. **Be concise**: Summarize findings, don't dump raw data

## Exit Codes

- **0**: Success
- **1**: General error
- **137**: OOMKilled (SIGKILL)
- **143**: Terminated (SIGTERM)

---

**Version**: 1.0  
**Updated**: 2026-06-18
**Maintained By**: causa-backend team