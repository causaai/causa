#!/usr/bin/env bash
# ============================================================
# causa-backend — core deployment logic
# ============================================================
# Can be run directly ON the VM (local deploy) or sourced by
# deploy_remote.sh which handles SSH transport.
#
# Usage (on the VM directly):
#   bash deploy.sh --env-path /opt/causa/.env [OPTIONS]
#
# Options:
#   --env-path <path>      Path to .env file (REQUIRED)
#   --dir <path>           Install directory (default: /opt/causa)
#   --uber-jar             Use single runner jar (default)
#   --fast-jar             Use quarkus-app directory layout
#   --skip-build           Skip Maven build, use existing artifact in target/
#   --skip-service         Copy files only, do not install/restart systemd
#   --jar-path <path>      Use this exact jar path (skips artifact discovery)
#   --help|-h              Show this help
#
# Examples:
#   # On the VM — artifact already transferred, just install service
#   bash deploy.sh --env-path /opt/causa/.env --skip-build --skip-service
#
#   # On the dev machine — full build + local deploy (no SSH)
#   bash deploy.sh --env-path ./prod.env
# ============================================================
set -euo pipefail

# ── script location ──────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
LOG_FILE="${SCRIPT_DIR}/deploy.log"

# Service template: look next to this script first (VM layout where the
# file was scp'd alongside deploy.sh), then fall back to project tree
# (dev machine layout: scripts/vm/ → ../../deployment/vm/)
if [ -f "${SCRIPT_DIR}/causa-backend.service" ]; then
  SERVICE_TEMPLATE="${SCRIPT_DIR}/causa-backend.service"
else
  SERVICE_TEMPLATE="${PROJECT_ROOT}/deployment/vm/causa-backend.service"
fi

# ── defaults ─────────────────────────────────────────────────
INSTALL_DIR="/opt/causa"
USE_FAST_JAR=false
ENV_PATH=""
SKIP_BUILD=false
SKIP_SERVICE=false
EXPLICIT_JAR=""

# ── argument parsing ─────────────────────────────────────────
while [[ $# -gt 0 ]]; do
  case "$1" in
    --env-path)    ENV_PATH="$2";       shift 2 ;;
    --dir)         INSTALL_DIR="$2";    shift 2 ;;
    --uber-jar)    USE_FAST_JAR=false;  shift ;;
    --fast-jar)    USE_FAST_JAR=true;   shift ;;
    --skip-build)  SKIP_BUILD=true;     shift ;;
    --skip-service) SKIP_SERVICE=true;  shift ;;
    --jar-path)    EXPLICIT_JAR="$2"; SKIP_BUILD=true; shift 2 ;;
    --help|-h)
      grep '^#' "$0" | sed 's/^# \?//'
      exit 0
      ;;
    *)
      echo "[ERROR] Unknown argument: $1" >&2
      echo "[ERROR] Usage: bash deploy.sh --env-path <path> [OPTIONS]" >&2
      exit 1
      ;;
  esac
done

# ── colours ──────────────────────────────────────────────────
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; BOLD='\033[1m'; NC='\033[0m'
TICK="${GREEN}✔${NC}"
CROSS="${RED}✘${NC}"

# ── logging ──────────────────────────────────────────────────
# All verbose output goes to log file; console shows only spinner + result
: > "$LOG_FILE"   # truncate log at start of each run

log()  { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >> "$LOG_FILE"; }
logn() { echo "$*" >> "$LOG_FILE"; }  # raw, no timestamp

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

# run_step <label> <cmd...>
# Runs cmd, streams output to log file, shows spinner on console
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

  if [ -z "$ENV_PATH" ]; then
    echo -e "  ${CROSS} ${RED}--env-path is required${NC}"
    echo -e "     Example:  --env-path ./prod.env"
    echo -e "     Template: deployment/vm/.env.example"
    errors=true
  elif [ ! -f "$ENV_PATH" ]; then
    echo -e "  ${CROSS} ${RED}.env file not found at: $ENV_PATH${NC}"
    errors=true
  fi

  if [ ! -f "$SERVICE_TEMPLATE" ]; then
    echo -e "  ${CROSS} ${RED}systemd template not found: $SERVICE_TEMPLATE${NC}"
    errors=true
  fi

  $errors && exit 1
  return 0
}

