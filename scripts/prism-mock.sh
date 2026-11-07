#!/usr/bin/env bash
# =============================================================================
# TTPU "Raqamli Inventar" — OpenAPI Contract Exporter & Prism Mock Server
# For Next.js (Web) and Kotlin Multiplatform (KMP Android & iOS) Developers
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
OPENAPI_FILE="${PROJECT_ROOT}/openapi.yaml"
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
PRISM_PORT="${PRISM_PORT:-4010}"

echo "==> Checking if backend is running at ${BACKEND_URL}/openapi.yaml ..."
if curl -fsS "${BACKEND_URL}/openapi.yaml" -o "${OPENAPI_FILE}.tmp" 2>/dev/null; then
  mv "${OPENAPI_FILE}.tmp" "${OPENAPI_FILE}"
  echo "==> Refreshed ${OPENAPI_FILE} from ${BACKEND_URL}/openapi.yaml"
else
  rm -f "${OPENAPI_FILE}.tmp"
  echo "==> Using existing contract at ${OPENAPI_FILE}"
fi

if [[ "${1:-}" == "--export-only" ]]; then
  echo "==> OpenAPI YAML specification available at: ${OPENAPI_FILE}"
  exit 0
fi

echo "==> Starting Stoplight Prism Mock Server on http://localhost:${PRISM_PORT} ..."
if command -v npx >/dev/null 2>&1; then
  exec npx --yes @stoplight/prism-cli mock -h 0.0.0.0 -p "${PRISM_PORT}" "${OPENAPI_FILE}"
elif command -v docker >/dev/null 2>&1; then
  exec docker run --rm -it -p "${PRISM_PORT}:4010" -v "${OPENAPI_FILE}:/tmp/openapi.yaml:ro" stoplight/prism:5 mock -h 0.0.0.0 /tmp/openapi.yaml
else
  echo "Error: Neither 'npx' nor 'docker' is installed to run Stoplight Prism." >&2
  exit 1
fi
