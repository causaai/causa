# Causa Observability Integration - Setup Summary

## What Was Done

### 1. Metrics Implementation ✅

Updated `CausaMetrics.java` to expose all metrics required by Datadog monitors:

**Metrics exposed:**
- `causa_rca_analysis_failed` - Counter with tags: `reason`, `analysis_type`, `namespace`
- `causa_rca_analysis_duration_seconds` - Timer with tags: `analysis_type`, `namespace`
- `causa_analysis_count` - General counter for all analyses
- `causa_rca_analysis_completed_total` - Total completed analyses

**Location:** `src/main/java/com/causa/observability/metrics/CausaMetrics.java`

**Key changes:**
- Added proper tagging to match monitor query expectations
- Micrometer will automatically add `.count` and `.sum` suffixes
- Methods accept context parameters (analysisType, namespace, reason) for proper tagging

### 2. Datadog Agent Deployment ✅

Deployed Datadog Agent to OpenShift cluster for testing purposes.

**Resources created:**
- Namespace: `datadog`
- Secret: `datadog-secret` (API and App keys)
- ServiceAccount: `datadog-agent`
- ClusterRole: `datadog-agent` (permissions to list services, pods, nodes)
- ClusterRoleBinding: `datadog-agent`
- DaemonSet: `datadog-agent` (3 pods running, one per node)

**Configuration:**
- Prometheus scraping enabled (`DD_PROMETHEUS_SCRAPE_ENABLED=true`)
- Service endpoint discovery enabled (`DD_PROMETHEUS_SCRAPE_SERVICE_ENDPOINTS=true`)
- Site: `us5.datadoghq.com`
- Host networking enabled for node-level visibility

**Status:**
```bash
$ oc get pods -n datadog
NAME                  READY   STATUS    RESTARTS   AGE
datadog-agent-5q2qv   1/1     Running   0          5m
datadog-agent-f968p   1/1     Running   0          5m
datadog-agent-fz7r8   1/1     Running   0          5m
```

### 3. Application Build & Deployment (In Progress) ⏳

**Current status:**
- Building new container image with updated metrics: `quay.io/pingupta/irb:causa-observability-latest`
- Will be deployed to OpenShift `pinky` namespace once build completes
- Deployment configuration: `deployment/kubernetes/overlays/pinky/`

### 4. Documentation Created ✅

Created comprehensive documentation:

1. **DATADOG-AGENT-INSTALLATION.md** - Step-by-step installation guide
   - All commands used to deploy Datadog Agent
   - Troubleshooting common issues
   - Security considerations
   - Testing vs production notes

2. **TESTING-DATADOG-INTEGRATION.md** - End-to-end testing guide
   - Complete workflow from agent deployment to metrics verification
   - Test alert examples
   - Verification checklist
   - Troubleshooting scenarios

3. **DATADOG-SETUP.md** - Architecture and setup overview
   - Current status of integration
   - What's working vs what's needed
   - Metrics reference
   - Monitor descriptions

## Architecture

```
┌─────────────────────┐      ┌──────────────────┐      ┌─────────────────┐
│   Causa App         │      │  Datadog Agent   │      │  Datadog Cloud  │
│   (pinky namespace) │      │  (datadog ns)    │      │  (us5)          │
│                     │      │                  │      │                 │
│ Exposes /q/metrics  │─────>│ Scrapes metrics  │─────>│ Evaluates       │
│ - causa_rca_*       │      │ every 15s via    │      │ monitors and    │
│ - causa_analysis_*  │      │ Prometheus API   │      │ alerts          │
└─────────────────────┘      └──────────────────┘      └─────────────────┘
```

## Next Steps

### Immediate (Once Build Completes)

1. **Deploy updated Causa app:**
   ```bash
   cd deployment/kubernetes/overlays/pinky
   oc apply -k .
   ```

2. **Verify metrics endpoint:**
   ```bash
   POD=$(oc get pods -n pinky -l app.kubernetes.io/name=causa-backend -o jsonpath='{.items[0].metadata.name}')
   oc exec -n pinky $POD -- curl -s http://localhost:8080/q/metrics | grep causa
   ```

3. **Generate test metrics:**
   ```bash
   CAUSA_ROUTE=$(oc get route ocp-causa-backend -n pinky -o jsonpath='{.spec.host}')
   
   curl -X POST https://$CAUSA_ROUTE/api/v1/webhooks/alerts \
     -H "Content-Type: application/json" \
     -d '{
       "alerts": [{
         "status": "firing",
         "labels": {
           "alertname": "TestHighCPU",
           "severity": "critical",
           "namespace": "test-app",
           "pod": "test-pod-123"
         },
         "annotations": {
           "description": "Test alert for metrics generation"
         }
       }]
     }'
   ```

4. **Verify Datadog Agent is scraping:**
   ```bash
   oc logs -n datadog -l app=datadog-agent | grep -i causa
   ```

5. **Check metrics in Datadog UI:**
   - Go to https://app.us5.datadoghq.com
   - Metrics → Explorer
   - Search for `causa`
   - Should see: `causa.rca.analysis.failed.count`, `causa.rca.analysis.duration.seconds.sum`, etc.

