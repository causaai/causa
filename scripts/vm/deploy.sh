#!/usr/bin/env bash
# ============================================================
# causa-backend — core deployment logic (Verbose Logging)
# ============================================================
set -euo pipefail

# ── Script Location & Logging Setup ──────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
LOG_FILE="${SCRIPT_DIR}/deploy.log"

# Clear the log file at the start of each run
: > "$LOG_FILE"

# Helper 1: Print to BOTH console and log file
info() {
    echo -e "$*" | tee -a "$LOG_FILE"
}

# Helper 2: Run command silently on console, but verbosely in log
run_verbose() {
    echo "[$(date '+%H:%M:%S')] Executing: $*" >> "$LOG_FILE"
    # Execute the command, appending stdout and stderr to the log
    "$@" >> "$LOG_FILE" 2>&1
}

if [ -f "${SCRIPT_DIR}/causa-backend.service" ]; then
    SERVICE_TEMPLATE="${SCRIPT_DIR}/causa-backend.service"
else
    SERVICE_TEMPLATE="${PROJECT_ROOT}/deployment/vm/causa-backend.service"
fi

# ── Defaults ─────────────────────────────────────────────────
INSTALL_DIR="/opt/causa"
USE_FAST_JAR=false
ENV_PATH=""
SKIP_BUILD=false
EXPLICIT_JAR=""

# ── Argument Parsing ─────────────────────────────────────────
while [[ $# -gt 0 ]]; do
    case "$1" in
        --env-path)    ENV_PATH="$2";       shift 2 ;;
        --dir)         INSTALL_DIR="$2";    shift 2 ;;
        --uber-jar)    USE_FAST_JAR=false;  shift ;;
        --fast-jar)    USE_FAST_JAR=true;   shift ;;
        --skip-build)  SKIP_BUILD=true;     shift ;;
        --jar-path)    EXPLICIT_JAR="$2"; SKIP_BUILD=true; shift 2 ;;
        --help|-h)
            echo "Usage: sudo bash deploy.sh --env-path <path> [OPTIONS]"
            exit 0
            ;;
        *)
            echo "[ERROR] Unknown argument: $1" >&2
            exit 1
            ;;
    esac
done

info "======================================================"
info "  Deploying Causa Backend"
info "======================================================"
info "  Install dir : ${INSTALL_DIR}"
info "  Env file    : ${ENV_PATH}"
info "  Log file    : ${LOG_FILE}"
info "======================================================"
info ""

# ── Step 1: Validate ─────────────────────────────────────────
info "=> [1/7] Validating configuration..."
if [ -z "$ENV_PATH" ] || [ ! -f "$ENV_PATH" ]; then
    info "❌ ERROR: --env-path is missing or file not found ($ENV_PATH)"
    exit 1
fi

if [ ! -f "$SERVICE_TEMPLATE" ]; then
    info "❌ ERROR: systemd template not found ($SERVICE_TEMPLATE)"
    exit 1
fi
info "✔ Validation passed."

# ── Step 2: Build ────────────────────────────────────────────
info "=> [2/7] Building application (this may take a minute)..."
if $SKIP_BUILD; then
  info "⏭  Skipping build (--skip-build or --jar-path provided)"
else
  cd "$PROJECT_ROOT"
  mvnw="./mvnw"; [ -f "$mvnw" ] || mvnw="mvn"
  run_verbose chmod +x "$mvnw"
  
  jar_flag=""
  $USE_FAST_JAR || jar_flag="-Dquarkus.package.jar.type=uber-jar"
  
  if ! run_verbose $mvnw clean package -DskipTests -Dquarkus.container-image.build=false $jar_flag; then
    info "❌ ERROR: Maven build failed!"
    info "   Check the log for details: tail -n 50 $LOG_FILE"
    exit 1
  fi
  
  info "✔ Build complete."
fi

# ── Step 3: Locate Artifact ──────────────────────────────────
info "=> [3/7] Locating artifact..."
if [ -n "$EXPLICIT_JAR" ]; then
    RUNNER="$EXPLICIT_JAR"
elif $USE_FAST_JAR; then
    RUNNER="${PROJECT_ROOT}/target/quarkus-app/quarkus-run.jar"
