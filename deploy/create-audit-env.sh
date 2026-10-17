#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
OUT="${1:-$ROOT/.env.audit}"
if [[ -e "$OUT" ]]; then
  echo "Refusing to overwrite existing $OUT; inspect/delete it manually if it is only a disposable audit env." >&2
  exit 1
fi
command -v openssl >/dev/null 2>&1 || { echo 'openssl is required' >&2; exit 1; }
rand_b64() { openssl rand -base64 32 | tr -d '\n'; }
rand_password() { openssl rand -hex 16; }
ADMIN_PASSWORD="Aa1!$(openssl rand -hex 18)"
umask 077
cat > "$OUT" <<ENV
# GENERATED FOR LOCAL AUDIT ONLY. Do not commit or use in production.
POSTGRES_ADMIN_USERNAME=loansaas
POSTGRES_ADMIN_PASSWORD=$(rand_password)
DB_PASSWORD=$(rand_password)
FLYWAY_DB_PASSWORD=$(rand_password)
JWT_SECRET=$(rand_b64)
APP_ENCRYPTION_KEY=$(rand_b64)
APP_INDEX_KEY=$(rand_b64)
BACKUP_ENCRYPTION_KEY=$(rand_b64)
BOOTSTRAP_ADMIN_EMAIL=audit-admin@example.invalid
BOOTSTRAP_ADMIN_PASSWORD=$ADMIN_PASSWORD
BOOTSTRAP_ADMIN_NAME=Audit Test Administrator
BOOTSTRAP_ADMIN_PHONE=+250700000000
BOOTSTRAP_ORG_NAME=Noble Loan Solutions Audit Test
BOOTSTRAP_ORG_SLUG=nobleloansolutions
BOOTSTRAP_ORG_PUBLIC_DOMAIN=localhost
BOOTSTRAP_ORG_COUNTRY=RW
BOOTSTRAP_ORG_CURRENCY=RWF
BOOTSTRAP_ORG_TIMEZONE=Africa/Kigali
BOOTSTRAP_ORG_LOCALE=en-RW
BOOTSTRAP_ORG_WEBSITE=http://localhost:18080
BOOTSTRAP_ORG_CONTACT_EMAIL=audit-contact@example.invalid
BOOTSTRAP_ORG_CONTACT_PHONE=+250700000000
BOOTSTRAP_ORG_ADDRESS=LOCAL AUDIT ONLY - NOT A REAL OFFICE
BOOTSTRAP_ORG_REGISTRATION_NUMBER=AUDIT-ONLY-NOT-REAL
APP_ENVIRONMENT=staging
SPRING_PROFILE=default
CORS_ORIGINS=http://localhost:18080
WEBSOCKET_ALLOWED_ORIGINS=http://localhost:18080
FRONTEND_URL=http://localhost:18080
PUBLIC_TENANT_SLUG=nobleloansolutions
PUBLIC_REGISTRATION_ENABLED=false
MAIL_ENABLED=false
MAIL_FROM=audit-noreply@example.invalid
MAIL_FROM_NAME=Noble Loan Solutions Audit
BREVO_API_KEY=AUDIT_ONLY_MAIL_DISABLED
SMS_ENABLED=false
CREDIT_BUREAU_ENABLED=false
CREDIT_BUREAU_REQUIRED_FOR_DISBURSEMENT=true
COMPLIANCE_EXTERNAL_PROVIDER_ENABLED=false
FLUTTERWAVE_ENABLED=false
MTN_MOMO_ENABLED=false
AIRTEL_MONEY_ENABLED=false
OPENAPI_ENABLED=false
EXPOSE_H2=false
NEXT_PUBLIC_API_URL=/api
ENV
chmod 600 "$OUT"
echo "Created local-only audit environment at $OUT (mode 600)."
echo "OTP and password-reset email are disabled in this audit stack; use mocked backend tests for delivery logic."
echo "Do not print or upload this file."