6. **Verify monitors show data:**
   - Monitors → Manage Monitors
   - Search for "Causa"
   - Check that monitors show "OK" or "Alert" status (not "No Data")

### Testing Workflow

Follow the complete testing guide: `docs/observability/TESTING-DATADOG-INTEGRATION.md`

**Key verification points:**
- [ ] Metrics endpoint returns Prometheus-formatted metrics
- [ ] Metrics have correct names and tags
- [ ] Datadog Agent discovers and scrapes Causa service
- [ ] Metrics appear in Datadog UI within 5-10 minutes
- [ ] Monitors transition from "No Data" to evaluating thresholds

### Production Deployment Notes

**For client environments:**
- Clients will have their own Datadog Agent already deployed
- Causa only needs to:
  1. Expose metrics at `/q/metrics` (already configured)
  2. Have Prometheus annotations on service (already configured)
  3. Provide Datadog API credentials for monitor creation
- No need to deploy/manage Datadog Agent in client environments

## Monitor Configuration

**Monitors created via API:**

1. **Causa RCA Generation Failure**
   - Query: `causa_rca_analysis_failed.count` by `reason`, `analysis_type`, `namespace`
   - Alerts on individual failures

2. **Causa RCA Generation Latency High**
   - Query: `causa_rca_analysis_duration_seconds.sum` by `analysis_type`, `namespace`
   - Alerts on slow analyses

3. **Causa RCA - High Failure Rate**
   - Query: `causa_rca_analysis_failed.count`
   - Alerts on spike in failures

4. **Causa RCA Success Rate Low**
   - Query: `causa_rca_analysis_failed.count`
   - Alerts on low success percentage

5. **Causa RCA - No Analysis Generated**
   - Query: `causa_analysis_count.count`
   - Alerts when no analyses in time window

6. **Causa RCA - Slow Analysis Trend**
   - Query: `causa_rca_analysis_duration_seconds.sum`
   - Alerts on degrading performance over time

**Monitor config files:** `src/main/resources/monitors/*/monitor-config.json`

## Troubleshooting

### Common Issues

**Issue:** Metrics endpoint returns 404
**Solution:** Verify `quarkus-micrometer-registry-prometheus` dependency in pom.xml

**Issue:** Datadog Agent pods in CrashLoopBackOff
**Solution:** Ensure privileged SCC is granted: `oc adm policy add-scc-to-user privileged -z datadog-agent -n datadog`

**Issue:** Monitors show "No Data"
**Solution:** 
1. Generate metrics by sending test alerts
2. Wait 5-10 minutes for propagation
3. Verify metric names match monitor queries
4. Check Datadog Agent is scraping: `oc logs -n datadog -l app=datadog-agent | grep causa`

**Issue:** Metrics show 0 values
**Solution:** No RCA analyses have run yet - send test alerts to trigger analysis

## Metrics Tag Reference

### Tags Used

| Tag | Description | Example Values | Used By Metrics |
|-----|-------------|----------------|-----------------|
| `app` | Application identifier | `causa` | All metrics |
| `reason` | Failure reason | `llm_error`, `data_unavailable`, `timeout` | `causa_rca_analysis_failed` |
| `analysis_type` | Type of analysis | `pod_crash`, `high_cpu`, `oom` | `causa_rca_analysis_failed`, `causa_rca_analysis_duration_seconds` |
| `namespace` | Kubernetes namespace | `production`, `staging`, `test-app` | `causa_rca_analysis_failed`, `causa_rca_analysis_duration_seconds` |

### Metric Naming Conventions

Micrometer (Prometheus) → Datadog metric name transformations:
- `causa_rca_analysis_failed` → `causa.rca.analysis.failed.count`
- `causa_rca_analysis_duration_seconds` → `causa.rca.analysis.duration.seconds.sum`
- Underscores become dots
- Counters get `.count` suffix
- Timers get `.sum`, `.count`, `.max` suffixes

## Current Status

✅ **Completed:**
- Metrics implementation with proper tags
- Datadog Agent deployment
- Monitor creation via API
- Comprehensive documentation

⏳ **In Progress:**
- Building updated container image with new metrics
- Deployment to OpenShift

🔜 **Next:**
- Verify metrics flow end-to-end
- Generate test data
- Confirm monitors evaluate correctly
- Document any adjustments needed

## Datadog Credentials (Testing)

- API Key: `e68f2e4dd1d5a01f5499fd9ce3436ce1`
- App Key: `ddapp_bivIqE2nLFbG6QPD3MmggP0nGtKl3KMfet`
- Site: `us5.datadoghq.com`
- Dashboard: https://app.us5.datadoghq.com

## Reference Links

- Causa route: `oc get route ocp-causa-backend -n pinky -o jsonpath='{.spec.host}'`
- Metrics endpoint: `http://ocp-causa-backend.pinky.svc.cluster.local:8080/q/metrics`
- Health endpoint: `http://ocp-causa-backend.pinky.svc.cluster.local:8080/q/health`
- Alert webhook: `https://<route>/api/v1/webhooks/alerts`
- Integration API: `https://<route>/api/config/integration/datadog/*`
