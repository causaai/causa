#!/bin/bash
# Setup Datadog Agent for Causa RCA Observability
# This script configures the Datadog Agent to scrape Causa metrics from /q/metrics

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║        Datadog Agent Setup for Causa RCA Metrics          ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Check prerequisites
if [ -z "$DD_API_KEY" ] || [ -z "$DD_APP_KEY" ]; then
    echo -e "${RED}Error: DD_API_KEY and DD_APP_KEY environment variables must be set${NC}"
    echo "Usage:"
    echo "  export DD_API_KEY='your-api-key'"
    echo "  export DD_APP_KEY='your-app-key'"
    echo "  export DD_SITE='us5.datadoghq.com'   # optional"
    exit 1
fi

DD_SITE="${DD_SITE:-us5.datadoghq.com}"
DATADOG_NAMESPACE="openshift-operators"

# Get script directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AGENT_CONFIG="${SCRIPT_DIR}/../agent/datadog-agent-causa-openmetrics.yaml"

echo -e "${YELLOW}Checking prerequisites...${NC}"

if ! command -v oc &> /dev/null; then
    echo -e "${RED}✗ oc CLI not found${NC}"
    exit 1
fi
echo -e "${GREEN}✓ oc CLI found${NC}"

if ! oc whoami &>/dev/null; then
    echo -e "${RED}✗ Not logged in to OpenShift${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Logged in as $(oc whoami)${NC}"

if [ ! -f "$AGENT_CONFIG" ]; then
    echo -e "${RED}✗ Datadog Agent config not found at: $AGENT_CONFIG${NC}"
    exit 1
fi
echo -e "${GREEN}✓ Datadog Agent config found${NC}"

echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}Step 1: Creating Datadog Namespace and Secret${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"

# Create namespace
oc create namespace ${DATADOG_NAMESPACE} --dry-run=client -o yaml | oc apply -f -
echo -e "${GREEN}✓ Namespace ${DATADOG_NAMESPACE} ready${NC}"

# Create or update secret
if oc get secret datadog-secret -n ${DATADOG_NAMESPACE} &>/dev/null; then
    echo -e "${YELLOW}⚠ Datadog secret already exists${NC}"
    read -p "Do you want to update it? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        oc delete secret datadog-secret -n ${DATADOG_NAMESPACE}
        oc create secret generic datadog-secret \
            --from-literal api-key=${DD_API_KEY} \
            --from-literal app-key=${DD_APP_KEY} \
            -n ${DATADOG_NAMESPACE}
        echo -e "${GREEN}✓ Datadog secret updated${NC}"
    fi
else
    oc create secret generic datadog-secret \
        --from-literal api-key=${DD_API_KEY} \
        --from-literal app-key=${DD_APP_KEY} \
        -n ${DATADOG_NAMESPACE}
    echo -e "${GREEN}✓ Datadog secret created${NC}"
fi

echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}Step 2: Installing Datadog Operator${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"

# Check if operator is already installed
if oc get subscription datadog-operator-certified -n ${DATADOG_NAMESPACE} &>/dev/null; then
    echo -e "${GREEN}✓ Datadog Operator already installed${NC}"
else
    echo "Installing Datadog Operator..."
    cat <<EOF | oc apply -f -
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata:
  name: datadog-operator-certified
  namespace: ${DATADOG_NAMESPACE}
spec:
  channel: stable
  installPlanApproval: Automatic
  name: datadog-operator-certified
  source: certified-operators
  sourceNamespace: openshift-marketplace
EOF
    
    echo "Waiting for operator to be ready..."
    sleep 30
    
    # Wait for operator pod
    for i in {1..30}; do
        if oc get pods -n ${DATADOG_NAMESPACE} | grep datadog-operator | grep -q Running; then
            echo -e "${GREEN}✓ Datadog Operator is running${NC}"
            break
        fi
        echo "Waiting for operator pod... ($i/30)"
        sleep 10
    done
fi

echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}Step 3: Applying SCC Permissions${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"

# Apply OpenShift SCC permissions
oc adm policy add-scc-to-user privileged -z datadog-agent -n ${DATADOG_NAMESPACE} || true
oc adm policy add-scc-to-user anyuid -z datadog-agent -n ${DATADOG_NAMESPACE} || true
oc adm policy add-scc-to-user hostnetwork -z datadog-agent -n ${DATADOG_NAMESPACE} || true
echo -e "${GREEN}✓ SCC permissions applied${NC}"

echo ""
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}Step 4: Deploying Datadog Agent${NC}"
echo -e "${BLUE}═══════════════════════════════════════════════════════════${NC}"

# Check if DatadogAgent already exists
if oc get datadogagent datadog -n ${DATADOG_NAMESPACE} &>/dev/null; then
    echo -e "${YELLOW}⚠ Datadog Agent already exists${NC}"
    read -p "Do you want to update it? (y/n) " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        oc delete datadogagent datadog -n ${DATADOG_NAMESPACE}
        sleep 20
        oc apply -f "$AGENT_CONFIG"
        echo -e "${GREEN}✓ Datadog Agent updated${NC}"
    fi
else
    oc apply -f "$AGENT_CONFIG"
    echo -e "${GREEN}✓ Datadog Agent deployed${NC}"
fi

echo ""
echo "Waiting for Datadog Agent pods to be ready..."
sleep 30

# Check agent status
echo ""
echo "Datadog Agent Pods:"
oc get pods -n ${DATADOG_NAMESPACE} -l agent.datadoghq.com/component=agent

echo ""
echo "Datadog Cluster Agent:"
oc get pods -n ${DATADOG_NAMESPACE} -l agent.datadoghq.com/component=cluster-agent

echo ""
echo -e "${GREEN}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║          Datadog Agent Setup Completed!                    ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${YELLOW}Next Steps:${NC}"
echo ""
echo "1. Verify agent is scraping metrics:"
echo -e "   ${BLUE}oc logs -n ${DATADOG_NAMESPACE} -l agent.datadoghq.com/component=agent --tail=50 | grep -i openmetrics${NC}"
echo ""
echo "2. Check agent status:"
echo -e "   ${BLUE}oc exec -n ${DATADOG_NAMESPACE} -it \$(oc get pods -n ${DATADOG_NAMESPACE} -l agent.datadoghq.com/component=agent -o name | head -1) -- agent status${NC}"
echo ""
echo "3. Verify metrics in Datadog:"
echo "   - Go to Datadog → Metrics → Explorer"
echo "   - Search for: causa_rca_analysis_available"
echo ""
echo "4. Create monitors:"
echo -e "   ${BLUE}./create-datadog-resources.sh${NC}"
echo ""
echo -e "${GREEN}Configuration complete! 🎉${NC}"

