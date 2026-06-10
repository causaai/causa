#!/bin/bash
# Create Datadog monitors for Causa RCA metrics already exposed via /q/metrics
# export DD_API_KEY='f76ff07199a38562e22d1d3c09781068'
# export DD_APP_KEY='ddapp_qQi640r6QhzPaaZtDYmKbXVlUiZR0QwGdI'

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

if [ -z "$DD_API_KEY" ] || [ -z "$DD_APP_KEY" ]; then
    echo -e "${RED}Error: DD_API_KEY and DD_APP_KEY environment variables must be set${NC}"
    echo "Usage:"
    echo "  export DD_API_KEY='your-api-key'"
    echo "  export DD_APP_KEY='your-app-key'"
    echo "  export DD_SITE='us5.datadoghq.com'   # optional"
    exit 1
fi

DD_SITE="${DD_SITE:-us5.datadoghq.com}"
API_URL="https://api.${DD_SITE}/api/v1"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATADOG_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

create_or_update_monitor() {
    local config_file="$1"
    local monitor_name="$2"

    echo -e "${YELLOW}Checking monitor: ${monitor_name}${NC}"

    # Search for existing monitor
    SEARCH_RESPONSE=$(curl -s -X GET \
      "${API_URL}/monitor/search?query=${monitor_name// /%20}" \
      -H "DD-API-KEY: ${DD_API_KEY}" \
      -H "DD-APPLICATION-KEY: ${DD_APP_KEY}")

    EXISTING_MONITOR=$(echo "$SEARCH_RESPONSE" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2 || true)

    if [ -n "$EXISTING_MONITOR" ]; then
        echo -e "${YELLOW}Monitor exists, updating...${NC}"
        
        # Update existing monitor
        UPDATE_RESPONSE=$(curl -s -X PUT "${API_URL}/monitor/${EXISTING_MONITOR}" \
          -H "Content-Type: application/json" \
          -H "DD-API-KEY: ${DD_API_KEY}" \
          -H "DD-APPLICATION-KEY: ${DD_APP_KEY}" \
          -d @"${config_file}")
        
        if echo "$UPDATE_RESPONSE" | grep -q '"id"'; then
            echo -e "${GREEN}✓ Monitor updated successfully${NC}"
            echo "  Monitor ID: $EXISTING_MONITOR"
            echo "  View at: https://app.${DD_SITE}/monitors/${EXISTING_MONITOR}"
            echo ""
            return 0
        else
            echo -e "${RED}✗ Failed to update monitor${NC}"
            echo "  Response: $UPDATE_RESPONSE"
            echo ""
            return 1
        fi
    else
        echo -e "${YELLOW}Creating new monitor from ${config_file}${NC}"

        # Create new monitor
        CREATE_RESPONSE=$(curl -s -X POST "${API_URL}/monitor" \
          -H "Content-Type: application/json" \
          -H "DD-API-KEY: ${DD_API_KEY}" \
          -H "DD-APPLICATION-KEY: ${DD_APP_KEY}" \
          -d @"${config_file}")

        MONITOR_ID=$(echo "$CREATE_RESPONSE" | grep -o '"id":[0-9]*' | head -1 | cut -d':' -f2)

        if [ -n "$MONITOR_ID" ]; then
            echo -e "${GREEN}✓ Monitor created successfully${NC}"
            echo "  Monitor ID: $MONITOR_ID"
            echo "  View at: https://app.${DD_SITE}/monitors/${MONITOR_ID}"
            echo ""
            return 0
        else
            # Check for specific errors
            if echo "$CREATE_RESPONSE" | grep -q "Forbidden"; then
                echo -e "${RED}✗ Permission denied${NC}"
                echo "  Your Datadog APP_KEY needs 'monitors_write' permission"
                echo "  Check: https://app.${DD_SITE}/organization-settings/api-keys"
                echo ""
                return 1
            else
                echo -e "${RED}✗ Failed to create monitor${NC}"
                echo "  Response: $CREATE_RESPONSE"
                echo ""
                return 1
            fi
        fi
    fi
}

echo -e "${BLUE}Creating Causa RCA Monitors...${NC}"
echo ""

# Core RCA Health Monitors
create_or_update_monitor "${DATADOG_DIR}/monitors/failure/monitor-config.json" "Causa RCA Generation Failure"
create_or_update_monitor "${DATADOG_DIR}/monitors/high-failure-rate/monitor-config.json" "Causa RCA - High Failure Rate"
create_or_update_monitor "${DATADOG_DIR}/monitors/success-rate/monitor-config.json" "Causa RCA Success Rate Low"
create_or_update_monitor "${DATADOG_DIR}/monitors/no-rca-generated/monitor-config.json" "Causa RCA - No Analysis Generated"

# Performance Monitors
create_or_update_monitor "${DATADOG_DIR}/monitors/latency/monitor-config.json" "Causa RCA Generation Latency High"
create_or_update_monitor "${DATADOG_DIR}/monitors/slow-rca-trend/monitor-config.json" "Causa RCA - Slow Analysis Trend"

# Volume Monitors
#create_or_update_monitor "${DATADOG_DIR}/monitors/volume/monitor-config.json" "Causa RCA Volume"

echo ""
echo -e "${GREEN}Done!${NC}"
echo "Next steps:"
echo "1. Ensure Datadog is scraping Causa /q/metrics"
echo "2. Verify metrics exist in Datadog Metrics Explorer"
echo "3. Validate monitor queries against live RCA metric labels"

# Made with Bob
