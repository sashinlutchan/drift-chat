#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MINIKUBE_PROFILE="${MINIKUBE_PROFILE:-drift}"
SERVICES=(user-service room-service message-service file-service)

MODE="all"
YES=false
for arg in "$@"; do
  case "$arg" in
    all|compose|k8s) MODE="$arg" ;;
    -y|--yes) YES=true ;;
    -h|--help) sed -n '2,/^$/p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Usage: teardown.sh [all|compose|k8s] [-y|--yes]"; exit 1 ;;
  esac
done

confirm() {
  $YES && return 0
  echo "This will delete:"
  if [ "$MODE" != "k8s" ]; then
    echo "  - the Docker Compose stack: containers, volumes (all chat data!) and orphans"
  fi
  if [ "$MODE" != "compose" ]; then
    echo "  - the Minikube profile \"$MINIKUBE_PROFILE\" (the Kubernetes namespaces go with it)"
  fi
  if [ "$MODE" = "all" ]; then
    echo "  - all drift/* Docker images and the pulled infrastructure images that nothing else uses"
    echo "  - Gradle daemons, build/ and .gradle/ in every service, and the frontend dist/"
  fi
  read -r -p "Continue? [y/N] " answer
  [[ "$answer" =~ ^[Yy]$ ]] || { echo "Nothing was changed."; exit 0; }
}

stop_compose() {
  if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
    echo "docker not available — nothing to stop."
    return 0
  fi
  echo "--- stopping docker containers ---"
  cd "$ROOT_DIR"
  docker compose stop || true
}

stop_k8s() {
  if ! command -v minikube >/dev/null 2>&1; then
    echo "minikube not installed — nothing to stop."
    return 0
  fi
  echo "--- stopping Minikube ---"
  echo "Stopping any active dashboard, tunnel, or proxy processes for profile '$MINIKUBE_PROFILE'..."
  pkill -f "minikube dashboard -p $MINIKUBE_PROFILE" || true
  pkill -f "minikube -p $MINIKUBE_PROFILE dashboard" || true
  pkill -f "kubectl --context $MINIKUBE_PROFILE proxy" || true
  pkill -f "minikube -p $MINIKUBE_PROFILE tunnel" || true
  minikube stop -p "$MINIKUBE_PROFILE" 2>/dev/null || true
}

compose_remove() {
  if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
    echo "docker not available — nothing to tear down."
    return 0
  fi
  echo "--- removing the docker compose stack and its volumes ---"
  cd "$ROOT_DIR"
  docker compose down -v --remove-orphans || true
  local leftovers
  leftovers="$(docker ps -aq --filter 'label=com.docker.compose.project=drift' 2>/dev/null || true)"
  [ -n "$leftovers" ] && docker rm -f $leftovers >/dev/null 2>&1 || true
}

k8s_remove() {
  if ! command -v minikube >/dev/null 2>&1; then
    echo "minikube not installed — nothing to tear down."
    return 0
  fi
  echo "--- removing Minikube profile '$MINIKUBE_PROFILE' ---"
  if [ -d "${MINIKUBE_HOME:-$HOME}/.minikube/profiles/$MINIKUBE_PROFILE" ]; then
    minikube delete -p "$MINIKUBE_PROFILE" || true
  else
    echo "no minikube profile \"$MINIKUBE_PROFILE\""
  fi
}

image_cleanup() {
  if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
    return 0
  fi
  echo "--- removing project-related docker images ---"
  cd "$ROOT_DIR"
  local images
  images="$(docker images --format '{{.Repository}}:{{.Tag}}' | grep -E '^drift/' || true)"
  images="$images
$(docker compose config --images 2>/dev/null | grep -v '^drift/' || true)"
  echo $images | xargs -n1 docker rmi 2>/dev/null || true
  if [ "$MODE" != "compose" ]; then
    local kicbase
    kicbase="$(docker images --format '{{.Repository}} {{.ID}}' | awk '/^gcr\.io\/k8s-minikube\/kicbase / {print $2}' | sort -u)"
    [ -n "$kicbase" ] && docker rmi $kicbase 2>/dev/null || true
  fi
}

build_cleanup() {
  echo "--- stopping Gradle daemons and removing build output ---"
  for svc in "${SERVICES[@]}"; do
    (cd "$ROOT_DIR/backend/$svc" && ./gradlew --stop >/dev/null 2>&1) || true
    rm -rf "$ROOT_DIR/backend/$svc/build" "$ROOT_DIR/backend/$svc/.gradle"
  done
  rm -rf "$ROOT_DIR/frontend/drift-chat/dist"
}

compose_down() {
  stop_compose
  echo
  compose_remove
}

k8s_down() {
  stop_k8s
  echo
  k8s_remove
}

down_all() {
  echo "=== Phase 1: stopping containers ==="
  stop_compose
  echo
  stop_k8s
  echo
  echo "=== Phase 2: removing stacks, volumes, and images ==="
  compose_remove
  echo
  k8s_remove
  echo
  image_cleanup
  echo
  build_cleanup
}

confirm
case "$MODE" in
  all)     down_all ;;
  compose) compose_down ;;
  k8s)     k8s_down ;;
esac
echo
echo "Done."
