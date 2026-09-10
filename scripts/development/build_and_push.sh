#!/bin/bash
set -euo pipefail

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Usage function
usage() {
    local exit_code="${1:-1}"
    echo "Usage: $0 -i IMAGE_NAME [OPTIONS]"
    echo ""
    echo "Build and push Docker images for causa-backend with multi-architecture support."
    echo "Uses podman or docker buildx + Dockerfile.jvm for multi-arch builds (amd64 + arm64)."
    echo ""
    echo "Options:"
    echo "  -i IMAGE_NAME    (REQUIRED) Full image name (registry/repository:tag)"
    echo "  -b BUILD         Build image true/false (default: true)"
    echo "  -p PUSH          Push image true/false (default: false)"
    echo "  -l PLATFORMS     Target platforms (default: linux/amd64,linux/arm64)"
    echo "  -c CLEAN         Run clean build true/false (default: true)"
    echo "  -s SKIP_TESTS    Skip tests during build true/false (default: false)"
    echo "  -d TOOL          Container tool: podman or docker (default: auto-detect)"
    echo "  -h               Show this help message"
    echo ""
    echo "Image naming conventions:"
    echo "  Production releases : quay.io/causaai/causa:<version>"
    echo "  Overnight/CI builds : quay.io/causaai/causa-dev:<tag>"
    echo "  Fork/custom builds  : quay.io/<your-org>/<your-repo>:<tag>"
    echo ""
    echo "  Use causaai/causa for stable production images."
    echo "  Use causaai/causa-dev for pipeline automation and nightly builds."
    echo "  Use your own fork registry path when building and testing custom changes."
    echo ""
    echo "Environment Variables (alternative to -i):"
    echo "  IMAGE_NAME       Full image name (required if -i is not passed)"
    echo "  BUILD_IMAGE      Build image (true/false)"
    echo "  PUSH_IMAGE       Push image (true/false)"
    echo "  PLATFORMS        Target platforms"
    echo "  CLEAN_BUILD      Clean build (true/false)"
    echo "  SKIP_TESTS       Skip tests (true/false)"
    echo "  CONTAINER_TOOL   Container tool: podman or docker"
    echo ""
    echo "Examples:"
    echo "  # Production build and push"
    echo "  $0 -i quay.io/causaai/causa:1.0.0 -b true -p true"
    echo ""
    echo "  # Build only with custom tag (no push)"
    echo "  $0 -i quay.io/causaai/causa:1.0.0 -b true"
    echo ""
    echo "  # Build for AMD64 only and push"
    echo "  $0 -i quay.io/causaai/causa:1.0.0 -l linux/amd64 -p true"
    echo ""
    echo ""
    echo "  # Using environment variable"
    echo "  IMAGE_NAME=quay.io/causaai/causa-dev:0.0.1 PUSH_IMAGE=true ./build_and_push.sh"
    echo ""
    echo "Note: -i (or IMAGE_NAME env var) is required. Command-line flags take precedence over environment variables."
    exit "${exit_code}"
}

# Validate boolean values
validate_boolean() {
    local value="$1"
    local flag="$2"
    if [[ ! "$value" =~ ^(true|false)$ ]]; then
        echo -e "${RED}Error: $flag must be 'true' or 'false', got: '$value'${NC}" >&2
        usage 1
    fi
}

# Print colored message
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Trap to print a failure banner on any unexpected non-zero exit.
# Works with `set -e` — fires whenever the script aborts early.
on_exit() {
    local code=$?
    if [ $code -ne 0 ]; then
        echo ""
        print_error "=== Build Failed ==="
        print_error "Build process failed. Check the logs above for details."
        echo ""
    fi
    exit $code
}
trap on_exit EXIT

# Resolve the project root pom.xml relative to this script's location,
# regardless of the working directory the script is invoked from.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"

# Resolve application version from pom.xml (used as the default image tag).
# Uses mvn help:evaluate for an authoritative project.version read — avoids
# accidentally picking up a parent or plugin <version> via grep.
# If the version contains SNAPSHOT, appends a UTC timestamp so each dev build
# gets a unique, sortable tag (e.g. 0.0.1-SNAPSHOT-20250127143012).
resolve_app_version() {
    local pom="${PROJECT_ROOT}/pom.xml"
    local ver="latest"
    if [ -f "${pom}" ]; then
        local mvnw="${PROJECT_ROOT}/mvnw"
        local mvn_cmd="mvn"
        [ -f "${mvnw}" ] && mvn_cmd="${mvnw}"
        ver=$(cd "${PROJECT_ROOT}" && \
              ${mvn_cmd} help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null)
        ver="${ver:-latest}"
    fi
    if [[ "$ver" == *SNAPSHOT* ]]; then
        local ts
        ts=$(date -u +"%Y%m%d%H%M%S")
        ver="${ver}-${ts}"
    fi
    echo "$ver"
}

