# Diagnostics API

This document covers the diagnostic listing and diagnostic detail endpoints.

**Base URLs:** `{{BASE_URL}}` or `http://localhost:8080`

## Endpoints

| Endpoint | Purpose | Success response code | Error response codes |
|---|---|---|---|
| `GET /api/v1/diagnostics` | List diagnostic summaries | `200 OK` | `500 Server Error` |
| `GET /api/v1/diagnostics/{id}` | Fetch full diagnostic detail by id | `200 OK` | `404 Not Found` |

---

## GET `/api/v1/diagnostics`

Returns a summary list of diagnostics.

### Request Example (cURL)

**Using `{{BASE_URL}}`:**

```bash
curl {{BASE_URL}}/api/v1/diagnostics
```

**Using `localhost:8080`:**

```bash
curl http://localhost:8080/api/v1/diagnostics
```

### Response Example

```json
[
 {
  "id": "diag_iYRpHr6rt8kkXgBu",
  "status": "COMPLETED",
  "issue": "auth-cache pod in Unknown state with no resource limits and repeated short-lived container cycles",
  "issue_summary": "The auth-cache application pod is in an Unknown state and its containers were started and stopped repeatedly in very short windows, suggesting the workload was being redeployed or reconfigured rapidly without any memory or CPU limits set to protect it.",
  "workload_name": "auth-cache-1",
  "namespace": "openshift-tuning",
  "severity": "critical",
  "cluster_name": "default",
  "date": "2026-08-05T12:56:01.073Z"
 }
]
```

### Response notes

- `status` is the diagnostic pipeline status.
- `issue` and `issue_summary` can be `null` when generation failed or has not produced RCA content yet.
- `cluster_name` is populated from runtime configuration and currently returns `default` locally.

---

## GET `/api/v1/diagnostics/{id}`

Returns the full diagnostic detail for a single diagnostic id.

### Request Example (cURL)

**Using `{{BASE_URL}}`:**

```bash
curl {{BASE_URL}}/api/v1/diagnostics/diag_6YuCDWBMfwzWoMhY
```

**Using `localhost:8080`:**

```bash
curl http://localhost:8080/api/v1/diagnostics/diag_6YuCDWBMfwzWoMhY
```

### Response Example

