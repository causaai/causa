# Kubernetes MCP Server Deployment Guide for OpenShift

## Overview

This guide provides step-by-step instructions for deploying the Kubernetes MCP (Model Context Protocol) Server to your OpenShift cluster in the `diagnostics-tool` namespace.

## What is Kubernetes MCP Server?

The Kubernetes MCP Server is a tool that provides a standardized interface for AI assistants and applications to interact with Kubernetes clusters. It allows:

- **Read-only access** to cluster resources (pods, logs, events, services)
- **Safe observation** without modification capabilities
- **Standardized API** for querying cluster state
- **Integration** with AI tools like Claude, LangChain4J, and other MCP-compatible clients

## Prerequisites

### Required Tools

1. **OpenShift CLI (oc)**
   - Download: http://mirror.openshift.com/pub/openshift-v4/clients/ocp/4.21.5/openshift-client-linux-4.21.5.tar.gz
   - Installation:
     ```bash
     wget http://mirror.openshift.com/pub/openshift-v4/clients/ocp/4.21.5/openshift-client-linux-4.21.5.tar.gz
     tar -xzf openshift-client-linux-4.21.5.tar.gz
     sudo mv oc /usr/local/bin/
     sudo chmod +x /usr/local/bin/oc
     ```

2. **Access to OpenShift Cluster**
   - Cluster API: `https://api.cluster-n7mbm.n7mbm.sandbox983.opentlc.com:6443`
   - Namespace: `diagnostics-tool`
   - Cluster-admin access required

### Cluster Information

```
OpenShift Console: https://console-openshift-console.apps.cluster-n7mbm.n7mbm.sandbox983.opentlc.com
API Server: https://api.cluster-n7mbm.n7mbm.sandbox983.opentlc.com:6443
Namespace: diagnostics-tool
```

## Files Included

1. **openshift-mcp-server-diagnostics-tool.yaml** - Kubernetes manifests for deployment
2. **deploy-mcp-server.sh** - Automated deployment script
3. **README-MCP-DEPLOYMENT.md** - This documentation

## Deployment Methods

### Method 1: Automated Deployment (Recommended)

The automated script handles all deployment steps including login, namespace verification, deployment, and health checks.

```bash
# Make the script executable
chmod +x deploy-mcp-server.sh

# Run the deployment script
./deploy-mcp-server.sh
```

The script will:
1. ✓ Check prerequisites (oc CLI)
2. ✓ Login to OpenShift cluster
3. ✓ Verify/create namespace
4. ✓ Deploy MCP server
5. ✓ Wait for deployment to be ready
6. ✓ Display deployment information
7. ✓ Test server health
8. ✓ Provide next steps

### Method 2: Manual Deployment

If you prefer manual control or need to troubleshoot:

#### Step 1: Login to OpenShift

```bash
oc login --server=https://api.cluster-n7mbm.n7mbm.sandbox983.opentlc.com:6443 \
  --token=YOUR_TOKEN_HERE \
  --insecure-skip-tls-verify=true
```

#### Step 2: Verify Namespace

```bash
# Check if namespace exists
oc get namespace diagnostics-tool

# If it doesn't exist, create it
oc create namespace diagnostics-tool
```

#### Step 3: Deploy MCP Server

```bash
# Apply the deployment configuration
oc apply -f openshift-mcp-server-diagnostics-tool.yaml -n diagnostics-tool
```

#### Step 4: Wait for Deployment

```bash
# Wait for deployment to be ready (timeout: 5 minutes)
oc wait --for=condition=available --timeout=300s \
  deployment/kubernetes-mcp-server -n diagnostics-tool
```

#### Step 5: Verify Deployment

```bash
# Check deployment status
oc get deployment kubernetes-mcp-server -n diagnostics-tool

# Check pod status
oc get pods -n diagnostics-tool -l app=kubernetes-mcp-server

# Check service
oc get service kubernetes-mcp-server -n diagnostics-tool

# Check route (external access)
oc get route kubernetes-mcp-server -n diagnostics-tool
```

## Deployment Architecture

### Components Deployed

1. **ServiceAccount**: `kubernetes-mcp-server`
   - Identity for the MCP server pod

2. **ClusterRole**: `kubernetes-mcp-server`
   - Read-only permissions for:
     - Pods (get, list, watch)
     - Pod logs (get)
     - Pod status (get)
     - Events (get, list, watch)
     - Namespaces (get, list)
     - Nodes (get, list)
     - Services (get, list)

