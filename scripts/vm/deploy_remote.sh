#!/usr/bin/env bash
# ============================================================
# causa-backend — remote VM deployment wrapper
# ============================================================
# Handles SSH transport: builds the artifact locally, transfers
# it to the VM via scp, then runs deploy.sh ON the VM.
# All core logic lives in deploy.sh — this script only does SSH.
#
# Usage:
#   bash deploy_remote.sh --host <vm-host> --env-path <path> [OPTIONS]
#
# Required:
#   --host <host>          VM hostname or IP
#   --env-path <path>      Local .env file to transfer (REQUIRED)
#
# Options:
#   --user <user>          SSH user (default: current user)
#   --password <pass>      SSH password via sshpass (or use key auth)
#   --dir <path>           Install directory on VM (default: /opt/causa)
#   --uber-jar             Build and deploy single runner jar (default)
#   --fast-jar             Build and deploy quarkus-app directory layout
#   --skip-build           Skip Maven build, use existing artifact in target/
#   --skip-service         Transfer files only, do not install/restart systemd
#   --help|-h              Show this help
#
# Examples:
#   # Full deploy with password auth
#   bash deploy_remote.sh --host 192.168.1.100 --user root --password 'mypass' --env-path ./prod.env
#
#   # Skip build, uber-jar already in target/
#   bash deploy_remote.sh --host myvm.example.com --env-path ./prod.env --skip-build
#
#   # Key-based auth, custom install dir
#   bash deploy_remote.sh --host myvm.example.com --user ec2-user --dir /opt/causa --env-path ./prod.env
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
SERVICE_TEMPLATE="${PROJECT_ROOT}/deployment/vm/causa-backend.service"
LOG_FILE="${SCRIPT_DIR}/deploy.log"

# ── defaults ─────────────────────────────────────────────────
VM_HOST=""
VM_USER="${USER}"
VM_PASSWORD=""
VM_DIR="/opt/causa"
USE_FAST_JAR=false
ENV_PATH=""
SKIP_BUILD=false
SKIP_SERVICE=false

# ── argument parsing ─────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case "$1" in
    --host)         VM_HOST="$2";       shift 2 ;;
    --user)         VM_USER="$2";       shift 2 ;;
    --password)     VM_PASSWORD="$2";   shift 2 ;;
    --dir)          VM_DIR="$2";        shift 2 ;;
    --uber-jar)     USE_FAST_JAR=false; shift ;;
    --fast-jar)     USE_FAST_JAR=true;  shift ;;
    --env-path)     ENV_PATH="$2";      shift 2 ;;
    --skip-build)   SKIP_BUILD=true;    shift ;;
    --skip-service) SKIP_SERVICE=true;  shift ;;
    --help|-h)
      grep '^#' "$0" | sed 's/^# \?//'
      exit 0
      ;;
    *)
      echo "[ERROR] Unknown argument: $1" >&2
      echo "[ERROR] Usage: bash deploy_remote.sh --host <host> --env-path <path> [OPTIONS]" >&2
      exit 1
      ;;
  esac
done

# ── colours ──────────────────────────────────────────────────
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; BOLD='\033[1m'; NC='\033[0m'
TICK="${GREEN}✔${NC}"
CROSS="${RED}✘${NC}"

# ── logging ──────────────────────────────────────────────────
: > "$LOG_FILE"
log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >> "$LOG_FILE"; }

# ── spinner ──────────────────────────────────────────────────
_SPINNER_PID=""

spinner_start() {
  local msg="$1"
  printf "  ${BOLD}%-45s${NC}" "$msg"
  (
    local frames=('⠋' '⠙' '⠹' '⠸' '⠼' '⠴' '⠦' '⠧' '⠇' '⠏')
    local i=0
    while true; do
      printf "\r  ${BOLD}%-45s${NC} ${YELLOW}%s${NC}" "$msg" "${frames[$((i % ${#frames[@]}))]}"
      sleep 0.1
      ((i++))
    done
  ) &
  _SPINNER_PID=$!
  disown "$_SPINNER_PID"
}

spinner_stop_ok() {
  [[ -n "$_SPINNER_PID" ]] && kill "$_SPINNER_PID" 2>/dev/null; _SPINNER_PID=""
  printf "\r  ${BOLD}%-45s${NC} ${TICK}\n" "$1"
}

spinner_stop_fail() {
  [[ -n "$_SPINNER_PID" ]] && kill "$_SPINNER_PID" 2>/dev/null; _SPINNER_PID=""
  printf "\r  ${BOLD}%-45s${NC} ${CROSS}\n" "$1"
  echo ""
  echo -e "  ${RED}Error details in: ${LOG_FILE}${NC}"
  echo -e "  ${YELLOW}Last 20 lines:${NC}"
  tail -20 "$LOG_FILE" | sed 's/^/    /'
  echo ""
}

