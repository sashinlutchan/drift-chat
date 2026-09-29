#!/usr/bin/env bash
# The one command that brings Drift up (spec section 27): checks your tools, runs every test, builds the images,
# starts the Docker Compose stack, and deploys the same thing to minikube (Kubernetes).
#
# Usage: scripts/deploy.sh [--skip-tests] [--skip-k8s] [--skip-compose] [--no-open]
#
# Safe to run more than once. Stops at the first failure.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SERVICES=(user-service room-service message-service file-service)
MINIKUBE_PROFILE="${MINIKUBE_PROFILE:-drift}"
TAG="${DRIFT_TAG:-local}"
DATABASES=(users_db rooms_db messages_db files_db)
TOPICS=(user.deleted user.updated presence.changed room.created room.updated room.deleted member.joined member.left room.occupancy message.created)
BUCKETS=(drift-avatars drift-media)

RUN_TESTS=true; RUN_K8S=true; RUN_COMPOSE=true; OPEN_BROWSER=true
for arg in "$@"; do
  case "$arg" in
    --skip-tests) RUN_TESTS=false ;;
    --skip-k8s) RUN_K8S=false ;;
    --skip-compose) RUN_COMPOSE=false ;;
    --no-open) OPEN_BROWSER=false ;;
    -h|--help) sed -n '2,8p' "$0"; exit 0 ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

# ── output helpers ──────────────────────────────────────────────────────────────────────────────────────────────
if [[ -t 1 ]]; then BOLD=$'\033[1m'; RED=$'\033[31m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'; BLUE=$'\033[34m'; RESET=$'\033[0m'
else BOLD=""; RED=""; GREEN=""; YELLOW=""; BLUE=""; RESET=""; fi
step() { printf '\n%s==> %s%s\n' "$BLUE$BOLD" "$*" "$RESET"; }
info() { printf '    %s\n' "$*"; }
ok()   { printf '    %s✔%s %s\n' "$GREEN" "$RESET" "$*"; }
warn() { printf '    %s!%s %s\n' "$YELLOW" "$RESET" "$*" >&2; }
die()  { printf '\n%s✘ %s%s\n' "$RED$BOLD" "$*" "$RESET" >&2; exit 1; }
have() { command -v "$1" >/dev/null 2>&1; }
env_value() { local v=""; [[ -f "$1" ]] && v="$(grep -E "^$2=" "$1" | tail -1 | cut -d= -f2- || true)"; printf '%s' "${v:-${3:-}}"; }

COMPOSE=(docker compose)

# ── 1. prerequisites ────────────────────────────────────────────────────────────────────────────────────────────
step "Checking prerequisites"
missing=0
check() { # check <command> <install hint>
  if have "$1"; then ok "$1"; else warn "$1 is missing — $2"; missing=1; fi
}
check docker "install Docker Desktop: https://docs.docker.com/get-docker/"
docker compose version >/dev/null 2>&1 && ok "docker compose" || { warn "docker compose is missing — it ships with Docker Desktop"; missing=1; }
if $RUN_K8S; then
  check minikube "brew install minikube (https://minikube.sigs.k8s.io/docs/start/)"
  check kubectl "brew install kubectl (https://kubernetes.io/docs/tasks/tools/)"
fi
if $RUN_TESTS; then
  check java "install Amazon Corretto 25: https://aws.amazon.com/corretto/"
  check node "install the current Node.js LTS: https://nodejs.org/"
  check yarn "npm install -g yarn"
fi
[[ $missing -eq 0 ]] || die "Install what is missing (see above) and run this again. Gradle itself is not needed: the Gradle Wrapper downloads it."
docker info >/dev/null 2>&1 || die "Docker is not running. Start Docker Desktop and try again."
if $RUN_TESTS; then
  java_major="$(java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')"
  [[ "$java_major" -ge 25 ]] || warn "Java $java_major found; the services need JDK 25 (Gradle will use its toolchain if it can find one)."
fi

# ── 2. environment files ────────────────────────────────────────────────────────────────────────────────────────
step "Checking .env files"
for envdir in . backend/user-service backend/room-service backend/message-service backend/file-service; do
  if [[ ! -f "$envdir/.env" ]]; then
    cp "$envdir/.env.example" "$envdir/.env"
    warn "$envdir/.env was missing, copied from .env.example (edit it if you need other values)"
  else
    ok "$envdir/.env"
  fi
