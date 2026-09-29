#!/usr/bin/env bash
# Removes everything deploy.sh created (spec section 27): the Compose stack with its volumes, the Kubernetes
# namespaces, the minikube profile, Drift images and containers, Gradle daemons and build output.
# Asks first; --yes skips the question. Safe to run more than once. Your .env files are kept.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
MINIKUBE_PROFILE="${MINIKUBE_PROFILE:-drift}"
SERVICES=(user-service room-service message-service file-service)

if [[ -t 1 ]]; then BOLD=$'\033[1m'; RED=$'\033[31m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; BLUE=$'\033[34m'; RESET=$'\033[0m'
else BOLD=""; RED=""; GREEN=""; YELLOW=""; BLUE=""; RESET=""; fi
step() { printf '\n%s==> %s%s\n' "$BLUE$BOLD" "$*" "$RESET"; }
info() { printf '    %s\n' "$*"; }
ok()   { printf '    %s✔%s %s\n' "$GREEN" "$RESET" "$*"; }
warn() { printf '    %s!%s %s\n' "$YELLOW" "$RESET" "$*" >&2; }
have() { command -v "$1" >/dev/null 2>&1; }

YES=false
for arg in "$@"; do
  case "$arg" in
    -y|--yes) YES=true ;;
    -h|--help) sed -n '2,5p' "$0"; exit 0 ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

if ! $YES; then
  printf '%sThis will delete:%s\n' "$BOLD" "$RESET"
  printf '  - the Docker Compose stack: containers, volumes (all chat data!) and orphans\n'
  printf '  - the Kubernetes namespaces "drift" and "observability" and the minikube profile "%s"\n' "$MINIKUBE_PROFILE"
  printf '  - all drift/* Docker images\n'
  printf '  - Gradle daemons, build/ and .gradle/ in every service, and the frontend dist/\n'
  read -r -p "Continue? [y/N] " answer
  [[ "$answer" =~ ^[Yy]$ ]] || { echo "Nothing was changed."; exit 0; }
fi

removed=()

step "Docker Compose"
if have docker && docker info >/dev/null 2>&1; then
  if docker compose down --volumes --remove-orphans >/dev/null 2>&1; then removed+=("Compose stack, containers and volumes"); ok "stack removed"; else info "nothing to remove"; fi
else
  warn "Docker is not running, skipping"
fi

step "Kubernetes"
if have kubectl && have minikube && minikube -p "$MINIKUBE_PROFILE" status >/dev/null 2>&1; then
  for ns in drift observability; do
    if kubectl --context "$MINIKUBE_PROFILE" delete namespace "$ns" --ignore-not-found --wait=false >/dev/null 2>&1; then removed+=("namespace $ns"); fi
  done
fi
if have minikube; then
  if minikube profile list 2>/dev/null | grep -qw "$MINIKUBE_PROFILE"; then
    minikube delete -p "$MINIKUBE_PROFILE" && removed+=("minikube profile $MINIKUBE_PROFILE") && ok "profile deleted"
  else
    info "no minikube profile \"$MINIKUBE_PROFILE\""
  fi
fi

step "Docker images and containers"
if have docker && docker info >/dev/null 2>&1; then
  containers="$(docker ps -aq --filter 'label=com.docker.compose.project=drift' 2>/dev/null || true)"
  if [[ -n "$containers" ]]; then
    # shellcheck disable=SC2086
    docker rm -f $containers >/dev/null 2>&1 && removed+=("leftover containers")
  fi
  images="$(docker images --format '{{.Repository}}:{{.Tag}}' | grep -E '^drift/' || true)"
  if [[ -n "$images" ]]; then
    # shellcheck disable=SC2086
    docker rmi -f $images >/dev/null 2>&1 && removed+=("Drift images ($(echo "$images" | wc -l | tr -d ' '))") && ok "images removed"
  else
    info "no Drift images"
  fi
fi

step "Gradle daemons and build output"
for svc in "${SERVICES[@]}"; do
  dir="backend/$svc"
  [[ -x "$dir/gradlew" ]] && (cd "$dir" && ./gradlew --stop >/dev/null 2>&1 || true)
  rm -rf "$dir/build" "$dir/.gradle" "$dir/.kotlin"
done
rm -rf frontend/drift-chat/dist
removed+=("build output (build/, .gradle/, dist/)")
ok "done"

step "Summary"
if [[ ${#removed[@]} -eq 0 ]]; then info "Nothing needed removing."; else for item in "${removed[@]}"; do ok "removed $item"; done; fi
info "Your .env files were kept."
