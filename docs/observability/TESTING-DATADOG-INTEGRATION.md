# Testing Datadog Integration - Complete Step-by-Step Guide

This guide walks you through deploying the Datadog Agent and testing the complete observability flow.

## Prerequisites

- ✅ Causa app deployed in OpenShift (pinky namespace)
- ✅ Datadog account with API and App keys
- ✅ `oc` CLI configured and logged in
- ✅ Datadog monitors already created via `/api/config/integration/datadog/connect`

## Architecture Overview

```
┌─────────────────┐      ┌──────────────────┐      ┌─────────────────┐
│   Causa App     │      │  Datadog Agent   │      │  Datadog Cloud  │
│                 │      │                  │      │                 │
│ Exposes metrics │─────>│ Scrapes metrics  │─────>│ Evaluates       │
│ at /q/metrics   │      │ every 15s        │      │ monitors        │
└─────────────────┘      └──────────────────┘      └─────────────────┘
```

---

## Step 1: Deploy Datadog Agent

### Option A: Using Datadog Operator (Recommended)

**1.1. Install Datadog Operator**

```bash
# Switch to admin context (if needed)
oc login -u kubeadmin

# Create datadog namespace
oc create namespace datadog

# Install Datadog Operator from OperatorHub
# Go to OpenShift Console -> OperatorHub -> Search "Datadog" -> Install
# Or use CLI:
cat <<EOF | oc apply -f -
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata:
  name: datadog-operator
  namespace: openshift-operators
spec:
  channel: stable
  name: datadog-operator
  source: certified-operators
  sourceNamespace: openshift-marketplace
EOF
```

**1.2. Create Datadog Agent Secret**

```bash
# Create secret with your Datadog credentials
oc create secret generic datadog-secret \
  --from-literal=api-key=e68f2e4dd1d5a01f5499fd9ce3436ce1 \
  --from-literal=app-key=ddapp_bivIqE2nLFbG6QPD3MmggP0nGtKl3KMfet \
  -n datadog
```

**1.3. Deploy DatadogAgent Resource**

```bash
cat <<EOF | oc apply -f -
apiVersion: datadoghq.com/v2alpha1
kind: DatadogAgent
metadata:
  name: datadog
  namespace: datadog
spec:
  global:
    credentials:
      apiSecret:
        secretName: datadog-secret
        keyName: api-key
      appSecret:
        secretName: datadog-secret
        keyName: app-key
    site: us5.datadoghq.com
    clusterName: pinky-openshift
  
  features:
    # Enable Prometheus/OpenMetrics scraping
    prometheusScrape:
      enabled: true
      enableServiceEndpoints: true
    
    # Enable cluster monitoring
    kubeStateMetricsCore:
      enabled: true
    
    # Enable log collection (optional)
    logCollection:
      enabled: false
  
  override:
    clusterAgent:
      replicas: 1
EOF
```

### Option B: Using DaemonSet (Alternative)

<details>
<summary>Click to expand DaemonSet installation</summary>

```bash
cat <<EOF | oc apply -f -
apiVersion: v1
kind: ServiceAccount
metadata:
  name: datadog-agent
  namespace: datadog
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: datadog-agent
rules:
  - apiGroups: [""]
    resources:
      - services
      - events
      - endpoints
      - pods
      - nodes
      - componentstatuses
    verbs: ["get", "list", "watch"]
  - apiGroups: [""]
    resources:
      - configmaps
    resourceNames: ["datadog-leader-election"]
    verbs: ["get", "update"]
  - apiGroups: [""]
    resources:
      - configmaps
    verbs: ["create"]
  - nonResourceURLs: ["/version", "/healthz", "/metrics"]
    verbs: ["get"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: datadog-agent
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: datadog-agent
subjects:
  - kind: ServiceAccount
    name: datadog-agent
    namespace: datadog
---
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: datadog-agent
  namespace: datadog
spec:
  selector:
    matchLabels:
      app: datadog-agent
  template:
    metadata:
      labels:
        app: datadog-agent
      annotations:
        # Enable Prometheus autodiscovery
        ad.datadoghq.com/datadog-agent.check_names: '["openmetrics"]'
        ad.datadoghq.com/datadog-agent.init_configs: '[{}]'
        ad.datadoghq.com/datadog-agent.instances: |
          [{
            "openmetrics_endpoint": "http://%%host%%:%%port%%/q/metrics",
            "namespace": "causa",
            "metrics": ["causa_rca_.*"]
          }]
    spec:
      serviceAccountName: datadog-agent
      containers:
      - name: datadog-agent
        image: gcr.io/datadoghq/agent:7
        env:
          - name: DD_API_KEY
            value: e68f2e4dd1d5a01f5499fd9ce3436ce1
          - name: DD_SITE
            value: us5.datadoghq.com
          - name: DD_KUBERNETES_KUBELET_HOST
            valueFrom:
              fieldRef:
                fieldPath: status.hostIP
          - name: DD_PROMETHEUS_SCRAPE_ENABLED
            value: "true"
          - name: DD_PROMETHEUS_SCRAPE_SERVICE_ENDPOINTS
            value: "true"
        volumeMounts:
          - name: dockersocket
            mountPath: /var/run/docker.sock
          - name: procdir
            mountPath: /host/proc
            readOnly: true
          - name: cgroups
            mountPath: /host/sys/fs/cgroup
            readOnly: true
      volumes:
        - name: dockersocket
          hostPath:
            path: /var/run/docker.sock
        - name: procdir
          hostPath:
            path: /proc
        - name: cgroups
          hostPath:
            path: /sys/fs/cgroup
EOF
```
</details>

