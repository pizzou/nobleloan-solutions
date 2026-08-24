#!/usr/bin/env bash
# Production security smoke checks. This is NOT a penetration test.
set -euo pipefail

BASE_URL="${BASE_URL:?BASE_URL is required}"
ORIGIN="${ORIGIN:?ORIGIN is required, e.g. https://nobleloan-solutions.vercel.app}"
TENANT_SLUG="${TENANT_SLUG:-nobleloansolutions}"
FAILED=0

pass() { echo "[SECURITY PASS] $1"; }
fail() { echo "[SECURITY FAIL] $1"; FAILED=1; }

# CORS must explicitly allow the production frontend and the tenant header.
CORS_HEADERS="$(curl -sS -i -X OPTIONS "${BASE_URL%/}/api/auth/login" \
  -H "Origin: $ORIGIN" \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type,x-tenant-slug')"

grep -qi "access-control-allow-origin: $ORIGIN" <<<"$CORS_HEADERS" \
  && pass "CORS allows the configured frontend origin" \
  || fail "CORS does not allow the configured frontend origin"

grep -Eqi 'access-control-allow-headers:.*x-tenant-slug' <<<"$CORS_HEADERS" \
  && pass "CORS allows X-Tenant-Slug" \
  || fail "CORS does not allow X-Tenant-Slug"

# Protected endpoint must not be readable without authentication.
HTTP_CODE="$(curl -sS -o /dev/null -w '%{http_code}' "${BASE_URL%/}/api/accounting/trial-balance")"
[[ "$HTTP_CODE" == "401" || "$HTTP_CODE" == "403" ]] \
  && pass "Protected accounting endpoint rejects unauthenticated access ($HTTP_CODE)" \
  || fail "Protected accounting endpoint returned unexpected HTTP $HTTP_CODE"

# Prometheus should not be public unless the deployment explicitly protects it upstream.
METRICS_CODE="$(curl -sS -o /dev/null -w '%{http_code}' "${BASE_URL%/}/actuator/prometheus")"
[[ "$METRICS_CODE" == "401" || "$METRICS_CODE" == "403" || "$METRICS_CODE" == "404" ]] \
  && pass "Prometheus metrics are not publicly exposed ($METRICS_CODE)" \
  || fail "Prometheus metrics appear publicly exposed ($METRICS_CODE)"

# TRACE should not be enabled on the application/proxy.
TRACE_CODE="$(curl -sS -o /dev/null -w '%{http_code}' -X TRACE "${BASE_URL%/}/api/auth/login")"
[[ "$TRACE_CODE" == "400" || "$TRACE_CODE" == "403" || "$TRACE_CODE" == "404" || "$TRACE_CODE" == "405" ]] \
  && pass "TRACE is disabled/rejected ($TRACE_CODE)" \
  || fail "TRACE returned unexpected HTTP $TRACE_CODE"

# The fixed tenant slug must be the only tenant identity used by this deployment.
[[ "$TENANT_SLUG" == "nobleloansolutions" ]] \
  && pass "Tenant identity is fixed to nobleloansolutions" \
  || fail "Unexpected tenant slug: $TENANT_SLUG"

exit "$FAILED"
