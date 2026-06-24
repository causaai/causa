# Datadog Agent Installation for OpenShift - Reference Guide

This document provides the exact steps used to install the Datadog Agent in OpenShift for testing the Causa observability integration.

## Prerequisites

- OpenShift cluster access with admin privileges
- Datadog account credentials (API key and App key)
- `oc` CLI configured and logged in

## Installation Steps

### 1. Create Datadog Namespace

```bash
oc create namespace datadog
```

### 2. Create Datadog Credentials Secret

```bash
oc create secret generic datadog-secret \
  --from-literal=api-key=YOUR_DD_API_KEY \
  --from-literal=app-key=YOUR_DD_APP_KEY \
  -n datadog
```

**Example (for testing with provided credentials):**
```bash
oc create secret generic datadog-secret \
  --from-literal=api-key=e68f2e4dd1d5a01f5499fd9ce3436ce1 \
  --from-literal=app-key=ddapp_bivIqE2nLFbG6QPD3MmggP0nGtKl3KMfet \
  -n datadog
```

### 3. Create RBAC Resources

Create ServiceAccount, ClusterRole, and ClusterRoleBinding:

```bash
cat <<'EOF' | oc apply -f -
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
EOF
```

### 4. Grant Privileged SCC to ServiceAccount

The Datadog Agent requires access to host resources (docker socket, proc, cgroups) which needs privileged permissions in OpenShift:

```bash
oc adm policy add-scc-to-user privileged -z datadog-agent -n datadog
```

**Why privileged?** The agent needs:
- Access to `/var/run/docker.sock` for container metrics
- Access to `/host/proc` for process monitoring
- Access to `/host/sys/fs/cgroup` for resource metrics
- Host network and PID namespace for node-level visibility

### 5. Deploy Datadog Agent DaemonSet

```bash
cat <<'EOF' | oc apply -f -
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
    spec:
      serviceAccountName: datadog-agent
      hostPID: true
      hostNetwork: true
      containers:
      - name: datadog-agent
        image: gcr.io/datadoghq/agent:7
        env:
          # Datadog credentials
          - name: DD_API_KEY
            valueFrom:
              secretKeyRef:
                name: datadog-secret
                key: api-key
          - name: DD_SITE
            value: us5.datadoghq.com
          
          # Kubernetes integration
          - name: DD_KUBERNETES_KUBELET_HOST
            valueFrom:
              fieldRef:
                fieldPath: status.hostIP
          - name: DD_KUBERNETES_POD_NAME
            valueFrom:
              fieldRef:
                fieldPath: metadata.name
          - name: DD_HOSTNAME
            valueFrom:
              fieldRef:
                fieldPath: spec.nodeName
          
          # Prometheus scraping (for Causa metrics)
          - name: DD_PROMETHEUS_SCRAPE_ENABLED
            value: "true"
          - name: DD_PROMETHEUS_SCRAPE_SERVICE_ENDPOINTS
            value: "true"
          
          # Optional features
          - name: DD_COLLECT_KUBERNETES_EVENTS
            value: "true"
          - name: DD_LOGS_ENABLED
            value: "false"
        
        volumeMounts:
          - name: dockersocket
            mountPath: /var/run/docker.sock
            readOnly: true
          - name: procdir
            mountPath: /host/proc
            readOnly: true
          - name: cgroups
            mountPath: /host/sys/fs/cgroup
            readOnly: true
        
        resources:
          requests:
            memory: "256Mi"
            cpu: "200m"
          limits:
            memory: "512Mi"
            cpu: "500m"
      
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

### 6. Verify Installation

**Check pod status:**
```bash
oc get pods -n datadog

# Expected output:
# NAME                  READY   STATUS    RESTARTS   AGE
# datadog-agent-xxxxx   1/1     Running   0          30s
# datadog-agent-yyyyy   1/1     Running   0          30s
# datadog-agent-zzzzz   1/1     Running   0          30s
```

**Check agent logs:**
```bash
POD=$(oc get pods -n datadog -l app=datadog-agent -o jsonpath='{.items[0].metadata.name}')
oc logs -n datadog $POD --tail=50
```

**Check agent status (once running):**
```bash
POD=$(oc get pods -n datadog -l app=datadog-agent -o jsonpath='{.items[0].metadata.name}')
oc exec -n datadog $POD -- agent status
```

Look for:
- `API key ending with xxx: API Key valid`
- `Running Checks` section should show `openmetrics` or `prometheus` checks

## Configuration for Causa Integration

### Enable Prometheus Scraping for Causa Service

The Datadog Agent automatically discovers services with Prometheus annotations. Ensure your Causa service has these annotations (already configured):

```yaml
metadata:
  annotations:
    prometheus.io/scrape: "true"
    prometheus.io/port: "8080"
    prometheus.io/path: "/q/metrics"
```

### Optional: Custom OpenMetrics Configuration

If auto-discovery doesn't work, create a custom configuration:

```bash
cat <<'EOF' | oc apply -f -
apiVersion: v1
kind: ConfigMap
metadata:
  name: datadog-causa-openmetrics
  namespace: datadog
