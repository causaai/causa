---
name: cryostat-diagnostics
description: Provides Java application diagnostics and profiling using Cryostat MCP server. Analyzes JFR recordings to identify performance issues, memory leaks, thread problems, and resource bottlenecks in containerized Java workloads.
compatibility: Requires Cryostat MCP server connection. Works with Java applications exposing JFR data.
metadata:
  mcp_server: cryostat-mcp-server
  primary_tools: get_event_data, get_gc_analysis, get_memory_analysis, get_thread_analysis, get_cpu_analysis
  use_case: performance-diagnostics, memory-analysis, thread-analysis, profiling
---

# Cryostat Diagnostics Skill

Review [`references.md`](.bob/skills/cryostat-diagnostics/references.md) for comprehensive background on Cryostat concepts, JFR recordings, and detailed explanations.

## Overview

Enables deep diagnostics of Java applications using JDK Flight Recorder (JFR) data through Cryostat MCP server. Provides analysis of CPU, memory, threads, I/O, exceptions, and container metrics.

## When to Use

- Performance degradation or high resource usage
- Memory leaks or OOMKilled events
- Thread contention or deadlocks
- CPU hotspots and method profiling
- I/O bottlenecks or exception patterns

## Available MCP Tools

### 1. get_event_data

Retrieve raw JFR event data for custom analysis.

**Input Schema**:
```json
{
  "targetId": "string (required)",
  "recordingName": "string (required)",
  "eventType": "string (optional)"
}
```

**Attributes**:
- `targetId`: Target Java application identifier
- `recordingName`: JFR recording name
- `eventType`: Specific event type filter (e.g., "jdk.GarbageCollection")

---

### 2. get_gc_analysis

Analyze garbage collection behavior and impact.

**Input Schema**:
```json
{
  "targetId": "string (required)",
  "recordingName": "string (required)"
}
```

**Output**: GC pause times, frequency, heap usage, GC overhead, algorithm type

**Use For**: Memory alerts, OOMKilled events, GC tuning validation

---

### 3. get_memory_analysis

Analyze memory allocation patterns and identify leaks.

**Input Schema**:
```json
{
  "targetId": "string (required)",
  "recordingName": "string (required)"
}
```

**Output**: Heap usage trends, allocation rate, top allocating classes, leak indicators

**Use For**: Memory leaks, growing heap usage, allocation hotspots

---

### 4. get_thread_analysis

Analyze thread behavior, contention, and deadlocks.

**Input Schema**:
```json
{
  "targetId": "string (required)",
  "recordingName": "string (required)"
}
```

**Output**: Thread states, lock contention, deadlocks, CPU time distribution

**Use For**: Application hangs, slowdowns, thread contention, deadlock detection

---

### 5. get_cpu_analysis

Analyze CPU usage patterns and identify hotspots.

**Input Schema**:
```json
{
  "targetId": "string (required)",
  "recordingName": "string (required)"
}
```

**Output**: CPU usage, method profiles, thread CPU time, JIT compilation activity

**Use For**: High CPU alerts, performance bottlenecks, hot method identification

---

### 6. get_io_analysis

Analyze I/O operations and identify bottlenecks.

**Input Schema**:
```json
{
  "targetId": "string (required)",
  "recordingName": "string (required)"
}
```

**Output**: File/network I/O stats, wait times, throughput, blocking operations

**Use For**: Slow response times, I/O bottlenecks, disk/network usage

---

### 7. get_exception_analysis

Analyze exception patterns and frequency.

**Input Schema**:
```json
{
  "targetId": "string (required)",
  "recordingName": "string (required)"
}
```

**Output**: Exception types, frequency, throw locations, common exceptions

**Use For**: Application errors, exception hotspots, error handling analysis

---

### 8. get_container_analysis

Analyze container-level metrics and resource usage.

**Input Schema**:
```json
{
  "targetId": "string (required)",
  "recordingName": "string (required)"
}
```

**Output**: Container CPU/memory usage, throttling, limits, JVM container awareness

**Use For**: Resource configuration validation, CPU throttling, memory limit impacts

---

### 9. list_available_events

List all available JFR event types.

**Input Schema**:
```json
{
  "targetId": "string (required)"
}
```

**Output**: Available event types, categories, descriptions, settings

---

### 10. health_check

Verify Cryostat MCP server connectivity.

**Input Schema**: None required

**Output**: Server status, available targets, version info

---

## Best Practices

1. **Start with health_check** - Verify connectivity before analysis
2. **Use specialized tools** - Prefer specific analysis tools over raw event data
3. **Correlate analyses** - Combine CPU, memory, thread analysis for complete picture
4. **Check container metrics** - Always review for containerized workloads
5. **Longer recordings** - Provide better statistical data

## Limitations

- Requires active JFR recordings on target applications
- Analysis quality depends on recording template and duration
- Container analysis requires container-aware JVM (Java 10+)
- Historical analysis limited to archived recordings

## MCP Protocol Response

All tools return JSON-RPC 2.0 responses:
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": {
    "content": [{"type": "text", "text": "<Analysis results>"}]
  }
}
```

## Troubleshooting

**No recordings**: Verify JFR enabled, check recording creation, validate agent config

**Incomplete results**: Check recording template includes required events, ensure sufficient duration

**Connection failures**: Run `health_check`, verify network connectivity, validate MCP config

---

**Skill Version**: 1.0  
**Last Updated**: 2026-06-19  
**Maintained By**: causa-backend team