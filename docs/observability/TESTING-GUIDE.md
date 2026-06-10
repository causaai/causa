# Causa RCA Observability - Testing Guide

## Overview

This guide provides step-by-step instructions to test the Causa RCA observability integration as described in `observability-rca-architecture.md`. Since the actual Causa backend doesn't yet expose RCA metrics, we use a mock Python application that simulates the metrics endpoint.

## Architecture Being Tested

```
Fake Causa App (Python Flask)
    ↓
/metrics endpoint (OpenMetrics format)
    ↓
Datadog Agent (OpenMetrics scraping)
    ↓
Datadog Platform
    ↓
Monitors & Alerts
```

## Prerequisites

### Required Tools
- OpenShift CLI (`oc`)
- Docker or Podman
- curl
- bash

### Required Credentials
```bash
export DD_API_KEY='f76ff07199a38562e22d1d3c09781068'
export DD_APP_KEY='ddapp_qQi640r6QhzPaaZtDYmKbXVlUiZR0QwGdI'
export DD_API_KEY='your-datadog-api-key'
export DD_APP_KEY='your-datadog-app-key'
export DD_SITE='us5.datadoghq.com'  # or your Datadog site
```

### OpenShift Access
```bash
# Login to your OpenShift cluster
oc login --server=https://your-cluster:6443
```

## Testing Steps

### Step 1: Setup Datadog Agent (One-time Setup)

**This is part of the permanent Causa infrastructure, not the temporary simulation.**

The Datadog Agent needs to be configured to scrape OpenMetrics from Causa applications.

```bash
# Set your Datadog credentials
export DD_API_KEY='your-datadog-api-key'
export DD_APP_KEY='your-datadog-app-key'
export DD_SITE='us5.datadoghq.com'  # or your Datadog site

# Run the Datadog Agent setup script
cd src/main/java/com/causa/observability/datadog/scripts
./setup-datadog-agent.sh
```

**What this does:**
1. Creates `openshift-operators` namespace
2. Creates Datadog secret with API keys
3. Installs Datadog Operator
4. Applies OpenShift SCC permissions
5. Deploys Datadog Agent with OpenMetrics scraping enabled

**Configuration used:**
- Agent config: `src/main/java/com/causa/observability/datadog/agent/datadog-agent-causa-openmetrics.yaml`
- Enables Prometheus scraping with `serviceEndpoints: true`
- Configures agent to scrape metrics from all services

**Expected Output:**
```
✓ Namespace openshift-operators ready
✓ Datadog secret created
✓ Datadog Operator is running
✓ SCC permissions applied
✓ Datadog Agent deployed
```

**Verify Agent is Running:**
```bash
oc get pods -n openshift-operators -l agent.datadoghq.com/component=agent
```

---

### Step 2: Deploy the Mock Causa Application (Temporary Simulation)

**This is temporary - it simulates the metrics that the real Causa backend will expose.**

The mock application (`src/main/resources/working-demo/app.py`) simulates Causa RCA metrics.

```bash
cd src/main/resources/working-demo

# Run the simplified deployment script
./simple-deploy.sh
```

**What this does:**
1. Builds a Docker image with the Flask app
2. Pushes it to OpenShift internal registry
3. Deploys the app to `causa-datadog` namespace
4. Creates Kubernetes Service with Datadog annotations for metrics scraping
5. Creates Datadog monitors using the main codebase scripts

**Expected Output:**
```
✓ docker found
✓ Logged in as system:serviceaccount:...
✓ Image built
✓ Image pushed
✓ Application deployed
✓ Datadog monitors created
```

### Step 3: Verify Application Deployment

```bash
# Check pod status
oc get pods -n causa-datadog

# Expected output:
# NAME                              READY   STATUS    RESTARTS   AGE
# fake-causa-app-xxxxxxxxxx-xxxxx   1/1     Running   0          2m
```

```bash
# Check application logs
oc logs -n causa-datadog -l app=fake-causa --tail=50

# Expected output:
# Starting Fake Causa Application
#   Namespace: causa-datadog
#   Workload: auth-cache
#   Pod: auth-cache-7fb7ddf54c-tqr9w
#   Listening on: 0.0.0.0:9081
```

### Step 4: Test Metrics Endpoint Locally

```bash
# Port forward to access the app
oc port-forward -n causa-datadog svc/fake-causa-service 9081:9081
```

In another terminal:

```bash
# Test health endpoint
curl http://localhost:9081/health

# Expected output:
# {
#   "status": "healthy",
#   "app": "fake-causa",
#   "namespace": "causa-datadog",
#   "workload": "auth-cache",
#   "pod": "auth-cache-7fb7ddf54c-tqr9w"
# }
```