done

# ── 3. tests ────────────────────────────────────────────────────────────────────────────────────────────────────
if $RUN_TESTS; then
  step "Running all tests (deployment stops if any fail)"
  for svc in "${SERVICES[@]}"; do
    info "$svc: ./gradlew check (ktlint, detekt, unit + integration tests, coverage)"
    (cd "backend/$svc" && ./gradlew check --console=plain -q) || die "$svc failed its checks"
    ok "$svc"
  done
  info "frontend: lint, type-check, tests"
  (cd frontend/drift-chat && yarn install --frozen-lockfile --silent && yarn lint && yarn tsc -b && yarn test) || die "frontend checks failed"
  ok "frontend"
else
  step "Tests skipped (--skip-tests)"
fi

# ── 4. images ───────────────────────────────────────────────────────────────────────────────────────────────────
step "Building Docker images (four services + frontend)"
"${COMPOSE[@]}" build user-service room-service message-service file-service frontend
ok "images built: $(docker images --format '{{.Repository}}:{{.Tag}}' | grep -c '^drift/') drift/* images"

# ── 5. Docker Compose ───────────────────────────────────────────────────────────────────────────────────────────
compose_setup() {
  step "Docker Compose: starting infrastructure"
  "${COMPOSE[@]}" up -d --wait postgres kafka valkey minio
  ok "postgres, kafka, valkey and minio are healthy"

  step "Creating databases, Kafka topics and MinIO buckets (skipped if they exist)"
  local db_user; db_user="$(env_value .env POSTGRES_USER drift)"
  for db in "${DATABASES[@]}"; do
    if "${COMPOSE[@]}" exec -T postgres psql -U "$db_user" -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$db'" | grep -q 1; then
      info "database $db already exists"
    else
      "${COMPOSE[@]}" exec -T postgres psql -U "$db_user" -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE $db OWNER $db_user" >/dev/null
      ok "created database $db"
    fi
  done
  for topic in "${TOPICS[@]}"; do
    "${COMPOSE[@]}" exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
      --create --if-not-exists --topic "$topic" --partitions 3 --replication-factor 1 >/dev/null
  done
  ok "Kafka topics: ${TOPICS[*]}"
  local root_user root_pass
  root_user="$(env_value .env MINIO_ROOT_USER minioadmin)"; root_pass="$(env_value .env MINIO_ROOT_PASSWORD change-me)"
  # MC_HOST_<alias> configures the MinIO client without a shell inside the image
  docker run --rm --network "drift_default" -e "MC_HOST_local=http://$root_user:$root_pass@minio:9000" \
    chainguard/minio-client@sha256:be51ef820151a708a8e140037e3746862a8c1dd5e624f84b404a1d71bcefb167 \
    mb --ignore-existing "${BUCKETS[@]/#/local/}" >/dev/null
  ok "MinIO buckets: ${BUCKETS[*]}"

  step "Docker Compose: starting services, frontend, edge and observability"
  "${COMPOSE[@]}" up -d --wait
  ok "the stack is up"
}

if $RUN_COMPOSE && $RUN_K8S; then
  docker_mb=$(( $(docker info --format '{{.MemTotal}}') / 1024 / 1024 ))
  if [[ "$docker_mb" -lt 12000 ]]; then
    warn "Docker Desktop has ${docker_mb} MB. Running Compose and minikube together needs about 12 GB (spec section 25)."
    warn "If the Kubernetes step runs out of memory: raise Docker's memory limit, or run with --skip-k8s / --skip-compose."
  fi
fi
if $RUN_COMPOSE; then compose_setup; else step "Docker Compose skipped (--skip-compose)"; fi

