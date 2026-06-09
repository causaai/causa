#!/usr/bin/env bash
#
# setup-vertex-ai.sh - Vertex AI ADC Setup for Development
#
# This script sets up Google Vertex AI authentication using Application Default
# Credentials (ADC) for LOCAL, KIND, and OPENSHIFT development environments.
#
# ⚠️  FOR DEVELOPMENT/POC ONLY - DO NOT USE IN PRODUCTION
# For production, see: docs/llm/vertex-ai-production-guide.md
#
# Usage:
#   ./scripts/llm/setup-vertex-ai.sh --env [local|kind|openshift] --project <gcp-project-id>
#
# Examples:
#   # Local development (no Kubernetes)
#   ./scripts/llm/setup-vertex-ai.sh --env local --project my-gcp-project
#
#   # KIND cluster development
#   ./scripts/llm/setup-vertex-ai.sh --env kind --project my-gcp-project
#
#   # OpenShift development
#   ./scripts/llm/setup-vertex-ai.sh --env openshift --project my-gcp-project
#

set -euo pipefail

# ═══════════════════════════════════════════════════════════════════════════
# Configuration
# ═══════════════════════════════════════════════════════════════════════════

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
DEPLOYMENT_DIR="$PROJECT_ROOT/deployment/kubernetes"
VERTEX_AI_DIR="$DEPLOYMENT_DIR/vertex-ai"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Defaults
ENV_TYPE=""
GCP_PROJECT_ID=""
K8S_NAMESPACE="diagnostics-tool"
VERTEX_LOCATION="us-east5"
LLM_MODEL="claude-sonnet-4-6"
ADC_FILE=""

# ═══════════════════════════════════════════════════════════════════════════
# Functions
# ═══════════════════════════════════════════════════════════════════════════

log_info() {
    echo -e "${BLUE}[INFO]${NC} $*"
}

log_success() {
    echo -e "${GREEN}[✓]${NC} $*"
}

log_warn() {
    echo -e "${YELLOW}[⚠]${NC} $*"
}

log_error() {
    echo -e "${RED}[✗]${NC} $*" >&2
}

log_step() {
    echo -e "${CYAN}[STEP]${NC} $*"
}

show_usage() {
    cat << EOF
Usage: $0 --env <local|kind|openshift> --project <gcp-project-id> [OPTIONS]

Required Arguments:
  --env          Environment: 'local', 'kind', or 'openshift'
  --project      Google Cloud Project ID

Optional Arguments:
  --namespace    Kubernetes namespace (default: diagnostics-tool)
  --location     Vertex AI location (default: us-east5)
  --model        Claude model name (default: claude-sonnet-4-6)
  --help         Show this help message

Examples:
  # Local development (no Kubernetes)
  $0 --env local --project my-gcp-project

  # KIND cluster (generates YAMLs in deployment/kubernetes/vertex-ai/)
  $0 --env kind --project my-gcp-project

  # OpenShift development (generates YAMLs in deployment/kubernetes/vertex-ai/)
  $0 --env openshift --project my-gcp-project

EOF
}

detect_adc_file() {
    log_step "Detecting Application Default Credentials..."

    # Detect OS and set ADC path
    case "$(uname -s)" in
        Darwin|Linux)
            ADC_FILE="$HOME/.config/gcloud/application_default_credentials.json"
            ;;
        CYGWIN*|MINGW*|MSYS*)
            ADC_FILE="$APPDATA/gcloud/application_default_credentials.json"
            ;;
        *)
            log_error "Unsupported OS: $(uname -s)"
            exit 1
            ;;
    esac

    if [ ! -f "$ADC_FILE" ]; then
        log_error "ADC file not found: $ADC_FILE"
        log_error ""
        log_error "Please run: gcloud auth application-default login"
        log_error "Then try this script again."
        exit 1
    fi

    log_success "ADC file found: $ADC_FILE"
}

validate_adc_credentials() {
    log_step "Validating ADC credentials..."

    if ! gcloud auth application-default print-access-token &> /dev/null; then
        log_error "ADC credentials are invalid or expired"
        log_error ""
        log_error "Please re-authenticate:"
        log_error "  gcloud auth application-default login"
        log_error "  gcloud auth application-default set-quota-project $GCP_PROJECT_ID"
        exit 1
    fi

    log_success "ADC credentials are valid"
}