---

## Step 2: Verify Datadog Agent Deployment

**2.1. Check Datadog Agent Pods**

```bash
# Wait for agent pods to be ready
oc get pods -n datadog -w

# Expected output (after 2-3 minutes):
# NAME                                    READY   STATUS    RESTARTS   AGE
# datadog-agent-xxxxx                     3/3     Running   0          2m
# datadog-cluster-agent-xxxxxxxxx-xxxxx   1/1     Running   0          2m
```

**2.2. Check Agent Logs**

```bash
# Check agent logs for errors
oc logs -n datadog -l app.kubernetes.io/component=agent --tail=50

# Look for successful startup messages like:
# "Datadog Agent is now running"
# "Successfully connected to Datadog"
```

**2.3. Verify Agent Status**

```bash
# Get agent status
POD=$(oc get pods -n datadog -l app.kubernetes.io/component=agent -o jsonpath='{.items[0].metadata.name}')
oc exec -n datadog $POD -- agent status

# Look for:
# - "API Keys status: API key ending with xxx: API Key valid"
# - "Running Checks" section should show openmetrics check
```

---

## Step 3: Verify Causa Metrics Endpoint

**3.1. Wait for Latest Causa Deployment**

```bash
# Check if the latest image with metrics is deployed
oc get pods -n pinky | grep causa

# Wait for new pods to be Running
oc rollout status deployment/ocp-causa-backend -n pinky
```

**3.2. Test Metrics Endpoint**

```bash
# Get a running pod
POD=$(oc get pods -n pinky -l app.kubernetes.io/name=causa-backend --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}')

# Check metrics endpoint
oc exec -n pinky $POD -- curl -s http://localhost:8080/q/metrics | head -50

# Expected output:
# # HELP jvm_memory_used_bytes The amount of used memory
# # TYPE jvm_memory_used_bytes gauge
# ...
# # HELP causa_rca_analysis_completed_total Total number of completed RCA analyses
# # TYPE causa_rca_analysis_completed_total counter
# causa_rca_analysis_completed_total{app="causa"} 0.0
```

**3.3. Verify Prometheus Annotations**

```bash
# Check service annotations for Prometheus scraping
oc get service ocp-causa-backend -n pinky -o yaml | grep -A 3 annotations

# Should see:
#   prometheus.io/scrape: "true"
#   prometheus.io/port: "8080"
#   prometheus.io/path: "/q/metrics"
```

---

## Step 4: Configure Datadog to Scrape Causa

**4.1. Create OpenMetrics Configuration for Causa**

```bash
cat <<EOF | oc apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: datadog-causa-openmetrics
  namespace: datadog
  labels:
    app: datadog
data:
  causa-openmetrics.yaml: |
    ad_identifiers:
      - ocp-causa-backend
    init_config:
    instances:
      - openmetrics_endpoint: http://ocp-causa-backend.pinky.svc.cluster.local:8080/q/metrics
        namespace: "causa"
        metrics:
          - causa_rca_.*
        tags:
          - app:causa
          - env:pinky
EOF
```

**4.2. Restart Datadog Agent to Pick Up Config**

```bash
# If using Operator
oc rollout restart deployment/datadog-cluster-agent -n datadog
oc rollout restart daemonset/datadog-agent -n datadog

# Wait for restart
oc rollout status daemonset/datadog-agent -n datadog
```

---

## Step 5: Generate Test Metrics

Since Causa RCA metrics only increment when analyses run, we need to trigger an RCA analysis.

**5.1. Send Test Alert to Causa**

