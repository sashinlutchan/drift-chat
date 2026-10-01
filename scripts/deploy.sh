#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

SERVICES=(user-service room-service message-service file-service)
MINIKUBE_PROFILE="${MINIKUBE_PROFILE:-drift}"
TAG="${DRIFT_TAG:-local}"
EDGE_PORT="${EDGE_PORT:-8080}"
export EDGE_PORT
DATABASES=(users_db rooms_db messages_db files_db)
TOPICS=(user.deleted user.updated presence.changed room.created room.updated room.deleted member.joined member.left room.occupancy message.created)
BUCKETS=(drift-avatars drift-media)

usage() {
  sed -n '2,/^$/p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

require() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "error: '$1' is required but not installed. $2" >&2
    exit 1
  fi
}

k8s_database() {
  case "$1" in
    user-service) echo users_db ;;
    room-service) echo rooms_db ;;
    message-service) echo messages_db ;;
    file-service) echo files_db ;;
  esac
}

check_tools() {
  local mode="$1"
  require docker "Install Docker Desktop: https://docs.docker.com/get-docker/"
  docker compose version >/dev/null 2>&1 || { echo "error: 'docker compose' is required (it ships with Docker Desktop)." >&2; exit 1; }
  docker info >/dev/null 2>&1 || { echo "error: Docker is not running. Start Docker Desktop and try again." >&2; exit 1; }
  if [ "$mode" != "compose-up" ]; then
    require minikube "Install it with: brew install minikube"
    require kubectl "Install it with: brew install kubectl"
  fi
  if [ "${SKIP_TESTS:-false}" != "true" ]; then
    require java "Install Amazon Corretto 25: https://aws.amazon.com/corretto/"
    require node "Install the current Node.js LTS: https://nodejs.org/"
    require yarn "Install it with: npm install -g yarn"
  fi
}

check_env_files() {
  echo "Checking the service .env files..."
  for svc in "${SERVICES[@]}"; do
    local envdir="$ROOT_DIR/backend/$svc"
    if [ ! -f "$envdir/.env" ]; then
      cp "$envdir/.env.example" "$envdir/.env"
      echo "  backend/$svc/.env was missing, copied from .env.example (edit it if you need other values)"
    fi
  done
  local secret="$ROOT_DIR/deploy/k8s/infra/secret.yaml"
  if [ ! -f "$secret" ]; then
    cp "$ROOT_DIR/deploy/k8s/infra/secret.example.yaml" "$secret"
    echo "  deploy/k8s/infra/secret.yaml was missing, copied from secret.example.yaml (edit it if you need other values)"
  fi
}

run_tests() {
  if [ "${SKIP_TESTS:-false}" = "true" ]; then
    echo "Skipping the tests (SKIP_TESTS=true)."
    return 0
  fi
  echo "Running all tests (deployment stops if any fail)..."
  for svc in "${SERVICES[@]}"; do
    echo "  $svc: ./gradlew check (ktlint, detekt, unit + integration tests, coverage)"
    (cd "$ROOT_DIR/backend/$svc" && ./gradlew check --console=plain -q)
  done
  echo "  frontend: lint, type-check, tests"
  (cd "$ROOT_DIR/frontend/drift-chat" && yarn install --frozen-lockfile --silent && yarn lint && yarn tsc -b && yarn test)
}

build_images() {
  echo "Building Docker images (four services + frontend)..."
  cd "$ROOT_DIR"
  docker compose build "${SERVICES[@]}" frontend
}

open_browser() {
  [ "${NO_OPEN:-false}" = "true" ] && return 0
  local opener=""
  command -v open >/dev/null 2>&1 && opener=open
  command -v xdg-open >/dev/null 2>&1 && opener=xdg-open
  [ -z "$opener" ] && return 0
  echo "Opening in the browser..."
  if [ "$1" != "k8s-up" ]; then
    "$opener" "http://localhost:$EDGE_PORT" || true
    "$opener" "http://localhost:3000" || true
    "$opener" "http://localhost:9001" || true
  fi
  if [ "$1" != "compose-up" ]; then
    (minikube -p "$MINIKUBE_PROFILE" dashboard >/dev/null 2>&1 &) || true
  fi
}