# Auto-detect container tool: prefer podman if available, fall back to docker
resolve_container_tool() {
    if command -v podman &>/dev/null; then
        echo "podman"
    elif command -v docker &>/dev/null; then
        echo "docker"
    else
        echo ""
    fi
}

# Default values from environment or hardcoded defaults
BUILD_IMAGE="${BUILD_IMAGE:-true}"
PUSH_IMAGE="${PUSH_IMAGE:-false}"
PLATFORMS="${PLATFORMS:-linux/amd64,linux/arm64}"
CLEAN_BUILD="${CLEAN_BUILD:-true}"
SKIP_TESTS="${SKIP_TESTS:-false}"
IMAGE_NAME="${IMAGE_NAME:-}"
CONTAINER_TOOL="${CONTAINER_TOOL:-$(resolve_container_tool)}"

# Parse command line arguments (these override environment variables)
while getopts "i:b:p:l:c:s:d:h" opt; do
    case ${opt} in
        i )
            IMAGE_NAME="$OPTARG"
            ;;
        b )
            BUILD_IMAGE="$OPTARG"
            ;;
        p )
            PUSH_IMAGE="$OPTARG"
            ;;
        l )
            PLATFORMS="$OPTARG"
            ;;
        c )
            CLEAN_BUILD="$OPTARG"
            ;;
        s )
            SKIP_TESTS="$OPTARG"
            ;;
        d )
            CONTAINER_TOOL="$OPTARG"
            ;;
        h )
            usage 0
            ;;
        \? )
            print_error "Invalid option: -$OPTARG"
            usage 1
            ;;
    esac
done

# Require IMAGE_NAME — must be supplied via -i or the IMAGE_NAME environment variable
if [ -z "$IMAGE_NAME" ]; then
    print_error "Option -i IMAGE_NAME is required."
    print_error "  Production:  -i quay.io/causaai/causa:<version>"
    print_error "  Overnight:   -i quay.io/causaai/causa-dev:<tag>"
    usage 1
fi

# Validate boolean flags
validate_boolean "$BUILD_IMAGE" "BUILD_IMAGE (-b)"
validate_boolean "$PUSH_IMAGE"  "PUSH_IMAGE (-p)"
validate_boolean "$CLEAN_BUILD" "CLEAN_BUILD (-c)"
validate_boolean "$SKIP_TESTS"  "SKIP_TESTS (-s)"

# Validate CONTAINER_TOOL
if [[ ! "$CONTAINER_TOOL" =~ ^(podman|docker)$ ]]; then
    print_error "CONTAINER_TOOL (-d) must be 'podman' or 'docker', got: '${CONTAINER_TOOL}'"
    usage 1
fi

# Reject the invalid combination: push requested but no image will be built
if [ "$PUSH_IMAGE" = "true" ] && [ "$BUILD_IMAGE" = "false" ]; then
    print_error "PUSH_IMAGE=true requires BUILD_IMAGE=true. Cannot push without building."
    exit 1
fi

# Validate project root structure
if [ ! -f "${PROJECT_ROOT}/pom.xml" ]; then
    print_error "pom.xml not found at ${PROJECT_ROOT}."
    exit 1
fi

# Check if Maven wrapper exists
if [ ! -f "${PROJECT_ROOT}/mvnw" ]; then
    print_error "Maven wrapper (mvnw) not found at ${PROJECT_ROOT}."
    exit 1
fi

DOCKERFILE="${PROJECT_ROOT}/src/main/docker/Dockerfile.jvm"

# Container tool and Dockerfile are only required when actually building an image
if [ "$BUILD_IMAGE" = "true" ]; then
    if ! command -v "${CONTAINER_TOOL}" &>/dev/null; then
        print_error "'${CONTAINER_TOOL}' is not installed or not on PATH. Multi-arch builds require ${CONTAINER_TOOL}."
        exit 1
    fi
    if ! "${CONTAINER_TOOL}" buildx --help &>/dev/null 2>&1; then
        print_error "'${CONTAINER_TOOL} buildx' is not available."
        if [ "$CONTAINER_TOOL" = "podman" ]; then
            print_error "Upgrade podman or install the buildx plugin. See: https://podman.io/getting-started/installation"
        else
            print_error "Install Docker Desktop or the buildx plugin. See: https://docs.docker.com/buildx/working-with-buildx/"
        fi
        exit 1
    fi
    if [ ! -f "${DOCKERFILE}" ]; then
        print_error "Dockerfile not found at ${DOCKERFILE}."
        exit 1
    fi
fi

# Run all Maven commands from the project root
cd "${PROJECT_ROOT}"

# Make Maven wrapper executable
chmod +x ./mvnw

# Display configuration
echo ""
print_info "=== Build Configuration ==="
print_info "Image Name:      ${IMAGE_NAME}"
print_info "Container Tool:  ${CONTAINER_TOOL}"
print_info "Build:           ${BUILD_IMAGE}"
print_info "Push:            ${PUSH_IMAGE}"
print_info "Platforms:       ${PLATFORMS}"
print_info "Clean Build:     ${CLEAN_BUILD}"
print_info "Skip Tests:      ${SKIP_TESTS}"
if [ "$BUILD_IMAGE" = "true" ]; then
    print_info "Dockerfile:      ${DOCKERFILE}"