3. **ClusterRoleBinding**: Links ServiceAccount to ClusterRole

4. **Deployment**: `kubernetes-mcp-server`
   - 1 replica
   - Node.js 20 slim image
   - Runs `npx kubernetes-mcp-server` on port 3000
   - Resource limits: 512Mi memory, 500m CPU
   - Health checks configured

5. **Service**: `kubernetes-mcp-server`
   - ClusterIP service on port 3000
   - Internal cluster access

6. **Route**: `kubernetes-mcp-server`
   - External HTTPS access
   - Edge TLS termination
   - 300s timeout for long-lived connections

### Network Architecture

```
External Clients (HTTPS)
         ↓
    OpenShift Route (TLS Edge)
         ↓
    Service (ClusterIP:3000)
         ↓
    Pod (kubernetes-mcp-server:3000)
         ↓
    Kubernetes API Server
```

## Verification and Testing

### 1. Check Deployment Status

```bash
# Get all resources
oc get all -n diagnostics-tool -l app=kubernetes-mcp-server

# Check pod logs
oc logs -n diagnostics-tool -l app=kubernetes-mcp-server

# Describe pod for detailed info
oc describe pod -n diagnostics-tool -l app=kubernetes-mcp-server
```

### 2. Test Health Endpoint

```bash
# Get the route URL
ROUTE_URL=$(oc get route kubernetes-mcp-server -n diagnostics-tool -o jsonpath='{.spec.host}')

# Test health endpoint
curl -k https://$ROUTE_URL/healthz
```

### 3. Test MCP Server Functionality

```bash
# Execute command inside the pod
oc exec -n diagnostics-tool deployment/kubernetes-mcp-server -- \
  npx -y kubernetes-mcp-server --help
```

### 4. Test from Inside Cluster

```bash
# Create a test pod
oc run test-pod --image=curlimages/curl -n diagnostics-tool -- sleep 3600

# Test internal service
oc exec -n diagnostics-tool test-pod -- \
  curl http://kubernetes-mcp-server.diagnostics-tool.svc.cluster.local:3000/healthz

# Cleanup test pod
oc delete pod test-pod -n diagnostics-tool
```

## Integration with Causa Backend

### Configuration for Causa Application

Once deployed, configure your Causa backend to use the MCP server:

#### Internal Access (Recommended for in-cluster apps)

```properties
# application.properties or environment variables
mcp.kubernetes.url=http://kubernetes-mcp-server.diagnostics-tool.svc.cluster.local:3000
mcp.kubernetes.enabled=true
```

#### External Access (For development/testing)

```properties
# Get the route URL first
# oc get route kubernetes-mcp-server -n diagnostics-tool -o jsonpath='{.spec.host}'

mcp.kubernetes.url=https://<route-url>
mcp.kubernetes.enabled=true
```

### Java/Quarkus Integration Example

```java
// In your MCP client configuration
@ConfigProperty(name = "mcp.kubernetes.url")
String mcpServerUrl;

// Use with LangChain4J MCP client
McpClient mcpClient = McpClient.builder()
    .serverUrl(mcpServerUrl)
    .build();
```

## Troubleshooting

### Pod Not Starting

```bash
# Check pod events
oc describe pod -n diagnostics-tool -l app=kubernetes-mcp-server

# Check pod logs
oc logs -n diagnostics-tool -l app=kubernetes-mcp-server --tail=100

# Common issues:
# - Image pull errors: Check network connectivity
# - CrashLoopBackOff: Check logs for application errors
# - Pending: Check resource quotas and node capacity
```

### Health Check Failures

```bash
# Check if port 3000 is accessible
oc port-forward -n diagnostics-tool deployment/kubernetes-mcp-server 3000:3000

# In another terminal
curl http://localhost:3000/healthz

# Check readiness/liveness probe configuration
oc get deployment kubernetes-mcp-server -n diagnostics-tool -o yaml | grep -A 10 "Probe"
```

### Permission Issues

```bash
# Verify ServiceAccount exists
oc get serviceaccount kubernetes-mcp-server -n diagnostics-tool

# Verify ClusterRole exists
oc get clusterrole kubernetes-mcp-server

# Verify ClusterRoleBinding
oc get clusterrolebinding kubernetes-mcp-server

# Test permissions
oc auth can-i list pods --as=system:serviceaccount:diagnostics-tool:kubernetes-mcp-server
```

### Route Not Accessible

