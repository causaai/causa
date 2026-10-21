#!/bin/bash

###############################################################################
# GC Pause Validation Test Script - OpenShift/OCP
#
# Tests validation pipeline for GC pause issues in OpenShift
#
# Usage: ./scripts/test-gc-pause-oc.sh [namespace] [pod-name]
###############################################################################

set -e

# Configuration
CAUSA_NAMESPACE="${CAUSA_NAMESPACE:-diagnostics-tool}"
TARGET_NAMESPACE="${1:-gc-pause}"
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
        print_info "Searching for pods in namespace: ${TARGET_NAMESPACE}"

        # Get running pods (GC pause doesn't kill pods)
        PODS=$(oc get pods -n "${TARGET_NAMESPACE}" --field-selector=status.phase=Running -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)

        if [ -z "$PODS" ]; then
            print_error "No running pods found in namespace ${TARGET_NAMESPACE}"
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

    # Show pod status
    POD_STATUS=$(oc get pod "${TARGET_POD}" -n "${TARGET_NAMESPACE}" -o jsonpath='{.status.phase}')
    print_info "Pod status: ${POD_STATUS}"

    echo ""
}

# Trigger alert
trigger_alert() {
    print_header "Triggering GC Pause Alert"

    print_info "Configuration:"
    echo "  Namespace: ${TARGET_NAMESPACE}"
    echo "  Pod: ${TARGET_POD}"
    echo "  Container: ${TARGET_CONTAINER}"
    echo ""

    print_info "Sending alert request to Causa..."

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
    print_info "RCA generation and validation in progress..."
    print_info "This may take 30-90s depending on MCP data collection and LLM..."
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

    print_info "Anomaly Type: ${ANOMALY}"

    if [ "$IS_VALID" = "true" ]; then
        print_success "Validation: VALID ✅"
    else
        print_warning "Validation: INVALID ⚠️"
    fi

    print_info "Overall Confidence: ${CONFIDENCE}"
    echo ""

    # Check if it's GC pause
    if [ "$ANOMALY" = "POSSIBLE_GC_PAUSE" ]; then
        print_success "Correctly classified as GC Pause issue"
    elif [ "$ANOMALY" = "OOM_KILLED" ]; then
        print_warning "Misclassified as OOM (expected GC Pause)"
    else
        print_info "Classified as: ${ANOMALY}"
    fi
    echo ""

    # Dual validation breakdown
    print_header "Dual Validation Breakdown"

    FINAL_STATUS=$(echo "$RESULT" | jq -r '.validation.dual_validation.final_verdict.status // "N/A"' 2>/dev/null || echo "N/A")
    FINAL_CONF=$(echo "$RESULT" | jq -r '.validation.dual_validation.final_verdict.confidence // "N/A"' 2>/dev/null || echo "N/A")

    print_info "Final Verdict: ${FINAL_STATUS} (confidence: ${FINAL_CONF})"
    echo ""

    # PATH A (Assertion-based)
    echo "PATH A (Assertion-based LLM Validation):"
    A_STATUS=$(echo "$RESULT" | jq -r '.validation.dual_validation.assertion_based_verdict.status // "N/A"' 2>/dev/null || echo "N/A")
    A_CONF=$(echo "$RESULT" | jq -r '.validation.dual_validation.assertion_based_verdict.confidence // "N/A"' 2>/dev/null || echo "N/A")
    A_SUPPORTED=$(echo "$RESULT" | jq -r '.validation.dual_validation.assertion_based_verdict.supported // "N/A"' 2>/dev/null || echo "N/A")
    A_TOTAL=$(echo "$RESULT" | jq -r '.validation.dual_validation.assertion_based_verdict.total // "N/A"' 2>/dev/null || echo "N/A")
    echo "  Status: ${A_STATUS}"
    echo "  Confidence: ${A_CONF}"
    echo "  Assertions Supported: ${A_SUPPORTED}/${A_TOTAL}"
    echo ""

    # PATH B (Rule-based)
    echo "PATH B (Rule-based Deterministic Validation):"
    B_STATUS=$(echo "$RESULT" | jq -r '.validation.dual_validation.rule_based_verdict.status // "N/A"' 2>/dev/null || echo "N/A")
    B_CONF=$(echo "$RESULT" | jq -r '.validation.dual_validation.rule_based_verdict.confidence // "N/A"' 2>/dev/null || echo "N/A")
    B_SCORE=$(echo "$RESULT" | jq -r '.validation.dual_validation.rule_based_verdict.total_score // "N/A"' 2>/dev/null || echo "N/A")
    B_MAX=$(echo "$RESULT" | jq -r '.validation.dual_validation.rule_based_verdict.max_possible_score // "N/A"' 2>/dev/null || echo "N/A")
    B_REQ=$(echo "$RESULT" | jq -r '.validation.dual_validation.rule_based_verdict.required_passed // "N/A"' 2>/dev/null || echo "N/A")
    B_REQ_TOTAL=$(echo "$RESULT" | jq -r '.validation.dual_validation.rule_based_verdict.required_total // "N/A"' 2>/dev/null || echo "N/A")
    echo "  Status: ${B_STATUS}"
    echo "  Confidence: ${B_CONF}"
    echo "  Score: ${B_SCORE}/${B_MAX}"
    echo "  Required Rules: ${B_REQ}/${B_REQ_TOTAL} passed"
    echo ""

    # GC-specific info
    if [ "$ANOMALY" = "POSSIBLE_GC_PAUSE" ]; then
        print_header "GC Pause Indicators"

        # Extract GC-related evidence from RCA
        GC_EVIDENCE=$(echo "$RESULT" | jq -r '.rca.supporting_evidence[]? | select(.type == "GC_ANALYSIS" or .description | contains("GC") or contains("pause")) | .description' 2>/dev/null | head -5)

        if [ -n "$GC_EVIDENCE" ]; then
            echo "$GC_EVIDENCE"
        else
            print_info "No specific GC evidence extracted (check full response)"
        fi
        echo ""
    fi

    print_header "Full Diagnostic Response"
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
    print_header "🧪 GC Pause Validation Test (OpenShift)"
    echo ""

    check_prerequisites
    get_causa_url
    find_target_pod

    if trigger_alert; then
        if wait_for_completion; then
            display_results
            print_header "Test Complete"
            print_success "GC pause validation test completed"
            print_info "Expected: POSSIBLE_GC_PAUSE anomaly type"
            print_info "Running pod with high heap but no OOM kill"
            echo ""
        fi
    fi
}

main
