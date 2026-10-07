---
name: liberty-app
description: Provides diagnostic guidance for Open Liberty or WebSphere Liberty applications using JVM logs, Liberty trace.log, pod status, logs, and Prometheus metrics.
compatibility: Requires Causa diagnostic context collected from Kubernetes/OpenShift pods running Liberty applications.
metadata:
  runtime: Open Liberty / WebSphere Liberty
  primary_signals: verbosegc, javacore, trace.log, pod logs, prometheus metrics
  use_case: liberty application diagnostics, memory analysis, connection pool troubleshooting
  version: 1.0
---

# Liberty App Skill

## Overview

Analyze Open Liberty or WebSphere Liberty application incidents using the diagnostic context already collected by Causa. Focus on Liberty-specific evidence from JVM logs, application logs, thread dumps, trace output, pod status, and Prometheus metrics.

## When to Use

- Investigating memory pressure or heap exhaustion in Liberty pods
- Diagnosing connection pool exhaustion or JDBC wait timeouts
- Analyzing Liberty applications running on Semeru/OpenJ9
- Interpreting `trace.log`, `verbosegc`, `javacore`, and pod logs together
- Correlating Liberty runtime behavior with Kubernetes resource limits

## Available Diagnostic Signals

### 1. Verbose GC Log

**Source**: `verbosegc*.log`

Use to identify:
- GC frequency increases
- Heap occupancy after collection
- GC pause duration spikes
- JVM heap sizing relative to container memory
- OpenJ9 GC policy such as `gencon`

### 2. Javacore Dump

**Source**: `javacore*.txt`

Use to identify:
- Threads blocked on JDBC or connection pool access
- Lock contention and deadlock indicators
- Native memory or JVM state snapshots
- Repeated thread states showing request starvation

### 3. Liberty Trace Log

**Source**: `trace.log`

Use to identify:
- `WAS.j2c` connection pool wait and timeout behavior
- JDBC acquisition failures
- Transaction and datasource errors
- Repeated retries or backend dependency failures

### 4. Pod and Application Logs

**Source**: Kubernetes pod logs and status

Use to identify:
- Liberty startup failures
- `OutOfMemoryError`, `FFDC`, and unhandled exceptions
- Restart patterns, crash loops, and OOM kills
- Timing correlation with trace and GC events

### 5. Prometheus Metrics

Use to identify:
- High heap or non-heap utilization
- CPU saturation and throttling
- Resource pressure preceding restarts
- Liberty or JVM memory trends correlated with alerts

## Common Patterns

**Heap Exhaustion**:
- `verbosegc` shows frequent collections with low memory reclaimed
- Pod logs show `OutOfMemoryError`
- Pod status may show restart count growth or OOMKilled
- **Likely action**: increase heap or memory limits and investigate leak sources

**Connection Pool Exhaustion**:
- `trace.log` contains `WAS.j2c` wait or timeout entries
- `javacore` shows many threads blocked waiting for DB connections
- Application logs show datasource or transaction timeout errors
- **Likely action**: tune pool size, reduce DB latency, or fix connection leaks

**Liberty Startup Failure**:
- Pod logs show server startup exception or application deployment failure
- Pod status shows CrashLoopBackOff
- Metrics may remain low because app never reaches steady state
- **Likely action**: fix startup config, datasource config, or missing dependency

## Best Practices

1. Start with the alert symptom and confirm it in pod status or logs
2. Correlate timestamps across `verbosegc`, `trace.log`, `javacore`, and metrics
3. Prefer specific evidence over generic JVM conclusions
4. Distinguish container memory limit issues from Java heap sizing issues
5. For connection issues, look for both Liberty trace evidence and blocked threads
6. Recommend the smallest operational fix supported by evidence
