#!/usr/bin/env bash
set -uo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MINIKUBE_PROFILE="${MINIKUBE_PROFILE:-drift}"
EDGE_PORT="${EDGE_PORT:-8080}"
export EDGE_PORT
cd "$ROOT_DIR"

management_port() {
  case "$1" in
    user-service) echo 8181 ;; room-service) echo 8182 ;; message-service) echo 8183 ;; file-service) echo 8184 ;;
  esac
}

compose_running=false
minikube_running=false

echo "=== DOCKER COMPOSE STATUS ==="
if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  if [ -n "$(docker compose ps -q 2>/dev/null)" ]; then
    compose_running=true
    docker compose ps --format 'table {{.Service}}\t{{.Status}}\t{{.Ports}}'
  else
    echo "Docker Compose is not running or no services configured."
  fi
else
  echo "Docker is not installed or not running."
fi
echo

echo "=== MINIKUBE STATUS ==="
if command -v minikube >/dev/null 2>&1; then
  if minikube -p "$MINIKUBE_PROFILE" status >/dev/null 2>&1; then
    minikube_running=true
    echo "Minikube profile '$MINIKUBE_PROFILE' is running."
  else
    echo "Minikube profile '$MINIKUBE_PROFILE' is not running."
  fi
else
  echo "Minikube is not installed."
fi
echo

echo "=== KUBERNETES PODS ==="
if $minikube_running; then
  if command -v kubectl >/dev/null 2>&1; then
    for ns in drift observability; do
      echo "--- namespace $ns ---"
      kubectl --context "$MINIKUBE_PROFILE" -n "$ns" get pods,services,ingress,hpa 2>/dev/null || echo "namespace $ns not found"
    done
  else
    echo "kubectl is not installed."
  fi
else
  echo "Minikube profile '$MINIKUBE_PROFILE' is not running. Cannot fetch pods."
fi
echo

echo "=== SERVICE HEALTH (Docker Compose) ==="
if $compose_running; then
  for svc in user-service room-service message-service file-service; do
    result="$(docker compose exec -T "$svc" wget -qO- "http://127.0.0.1:$(management_port "$svc")/actuator/health/readiness" 2>/dev/null || true)"
    if [[ "$result" == *'"UP"'* ]]; then echo "  $svc: UP"; else echo "  $svc: ${result:-not reachable}"; fi
  done
  if [ "$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$EDGE_PORT/healthz" 2>/dev/null)" = "200" ]; then
    echo "  nginx: UP (http://localhost:$EDGE_PORT)"
  else
    echo "  nginx: not reachable on port $EDGE_PORT (if you started it on another port, run: EDGE_PORT=<port> scripts/status.sh)"
  fi
else
  echo "Docker Compose is not running. Nothing to check."
fi
echo

echo "=== URLS ==="
if $compose_running; then
  echo "Docker Compose"
  echo "  App         http://localhost:$EDGE_PORT"
  echo "  Grafana     http://localhost:3000   (no login)"
  echo "  Prometheus  http://localhost:9090"
  echo "  MinIO       http://localhost:9001   login: ${MINIO_ROOT_USER:-admin-change-me} / ${MINIO_ROOT_PASSWORD:-admin-change-me}"
fi
if $minikube_running; then
  ip="$(minikube -p "$MINIKUBE_PROFILE" ip 2>/dev/null || true)"
  echo "Kubernetes (minikube profile '$MINIKUBE_PROFILE', ip $ip)"
  echo "  App         http://drift.$ip.nip.io"
  echo "  Grafana     http://grafana.$ip.nip.io"
  echo "  Dashboard   minikube -p $MINIKUBE_PROFILE dashboard"
  echo "  (On macOS with the Docker driver run: minikube -p $MINIKUBE_PROFILE tunnel)"
fi
if ! $compose_running && ! $minikube_running; then
  echo "Nothing is running. Start it with: scripts/deploy.sh"
fi