else
    RUNNER=$(ls "${PROJECT_ROOT}"/target/causa-backend-*-runner.jar 2>/dev/null | head -1 || true)
fi

if [ -z "$RUNNER" ] || [ ! -f "$RUNNER" ]; then
    info "❌ ERROR: Artifact not found at: $RUNNER"
    exit 1
fi
info "✔ Found artifact: $RUNNER"

# ── Step 4: Prepare & Copy Files ─────────────────────────────
info "=> [4/7] Copying files to $INSTALL_DIR..."
run_verbose mkdir -p "$INSTALL_DIR"
run_verbose chmod 755 "$INSTALL_DIR"

if $USE_FAST_JAR; then
    run_verbose rsync -a --delete "${PROJECT_ROOT}/target/quarkus-app/" "${INSTALL_DIR}/quarkus-app/"
else
    run_verbose cp "$RUNNER" "${INSTALL_DIR}/causa-backend-runner.jar"
fi

run_verbose cp "$ENV_PATH" "${INSTALL_DIR}/.env"
run_verbose chmod 600 "${INSTALL_DIR}/.env"
info "✔ Files copied successfully."

# ── Step 5: Install & Start Service ──────────────────────────
info "=> [5/7] Configuring and starting systemd service..."

if $USE_FAST_JAR; then
    VM_JAR_PATH="${INSTALL_DIR}/quarkus-app/quarkus-run.jar"
else
    VM_JAR_PATH="${INSTALL_DIR}/causa-backend-runner.jar"
fi

VM_USER="${SUDO_USER:-$(whoami)}"
UNIT_DEST="/etc/systemd/system/causa-backend.service"

sed \
    -e "s|__JAR_PATH__|${VM_JAR_PATH}|g" \
    -e "s|__VM_DIR__|${INSTALL_DIR}|g" \
    -e "s|__VM_USER__|${VM_USER}|g" \
    "$SERVICE_TEMPLATE" > /tmp/causa-backend.service

run_verbose mv /tmp/causa-backend.service "$UNIT_DEST"
run_verbose systemctl daemon-reload
run_verbose systemctl enable causa-backend

info "   Restarting service (this may take a moment)..."
run_verbose systemctl restart causa-backend --no-ask-password
info "✔ Service restarted."

# ── Step 6: Health Check ─────────────────────────────────────
info "=> [6/7] Waiting for application to become ready..."
PORT="${CAUSA_PORT:-8080}"
URL="http://localhost:${PORT}/q/health/ready"

ATTEMPTS=0
MAX_ATTEMPTS=12
READY=false

while [ $ATTEMPTS -lt $MAX_ATTEMPTS ]; do
    # Log the curl attempt verbosely just in case it fails weirdly
    run_verbose echo "Attempting curl to $URL"
    if curl -sf "$URL" >> "$LOG_FILE" 2>&1; then
        READY=true
        break
    fi
    ATTEMPTS=$((ATTEMPTS+1))
    info "   Attempt $ATTEMPTS/$MAX_ATTEMPTS - Not ready yet, waiting 10 seconds..."
    sleep 10
done

if [ "$READY" = true ]; then
    info "✔ Health check passed!"
    info ""
    
    # ── Step 7: Exposing Connections ─────────────────────────
    info "=> [7/7] Updating firewall rules..."
    run_verbose firewall-cmd --add-port=8080/tcp --permanent
    run_verbose firewall-cmd --reload
    info "✔ Firewall rules updated."
    
    LOCAL_IP=$(hostname -I | awk '{print $1}')
    
    info ""
    info "------------------------------------------------------"
    info "🎉 DEPLOYMENT SUCCESSFUL!"
    info "------------------------------------------------------"
    info "Service is reachable at :"
    info "  - Localhost : http://localhost:${PORT}"
    info "  - Network   : http://${LOCAL_IP}:${PORT}"
    info ""
    info "Health check URL        : ${URL}"
    info "Status: sudo systemctl status causa-backend"
    info "Logs:   sudo journalctl -u causa-backend -f"
else
    info "❌ ERROR: Application did not become ready within 60 seconds."
    info "Check logs to see what went wrong: sudo journalctl -u causa-backend -n 50"
    exit 1
fi