fi
echo ""

# Warn if pushing is enabled
if [ "$PUSH_IMAGE" = "true" ]; then
    print_warn "Push is enabled. Image will be pushed to registry."
    print_warn "Make sure you are authenticated to ${REGISTRY}"
    echo ""
fi

# ── Step 1: Maven package ─────────────────────────────────────────────────────
MAVEN_CMD=("./mvnw")

if [ "$CLEAN_BUILD" = "true" ]; then
    MAVEN_CMD+=("clean")
fi

MAVEN_CMD+=("package" "-Dquarkus.container-image.build=false")

if [ "$SKIP_TESTS" = "true" ]; then
    MAVEN_CMD+=("-DskipTests")
fi

if [ "$BUILD_IMAGE" = "true" ]; then
    print_info "Step 1/2 — Maven package"
else
    print_info "Step 1/1 — Maven package"
fi
print_info "Executing: ${MAVEN_CMD[*]}"
echo ""
"${MAVEN_CMD[@]}"

# ── Step 2: multi-arch build (and optional push) ─────────────────────────────
if [ "$BUILD_IMAGE" = "true" ]; then
    echo ""

    MANIFEST_NAME="${IMAGE_NAME}"

    if [ "$CONTAINER_TOOL" = "podman" ]; then
        # Remove any stale local manifest so podman doesn't error on re-runs.
        podman manifest rm "${MANIFEST_NAME}" 2>/dev/null || true

        # --manifest stores a multi-arch manifest locally; can be inspected
        # before pushing with: podman manifest inspect <name>
        BUILD_CMD=(
            "podman" "buildx" "build"
            "--platform" "${PLATFORMS}"
            "--manifest" "${MANIFEST_NAME}"
            "-f" "${DOCKERFILE}"
            "${PROJECT_ROOT}"
        )

        print_info "Step 2/2 — podman buildx multi-arch image build"
        print_info "Executing: ${BUILD_CMD[*]}"
        echo ""
        "${BUILD_CMD[@]}"

        if [ "$PUSH_IMAGE" = "true" ]; then
            echo ""
            print_info "Pushing multi-arch manifest to registry..."
            podman manifest push --all "${MANIFEST_NAME}" "docker://${MANIFEST_NAME}"
        fi
    else
        # docker buildx: build (and push in one step if requested, otherwise
        # load into the local daemon — note docker buildx cannot load
        # multi-platform images into the local daemon, so --push is required
        # when PLATFORMS contains more than one entry).
        BUILD_CMD=(
            "docker" "buildx" "build"
            "--platform" "${PLATFORMS}"
            "-t" "${MANIFEST_NAME}"
            "-f" "${DOCKERFILE}"
        )

        if [ "$PUSH_IMAGE" = "true" ]; then
            BUILD_CMD+=("--push")
        else
            # Single-platform builds can be loaded locally; multi-platform
            # cannot — warn the user if that is the case.
            platform_count=$(echo "${PLATFORMS}" | tr ',' '\n' | wc -l | tr -d ' ')
            if [ "${platform_count}" -gt 1 ]; then
                print_warn "docker buildx cannot load multi-platform images into the local daemon."
                print_warn "The image will be built but NOT loaded locally. Use -p true to push instead."
            else
                BUILD_CMD+=("--load")
            fi
        fi

        BUILD_CMD+=("${PROJECT_ROOT}")

        print_info "Step 2/2 — docker buildx multi-arch image build"
        print_info "Executing: ${BUILD_CMD[*]}"
        echo ""
        "${BUILD_CMD[@]}"
    fi
fi

# ── Summary ───────────────────────────────────────────────────────────────────
echo ""
print_info "=== Build Summary ==="
print_info "✓ Maven package completed successfully"

if [ "$BUILD_IMAGE" = "true" ]; then
    if [ "$PUSH_IMAGE" = "true" ]; then
        print_info "✓ Multi-arch image pushed: ${IMAGE_NAME}"
        print_info "  Platforms: ${PLATFORMS}"
        if [ "$CONTAINER_TOOL" = "podman" ]; then
            print_info "  Verify: podman manifest inspect ${IMAGE_NAME}"
        else
            print_info "  Verify: docker manifest inspect ${IMAGE_NAME}"
        fi
    else
        if [ "$CONTAINER_TOOL" = "podman" ]; then
            print_info "✓ Multi-arch manifest built locally: ${IMAGE_NAME}"
            print_info "  Platforms: ${PLATFORMS}"
            print_info "  Inspect locally: podman manifest inspect ${IMAGE_NAME}"
        else
            print_info "✓ Image built: ${IMAGE_NAME}"
            print_info "  Platforms: ${PLATFORMS}"
        fi
        print_warn "Image not pushed (PUSH_IMAGE=false). Re-run with -p true to push."
    fi
fi
echo ""
