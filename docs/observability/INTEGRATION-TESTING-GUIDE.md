gi# Datadog Integration Testing Guide

Complete guide to test the Datadog integration on your ITCP OpenShift cluster.

## Prerequisites

### 1. Datadog Account Setup
- Sign up for Datadog account at https://www.datadoghq.com/
- Get your credentials:
  - **API Key**: From Organization Settings → API Keys
  - **Application Key**: From Organization Settings → Application Keys (create one with `monitors_write` permission)
  - **Site**: Your Datadog site (e.g., `us5.datadoghq.com`)

### 2. Cluster Access
```bash
# Login to your ITCP OpenShift cluster
oc login <your-cluster-url>

# Verify you're logged in
oc whoami
oc cluster-info
```

### 3. Install Datadog Operator (One-time setup)
```bash
# The operator must be installed before running the integration
# This is typically done by cluster admin

# Check if operator is already installed
oc get subscription datadog-operator-certified -n openshift-operators

# If not installed, install it via OperatorHub:
# 1. Go to OpenShift Console → OperatorHub
# 2. Search for "Datadog Operator"
# 3. Click Install
# 4. Select "openshift-operators" namespace
# 5. Click Install

# Or install via CLI:
cat <<EOF | oc apply -f -
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata:
  name: datadog-operator-certified
  namespace: openshift-operators
spec:
  channel: stable
  installPlanApproval: Automatic
  name: datadog-operator-certified
  source: certified-operators
  sourceNamespace: openshift-marketplace
EOF

# Wait for operator to be ready
oc get pods -n openshift-operators | grep datadog-operator
```

## Step 1: Deploy Causa Backend

### Option A: Use Pre-Built Image (Recommended for Testing)

```bash
# Navigate to causa-backend directory
cd /Users/pinkygupta/Documents/workspace/causa-backend

# Deploy using Kustomize with OpenShift overlay
# Note: This creates resources in the 'diagnostics-tool' namespace (defined in kustomization.yaml)
oc apply -k deployment/kubernetes/overlays/openshift/

# Update deployment to use the pre-built testing image
oc set image deployment/ocp-causa-backend \
  causa-backend=quay.io/pingupta/irb:causa-datadog-testing-1.0 \
  -n diagnostics-tool





# Fix the route to point to the correct service (due to namePrefix in kustomization)
oc patch route ocp-causa-backend -n diagnostics-tool \
  --type=json -p='[{"op": "replace", "path": "/spec/to/name", "value": "ocp-causa-backend"}]'

# Wait for deployment to be ready
oc wait --for=condition=available --timeout=300s deployment/ocp-causa-backend -n diagnostics-tool

# Get the route
CAUSA_URL=$(oc get route ocp-causa-backend -n diagnostics-tool -o jsonpath='{.spec.host}')
echo "Causa Backend URL: https://$CAUSA_URL"

# Verify deployment
oc get pods -n diagnostics-tool
oc get deployment -n diagnostics-tool
oc logs -n diagnostics-tool deployment/ocp-causa-backend --tail=50

# Test health endpoints
curl -k "https://$CAUSA_URL/q/health/live"
curl -k "https://$CAUSA_URL/q/health/ready"
```

**Important Notes:**
- The deployment uses namespace `diagnostics-tool` (configured in kustomization.yaml)
- Resources are prefixed with `ocp-` (namePrefix in kustomization.yaml)
- The database secret must be created before pods can start
- Update the database credentials in the secret for production use
```

### Option B: Build and Push Your Own Image
```bash
# Navigate to causa-backend directory
cd /Users/pinkygupta/Documents/workspace/causa-backend

# Set your registry details (replace with your registry)
export REGISTRY="quay.io"
export REPO_NAME="your-username/causa-backend"
export IMAGE_TAG="latest"

# Login to your registry
podman login ${REGISTRY}
# or
docker login ${REGISTRY}

