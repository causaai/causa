#!/bin/bash

###############################################################################
# Heap OOM Validation Test Script - OpenShift/OCP
#
# Tests validation pipeline against app running in OpenShift
#
# Usage: ./scripts/test-heap-oom-oc.sh [namespace] [pod-name]
###############################################################################

set -e

# Configuration
CAUSA_NAMESPACE="${CAUSA_NAMESPACE:-diagnostics-tool}"
TARGET_NAMESPACE="${1:-heap-oom}"
TARGET_POD="${2}"
TARGET_CONTAINER="${3}"

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m'

print_header() { printf "${BLUE}========================================\n%s\n========================================${NC}\n" "$1"; }
print_success() { printf "${GREEN}✅ %s${NC}\n" "$1"; }
print_warning() { printf "${YELLOW}⚠️  %s${NC}\n" "$1"; }
print_error() { printf "${RED}❌ %s${NC}\n" "$1"; }
print_info() { printf "${BLUE}ℹ️  %s${NC}\n" "$1"; }

# Check prerequisites
check_prerequisites() {
    print_header "Checking Prerequisites"

    if ! command -v oc &> /dev/null; then
        print_error "oc CLI not installed"
        exit 1
    fi
    print_success "oc CLI installed"

    if ! command -v curl &> /dev/null; then
        print_error "curl not installed"
        exit 1
    fi
    print_success "curl installed"

    if command -v jq &> /dev/null; then
        JQ_AVAILABLE=true
        print_success "jq installed"
    else
        JQ_AVAILABLE=false
        print_warning "jq not installed (optional)"
    fi

    # Check OC login
    if ! oc whoami &> /dev/null; then
        print_error "Not logged into OpenShift"
        print_info "Run: oc login <cluster-url>"
        exit 1
    fi
    print_success "Logged in as $(oc whoami)"

    echo ""
}

