#!/bin/bash
set -euo pipefail

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Defaults
DEFAULT_IMAGE="quay.io/rh-ee-shesaxen/postgres-pgvector"
DEFAULT_TAG="17"
DEFAULT_PUSH="false"
DEFAULT_PLATFORM="linux/amd64,linux/arm64"

# Variables
IMAGE_NAME="${IMAGE_NAME:-$DEFAULT_IMAGE}"
IMAGE_TAG="${IMAGE_TAG:-$DEFAULT_TAG}"
PUSH_IMAGE="${PUSH_IMAGE:-$DEFAULT_PUSH}"
PLATFORM="${PLATFORM:-$DEFAULT_PLATFORM}"

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

Build PostgreSQL image with pgvector extension
Works with Docker and Podman

Options:
  -i IMAGE    Full image name (default: $DEFAULT_IMAGE)
  -t TAG      Image tag (default: $DEFAULT_TAG)
  -p          Push image to registry (default: false)
  -l PLATFORM Target platforms (default: $DEFAULT_PLATFORM)
  -h          Show this help

Examples:
  # Build only (no push)
  $0

  # Build and push
  $0 -p

  # Build with custom tag
  $0 -t 17.1 -p

  # Build for single platform
  $0 -l linux/amd64 -p

Build Tool Detection:
  - Docker: Uses 'docker buildx' for multi-platform builds
  - Podman: Uses 'podman manifest' for multi-platform builds
  
Multi-Platform Build Methods:
  - Docker: docker buildx build --platform linux/amd64,linux/arm64
  - Podman: podman build --manifest + podman manifest push

EOF
    exit "${1:-0}"
}

# Parse arguments
while getopts "i:t:pl:h" opt; do
    case ${opt} in
        i) IMAGE_NAME="$OPTARG" ;;
        t) IMAGE_TAG="$OPTARG" ;;
        p) PUSH_IMAGE="true" ;;
        l) PLATFORM="$OPTARG" ;;
        h) usage 0 ;;
        \?) print_error "Invalid option: -$OPTARG"; usage 1 ;;
    esac
done

# Construct full image reference
FULL_IMAGE="${IMAGE_NAME}:${IMAGE_TAG}"

# Validate paths
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
DOCKERFILE_PATH="${PROJECT_ROOT}/deployment/postgres/Dockerfile"

if [ ! -f "${DOCKERFILE_PATH}" ]; then
    print_error "Dockerfile not found at: ${DOCKERFILE_PATH}"
    exit 1
fi

# Check docker
if ! command -v docker &> /dev/null; then
    print_error "'docker' command is not available"
    print_error "Please install Docker or Podman with docker CLI"
    exit 1
fi

# Detect runtime
RUNTIME_INFO=$(docker --version 2>&1 || echo "unknown")
USE_PODMAN_MANIFEST=false
USE_BUILDX=false

if echo "$RUNTIME_INFO" | grep -qi "podman"; then
    RUNTIME_TYPE="Podman"
    # For Podman, use podman manifest for multi-platform builds
    if command -v podman &> /dev/null; then
        PODMAN_VERSION=$(podman --version | awk '{print $3}')
        print_info "Detected Podman version: ${PODMAN_VERSION}"
        # Check if multi-platform build is requested
        if [[ "$PLATFORM" == *","* ]]; then
            USE_PODMAN_MANIFEST=true
        fi
    else
        print_error "Podman detected via docker CLI but podman command not found"
        print_error "Please ensure podman is properly installed"
        exit 1
    fi
else
    RUNTIME_TYPE="Docker"
    # For Docker, check for buildx support for multi-platform builds
    if [[ "$PLATFORM" == *","* ]]; then
        if docker buildx version &> /dev/null 2>&1; then
            USE_BUILDX=true
        else
            print_error "Multi-platform build requested but buildx not available"
            print_error "Standard Docker build does not support multiple platforms"
            print_error "Please install Docker buildx or specify a single platform with -l"
            print_error "Example: $0 -l linux/amd64"
            exit 1
        fi
    fi
fi

# Display configuration
print_header "Build Configuration"
print_info "Runtime:     ${RUNTIME_TYPE}"
print_info "Image:       ${FULL_IMAGE}"
print_info "Push:        ${PUSH_IMAGE}"
print_info "Platforms:   ${PLATFORM}"
print_info "Dockerfile:  ${DOCKERFILE_PATH}"
if [ "$USE_PODMAN_MANIFEST" = true ]; then
    print_info "Build Method: podman build + manifest (multi-platform)"
elif [ "$USE_BUILDX" = true ]; then
    print_info "Build Method: docker buildx (multi-platform)"
else
    print_info "Build Method: standard build"
fi
echo ""

# Build the image
print_header "Building PostgreSQL Image"
print_info "Starting build process..."
echo ""

