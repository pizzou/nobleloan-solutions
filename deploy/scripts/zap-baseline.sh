#!/usr/bin/env bash
# OWASP ZAP baseline wrapper. Passive baseline only; run against staging first.
set -euo pipefail

BASE_URL="${BASE_URL:?BASE_URL is required}"
ZAP_IMAGE="${ZAP_IMAGE:-ghcr.io/zaproxy/zaproxy:stable}"
REPORT_DIR="${REPORT_DIR:-./security-reports}"
mkdir -p "$REPORT_DIR"

# Never run an active scan against production from this script.
if [[ "${ALLOW_ZAP_TARGET:-}" == "production" ]]; then
  echo "Refusing to run ZAP baseline against a target explicitly marked production." >&2
  echo "Run the scan against a production-equivalent staging environment." >&2
  exit 2
fi

docker run --rm \
  -v "$(pwd)/$REPORT_DIR:/zap/wrk:rw" \
  "$ZAP_IMAGE" \
  zap-baseline.py \
  -t "$BASE_URL" \
  -r zap-baseline.html \
  -J zap-baseline.json \
  -m 5

echo "ZAP baseline reports written to $REPORT_DIR"
