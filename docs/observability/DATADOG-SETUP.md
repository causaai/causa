# Datadog Monitoring Setup for Causa

This guide explains how to get data flowing into your Datadog monitors.

## Overview

The Causa observability integration creates monitors in Datadog via API, but for these monitors to show data, you need:

1. ✅ Causa app exposing Prometheus metrics
2. ⚠️ Datadog Agent deployed and configured to scrape metrics
3. ⚠️ Causa actually generating RCA analyses (to emit metrics)

## Current Status

### ✅ What's Working
- Datadog API integration endpoints (`/api/config/integration/datadog/*`)
- 6 monitors created in Datadog account
- Prometheus metrics endpoint (`/q/metrics`) enabled
- Metrics classes defined for RCA tracking

### ⚠️ What's Needed

#### 1. Deploy Datadog Agent

You need to deploy the Datadog Agent to your OpenShift cluster to scrape metrics from Causa.

**Option A: Using Datadog Operator (Recommended for OpenShift)**

```bash
# Install Datadog Operator from OperatorHub
oc create namespace datadog
oc apply -f - <<EOF
apiVersion: datadoghq.com/v2alpha1
kind: DatadogAgent
metadata:
  name: datadog
  namespace: datadog
spec:
  global:
    credentials:
      apiKey: <YOUR_DD_API_KEY>
      appKey: <YOUR_DD_APP_KEY>
    site: us5.datadoghq.com
  features:
    prometheusScrape:
      enabled: true
      enableServiceEndpoints: true
EOF
```

**Option B: Using DaemonSet**

See: https://docs.datadoghq.com/containers/kubernetes/installation/

#### 2. Configure Datadog Agent to Scrape Causa Metrics

Add Prometheus scrape annotations to Causa deployment (already configured):

```yaml
annotations:
  prometheus.io/scrape: "true"
  prometheus.io/port: "8080"
  prometheus.io/path: "/q/metrics"
```

Or configure Datadog Agent with OpenMetrics check:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: datadog-agent-openmetrics
data:
  conf.yaml: |
    ad_identifiers:
      - causa-backend
    init_config:
    instances:
      - openmetrics_endpoint: http://%%host%%:8080/q/metrics
        namespace: "causa"
        metrics:
          - causa_rca_analysis_.*
```

#### 3. Generate RCA Analyses

The Causa app needs to actually perform RCA analyses for metrics to be emitted.

**Trigger an RCA analysis by:**
- Sending an alert webhook to `/api/v1/webhooks/alerts`
- Or waiting for Prometheus AlertManager to send alerts

**Example alert webhook:**
```bash
curl -X POST https://ocp-causa-backend-pinky.apps.cluster-ns4r6.ns4r6.sandbox151.opentlc.com/api/v1/webhooks/alerts \
  -H "Content-Type: application/json" \
  -d '{
    "alerts": [{
      "status": "firing",
      "labels": {
        "alertname": "HighCPU",
        "severity": "critical",
        "namespace": "my-app",
        "pod": "my-pod-12345"
      },
      "annotations": {
        "description": "CPU usage is above 90%"
      }
    }]
  }'
```

## Metrics Exposed by Causa

Once RCA analyses run, these metrics will be available at `http://ocp-causa-backend.pinky.svc.cluster.local:8080/q/metrics`:

| Metric Name | Type | Description |
|-------------|------|-------------|
| `causa_rca_analysis_completed_total` | Counter | Total number of completed RCA analyses |
| `causa_rca_analysis_failed_total` | Counter | Total number of failed RCA analyses |
| `causa_rca_analysis_duration_seconds` | Timer | Duration of RCA analysis in seconds |

## Datadog Monitors Created

The integration creates these monitors:

1. **Causa RCA Generation Failure** - Alerts on failed analyses
2. **Causa RCA Generation Latency High** - Alerts on slow analyses
3. **Causa RCA - High Failure Rate** - Alerts on failure rate spike
4. **Causa RCA Success Rate Low** - Alerts on low success rate
5. **Causa RCA - No Analysis Generated** - Alerts when no analyses in time window
6. **Causa RCA - Slow Analysis Trend** - Alerts on degrading performance

## Verification Steps

### 1. Check Metrics Endpoint
```bash
kubectl exec -n pinky deployment/ocp-causa-backend -- \
  curl http://localhost:8080/q/metrics | grep causa_rca
```

Expected output (after RCA analyses run):
```
# HELP causa_rca_analysis_completed_total Total number of completed RCA analyses
# TYPE causa_rca_analysis_completed_total counter
causa_rca_analysis_completed_total{app="causa"} 5.0
```

### 2. Check Datadog Agent is Scraping
```bash
# Check Datadog Agent logs
kubectl logs -n datadog -l app=datadog-agent | grep causa
```

### 3. Check Datadog Metrics Explorer
Go to Datadog UI → Metrics → Explorer and search for `causa.rca`

### 4. Check Monitor Status
Go to Datadog UI → Monitors and check your Causa monitors

## Troubleshooting

### Monitors showing "No Data"
- **Cause**: Datadog Agent not deployed or not scraping metrics
- **Fix**: Deploy Datadog Agent and configure it to scrape Causa metrics

### Metrics endpoint returns 404
- **Cause**: Micrometer not enabled
- **Fix**: Ensure `quarkus-micrometer-registry-prometheus` dependency is in pom.xml

### Metrics show 0 values
- **Cause**: No RCA analyses have run yet
- **Fix**: Trigger an RCA analysis via alert webhook

### Datadog Agent can't reach Causa
- **Cause**: Network policy or service misconfiguration
- **Fix**: Verify service exists and Datadog Agent can reach it

## Architecture Note

**Causa does NOT install or manage the Datadog Agent.**

The Causa observability integration is **API-only**:
- ✅ Creates/manages Datadog monitors via Datadog API
- ✅ Exposes Prometheus metrics at `/q/metrics`
- ❌ Does NOT install Datadog Agent
- ❌ Does NOT configure agent scraping

**You are responsible for:**
1. Deploying the Datadog Agent in your cluster
2. Configuring the agent to scrape Causa's `/q/metrics` endpoint
3. Ensuring network connectivity between agent and Causa

## References

- [Datadog Agent Installation](https://docs.datadoghq.com/containers/kubernetes/installation/)
- [Datadog Operator](https://docs.datadoghq.com/containers/kubernetes/operator/)
- [Prometheus Autodiscovery](https://docs.datadoghq.com/containers/kubernetes/prometheus/)
- [OpenMetrics Integration](https://docs.datadoghq.com/integrations/openmetrics/)
