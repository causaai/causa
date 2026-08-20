# RCA Validation Results — Two Scenarios

## Scenario 1: Definite OOM (heap-oom)

**Diagnostic ID:** `diag_iib97BcPOWDAI57y`  
**Alert:** `KubePodCrashLooping` (critical)  
**Pod:** `heap-oom-6fb8684c8b-pjlpv` in namespace `heap-oom`  
**Anomaly Type:** `OOM_KILLED`  
**Validation Result:** `SUPPORTED`  
**RCA Confidence:** 0.87  
**Validation Score:** 0.90  
**Avg Assertion Confidence:** 0.86  

### RCA Summary

**Issue Title:** Pod OOMKilled: Unbounded heap accumulation exhausts 256Mi memory limit

**Root Cause:** The AllocatorService within the Quarkus application is executing a request-driven memory allocation routine that allocates ~2MB per request and retains all allocated byte chunks indefinitely ('retainedChunks' growing monotonically). This constitutes an intentional or buggy unbounded memory accumulation — no deallocation or reference release occurs between requests. The JVM max heap is capped at ~208.8Mi by the 256Mi container memory limit, and the continuously growing retained heap exhausts this budget rapidly (approximately 50Mi consumed per ~24 requests in under 2 seconds). Once the JVM heap and off-heap (stack, metaspace, code cache) collectively exceed the 256Mi container limit, the Linux kernel OOM killer issues SIGKILL (exit code 137), terminating the container instantly. The container has been OOMKilled at least twice and is now in a BackOff crash loop because the application restarts and immediately repeats the same allocation pattern.

### MCP Server Data

| MCP Server | Status | Data Retrieved |
|---|---|---|
| **Kubernetes MCP** | Connected | Pod status, pod events, pod logs (current + previous), container details |
| **Kruize MCP** | No Data | Cost recommendations: No Data Available; Performance recommendations: No Data Available |
| **Cryostat MCP** | No Data | GC analysis, Memory analysis, Thread analysis, Exception analysis, Container analysis: all No Data Available |
| **Quarkus MCP** | Not Integrated | Quarkus metrics MCP not yet integrated in causa-backend code |

### List of Assertions (15 total)

#### Extracted Assertions (Pre-validation)

| # | Type | Source | Assertion Text |
|---|---|---|---|
| 1 | TREND | ROOT_CAUSE | The AllocatorService within the Quarkus application is executing a request-driven memory allocation routine that allocates ~2MB per request and retains all allocated byte chunks indefinitely ('retainedChunks' growing monotonically) |
| 2 | OBSERVATION | ROOT_CAUSE | This constitutes an intentional or buggy unbounded memory accumulation — no deallocation or reference release occurs between requests |
| 3 | TREND | ROOT_CAUSE | The JVM max heap is capped at ~208.8Mi by the 256Mi container memory limit, and the continuously growing retained heap exhausts this budget rapidly (approximately 50Mi consumed per ~24 requests in under 2 seconds) |
| 4 | CONFIGURATION | ROOT_CAUSE | Once the JVM heap and off-heap (stack, metaspace, code cache) collectively exceed the 256Mi container limit, the Linux kernel OOM killer issues SIGKILL (exit code 137), terminating the container instantly |
| 5 | CAUSALITY | ROOT_CAUSE | The container has been OOMKilled at least twice and is now in a BackOff crash loop because the application restarts and immediately repeats the same allocation pattern |
| 6 | CONFIGURATION | ISSUE_DESCRIPTION | The application kept consuming more and more memory with each request it handled, retaining all previously allocated data without ever freeing it |
| 7 | TREND | ISSUE_DESCRIPTION | This caused the total memory used to grow steadily until the system had no choice but to kill the program to protect the server |
| 8 | CAUSALITY | ISSUE_DESCRIPTION | This happened at least twice before, and the application is now stuck in a restart loop because the same behavior repeats every time it starts up |
| 9 | OBSERVATION | ISSUE_DESCRIPTION | The service is currently unavailable as a result |
| 10 | OBSERVATION | TECHNICAL_DESCRIPTION | The container heap-oom running image quay.io/causa-ai-hub/quarkus-heap-oom:latest was terminated with exit code 137 (OOMKilled) and has restarted 2 times |
| 11 | CONFIGURATION | TECHNICAL_DESCRIPTION | The pod's memory limit is set to 256Mi with a request of 128Mi |
| 12 | CONFIGURATION | TECHNICAL_DESCRIPTION | Application logs from AllocatorService reveal a deliberate and unbounded heap accumulation pattern: each request allocates approximately 2MB (~2,086,100–2,095,007 bytes) and the allocated chunks are retained ('retainedChunks' counter increments by 2 per request) |
| 13 | OBSERVATION | TECHNICAL_DESCRIPTION | At request 44, remaining heap was 119,415,360 bytes (~113.9Mi) with 120 retained chunks; by request 68, remaining heap had dropped to 68,841,160 bytes (~65.7Mi) with 168 retained chunks — a loss of ~50.6Mi in just 24 requests over ~2 seconds |
| 14 | CONFIGURATION | TECHNICAL_DESCRIPTION | The JVM max heap is reported as 218,955,776 bytes (~208.8Mi), which itself is constrained by the 256Mi container limit |
| 15 | OBSERVATION | TECHNICAL_DESCRIPTION | The 'remaining' field tracks available heap, confirming it trends monotonically toward zero with no GC recovery occurring between allocations |