# ── step functions ───────────────────────────────────────────

step_build() {
  if $SKIP_BUILD; then
    log "Skipping build (--skip-build)"
    return 0
  fi
  local jar_flag=""
  $USE_FAST_JAR || jar_flag="-Dquarkus.package.jar.type=uber-jar"
  cd "$PROJECT_ROOT"
  local mvnw="./mvnw"; [ -f "$mvnw" ] || mvnw="mvn"
  chmod +x "$mvnw" 2>/dev/null || true
  # shellcheck disable=SC2086
  $mvnw clean package -DskipTests -Dquarkus.container-image.build=false $jar_flag
}

step_locate_artifact() {
  log "Locating artifact (jar_mode=$(${USE_FAST_JAR} && echo fast-jar || echo uber-jar))"

  if [ -n "$EXPLICIT_JAR" ]; then
    [ -f "$EXPLICIT_JAR" ] || { log "ERROR: --jar-path not found: $EXPLICIT_JAR"; return 1; }
    RUNNER="$EXPLICIT_JAR"
    log "Using explicit jar: $RUNNER"
    return 0
  fi

  if $USE_FAST_JAR; then
    ARTIFACT_DIR="${PROJECT_ROOT}/target/quarkus-app"
    [ -f "${ARTIFACT_DIR}/quarkus-run.jar" ] || {
      log "ERROR: fast-jar not found at ${ARTIFACT_DIR}/quarkus-run.jar"
      return 1
    }
    RUNNER="${ARTIFACT_DIR}/quarkus-run.jar"
  else
    RUNNER=$(ls "${PROJECT_ROOT}"/target/causa-backend-*-runner.jar 2>/dev/null | head -1 || true)
    [ -n "$RUNNER" ] || {
      log "ERROR: uber-jar not found in ${PROJECT_ROOT}/target/"
      return 1
    }
  fi
  log "Artifact: $RUNNER"
}

step_prepare_dir() {
  log "Preparing install directory: $INSTALL_DIR"
  mkdir -p "$INSTALL_DIR"
  chmod 755 "$INSTALL_DIR"
}

step_copy_files() {
  log "Copying artifact to $INSTALL_DIR"
  if $USE_FAST_JAR; then
    rsync -a --delete "${PROJECT_ROOT}/target/quarkus-app/" "${INSTALL_DIR}/quarkus-app/"
    log "Synced quarkus-app/ → ${INSTALL_DIR}/quarkus-app/"
  else
    cp "$RUNNER" "${INSTALL_DIR}/causa-backend-runner.jar"
    log "Copied runner jar → ${INSTALL_DIR}/causa-backend-runner.jar"
  fi

  log "Copying .env → ${INSTALL_DIR}/.env"
  cp "$ENV_PATH" "${INSTALL_DIR}/.env"
  chmod 600 "${INSTALL_DIR}/.env"
}

