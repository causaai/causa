#!/usr/bin/env bash
#
# setup-vertex-ai.sh - Automated Vertex AI Setup for Causa Backend
#
# This script automates the setup of Google Vertex AI authentication for
# Causa Backend on OpenShift or Kind clusters.
#
# Usage:
#   ./scripts/llm/setup-vertex-ai.sh --env [local|production] --project <gcp-project-id>
#
# Examples:
#   # Local development (Kind) - uses ADC
#   ./scripts/llm/setup-vertex-ai.sh --env local --project my-gcp-project
#
#   # Production (OpenShift) - creates service account key
#   ./scripts/llm/setup-vertex-ai.sh --env production --project my-gcp-project
#

set -euo pipefail

# ═══════════════════════════════════════════════════════════════════════════
# Configuration
# ═══════════════════════════════════════════════════════════════════════════

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
DEPLOYMENT_DIR="$PROJECT_ROOT/deployment/kubernetes"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Defaults
ENV_TYPE=""
GCP_PROJECT_ID=""
K8S_NAMESPACE="diagnostics-tool"
GCP_SA_NAME="causa-backend"
VERTEX_LOCATION="global"
LLM_MODEL="claude-sonnet-4-6"

# ═══════════════════════════════════════════════════════════════════════════
# Functions
# ═══════════════════════════════════════════════════════════════════════════

log_info() {
    echo -e "${BLUE}[INFO]${NC} $*"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $*"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $*"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $*" >&2
}

show_usage() {
    cat << EOF
Usage: $0 --env <local|production> --project <gcp-project-id> [OPTIONS]

Required Arguments:
  --env          Environment type: 'local' (Kind) or 'production' (OpenShift)
  --project      Google Cloud Project ID

Optional Arguments:
  --namespace    Kubernetes namespace (default: diagnostics-tool)
  --sa-name      GCP service account name (default: causa-backend)
  --location     Vertex AI location (default: global)
  --model        Claude model name (default: claude-sonnet-4-6)
  --help         Show this help message

Examples:
  # Local development setup
  $0 --env local --project my-gcp-project

  # Production setup with custom namespace
  $0 --env production --project my-gcp-project --namespace my-namespace

EOF
}

