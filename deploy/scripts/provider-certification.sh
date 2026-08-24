#!/usr/bin/env bash
# ================================================================
# Provider production-certification gate.
#
# This script deliberately refuses to claim certification from config alone.
# A provider is certified only after the provider's own onboarding/sandbox/
# production acceptance evidence has been completed and the corresponding
# environment variable is set to true.
# ================================================================
set -euo pipefail

FAILED=0
pass(){ echo "[PROVIDER PASS] $1"; }
fail(){ echo "[PROVIDER FAIL] $1"; FAILED=1; }

require_true(){
  local name="$1"
  local value="${!name:-false}"
  [[ "$value" == "true" ]] && pass "$name=true" || fail "$name must be true"
}

require_value(){
  local name="$1"
  local value="${!name:-}"
  [[ -n "$value" ]] && pass "$name is configured" || fail "$name is missing"
}

require_true KYC_AML_CERTIFIED
require_true CREDIT_BUREAU_CERTIFIED
require_true PAYMENT_PROVIDER_CERTIFIED

require_value COMPLIANCE_PROVIDER
require_value COMPLIANCE_BASE_URL
require_value COMPLIANCE_API_KEY
require_value COMPLIANCE_KYC_PROVIDER
require_value COMPLIANCE_KYC_BASE_URL
require_value COMPLIANCE_KYC_API_KEY
require_value CREDIT_BUREAU_BASE_URL
require_value CREDIT_BUREAU_API_KEY

if [[ "${APP_ENVIRONMENT:-production}" == "production" ]]; then
  [[ "${CREDIT_BUREAU_REQUIRED_FOR_DISBURSEMENT:-true}" == "true" ]] \
    && pass "Credit Bureau is required before disbursement" \
    || fail "Credit Bureau must be required before production disbursement"

  [[ "${COMPLIANCE_EXTERNAL_PROVIDER_ENABLED:-false}" == "true" ]] \
    && pass "External KYC/AML provider is enabled" \
    || fail "External KYC/AML provider must be enabled in production"
fi

echo ""
if [[ "$FAILED" -eq 0 ]]; then
  echo "[PROVIDER] Configuration and certification gate passed."
else
  echo "[PROVIDER] Certification gate FAILED. No production money movement should be enabled."
fi
exit "$FAILED"