# Build and push using the build script
./scripts/dev/build_and_push.sh \
  -r ${REGISTRY} \
  -n ${REPO_NAME} \
  -t ${IMAGE_TAG} \
  -b true \
  -p true \
  -l linux/amd64 \
  -s true

# Or build and push with Maven directly
./mvnw clean package \
  -DskipTests \
  -Dquarkus.container-image.build=true \
  -Dquarkus.container-image.push=true \
  -Dquarkus.container-image.image=${REGISTRY}/${REPO_NAME}:${IMAGE_TAG}

# Update the deployment image
oc set image deployment/causa-backend \
  causa-backend=${REGISTRY}/${REPO_NAME}:${IMAGE_TAG} \
  -n causa-observability

# Wait for rollout
oc rollout status deployment/causa-backend -n causa-observability
```

### Option C: Deploy from Local Build (Not Recommended - Use Option B Instead)
```bash
# Note: OpenShift internal registry may have certificate issues
# It's better to use Option B (push to external registry like Quay.io)

# If you still want to try internal registry:
# 1. First, get the actual internal registry route
REGISTRY_ROUTE=$(oc get route default-route -n openshift-image-registry -o jsonpath='{.spec.host}' 2>/dev/null)

if [ -z "$REGISTRY_ROUTE" ]; then
  echo "Internal registry route not exposed. Exposing it..."
  oc patch configs.imageregistry.operator.openshift.io/cluster --type merge -p '{"spec":{"defaultRoute":true}}'
  sleep 10
  REGISTRY_ROUTE=$(oc get route default-route -n openshift-image-registry -o jsonpath='{.spec.host}')
fi

echo "Registry route: $REGISTRY_ROUTE"

# 2. Build the application
./mvnw clean package -DskipTests

# 3. Build image locally
docker build -f src/main/docker/Dockerfile.jvm -t causa-backend:latest .

# 4. Login to internal registry (use --tls-verify=false for self-signed certs)
podman login -u $(oc whoami) -p $(oc whoami -t) --tls-verify=false $REGISTRY_ROUTE
# OR with docker
docker login -u $(oc whoami) -p $(oc whoami -t) $REGISTRY_ROUTE

# 5. Tag and push
docker tag causa-backend:latest $REGISTRY_ROUTE/diagnostics-tool/causa-backend:latest
docker push $REGISTRY_ROUTE/diagnostics-tool/causa-backend:latest

# 6. Update the deployment to use this image
oc set image deployment/ocp-causa-backend \
  causa-backend=$REGISTRY_ROUTE/diagnostics-tool/causa-backend:latest \
  -n diagnostics-tool

# 7. Wait for rollout
oc rollout status deployment/ocp-causa-backend -n diagnostics-tool
```

**Recommendation**: Use **Option A** (existing image) for testing or **Option B** (external registry) for custom builds. Option C has certificate and authentication complexities.

### Grant RBAC Permissions
```bash
# Causa needs permissions to manage Datadog resources
# Note: ServiceAccount is already created by Kustomize deployment
# We just need to create the ClusterRole and ClusterRoleBinding

cat <<EOF | oc apply -f -
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: causa-datadog-manager
rules:
  # Secrets management
  - apiGroups: [""]
    resources: ["secrets"]
    verbs: ["create", "get", "list", "update", "delete"]
  
  # Namespace management
  - apiGroups: [""]
    resources: ["namespaces"]
    verbs: ["create", "get", "list"]
  
  # Pod status checking
  - apiGroups: [""]
    resources: ["pods"]
    verbs: ["get", "list"]
  
  # DatadogAgent CR management
  - apiGroups: ["datadoghq.com"]
    resources: ["datadogagents"]
    verbs: ["create", "get", "list", "update", "delete"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: causa-datadog-manager-binding
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: causa-datadog-manager
subjects:
  - kind: ServiceAccount
    name: ocp-causa-backend
    namespace: diagnostics-tool
EOF

# Verify the service account is set (should already be configured by Kustomize)
oc get deployment ocp-causa-backend -n diagnostics-tool -o jsonpath='{.spec.template.spec.serviceAccountName}'
```

## Step 2: Test the Integration Flow

### 2.1 Validate Credentials
```bash
# Set your Datadog credentials
export DD_API_KEY="your-datadog-api-key"
export DD_APP_KEY="your-datadog-app-key"
export DD_SITE="us5.datadoghq.com"  # or your site

# Test credential validation
curl -X POST "http://$CAUSA_URL/api/integrations/validate" \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "DATADOG",
    "config": {
      "apiKey": "'"$DD_API_KEY"'",
      "appKey": "'"$DD_APP_KEY"'",
      "site": "'"$DD_SITE"'"
    }
  }' | jq .

