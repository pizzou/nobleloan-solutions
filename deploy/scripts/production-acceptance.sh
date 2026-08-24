#!/usr/bin/env bash
# ================================================================
# Noble Loan Solutions — Final Production Acceptance Test
#
# This test is intentionally safe: it does not create a real loan,
# disburse funds or initiate a real payment.
#
# Required:
#   BASE_URL=https://nobleloan-solutions.onrender.com
#   ORIGIN=https://nobleloan-solutions.vercel.app
#
# Optional authenticated checks:
#   ACCEPTANCE_JWT=<short-lived test/admin token>
# ================================================================
set -euo pipefail

BASE_URL="${BASE_URL:?BASE_URL is required}"
ORIGIN="${ORIGIN:?ORIGIN is required}"
TENANT_SLUG="${TENANT_SLUG:-nobleloansolutions}"
TOKEN="${ACCEPTANCE_JWT:-}"
FAILED=0

pass() { echo "[ACCEPTANCE PASS] $1"; }
fail() { echo "[ACCEPTANCE FAIL] $1"; FAILED=1; }

health="$(curl -sS --max-time 20 "${BASE_URL%/}/actuator/health/readiness")" 
grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' <<<"$health" \
  && pass "Backend readiness is UP" \
  || fail "Backend readiness is not UP"

frontend_code="$(curl -sS -o /dev/null -w '%{http_code}' --max-time 20 "$ORIGIN")"
[[ "$frontend_code" == "200" ]] \
  && pass "Frontend is reachable ($frontend_code)" \
  || fail "Frontend returned HTTP $frontend_code"

cors="$(curl -sS -i -X OPTIONS "${BASE_URL%/}/api/auth/login" \
  -H "Origin: $ORIGIN" \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type,x-tenant-slug')"
grep -qi "access-control-allow-origin: $ORIGIN" <<<"$cors" \
  && pass "Production CORS origin accepted" \
  || fail "Production CORS origin rejected"
grep -Eqi 'access-control-allow-headers:.*x-tenant-slug' <<<"$cors" \
  && pass "Tenant header accepted by CORS" \
  || fail "Tenant header rejected by CORS"

if [[ -n "$TOKEN" ]]; then
  auth_header="Authorization: Bearer $TOKEN"
  reconciliation_code="$(curl -sS -o /tmp/noble-reconciliation.json -w '%{http_code}' \
    -H "$auth_header" \
    -H "X-Tenant-Slug: $TENANT_SLUG" \
    "${BASE_URL%/}/api/accounting/reconciliation")"

  if [[ "$reconciliation_code" == "200" ]]; then
    grep -Eq '"balanced"[[:space:]]*:[[:space:]]*true' /tmp/noble-reconciliation.json \
      && pass "Financial reconciliation endpoint is balanced" \
      || fail "Financial reconciliation endpoint returned a non-balanced report"
  else
    fail "Financial reconciliation endpoint returned HTTP $reconciliation_code"
  fi

  rm -f /tmp/noble-reconciliation.json
else
  echo "[ACCEPTANCE INFO] ACCEPTANCE_JWT not supplied; authenticated financial checks were skipped."
fi

# The final acceptance test must never silently certify provider integrations.
for required in \
  CREDIT_BUREAU_CERTIFIED \
  KYC_AML_CERTIFIED \
  PAYMENT_PROVIDER_CERTIFIED \
  DR_RESTORE_CERTIFIED \
  SECURITY_PENTEST_PASSED; do
  value="${!required:-false}"
  [[ "$value" == "true" ]] \
    && pass "$required=true" \
    || fail "$required is not true"
done

exit "$FAILED"