run_step() {
  local label="$1"; shift
  spinner_start "$label"
  log "=== STEP: $label ==="
  if "$@" >> "$LOG_FILE" 2>&1; then
    spinner_stop_ok "$label"
    log "=== DONE: $label ==="
  else
    local rc=$?
    spinner_stop_fail "$label"
    log "=== FAILED: $label (exit $rc) ==="
    exit $rc
  fi
}

# ── validate ─────────────────────────────────────────────────
validate() {
  local errors=false
  [ -z "$VM_HOST" ] && { echo -e "  ${CROSS} ${RED}--host is required${NC}"; errors=true; }

  if [ -z "$ENV_PATH" ]; then
    echo -e "  ${CROSS} ${RED}--env-path is required${NC}"
    echo -e "     Template: deployment/vm/.env.example"
    errors=true
  elif [ ! -f "$ENV_PATH" ]; then
    echo -e "  ${CROSS} ${RED}.env file not found: $ENV_PATH${NC}"
    errors=true
  fi

  [ ! -f "$SERVICE_TEMPLATE" ] && {
    echo -e "  ${CROSS} ${RED}systemd template not found: $SERVICE_TEMPLATE${NC}"
    errors=true
  }

  $errors && exit 1; return 0
}

# ── SSH/SCP setup ─────────────────────────────────────────────
SSH_OPTS="-q -o StrictHostKeyChecking=no -o LogLevel=ERROR -o ServerAliveInterval=5 -o ServerAliveCountMax=3"

setup_ssh() {
  if [ -n "$VM_PASSWORD" ]; then
    if ! command -v sshpass &>/dev/null; then
      echo -e "  ${CROSS} ${RED}sshpass required for --password${NC}"
      echo -e "     macOS:  brew install hudochenkov/sshpass/sshpass"
      echo -e "     RHEL:   sudo dnf install sshpass"
      echo -e "     Ubuntu: sudo apt-get install sshpass"
      exit 1
    fi
    export SSHPASS="${VM_PASSWORD}"
    SSH_CMD="sshpass -e ssh ${SSH_OPTS}"
    SCP_CMD="sshpass -e scp ${SSH_OPTS}"
  else
    SSH_CMD="ssh ${SSH_OPTS} -o BatchMode=yes"
    SCP_CMD="scp ${SSH_OPTS} -o BatchMode=yes"
  fi
}

vm_ssh() { $SSH_CMD "${VM_USER}@${VM_HOST}" "$@"; }
scp_to_vm() { $SCP_CMD "$1" "${VM_USER}@${VM_HOST}:$2"; }

# ── step functions ───────────────────────────────────────────

step_build() {
  if $SKIP_BUILD; then log "Skipping build (--skip-build)"; return 0; fi
  local jar_flag=""; $USE_FAST_JAR || jar_flag="-Dquarkus.package.jar.type=uber-jar"
  cd "$PROJECT_ROOT"
  local mvnw="./mvnw"; [ -f "$mvnw" ] || mvnw="mvn"
  chmod +x "$mvnw" 2>/dev/null || true
  # shellcheck disable=SC2086
  $mvnw clean package -DskipTests -Dquarkus.container-image.build=false $jar_flag
}

step_locate_artifact() {
  log "Locating artifact"
  if $USE_FAST_JAR; then
    ARTIFACT_SRC="${PROJECT_ROOT}/target/quarkus-app"
    [ -f "${ARTIFACT_SRC}/quarkus-run.jar" ] || { log "ERROR: fast-jar not found"; return 1; }
    log "fast-jar: $ARTIFACT_SRC"
  else
    RUNNER=$(ls "${PROJECT_ROOT}"/target/causa-backend-*-runner.jar 2>/dev/null | head -1 || true)
    [ -n "$RUNNER" ] || { log "ERROR: uber-jar not found in target/"; return 1; }
    log "uber-jar: $RUNNER"
  fi
}

step_prepare_vm_dir() {
  log "Preparing ${VM_DIR} on ${VM_USER}@${VM_HOST}"
  vm_ssh "mkdir -p ${VM_DIR} && chmod 755 ${VM_DIR}"
}