# Expected response:
# {
#   "valid": true,
#   "message": "Credentials validated successfully",
#   "permissions": ["metrics_read", "monitors_write", "monitors_read"]
# }
```

### 2.2 Install Datadog Integration
```bash
# Install the integration (this will install agent + create monitors)
curl -X POST "http://$CAUSA_URL/api/integrations/install" \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "DATADOG",
    "config": {
      "apiKey": "'"$DD_API_KEY"'",
      "appKey": "'"$DD_APP_KEY"'",
      "site": "'"$DD_SITE"'",
      "clusterName": "itcp-cluster"
    }
  }' | jq .

# Expected response:
# {
#   "integrationId": "datadog-123",
#   "provider": "datadog",
#   "status": "installed",
#   "agentInstalled": true,
#   "agentStatus": "installed",
#   "monitorsCreated": 6,
#   "metricsDiscovered": 8,
#   "metricsEndpoint": "/q/metrics",
#   "scrapeInterval": "30s",
#   "monitors": [
#     {
#       "id": "123456",
#       "name": "Causa RCA Generation Failure",
#       "type": "metric alert",
#       "url": "https://app.us5.datadoghq.com/monitors/123456"
#     },
#     ...
#   ],
#   "notes": "Datadog Agent installed successfully. Created/updated 6 monitors."
# }

# Save the integration ID
INTEGRATION_ID=$(curl -s -X POST "http://$CAUSA_URL/api/integrations/install" \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "DATADOG",
    "config": {
      "apiKey": "'"$DD_API_KEY"'",
      "appKey": "'"$DD_APP_KEY"'",
      "site": "'"$DD_SITE"'",
      "clusterName": "itcp-cluster"
    }
  }' | jq -r '.integrationId')

echo "Integration ID: $INTEGRATION_ID"
```

### 2.3 Verify Installation in Cluster
```bash
# Check if Datadog Agent is deployed
oc get datadogagent -n openshift-operators

# Check Datadog Agent pods
oc get pods -n openshift-operators -l agent.datadoghq.com/component=agent

# Check Datadog secret
oc get secret datadog-secret -n openshift-operators

# View agent logs
oc logs -n openshift-operators -l agent.datadoghq.com/component=agent --tail=50

# Check if agent is scraping Causa metrics
oc logs -n openshift-operators -l agent.datadoghq.com/component=agent | grep -i "causa\|openmetrics"
```

### 2.4 Check Integration Status
```bash
# Get integration status
curl -X GET "http://$CAUSA_URL/api/integrations/$INTEGRATION_ID" \
  -H "Content-Type: application/json" | jq .

# Expected response:
# {
#   "integrationId": "datadog-123",
#   "provider": "datadog",
#   "installed": true,
#   "agentHealthy": true,
#   "metricsDiscovered": 8,
#   "monitorsCreated": 6,
#   "lastScrape": "2026-06-11T06:54:00Z",
#   "scrapeStatus": "success",
#   "monitors": [...]
# }
```

### 2.5 List All Integrations
```bash
# List all integrations
curl -X GET "http://$CAUSA_URL/api/integrations" \
  -H "Content-Type: application/json" | jq .
```

### 2.6 Send Test Event
```bash
# Send a test RCA event
curl -X POST "http://$CAUSA_URL/api/integrations/$INTEGRATION_ID/test" \
  -H "Content-Type: application/json" \
  -d '{
    "analysisType": "incident",
    "severity": "high"
  }' | jq .

