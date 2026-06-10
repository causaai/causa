#!/bin/bash
set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Defaults
DEFAULT_IMAGE="quay.io/rh-ee-shesaxen/postgres-pgvector:17"
DEFAULT_OPERATOR_VERSION="1.29.1"
DEFAULT_OPERATOR_RELEASE_BRANCH="release-1.29"
DEFAULT_CLUSTER_TYPE="kind"
NAMESPACE="diagnostics-tool"

# Variables
IMAGE_NAME="${IMAGE_NAME:-$DEFAULT_IMAGE}"
OPERATOR_VERSION="${OPERATOR_VERSION:-$DEFAULT_OPERATOR_VERSION}"
OPERATOR_RELEASE_BRANCH="${OPERATOR_RELEASE_BRANCH:-$DEFAULT_OPERATOR_RELEASE_BRANCH}"
CLUSTER_TYPE="${CLUSTER_TYPE:-$DEFAULT_CLUSTER_TYPE}"
TERMINATE_MODE=false

# Validate operator version matches release branch
validate_operator_version() {
    local version="$1"
    local branch="$2"
    
    # Extract major.minor from version (e.g., "1.29.1" -> "1.29")
    local version_major_minor
    version_major_minor=$(echo "$version" | awk -F. '{print $1"."$2}')
    
    # Extract major.minor from branch (e.g., "release-1.29" -> "1.29")
    local branch_major_minor
    branch_major_minor=$(echo "$branch" | sed 's/release-//')
    
    if [ "$version_major_minor" != "$branch_major_minor" ]; then
        print_error "Operator version mismatch!"
        print_error "  OPERATOR_VERSION: $version (major.minor: $version_major_minor)"
        print_error "  OPERATOR_RELEASE_BRANCH: $branch (expected: release-$version_major_minor)"
        print_error ""
        print_error "The operator version must match the release branch."
        print_error "For version $version, use OPERATOR_RELEASE_BRANCH=release-$version_major_minor"
        exit 1
    fi
}

# Print functions
print_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
print_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
print_error() { echo -e "${RED}[ERROR]${NC} $1"; }
print_header() {
    echo -e "${BLUE}================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}================================${NC}"
}

# Usage
usage() {
    cat << EOF
Usage: $0 [OPTIONS]

Deploy PostgreSQL with pgvector using CloudNativePG operator

Options:
  -i IMAGE    PostgreSQL image (default: $DEFAULT_IMAGE)
  -c TYPE     Cluster type: kind or openshift (default: $DEFAULT_CLUSTER_TYPE)
  -t          Terminate mode - remove all PostgreSQL and operator resources
  -h          Show this help

Environment Variables:
  OPERATOR_VERSION          CloudNativePG operator version (default: $DEFAULT_OPERATOR_VERSION)
  OPERATOR_RELEASE_BRANCH   Release branch (default: $DEFAULT_OPERATOR_RELEASE_BRANCH)
                            Must match the version's major.minor (e.g., release-1.29 for 1.29.x)

Examples:
  # Deploy on Kind cluster
  $0 -c kind

  # Deploy on OpenShift cluster
  $0 -c openshift

  # Deploy with custom image
  $0 -c kind -i quay.io/myorg/postgres-pgvector:17

  # Deploy with specific operator version (branch auto-validated)
  OPERATOR_VERSION=1.29.2 $0 -c kind

  # Deploy with different operator version line
  OPERATOR_VERSION=1.30.0 OPERATOR_RELEASE_BRANCH=release-1.30 $0 -c kind

  # Terminate everything
  $0 -t

Note: The script validates that OPERATOR_VERSION matches OPERATOR_RELEASE_BRANCH
      to prevent 404 errors from version/branch mismatches.

EOF
    exit "${1:-0}"
}

# Parse arguments
while getopts "i:c:th" opt; do
    case ${opt} in
        i) IMAGE_NAME="$OPTARG" ;;
        c) CLUSTER_TYPE="$OPTARG" ;;
        t) TERMINATE_MODE=true ;;
        h) usage 0 ;;
        \?) print_error "Invalid option: -$OPTARG"; usage 1 ;;
    esac
