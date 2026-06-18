---
name: kruize-optimization
description: Provides Kubernetes workload resource optimization recommendations using Kruize MCP server. Analyzes CPU and memory usage patterns to suggest performance-optimized configurations for Java containers. ONLY uses getPerformanceOptimizedRecommendations tool.
compatibility: Requires Kruize MCP server connection. Works with Kubernetes workloads exposing Prometheus metrics.
metadata:
  mcp_server: kruize-mcp-server
  primary_tool: getPerformanceOptimizedRecommendations
  allowed_tools: getPerformanceOptimizedRecommendations
  use_case: resource-optimization, performance-analysis
---

# Kruize Optimization Skill

Review [`kruize-reference.md`](.bob/skills/kruize-optimization/kruize-reference.md) for comprehensive background on Kruize concepts, optimization strategies, and detailed explanations.

## ⚠️ IMPORTANT: Tool Usage Restrictions

**ONLY use the `getPerformanceOptimizedRecommendations` tool from Kruize MCP server.**

DO NOT use these tools:
- ❌ getCostOptimizedRecommendations
- ❌ getIdleWorkloads
- ❌ listAllRecommendations
- ❌ listAllExperiments

This skill is configured to use ONLY performance-optimized recommendations.

## Overview

This skill enables gathering resource optimization context from Kruize MCP server. It provides intelligent CPU and memory recommendations based on actual workload usage patterns, helping identify resource-related issues in Java applications.

## When to Use This Skill

Use this skill when:
- Analyzing memory-related alerts (OOMKilled, high memory usage)
- Investigating performance degradation
- Validating resource configurations
- Understanding resource usage patterns for Java workloads

**Note**: This skill uses ONLY performance-optimized recommendations. For cost optimization or idle workload detection, use separate workflows.

## Available MCP Tool

**ONLY this tool should be used by causa-backend:**

### getPerformanceOptimizedRecommendations

**Purpose**: Get performance-optimized CPU/memory recommendations for maximum application responsiveness.

**Input Schema**:
```json
{
  "type": "object",
  "properties": {
    "containerName": {
      "type": "string",
      "description": "Container name"
    },
    "namespace": {
      "type": "string",
      "description": "Namespace"
    }
  },
  "required": ["containerName"]
}
```

**Attributes**:
- `containerName` (required): Name of the container to analyze (e.g., "app-backend")
- `namespace` (optional): Kubernetes namespace where container runs (e.g., "default")

**Output Context**:
- **Recommendation Terms**: short_term (24h), medium_term (7d), long_term (15d)
- **CPU Recommendations**: Based on 98th percentile usage including throttling
- **Memory Recommendations**: Request and limit values (unified)
- **Box Plots Data**: Min, max, median, quartiles for usage visualization
- **Runtime Recommendations**: JVM settings (GCPolicy, MaxRAMPercentage) when available
- **Framework Recommendations**: Quarkus thread pool settings when applicable
- **Notifications**: Warnings for idle containers (code 323001) or missing configurations

**Usage Strategy**:
1. Call after detecting performance issues or high resource usage alerts
2. Use `containerName` from alert context
3. Analyze long_term recommendations for most reliable insights
4. Check for notification code 323001 (idle workload indicator)
5. Compare current vs recommended values to identify misconfigurations

### Tool Output Schema

**Note:** Returns nested JSON array (200+ lines). Key structure below.

**Response Structure:**
```json
[{
  "namespace": "string",
  "container_name": "string",
  "current": {
    "requests": { "cpu": {"amount": float, "format": "cores"}, "memory": {...} }
  },
  "recommendation_terms": {
    "short_term|medium_term|long_term": {
      "duration_in_hours": int,
      "recommendation_engines": {
        "performance": {
          "config": {
            "requests": { "cpu": {...}, "memory": {...} },
            "limits": { "cpu": {...}, "memory": {...} }
          }
        }
      },
      "plots": {
        "plots_data": {
          "timestamp": {
            "cpuUsage": {"min": "str", "median": "str", "max": "str", "q1": "str", "q3": "str"},
            "memoryUsage": {"min": "str", "median": "str", "max": "str"}
          }
        }
      },
      "notifications": { "code": {"type": "info|warning", "message": "str", "code": int} }
    }
  }
}]
```

**Critical Fields:**
- `current.requests`: Current config
- `recommendation_terms.{term}.recommendation_engines.performance.config`: Recommended values
- `plots.plots_data`: Box plot statistics
- `notifications`: Codes 323001 (idle), 120001 (insufficient data)

## Best Practices

1. **Always use long_term recommendations** for production analysis (most reliable)
2. **Check notification codes** for special conditions (missing configs)
3. **Analyze box plots** to understand usage patterns and variability
4. **Use ONLY getPerformanceOptimizedRecommendations** - do not use cost or idle workload tools
5. **Validate runtime recommendations** against application requirements

## Limitations

- Requires 24 hours minimum data for short_term recommendations
- Runtime recommendations require proper metric exposure
- Framework recommendations need specific labels (e.g., Quarkus label)
- Recommendations based on historical patterns, may not predict future spikes


## MCP Protocol Response

All MCP tools return JSON-RPC 2.0 responses:
```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "result": {
    "content": [
      {
        "type": "text",
        "text": "<JSON recommendations array>"
      }
    ]
  }
}
```

The `text` field contains the JSON array shown in the Tool Output Schema section above.

## Troubleshooting

**No recommendations available**:
- Verify experiment exists for the container
- Check if 24 hours of data collected
- Ensure Prometheus metrics are accessible

**Missing runtime recommendations**:
- Verify application exposes runtime metrics
- Check for required labels (e.g., Quarkus label)
- Confirm metric endpoints are accessible
---

**Skill Version**: 1.0  
**Last Updated**: 2026-06-18  
**Maintained By**: causa-backend team