### List of Findings (Validated Assertions with Evidence)

| # | Status | Confidence | Assertion | Supporting Evidence | Refuting Evidence |
|---|---|---|---|---|---|
| 1 | SUPPORTED | 0.98 | AllocatorService allocates ~2MB per request and retains all chunks indefinitely | 6 | 0 |
| 2 | SUPPORTED | 0.88 | Unbounded memory accumulation — no deallocation occurs between requests | 5 | 0 |
| 3 | PARTIALLY_SUPPORTED | 0.62 | JVM max heap capped at ~208.8Mi, retained heap exhausts budget rapidly (~50Mi per 24 requests) | 6 | 0 |
| 4 | PARTIALLY_SUPPORTED | 0.65 | OOM killer issues SIGKILL (exit code 137) when heap + off-heap exceeds 256Mi | 4 | 0 |
| 5 | SUPPORTED | 0.85 | Container OOMKilled at least twice, now in BackOff crash loop | 5 | 0 |
| 6 | SUPPORTED | 0.92 | Application consumed more memory with each request, retaining all allocated data | 4 | 0 |
| 7 | SUPPORTED | 0.90 | Memory grew steadily until system killed the program | 5 | 0 |
| 8 | SUPPORTED | 0.82 | Happened at least twice, stuck in restart loop | 5 | 0 |
| 9 | PARTIALLY_SUPPORTED | 0.65 | Service is currently unavailable | 5 | 0 |
| 10 | SUPPORTED | 0.97 | Container terminated with exit code 137 (OOMKilled), restarted 2 times | 5 | 0 |
| 11 | SUPPORTED | 0.99 | Pod memory limit is 256Mi with request of 128Mi | 1 | 0 |
| 12 | SUPPORTED | 0.95 | AllocatorService: ~2MB per request, retainedChunks increments by 2 per request | 5 | 0 |
| 13 | SUPPORTED | 0.99 | At req=44 remaining=113.9Mi, at req=68 remaining=65.7Mi — 50.6Mi loss in 24 requests | 4 | 0 |
| 14 | SUPPORTED | 0.85 | JVM max heap = 218,955,776 bytes (~208.8Mi), constrained by 256Mi container limit | 2 | 0 |
| 15 | SUPPORTED | 0.92 | 'remaining' field trends monotonically toward zero with no GC recovery | 5 | 0 |

**Summary:** Supported=12, Partially Supported=3, Unsupported=0, Unknown=0 | Total Evidence: 67 pieces

### Citations/Evidence from RCA

1. POD STATUS: Last Terminated Reason = OOMKilled, Exit Code = 137, Finished At = 2026-08-25T05:06:46Z
2. POD STATUS: Restart Count = 2, State = Terminated
3. POD STATUS: Memory Limit = 256Mi, Memory Request = 128Mi
4. POD EVENTS: BackOff at 2026-08-25 05:11:47 UTC — 'Back-off restarting failed container heap-oom'
5. POD LOGS: JVM max heap = 218,955,776 bytes (~208.8Mi)
6. POD LOGS: At req=44, remaining=119,415,360B (~113.9Mi), retainedChunks=120; at req=68, remaining=68,841,160B (~65.7Mi), retainedChunks=168
7. POD LOGS: Each allocation ~2MB per request, retainedChunks increments by +2 per request
8. POD LOGS: 'left' counter decreases per request (57→33 over req 44–68)
9. JFR Analysis: No Data Available
10. Kruize Recommendations: No Data Available

