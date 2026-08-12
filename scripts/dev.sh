#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$ROOT_DIR/.dev-logs"
EXPO_ARGS=("$@")
PIDS=()

cleanup() {
  echo
  echo "Stopping Journy dev processes..."
  for pid in "${PIDS[@]:-}"; do
    if kill -0 "$pid" >/dev/null 2>&1; then
      kill "$pid" >/dev/null 2>&1 || true
    fi
  done
}

run_service() {
  local name="$1"
  shift

  (
    set +e
    "$@" 2>&1 | tee "$LOG_DIR/$name.log" | while IFS= read -r line; do
      printf '[%s] %s\n' "$name" "$line"
    done
  ) &
  PIDS+=("$!")
}

wait_for_http() {
  local name="$1"
  local url="$2"
  local pid="$3"
  local expected_status="${4:-any}"
  local attempts=60

  echo "Waiting for $name..."
  for _ in $(seq 1 "$attempts"); do
    if ! kill -0 "$pid" >/dev/null 2>&1; then
      echo "$name stopped before becoming ready. Check $LOG_DIR/$name.log"
      exit 1
    fi

    local status
    status="$(curl -s -o /dev/null -w "%{http_code}" "$url" || true)"
    if [ "$expected_status" = "any" ] && [ "$status" != "000" ]; then
      echo "$name is ready."
      return 0
    fi
    if [ "$status" = "$expected_status" ]; then
      echo "$name is ready."
      return 0
    fi

    sleep 1
  done

  echo "$name did not become ready in time. Check $LOG_DIR/$name.log"
  exit 1
}

trap cleanup INT TERM EXIT
mkdir -p "$LOG_DIR"

echo "Starting Journy dependencies..."
(cd "$ROOT_DIR/backend" && docker compose up -d)

echo "Starting Journy backend..."
run_service "backend" bash -lc "cd '$ROOT_DIR/backend' && ./mvnw spring-boot:run"
BACKEND_PID="${PIDS[$((${#PIDS[@]} - 1))]}"
wait_for_http "backend" "http://localhost:8080/api/destinations/popular" "$BACKEND_PID"

echo "Starting Journy AI agent..."
run_service "ai-agent" bash -lc "cd '$ROOT_DIR/ai-agent' && source .venv/bin/activate && uvicorn app.main:app --reload --port 8001"
AI_AGENT_PID="${PIDS[$((${#PIDS[@]} - 1))]}"
wait_for_http "ai-agent" "http://localhost:8001/health" "$AI_AGENT_PID" "200"

echo
echo "Journy backend services are ready."
echo "Backend: http://localhost:8080"
echo "AI agent: http://localhost:8001"
echo "Backend log: $LOG_DIR/backend.log"
echo "AI agent log: $LOG_DIR/ai-agent.log"
echo
echo "Starting Expo in interactive mode."
echo "QR code will be shown for Expo Go on the same Wi-Fi network."
echo "You can press i for iOS simulator, a for Android, r to reload."
echo "Press Ctrl+C to stop Expo, backend and AI agent. Docker containers stay running."
echo

cd "$ROOT_DIR/mobile"
if [ "${#EXPO_ARGS[@]}" -eq 0 ]; then
  npx expo start -c --lan
else
  npx expo start -c "${EXPO_ARGS[@]}"
fi
