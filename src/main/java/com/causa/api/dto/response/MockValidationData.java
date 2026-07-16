package com.causa.api.dto.response;

/**
 * Mock validation data for UI testing with OOM scenario.
 *
 * <p>This provides hardcoded validation response for a real OOM case
 * where all assertions are validated and all rules pass.
 *
 * @since 0.0.1
 */
public class MockValidationData {

    /**
     * Returns complete validation JSON for OOM scenario.
     * Pod: heap-oom-prom-8554b846d7-v5hj2
     * Diagnosis: POSSIBLE_OOM_KILLED
     * Result: SUPPORTED
     */
    public static String getOomValidationJson() {
        return """
{
  "validatedAt": "2026-07-16T12:54:00.000Z",
  "finalVerdict": {
    "status": "SUPPORTED",
    "confidence": 0.92,
    "strategy": "WEIGHTED_AVERAGE",
    "supported": true,
    "highConfidence": true,
    "explanation": "RCA validation: SUPPORTED (confidence: 92%). Assertion validation (8 assertions): 7 supported, 1 partial → SUPPORTED (conf: 90%, weight: 40%). Rule validation (hypothesis: OOM_KILLED): 3/3 required rules passed, total score: 22 → SUPPORTED (conf: 95%, weight: 60%). Weighted score: 0.90 × 40% + 0.95 × 60% = 0.93.",
    "userFriendlyExplanation": "The diagnosis of an out-of-memory crash is **strongly confirmed**. We found clear evidence including heap space exhaustion errors in logs, memory usage at 93% of limit, increasing GC pause times, and the pod entering restart loops. The analysis is well-supported and you can confidently proceed with the recommended memory increase to 806 MiB."
  },
  "assertionValidation": {
    "summary": {
      "status": "SUPPORTED",
      "confidence": 0.9,
      "totalAssertions": 8,
      "supportedAssertions": 7,
      "partiallySupportedAssertions": 1,
      "unsupportedAssertions": 0,
      "unknownAssertions": 0,
      "explanation": "PATH A (Assertion-based): 7/8 assertions fully supported, 1 partially supported → SUPPORTED verdict with 90% confidence"
    },
    "results": [
      {
        "status": "SUPPORTED",
        "assertion": {
          "id": "assert-oom-001",
          "text": "Application aborted with OutOfMemoryError in Java heap space",
          "type": "OBSERVATION",
          "source": "ROOT_CAUSE",
          "relatedField": "rootCause"
        },
        "validated": true,
        "confidence": 0.99,
        "explanation": "Direct and unambiguous evidence found in application logs showing explicit OutOfMemoryError message and JVM fatal error",
        "refutingEvidence": [],
        "supportingEvidence": [
          {
            "type": "POD_LOG",
            "source": "APPLICATION_LOGS",
            "snippet": "Aborting due to java.lang.OutOfMemoryError: Java heap space",
            "relevanceScore": 0.99,
            "structuredData": null
          },
          {
            "type": "POD_LOG",
            "source": "APPLICATION_LOGS",
            "snippet": "fatal error: OutOfMemory encountered: Java heap space",
            "relevanceScore": 0.98,
            "structuredData": null
          },
          {
            "type": "POD_LOG",
            "source": "APPLICATION_LOGS",
            "snippet": "JRE version: OpenJDK Runtime Environment Temurin-21.0.10+7 (21.0.10+7) (build 21.0.10+7-LTS)",
            "relevanceScore": 0.85,
            "structuredData": null
          }
        ]
      },
      {
        "status": "SUPPORTED",
        "assertion": {
          "id": "assert-oom-002",
          "text": "Memory usage reached 93% (478/512 MiB) at the time of alert",
          "type": "OBSERVATION",
          "source": "ISSUE_DESCRIPTION",
          "relatedField": "issueDescription"
        },
        "validated": true,
        "confidence": 0.97,
        "explanation": "Prometheus metrics confirm memory usage at 478 MiB out of 512 MiB limit, which is exactly 93.36%",
        "refutingEvidence": [],
        "supportingEvidence": [
          {
            "type": "METRIC",
            "source": "PROMETHEUS_METRICS",
            "snippet": "MEMORY 478/512",
            "relevanceScore": 0.98,
            "structuredData": "{\\"value\\": 478, \\"limit\\": 512, \\"percent\\": 93.36}"
          },
          {
            "type": "METRIC",
            "source": "PROMETHEUS_METRICS",
            "snippet": "Memory utilization: 93.36% of limit",
            "relevanceScore": 0.95,
            "structuredData": null
          }
        ]
      },
      {
        "status": "SUPPORTED",
        "assertion": {
          "id": "assert-oom-003",
          "text": "Registry size growing from 159,000 to 166,350 targets within 3 minutes",
          "type": "TREND",
          "source": "TECHNICAL_DESCRIPTION",
          "relatedField": "technicalDescription"
        },
        "validated": true,
        "confidence": 0.95,
        "explanation": "Application logs show continuous target insertion with registry size incrementing from 159000 to 166350 targets between 06:09:01 and 06:09:04 UTC",
        "refutingEvidence": [],
        "supportingEvidence": [
          {
            "type": "POD_LOG",
            "source": "APPLICATION_LOGS",
            "snippet": "2026-06-18 06:09:01,405 INFO  [ai.causa.scheduler.DiscoveryScheduler] (executor-thread-9) Inserted 159000 targets. Current registry size=159000",
            "relevanceScore": 0.92,
            "structuredData": null
          },
          {
            "type": "POD_LOG",
            "source": "APPLICATION_LOGS",
            "snippet": "2026-06-18 06:09:04,839 INFO  [ai.causa.scheduler.ScrapeScheduler] (executor-thread-12) Writer started. targets=166350",
            "relevanceScore": 0.94,
            "structuredData": null
          },
          {
            "type": "POD_LOG",
            "source": "APPLICATION_LOGS",
            "snippet": "2026-06-18 06:09:01,417 INFO  [ai.causa.scheduler.DiscoveryScheduler] (executor-thread-9) Inserted 161000 targets. Current registry size=161000",
            "relevanceScore": 0.88,
            "structuredData": null
          }
        ]
      },
      {
        "status": "SUPPORTED",
        "assertion": {
          "id": "assert-oom-004",
          "text": "Pod entered BackOff restart loop after the crash",
          "type": "OBSERVATION",
          "source": "ISSUE_DESCRIPTION",
          "relatedField": "issueDescription"
        },
        "validated": true,
        "confidence": 0.96,
        "explanation": "Kubernetes events show BackOff event at 06:09:13 UTC indicating container restart attempts after failure",
        "refutingEvidence": [],
        "supportingEvidence": [
          {
            "type": "KUBERNETES_EVENT",
            "source": "POD_EVENTS",
            "snippet": "[Normal] 2026-06-18 06:09:13 +0000 UTC: BackOff - Back-off restarting failed container heap-oom-prom in pod heap-oom-prom-8554b846d7-v5hj2_chaos-test(094f00cf-7b84-41c3-8977-7beb051b6689)",
            "relevanceScore": 0.97,
            "structuredData": null
          }
        ]
      },
      {
        "status": "SUPPORTED",
        "assertion": {
          "id": "assert-oom-005",
          "text": "Application was using Serial GC with increasing pause times (82.4ms → 313ms → 597ms)",
          "type": "TREND",
          "source": "TECHNICAL_DESCRIPTION",
          "relatedField": "technicalDescription"
        },
        "validated": true,
        "confidence": 0.88,
        "explanation": "JFR data confirms Serial GC configuration (DefNew/SerialOld) with parallelGCThreads=0 and increasing GC pause times indicating memory pressure",
        "refutingEvidence": [],
        "supportingEvidence": [
          {
            "type": "CRYOSTAT_ANALYSIS",
            "source": "JFR_DATA",
            "snippet": "youngCollector='DefNew', oldCollector='SerialOld', parallelGCThreads=0",
            "relevanceScore": 0.90,
            "structuredData": null
          },
          {
            "type": "CRYOSTAT_ANALYSIS",
            "source": "JFR_DATA",
            "snippet": "GC pause times: 82.4ms → 313ms → 597ms",
            "relevanceScore": 0.92,
            "structuredData": "{\\"pauses\\": [82.4, 313, 597], \\"unit\\": \\"ms\\"}"
          }
        ]
      },
      {
        "status": "SUPPORTED",
        "assertion": {
          "id": "assert-oom-006",
          "text": "Large object allocation detected: 99.3 MB byte array allocation",
          "type": "OBSERVATION",
          "source": "TECHNICAL_DESCRIPTION",
          "relatedField": "technicalDescription"
        },
        "validated": true,
        "confidence": 0.91,
        "explanation": "JFR profiling data shows large byte array allocation of 99.3 MB contributing to memory pressure",
        "refutingEvidence": [],
        "supportingEvidence": [
          {
            "type": "CRYOSTAT_ANALYSIS",
            "source": "JFR_DATA",
            "snippet": "Large object allocation detected: 99.3 MB byte array allocation",
            "relevanceScore": 0.93,
            "structuredData": "{\\"size\\": 99.3, \\"unit\\": \\"MB\\", \\"type\\": \\"byte array\\"}"
          }
        ]
      },
      {
        "status": "SUPPORTED",
        "assertion": {
          "id": "assert-oom-007",
          "text": "Kruize recommends memory increase from 512 MiB to 806 MiB (+294 MiB / +57%)",
          "type": "RECOMMENDATION",
          "source": "POSSIBLE_SOLUTIONS",
          "relatedField": "possibleSolutions"
        },
        "validated": true,
        "confidence": 0.94,
        "explanation": "Kruize performance analysis data confirms recommendation to increase memory limits from 512 MiB to 806 MiB",
        "refutingEvidence": [],
        "supportingEvidence": [
          {
            "type": "KRUIZE_RECOMMENDATION",
            "source": "KRUIZE_RECOMMENDATIONS",
            "snippet": "limits: memory: 806 Mi (current: 512 Mi, increase: +294 Mi)",
            "relevanceScore": 0.95,
            "structuredData": "{\\"current\\": 512, \\"recommended\\": 806, \\"increase\\": 294, \\"unit\\": \\"MiB\\"}"
          },
          {
            "type": "KRUIZE_RECOMMENDATION",
            "source": "KRUIZE_RECOMMENDATIONS",
            "snippet": "requests: memory: 806 Mi (current: 256 Mi, increase: +550 Mi)",
            "relevanceScore": 0.92,
            "structuredData": null
          }
        ]
      },
      {
        "status": "PARTIALLY_SUPPORTED",
        "assertion": {
          "id": "assert-oom-008",
          "text": "Unbounded memory growth due to continuous insertion without proper size limits",
          "type": "CAUSALITY",
          "source": "ROOT_CAUSE",
          "relatedField": "rootCause"
        },
        "validated": false,
        "confidence": 0.78,
        "explanation": "While logs show continuous target insertion and memory growth, there is no direct evidence proving the absence of size limits in the code. The pattern is consistent with unbounded growth, but we cannot definitively confirm lack of size limits without code inspection",
        "refutingEvidence": [],
        "supportingEvidence": [
          {
            "type": "POD_LOG",
            "source": "APPLICATION_LOGS",
            "snippet": "Registry grew from 159,000 to 166,350 targets in ~3 minutes",
            "relevanceScore": 0.85,
            "structuredData": null
          },
          {
            "type": "METRIC",
            "source": "PROMETHEUS_METRICS",
            "snippet": "Memory usage at 93% capacity: 478/512 MiB",
            "relevanceScore": 0.82,
            "structuredData": null
          }
        ]
      }
    ]
  },
  "ruleValidation": {
    "summary": {
      "status": "SUPPORTED",
      "confidence": 0.95,
      "hypothesis": "OOM_KILLED",
      "totalScore": 22,
      "requiredTotal": 3,
      "requiredPassed": 3,
      "supportingMatched": 5,
      "exclusionMatched": 0,
      "explanation": "PATH B (Rule-based): All 3 required rules passed + 5 supporting rules matched → total score: 22 → SUPPORTED verdict with 95% confidence"
    },
    "results": [
      {
        "rule": {
          "id": "oom.required.memory_high",
          "description": "Memory utilization exceeds 90% threshold",
          "type": "REQUIRED",
          "weight": 1
        },
        "passed": true,
        "reasoning": "Memory usage at 93.36% (478/512 MiB) exceeds 90% threshold",
        "matchedSignals": [
          {
            "type": "METRIC",
            "name": "memory.utilization.percent",
            "value": "93.36"
          }
        ]
      },
      {
        "rule": {
          "id": "oom.required.oom_error_in_logs",
          "description": "OutOfMemoryError found in application logs",
          "type": "REQUIRED",
          "weight": 1
        },
        "passed": true,
        "reasoning": "Found OutOfMemoryError: Java heap space in logs",
        "matchedSignals": [
          {
            "type": "LOG_PATTERN",
            "name": "error.oom",
            "value": "OutOfMemoryError: Java heap space"
          }
        ]
      },
      {
        "rule": {
          "id": "oom.required.memory_increasing",
          "description": "Memory utilization continuously increased",
          "type": "REQUIRED",
          "weight": 1
        },
        "passed": true,
        "reasoning": "Registry size grew from 159K to 166K targets indicating increasing memory usage",
        "matchedSignals": [
          {
            "type": "LOG_PATTERN",
            "name": "memory.growth.pattern",
            "value": "INCREASING"
          }
        ]
      },
      {
        "rule": {
          "id": "oom.supporting.pod_backoff",
          "description": "Pod entered BackOff restart state",
          "type": "SUPPORTING",
          "weight": 4
        },
        "passed": true,
        "reasoning": "BackOff event detected at 06:09:13 UTC",
        "matchedSignals": [
          {
            "type": "KUBERNETES_EVENT",
            "name": "pod.state",
            "value": "BackOff"
          }
        ]
      },
      {
        "rule": {
          "id": "oom.supporting.gc_pressure",
          "description": "GC pause times indicate memory pressure",
          "type": "SUPPORTING",
          "weight": 3
        },
        "passed": true,
        "reasoning": "GC pause times increased from 82ms to 597ms showing memory pressure",
        "matchedSignals": [
          {
            "type": "JVM_ANALYSIS",
            "name": "gc.pause.max",
            "value": "597"
          }
        ]
      },
      {
        "rule": {
          "id": "oom.supporting.large_allocation",
          "description": "Large object allocations detected",
          "type": "SUPPORTING",
          "weight": 3
        },
        "passed": true,
        "reasoning": "99.3 MB byte array allocation detected in JFR data",
        "matchedSignals": [
          {
            "type": "JVM_ANALYSIS",
            "name": "allocation.large.size",
            "value": "99.3"
          }
        ]
      },
      {
        "rule": {
          "id": "oom.supporting.kruize_memory_recommendation",
          "description": "Kruize recommends increasing memory limit",
          "type": "SUPPORTING",
          "weight": 4
        },
        "passed": true,
        "reasoning": "Kruize recommends memory increase to 806 MiB",
        "matchedSignals": [
          {
            "type": "KRUIZE_RECOMMENDATION",
            "name": "memory.limit.recommendation",
            "value": "806"
          }
        ]
      },
      {
        "rule": {
          "id": "oom.supporting.jvm_fatal_error",
          "description": "JVM fatal error reported",
          "type": "SUPPORTING",
          "weight": 5
        },
        "passed": true,
        "reasoning": "JVM fatal error: OutOfMemory encountered",
        "matchedSignals": [
          {
            "type": "LOG_PATTERN",
            "name": "jvm.fatal.error",
            "value": "fatal error: OutOfMemory encountered"
          }
        ]
      }
    ]
  }
}
""";
    }
}
