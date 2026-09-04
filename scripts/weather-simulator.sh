#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${1:-8080}"
if [[ "$PORT" != 8080 && "$PORT" != 8082 ]]; then
  echo 'Usage: bash scripts/weather-simulator.sh [8080|8082]' >&2
  exit 1
fi
if lsof -nP -iTCP:"$PORT" -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Port $PORT is occupied. Stop the normal backend yourself; this launcher never kills it." >&2
  exit 1
fi
cd "$ROOT_DIR/backend"
echo 'DEV ONLY: isolated memory database + recorded real OSM places + synthetic rain.'
echo 'No normal database is read or written. Ctrl+C discards this scenario.'
exec ./mvnw test-compile spring-boot:test-run \
  -Dspring-boot.run.main-class=com.journy.backend.weather.simulator.WeatherSimulator \
  -Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false \
  -Dspring-boot.run.arguments="$PORT"