```bash
# Test metrics endpoint
curl http://localhost:9081/metrics

# Expected output (OpenMetrics format):
# # HELP causa_rca_analysis_available RCA analysis available for pod
# # TYPE causa_rca_analysis_available gauge
# causa_rca_analysis_available{analysis_id="2178940",namespace="causa-datadog",pod="auth-cache-7fb7ddf54c-tqr9w",workload="auth-cache"} 1.0
# 
# # HELP pod_high_memory_count_total Number of pods with high memory detected
# # TYPE pod_high_memory_count_total counter
# pod_high_memory_count_total{container="auth-cache",namespace="causa-datadog",pod="auth-cache-7fb7ddf54c-tqr9w",workload="auth-cache"} 1.0
```

### Step 5: Trigger RCA Events

#### Manual Trigger (Single Event)

```bash
curl http://localhost:9081/trigger-event

# Expected output:
# {
#   "status": "event triggered",
#   "analysis_id": 2178941,
#   "pod": "auth-cache-7fb7ddf54c-tqr9w",
#   "namespace": "causa-datadog",
#   "workload": "auth-cache",
#   "analysis_url": "/causa/api/v1/analysis/2178941"
# }
```

#### Auto-Trigger (Continuous Events)

```bash
# Enable automatic event generation every 30 seconds
curl http://localhost:9081/auto-trigger

# Expected output:
# {
#   "status": "auto-trigger enabled",
#   "interval": "30 seconds"
# }
```

### Step 6: Verify Metrics in Datadog

#### Check Metrics Explorer

1. Go to Datadog → Metrics → Explorer
2. Search for: `causa_rca_analysis_available`
3. You should see metrics with labels:
   - `analysis_id`
   - `namespace`
   - `workload`
   - `pod`

#### Verify Metric Labels

```bash
# Query Datadog API to verify metrics
curl -X GET "https://api.${DD_SITE}/api/v1/metrics/causa_rca_analysis_available" \
  -H "DD-API-KEY: ${DD_API_KEY}" \
  -H "DD-APPLICATION-KEY: ${DD_APP_KEY}"
```

### Step 7: Test Datadog Monitors

The deployment script creates 4 monitors:

#### 1. RCA Analysis Available Monitor

**Query:**
```
avg(last_5m):avg:causa_rca_analysis_available{namespace:causa-datadog} > 0
```

**Test:**
```bash
# Trigger an event
curl http://localhost:9081/trigger-event

# Wait 2-3 minutes for Datadog to scrape metrics
# Check monitor status in Datadog UI
```

**Expected Alert:**
```
New IBM Runtime Intelligence Analysis available for pod auth-cache-7fb7ddf54c-tqr9w
Analysis ID: 2178941
Namespace: causa-datadog
Workload: auth-cache
URL: /causa/api/v1/analysis/2178941
```

#### 2. RCA Generation Failure Monitor

**Query:**
```
avg(last_5m):avg:causa_rca_analysis_failed_total{namespace:causa-datadog}.as_rate() > 0
```

**Note:** This metric is not yet implemented in the mock app. To test:

```bash
# You would need to modify app.py to add failure metrics
# See "Extending the Mock App" section below
```

#### 3. RCA Generation Latency Monitor

**Query:**
```
avg(last_5m):avg:causa_rca_analysis_duration_seconds.quantile{quantile:0.95,namespace:causa-datadog} > 30
```

**Note:** This metric is not yet implemented in the mock app.

#### 4. RCA Analysis Completed Monitor

**Query:**
```
avg(last_5m):avg:causa_analysis_count_total{namespace:causa-datadog}.as_rate() > 0
```

**Test:**
```bash
# Enable auto-trigger
curl http://localhost:9081/auto-trigger

# Wait 2-3 minutes
# Check monitor in Datadog - should show increasing rate
```

### Step 8: Verify Datadog Agent Scraping

```bash
# Check Datadog agent logs
oc logs -n openshift-operators -l agent.datadoghq.com/component=agent --tail=100 | grep causa

# Expected output:
# [INFO] Scraping metrics from http://fake-causa-service.causa-datadog.svc:9081/metrics
# [INFO] Found metric: causa_rca_analysis_available
# [INFO] Found metric: pod_high_memory_count_total
```

```bash
# Check agent status
oc exec -n openshift-operators -it $(oc get pods -n openshift-operators -l agent.datadoghq.com/component=agent -o name | head -1) -- agent status

# Look for:
# OpenMetrics Check
# ==================
#   Instance ID: openmetrics:fake-causa-service [OK]
#   Total Runs: 42
#   Metrics: 3
```

## Metrics Comparison: Mock vs Architecture

### Currently Implemented in Mock App