compose_up() {
  cd "$ROOT_DIR"
  echo "Starting the infrastructure (postgres, kafka, valkey, minio)..."
  docker compose up -d --wait postgres kafka valkey minio

  echo "Creating databases, Kafka topics and MinIO buckets (skipped if they exist)..."
  local db_user="${POSTGRES_USER:-admin-change-me}"
  for db in "${DATABASES[@]}"; do
    if docker compose exec -T postgres psql -U "$db_user" -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$db'" | grep -q 1; then
      echo "  database $db already exists"
    else
      docker compose exec -T postgres psql -U "$db_user" -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE $db OWNER \"$db_user\"" >/dev/null
      echo "  created database $db"
    fi
  done
  for topic in "${TOPICS[@]}"; do
    docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 \
      --create --if-not-exists --topic "$topic" --partitions 3 --replication-factor 1 >/dev/null
  done
  echo "  Kafka topics: ${TOPICS[*]}"
  local root_user="${MINIO_ROOT_USER:-admin-change-me}" root_pass="${MINIO_ROOT_PASSWORD:-admin-change-me}"
  docker run --rm --network "drift_default" -e "MC_HOST_local=http://$root_user:$root_pass@minio:9000" \
    chainguard/minio-client:latest \
    mb --ignore-existing "${BUCKETS[@]/#/local/}" >/dev/null
  echo "  MinIO buckets: ${BUCKETS[*]}"

  echo "Starting the services, frontend, nginx and observability stack..."
  docker compose up -d --wait
  echo
  echo "App: http://localhost:$EDGE_PORT"
  echo "  nginx routes /api and /socket.io -> the services; everything else -> frontend"
  echo "Grafana: http://localhost:3000   (no login)"
  echo "Prometheus: http://localhost:9090"
  echo "MinIO: http://localhost:9001   login: $root_user / $root_pass"
}

all_up() {
  check_tools all-up
  check_env_files
  run_tests
  build_images
  local docker_mb=$(( $(docker info --format '{{.MemTotal}}') / 1024 / 1024 ))
  if [ "$docker_mb" -lt 12000 ]; then
    echo "warning: Docker Desktop has ${docker_mb} MB. Compose and minikube together need about 12 GB."
    echo "         If the Kubernetes step runs out of memory, raise Docker's memory limit or run compose-up and k8s-up one at a time."
  fi
  echo
  echo "=== Step 1/2: Starting the Docker Compose stack ==="
  compose_up
  echo
  echo "=== Step 2/2: Starting Minikube and deploying to Kubernetes ==="
  RESERVE_MB=4500 k8s_up
  echo
  "$ROOT_DIR/scripts/status.sh" || true
  open_browser all-up
}