data:
  causa-openmetrics.yaml: |
    ad_identifiers:
      - ocp-causa-backend
    init_config:
    instances:
      - openmetrics_endpoint: http://ocp-causa-backend.pinky.svc.cluster.local:8080/q/metrics
        namespace: "causa"
        metrics:
          - causa_.*
        tags:
          - app:causa
          - env:pinky
EOF

# Mount this ConfigMap in the DaemonSet at /conf.d/openmetrics.d/conf.yaml
```

## Verification

### Check Metrics in Datadog

1. Login to Datadog: https://app.us5.datadoghq.com
2. Go to **Metrics → Explorer**
3. Search for: `causa`
4. You should see metrics like:
   - `causa.rca.analysis.failed.count`
   - `causa.rca.analysis.duration.seconds.sum`
   - `causa.analysis.count.count`

### Test the Flow

1. **Send test alert to Causa:**
   ```bash
   CAUSA_ROUTE=$(oc get route ocp-causa-backend -n pinky -o jsonpath='{.spec.host}')
   
   curl -X POST https://$CAUSA_ROUTE/api/v1/webhooks/alerts \
     -H "Content-Type: application/json" \
     -d '{
       "alerts": [{
         "status": "firing",
         "labels": {
           "alertname": "TestAlert",
           "severity": "critical",
           "namespace": "test-app",
           "pod": "test-pod-123"
         }
       }]
     }'
   ```

2. **Verify metrics updated:**
   ```bash
   POD=$(oc get pods -n pinky -l app.kubernetes.io/name=causa-backend -o jsonpath='{.items[0].metadata.name}')
   oc exec -n pinky $POD -- curl -s http://localhost:8080/q/metrics | grep causa
   ```

3. **Check metrics in Datadog** (wait 1-2 minutes for propagation)

## Troubleshooting

### Pods in CrashLoopBackOff

**Symptom:** Pods repeatedly crash with errors like `unable to chown /var/run/s6`

**Solution:** Ensure privileged SCC is granted:
```bash
oc adm policy add-scc-to-user privileged -z datadog-agent -n datadog
oc delete pod -n datadog -l app=datadog-agent
```

### Error: "unable to reliably determine the host name"

**Symptom:** Agent fails to start with hostname error

**Solution:** Set `DD_HOSTNAME` environment variable:
```yaml
env:
  - name: DD_HOSTNAME
    valueFrom:
      fieldRef:
        fieldPath: spec.nodeName
```

### Agent not scraping Causa metrics

**Symptom:** Metrics don't appear in Datadog

**Check list:**
1. Verify Causa service has Prometheus annotations
2. Check agent can reach Causa: `oc exec -n datadog $AGENT_POD -- curl http://ocp-causa-backend.pinky.svc.cluster.local:8080/q/metrics`
3. Check agent logs for scraping errors: `oc logs -n datadog $AGENT_POD | grep causa`
4. Verify RBAC allows listing services in `pinky` namespace

### Monitors show "No Data"

**Symptom:** Datadog monitors exist but show no data

**Check:**
1. Generate metrics by sending test alerts to Causa
2. Wait 5-10 minutes for metrics to propagate
3. Verify metric names in Datadog match monitor queries (dots vs underscores)
4. Check monitor query syntax and time range

## Cleanup

To remove the Datadog Agent:

```bash
oc delete daemonset datadog-agent -n datadog
oc delete clusterrolebinding datadog-agent
oc delete clusterrole datadog-agent
oc delete serviceaccount datadog-agent -n datadog
oc delete secret datadog-secret -n datadog
oc delete namespace datadog
```

## Environment-Specific Notes

### Testing vs Production

**Testing (this setup):**
- Deploy Datadog Agent in test cluster
- Use test Datadog account credentials
- Agent has full cluster visibility

**Production (client environment):**
- Client already has Datadog Agent deployed
- Client provides their Datadog credentials for Causa integration API
- Causa only uses Datadog REST API to create/manage monitors
- Causa exposes metrics at `/q/metrics` for client's agent to scrape

### Security Considerations

**For production deployments:**
1. Store Datadog credentials in secure secret management (Vault, Sealed Secrets, etc.)
2. Use network policies to restrict Datadog Agent communication
3. Consider using Datadog Operator instead of raw DaemonSet
4. Implement RBAC to limit agent permissions to necessary namespaces only
5. Enable audit logging for agent activities

## References

- [Datadog Agent Installation](https://docs.datadoghq.com/containers/kubernetes/installation/)
- [Datadog Operator](https://docs.datadoghq.com/containers/kubernetes/operator/)
- [Prometheus Autodiscovery](https://docs.datadoghq.com/containers/kubernetes/prometheus/)
- [OpenMetrics Integration](https://docs.datadoghq.com/integrations/openmetrics/)
- [OpenShift Security Context Constraints](https://docs.openshift.com/container-platform/latest/authentication/managing-security-context-constraints.html)
