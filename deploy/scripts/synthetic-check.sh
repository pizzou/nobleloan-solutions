#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${BASE_URL:?BASE_URL is required, e.g. https://nobleloan-solutions.onrender.com}"
HEALTH_URL="${HEALTH_URL:-${BASE_URL%/}/actuator/health/readiness}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-15}"

TMP_FILE="$(mktemp)"
trap 'rm -f "$TMP_FILE"' EXIT

HTTP_CODE="$(curl -sS --max-time "$TIMEOUT_SECONDS" -o "$TMP_FILE" -w '%{http_code}' "$HEALTH_URL")"

if [[ "$HTTP_CODE" != "200" ]]; then
  echo "[SYNTHETIC] FAIL health endpoint returned HTTP $HTTP_CODE"
  cat "$TMP_FILE" || true
  exit 1
fi

if ! grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' "$TMP_FILE"; then
  echo "[SYNTHETIC] FAIL readiness endpoint is not UP"
  cat "$TMP_FILE"
  exit 1
fi

echo "[SYNTHETIC] PASS $HEALTH_URL"