k8s_up() {
  cd "$ROOT_DIR"
  if ! minikube -p "$MINIKUBE_PROFILE" status >/dev/null 2>&1; then
    local docker_mb mem reserve="${RESERVE_MB:-700}"
    docker_mb=$(( $(docker info --format '{{.MemTotal}}') / 1024 / 1024 ))
    mem="${MINIKUBE_MEMORY:-$(( docker_mb - reserve > 8192 ? 8192 : docker_mb - reserve ))}"
    echo "Starting Minikube profile '$MINIKUBE_PROFILE' (4 CPU / ${mem} MB RAM; Docker has ${docker_mb} MB)..."
    minikube start -p "$MINIKUBE_PROFILE" --driver=docker --cpus=4 --memory="$mem" --disk-size=60g
  else
    echo "Minikube profile '$MINIKUBE_PROFILE' already running."
  fi

  minikube -p "$MINIKUBE_PROFILE" addons enable ingress >/dev/null
  minikube -p "$MINIKUBE_PROFILE" addons enable metrics-server >/dev/null
  minikube -p "$MINIKUBE_PROFILE" addons enable dashboard >/dev/null
  local ip ctx="$MINIKUBE_PROFILE"
  ip="$(minikube -p "$MINIKUBE_PROFILE" ip)"
  local host="drift.$ip.nip.io"

  echo "Loading the images into Minikube (no registry needed)..."
  local redeploying=false
  kubectl --context "$ctx" -n drift get deployment user-service >/dev/null 2>&1 && redeploying=true
  for img in "${SERVICES[@]}" frontend; do
    minikube -p "$MINIKUBE_PROFILE" ssh -- docker rmi -f "drift/$img:$TAG" >/dev/null 2>&1 || true
    minikube -p "$MINIKUBE_PROFILE" image load "drift/$img:$TAG"
    echo "  drift/$img:$TAG"
  done

  echo "Creating the namespaces, ConfigMaps and Secrets from each service's .env..."
  kubectl --context "$ctx" apply -f deploy/k8s/namespaces/namespaces.yaml >/dev/null
  for svc in "${SERVICES[@]}"; do
    local envfile="backend/$svc/.env" plain secrets
    [ -f "$envfile" ] || { echo "error: $envfile is missing" >&2; exit 1; }
    plain="$(mktemp)"; secrets="$(mktemp)"
    while IFS= read -r line || [ -n "$line" ]; do
      [[ -z "$line" || "$line" == \#* ]] && continue
      local key="${line%%=*}" value="${line#*=}"
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
    echo "  $svc: ConfigMap $svc-config + Secret $svc-secret"
  done

  echo "Applying the manifests (infrastructure -> services -> frontend -> observability)..."
  kubectl --context "$ctx" -n drift delete job postgres-init kafka-init minio-init --ignore-not-found >/dev/null
  kubectl kustomize --load-restrictor=LoadRestrictionsNone deploy/k8s | kubectl --context "$ctx" apply -f - >/dev/null
  sed "s/__HOST__/$host/g; s/__GRAFANA_HOST__/grafana.$ip.nip.io/g" deploy/k8s/ingress/ingress.template.yaml | kubectl --context "$ctx" apply -f - >/dev/null
  if $redeploying; then
    for dep in "${SERVICES[@]}" frontend; do kubectl --context "$ctx" -n drift rollout restart "deployment/$dep" >/dev/null; done
    kubectl --context "$ctx" -n drift rollout restart statefulset/minio >/dev/null
    echo "  restarted the deployments to use the freshly loaded images, and MinIO to pick up its Secret"
  fi

  echo "Waiting for everything to be ready (this can take a few minutes)..."
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

  echo
  echo "The stack is up on Minikube profile '$MINIKUBE_PROFILE'."
  echo "App: http://$host"
  echo "Grafana: http://grafana.$ip.nip.io"
  echo "Dashboard: minikube -p $MINIKUBE_PROFILE dashboard"
  echo "On macOS with the Docker driver run this in another terminal so the nip.io addresses work:"
  echo "  minikube -p $MINIKUBE_PROFILE tunnel"
}

case "${1:-all-up}" in
  all-up)
    all_up
    ;;
  compose-up)
    check_tools compose-up
    check_env_files
    run_tests
    build_images
    compose_up
    echo
    "$ROOT_DIR/scripts/status.sh" || true
    open_browser compose-up
    ;;
  k8s-up)
    check_tools k8s-up
    check_env_files
    run_tests
    build_images
    k8s_up
    echo
    "$ROOT_DIR/scripts/status.sh" || true
    open_browser k8s-up
    ;;
  -h|--help|help)
    usage
    ;;
  *)
    usage
    exit 1
    ;;
esac