step_install_service() {
  if $SKIP_SERVICE; then
    log "Skipping service install (--skip-service)"
    return 0
  fi

  local vm_jar_path
  if $USE_FAST_JAR; then
    vm_jar_path="${INSTALL_DIR}/quarkus-app/quarkus-run.jar"
  else
    vm_jar_path="${INSTALL_DIR}/causa-backend-runner.jar"
  fi

  local vm_user="${SUDO_USER:-$(whoami)}"
  local unit_dest="/etc/systemd/system/causa-backend.service"

  log "Installing systemd unit: $unit_dest"
  log "  JAR_PATH=${vm_jar_path}"
  log "  VM_DIR=${INSTALL_DIR}"
  log "  VM_USER=${vm_user}"

  sed \
    -e "s|__JAR_PATH__|${vm_jar_path}|g" \
    -e "s|__VM_DIR__|${INSTALL_DIR}|g" \
    -e "s|__VM_USER__|${vm_user}|g" \
    "$SERVICE_TEMPLATE" > /tmp/causa-backend.service

  if command -v sudo &>/dev/null && [ "$(id -u)" -ne 0 ]; then
    sudo mv /tmp/causa-backend.service "$unit_dest"
    sudo systemctl daemon-reload
    sudo systemctl enable causa-backend
    sudo systemctl restart causa-backend
  else
    mv /tmp/causa-backend.service "$unit_dest"
    systemctl daemon-reload
    systemctl enable causa-backend
    systemctl restart causa-backend
  fi

  log "Service started. Waiting 5s for startup..."
  sleep 5
  systemctl status causa-backend --no-pager >> "$LOG_FILE" 2>&1 || true
  journalctl -u causa-backend -n 30 --no-pager >> "$LOG_FILE" 2>&1 || true
}

# ── health check ─────────────────────────────────────────────
step_healthcheck() {
  local port="${CAUSA_PORT:-8080}"
  local url="http://localhost:${port}/q/health/ready"
  log "Health check: $url"
  local i=0
  while [ $i -lt 12 ]; do
    if curl -sf "$url" >> "$LOG_FILE" 2>&1; then
      log "Health check passed"
      return 0
    fi
    sleep 5
    ((i++))
    log "Waiting for health check... attempt $i/12"
  done
  log "Health check did not pass within 60s — service may still be starting"
  return 1
}

# ── main ─────────────────────────────────────────────────────
main() {
  local jar_mode; jar_mode=$(${USE_FAST_JAR} && echo "fast-jar" || echo "uber-jar")

  echo ""
  echo -e "  ${BOLD}Causa Backend — Deployment${NC}"
  echo -e "  ──────────────────────────────────────────────"
  echo -e "  Install dir : ${INSTALL_DIR}"
  echo -e "  Jar mode    : ${jar_mode}"
  echo -e "  Env file    : ${ENV_PATH}"
  echo -e "  Log file    : ${LOG_FILE}"
  echo ""

  log "=== Causa Backend Deployment Started ==="
  log "install_dir=$INSTALL_DIR jar_mode=$jar_mode env_path=$ENV_PATH"

  validate

  $SKIP_BUILD \
    && run_step "Step 1/5  Build app               " true \
    || run_step "Step 1/5  Build app               " step_build

  run_step   "Step 2/5  Locate artifact          " step_locate_artifact
  run_step   "Step 3/5  Prepare install dir       " step_prepare_dir
  run_step   "Step 4/5  Copy files                " step_copy_files

  $SKIP_SERVICE \
    && run_step "Step 5/5  Install systemd service  " true \
    || run_step "Step 5/5  Install systemd service  " step_install_service

  if ! $SKIP_SERVICE; then
    spinner_start "Health check (waiting up to 60s)  "
    if step_healthcheck >> "$LOG_FILE" 2>&1; then
      spinner_stop_ok "Health check (waiting up to 60s)  "
    else
      spinner_stop_fail "Health check (waiting up to 60s)  "
    fi
  fi

  echo ""
  echo -e "  ${BOLD}${GREEN}Deployment complete!${NC}"
  echo -e "  ──────────────────────────────────────────────"
  if ! $SKIP_SERVICE; then
    echo -e "  ${BOLD}Service:${NC}  sudo systemctl status causa-backend"
    echo -e "  ${BOLD}Logs:${NC}     sudo journalctl -u causa-backend -f"
    echo -e "  ${BOLD}Health:${NC}   curl http://localhost:${CAUSA_PORT:-8080}/q/health/ready"
  fi
  echo -e "  ${BOLD}Deploy log:${NC} ${LOG_FILE}"
  echo ""

  log "=== Deployment Finished Successfully ==="
}

main