# ── 6. Kubernetes (minikube) ────────────────────────────────────────────────────────────────────────────────────
k8s_setup() {
  step "minikube: profile \"$MINIKUBE_PROFILE\""
  if minikube -p "$MINIKUBE_PROFILE" status >/dev/null 2>&1; then
    ok "already running"
  else
    # minikube's node is a container inside Docker's VM, so it can't be given more memory than Docker itself has
    local docker_mb mem
    docker_mb=$(( $(docker info --format '{{.MemTotal}}') / 1024 / 1024 ))
    mem="${MINIKUBE_MEMORY:-$(( docker_mb > 9500 ? 8192 : docker_mb - 700 ))}"
    info "starting minikube with ${mem} MB (Docker has ${docker_mb} MB; override with MINIKUBE_MEMORY)"
    minikube start -p "$MINIKUBE_PROFILE" --driver=docker --cpus=4 --memory="$mem" --disk-size=60g
  fi
  minikube -p "$MINIKUBE_PROFILE" addons enable ingress >/dev/null
  minikube -p "$MINIKUBE_PROFILE" addons enable metrics-server >/dev/null
  minikube -p "$MINIKUBE_PROFILE" addons enable dashboard >/dev/null
  ok "addons: ingress, metrics-server, dashboard"
  local ip; ip="$(minikube -p "$MINIKUBE_PROFILE" ip)"
  local ctx="$MINIKUBE_PROFILE"

  step "minikube: loading images (no registry needed)"
  # "image load" keeps an existing image with the same tag, so a rebuilt image would silently be ignored: remove it first
  redeploying=false
  kubectl --context "$ctx" -n drift get deployment user-service >/dev/null 2>&1 && redeploying=true
  for img in "${SERVICES[@]}" frontend; do
    minikube -p "$MINIKUBE_PROFILE" ssh -- docker rmi -f "drift/$img:$TAG" >/dev/null 2>&1 || true
    minikube -p "$MINIKUBE_PROFILE" image load "drift/$img:$TAG"
    ok "drift/$img:$TAG"
  done

  step "Kubernetes: namespaces, ConfigMaps and Secrets generated from each service's .env"
  kubectl --context "$ctx" apply -f deploy/k8s/namespaces/namespaces.yaml >/dev/null
  local host="drift.$ip.nip.io"
  for svc in "${SERVICES[@]}"; do
    local envfile="backend/$svc/.env" plain secrets
    [[ -f "$envfile" ]] || die "$envfile is missing"
    plain="$(mktemp)"; secrets="$(mktemp)"
    while IFS= read -r line || [[ -n "$line" ]]; do
      [[ -z "$line" || "$line" == \#* ]] && continue
      local key="${line%%=*}" value="${line#*=}"
      # where things live inside the cluster replaces what the .env says for local runs
      case "$key" in
        SPRING_PROFILES_ACTIVE) value="default" ;;
        KAFKA_BOOTSTRAP_SERVERS) value="kafka:9092" ;;
        VALKEY_HOST) value="valkey" ;;
        MINIO_ENDPOINT) value="http://minio:9000" ;;
        USER_SERVICE_ADDR) value="user-service:9081" ;;
        ROOM_SERVICE_ADDR) value="room-service:9082" ;;
        FILE_SERVICE_ADDR) value="file-service:9084" ;;
        PUBLIC_BASE_URL|ALLOWED_ORIGINS) value="http://$host" ;;
        OTEL_EXPORTER_OTLP_ENDPOINT) value="http://alloy.observability:4318" ;;
        DATABASE_URL) value="jdbc:postgresql://postgres:5432/$(k8s_database "$svc")" ;;
      esac
      case "$key" in
        *PASSWORD*|*SECRET*|*_KEY|*_KEY_*) echo "$key=$value" >> "$secrets" ;;
        *) echo "$key=$value" >> "$plain" ;;
      esac
    done < "$envfile"
    touch "$plain" "$secrets"
    kubectl --context "$ctx" -n drift create configmap "$svc-config" --from-env-file="$plain" --dry-run=client -o yaml | kubectl --context "$ctx" apply -f - >/dev/null
    kubectl --context "$ctx" -n drift create secret generic "$svc-secret" --from-env-file="$secrets" --dry-run=client -o yaml | kubectl --context "$ctx" apply -f - >/dev/null
    rm -f "$plain" "$secrets"
    ok "$svc: ConfigMap $svc-config + Secret $svc-secret"
  done

  step "Kubernetes: applying manifests (infrastructure → services → frontend → observability)"
  # The observability configs are shared with Docker Compose, so kustomize may read files outside deploy/k8s
  kubectl kustomize --load-restrictor=LoadRestrictionsNone deploy/k8s | kubectl --context "$ctx" apply -f - >/dev/null
  # Ingress hosts are built from the minikube IP (nip.io), so this one is generated
  sed "s/__HOST__/$host/g; s/__GRAFANA_HOST__/grafana.$ip.nip.io/g" deploy/k8s/ingress/ingress.template.yaml | kubectl --context "$ctx" apply -f - >/dev/null
  if $redeploying; then
    # the pods started from the old images: roll them so they pick up the new ones
    for dep in "${SERVICES[@]}" frontend; do kubectl --context "$ctx" -n drift rollout restart "deployment/$dep" >/dev/null; done
    info "restarted the deployments to use the freshly loaded images"
  fi
  for res in statefulset/postgres statefulset/kafka statefulset/valkey statefulset/minio; do
    kubectl --context "$ctx" -n drift rollout status "$res" --timeout=300s
  done
  for job in postgres-init kafka-init minio-init; do
    kubectl --context "$ctx" -n drift wait --for=condition=complete "job/$job" --timeout=300s
  done
  for dep in "${SERVICES[@]}" frontend; do
    kubectl --context "$ctx" -n drift rollout status "deployment/$dep" --timeout=400s
  done
  for dep in prometheus loki tempo alloy grafana; do
    kubectl --context "$ctx" -n observability rollout status "deployment/$dep" --timeout=300s
  done
  ok "the cluster is up"
}

