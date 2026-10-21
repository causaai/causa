#!/bin/bash

###############################################################################
# Heap OOM Validation Test Script
#
# This script tests the validation pipeline for heap OOM scenarios.
# It triggers a synthetic alert and shows the validation results.
#
# Usage: ./scripts/test-heap-oom-validation.sh
###############################################################################

set -e

# Configuration
BASE_URL="${BASE_URL:-http://localhost:8080}"
NAMESPACE="${NAMESPACE:-chaos-test}"
CONTAINER="${CONTAINER:-heap-oom-prom}"
POD_NAME="${POD_NAME:-heap-oom-prom-75d49588db-tqhrg}"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Helper functions
print_header() {
    printf "${BLUE}========================================${NC}\n"
    printf "${BLUE}%s${NC}\n" "$1"
    printf "${BLUE}========================================${NC}\n"
}

print_success() {
    printf "${GREEN}✅ %s${NC}\n" "$1"
}

print_warning() {
    printf "${YELLOW}⚠️  %s${NC}\n" "$1"
}

print_error() {
    printf "${RED}❌ %s${NC}\n" "$1"
}

print_info() {
    printf "${BLUE}ℹ️  %s${NC}\n" "$1"
}

# Check prerequisites
check_prerequisites() {
    print_header "Checking Prerequisites"

    # Check if curl is available
    if ! command -v curl &> /dev/null; then
        print_error "curl is not installed. Please install curl."
        exit 1
    fi
    print_success "curl is installed"

    # Check if jq is available (optional but recommended)
    if command -v jq &> /dev/null; then
        JQ_AVAILABLE=true
        print_success "jq is installed (JSON formatting enabled)"
    else
        JQ_AVAILABLE=false
        print_warning "jq is not installed (JSON output will not be formatted)"
    fi

    # Check if application is running
    print_info "Checking if Causa backend is running at ${BASE_URL}..."

    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/q/health" 2>&1 || echo "000")

    if [ "$HTTP_CODE" = "200" ]; then
        print_success "Application is running"
    else
        print_error "Application is not running (HTTP $HTTP_CODE)"
        print_info "Start the application with: ./mvnw quarkus:dev"
        exit 1
    fi

    echo ""
}

# Trigger synthetic alert
trigger_alert() {
    print_header "Triggering Synthetic Alert"

    print_info "Configuration:"
    echo "  - Namespace: ${NAMESPACE}"
    echo "  - Container: ${CONTAINER}"
    echo "  - Pod: ${POD_NAME}"
    echo ""

    print_info "Sending POST request to ${BASE_URL}/api/v1/alerts..."

    RESPONSE=$(curl -s -X POST "${BASE_URL}/api/v1/alerts" \
        -H 'Content-Type: application/json' \
        -d "{
          \"namespace\": \"${NAMESPACE}\",
          \"container\": \"${CONTAINER}\",
          \"pod\": \"${POD_NAME}\"
        }")

    # Check response
    STATUS=$(echo "$RESPONSE" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)

    if [ "$STATUS" = "accepted" ]; then
        print_success "Alert accepted and diagnostic triggered"

        # Extract diagnostic ID
        DIAG_ID=$(echo "$RESPONSE" | grep -o 'diag_[^"]*' | head -1)
        ALERT_ID=$(echo "$RESPONSE" | grep -o 'alrt_[^"]*' | head -1)

        echo ""
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
        print_warning "Alert was rejected"

        REASON=$(echo "$RESPONSE" | grep -o '"message":"[^"]*"' | cut -d'"' -f4)
        print_info "Reason: ${REASON}"

        echo ""
        if [ "$JQ_AVAILABLE" = true ]; then
            echo "$RESPONSE" | jq .
        else
            echo "$RESPONSE"
        fi

        echo ""
        print_info "This might be due to:"
        echo "  - Alert cooldown (wait 15 minutes)"
        echo "  - Namespace in ignore list"
        echo "  - Severity too low"
        return 1
    else
        print_error "Unexpected response"
        echo "$RESPONSE"
        return 1
    fi
}

# Wait for processing
wait_for_processing() {
    print_header "Waiting for Diagnostic Processing"

    print_info "RCA generation and validation in progress..."
    print_info "This may take 30-60 seconds depending on LLM response time..."
    echo ""

    MAX_WAIT=120
    ELAPSED=0

    while [ $ELAPSED -lt $MAX_WAIT ]; do
        # Check diagnostic status
        DIAG_RESPONSE=$(curl -s "${BASE_URL}/api/v1/diagnostics/${DIAG_ID}")
        DIAG_STATUS=$(echo "$DIAG_RESPONSE" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)

        if [ "$DIAG_STATUS" = "COMPLETED" ]; then
            print_success "Diagnostic processing completed!"
            echo ""
            return 0
        elif [ "$DIAG_STATUS" = "FAILED" ]; then
            print_error "Diagnostic processing failed"
            return 1
        elif [ "$DIAG_STATUS" = "PENDING" ] || [ "$DIAG_STATUS" = "IN_PROGRESS" ]; then
            echo -ne "\r⏳ Status: ${DIAG_STATUS} (${ELAPSED}s elapsed)                    "
            sleep 5
            ELAPSED=$((ELAPSED + 5))
        else
            print_warning "Unknown status: ${DIAG_STATUS}"
            sleep 5
            ELAPSED=$((ELAPSED + 5))
        fi
    done

    echo ""
    print_warning "Timeout waiting for completion (${MAX_WAIT}s)"
    print_info "Check logs or retry: curl ${BASE_URL}/api/v1/diagnostics/${DIAG_ID}"
    return 1
}

