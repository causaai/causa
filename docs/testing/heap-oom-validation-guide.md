# Heap OOM Validation Pipeline Testing Guide

This guide provides step-by-step instructions for triggering and testing the heap OOM validation pipeline with the recent signal extraction and scoring improvements.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [Understanding the Changes](#understanding-the-changes)
3. [Setup Instructions](#setup-instructions)
4. [Triggering Heap OOM Validation](#triggering-heap-oom-validation)
5. [Expected Results](#expected-results)
6. [Verification Steps](#verification-steps)
7. [Troubleshooting](#troubleshooting)

---

## Prerequisites

### Required Software

- **Java 25**
- **Maven 3.9.x** (wrapper included: `./mvnw`)
- **PostgreSQL** database running
- **curl** or **httpie** for API testing

### Optional

- **Docker/Podman** for containerized testing
- **kubectl** if testing against Kubernetes cluster
- **jq** for JSON formatting

---

## Understanding the Changes

### Recent Improvements

The validation pipeline has been enhanced with:

1. **Smarter Signal Extraction** (`DiagnosticContextSignalExtractor.java`):
   - Infers `OOMKilled` termination reason from exit code 137 when explicit reason is missing
   - Better detection of `CrashLoopBackOff` state from "Back-off restarting" messages
   - Improved memory pressure duration calculation from restart counts

2. **Updated Ruleset** (`oom-killed.yml`):
   - Removed ambiguous rules (`full_gc_frequent`, `kruize_memory_recommendation`)
   - Kept high-confidence supporting rules:
     - `heap_exceeded` (weight: 5)
     - `oom_error_in_logs` (weight: 4)
     - `sustained_memory_pressure` (weight: 4)
     - `crash_loop` (weight: 3)
     - `high_restart_count` (weight: 3)
     - `heap_trend_increasing` (weight: 2)

3. **Clearer RCA Classification** (`rca-prompt-template.yml`):
   - **OOM_KILLED**: Requires exit code 137 + OOMKilled reason + restart evidence
   - **POSSIBLE_OOM_KILLED**: `java.lang.OutOfMemoryError` in logs (not GC allocation failures)
   - **POSSIBLE_GC_PAUSE**: Full GC events, GC pauses >30ms, high heap in running pod

### Validation Flow

```
Alert Webhook
    ↓
Trigger Diagnostics
    ↓
Collect MCP Context (K8s events, logs, metrics)
    ↓
LLM Generates RCA + Classifies Anomaly
    ↓
┌─────────────────┐
│ DUAL VALIDATION │
└─────────────────┘
         ↓
    ┌────┴────┐
    ↓         ↓
PATH A    PATH B
(LLM)     (Rules)
    ↓         ↓
    └────┬────┘
         ↓
  Aggregate Results
         ↓
  Final Verdict
```

---

## Setup Instructions

### 1. Start PostgreSQL Database

```bash
# Using Docker
docker run -d \
  --name causa-postgres \
  -e POSTGRES_USER=causa \
  -e POSTGRES_PASSWORD=causa \
  -e POSTGRES_DB=causa_db \
  -p 5432:5432 \
  postgres:15

# Or use existing database and update application.yml
```

### 2. Configure LLM Provider

Create or update `.env` file:

```bash
# For Anthropic Claude (Direct API)
LANGCHAIN4J_ANTHROPIC_API_KEY=your-api-key-here
LLM_PROVIDER=anthropic
LLM_MODEL_NAME=claude-3-5-sonnet-20241022

# OR for Vertex AI Claude
LLM_PROVIDER=vertex-ai-anthropic
VERTEX_LOCATION=us-east5
VERTEX_PROJECT_ID=your-gcp-project
# Requires: gcloud auth application-default login
```

### 3. Build the Application

```bash
# Clean build with tests
./mvnw clean package

# Or skip tests for faster build
./mvnw clean package -DskipTests
```

### 4. Start the Application

```bash
# Development mode (hot reload enabled)
./mvnw quarkus:dev

# Or run the built JAR
java -jar target/quarkus-app/quarkus-run.jar
```

### 5. Verify Application is Running

```bash
# Check health
curl http://localhost:8080/q/health

# Expected output:
# {"status": "UP", "checks": [...]}
```

---

## Triggering Heap OOM Validation

### Method 1: Synthetic Alert (Manual Trigger)

Use this method to test validation against a specific pod that has experienced OOM issues.

**Step 1:** Identify the target pod (replace with your actual pod name):

```bash
# Example: Pod that was OOMKilled
NAMESPACE="chaos-test"
POD_NAME="heap-oom-prom-75d49588db-tqhrg"
CONTAINER="heap-oom-prom"
```

**Step 2:** Trigger diagnostic analysis:

```bash
curl -X POST http://localhost:8080/api/v1/alerts \
  -H 'Content-Type: application/json' \
  -d "{
    \"namespace\": \"${NAMESPACE}\",
    \"container\": \"${CONTAINER}\",
    \"pod\": \"${POD_NAME}\"
  }"
```

**Step 3:** Capture the diagnostic ID from response:

```json
{
  "status": "accepted",
  "message": "All 1 alerts accepted and diagnostics initiated",
  "totalReceived": 1,
  "totalAccepted": 1,
  "totalRejected": 0,
  "accepted": {
    "alrt_wXhKJWpfPoJTWMZN": "diag_6YuCDWBMfwzWoMhY"  // ← This is your diagnostic ID
  },
  "rejected": {},
  "timestamp": "2026-08-28T12:00:00.000000Z"
}
```

### Method 2: Alertmanager Webhook (Production-like)

Simulate a real Prometheus Alertmanager webhook:

```bash
curl -X POST http://localhost:8080/api/v1/webhooks/alerts \
  -H 'Content-Type: application/json' \
  -d '{
  "receiver": "Critical",
  "status": "firing",
  "alerts": [
    {
      "status": "firing",
      "labels": {
        "alertname": "causa-high-memory",
        "app": "heap-oom-app",
        "container": "heap-oom-prom",
        "namespace": "chaos-test",
        "pod": "heap-oom-prom-75d49588db-tqhrg",
        "severity": "critical"
      },
      "annotations": {
        "pod_name": "heap-oom-prom-75d49588db-tqhrg",
        "container_name": "heap-oom-prom",
        "namespace": "chaos-test",
        "workload_name": "heap-oom-prom",
        "description": "Pod currently using more memory than threshold.",
        "summary": "Container is using more than 90% of its memory limit."
      },
      "startsAt": "2026-08-28T10:00:00.000Z",
      "endsAt": "0001-01-01T00:00:00Z",
      "fingerprint": "heap-oom-test-001"
    }
  ],
  "version": "4"
}'
```

---

## Expected Results

### Console Output (PATH A + PATH B Validation)

When validation runs, you should see logs like:

```
================================================================================
🔍 DUAL VALIDATION PIPELINE STARTED
================================================================================
Issue Title: OOMKilled - heap-oom-prom
Anomaly Type: OOM_KILLED
Minimum Confidence: 0.5

PATH A: Starting assertion-based validation
✅ Assertions extracted: 8 (using 8)

────────────────────────────────────────────────────────────────────────────────
Extracted Assertions (Claims to Validate)
────────────────────────────────────────────────────────────────────────────────
  [1] ROOT_CAUSE: Container terminated with exit code 137 | Source: ROOT_CAUSE
  [2] ROOT_CAUSE: Memory limit too low for workload | Source: ROOT_CAUSE
  [3] SYMPTOM: Heap usage at 95% before termination | Source: SYMPTOMS
  [4] SUPPORTING_EVIDENCE: CrashLoopBackOff detected | Source: SUPPORTING_EVIDENCE
  ...

────────────────────────────────────────────────────────────────────────────────
PATH A: Assertion Validation Results
────────────────────────────────────────────────────────────────────────────────
  [1] ✅ Container terminated with exit code 137 | Status: SUPPORTED, Confidence: 0.95, Evidence: 2 supporting
  [2] ✅ Memory limit too low for workload | Status: SUPPORTED, Confidence: 0.85, Evidence: 3 supporting
  [3] ✅ Heap usage at 95% before termination | Status: SUPPORTED, Confidence: 0.90, Evidence: 2 supporting
  [4] ✅ CrashLoopBackOff detected | Status: SUPPORTED, Confidence: 1.00, Evidence: 1 supporting
  ...

================================================================================
✅ PATH A COMPLETED
================================================================================
Assertion Results Count: 8
PATH A Results: ✅ Supported=6, 🟡 Partial=1, ❌ Unsupported=0, ❓ Unknown=1

PATH B: Starting rule-based hypothesis validation
PATH B: Running with ruleset: rulesets/oom-killed.yml

Signal Extraction:
  - CONTAINER_STATUS: exitCode = 137
  - CONTAINER_STATUS: terminationReason = OOMKilled
  - METRIC: memory.utilization.trend = INCREASING
  - METRIC: heap.usage = 0.95
  - POD_STATUS: podState = CrashLoopBackOff

Rule Evaluation:
  ✅ oom.required.exit_code_137 PASSED (weight: 1)
  ✅ oom.required.reason_oomkilled PASSED (weight: 1)
  ✅ oom.required.memory_increasing PASSED (weight: 1)
  ✅ oom.supporting.heap_exceeded PASSED (weight: 5)
  ✅ oom.supporting.crash_loop PASSED (weight: 3)
  ✅ oom.supporting.sustained_memory_pressure PASSED (weight: 4)
  ❌ oom.exclusion.deployment_rollout NOT MATCHED

================================================================================
✅ PATH B COMPLETED
================================================================================
Hypothesis: OOM_KILLED
Status: SUPPORTED
Confidence: 0.92
Total Score: 17
Required Rules Passed: 3/3
Supporting Rules Matched: 3

🎯 DUAL VALIDATION AGGREGATION COMPLETED
================================================================================
Final Status: SUPPORTED
Final Confidence: 0.88
PATH A Confidence: 0.85
PATH B Confidence: 0.92
Strategy: WEIGHTED_AVERAGE (PATH A: 40%, PATH B: 60%)
================================================================================
```

### API Response

Retrieve the diagnostic result:

```bash
# Replace with your diagnostic ID
DIAG_ID="diag_6YuCDWBMfwzWoMhY"
curl http://localhost:8080/api/v1/diagnostics/${DIAG_ID} | jq
```

Expected response structure:

```json
{
  "id": "diag_6YuCDWBMfwzWoMhY",
  "alert_id": "alrt_wXhKJWpfPoJTWMZN",
  "status": "COMPLETED",
  "rca": {
    "issue_title": "OOMKilled - heap-oom-prom",
    "anomaly_type": "OOM_KILLED",
    "root_cause": "Container terminated with exit code 137 due to memory limit exceeded",
    "symptoms": [...],
    "supporting_evidence": [...],
    "recommendations": [...]
  },
  "validation": {
    "is_valid": true,
    "overall_confidence": 0.88,
    "dual_validation": {
      "final_verdict": {
        "status": "SUPPORTED",
        "confidence": 0.88,
        "strategy": "WEIGHTED_AVERAGE"
      },
      "assertion_based_verdict": {
        "status": "SUPPORTED",
        "confidence": 0.85,
        "total": 8,
        "supported": 6,
        "partially_supported": 1,
        "unsupported": 0,
        "unknown": 1
      },
      "rule_based_verdict": {
        "status": "SUPPORTED",
        "confidence": 0.92,
        "total_score": 17,
        "max_possible_score": 24,
        "required_passed": 3,
        "required_total": 3,
        "supporting_matched": 3,
        "exclusion_matched": 0
      }
    }
  },
  "generated_at": "2026-08-28T12:00:15.123456Z"
}
```

---

## Verification Steps

### 1. Check Signal Extraction

Look for logs showing signal extraction:

```bash
# In the application logs, find:
📋 FULL MCP DIAGNOSTIC CONTEXT (for assertion validation)
────────────────────────────────────────────────────────────────────────────────
[MCP context showing K8s events, pod status, metrics, logs]
────────────────────────────────────────────────────────────────────────────────

Signal Extraction Results:
  - CONTAINER_STATUS: exitCode = 137
  - CONTAINER_STATUS: terminationReason = OOMKilled (source: inferred_from_exit_code_137)
  - METRIC: memory.utilization.trend = INCREASING
  ...
```

### 2. Verify Required Rules Pass

All 3 required rules must pass for OOM_KILLED:

- ✅ `oom.required.exit_code_137`
- ✅ `oom.required.reason_oomkilled`
- ✅ `oom.required.memory_increasing`

If any fail → Status becomes `UNSUPPORTED`

### 3. Check Supporting Evidence

Supporting rules that should match for a genuine OOM:

- ✅ `oom.supporting.heap_exceeded` (heap >90%) → +5 points
- ✅ `oom.supporting.crash_loop` (CrashLoopBackOff) → +3 points
- ✅ `oom.supporting.sustained_memory_pressure` (>600s) → +4 points
- ✅ `oom.supporting.oom_error_in_logs` (OutOfMemoryError) → +4 points

### 4. Verify Score Calculation

```
Score = (Required passed) + (Supporting matched) - (Exclusion matched)

Example:
  Required: 3 passed × 1 = 3
  Supporting: heap_exceeded(5) + crash_loop(3) + sustained_pressure(4) = 12
  Exclusion: 0 matched
  ──────────────────
  Total Score: 15

Verdict:
  - Score ≥ 10 → SUPPORTED ✅
  - Score ≥ 5  → PARTIALLY_SUPPORTED 🟡
  - Score < 5  → UNSUPPORTED ❌
```

### 5. Check Dual Validation Aggregation

Final verdict combines PATH A (LLM) + PATH B (Rules):

```
Final Confidence = (PATH_A × 0.4) + (PATH_B × 0.6)

Example:
  PATH A: 0.85 confidence
  PATH B: 0.92 confidence
  Final: (0.85 × 0.4) + (0.92 × 0.6) = 0.34 + 0.552 = 0.892 ≈ 0.89
```

---

## Troubleshooting

### Issue: Signal Not Extracted

**Problem:** Required signals (exit code 137, OOMKilled reason) not found

**Solutions:**

1. **Check MCP context has the data:**
   ```bash
   # Look for exit code in K8s events or container status
   kubectl get pod <pod-name> -n <namespace> -o yaml | grep -i "exit\|oom"
   ```

2. **Verify signal extraction patterns:**
   - Exit code pattern: `exitCode:\s*(\d+)` or `Exit Code:\s*(\d+)`
   - OOMKilled keyword: case-insensitive match

3. **Check if pod still exists:**
   ```bash
   kubectl get pod <pod-name> -n <namespace>
   # If pod was deleted, MCP may not find historical data
   ```

### Issue: Validation Status is UNSUPPORTED

**Problem:** Even with OOM evidence, validation fails

**Check:**

1. **All required rules passed?**
   - If any of the 3 required rules fail → instant UNSUPPORTED
   - Check logs for: `Failed required rules: [rule-id]`

2. **Score too low?**
   - Need score ≥ 10 for SUPPORTED
   - Need score ≥ 5 for PARTIALLY_SUPPORTED

3. **Exclusion rules triggered?**
   - `deployment_rollout` (-10 points)
   - `node_eviction` (-8 points)
   - These can reduce score below threshold

### Issue: Missing OOM Error in Logs

**Problem:** `oom.supporting.oom_error_in_logs` not matching

**Check:**

1. **Log retention:**
   ```bash
   kubectl logs <pod-name> -n <namespace> --previous
   # Use --previous to get logs from crashed container
   ```

2. **Search for OOM errors:**
   ```bash
   kubectl logs <pod-name> -n <namespace> --previous | grep -i "outofmemory"
   ```

3. **Not all OOM kills show `OutOfMemoryError`:**
   - Kernel OOM killer acts before JVM throws exception
   - This rule is **supporting**, not required, so validation can still pass

### Issue: LLM Misclassification

**Problem:** LLM classifies as `POSSIBLE_GC_PAUSE` instead of `OOM_KILLED`

**Solutions:**

1. **Check prompt template clarity:**
   - Recent update clarifies that GC "Allocation Failure" ≠ OOM
   - OOM_KILLED requires exit code 137 + OOMKilled event

2. **Verify MCP context includes:**
   - Container termination reason
   - Exit code
   - Restart count

3. **PATH B (rules) should catch this:**
   - Even if LLM misclassifies, rule-based validation validates against OOM ruleset
   - Dual validation provides redundancy

### Issue: Application Won't Start

**Problem:** Quarkus dev mode fails

**Check:**

1. **Database connection:**
   ```bash
   # Check PostgreSQL is running
   docker ps | grep postgres
   
   # Test connection
   psql -h localhost -U causa -d causa_db
   ```

2. **Port conflict:**
   ```bash
   # Check if port 8080 is already in use
   lsof -i :8080
   
   # Kill existing process or use different port
   QUARKUS_HTTP_PORT=8081 ./mvnw quarkus:dev
   ```

3. **LLM credentials:**
   ```bash
   # Verify API key is set
   echo $LANGCHAIN4J_ANTHROPIC_API_KEY
   
   # Or check .env file exists
   cat .env | grep LANGCHAIN4J_ANTHROPIC_API_KEY
   ```

---

## Advanced Testing Scenarios

### Test Case 1: True OOM Kill

**Scenario:** Container killed by kernel OOM killer

**Expected Signals:**
- ✅ Exit code 137
- ✅ Termination reason: OOMKilled
- ✅ Memory trend: INCREASING
- ✅ Heap usage: >90%
- ✅ CrashLoopBackOff: Yes

**Expected Result:** `SUPPORTED` with confidence >0.8

### Test Case 2: Deployment Rollout (False Positive)

**Scenario:** Pod terminated during deployment, not OOM

**Expected Signals:**
- ✅ Exit code 137 (SIGTERM escalated to SIGKILL)
- ❌ Termination reason: Pod deleted (not OOMKilled)
- ❌ Memory trend: STABLE
- ✅ Deployment rollout detected in events

**Expected Result:** `UNSUPPORTED` due to exclusion rule or failed required rules

### Test Case 3: GC Pause (Not OOM)

**Scenario:** High heap usage in running pod, frequent Full GC

**Expected Signals:**
- ❌ Exit code: 0 (still running)
- ❌ Termination reason: None
- ⚠️ Heap usage: 95%
- ✅ Full GC events: Frequent
- ✅ GC pauses: >100ms

**Expected Result:** Classified as `POSSIBLE_GC_PAUSE`, not `OOM_KILLED`

---

## Quick Reference Commands

```bash
# Start application
./mvnw quarkus:dev

# Trigger synthetic alert
curl -X POST http://localhost:8080/api/v1/alerts \
  -H 'Content-Type: application/json' \
  -d '{"namespace":"chaos-test","container":"heap-oom-prom","pod":"heap-oom-prom-75d49588db-tqhrg"}'

# Get diagnostic result
curl http://localhost:8080/api/v1/diagnostics/<diagnostic-id> | jq

# List all diagnostics
curl http://localhost:8080/api/v1/diagnostics | jq

# Check application health
curl http://localhost:8080/q/health

# View application logs (filter for validation)
tail -f target/quarkus.log | grep -i "validation\|signal\|rule"
```

---

## Next Steps

After successful validation testing:

1. **Test with real Prometheus alerts** in a staging environment
2. **Monitor false positive/negative rates** over time
3. **Tune scoring thresholds** if needed (in `oom-killed.yml`)
4. **Add custom supporting rules** for your environment
5. **Integrate with notification channels** (Slack, PagerDuty, etc.)

---

**Questions or Issues?** Open an issue at https://github.com/causaai/causa-backend/issues
