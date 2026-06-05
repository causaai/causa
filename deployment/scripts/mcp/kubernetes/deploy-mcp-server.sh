#!/bin/bash

##############################################################################
# Kubernetes MCP Server Deployment Script for OpenShift
#
# This script deploys the Kubernetes MCP Server to an OpenShift cluster
# in the 'diagnostics-tool' namespace.
#
# Prerequisites:
# - oc CLI installed and configured
# - Access to OpenShift cluster with cluster-admin privileges
# - Network connectivity to OpenShift API server
# - Configuration file: mcp-config.local.sh (copy from mcp-config.sh)
#
# Usage:
#   1. Create your config: cp mcp-config.sh mcp-config.local.sh
#   2. Update mcp-config.local.sh with your cluster details
#   3. Run: ./deploy-mcp-server.sh
##############################################################################

set -e  # Exit on any error

# Get the directory where this script is located
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Load configuration from local config file if it exists
if [ -f "$SCRIPT_DIR/mcp-config.local.sh" ]; then
    source "$SCRIPT_DIR/mcp-config.local.sh"
    echo "✓ Loaded configuration from mcp-config.local.sh"
elif [ -f "$SCRIPT_DIR/mcp-config.sh" ]; then
    source "$SCRIPT_DIR/mcp-config.sh"
    echo "⚠ Using default mcp-config.sh - please create mcp-config.local.sh with your values"
else
    echo "✗ Configuration file not found. Please create mcp-config.local.sh"
    exit 1
fi

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration (can be overridden by config file)
NAMESPACE="${NAMESPACE:-diagnostics-tool}"
DEPLOYMENT_FILE="openshift-mcp-server-diagnostics-tool.yaml"
MCP_SERVER_NAME="kubernetes-mcp-server"
OPENSHIFT_API="${OPENSHIFT_API:-}"
OPENSHIFT_TOKEN="${OPENSHIFT_TOKEN:-}"

##############################################################################
# Helper Functions
##############################################################################