# Display validation results
display_results() {
    print_header "Validation Results"

    print_info "Fetching diagnostic result..."
    RESULT=$(curl -s "${BASE_URL}/api/v1/diagnostics/${DIAG_ID}")

    # Extract key information
    ANOMALY_TYPE=$(echo "$RESULT" | grep -o '"anomaly_type":"[^"]*"' | cut -d'"' -f4)
    IS_VALID=$(echo "$RESULT" | grep -o '"is_valid":[^,}]*' | cut -d':' -f2)
    OVERALL_CONFIDENCE=$(echo "$RESULT" | grep -o '"overall_confidence":[^,}]*' | cut -d':' -f2)

    echo ""
    print_info "Anomaly Type: ${ANOMALY_TYPE}"

    if [ "$IS_VALID" = "true" ]; then
        print_success "Validation Status: VALID ✅"
    else
        print_warning "Validation Status: INVALID ⚠️"
    fi

    print_info "Overall Confidence: ${OVERALL_CONFIDENCE}"
    echo ""

    # Display full result
    print_header "Full Diagnostic Response"

    if [ "$JQ_AVAILABLE" = true ]; then
        echo "$RESULT" | jq .
    else
        echo "$RESULT"
    fi

    echo ""

    # Show PATH A and PATH B results
    print_header "Dual Validation Breakdown"

    FINAL_STATUS=$(echo "$RESULT" | grep -o '"final_verdict":[^}]*"status":"[^"]*"' | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
    FINAL_CONF=$(echo "$RESULT" | grep -o '"final_verdict":[^}]*"confidence":[^,}]*' | grep -o '"confidence":[^,}]*' | cut -d':' -f2)

    print_info "Final Verdict: ${FINAL_STATUS} (confidence: ${FINAL_CONF})"
    echo ""

    # PATH A (Assertion-based)
    echo "PATH A (Assertion-based LLM Validation):"
    A_STATUS=$(echo "$RESULT" | grep -o '"assertion_based_verdict":[^}]*"status":"[^"]*"' | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
    A_CONF=$(echo "$RESULT" | grep -o '"assertion_based_verdict":[^}]*"confidence":[^,}]*' | grep -o '"confidence":[^,}]*' | cut -d':' -f2)
    A_SUPPORTED=$(echo "$RESULT" | grep -o '"assertion_based_verdict":[^}]*"supported":[^,}]*' | grep -o '"supported":[^,}]*' | cut -d':' -f2)
    A_TOTAL=$(echo "$RESULT" | grep -o '"assertion_based_verdict":[^}]*"total":[^,}]*' | grep -o '"total":[^,}]*' | cut -d':' -f2)

    echo "  Status: ${A_STATUS}"
    echo "  Confidence: ${A_CONF}"
    echo "  Assertions Supported: ${A_SUPPORTED}/${A_TOTAL}"
    echo ""

    # PATH B (Rule-based)
    echo "PATH B (Rule-based Deterministic Validation):"
    B_STATUS=$(echo "$RESULT" | grep -o '"rule_based_verdict":[^}]*"status":"[^"]*"' | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
    B_CONF=$(echo "$RESULT" | grep -o '"rule_based_verdict":[^}]*"confidence":[^,}]*' | grep -o '"confidence":[^,}]*' | cut -d':' -f2)
    B_SCORE=$(echo "$RESULT" | grep -o '"rule_based_verdict":[^}]*"total_score":[^,}]*' | grep -o '"total_score":[^,}]*' | cut -d':' -f2)
    B_MAX=$(echo "$RESULT" | grep -o '"rule_based_verdict":[^}]*"max_possible_score":[^,}]*' | grep -o '"max_possible_score":[^,}]*' | cut -d':' -f2)
    B_REQ_PASS=$(echo "$RESULT" | grep -o '"rule_based_verdict":[^}]*"required_passed":[^,}]*' | grep -o '"required_passed":[^,}]*' | cut -d':' -f2)
    B_REQ_TOT=$(echo "$RESULT" | grep -o '"rule_based_verdict":[^}]*"required_total":[^,}]*' | grep -o '"required_total":[^,}]*' | cut -d':' -f2)

    echo "  Status: ${B_STATUS}"
    echo "  Confidence: ${B_CONF}"
    echo "  Score: ${B_SCORE}/${B_MAX}"
    echo "  Required Rules: ${B_REQ_PASS}/${B_REQ_TOT} passed"
    echo ""
}

# Main execution
main() {
    echo ""
    print_header "🧪 Heap OOM Validation Test"
    echo ""

    check_prerequisites

    if trigger_alert; then
        wait_for_processing
        display_results

        print_header "Test Complete"
        print_success "Validation pipeline test completed successfully!"
        print_info "View detailed logs in application console"
        print_info "Documentation: docs/testing/heap-oom-validation-guide.md"
        echo ""
    else
        print_error "Alert trigger failed"
        exit 1
    fi
}

# Run main function
main