### Supporting Logs

```
[request-mode] req=44 left=57 alloc=2095007B remaining=119415360B max=218955776B retainedChunks=120
[request-mode] req=45 left=56 alloc=2095006B remaining=117320320B max=218955776B retainedChunks=122
[request-mode] req=67 left=34 alloc=2086097B remaining=70927296B max=218955776B retainedChunks=166
[request-mode] req=68 left=33 alloc=2086096B remaining=68841160B max=218955776B retainedChunks=168
```

### Rules (PATH B)

**Status:** Skipped — hypothesis validator not available  
PATH B (rule-based validation) was not executed because the hypothesis validator component is not available in this build. Only PATH A (LLM assertion-based) validation ran.

### Recommendations

| # | Type | Title | Confidence |
|---|---|---|---|
| 1 | Immediate Mitigation | Increase container memory limit to 512Mi | 0.55 |
| 2 | Validate & Monitor | Monitor heap consumption trend and confirm OOMKill cessation | 0.85 |
| 3 | Root Cause Fix | Fix AllocatorService to release retained memory chunks after each request | 0.88 |

---

## Scenario 2: GC Pause (perf-impact)

**Diagnostic ID:** `diag_c6cnbXGF1TPGEL0v`  
**Alert:** `KubePodHighGCPause` (critical)  
**Pod:** `auth-cache-7f45b6b8f9-fj5qw` in namespace `gc-pause`  
**Anomaly Type:** `POSSIBLE_GC_PAUSE`  
**Validation Result:** `PARTIALLY_SUPPORTED`  
**RCA Confidence:** 0.60  
**Validation Score:** 0.73  
**Avg Assertion Confidence:** 0.73  

### RCA Summary

**Issue Title:** Repeated Full GC Allocation Failures with Heap Exhaustion at 365M Ceiling

**Root Cause:** The JVM heap is configured at approximately 365M, which is insufficient for the application's sustained working set. Repeated Allocation Failure triggers on both Young and Full GC collections indicate the heap fills faster than GC can reclaim space. Full GC events with poor reclamation ratios (e.g., GC(16): 315M->263M, only 17% freed; GC(22): 301M->265M, only 12% freed) show that a large fraction of objects are surviving collection, suggesting either a memory leak accumulating tenured objects or a legitimate large working set approaching the heap ceiling. The escalating post-Full-GC baseline (8M after GC7, 263M after GC16, 265M after GC17/23, 264M after GC28) confirms progressive live-heap growth over time. The 107ms Full GC pause at GC(16) and the sustained high-frequency Full GC pattern will cause application latency spikes and potential request timeouts. Without intervention, the heap will eventually be unable to reclaim sufficient memory even with Full GC, leading to OutOfMemoryError and container crash.

### MCP Server Data

| MCP Server | Status | Data Retrieved |
|---|---|---|
| **Kubernetes MCP** | Connected | Pod status (Running), pod events (BackOff from prior pods), pod logs (28 GC events) |
| **Kruize MCP** | No Data | Cost recommendations: No Data Available; Performance recommendations: No Data Available |
| **Cryostat MCP** | No Data | GC analysis, Memory analysis, Thread analysis, Exception analysis, Container analysis: all No Data Available |
| **Quarkus MCP** | Not Integrated | Quarkus metrics MCP not yet integrated in causa-backend code |

### List of Assertions (15 total)

#### Extracted Assertions (Pre-validation)