print_header() {
    echo -e "\n${BLUE}========================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}========================================${NC}\n"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

print_info() {
    echo -e "${BLUE}ℹ $1${NC}"
}

check_prerequisites() {
    print_header "Checking Prerequisites"
    
    # Check if oc is installed
    if ! command -v oc &> /dev/null; then
        print_error "oc CLI is not installed. Please install it first."
        echo "Download from: http://mirror.openshift.com/pub/openshift-v4/clients/ocp/4.21.5/openshift-client-linux-4.21.5.tar.gz"
        exit 1
    fi
    print_success "oc CLI is installed"
    
    # Check if deployment file exists
    if [ ! -f "$DEPLOYMENT_FILE" ]; then
        print_error "Deployment file '$DEPLOYMENT_FILE' not found in current directory"
        exit 1
    fi
    print_success "Deployment file found"
    
    # Validate required configuration variables
    if [ -z "$OPENSHIFT_API" ]; then
        print_error "OPENSHIFT_API is not set. Please configure it in mcp-config.local.sh/mcp-config.sh"
        echo "Example: OPENSHIFT_API=\"https://api.your-cluster.example.com:6443\""
        exit 1
    fi
    print_success "OPENSHIFT_API is configured"
    
    if [ -z "$OPENSHIFT_TOKEN" ]; then
        print_error "OPENSHIFT_TOKEN is not set. Please set it as an environment variable"
        echo "Example: export OPENSHIFT_TOKEN=\"your-token-here\""
        echo "Or get a token: oc create token cluster-admin -n openshift-config --duration=24h"
        exit 1
    fi
    print_success "OPENSHIFT_TOKEN is configured"
}

login_to_openshift() {
    print_header "Logging into OpenShift Cluster"
    
    print_info "Logging in to: $OPENSHIFT_API"
    
    # Configure TLS verification behavior for oc login.
    # By default, TLS verification is enforced. To skip verification (e.g. for
    # local/non-production clusters without a proper CA bundle), set SKIP_TLS_VERIFY=true.
    local oc_login_args=(--server="$OPENSHIFT_API" --token="$OPENSHIFT_TOKEN")
    
    if [ "${SKIP_TLS_VERIFY:-false}" = "true" ]; then
        print_warning "SKIP_TLS_VERIFY=true - proceeding with insecure TLS (certificate verification disabled)"
        oc_login_args+=(--insecure-skip-tls-verify=true)
    else
        print_info "TLS certificate verification enabled (use SKIP_TLS_VERIFY=true to disable for non-production)"
    fi
    
    if oc login "${oc_login_args[@]}"; then
        print_success "Successfully logged into OpenShift cluster"
    else
        print_error "Failed to login to OpenShift cluster"
        exit 1
    fi
}

verify_namespace() {
    print_header "Verifying Namespace"
    
    if oc get namespace "$NAMESPACE" &> /dev/null; then
        print_success "Namespace '$NAMESPACE' exists"
    else
        print_warning "Namespace '$NAMESPACE' does not exist. Creating it..."
        if oc create namespace "$NAMESPACE"; then
            print_success "Namespace '$NAMESPACE' created"
        else
            print_error "Failed to create namespace '$NAMESPACE'"
            exit 1
        fi
    fi
}

deploy_mcp_server() {
    print_header "Deploying Kubernetes MCP Server"
    
    print_info "Applying deployment configuration..."
    if oc apply -f "$DEPLOYMENT_FILE" -n "$NAMESPACE"; then
        print_success "Deployment configuration applied"
    else
        print_error "Failed to apply deployment configuration"
        exit 1
    fi
}

wait_for_deployment() {
    print_header "Waiting for Deployment to be Ready"
    
    print_info "Waiting for deployment to be ready (this may take 2-3 minutes)..."
    
    if oc wait --for=condition=available --timeout=300s deployment/"$MCP_SERVER_NAME" -n "$NAMESPACE"; then
        print_success "Deployment is ready"
    else
        print_error "Deployment failed to become ready within timeout"
        print_info "Checking pod status..."
        oc get pods -n "$NAMESPACE" -l app="$MCP_SERVER_NAME"
        print_info "Checking pod logs..."
        oc logs -n "$NAMESPACE" -l app="$MCP_SERVER_NAME" --tail=50
        exit 1
    fi
}

get_deployment_info() {
    print_header "Deployment Information"
    
    # Get route URL
    ROUTE_URL=$(oc get route "$MCP_SERVER_NAME" -n "$NAMESPACE" -o jsonpath='{.spec.host}' 2>/dev/null || echo "")
    
    if [ -n "$ROUTE_URL" ]; then
        print_success "MCP Server Route: https://$ROUTE_URL"
        print_info "Internal Service URL: http://$MCP_SERVER_NAME.$NAMESPACE.svc.cluster.local:3000"
    else
        print_warning "Route not found. Service is only accessible internally."
        print_info "Internal Service URL: http://$MCP_SERVER_NAME.$NAMESPACE.svc.cluster.local:3000"
    fi
    
    echo ""
    print_info "Deployment Status:"
    oc get deployment "$MCP_SERVER_NAME" -n "$NAMESPACE"
    
    echo ""
    print_info "Pod Status:"
    oc get pods -n "$NAMESPACE" -l app="$MCP_SERVER_NAME"
    
    echo ""
    print_info "Service Status:"
    oc get service "$MCP_SERVER_NAME" -n "$NAMESPACE"
}

test_mcp_server() {
    print_header "Testing MCP Server"
    
    ROUTE_URL=$(oc get route "$MCP_SERVER_NAME" -n "$NAMESPACE" -o jsonpath='{.spec.host}' 2>/dev/null || echo "")
    
    if [ -n "$ROUTE_URL" ]; then
        print_info "Testing MCP server health endpoint..."
        
        if curl -k -s -o /dev/null -w "%{http_code}" "https://$ROUTE_URL/healthz" | grep -q "200"; then
            print_success "MCP Server is responding to health checks"
        else
            print_warning "Health check returned non-200 status. Server may still be initializing."
        fi
    else
        print_info "Skipping external health check (no route available)"
    fi
    
    # Check pod logs
    print_info "Recent pod logs:"
    oc logs -n "$NAMESPACE" -l app="$MCP_SERVER_NAME" --tail=20
}

print_next_steps() {
    print_header "Next Steps"
    
    ROUTE_URL=$(oc get route "$MCP_SERVER_NAME" -n "$NAMESPACE" -o jsonpath='{.spec.host}' 2>/dev/null || echo "")
    
    echo "1. Verify the MCP server is working:"
    if [ -n "$ROUTE_URL" ]; then
        echo "   curl -k https://$ROUTE_URL/healthz"
    fi
    echo "   oc logs -n $NAMESPACE -l app=$MCP_SERVER_NAME"
    echo ""
    echo "2. Configure your Causa application to use the MCP server:"
    if [ -n "$ROUTE_URL" ]; then
        echo "   External URL: https://$ROUTE_URL"
    fi
    echo "   Internal URL: http://$MCP_SERVER_NAME.$NAMESPACE.svc.cluster.local:3000"
    echo ""
    echo "3. Test MCP server functionality:"
    echo "   oc exec -n $NAMESPACE deployment/$MCP_SERVER_NAME -- npx -y kubernetes-mcp-server --help"
    echo ""
    echo "4. Monitor the deployment:"
    echo "   oc get all -n $NAMESPACE -l app=$MCP_SERVER_NAME"
    echo ""
    echo "5. View detailed pod information:"
    echo "   oc describe pod -n $NAMESPACE -l app=$MCP_SERVER_NAME"
}

cleanup_on_error() {
    print_error "Deployment failed. Cleaning up..."
    oc delete -f "$DEPLOYMENT_FILE" -n "$NAMESPACE" --ignore-not-found=true
    exit 1
}

##############################################################################
# Main Execution
##############################################################################

main() {
    print_header "Kubernetes MCP Server Deployment"
    
    # Set error trap
    trap cleanup_on_error ERR
    
    # Execute deployment steps
    check_prerequisites
    login_to_openshift
    verify_namespace
    deploy_mcp_server
    wait_for_deployment
    get_deployment_info
    test_mcp_server
    print_next_steps
    
    print_header "Deployment Complete"
    print_success "Kubernetes MCP Server has been successfully deployed!"
}

# Run main function
main