if [ "$USE_PODMAN_MANIFEST" = true ]; then
    # Multi-platform build with podman manifest
    MANIFEST_NAME="${FULL_IMAGE}"
    
    # Parse platforms
    IFS=',' read -ra PLATFORMS <<< "$PLATFORM"
    
    # Remove existing manifest if it exists
    if podman manifest exists "${MANIFEST_NAME}" 2>/dev/null; then
        print_info "Removing existing manifest: ${MANIFEST_NAME}"
        podman manifest rm "${MANIFEST_NAME}" || {
            print_warn "Failed to remove existing manifest, will try to continue"
        }
    fi
    
    print_info "Creating manifest: ${MANIFEST_NAME}"
    podman manifest create "${MANIFEST_NAME}" || {
        print_error "Failed to create manifest"
        exit 1
    }
    
    # Build for each platform
    for PLAT in "${PLATFORMS[@]}"; do
        echo ""
        print_info "Building for platform: ${PLAT}"
        BUILD_CMD="podman build"
        BUILD_CMD="${BUILD_CMD} --platform ${PLAT}"
        BUILD_CMD="${BUILD_CMD} --manifest ${MANIFEST_NAME}"
        BUILD_CMD="${BUILD_CMD} -f ${DOCKERFILE_PATH}"
        BUILD_CMD="${BUILD_CMD} ${PROJECT_ROOT}/deployment/postgres"
        
        print_info "Executing: ${BUILD_CMD}"
        echo ""
        
        if eval "${BUILD_CMD}"; then
            print_info "✓ Build completed for ${PLAT}"
        else
            print_error "✗ Build failed for ${PLAT}"
            exit 1
        fi
    done
    
    echo ""
    print_info "✓ All platform builds completed successfully"
    
    # Push the manifest if requested
    if [ "$PUSH_IMAGE" = "true" ]; then
        echo ""
        print_header "Pushing Manifest to Registry"
        print_info "Pushing ${MANIFEST_NAME}..."
        echo ""
        
        if podman manifest push "${MANIFEST_NAME}" "docker://${MANIFEST_NAME}"; then
            echo ""
            print_info "✓ Manifest pushed successfully"
        else
            echo ""
            print_error "✗ Manifest push failed"
            exit 1
        fi
    fi
elif [ "$USE_BUILDX" = true ]; then
    # Multi-platform build with buildx
    BUILD_CMD="docker buildx build"
    BUILD_CMD="${BUILD_CMD} --platform ${PLATFORM}"
    
    if [ "$PUSH_IMAGE" = "true" ]; then
        BUILD_CMD="${BUILD_CMD} --push"
    else
        BUILD_CMD="${BUILD_CMD} --load"
    fi
    
    BUILD_CMD="${BUILD_CMD} -t ${FULL_IMAGE}"
    BUILD_CMD="${BUILD_CMD} -f ${DOCKERFILE_PATH}"
    BUILD_CMD="${BUILD_CMD} ${PROJECT_ROOT}/deployment/postgres"
    
    print_info "Executing: ${BUILD_CMD}"
    echo ""
    
    if eval "${BUILD_CMD}"; then
        echo ""
        print_info "✓ Build completed successfully"
        if [ "$PUSH_IMAGE" = "true" ]; then
            print_info "✓ Image pushed to registry"
        fi
    else
        echo ""
        print_error "✗ Build failed"
        exit 1
    fi
else
    # Standard build (single platform or no multi-platform support)
    if [ "$RUNTIME_TYPE" = "Podman" ]; then
        BUILD_CMD="podman build"
    else
        BUILD_CMD="docker build"
    fi
    
    BUILD_CMD="${BUILD_CMD} --platform ${PLATFORM}"
    BUILD_CMD="${BUILD_CMD} -t ${FULL_IMAGE}"
    BUILD_CMD="${BUILD_CMD} -f ${DOCKERFILE_PATH}"
    BUILD_CMD="${BUILD_CMD} ${PROJECT_ROOT}/deployment/postgres"
    
    print_info "Executing: ${BUILD_CMD}"
    echo ""
    
    if eval "${BUILD_CMD}"; then
        echo ""
        print_info "✓ Build completed successfully"
    else
        echo ""
        print_error "✗ Build failed"
        exit 1
    fi
    
    # Push the image if requested
    if [ "$PUSH_IMAGE" = "true" ]; then
        echo ""
        print_header "Pushing Image to Registry"
        print_info "Pushing ${FULL_IMAGE}..."
        echo ""
        
        if [ "$RUNTIME_TYPE" = "Podman" ]; then
            PUSH_CMD="podman push ${FULL_IMAGE}"
        else
            PUSH_CMD="docker push ${FULL_IMAGE}"
        fi
        
        if eval "${PUSH_CMD}"; then
            echo ""
            print_info "✓ Image pushed successfully"
        else
            echo ""
            print_error "✗ Push failed"
            exit 1
        fi
    fi
fi

# Display summary
echo ""
print_header "Build Summary"
print_info "✓ Runtime: ${RUNTIME_TYPE}"
print_info "✓ Image built: ${FULL_IMAGE}"
print_info "✓ Platforms: ${PLATFORM}"

if [ "$PUSH_IMAGE" = "true" ]; then
    print_info "✓ Image pushed to registry"
    echo ""
    print_info "You can now use this image in your PostgreSQL cluster:"
    print_info "  imageName: ${FULL_IMAGE}"
else
    print_warn "Image was built but not pushed (use -p to push)"
    echo ""
    print_info "To push the image later, run:"
    if [ "$RUNTIME_TYPE" = "Podman" ]; then
        if [ "$USE_PODMAN_MANIFEST" = true ]; then
            print_info "  podman manifest push ${FULL_IMAGE} docker://${FULL_IMAGE}"
        else
            print_info "  podman push ${FULL_IMAGE}"
        fi
    else
        print_info "  docker push ${FULL_IMAGE}"
    fi
fi

echo ""
print_info "To test the image locally:"
if [ "$RUNTIME_TYPE" = "Podman" ]; then
    print_info "  podman run --rm -e POSTGRES_PASSWORD=test ${FULL_IMAGE}"
else
    print_info "  docker run --rm -e POSTGRES_PASSWORD=test ${FULL_IMAGE}"
fi
echo ""

exit 0