| # | Type | Source | Assertion Text |
|---|---|---|---|
| 1 | CONFIGURATION | ROOT_CAUSE | The JVM heap is configured at approximately 365M, which is insufficient for the application's sustained working set |
| 2 | OBSERVATION | ROOT_CAUSE | Repeated Allocation Failure triggers on both Young and Full GC collections indicate the heap fills faster than GC can reclaim space |
| 3 | RECOMMENDATION | ROOT_CAUSE | Full GC events with poor reclamation ratios (e.g., GC(16): 315M->263M, only 17% freed; GC(22): 301M->265M, only 12% freed) show that a large fraction of objects are surviving collection |
| 4 | TREND | ROOT_CAUSE | The escalating post-Full-GC baseline (8M after GC7, 263M after GC16, 265M after GC17/23, 264M after GC28) confirms progressive live-heap growth over time |
| 5 | OBSERVATION | ROOT_CAUSE | The 107ms Full GC pause at GC(16) and the sustained high-frequency Full GC pattern will cause application latency spikes and potential request timeouts |
| 6 | OBSERVATION | ROOT_CAUSE | Without intervention, the heap will eventually be unable to reclaim sufficient memory even with Full GC, leading to OutOfMemoryError and container crash |
| 7 | OBSERVATION | ISSUE_DESCRIPTION | The application keeps running out of space to store data in memory, forcing it to frequently pause and clean up |
| 8 | OBSERVATION | ISSUE_DESCRIPTION | Some of these cleanup pauses are long enough to noticeably slow down the service |
| 9 | OBSERVATION | ISSUE_DESCRIPTION | The memory ceiling is being hit so often that the application is spending significant time in recovery mode rather than doing real work |
| 10 | OBSERVATION | ISSUE_DESCRIPTION | If this pattern continues, the application risks becoming completely unresponsive or crashing |
| 11 | CONFIGURATION | TECHNICAL_DESCRIPTION | The container is configured with a memory limit of 650Mi and a request of 256Mi |
| 12 | CAUSALITY | TECHNICAL_DESCRIPTION | Pod logs show 28 GC events within the first ~698 seconds of runtime, including 8 Full GC events all triggered by Allocation Failure |
| 13 | OBSERVATION | TECHNICAL_DESCRIPTION | The heap ceiling observed in logs is 365M, with repeated episodes of the heap filling to capacity |
| 14 | OBSERVATION | TECHNICAL_DESCRIPTION | Full GC pause times include 87ms (GC5), 107ms (GC16), 43ms (GC17), 35ms (GC18), 41ms (GC22), 34ms (GC23), and 47ms (GC28), with GC(16) exceeding the 100ms threshold |
| 15 | CONFIGURATION | TECHNICAL_DESCRIPTION | After some Full GCs, heap reclamation is poor — GC(16) only freed 52M (315M->263M), GC(17) freed 100M (365M->265M), and GC(22) freed only 36M (301M->265M) |

### List of Findings (Validated Assertions with Evidence)

| # | Status | Confidence | Assertion | Supporting Evidence | Refuting Evidence |
|---|---|---|---|---|---|
| 1 | SUPPORTED | 0.90 | JVM heap configured at ~365M, insufficient for sustained working set | 7 | 0 |
| 2 | SUPPORTED | 0.92 | Repeated Allocation Failure triggers indicate heap fills faster than GC can reclaim | 7 | 0 |
| 3 | UNKNOWN | 0.00 | Full GC events with poor reclamation ratios (skipped — classified as RECOMMENDATION) | 0 | 0 |
| 4 | PARTIALLY_SUPPORTED | 0.62 | Escalating post-Full-GC baseline confirms progressive live-heap growth | 7 | 0 |
| 5 | PARTIALLY_SUPPORTED | 0.55 | 107ms Full GC pause at GC(16) will cause latency spikes | 4 | 0 |
| 6 | PARTIALLY_SUPPORTED | 0.55 | Without intervention, heap will lead to OutOfMemoryError and crash | 8 | 0 |
| 7 | SUPPORTED | 0.92 | Application keeps running out of memory, forcing frequent cleanup pauses | 7 | 0 |
| 8 | SUPPORTED | 0.82 | Some cleanup pauses are long enough to noticeably slow down the service | 7 | 0 |
| 9 | PARTIALLY_SUPPORTED | 0.55 | Memory ceiling being hit often, application spending time in recovery vs. real work | 9 | 0 |
| 10 | PARTIALLY_SUPPORTED | 0.60 | If pattern continues, application risks becoming unresponsive or crashing | 8 | 0 |
| 11 | SUPPORTED | 0.98 | Container memory limit = 650Mi, request = 256Mi | 1 | 0 |
| 12 | SUPPORTED | 0.92 | 28 GC events in 698s, 8 Full GC events all triggered by Allocation Failure | 4 | 0 |
| 13 | PARTIALLY_SUPPORTED | 0.75 | Heap ceiling at 365M, repeated heap filling to capacity | 6 | 0 |
| 14 | SUPPORTED | 0.97 | Full GC pause times: 87ms, 107ms, 43ms, 35ms, 41ms, 34ms, 47ms — GC(16) exceeds 100ms | 7 | 0 |
| 15 | SUPPORTED | 0.85 | Poor heap reclamation: GC(16) freed 52M of 315M (17%), GC(22) freed 36M of 301M (12%) | 5 | 0 |