check_prerequisites() {
    log_step "Checking prerequisites..."

    local missing_tools=()

    if ! command -v gcloud &> /dev/null; then
        missing_tools+=("gcloud")
    fi

    if [ "$ENV_TYPE" != "local" ]; then
        if ! command -v kubectl &> /dev/null && ! command -v oc &> /dev/null; then
            missing_tools+=("kubectl or oc")
        fi
        if ! command -v base64 &> /dev/null; then
            missing_tools+=("base64")
        fi
    fi

    if ! command -v jq &> /dev/null; then
        missing_tools+=("jq")
    fi

    if [ ${#missing_tools[@]} -gt 0 ]; then
        log_error "Missing required tools: ${missing_tools[*]}"
        exit 1
    fi

    log_success "All prerequisites met"
}

validate_gcp_project() {
    log_step "Validating GCP project: $GCP_PROJECT_ID"

    if ! gcloud projects describe "$GCP_PROJECT_ID" &> /dev/null; then
        log_error "GCP project '$GCP_PROJECT_ID' not found or no access"
        log_error "Run: gcloud projects list"
        exit 1
    fi

    log_success "GCP project validated"
}

check_vertex_ai_api() {
    log_step "Checking Vertex AI API..."

    if ! gcloud services list --enabled --project="$GCP_PROJECT_ID" \
         --filter="name:aiplatform.googleapis.com" --format="value(name)" | grep -q aiplatform; then
        log_warn "Vertex AI API not enabled"
        log_info "Enabling Vertex AI API..."

        if gcloud services enable aiplatform.googleapis.com --project="$GCP_PROJECT_ID"; then
            log_success "Vertex AI API enabled"
        else
            log_error "Failed to enable Vertex AI API"
            exit 1
        fi
    else
        log_success "Vertex AI API is enabled"
    fi
}

grant_user_permissions() {
    log_step "Checking user permissions..."

    local user_email
    user_email=$(gcloud config get-value account 2>/dev/null)

    log_info "Granting aiplatform.user role to $user_email..."

    if gcloud projects add-iam-policy-binding "$GCP_PROJECT_ID" \
        --member="user:$user_email" \
        --role="roles/aiplatform.user" \
        --condition=None \
        &> /dev/null; then
        log_success "Permissions granted"
    else
        log_warn "Failed to grant permissions (may already exist or insufficient access)"
    fi
}

update_configmap() {
    log_step "Updating ConfigMap..."

    local configmap_file="$DEPLOYMENT_DIR/base/configmap.yaml"

    if [ ! -f "$configmap_file" ]; then
        log_error "ConfigMap not found: $configmap_file"
        exit 1
    fi

    # Backup original
    cp "$configmap_file" "$configmap_file.bak.$(date +%Y%m%d_%H%M%S)"

    # Update values
    sed -i.tmp "s|LLM_PROVIDER:.*|LLM_PROVIDER: \"vertex-ai-anthropic\"|" "$configmap_file"
    sed -i.tmp "s|LLM_MODEL_NAME:.*|LLM_MODEL_NAME: \"$LLM_MODEL\"|" "$configmap_file"
    sed -i.tmp "s|VERTEX_LOCATION:.*|VERTEX_LOCATION: \"$VERTEX_LOCATION\"|" "$configmap_file"
    rm -f "$configmap_file.tmp"

    log_success "ConfigMap updated"
}

generate_secret_yamls() {
    log_step "Generating secret YAMLs..."

    local output_dir="$VERTEX_AI_DIR/generated"
    mkdir -p "$output_dir"

    # Generate project ID secret
    cat > "$output_dir/causa-llm-secrets.yaml" << EOFYAML
apiVersion: v1
kind: Secret
metadata:
  name: causa-llm-secrets
  namespace: $K8S_NAMESPACE
  labels:
    app.kubernetes.io/name: causa-backend
    app.kubernetes.io/component: secret
type: Opaque
stringData:
  VERTEX_PROJECT_ID: "$GCP_PROJECT_ID"
EOFYAML

    log_success "Created: $output_dir/causa-llm-secrets.yaml"

    # Generate ADC secret
    local adc_base64
    adc_base64=$(base64 < "$ADC_FILE" | tr -d '\n')

    cat > "$output_dir/gcp-adc-credentials.yaml" << EOFYAML
apiVersion: v1
kind: Secret
metadata:
  name: gcp-adc-credentials
  namespace: $K8S_NAMESPACE
  labels:
    app.kubernetes.io/name: causa-backend
    app.kubernetes.io/component: secret
    causa.dev/auth-type: adc
    causa.dev/purpose: development
type: Opaque
data:
  application_default_credentials.json: $adc_base64
EOFYAML

    log_success "Created: $output_dir/gcp-adc-credentials.yaml"
}

generate_patch_yaml() {
    log_step "Generating deployment patch..."

    local output_dir="$VERTEX_AI_DIR/generated"

    # Copy deployment patch
    cp "$VERTEX_AI_DIR/deployment-adc-patch.yaml" "$output_dir/deployment-adc-patch.yaml"
    log_success "Created: $output_dir/deployment-adc-patch.yaml"
}

generate_apply_script() {
    log_step "Generating apply script..."

    local output_dir="$VERTEX_AI_DIR/generated"
    local cmd="kubectl"
    local deployment_name="causa-backend"

    if [ "$ENV_TYPE" = "openshift" ]; then
        cmd="oc"
        deployment_name="causa-backend"
    fi

    cat > "$output_dir/apply.sh" << EOFAPPLY
#!/usr/bin/env bash
#
# Apply Vertex AI ADC configuration to $ENV_TYPE cluster
#
# This script:
# 1. Ensures base deployment exists
# 2. Applies ADC secrets
# 3. Patches deployment to mount secrets
# 4. Restarts deployment
#

set -euo pipefail

NAMESPACE="$K8S_NAMESPACE"
CMD="$cmd"
DEPLOYMENT_NAME="$deployment_name"

echo "Applying Vertex AI ADC configuration..."

# Step 1: Check if base deployment exists, if not deploy it
if ! \$CMD get deployment \$DEPLOYMENT_NAME -n \$NAMESPACE &> /dev/null; then
    echo "Base deployment not found, deploying from overlay..."
    \$CMD apply -k ../../overlays/$ENV_TYPE
    echo "Waiting for deployment to be created..."
    sleep 5
fi

# Step 2: Apply secrets
echo "Applying secrets..."
\$CMD apply -f causa-llm-secrets.yaml
\$CMD apply -f gcp-adc-credentials.yaml

# Step 3: Patch deployment
echo "Patching deployment with ADC configuration..."
\$CMD patch deployment \$DEPLOYMENT_NAME -n \$NAMESPACE --patch-file deployment-adc-patch.yaml

# Step 4: Restart deployment
echo "Restarting deployment..."
\$CMD rollout restart deployment/\$DEPLOYMENT_NAME -n \$NAMESPACE

# Step 5: Wait for rollout
echo "Waiting for rollout to complete..."
\$CMD rollout status deployment/\$DEPLOYMENT_NAME -n \$NAMESPACE --timeout=3m

echo "✓ Vertex AI ADC configuration applied successfully!"
echo ""
echo "Verify deployment:"
echo "  \$CMD get pods -n \$NAMESPACE"
echo "  \$CMD logs -f deployment/\$DEPLOYMENT_NAME -n \$NAMESPACE | grep LLM"
EOFAPPLY

    chmod +x "$output_dir/apply.sh"
    log_success "Created: $output_dir/apply.sh"
}

setup_local() {
    log_info ""
    log_info "═══════════════════════════════════════════════════════════"
    log_info "  LOCAL DEVELOPMENT SETUP"
    log_info "═══════════════════════════════════════════════════════════"
    log_info ""

    detect_adc_file
    validate_adc_credentials
    grant_user_permissions
    update_configmap

    log_info ""
    log_success "Local development setup complete!"
    log_info ""
    log_info "Next steps:"
    log_info "  1. Export environment variables:"
    log_info "     export VERTEX_PROJECT_ID=$GCP_PROJECT_ID"
    log_info "     export LLM_PROVIDER=vertex-ai-anthropic"
    log_info ""
    log_info "  2. Run locally:"
    log_info "     ./mvnw quarkus:dev"
    log_info ""
}

setup_kind() {
    log_info ""
    log_info "═══════════════════════════════════════════════════════════"
    log_info "  KIND CLUSTER SETUP"
    log_info "═══════════════════════════════════════════════════════════"
    log_info ""

    detect_adc_file
    validate_adc_credentials
    grant_user_permissions
    update_configmap
    generate_secret_yamls
    generate_patch_yaml
    generate_apply_script

    log_info ""
    log_success "Configuration files generated successfully!"
    log_info ""
    log_info "Generated files in: deployment/kubernetes/vertex-ai/generated/"
    log_info "  - causa-llm-secrets.yaml         (Vertex project ID)"
    log_info "  - gcp-adc-credentials.yaml       (Your ADC credentials)"
    log_info "  - deployment-adc-patch.yaml      (Deployment patch)"
    log_info "  - apply.sh                       (Deployment script)"
    log_info ""
    log_info "Next steps:"
    log_info ""
    log_info "  1. Review the generated files:"
    log_info "     cd deployment/kubernetes/vertex-ai/generated"
    log_info "     cat causa-llm-secrets.yaml"
    log_info "     cat deployment-adc-patch.yaml"
    log_info ""
    log_info "  2. Deploy to KIND:"
    log_info "     cd deployment/kubernetes/vertex-ai/generated"
    log_info "     ./apply.sh"
    log_info ""
    log_info "     Or manually:"
    log_info "     kubectl apply -k ../../overlays/kind            # Deploy base first"
    log_info "     kubectl apply -f causa-llm-secrets.yaml"
    log_info "     kubectl apply -f gcp-adc-credentials.yaml"
    log_info "     kubectl patch deployment causa-backend -n $K8S_NAMESPACE \\"
    log_info "       --patch-file deployment-adc-patch.yaml"
    log_info "     kubectl rollout restart deployment/causa-backend -n $K8S_NAMESPACE"
    log_info ""
    log_info "  3. Verify:"
    log_info "     kubectl logs -f deployment/causa-backend -n $K8S_NAMESPACE | grep LLM"
    log_info ""
}

setup_openshift() {
    log_info ""
    log_info "═══════════════════════════════════════════════════════════"
    log_info "  OPENSHIFT CLUSTER SETUP"
    log_info "═══════════════════════════════════════════════════════════"
    log_info ""

    detect_adc_file
    validate_adc_credentials
    grant_user_permissions
    update_configmap
    generate_secret_yamls
    generate_patch_yaml
    generate_apply_script

    log_info ""
    log_success "Configuration files generated successfully!"
    log_info ""
    log_info "Generated files in: deployment/kubernetes/vertex-ai/generated/"
    log_info "  - causa-llm-secrets.yaml         (Vertex project ID)"
    log_info "  - gcp-adc-credentials.yaml       (Your ADC credentials)"
    log_info "  - deployment-adc-patch.yaml      (Deployment patch)"
    log_info "  - apply.sh                       (Deployment script)"
    log_info ""
    log_info "Next steps:"
    log_info ""
    log_info "  1. Review the generated files:"
    log_info "     cd deployment/kubernetes/vertex-ai/generated"
    log_info "     cat causa-llm-secrets.yaml"
    log_info "     cat deployment-adc-patch.yaml"
    log_info ""
    log_info "  2. Deploy to OpenShift:"
    log_info "     cd deployment/kubernetes/vertex-ai/generated"
    log_info "     ./apply.sh"
    log_info ""
    log_info "     Or manually:"
    log_info "     oc apply -k ../../overlays/openshift           # Deploy base first"
    log_info "     oc apply -f causa-llm-secrets.yaml"
    log_info "     oc apply -f gcp-adc-credentials.yaml"
    log_info "     oc patch deployment causa-backend -n $K8S_NAMESPACE \\"
    log_info "       --patch-file deployment-adc-patch.yaml"
    log_info "     oc rollout restart deployment/causa-backend -n $K8S_NAMESPACE"
    log_info ""
    log_info "  3. Verify:"
    log_info "     oc logs -f deployment/causa-backend -n $K8S_NAMESPACE | grep LLM"
    log_info ""
}

# ═══════════════════════════════════════════════════════════════════════════
# Main
# ═══════════════════════════════════════════════════════════════════════════

main() {
    # Parse arguments
    while [[ $# -gt 0 ]]; do
        case $1 in
            --env)
                ENV_TYPE="$2"
                shift 2
                ;;
            --project)
                GCP_PROJECT_ID="$2"
                shift 2
                ;;
            --namespace)
                K8S_NAMESPACE="$2"
                shift 2
                ;;
            --location)
                VERTEX_LOCATION="$2"
                shift 2
                ;;
            --model)
                LLM_MODEL="$2"
                shift 2
                ;;
            --help)
                show_usage
                exit 0
                ;;
            *)
                log_error "Unknown option: $1"
                show_usage
                exit 1
                ;;
        esac
    done

    # Validate required arguments
    if [ -z "$ENV_TYPE" ] || [ -z "$GCP_PROJECT_ID" ]; then
        log_error "Missing required arguments"
        show_usage
        exit 1
    fi

    if [[ ! "$ENV_TYPE" =~ ^(local|kind|openshift)$ ]]; then
        log_error "Invalid environment type: $ENV_TYPE"
        log_error "Must be 'local', 'kind', or 'openshift'"
        exit 1
    fi

    # Show configuration
    log_info ""
    log_info "Configuration:"
    log_info "  Environment:     $ENV_TYPE"
    log_info "  GCP Project:     $GCP_PROJECT_ID"
    log_info "  Namespace:       $K8S_NAMESPACE"
    log_info "  Vertex Location: $VERTEX_LOCATION"
    log_info "  Model:           $LLM_MODEL"
    log_info ""

    # Run prerequisites
    check_prerequisites
    validate_gcp_project
    check_vertex_ai_api

    # Run environment-specific setup
    case $ENV_TYPE in
        local)
            setup_local
            ;;
        kind)
            setup_kind
            ;;
        openshift)
            setup_openshift
            ;;
    esac
}

main "$@"
