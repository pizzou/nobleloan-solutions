#!/usr/bin/env bash
# Noble Loan Solutions — local production code gate.
# Runs repository checks that can be executed before deployment.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND="$ROOT_DIR/backend/loan-management-api/loan-backend"
FRONTEND="$ROOT_DIR/frontend/loan-management-ui"

echo "[GATE] Checking tenant configuration..."
grep -q 'nobleloansolutions' "$FRONTEND/lib/tenant.ts"
echo "[GATE] Tenant slug is fixed to nobleloansolutions."

echo "[GATE] Checking forbidden production simulation settings..."
grep -q 'app.credit-bureau.simulation-enabled=false' "$BACKEND/src/main/resources/application.properties"
echo "[GATE] Credit-bureau internal simulation is disabled."

echo "[GATE] Checking production certification scripts..."
test -x "$ROOT_DIR/deploy/scripts/production-acceptance.sh" 2>/dev/null || chmod +x "$ROOT_DIR/deploy/scripts/production-acceptance.sh"
test -x "$ROOT_DIR/deploy/scripts/provider-certification.sh" 2>/dev/null || chmod +x "$ROOT_DIR/deploy/scripts/provider-certification.sh"

echo "[GATE] Checking frontend TypeScript..."
cd "$FRONTEND"
node node_modules/typescript/bin/tsc --noEmit

echo "[GATE] Checking frontend ESLint..."
node node_modules/next/dist/bin/next lint --max-warnings=0

echo "[GATE] Frontend typecheck + lint PASSED."

echo "[GATE] Backend Maven tests must be run with Maven in CI/deployment environment:"
echo "       cd backend/loan-management-api/loan-backend && mvn clean test"
echo "[GATE] External certification still required: KYC/AML, credit bureau, payment provider, DR restore, penetration test."
echo "[GATE] CODE GATE PASSED for locally executable checks."