done

# Validate cluster type
if [[ "$CLUSTER_TYPE" != "kind" && "$CLUSTER_TYPE" != "openshift" ]]; then
    print_error "Invalid cluster type: $CLUSTER_TYPE"
    print_error "Must be 'kind' or 'openshift'"
    exit 1
fi

# Check kubectl
if ! command -v kubectl &> /dev/null; then
    print_error "kubectl is not installed"
    exit 1
fi

# Check cluster connectivity
print_info "Checking cluster connectivity..."
if ! kubectl cluster-info &> /dev/null; then
    print_error "Cannot connect to Kubernetes cluster"
    exit 1
fi
print_info "✓ Connected to cluster"
echo ""

# Validate operator version matches release branch (skip in terminate mode)
if [ "$TERMINATE_MODE" = false ]; then
    validate_operator_version "$OPERATOR_VERSION" "$OPERATOR_RELEASE_BRANCH"
fi

# Handle terminate mode
if [ "$TERMINATE_MODE" = true ]; then
    print_header "Terminate Mode - Cleaning Up PostgreSQL Resources"
    
    CLUSTER_NAME="diagnostics-tool-db"
    
    # Delete PostgreSQL cluster
    print_info "Deleting PostgreSQL cluster ${CLUSTER_NAME}..."
    kubectl delete cluster "${CLUSTER_NAME}" -n "${NAMESPACE}" --ignore-not-found=true
    
    # Wait for cluster deletion
    print_info "Waiting for cluster to be deleted..."
    kubectl wait --for=delete cluster/"${CLUSTER_NAME}" -n "${NAMESPACE}" --timeout=60s 2>/dev/null || true
    
    # Delete any remaining PVCs
    print_info "Deleting PostgreSQL PVCs..."
    kubectl delete pvc -l cnpg.io/cluster="${CLUSTER_NAME}" -n "${NAMESPACE}" --ignore-not-found=true
    
    # Delete CloudNativePG operator based on cluster type
    print_info "Deleting CloudNativePG operator..."
    if [ "$CLUSTER_TYPE" = "openshift" ]; then
        # Delete OLM subscription
        kubectl delete subscription cloudnative-pg -n openshift-operators --ignore-not-found=true
        kubectl delete csv -n openshift-operators -l operators.coreos.com/cloudnative-pg.openshift-operators --ignore-not-found=true
    else
        # Delete direct installation
        OPERATOR_URL="https://raw.githubusercontent.com/cloudnative-pg/cloudnative-pg/${OPERATOR_RELEASE_BRANCH}/releases/cnpg-${OPERATOR_VERSION}.yaml"
        kubectl delete -f "${OPERATOR_URL}" --ignore-not-found=true --wait=false
    fi
    
    print_info "✓ PostgreSQL cleanup complete"
    echo ""
    print_info "PostgreSQL cluster and operator have been removed"
    exit 0
fi

# Display configuration
print_header "Deployment Configuration"
print_info "Cluster Type: ${CLUSTER_TYPE}"
print_info "Image:        ${IMAGE_NAME}"
print_info "Namespace:    ${NAMESPACE}"
echo ""

# Install CloudNativePG operator
print_header "Installing CloudNativePG Operator"

if [ "$CLUSTER_TYPE" = "openshift" ]; then
    # OpenShift: Use OLM (Operator Lifecycle Manager)
    print_info "Installing operator via OLM for OpenShift..."
    
    # Check if operator is already installed
    if kubectl get csv -n openshift-operators 2>/dev/null | grep -q cloudnative-pg; then
        print_info "✓ CloudNativePG operator already installed"
    else
        print_info "Creating operator subscription..."
        cat <<EOF | kubectl apply -f -
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata:
  name: cloudnative-pg
  namespace: openshift-operators
spec:
  channel: stable-v1
  name: cloudnative-pg
  source: certified-operators
  sourceNamespace: openshift-marketplace