step_transfer_files() {
  local total=4

  # [1/4] artifact
  spinner_start "Step 4/6  Transferring [1/${total}] jar     "
  log "=== STEP: Step 4/6  Transfer files ==="
  if $USE_FAST_JAR; then
    log "Copying quarkus-app/ → ${VM_DIR}/quarkus-app/"
    # scp -r for directories
    vm_ssh "mkdir -p ${VM_DIR}/quarkus-app"
    $SCP_CMD -r "${ARTIFACT_SRC}/." "${VM_USER}@${VM_HOST}:${VM_DIR}/quarkus-app/"
  else
    log "Copying $(basename "$RUNNER") → ${VM_DIR}/causa-backend-runner.jar"
    scp_to_vm "$RUNNER" "${VM_DIR}/causa-backend-runner.jar"
  fi
  spinner_stop_ok "Step 4/6  Transferring [1/${total}] jar     "

  # [2/4] .env
  spinner_start "Step 4/6  Transferring [2/${total}] .env     "
  log "Copying .env → ${VM_DIR}/.env"
  scp_to_vm "$ENV_PATH" "${VM_DIR}/.env"
  vm_ssh "chmod 600 ${VM_DIR}/.env"
  spinner_stop_ok "Step 4/6  Transferring [2/${total}] .env     "

  # [3/4] systemd unit
  spinner_start "Step 4/6  Transferring [3/${total}] service  "
  log "Copying causa-backend.service → ${VM_DIR}/causa-backend.service"
  scp_to_vm "$SERVICE_TEMPLATE" "${VM_DIR}/causa-backend.service"
  spinner_stop_ok "Step 4/6  Transferring [3/${total}] service  "

  # [4/4] deploy.sh
  spinner_start "Step 4/6  Transferring [4/${total}] deploy   "
  log "Copying deploy.sh → ${VM_DIR}/deploy.sh"
  scp_to_vm "${SCRIPT_DIR}/deploy.sh" "${VM_DIR}/deploy.sh"
  vm_ssh "chmod +x ${VM_DIR}/deploy.sh"
  spinner_stop_ok "Step 4/6  Transferring [4/${total}] deploy   "

  log "=== DONE: Step 4/6  Transfer files ==="
}

step_remote_install_service() {
  if $SKIP_SERVICE; then log "Skipping service (--skip-service)"; return 0; fi
  log "Running deploy.sh on VM to install systemd service"

  local jar_flag="--uber-jar"
  $USE_FAST_JAR && jar_flag="--fast-jar"

  vm_ssh bash <<EOF
    bash ${VM_DIR}/deploy.sh \
      --env-path ${VM_DIR}/.env \
      --dir ${VM_DIR} \
      ${jar_flag} \
      --skip-build \
      2>&1
EOF
}

step_healthcheck() {
  local port="${CAUSA_PORT:-8080}"
  local url="http://localhost:${port}/q/health/ready"
  log "Health check via SSH: $url"
  local i=0
  while [ $i -lt 12 ]; do
    if vm_ssh "curl -sf ${url}" >> "$LOG_FILE" 2>&1; then
      log "Health check passed"
      return 0
    fi
    sleep 5; ((i++))
    log "Waiting... attempt $i/12"
  done
  log "Health check did not pass within 60s"
  return 1
}

# ── main ─────────────────────────────────────────────────────
main() {
  local jar_mode; jar_mode=$(${USE_FAST_JAR} && echo "fast-jar" || echo "uber-jar")

  echo ""
  echo -e "  ${BOLD}Causa Backend — Remote Deployment${NC}"
  echo -e "  ──────────────────────────────────────────────"
  echo -e "  Host        : ${VM_USER}@${VM_HOST}"
  echo -e "  Install dir : ${VM_DIR}"
  echo -e "  Jar mode    : ${jar_mode}"
  echo -e "  Env file    : ${ENV_PATH}"
  echo -e "  Log file    : ${LOG_FILE}"
  echo ""

  log "=== Remote Deployment Started: ${VM_USER}@${VM_HOST} ==="
  validate
  setup_ssh

  $SKIP_BUILD \
    && run_step "Step 1/6  Build app               " true \
    || run_step "Step 1/6  Build app               " step_build

  run_step "Step 2/6  Locate artifact          " step_locate_artifact
  run_step "Step 3/6  Prepare VM directory     " step_prepare_vm_dir

  # Transfer has its own per-file spinners — call directly, not via run_step
  step_transfer_files

  $SKIP_SERVICE \
    && run_step "Step 5/6  Install service (remote) " true \
    || run_step "Step 5/6  Install service (remote) " step_remote_install_service

  if ! $SKIP_SERVICE; then
    spinner_start "Step 6/6  Health check             "
    if step_healthcheck >> "$LOG_FILE" 2>&1; then
      spinner_stop_ok "Step 6/6  Health check             "
    else
      spinner_stop_fail "Step 6/6  Health check             "
    fi
  fi

  echo ""
  echo -e "  ${BOLD}${GREEN}Deployment complete!${NC}"
  echo -e "  ──────────────────────────────────────────────"
  echo -e "  ${BOLD}On the VM:${NC}"
  echo -e "    sudo systemctl status causa-backend"
  echo -e "    sudo journalctl -u causa-backend -f"
  echo -e "    curl http://localhost:${CAUSA_PORT:-8080}/q/health/ready"
  echo -e "  ${BOLD}Deploy log:${NC} ${LOG_FILE}"
  echo ""

  log "=== Remote Deployment Finished Successfully ==="
}

main