**Summary:** Supported=8, Partially Supported=6, Unsupported=0, Unknown=1 | Total Evidence: 87 pieces

### Citations/Evidence from RCA

1. 8 Full GC events all triggered by Allocation Failure within 698 seconds of startup
2. GC(16) Full GC pause of 107.317ms exceeds the 100ms POSSIBLE_GC_PAUSE threshold
3. Heap ceiling consistently at 365M; Young GC at GC(15) shows 315M->315M with 0ms — GC could not free any memory
4. Post-Full-GC live heap baseline rising: 8M (GC7, t=83s) → 263M (GC16, t=180s) → 265M (GC17, t=262s) → 265M (GC22, t=480s) → 264M (GC28, t=697s)
5. GC(16) reclaimed only 52M of 315M (17% freed), GC(22) reclaimed only 36M of 301M (12% freed)
6. Container memory limit: 650Mi; memory request: 256Mi
7. Prior pods (auth-cache-7f45b6b8f9-tsggt, auth-cache-bd9fbff8d-4nhp9) both show BackOff restarting events
8. No JFR/Cryostat data available across all 5 analysis categories
9. No Kruize performance or cost recommendations available

### Supporting Logs

```
[14.057s][info][gc] GC(5) Pause Full (Allocation Failure) 223M->223M(365M) 87.055ms
[83.966s][info][gc] GC(7) Pause Full (Allocation Failure) 316M->8M(365M) 30.643ms
[179.935s][info][gc] GC(15) Pause Young (Allocation Failure) 315M->315M(365M) 0.032ms
[180.042s][info][gc] GC(16) Pause Full (Allocation Failure) 315M->263M(365M) 107.317ms
[262.979s][info][gc] GC(17) Pause Full (Allocation Failure) 365M->265M(365M) 43.938ms
[463.969s][info][gc] GC(18) Pause Full (Allocation Failure) 365M->29M(365M) 34.745ms
[480.976s][info][gc] GC(22) Pause Full (Allocation Failure) 301M->265M(365M) 41.063ms
[673.969s][info][gc] GC(23) Pause Full (Allocation Failure) 365M->8M(365M) 34.809ms
[697.982s][info][gc] GC(28) Pause Full (Allocation Failure) 301M->264M(365M) 47.105ms
```

### Rules (PATH B)

**Status:** Skipped — hypothesis validator not available  
PATH B (rule-based validation) was not executed because the hypothesis validator component is not available in this build. Only PATH A (LLM assertion-based) validation ran.

### Recommendations

| # | Type | Title | Confidence |
|---|---|---|---|
| 1 | Immediate Mitigation | Increase JVM Heap Size and Container Memory Limit to 1Gi | 0.62 |
| 2 | Validate & Monitor | Monitor GC Frequency, Pause Duration, and Heap Utilization Post-Fix | 0.70 |
| 3 | Root Cause Fix | Investigate and Fix Memory Leak or Unbounded Cache/Object Retention | 0.50 |

---

## Comparison Summary

| Metric | Scenario 1: OOM | Scenario 2: GC Pause |
|---|---|---|
| **Anomaly Type** | OOM_KILLED | POSSIBLE_GC_PAUSE |
| **Validation Result** | SUPPORTED | PARTIALLY_SUPPORTED |
| **RCA Confidence** | 0.87 | 0.60 |
| **Validation Score** | 0.90 | 0.73 |
| **Avg Assertion Confidence** | 0.86 | 0.73 |
| **Assertions: Supported** | 12 | 8 |
| **Assertions: Partially Supported** | 3 | 6 |
| **Assertions: Unsupported** | 0 | 0 |
| **Assertions: Unknown** | 0 | 1 |
| **Total Evidence Pieces** | 67 | 87 |
| **K8s MCP Data** | Yes | Yes |
| **Kruize MCP Data** | No | No |
| **Cryostat MCP Data** | No | No |
| **Quarkus MCP Data** | Not integrated | Not integrated |
| **PATH A (Assertion)** | Ran | Ran |
| **PATH B (Rules)** | Skipped | Skipped |