```bash
# Check route status
oc get route kubernetes-mcp-server -n diagnostics-tool

# Check route details
oc describe route kubernetes-mcp-server -n diagnostics-tool

# Test from inside cluster first
oc run test-curl --image=curlimages/curl -n diagnostics-tool -- \
  curl http://kubernetes-mcp-server:3000/healthz
```

## Monitoring and Maintenance

### View Logs

```bash
# Real-time logs
oc logs -f -n diagnostics-tool -l app=kubernetes-mcp-server

# Last 100 lines
oc logs -n diagnostics-tool -l app=kubernetes-mcp-server --tail=100

# Logs from previous container (if crashed)
oc logs -n diagnostics-tool -l app=kubernetes-mcp-server --previous
```

### Resource Usage

```bash
# Check resource usage
oc adm top pod -n diagnostics-tool -l app=kubernetes-mcp-server

# Check resource limits
oc get deployment kubernetes-mcp-server -n diagnostics-tool -o yaml | grep -A 10 resources
```

### Scaling

```bash
# Scale up (if needed)
oc scale deployment kubernetes-mcp-server -n diagnostics-tool --replicas=2

# Scale down
oc scale deployment kubernetes-mcp-server -n diagnostics-tool --replicas=1
```

## Updating the Deployment

### Update Configuration

```bash
# Edit the YAML file
vim openshift-mcp-server-diagnostics-tool.yaml

# Apply changes
oc apply -f openshift-mcp-server-diagnostics-tool.yaml -n diagnostics-tool

# Restart deployment to pick up changes
oc rollout restart deployment/kubernetes-mcp-server -n diagnostics-tool

# Watch rollout status
oc rollout status deployment/kubernetes-mcp-server -n diagnostics-tool
```

### Update Image Version

```bash
# Update to a specific version (if available)
oc set image deployment/kubernetes-mcp-server \
  mcp-server=node:20-slim \
  -n diagnostics-tool

# Trigger rollout
oc rollout restart deployment/kubernetes-mcp-server -n diagnostics-tool
```

## Cleanup

### Remove MCP Server Deployment

```bash
# Delete all resources
oc delete -f openshift-mcp-server-diagnostics-tool.yaml -n diagnostics-tool

# Verify deletion
oc get all -n diagnostics-tool -l app=kubernetes-mcp-server
```

### Remove Namespace (Optional)

```bash
# Only if you want to remove the entire namespace
oc delete namespace diagnostics-tool
```

## Security Considerations

1. **Read-Only Access**: The MCP server has only read permissions, cannot modify cluster resources
2. **TLS Encryption**: External access is encrypted via OpenShift Route
3. **Service Account**: Uses dedicated ServiceAccount with minimal required permissions
4. **Network Policies**: Consider adding NetworkPolicies to restrict access
5. **Token Rotation**: Regularly rotate the cluster-admin token used for deployment

## Performance Tuning

### Resource Limits

Current configuration:
- Memory: 256Mi (request) / 512Mi (limit)
- CPU: 100m (request) / 500m (limit)

Adjust based on your workload:

```yaml
resources:
  requests:
    memory: "512Mi"  # Increase if needed
    cpu: "200m"
  limits:
    memory: "1Gi"
    cpu: "1000m"
```

### Timeout Configuration

For long-running MCP operations, the route timeout is set to 300s. Adjust if needed:

```yaml
annotations:
  haproxy.router.openshift.io/timeout: 600s  # 10 minutes
```

## Support and Documentation

- **Upstream Project**: https://github.com/containers/kubernetes-mcp-server
- **MCP Protocol**: https://modelcontextprotocol.io/
- **OpenShift Documentation**: https://docs.openshift.com/

## Appendix: Quick Reference Commands

```bash
# Deployment
./deploy-mcp-server.sh

# Status Check
oc get all -n diagnostics-tool -l app=kubernetes-mcp-server

# Logs
oc logs -f -n diagnostics-tool -l app=kubernetes-mcp-server

# Get Route URL
oc get route kubernetes-mcp-server -n diagnostics-tool -o jsonpath='{.spec.host}'

# Test Health
curl -k https://$(oc get route kubernetes-mcp-server -n diagnostics-tool -o jsonpath='{.spec.host}')/healthz

# Restart
oc rollout restart deployment/kubernetes-mcp-server -n diagnostics-tool

# Delete
oc delete -f openshift-mcp-server-diagnostics-tool.yaml -n diagnostics-tool
```

---

**Last Updated**: 2026-06-04  
**Version**: 1.0  
**Author**: Aakriti