```json
{
  "id": "diag_iYRpHr6rt8kkXgBu",
  "status": "COMPLETED",
  "alert_id": "alrt_waWEGwPtpXPpeEvr",
  "alert_name": "chaos-lab-high-memory",
  "severity": "critical",
  "alert_received_at": "2026-08-05T12:53:26.048Z",
  "workload_info": {
    "pod_name": "auth-cache-1-6c5dfb54b9-4cmbf",
    "workload_name": "auth-cache-1",
    "namespace": "openshift-tuning",
    "cluster_name": "default",
    "workload_type": null
  },
  "diagnosis": {
    "issue_title": "auth-cache pod in Unknown state with no resource limits and repeated short-lived container cycles",
    "issue_summary": "The auth-cache application pod is in an Unknown state and its containers were started and stopped repeatedly in very short windows, suggesting the workload was being redeployed or reconfigured rapidly without any memory or CPU limits set to protect it.",
    "issue_description": "The auth-cache service keeps starting and stopping within minutes each time it is deployed, and it currently shows as Unknown rather than running normally. The application has no memory or CPU boundaries configured, meaning it could use as much of the system's resources as it wants. Without those boundaries, if the application needs more memory than the system can provide, it may be shut down abruptly. The repeated short-lived runs point to an ongoing instability that has not yet been resolved.",
    "technical_description": "The pod auth-cache-1-6c5dfb54b9-4cmbf is currently in an Unknown phase with 0 recorded restarts. Pod events show a rapid succession of ReplicaSet scaling operations beginning around 12:31 UTC and continuing through 12:55 UTC, with individual pod instances surviving only 2–4 minutes before being killed (e.g., auth-cache-1-586d95c87-drkm4 started at 12:31:28 and killed at 12:34:43; auth-cache-1-5cd5f89c6f-jbqpd started at 12:51:13 and killed at 12:51:24; auth-cache-1-6c5dfb54b9-4cmbf started at 12:51:23 with a Killing event at 12:55:10). The container image used is quay.io/causa-ai-hub/quarkus-gc-pause:promotion-pressure-nd, whose tag 'promotion-pressure-nd' strongly implies it is a test image designed to simulate GC promotion pressure conditions. No CPU or memory resource requests or limits are configured for the auth-cache container. Kruize performance recommendations for the short term indicate memory usage ranging from a minimum of ~397 MiB to a maximum of ~736 MiB across the 24-hour observation window, with a recommended memory limit of approximately 883 MiB (~842 MiB) and a recommended CPU limit of ~0.045 cores. JFR analysis data (GC, memory, thread, exception, container) is entirely absent, preventing direct heap or GC characterization. Application logs for the pod are unavailable because the pod no longer exists in the cluster at query time. The Unknown pod phase combined with the pattern of rapid successive deployments and the promotion-pressure image tag collectively suggest GC promotion pressure is causing instability, but direct OOMKilled events or exit code 137 are not present in the available event stream.",
    "anomaly_type": "POSSIBLE_GC_PAUSE",
    "root_cause": "The auth-cache container runs the image tagged 'promotion-pressure-nd', which is explicitly designed to simulate GC object promotion pressure — a condition where the JVM young generation fills rapidly and objects are promoted to old/tenured generation faster than GC can reclaim them, leading to long GC pause events or continuous GC overhead. Because no memory limits or requests are set on the container, the JVM cannot be correctly auto-configured to match available headroom, and the heap may be sized either too small (causing frequent GC cycles and long stop-the-world pauses) or too large relative to node capacity. The Kruize metrics show memory consumption ranging up to ~736 MiB with spikes to ~736 MiB max observed, and the recommendation engine suggests an upper bound near 883 MiB to handle peak demand safely. The rapid container lifecycle (pods lasting 2–4 minutes before Killing events) is consistent with an external controller terminating pods that fail readiness or liveness probes during prolonged GC pause windows, or with automated redeployment cycles during a configuration tuning experiment. The absence of explicit OOMKilled events or exit code 137 shifts the primary hypothesis toward GC-induced probe failures rather than hard OOM termination, though OOM cannot be fully excluded without logs or JFR data.",
    "evidences": [
      "Pod phase is Unknown with 0 restarts recorded for auth-cache-1-6c5dfb54b9-4cmbf — indicates abnormal termination or node communication loss rather than clean lifecycle",
      "Container image tag 'quay.io/causa-ai-hub/quarkus-gc-pause:promotion-pressure-nd' explicitly signals a GC promotion pressure simulation workload, confirming GC-related stress is intentional or expected",
      "Pod event at 12:51:23 UTC: Started container auth-cache; Pod event at 12:55:10 UTC: Killing — Stopping container auth-cache — lifetime of approximately 3 minutes 47 seconds before forced termination",
      "Pod event at 12:51:13 UTC: Started container auth-cache (auth-cache-1-5cd5f89c6f-jbqpd); Pod event at 12:51:24 UTC: Killing — lifetime of ~11 seconds, indicating probe failure or immediate crash on startup",
      "No CPU or memory resource requests or limits are set on the auth-cache container, preventing JVM ergonomics from correctly sizing heap relative to container boundaries",
      "Kruize short-term memory usage data: min ~397 MiB, Q1 ~562 MiB, median ~580 MiB, Q3 ~582 MiB, max ~736 MiB (2026-08-04T18:47 window); max observed across all windows ~736 MiB — significant variance indicating memory pressure spikes",
      "Kruize performance recommendation: memory request and limit of ~883 MiB (8.831803392E8 bytes), representing a delta of +883 MiB from current unset baseline — confirms no limits are defined and significant headroom is needed",
      "Kruize performance recommendation: CPU request and limit of ~0.045 cores (4.467E-2), representing a delta of +0.045 from current unset baseline",
      "Multiple rapid ReplicaSet scaling events observed: auth-cache-1-786bcc4d95 scaled up at 12:51:09 and deleted at 12:51:10 (< 1 minute); auth-cache-1-5cd5f89c6f scaled up at 12:51:10 and deleted at 12:51:24 — pattern consistent with rolling update loop or repeated probe failures",
      "JFR GC analysis, memory analysis, thread analysis, exception analysis, and container resource analysis all report 'No Data Available' — prevents direct confirmation of GC pause durations, heap pool saturation, or OOM exception counts"
    ],
    "supporting_logs": [
      "No direct supporting logs present — pod auth-cache-1-6c5dfb54b9-4cmbf not found when logs were retrieved (pod no longer exists in cluster)"
    ],
    "rca_confidence_score": 0.42,
    "confidence_summary": "Confidence in the root cause diagnosis is moderate-low at 0.42. The strongest evidence is circumstantial: the image tag 'promotion-pressure-nd' directly implies GC promotion pressure as the design intent, and the pattern of pods surviving only 2–11 minutes before being killed is consistent with probe failures during GC pause windows or a rapid rolling-update experiment. However, all direct confirming signals are absent — APPLICATION_LOGS returned 'pod not found', JFR analysis across all five analysis types shows 'No Data Available', PROMETHEUS_METRICS are not present, and no OOMKilled or exit code 137 events appear in the pod event stream. The Kruize memory usage data (max ~736 MiB, recommendation ~883 MiB) provides indirect evidence of memory pressure but does not differentiate between GC-induced slowness and hard OOM termination. Higher confidence would require JFR data showing jdk.PromotionFailed events or GC pause durations, application logs showing GC overhead warnings or OOM errors, and Prometheus memory utilization percentages at time of container termination.",
    "recommendations": [
      {
        "solution_type": "Immediate Mitigation",
        "solution_title": "Set Memory and CPU Limits Based on Kruize Recommendations",
        "solution_description": "The auth-cache container currently has no resource requests or limits defined. Setting memory limits to ~883 MiB and CPU limits to ~0.045 cores (as recommended by Kruize) will allow the JVM to correctly size its heap via container-aware ergonomics and will protect the node from unbounded resource consumption. This is the most impactful immediate change to stabilize the pod.",
        "implementation_notes": "Step 1 — Verify current deployment configuration:\n  kubectl get deployment auth-cache-1 -n openshift-tuning -o yaml | grep -A 20 resources\n\nStep 2 — Patch the deployment to add resource requests and limits:\n  kubectl patch deployment auth-cache-1 -n openshift-tuning --type='json' -p='[\n    {\n      \"op\": \"add\",\n      \"path\": \"/spec/template/spec/containers/0/resources\",\n      \"value\": {\n        \"requests\": {\n          \"memory\": \"883Mi\",\n          \"cpu\": \"50m\"\n        },\n        \"limits\": {\n          \"memory\": \"883Mi\",\n          \"cpu\": \"50m\"\n        }\n      }\n    }\n  ]'\n\nStep 3 — If the container name needs to be targeted specifically:\n  kubectl set resources deployment auth-cache-1 -n openshift-tuning \\\n    -c auth-cache \\\n    --requests=memory=883Mi,cpu=50m \\\n    --limits=memory=883Mi,cpu=50m\n\nStep 4 — Verify the patch was applied:\n  kubectl get deployment auth-cache-1 -n openshift-tuning -o jsonpath='{.spec.template.spec.containers[0].resources}'\n\nStep 5 — Watch the rollout:\n  kubectl rollout status deployment/auth-cache-1 -n openshift-tuning\n\nStep 6 — Confirm new pod is running:\n  kubectl get pods -n openshift-tuning -l app=auth-cache -w",
        "solution_confidence_score": 0.62,
        "solution_alerts": [
          "APPLICATION_LOGS not available — cannot confirm whether the short container lifetimes are caused by GC pauses failing probes, OOM termination, or an external controller-driven redeployment loop; resource limit increase may not fully resolve the issue",
          "JFR_ANALYSIS not available — heap sizing recommendation is based solely on Kruize observed metrics (max ~736 MiB) and not on actual GC heap pool saturation data; the 883 MiB limit may still be insufficient if promotion pressure causes peak spikes beyond observed maximums",
          "Pod phase is Unknown — if the node lost contact with the API server, the pod may need to be manually deleted and recreated: kubectl delete pod auth-cache-1-6c5dfb54b9-4cmbf -n openshift-tuning"
        ]
      },
      {
        "solution_type": "Validate & Monitor",
        "solution_title": "Monitor Pod Stability, Memory Usage, and GC Behavior After Limit Configuration",
        "solution_description": "After applying resource limits, monitor pod restart frequency, memory consumption relative to the new 883 MiB limit, and liveness/readiness probe success rates to validate that GC promotion pressure is no longer causing probe timeouts or crashes. Success criteria: pod remains Running for at least 30 minutes without Killing events, and memory stays below 80% of the configured limit (~706 MiB).",
        "implementation_notes": "Step 1 — Watch pod status continuously for at least 10 minutes after rollout:\n  kubectl get pods -n openshift-tuning -l app=auth-cache -w\n\nStep 2 — Check resource consumption in real time:\n  kubectl top pod -n openshift-tuning -l app=auth-cache --containers\n\nStep 3 — Stream logs from the new pod to detect GC or OOM messages:\n  kubectl logs -f deployment/auth-cache-1 -n openshift-tuning -c auth-cache\n\nStep 4 — Check pod events for any new Killing or OOMKilled events:\n  kubectl describe pod <new-pod-name> -n openshift-tuning | grep -E 'OOMKilled|Killing|Exit Code|Reason'\n\nStep 5 — Prometheus queries to monitor memory pressure (if Prometheus is available):\n  # Memory usage as percentage of limit\n  container_memory_working_set_bytes{namespace=\"openshift-tuning\", container=\"auth-cache\"} / (883 * 1024 * 1024)\n\n  # Restart count\n  kube_pod_container_status_restarts_total{namespace=\"openshift-tuning\", container=\"auth-cache\"}\n\n  # OOMKilled detection\n  kube_pod_container_status_last_terminated_reason{namespace=\"openshift-tuning\", container=\"auth-cache\", reason=\"OOMKilled\"}\n\nStep 6 — Success criteria:\n  - Pod Running continuously for >= 30 minutes: PASS\n  - Memory usage stays below 706 MiB (80% of 883 MiB): PASS\n  - Zero Killing events not triggered by intentional redeployment: PASS\n  - No OOMKilled reason in pod status: PASS\n\nStep 7 — If Cryostat JFR recording is available, trigger a new recording to capture GC behavior:\n  # Via Cryostat UI or API, create a new recording targeting auth-cache pod\n  # Review GC_ANALYSIS output for pause durations > 100ms and promotion failure counts",
        "solution_confidence_score": 0.58,
        "solution_alerts": [
          "PROMETHEUS_METRICS for the current pod are not available in the provided signals — baseline memory percentage cannot be established; manual kubectl top observation is required immediately after fix",
          "JFR_ANALYSIS was unavailable for the previous pod instances — ensure Cryostat agent init container (cryostat-agent-init:0.7.0, which is present in events) is functioning and a JFR recording is triggered on the new pod to enable GC pause validation",
          "Pod lifecycle was extremely short (some instances < 30 seconds) — if the new pod also terminates within minutes, the root cause likely involves an external controller action (e.g., rolling update loop) rather than purely a resource limit issue; investigate the deployment controller or HPA configuration"
        ]
      },
      {
        "solution_type": "Root Cause Fix",
        "solution_title": "Configure JVM GC Settings and Heap Sizing to Eliminate Promotion Pressure",
        "solution_description": "The image tag 'promotion-pressure-nd' indicates the application is either simulating or experiencing object promotion pressure, where short-lived objects are not being reclaimed in the young generation fast enough and are being promoted to the old generation, eventually causing full GC pauses or GC overhead limit exhaustion. The permanent fix involves tuning JVM garbage collection parameters to reduce promotion frequency, appropriately sizing young and old generation regions, and (if this is a test image) validating that the simulated scenario matches the intended test parameters. If this is a production Quarkus application, GC tuning should be applied via JVM flags in the container environment.",
        "implementation_notes": "Step 1 — Identify current JVM flags in use:\n  kubectl exec -n openshift-tuning <pod-name> -c auth-cache -- jcmd 1 VM.flags\n  # OR check environment variables:\n  kubectl exec -n openshift-tuning <pod-name> -c auth-cache -- env | grep -E 'JAVA_OPTS|JVM_OPTS|QUARKUS'\n\nStep 2 — For a Quarkus application experiencing GC promotion pressure, add the following JVM tuning flags via environment variables in the deployment:\n  kubectl patch deployment auth-cache-1 -n openshift-tuning --type='json' -p='[\n    {\n      \"op\": \"add\",\n      \"path\": \"/spec/template/spec/containers/0/env\",\n      \"value\": [\n        {\n          \"name\": \"JAVA_OPTS_APPEND\",\n          \"value\": \"-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -XX:InitialRAMPercentage=50.0 -XX:+UseContainerSupport -XX:G1NewSizePercent=20 -XX:G1MaxNewSizePercent=40 -XX:MaxGCPauseMillis=200 -Xlog:gc*:stdout:time,uptime,level,tags\"\n        }\n      ]\n    }\n  ]'\n\nStep 3 — Key JVM flag explanations:\n  -XX:+UseG1GC                  : Use G1 garbage collector which handles promotion pressure better than Serial/Parallel GC\n  -XX:MaxRAMPercentage=75.0     : Limit heap to 75% of container memory limit (662 MiB of 883 MiB), leaving OS headroom\n  -XX:+UseContainerSupport      : Enable JVM container awareness so it reads cgroup limits correctly\n  -XX:G1NewSizePercent=20       : Set minimum young generation to 20% of heap\n  -XX:G1MaxNewSizePercent=40    : Cap young generation at 40% to reduce promotion frequency\n  -XX:MaxGCPauseMillis=200      : Target GC pause goal of 200ms\n  -Xlog:gc*:stdout              : Enable GC logging to stdout for visibility in kubectl logs\n\nStep 4 — If the 'promotion-pressure-nd' image is a purpose-built test/demo image (not production), verify with the image maintainer (quay.io/causa-ai-hub) what JVM flags or load parameters control the promotion pressure simulation, and adjust the test parameters to match realistic workload expectations.\n\nStep 5 — If memory leaks are suspected (evidenced by monotonically increasing memory in the Kruize plots from ~397 MiB min to ~736 MiB max over 24 hours), enable heap dump on OOM for post-mortem analysis:\n  Add to JAVA_OPTS_APPEND: -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp/heapdump.hprof\n  After an OOM event: kubectl cp <pod-name>:/tmp/heapdump.hprof ./heapdump.hprof -n openshift-tuning -c auth-cache\n\nStep 6 — Trigger a Cryostat JFR recording with GC-focused event settings immediately after the pod stabilizes:\n  # Use Cryostat UI at the cryostat-sample route in openshift-tuning namespace\n  # Create a recording with template 'Profiling' or custom template including: jdk.GCHeapSummary, jdk.GarbageCollection, jdk.PromotionFailed, jdk.OldObjectSample\n  # Minimum recording duration: 5 minutes under load\n\nStep 7 — Review JFR output for:\n  - jdk.PromotionFailed events (direct evidence of promotion pressure)\n  - GC pause duration exceeding 200ms\n  - Old generation occupancy trend (should stabilize, not grow linearly)\n  - jdk.OldObjectSample showing which object types are being promoted",
        "solution_confidence_score": 0.38,
        "solution_alerts": [
          "JFR_ANALYSIS not available — cannot confirm GC collector type in use, actual promotion failure events, or heap region occupancy; all JVM tuning flags in this recommendation are based on best practices for the 'promotion-pressure' symptom pattern and may need adjustment after JFR data is collected",
          "APPLICATION_LOGS not available — cannot determine if GC log output or OOM stack traces were emitted before container termination; the GC collector currently configured in the image is unknown",
          "The image 'quay.io/causa-ai-hub/quarkus-gc-pause:promotion-pressure-nd' appears to be a test/simulation image — if the promotion pressure is intentionally injected by the application code (not a real workload bug), JVM tuning alone will not eliminate the root cause; the test scenario parameters must be reviewed with the image authors",
          "Pod phase Unknown and log retrieval failure indicate the pod no longer exists — no live analysis is possible; all recommendations assume a new pod will be deployed with the corrected configuration"
        ]
      }
    ],
    "llm_notes": "Analysis began with the available signals: POD_STATUS showing Unknown phase with 0 restarts, a rich but namespace-broad POD_EVENTS stream, failed log retrieval for both current and previous container, Kruize cost and performance recommendations with 24-hour memory data, and entirely empty JFR analysis sections. The first notable finding was the image tag 'quay.io/causa-ai-hub/quarkus-gc-pause:promotion-pressure-nd' — the workload name 'auth-cache' combined with this tag makes it highly likely this is a demonstration or test application deliberately simulating GC promotion pressure, not a production workload with an organic bug. The pod event stream required careful filtering since it contains events from many different workloads in the openshift-tuning namespace (heap-oom, cryostat, kruize, cnpg, etc.); I isolated only events referencing auth-cache-1 pods. The key pattern that emerged was rapid ReplicaSet cycling: six different ReplicaSets for auth-cache-1 were created and destroyed between 12:31 and 12:55 UTC, with individual pod lifetimes ranging from under 15 seconds to about 4 minutes — this is not consistent with normal OOMKilled behavior (which typically shows exit code 137 and an OOMKilled reason) but is consistent with either liveness/readiness probe failures during GC pauses or an automated rolling-update experiment. The absence of any OOMKilled or exit code 137 events was the primary reason I chose POSSIBLE_GC_PAUSE over POSSIBLE_OOM_KILLED or OOM_KILLED, despite the memory usage reaching ~736 MiB max according to Kruize. The Kruize data was the only quantitative memory signal available and was used to anchor the resource limit recommendation at 883 MiB. I considered POSSIBLE_OOM_KILLED as an alternative but the lack of exit code 137 events, combined with the explicit GC-pause-themed image name, made POSSIBLE_GC_PAUSE the more defensible classification. The Unknown pod phase adds another layer of ambiguity — it could mean node-level communication failure rather than application-level crash. All solution confidence scores are below 0.7 because the three most diagnostic signal types (application logs, JFR data, and Prometheus metrics) are all unavailable, leaving the analysis heavily dependent on indirect indicators."
  },
  "validation_result": null
}
```

### Error example

If the diagnostic id does not exist, the API returns `404` with this shape:

```json
{
  "statusCode": 404,
  "error": "Not Found",
  "message": "No diagnostic found with id: <id>"
}
```