```bash
# Get the Causa route
CAUSA_ROUTE=$(oc get route ocp-causa-backend -n pinky -o jsonpath='{.spec.host}')

# Send a test alert (this will trigger an RCA analysis)
curl -X POST https://$CAUSA_ROUTE/api/v1/webhooks/alerts \
  -H "Content-Type: application/json" \
  -d '{
    "alerts": [{
      "status": "firing",
      "labels": {
        "alertname": "TestHighCPU",
        "severity": "critical",
        "namespace": "test-app",
        "pod": "test-pod-12345",
        "container": "main"
      },
      "annotations": {
        "summary": "Test alert for Datadog integration",
        "description": "CPU usage is above 90% - THIS IS A TEST"
      },
      "startsAt": "2026-06-16T10:00:00Z"
    }]
  }'

# Expected response: 200 OK
```

**5.2. Verify Metrics Were Generated**

```bash
# Check metrics again (should now have non-zero values)
POD=$(oc get pods -n pinky -l app.kubernetes.io/name=causa-backend --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}')

oc exec -n pinky $POD -- curl -s http://localhost:8080/q/metrics | grep causa_rca

# Expected output (with actual values):
# causa_rca_analysis_completed_total{app="causa"} 1.0
# OR
# causa_rca_analysis_failed_total{app="causa"} 1.0
# causa_rca_analysis_duration_seconds_count{app="causa"} 1.0
# causa_rca_analysis_duration_seconds_sum{app="causa"} 2.5
```

---

## Step 6: Verify Datadog is Scraping Metrics

**6.1. Check Datadog Agent is Discovering Causa**

```bash
# Check agent logs for Causa service discovery
oc logs -n datadog -l app.kubernetes.io/component=agent --tail=100 | grep -i "causa\|openmetrics"

# Look for messages like:
# "Scheduling check openmetrics for causa-backend"
# "Successfully scraped metrics from http://ocp-causa-backend.pinky.svc.cluster.local:8080/q/metrics"
```

**6.2. Check OpenMetrics Check Status**

```bash
POD=$(oc get pods -n datadog -l app.kubernetes.io/component=agent -o jsonpath='{.items[0].metadata.name}')

oc exec -n datadog $POD -- agent status | grep -A 20 "openmetrics"

# Should show:
#   openmetrics (X.X.X)
#   ----------------
#     Instance ID: openmetrics:causa [OK]
#     Total Runs: XX
#     Metric Samples: Last Run: XX, Total: XXX
```

---

## Step 7: Verify Data in Datadog UI

**7.1. Check Metrics Explorer**

1. Login to Datadog: https://app.us5.datadoghq.com
2. Go to **Metrics → Explorer**
3. Search for: `causa.rca`
4. You should see:
   - `causa.rca.analysis.completed.total`
   - `causa.rca.analysis.failed.total`
   - `causa.rca.analysis.duration.seconds`

**7.2. Check Monitors**

1. Go to **Monitors → Manage Monitors**
2. Search for: `Causa`
3. Your 6 monitors should appear:
   - ✅ Causa RCA Generation Failure
   - ✅ Causa RCA Generation Latency High
   - ✅ Causa RCA - High Failure Rate
   - ✅ Causa RCA Success Rate Low
   - ✅ Causa RCA - No Analysis Generated
   - ✅ Causa RCA - Slow Analysis Trend

4. Click on a monitor to view:
   - Status should be **OK** or **Alert** (not "No Data")
   - Graph should show metric data

**7.3. Create Test Dashboard**

```bash
# Optional: Create a simple dashboard via API to visualize metrics
curl -X POST "https://api.us5.datadoghq.com/api/v1/dashboard" \
  -H "DD-API-KEY: e68f2e4dd1d5a01f5499fd9ce3436ce1" \
  -H "DD-APPLICATION-KEY: ddapp_bivIqE2nLFbG6QPD3MmggP0nGtKl3KMfet" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Causa RCA Metrics",
    "widgets": [{
      "definition": {
        "type": "timeseries",
        "requests": [{
          "q": "sum:causa.rca.analysis.completed.total{*}"
        }],
        "title": "Total RCA Analyses Completed"
      }
    }],
    "layout_type": "ordered"
  }'
```

---

## Step 8: Continuous Testing

**8.1. Send Multiple Test Alerts**

```bash
# Send 5 test alerts to generate metrics
for i in {1..5}; do
  curl -X POST https://$CAUSA_ROUTE/api/v1/webhooks/alerts \
    -H "Content-Type: application/json" \
    -d "{
      \"alerts\": [{
        \"status\": \"firing\",
        \"labels\": {
          \"alertname\": \"TestAlert$i\",
          \"severity\": \"critical\",
          \"namespace\": \"test-app\",
          \"pod\": \"test-pod-$i\"
        },
        \"annotations\": {
          \"description\": \"Test alert $i\"
        }
      }]
    }"
  echo "Sent alert $i"
  sleep 10
done
```