# The database each service owns
k8s_database() {
  case "$1" in
    user-service) echo users_db ;;
    room-service) echo rooms_db ;;
    message-service) echo messages_db ;;
    file-service) echo files_db ;;
  esac
}

if $RUN_K8S; then k8s_setup; else step "Kubernetes skipped (--skip-k8s)"; fi

# ── 7. URLs ─────────────────────────────────────────────────────────────────────────────────────────────────────
step "Done"
edge_port="$(env_value .env EDGE_PORT 8080)"
printf '\n%sURLs%s\n' "$BOLD" "$RESET"
if $RUN_COMPOSE; then
  printf '  Docker Compose\n'
  printf '    App         http://localhost:%s\n' "$edge_port"
  printf '    Grafana     http://localhost:3000       login: %s / %s\n' "$(env_value .env GRAFANA_ADMIN_USER admin)" "$(env_value .env GRAFANA_ADMIN_PASSWORD admin-change-me)"
  printf '    Prometheus  http://localhost:9090\n'
  printf '    MinIO       http://localhost:9001       login: %s / %s\n' "$(env_value .env MINIO_ROOT_USER minioadmin)" "$(env_value .env MINIO_ROOT_PASSWORD change-me)"
fi
if $RUN_K8S; then
  ip="$(minikube -p "$MINIKUBE_PROFILE" ip)"
  printf '  Kubernetes (minikube profile "%s", ip %s)\n' "$MINIKUBE_PROFILE" "$ip"
  printf '    App         http://drift.%s.nip.io\n' "$ip"
  printf '    Grafana     http://grafana.%s.nip.io\n' "$ip"
  printf '    Dashboard   minikube -p %s dashboard\n' "$MINIKUBE_PROFILE"
  printf '    (On macOS with the Docker driver, run "minikube -p %s tunnel" so the nip.io addresses are reachable.)\n' "$MINIKUBE_PROFILE"
fi
printf '  Local development credentials come from the .env files.\n'

# ── 8. open ─────────────────────────────────────────────────────────────────────────────────────────────────────
if $OPEN_BROWSER; then
  opener=""; have open && opener=open; have xdg-open && opener=xdg-open
  if [[ -n "$opener" ]]; then
    step "Opening in the browser"
    $RUN_COMPOSE && { "$opener" "http://localhost:$edge_port" || true; "$opener" "http://localhost:3000" || true; }
    $RUN_K8S && { (minikube -p "$MINIKUBE_PROFILE" dashboard >/dev/null 2>&1 &) || true; }
  fi
fi