check_prerequisites() {
    log_info "Checking prerequisites..."

    local missing_tools=()

    if ! command -v gcloud &> /dev/null; then
        missing_tools+=("gcloud")
    fi

    if ! command -v kubectl &> /dev/null; then
        missing_tools+=("kubectl")
    fi

    if ! command -v jq &> /dev/null; then
        missing_tools+=("jq")
    fi

    if [ ${#missing_tools[@]} -gt 0 ]; then
        log_error "Missing required tools: ${missing_tools[*]}"
        log_error "Please install them and try again"
        exit 1
    fi

    log_success "All prerequisites met"
}

validate_gcp_project() {
    log_info "Validating GCP project: $GCP_PROJECT_ID"

    if ! gcloud projects describe "$GCP_PROJECT_ID" &> /dev/null; then
        log_error "GCP project '$GCP_PROJECT_ID' not found or no access"
        log_error "Run: gcloud projects list"
        exit 1
    fi

    log_success "GCP project validated"
}

check_vertex_ai_api() {
    log_info "Checking Vertex AI API status..."

    if ! gcloud services list --enabled --project="$GCP_PROJECT_ID" --filter="name:aiplatform.googleapis.com" --format="value(name)" | grep -q aiplatform; then
        log_warn "Vertex AI API not enabled"
        log_info "Enabling Vertex AI API..."

        if gcloud services enable aiplatform.googleapis.com --project="$GCP_PROJECT_ID"; then
            log_success "Vertex AI API enabled"
        else
            log_error "Failed to enable Vertex AI API"
            exit 1
        fi
    else
        log_success "Vertex AI API already enabled"
    fi
}

setup_local_adc() {
    log_info "Setting up Application Default Credentials for local development..."

    # Check if ADC is already configured
    if [ -f "$HOME/.config/gcloud/application_default_credentials.json" ]; then
        log_warn "ADC already configured"
        read -p "Do you want to re-authenticate? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            log_info "Skipping ADC setup"
            return 0
        fi
    fi

    log_info "Running: gcloud auth application-default login"
    log_info "A browser window will open for authentication..."

    if ! gcloud auth application-default login; then
        log_error "ADC authentication failed"
        exit 1
    fi

    log_info "Setting quota project..."
    if gcloud auth application-default set-quota-project "$GCP_PROJECT_ID"; then
        log_success "ADC configured successfully"
    else
        log_warn "Failed to set quota project (non-fatal)"
    fi

    # Grant user aiplatform.user role
    local user_email
    user_email=$(gcloud config get-value account)
    log_info "Granting aiplatform.user role to $user_email..."

    if gcloud projects add-iam-policy-binding "$GCP_PROJECT_ID" \
        --member="user:$user_email" \
        --role="roles/aiplatform.user" \
        --condition=None \
        &> /dev/null; then
        log_success "Permissions granted"
    else
        log_warn "Failed to grant permissions (you may need project admin access)"
    fi
}

create_gcp_service_account() {
    log_info "Creating GCP service account: $GCP_SA_NAME..."

    local sa_email="$GCP_SA_NAME@$GCP_PROJECT_ID.iam.gserviceaccount.com"

    # Check if service account already exists
    if gcloud iam service-accounts describe "$sa_email" --project="$GCP_PROJECT_ID" &> /dev/null; then
        log_warn "Service account already exists: $sa_email"
        return 0
    fi

    if gcloud iam service-accounts create "$GCP_SA_NAME" \
        --display-name="Causa Backend LLM (Production)" \
        --description="Service account for Causa Backend Vertex AI access on OpenShift" \
        --project="$GCP_PROJECT_ID"; then
        log_success "Service account created: $sa_email"
    else
        log_error "Failed to create service account"
        exit 1
    fi
}

grant_vertex_ai_permissions() {
    log_info "Granting Vertex AI permissions..."

    local sa_email="$GCP_SA_NAME@$GCP_PROJECT_ID.iam.gserviceaccount.com"

    if gcloud projects add-iam-policy-binding "$GCP_PROJECT_ID" \
        --member="serviceAccount:$sa_email" \
        --role="roles/aiplatform.user" \
        --condition=None \
        &> /dev/null; then
        log_success "Permissions granted to $sa_email"
    else
        log_error "Failed to grant permissions"
        exit 1
    fi
}

download_service_account_key() {
    log_info "Downloading service account key..."

    local sa_email="$GCP_SA_NAME@$GCP_PROJECT_ID.iam.gserviceaccount.com"
    local key_file="$PROJECT_ROOT/causa-backend-sa-key.json"

    if [ -f "$key_file" ]; then
        log_warn "Key file already exists: $key_file"
        read -p "Overwrite? (y/N): " -n 1 -r
        echo
        if [[ ! $REPLY =~ ^[Yy]$ ]]; then
            log_info "Using existing key file"
            return 0
        fi
    fi

    if gcloud iam service-accounts keys create "$key_file" \
        --iam-account="$sa_email" \
        --project="$GCP_PROJECT_ID"; then
        log_success "Service account key downloaded: $key_file"
        log_warn "SECURITY: This file contains sensitive credentials!"
        log_warn "DO NOT commit to Git. Delete after creating K8s secret."
    else
        log_error "Failed to download service account key"
        exit 1
    fi
}

create_k8s_secrets() {
    log_info "Creating Kubernetes secrets in namespace: $K8S_NAMESPACE..."

    # Check if namespace exists
    if ! kubectl get namespace "$K8S_NAMESPACE" &> /dev/null; then
        log_warn "Namespace '$K8S_NAMESPACE' does not exist"
        log_info "Creating namespace..."
        kubectl create namespace "$K8S_NAMESPACE"
    fi

    # Secret 1: Vertex Project ID
    log_info "Creating secret: causa-llm-secrets"
    if kubectl create secret generic causa-llm-secrets \
        --from-literal=VERTEX_PROJECT_ID="$GCP_PROJECT_ID" \
        -n "$K8S_NAMESPACE" \
        --dry-run=client -o yaml | kubectl apply -f -; then
        log_success "Secret created: causa-llm-secrets"
    else
        log_error "Failed to create causa-llm-secrets"
        exit 1
    fi

    # Secret 2: GCP Service Account Key (production only)
    if [ "$ENV_TYPE" = "production" ]; then
        local key_file="$PROJECT_ROOT/causa-backend-sa-key.json"

        if [ ! -f "$key_file" ]; then
            log_error "Key file not found: $key_file"
            exit 1
        fi

        log_info "Creating secret: gcp-sa-key"
        if kubectl create secret generic gcp-sa-key \
            --from-file=key.json="$key_file" \
            -n "$K8S_NAMESPACE" \
            --dry-run=client -o yaml | kubectl apply -f -; then
            log_success "Secret created: gcp-sa-key"
        else
            log_error "Failed to create gcp-sa-key"
            exit 1
        fi
    fi
}

update_configmap() {
    log_info "Updating ConfigMap..."

    local configmap_file="$DEPLOYMENT_DIR/base/configmap.yaml"

    if [ ! -f "$configmap_file" ]; then
        log_error "ConfigMap not found: $configmap_file"
        exit 1
    fi

    # Backup original
    cp "$configmap_file" "$configmap_file.bak"

    # Update values using sed (portable)
    sed -i.tmp "s|LLM_PROVIDER:.*|LLM_PROVIDER: \"vertex-ai-anthropic\"|" "$configmap_file"
    sed -i.tmp "s|LLM_MODEL_NAME:.*|LLM_MODEL_NAME: \"$LLM_MODEL\"|" "$configmap_file"
    sed -i.tmp "s|VERTEX_LOCATION:.*|VERTEX_LOCATION: \"$VERTEX_LOCATION\"|" "$configmap_file"
    rm -f "$configmap_file.tmp"

    log_success "ConfigMap updated: $configmap_file"
}

create_deployment_patch() {
    if [ "$ENV_TYPE" != "production" ]; then
        return 0
    fi

    log_info "Creating deployment patch for service account key mount..."

    local overlay_dir="$DEPLOYMENT_DIR/overlays/openshift"
    local patch_file="$overlay_dir/deployment-vertex-patch.yaml"

    mkdir -p "$overlay_dir"

    cat > "$patch_file" << 'EOF'
apiVersion: apps/v1
kind: Deployment
metadata:
  name: causa-backend
spec:
  template:
    spec:
      containers:
      - name: causa-backend
        env:
        - name: GOOGLE_APPLICATION_CREDENTIALS
          value: /var/secrets/google/key.json
        volumeMounts:
        - name: gcp-sa-key
          mountPath: /var/secrets/google
          readOnly: true
      volumes:
      - name: gcp-sa-key
        secret:
          secretName: gcp-sa-key
          items:
          - key: key.json
            path: key.json
EOF

    log_success "Patch created: $patch_file"

    # Update kustomization.yaml
    local kustomization_file="$overlay_dir/kustomization.yaml"

    if [ -f "$kustomization_file" ]; then
        if ! grep -q "deployment-vertex-patch.yaml" "$kustomization_file"; then
            log_info "Adding patch to kustomization.yaml..."
            # Add to patchesStrategicMerge if not already there
            if grep -q "patchesStrategicMerge:" "$kustomization_file"; then
                sed -i.tmp '/patchesStrategicMerge:/a\
  - deployment-vertex-patch.yaml' "$kustomization_file"
            else
                echo "" >> "$kustomization_file"
                echo "patchesStrategicMerge:" >> "$kustomization_file"
                echo "  - deployment-vertex-patch.yaml" >> "$kustomization_file"
            fi
            rm -f "$kustomization_file.tmp"
            log_success "Kustomization updated"
        fi
    fi
}

show_summary() {
    echo ""
    log_success "══════════════════════════════════════════════════════════════"
    log_success "  Vertex AI Setup Complete!"
    log_success "══════════════════════════════════════════════════════════════"
    echo ""
    log_info "Configuration:"
    log_info "  Environment:     $ENV_TYPE"
    log_info "  GCP Project:     $GCP_PROJECT_ID"
    log_info "  Namespace:       $K8S_NAMESPACE"
    log_info "  Model:           $LLM_MODEL"
    log_info "  Location:        $VERTEX_LOCATION"
    echo ""

    if [ "$ENV_TYPE" = "local" ]; then
        log_info "Next steps (Local):"
        log_info "  1. Run locally:"
        log_info "     export VERTEX_PROJECT_ID=$GCP_PROJECT_ID"
        log_info "     export LLM_PROVIDER=vertex-ai-anthropic"
        log_info "     ./mvnw quarkus:dev"
        echo ""
        log_info "  2. Or deploy to Kind:"
        log_info "     kubectl apply -k deployment/kubernetes/overlays/kind"
    else
        log_info "Next steps (Production):"
        log_info "  1. Deploy to OpenShift:"
        log_info "     oc apply -k deployment/kubernetes/overlays/openshift"
        echo ""
        log_info "  2. Watch rollout:"
        log_info "     oc rollout status deployment/causa-backend -n $K8S_NAMESPACE"
        echo ""
        log_info "  3. Check logs:"
        log_info "     oc logs -f deployment/causa-backend -n $K8S_NAMESPACE | grep LLM"
        echo ""
        log_warn "  4. SECURITY: Delete local key file after deployment:"
        log_warn "     shred -u $PROJECT_ROOT/causa-backend-sa-key.json"
    fi

    echo ""
    log_info "Documentation:"
    log_info "  Local Setup:      docs/llm/vertex-ai-local-setup.md"
    log_info "  Production Setup: docs/llm/vertex-ai-openshift-setup.md"
    echo ""
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
            --sa-name)
                GCP_SA_NAME="$2"
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

    if [ "$ENV_TYPE" != "local" ] && [ "$ENV_TYPE" != "production" ]; then
        log_error "Invalid environment type: $ENV_TYPE"
        log_error "Must be 'local' or 'production'"
        exit 1
    fi

    # Run setup
    check_prerequisites
    validate_gcp_project
    check_vertex_ai_api

    if [ "$ENV_TYPE" = "local" ]; then
        setup_local_adc
    else
        create_gcp_service_account
        grant_vertex_ai_permissions
        download_service_account_key
    fi

    create_k8s_secrets
    update_configmap
    create_deployment_patch

    show_summary
}

main "$@"
