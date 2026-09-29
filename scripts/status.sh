#!/usr/bin/env bash
# What is running (spec section 27): Docker Compose containers, the minikube profile and its pods, each service's
# health, and where to find everything.
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
MINIKUBE_PROFILE="${MINIKUBE_PROFILE:-drift}"

if [[ -t 1 ]]; then BOLD=$'\033[1m'; RED=$'\033[31m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; BLUE=$'\033[34m'; RESET=$'\033[0m'
else BOLD=""; RED=""; GREEN=""; YELLOW=""; BLUE=""; RESET=""; fi
step() { printf '\n%s==> %s%s\n' "$BLUE$BOLD" "$*" "$RESET"; }
info() { printf '    %s\n' "$*"; }
ok()   { printf '    %s✔%s %s\n' "$GREEN" "$RESET" "$*"; }
warn() { printf '    %s!%s %s\n' "$YELLOW" "$RESET" "$*" >&2; }
have() { command -v "$1" >/dev/null 2>&1; }
env_value() { local v=""; [[ -f "$1" ]] && v="$(grep -E "^$2=" "$1" | tail -1 | cut -d= -f2- || true)"; printf '%s' "${v:-${3:-}}"; }

step "Docker Compose"
if have docker && docker info >/dev/null 2>&1; then
  docker compose ps --format 'table {{.Service}}\t{{.State}}\t{{.Health}}\t{{.Ports}}' 2>/dev/null || warn "the Compose stack is not running"
else
  warn "Docker is not running"
fi

step "minikube"
ip=""
if have minikube && minikube -p "$MINIKUBE_PROFILE" status >/dev/null 2>&1; then
  minikube -p "$MINIKUBE_PROFILE" status
  ip="$(minikube -p "$MINIKUBE_PROFILE" ip 2>/dev/null || true)"
  info "IP: $ip"
  if have kubectl; then
    for ns in drift observability; do
      printf '\n%snamespace %s%s\n' "$BOLD" "$ns" "$RESET"
      kubectl --context "$MINIKUBE_PROFILE" -n "$ns" get pods,deployments,statefulsets,services,ingress,hpa 2>/dev/null || warn "namespace $ns not found"
    done
  fi
else
  warn "minikube profile \"$MINIKUBE_PROFILE\" is not running"
fi

step "Service health (Actuator readiness, Compose stack)"
management_port() {
  case "$1" in
    user-service) echo 8181 ;; room-service) echo 8182 ;; message-service) echo 8183 ;; file-service) echo 8184 ;;
  esac
}
for svc in user-service room-service message-service file-service; do
  result="$(docker compose exec -T "$svc" wget -qO- "http://127.0.0.1:$(management_port "$svc")/actuator/health/readiness" 2>/dev/null || true)"
  if [[ "$result" == *'"UP"'* ]]; then ok "$svc  UP"; else warn "$svc  ${result:-not reachable}"; fi
done
edge_port="$(env_value .env EDGE_PORT 8080)"
if [[ "$(curl -s -o /dev/null -w '%{http_code}' "http://localhost:$edge_port/healthz" 2>/dev/null)" == "200" ]]; then
  ok "edge nginx  UP (http://localhost:$edge_port)"
else
  warn "edge nginx  not reachable on port $edge_port"
fi

printf '\n%sURLs%s\n' "$BOLD" "$RESET"
printf '  Docker Compose\n'
printf '    App         http://localhost:%s\n' "$edge_port"
printf '    Grafana     http://localhost:3000       login: %s / %s\n' "$(env_value .env GRAFANA_ADMIN_USER admin)" "$(env_value .env GRAFANA_ADMIN_PASSWORD admin-change-me)"
printf '    Prometheus  http://localhost:9090\n'
printf '    MinIO       http://localhost:9001       login: %s / %s\n' "$(env_value .env MINIO_ROOT_USER minioadmin)" "$(env_value .env MINIO_ROOT_PASSWORD change-me)"
if [[ -n "$ip" ]]; then
  printf '  Kubernetes (minikube profile "%s", ip %s)\n' "$MINIKUBE_PROFILE" "$ip"
  printf '    App         http://drift.%s.nip.io\n' "$ip"
  printf '    Grafana     http://grafana.%s.nip.io\n' "$ip"
  printf '    Dashboard   minikube -p %s dashboard\n' "$MINIKUBE_PROFILE"
fi