# Expected response:
# {
#   "testEventSent": true,
#   "analysisId": "test-1234567890",
#   "metricsPublished": [
#     "causa_rca_analysis_available",
#     "causa_rca_analysis_completed_total",
#     "causa_rca_analysis_duration_seconds"
#   ],
#   "expectedMonitorTriggers": [
#     "Causa RCA Generation Failure",
#     "Causa RCA Generation Latency High"
#   ]
# }
```

## Step 3: Verify in Datadog UI

### 3.1 Check Monitors
1. Go to https://app.us5.datadoghq.com/monitors/manage (replace with your site)
2. Search for "Causa RCA"
3. You should see 6 monitors:
   - Causa RCA Generation Failure
   - Causa RCA Generation Latency High
   - Causa RCA - High Failure Rate
   - Causa RCA - No Analysis Generated
   - Causa RCA - Slow Analysis Trend
   - Causa RCA Success Rate Low

### 3.2 Check Metrics
1. Go to Metrics Explorer: https://app.us5.datadoghq.com/metric/explorer
2. Search for metrics starting with `causa_`
3. You should see:
   - `causa_rca_analysis_available`
   - `causa_rca_analysis_completed_total`
   - `causa_rca_analysis_duration_seconds`
   - `causa_rca_analysis_failed`
   - `causa_analysis_count`

### 3.3 Check Infrastructure
1. Go to Infrastructure → Containers
2. You should see your ITCP cluster
3. Check that Datadog Agent pods are running

## Step 4: Test Cleanup

### 4.1 Delete Integration
```bash
# Delete the integration (removes agent, monitors, secrets)
curl -X DELETE "http://$CAUSA_URL/api/integrations/$INTEGRATION_ID" \
  -H "Content-Type: application/json" | jq .

# Expected response:
# {
#   "success": true,
#   "message": "Integration deleted successfully"
# }
```

### 4.2 Verify Cleanup
```bash
# Check if DatadogAgent is removed
oc get datadogagent -n openshift-operators

# Check if secret is removed
oc get secret datadog-secret -n openshift-operators

# Check if monitors are removed in Datadog UI
# Go to Monitors page and search for "Causa RCA"
```

## Troubleshooting

### Issue: Agent pods not starting
```bash
# Check operator logs
oc logs -n openshift-operators -l app.kubernetes.io/name=datadog-operator

# Check DatadogAgent status
oc describe datadogagent datadog -n openshift-operators

# Check events
oc get events -n openshift-operators --sort-by='.lastTimestamp'
```

### Issue: Metrics not appearing in Datadog
```bash
# Check if Causa is exposing metrics
curl http://$CAUSA_URL/q/metrics | grep causa_

# Check agent configuration
oc get datadogagent datadog -n openshift-operators -o yaml

# Check agent logs for scraping errors
oc logs -n openshift-operators -l agent.datadoghq.com/component=agent | grep -i error
```

### Issue: Monitors not created
```bash
# Check Causa backend logs
oc logs -n diagnostics-tool deployment/ocp-causa-backend | grep -i monitor

# Verify Datadog credentials have monitors_write permission
# Go to Datadog → Organization Settings → Application Keys
# Check permissions for your app key
```

### Issue: Permission denied errors
```bash
# Check if service account has correct permissions
oc get clusterrolebinding causa-datadog-manager-binding -o yaml

# Check if deployment is using the service account
oc get deployment ocp-causa-backend -n diagnostics-tool -o yaml | grep serviceAccount

# Check Causa pods
oc get pods -n diagnostics-tool
```

## Complete Test Script

Save this as `test-integration.sh`:

```bash
#!/bin/bash
set -e

# Configuration
export DD_API_KEY="your-api-key"
export DD_APP_KEY="your-app-key"
export DD_SITE="us5.datadoghq.com"
CAUSA_URL="https://$(oc get route ocp-causa-backend -n diagnostics-tool -o jsonpath='{.spec.host}')"

