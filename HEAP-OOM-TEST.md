# Heap OOM Validation Test - Quick Guide

## OpenShift/OCP (Running App)

### Heap OOM Test:
```bash
# Auto-detect OOMKilled pod
./scripts/test-heap-oom-oc.sh

# Or specify pod
./scripts/test-heap-oom-oc.sh heap-oom heap-oom-7fbffd977f-ccsvl
```

### GC Pause Test:
```bash
# Auto-detect running pod with GC issues
./scripts/test-gc-pause-oc.sh

# Or specify pod
./scripts/test-gc-pause-oc.sh gc-pause auth-cache-6b77786ffd-jqwgn
```

---

## Local Dev (5 Steps)

### 1. Start Application
```bash
./mvnw quarkus:dev
```

### 2. Trigger Validation
```bash
curl -X POST http://localhost:8080/api/v1/alerts \
  -H 'Content-Type: application/json' \
  -d '{
    "namespace": "chaos-test",
    "container": "heap-oom-prom",
    "pod": "heap-oom-prom-75d49588db-tqhrg"
  }'
```

**Copy the diagnostic ID from response** (format: `diag_XXXX`)

### 3. Check Status (wait ~30-60s)
```bash
curl http://localhost:8080/api/v1/diagnostics/<DIAG_ID> | jq .status
```

### 4. Get Results
```bash
curl http://localhost:8080/api/v1/diagnostics/<DIAG_ID> | jq
```

### 5. Verify Validation

**Look for:**
- ✅ `"is_valid": true`
- ✅ `"anomaly_type": "OOM_KILLED"`
- ✅ `"final_verdict": { "status": "SUPPORTED" }`
- ✅ PATH B: `"required_passed": 3` out of 3
- ✅ PATH B: `"total_score": 15+` (threshold: 10)

---

## Changes Tested

### Signal Extraction
- **Exit code 137** → auto-infers `OOMKilled` termination reason
- **"Back-off restarting"** → detects `CrashLoopBackOff`
- **Restart count** → calculates memory pressure duration

### Ruleset (oom-killed.yml)
**Required (weight=1 each):**
- Exit code 137
- OOMKilled reason
- Memory INCREASING

**Supporting (weights 2-5):**
- Heap >90% (5)
- OOM error in logs (4)
- Sustained pressure (4)
- CrashLoopBackOff (3)
- High restarts (3)
- Heap increasing (2)

**Exclusions (negative weights):**
- Deployment rollout (-10)
- Node eviction (-8)
- Manual restart (-5)

### Scoring
```
Score = Required(3) + Supporting(0-21) - Exclusion(0-23)

Verdict:
  ≥10 = SUPPORTED
  ≥5  = PARTIALLY_SUPPORTED
  <5  = UNSUPPORTED
```

---

## Auto Test Script

```bash
./scripts/test-heap-oom-validation.sh
```

---

## Expected Console Output

```
PATH B: Running rule-based validation
  ✅ oom.required.exit_code_137 PASSED
  ✅ oom.required.reason_oomkilled PASSED
  ✅ oom.required.memory_increasing PASSED
  ✅ oom.supporting.heap_exceeded PASSED (weight: 5)
  ✅ oom.supporting.crash_loop PASSED (weight: 3)
  
Total Score: 15
Status: SUPPORTED
Confidence: 0.92

Final Verdict: SUPPORTED (0.88)
  PATH A: 0.85 (40%)
  PATH B: 0.92 (60%)
```

---

## Troubleshooting

| Issue | Fix |
|-------|-----|
| App not running | `./mvnw quarkus:dev` |
| Alert rejected (cooldown) | Wait 15 min or restart app |
| 404 on diagnostic | Wait longer, LLM may be slow |
| UNSUPPORTED verdict | Check logs for failed required rules |
| Missing signals | Verify pod name exists in cluster |

---

**Full Docs:** `docs/testing/heap-oom-validation-guide.md`