EOF
        
        if [ $? -eq 0 ]; then
            print_info "✓ Operator subscription created"
            print_info "Waiting for operator CSV to be ready (this may take 1-2 minutes)..."
            
            # Wait for CSV to appear first
            CSV_FOUND=false
            for i in {1..20}; do
                if kubectl get csv -n openshift-operators 2>/dev/null | grep -q cloudnative-pg; then
                    CSV_FOUND=true
                    CSV_NAME=$(kubectl get csv -n openshift-operators -o name 2>/dev/null | grep cloudnative-pg | head -1)
                    print_info "✓ CSV found: ${CSV_NAME}"
                    break
                fi
                echo -n "."
                sleep 6
            done
            echo ""
            
            if [ "$CSV_FOUND" = true ]; then
                # Now wait for CSV to be ready
                kubectl wait --for=jsonpath='{.status.phase}'=Succeeded \
                    ${CSV_NAME} -n openshift-operators \
                    --timeout=120s 2>/dev/null || \
                    print_warn "CSV not ready yet, will check operator deployment..."
            else
                print_warn "CSV not found after 120 seconds, will check operator deployment..."
            fi
        else
            print_error "Failed to create operator subscription"
            exit 1
        fi
    fi
else
    # Kind/Kubernetes: Use direct manifest installation
    print_info "Installing operator v${OPERATOR_VERSION} for Kind..."
    
    OPERATOR_URL="https://raw.githubusercontent.com/cloudnative-pg/cloudnative-pg/${OPERATOR_RELEASE_BRANCH}/releases/cnpg-${OPERATOR_VERSION}.yaml"
    
    # Check if operator already exists and cleanup if needed
    if kubectl get namespace cnpg-system &> /dev/null; then
        print_warn "Operator namespace exists, cleaning up old installation..."
        kubectl delete -f "${OPERATOR_URL}" --ignore-not-found=true --wait=false 2>/dev/null || true
        print_info "Waiting for cleanup to complete..."
        sleep 5
    fi
    
    # Install operator directly
    print_info "Installing operator..."
    if kubectl create -f "${OPERATOR_URL}"; then
        print_info "✓ Operator installed"
    else
        print_error "Failed to install operator"
        exit 1
    fi
fi

# Wait for operator based on cluster type
print_info "Waiting for operator deployment to be ready..."
if [ "$CLUSTER_TYPE" = "openshift" ]; then
    OPERATOR_NS="openshift-operators"
else
    OPERATOR_NS="cnpg-system"
fi

# Wait for deployment to exist first
DEPLOYMENT_FOUND=false
for i in {1..20}; do
    if kubectl get deployment -n ${OPERATOR_NS} 2>/dev/null | grep -q cnpg-controller-manager; then
        DEPLOYMENT_FOUND=true
        print_info "✓ Operator deployment found in namespace ${OPERATOR_NS}"
        break
    fi
    echo -n "."
    sleep 6
done
echo ""

if [ "$DEPLOYMENT_FOUND" = true ]; then
    # Now wait for deployment to be available
    kubectl wait --for=condition=Available --timeout=180s \
        deployment/cnpg-controller-manager -n ${OPERATOR_NS} 2>/dev/null && \
        print_info "✓ Operator is ready" || \
        print_warn "Operator deployment not ready yet, continuing anyway..."
else
    print_warn "Operator deployment not found after 120 seconds"
    print_warn "Checking if operator pods are running..."
    kubectl get pods -n ${OPERATOR_NS} 2>/dev/null || true
fi
echo ""

# Deploy PostgreSQL cluster
print_header "Deploying PostgreSQL Cluster"

# Create namespace first
print_info "Creating namespace ${NAMESPACE}..."
kubectl create namespace "${NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

# Deploy PostgreSQL cluster
print_info "Deploying PostgreSQL cluster..."
if kubectl apply -f deployment/postgres/postgres-cluster.yaml -n "${NAMESPACE}"; then
    print_info "✓ PostgreSQL cluster deployed"