**8.2. Monitor Metrics in Real-Time**

```bash
# Watch metrics update
watch -n 5 "oc exec -n pinky $POD -- curl -s http://localhost:8080/q/metrics | grep causa_rca_analysis"
```

---

## Troubleshooting

### Problem: Metrics endpoint returns 404

**Solution:**
```bash
# Check if metrics dependency is in the deployed app
oc exec -n pinky $POD -- ls -la /deployments/lib | grep micrometer

# If not found, rebuild and redeploy with metrics dependency
```

### Problem: Datadog Agent not discovering Causa

**Solution:**
```bash
# 1. Check service annotations
oc get svc ocp-causa-backend -n pinky -o yaml | grep prometheus

# 2. Ensure agent has RBAC to list services
oc auth can-i list services --as=system:serviceaccount:datadog:datadog-agent -n pinky

# 3. Manually test agent can reach Causa
oc exec -n datadog $AGENT_POD -- curl http://ocp-causa-backend.pinky.svc.cluster.local:8080/q/metrics
```

### Problem: Monitors still show "No Data"

**Solution:**
```bash
# 1. Verify metrics exist locally
oc exec -n pinky $POD -- curl -s http://localhost:8080/q/metrics | grep causa

# 2. Check Datadog Agent is scraping
oc exec -n datadog $AGENT_POD -- agent status | grep -A 20 openmetrics

# 3. Check metric names in Datadog (they may have dots instead of underscores)
# In Datadog UI: search for "causa" in Metrics Explorer

# 4. Wait 5-10 minutes for metrics to propagate to Datadog cloud
```

### Problem: Alert webhook returns error

**Solution:**
```bash
# Check Causa logs
oc logs -n pinky deployment/ocp-causa-backend --tail=50

# Verify database is accessible
oc exec -n pinky $POD -- curl -s http://localhost:8080/q/health/ready | jq .
```

---

## Expected Timeline

- **T+0**: Deploy Datadog Agent
- **T+2 min**: Agent pods running
- **T+5 min**: Agent discovers Causa service
- **T+10 min**: Send test alerts
- **T+12 min**: Metrics visible at `/q/metrics`
- **T+15 min**: Metrics appear in Datadog Metrics Explorer
- **T+20 min**: Monitors show data and evaluate thresholds

---

## Verification Checklist

- [ ] Datadog Agent pods running (3/3 ready)
- [ ] Agent status shows "API Key valid"
- [ ] Causa `/q/metrics` endpoint returns Prometheus metrics
- [ ] `causa_rca_analysis_*` metrics exist with non-zero values
- [ ] Datadog Agent logs show successful scraping
- [ ] Metrics appear in Datadog Metrics Explorer
- [ ] Monitors show "OK" or "Alert" status (not "No Data")
- [ ] Dashboard visualizes Causa metrics

---

## Next Steps

Once everything is working:

1. **Set up Real Alerting**: Configure AlertManager to send real alerts to Causa
2. **Customize Monitors**: Adjust thresholds in Datadog monitors as needed
3. **Create Dashboards**: Build comprehensive dashboards for RCA metrics
4. **Configure LLM**: Add LLM credentials so RCA analyses actually complete
5. **Monitor Production**: Watch monitors respond to real incidents

---

## Quick Reference

### Key Endpoints
- Causa Metrics: `http://ocp-causa-backend.pinky.svc.cluster.local:8080/q/metrics`
- Causa Health: `http://ocp-causa-backend.pinky.svc.cluster.local:8080/q/health`
- Alert Webhook: `https://<route>/api/v1/webhooks/alerts`
- Integration API: `https://<route>/api/config/integration/datadog/*`

### Key Commands
```bash
# Check agent status
oc exec -n datadog $(oc get pods -n datadog -l app.kubernetes.io/component=agent -o name | head -1) -- agent status

# Check Causa metrics
oc exec -n pinky $(oc get pods -n pinky -l app.kubernetes.io/name=causa-backend -o name | head -1) -- curl http://localhost:8080/q/metrics | grep causa

# Send test alert
curl -X POST https://$(oc get route ocp-causa-backend -n pinky -o jsonpath='{.spec.host}')/api/v1/webhooks/alerts -H "Content-Type: application/json" -d '{"alerts":[{"status":"firing","labels":{"alertname":"Test","severity":"critical","namespace":"test","pod":"test-pod"}}]}'
```

### Datadog Credentials
- API Key: `e68f2e4dd1d5a01f5499fd9ce3436ce1`
- App Key: `ddapp_bivIqE2nLFbG6QPD3MmggP0nGtKl3KMfet`
- Site: `us5.datadoghq.com`