# Get Causa app URL
get_causa_url() {
    print_header "Finding Causa Backend"

    print_info "Looking in namespace: ${CAUSA_NAMESPACE}"

    # Try route first
    ROUTE_HOST=$(oc get route -n "${CAUSA_NAMESPACE}" -l app.kubernetes.io/name=causa-backend -o jsonpath='{.items[0].spec.host}' 2>/dev/null || echo "")

    if [ -n "$ROUTE_HOST" ]; then
        CAUSA_URL="https://${ROUTE_HOST}"
        print_success "Found route: ${CAUSA_URL}"
    else
        # Try service
        SERVICE_NAME=$(oc get svc -n "${CAUSA_NAMESPACE}" -l app.kubernetes.io/name=causa-backend -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")

        if [ -n "$SERVICE_NAME" ]; then
            SERVICE_PORT=$(oc get svc "${SERVICE_NAME}" -n "${CAUSA_NAMESPACE}" -o jsonpath='{.spec.ports[0].port}')
            CAUSA_URL="http://${SERVICE_NAME}.${CAUSA_NAMESPACE}.svc.cluster.local:${SERVICE_PORT}"
            print_warning "No route found, using service: ${CAUSA_URL}"
            print_info "You may need to port-forward: oc port-forward svc/${SERVICE_NAME} 8080:${SERVICE_PORT} -n ${CAUSA_NAMESPACE}"
        else
            print_error "Causa backend not found in ${CAUSA_NAMESPACE}"
            print_info "Check namespace with: oc get deploy -A | grep causa"
            exit 1
        fi
    fi

    # Test connectivity
    HTTP_CODE=$(curl -k -s -o /dev/null -w "%{http_code}" "${CAUSA_URL}/q/health" 2>&1 || echo "000")

    if [ "$HTTP_CODE" = "200" ]; then
        print_success "App is healthy"
    else
        print_warning "Health check returned HTTP ${HTTP_CODE}"
        print_info "Proceeding anyway..."
    fi

    echo ""
}

# Find target pod
find_target_pod() {
    print_header "Finding Target Pod"

    if [ -z "$TARGET_POD" ]; then
        print_info "Searching for OOM pods in namespace: ${TARGET_NAMESPACE}"

        # Look for pods with OOMKilled containers
        PODS=$(oc get pods -n "${TARGET_NAMESPACE}" -o json 2>/dev/null | \
            jq -r '.items[] | select(.status.containerStatuses[]?.lastState.terminated.reason == "OOMKilled") | .metadata.name' | head -1)

        if [ -z "$PODS" ]; then
            # Look for CrashLoopBackOff pods
            PODS=$(oc get pods -n "${TARGET_NAMESPACE}" --field-selector=status.phase=Running -o json 2>/dev/null | \
                jq -r '.items[] | select(.status.containerStatuses[]?.state.waiting.reason == "CrashLoopBackOff") | .metadata.name' | head -1)
        fi

        if [ -z "$PODS" ]; then
            # Just get any running pod
            PODS=$(oc get pods -n "${TARGET_NAMESPACE}" --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
        fi

        if [ -z "$PODS" ]; then
            print_error "No pods found in namespace ${TARGET_NAMESPACE}"
            print_info "List pods with: oc get pods -n ${TARGET_NAMESPACE}"
            exit 1
        fi

        TARGET_POD="$PODS"
    fi

    print_success "Target pod: ${TARGET_POD}"

    # Get container name if not provided
    if [ -z "$TARGET_CONTAINER" ]; then
        TARGET_CONTAINER=$(oc get pod "${TARGET_POD}" -n "${TARGET_NAMESPACE}" -o jsonpath='{.spec.containers[0].name}' 2>/dev/null)
        print_info "Container: ${TARGET_CONTAINER}"
    fi

    echo ""
}

# Trigger alert
trigger_alert() {
    print_header "Triggering Alert"

    print_info "Configuration:"
    echo "  Namespace: ${TARGET_NAMESPACE}"
    echo "  Pod: ${TARGET_POD}"
    echo "  Container: ${TARGET_CONTAINER}"
    echo ""

    RESPONSE=$(curl -k -s -X POST "${CAUSA_URL}/api/v1/alerts" \
        -H 'Content-Type: application/json' \
        -d "{
          \"namespace\": \"${TARGET_NAMESPACE}\",
          \"container\": \"${TARGET_CONTAINER}\",
          \"pod\": \"${TARGET_POD}\"
        }")

    STATUS=$(echo "$RESPONSE" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)

    if [ "$STATUS" = "accepted" ]; then
        print_success "Alert accepted"

        DIAG_ID=$(echo "$RESPONSE" | grep -o 'diag_[^"]*' | head -1)
        ALERT_ID=$(echo "$RESPONSE" | grep -o 'alrt_[^"]*' | head -1)

        print_info "Alert ID: ${ALERT_ID}"
        print_info "Diagnostic ID: ${DIAG_ID}"
        echo ""

        if [ "$JQ_AVAILABLE" = true ]; then
            echo "$RESPONSE" | jq .
        else
            echo "$RESPONSE"
        fi
        echo ""
        return 0
    elif [ "$STATUS" = "rejected" ]; then
        print_warning "Alert rejected"

        if [ "$JQ_AVAILABLE" = true ]; then
            echo "$RESPONSE" | jq .
        else
            echo "$RESPONSE"
        fi
        echo ""
        return 1
    else
        print_error "Unexpected response: $RESPONSE"
        return 1
    fi
}

# Wait for completion
wait_for_completion() {
    print_header "Waiting for Processing"
    print_info "This may take 30-90s..."
    echo ""

    MAX_WAIT=150
    ELAPSED=0

    while [ $ELAPSED -lt $MAX_WAIT ]; do
        DIAG_RESPONSE=$(curl -k -s "${CAUSA_URL}/api/v1/diagnostics/${DIAG_ID}")
        DIAG_STATUS=$(echo "$DIAG_RESPONSE" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)

        if [ "$DIAG_STATUS" = "COMPLETED" ]; then
            echo ""
            print_success "Processing completed"
            echo ""
            return 0
        elif [ "$DIAG_STATUS" = "FAILED" ]; then
            echo ""
            print_error "Processing failed"
            return 1
        else
            echo -ne "\r⏳ Status: ${DIAG_STATUS} (${ELAPSED}s)    "
            sleep 5
            ELAPSED=$((ELAPSED + 5))
        fi
    done

    echo ""
    print_warning "Timeout after ${MAX_WAIT}s"
    return 1
}

# Display results
display_results() {
    print_header "Validation Results"

    RESULT=$(curl -k -s "${CAUSA_URL}/api/v1/diagnostics/${DIAG_ID}")

    ANOMALY=$(echo "$RESULT" | grep -o '"anomaly_type":"[^"]*"' | cut -d'"' -f4)
    IS_VALID=$(echo "$RESULT" | grep -o '"is_valid":[^,}]*' | cut -d':' -f2)
    CONFIDENCE=$(echo "$RESULT" | grep -o '"overall_confidence":[^,}]*' | cut -d':' -f2)

    print_info "Anomaly: ${ANOMALY}"

    if [ "$IS_VALID" = "true" ]; then
        print_success "Valid: ${IS_VALID}"
    else
        print_warning "Valid: ${IS_VALID}"
    fi

    print_info "Confidence: ${CONFIDENCE}"
    echo ""

    # PATH A
    echo "PATH A (Assertion-based):"
    A_STATUS=$(echo "$RESULT" | grep -o '"assertion_based_verdict":{[^}]*"status":"[^"]*"' | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
    A_CONF=$(echo "$RESULT" | grep -o '"assertion_based_verdict":{[^}]*"confidence":[^,}]*' | grep -o '"confidence":[^,}]*' | cut -d':' -f2 | head -1)
    echo "  Status: ${A_STATUS}"
    echo "  Confidence: ${A_CONF}"
    echo ""

    # PATH B
    echo "PATH B (Rule-based):"
    B_STATUS=$(echo "$RESULT" | jq -r '.validation.dual_validation.rule_based_verdict.status // "N/A"' 2>/dev/null || echo "N/A")
    B_SCORE=$(echo "$RESULT" | jq -r '.validation.dual_validation.rule_based_verdict.total_score // "N/A"' 2>/dev/null || echo "N/A")
    B_MAX=$(echo "$RESULT" | jq -r '.validation.dual_validation.rule_based_verdict.max_possible_score // "N/A"' 2>/dev/null || echo "N/A")
    B_REQ=$(echo "$RESULT" | jq -r '.validation.dual_validation.rule_based_verdict.required_passed // "N/A"' 2>/dev/null || echo "N/A")
    echo "  Status: ${B_STATUS}"
    echo "  Score: ${B_SCORE}/${B_MAX}"
    echo "  Required: ${B_REQ}/3"
    echo ""

    print_header "Full Response"
    if [ "$JQ_AVAILABLE" = true ]; then
        echo "$RESULT" | jq .
    else
        echo "$RESULT"
    fi
    echo ""
}

# Main
main() {
    echo ""
    print_header "🧪 Heap OOM Validation Test (OpenShift)"
    echo ""

    check_prerequisites
    get_causa_url
    find_target_pod

    if trigger_alert; then
        if wait_for_completion; then
            display_results
            print_header "Test Complete"
            print_success "Validation test completed"
            echo ""
        fi
    fi
}

main