else
    print_error "Failed to deploy PostgreSQL cluster"
    exit 1
fi
echo ""

# Wait for cluster to be ready
print_header "Waiting for PostgreSQL Cluster"
CLUSTER_NAME="diagnostics-tool-db"

# Wait for pod to be created (20 attempts x 6 seconds = 120 seconds)
print_info "Waiting for pod to be created..."
for i in {1..20}; do
    if kubectl get pod "${CLUSTER_NAME}-1" -n "${NAMESPACE}" &> /dev/null; then
        print_info "✓ Pod ${CLUSTER_NAME}-1 created"
        break
    fi
    print_info "Attempt $i/20: Waiting for pod..."
    sleep 6
done

if ! kubectl get pod "${CLUSTER_NAME}-1" -n "${NAMESPACE}" &> /dev/null; then
    print_error "Pod not created after 120 seconds"
    print_error "Check: kubectl get cluster -n ${NAMESPACE}"
    exit 1
fi

# Wait for pod to be ready (20 attempts x 6 seconds = 120 seconds)
print_info "Waiting for pod to be ready..."
for i in {1..20}; do
    POD_STATUS=$(kubectl get pod "${CLUSTER_NAME}-1" -n "${NAMESPACE}" -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}' 2>/dev/null)
    if [ "$POD_STATUS" = "True" ]; then
        print_info "✓ Pod is ready"
        break
    fi
    PHASE=$(kubectl get pod "${CLUSTER_NAME}-1" -n "${NAMESPACE}" -o jsonpath='{.status.phase}' 2>/dev/null || echo "Unknown")
    print_info "Attempt $i/20: Pod phase - $PHASE"
    sleep 6
done

if [ "$POD_STATUS" != "True" ]; then
    print_warn "Pod not ready after 120 seconds"
    print_warn "Check: kubectl get pods -n ${NAMESPACE}"
fi
echo ""

# Display connection info
print_header "Connection Information"
SECRET_NAME="diagnostics-tool-db-app"

# Wait for secret to be created (6 attempts x 5 seconds = 30 seconds)
print_info "Waiting for credentials secret..."
WAIT_COUNT=0
MAX_WAIT=6
while [ $WAIT_COUNT -lt $MAX_WAIT ]; do
    if kubectl get secret "${SECRET_NAME}" -n "${NAMESPACE}" &> /dev/null; then
        break
    fi
    print_info "Attempt $((WAIT_COUNT + 1))/$MAX_WAIT: Waiting for secret..."
    sleep 5
    WAIT_COUNT=$((WAIT_COUNT + 1))
done

if kubectl get secret "${SECRET_NAME}" -n "${NAMESPACE}" &> /dev/null; then
    print_info "✓ Credentials stored in secret: ${SECRET_NAME}"
    print_info ""
    print_info "Connection details:"
    print_info "  Host:     diagnostics-tool-db-rw.${NAMESPACE}.svc.cluster.local"
    print_info "  Port:     5432"
    print_info "  Database: diagnostics-tool-db"
    print_info "  User:     causa_backend"
    print_info ""
    print_info "Get password:"
    print_info "  kubectl get secret ${SECRET_NAME} -n ${NAMESPACE} -o jsonpath='{.data.password}' | base64 -d"
else
    print_warn "Secret not found after 30 seconds"
    print_warn "The cluster may still be initializing. Check status with:"
    print_warn "  kubectl get pods -n ${NAMESPACE}"
    print_warn "  kubectl get secrets -n ${NAMESPACE}"
fi
echo ""

# Summary
print_header "Deployment Complete"
print_info "✓ CloudNativePG operator installed"
print_info "✓ PostgreSQL cluster deployed"
print_info "✓ pgvector extension enabled"
print_info ""
print_info "Verify:"
print_info "  kubectl get cluster -n ${NAMESPACE}"
print_info "  kubectl get pods -n ${NAMESPACE}"
echo ""

exit 0