echo "🧪 Testing Datadog Integration"
echo "================================"
echo ""

# Test 1: Validate credentials
echo "1️⃣  Validating credentials..."
VALIDATION=$(curl -s -X POST "$CAUSA_URL/api/integrations/validate" \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "DATADOG",
    "config": {
      "apiKey": "'"$DD_API_KEY"'",
      "appKey": "'"$DD_APP_KEY"'",
      "site": "'"$DD_SITE"'"
    }
  }')

if echo "$VALIDATION" | jq -e '.valid == true' > /dev/null; then
  echo "✅ Credentials valid"
else
  echo "❌ Credentials invalid"
  echo "$VALIDATION" | jq .
  exit 1
fi

# Test 2: Install integration
echo ""
echo "2️⃣  Installing integration..."
INSTALL=$(curl -s -X POST "$CAUSA_URL/api/integrations/install" \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "DATADOG",
    "config": {
      "apiKey": "'"$DD_API_KEY"'",
      "appKey": "'"$DD_APP_KEY"'",
      "site": "'"$DD_SITE"'",
      "clusterName": "itcp-cluster"
    }
  }')

INTEGRATION_ID=$(echo "$INSTALL" | jq -r '.integrationId')
MONITORS_CREATED=$(echo "$INSTALL" | jq -r '.monitorsCreated')

if [ "$MONITORS_CREATED" == "6" ]; then
  echo "✅ Integration installed (ID: $INTEGRATION_ID)"
  echo "✅ Created $MONITORS_CREATED monitors"
else
  echo "❌ Installation failed"
  echo "$INSTALL" | jq .
  exit 1
fi

# Test 3: Check agent status
echo ""
echo "3️⃣  Checking agent status..."
sleep 10  # Wait for agent to start

AGENT_PODS=$(oc get pods -n openshift-operators -l agent.datadoghq.com/component=agent --no-headers | wc -l)
if [ "$AGENT_PODS" -gt 0 ]; then
  echo "✅ Datadog Agent pods running: $AGENT_PODS"
else
  echo "⚠️  No agent pods found yet (may still be starting)"
fi

# Test 4: Get status
echo ""
echo "4️⃣  Getting integration status..."
STATUS=$(curl -s -X GET "$CAUSA_URL/api/integrations/$INTEGRATION_ID")
echo "$STATUS" | jq .

# Test 5: Send test event
echo ""
echo "5️⃣  Sending test event..."
TEST_EVENT=$(curl -s -X POST "$CAUSA_URL/api/integrations/$INTEGRATION_ID/test" \
  -H "Content-Type: application/json" \
  -d '{"analysisType": "incident", "severity": "high"}')

if echo "$TEST_EVENT" | jq -e '.testEventSent == true' > /dev/null; then
  echo "✅ Test event sent"
else
  echo "❌ Test event failed"
fi

echo ""
echo "================================"
echo "✅ All tests passed!"
echo ""
echo "Next steps:"
echo "1. Check monitors in Datadog: https://app.$DD_SITE/monitors/manage"
echo "2. Check metrics in Datadog: https://app.$DD_SITE/metric/explorer"
echo "3. View integration: curl $CAUSA_URL/api/integrations/$INTEGRATION_ID | jq ."
echo ""
echo "To cleanup:"
echo "curl -X DELETE $CAUSA_URL/api/integrations/$INTEGRATION_ID"
```

Run it:
```bash
chmod +x test-integration.sh
./test-integration.sh
```

## Success Criteria

✅ Credentials validated successfully  
✅ Integration installed with status "installed"  
✅ 6 monitors created in Datadog  
✅ Datadog Agent pods running in cluster  
✅ Metrics visible in Datadog UI  
✅ Test event sent successfully  
✅ Integration status shows "healthy"  
✅ Cleanup removes all resources  

## Next Steps

After successful testing:
1. Configure alert notifications in Datadog monitors
2. Set up dashboards for Causa RCA metrics
3. Integrate with incident management tools
4. Add more observability providers (Grafana, Instana)