| Metric | Type | Labels | Status |
|--------|------|--------|--------|
| `causa_rca_analysis_available` | Gauge | pod, namespace, workload, analysis_id | ✅ Implemented |
| `pod_high_memory_count_total` | Counter | pod, namespace, workload, container | ✅ Implemented |
| `causa_analysis_count_total` | Counter | namespace, workload | ✅ Implemented |

### Missing from Mock App (Per Architecture)

| Metric | Type | Labels | Status |
|--------|------|--------|--------|
| `causa_rca_analysis_available` | Gauge | analysis_id, analysis_url, namespace, workload, pod, issue_type, severity, status | ⚠️ Partial (missing issue_type, severity, status, analysis_url) |
| `causa_rca_analysis_duration_seconds` | Histogram | analysis_type, status, namespace | ❌ Not implemented |
| `causa_rca_analysis_failed_total` | Counter | reason, analysis_type, namespace | ❌ Not implemented |
| `causa_rca_analysis_completed_total` | Counter | analysis_type, severity, namespace | ❌ Not implemented |

## Extending the Mock App

To fully test the architecture, update `app.py`:

```python
from prometheus_client import Counter, Gauge, Histogram

# Add missing metrics
rca_analysis_duration = Histogram(
    'causa_rca_analysis_duration_seconds',
    'RCA analysis duration',
    ['analysis_type', 'status', 'namespace']
)

rca_analysis_failed = Counter(
    'causa_rca_analysis_failed_total',
    'RCA analysis failures',
    ['reason', 'analysis_type', 'namespace']
)

rca_analysis_completed = Counter(
    'causa_rca_analysis_completed_total',
    'RCA analysis completed',
    ['analysis_type', 'severity', 'namespace']
)

# Update rca_analysis_available with all labels
rca_analysis_available = Gauge(
    'causa_rca_analysis_available',
    'RCA analysis available',
    ['analysis_id', 'analysis_url', 'namespace', 'workload', 
     'pod', 'issue_type', 'severity', 'status']
)
```

## Troubleshooting

### Metrics Not Appearing in Datadog

```bash
# 1. Check if app is running
oc get pods -n causa-datadog

# 2. Check app logs
oc logs -n causa-datadog -l app=fake-causa

# 3. Test metrics endpoint
oc port-forward -n causa-datadog svc/fake-causa-service 9081:9081
curl http://localhost:9081/metrics

# 4. Check Datadog agent configuration
oc get datadogagent datadog -n openshift-operators -o yaml

# 5. Check agent logs
oc logs -n openshift-operators -l agent.datadoghq.com/component=agent | grep -i error
```

### Monitor Not Triggering

```bash
# 1. Verify metrics exist in Datadog
# Go to Metrics Explorer and search for metric name

# 2. Check monitor query syntax
# Go to Monitors → Edit Monitor → Check query

# 3. Verify metric labels match monitor query
curl http://localhost:9081/metrics | grep causa_rca_analysis_available

# 4. Check monitor evaluation frequency
# Monitors evaluate every 1 minute by default
```

### Image Build Failures

```bash
# If using Apple Silicon Mac, ensure AMD64 build
docker buildx build --platform linux/amd64 -t fake-causa-app:latest .

# Check architecture
docker inspect fake-causa-app:latest | grep Architecture
```

## Testing Checklist

- [ ] Mock app deployed successfully
- [ ] Metrics endpoint accessible
- [ ] Metrics in OpenMetrics format
- [ ] Manual event trigger works
- [ ] Auto-trigger works
- [ ] Metrics appear in Datadog Metrics Explorer
- [ ] Datadog agent scraping metrics
- [ ] Monitor created in Datadog
- [ ] Monitor triggers on event
- [ ] Alert message includes analysis_id
- [ ] Alert message includes namespace/workload/pod

## Next Steps

Once testing is complete with the mock app:

1. **Implement in Causa Backend:**
   - Add Micrometer metrics to Quarkus app
   - Expose metrics via `/q/metrics`
   - Use same metric names and labels

2. **Update Monitors:**
   - Point monitors to real Causa metrics
   - Adjust thresholds based on production data

3. **Add Missing Metrics:**
   - Implement histogram for duration
   - Add failure counter
   - Add completed counter with severity

4. **Production Deployment:**
   - Deploy Causa with metrics enabled
   - Configure Datadog agent to scrape Causa
   - Test end-to-end flow

## References

- Architecture: `docs/observability/observability-rca-architecture.md`
- Mock App: `src/main/resources/working-demo/app.py`
- Deploy Script: `src/main/resources/working-demo/deploy.sh`
- Monitor Configs: `src/main/java/com/causa/observability/datadog/monitors/`
- Datadog Agent Config: `src/main/java/com/causa/observability/datadog/agent/datadog-agent-causa-openmetrics.yaml`

---

**Made with Bob**