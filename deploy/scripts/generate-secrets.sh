#!/usr/bin/env bash
#
# generate-secrets.sh — produces cryptographically strong random values for
# every secret this app needs, in .env-ready format.
#
# This script closes the "generate the values" part of secret rotation.
# It does NOT close the actual task on the readiness checklist — you still
# need to:
#   1. Run this once per environment (dev, staging, prod each get their own
#      values — never reuse a value across environments).
#   2. Put the output into your real secrets manager (or, at minimum, a
#      .env file that is NOT committed to git and NOT reused from this repo's
#      .env.example).
#   3. Restart the backend so it picks up the new values.
#
# APP_ENCRYPTION_KEY / APP_INDEX_KEY specifically: if you are ROTATING an
# already-in-use key (not setting it for the first time), do not just swap
# the value — existing encrypted columns were encrypted with the old key and
# will fail to decrypt. See SECRETS_AND_KEY_ROTATION.md: a re-encryption
# migration is required for that case and does not exist yet.
#
# Usage:
#   ./deploy/scripts/generate-secrets.sh            # print to terminal
#   ./deploy/scripts/generate-secrets.sh > secrets.env   # save to a file
#                                                          # (then chmod 600 it,
#                                                          #  don't commit it)

set -euo pipefail

rand_b64() {
  # 32 random bytes, base64-encoded, no line wraps — works with or without openssl
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 32 | tr -d '\n'
  else
    head -c 32 /dev/urandom | base64 | tr -d '\n'
  fi
}

rand_password() {
  # 20-char strong password: mixed case, digits, symbols, no ambiguous chars
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 24 | tr -dc 'A-Za-z0-9' | head -c 20
  else
    head -c 24 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 20
  fi
  echo
}

echo "# ─────────────────────────────────────────────────────────────────"
echo "# Generated $(date -u +%Y-%m-%dT%H:%M:%SZ) — DO NOT COMMIT THIS FILE"
echo "# Each value below is unique to this run. Re-running generates new,"
echo "# different values — that's expected, not a bug."
echo "# ─────────────────────────────────────────────────────────────────"
echo
echo "# Signs and verifies JWT auth tokens. Rotating this invalidates every"
echo "# currently-logged-in session (a drop-in restart, per"
echo "# SECRETS_AND_KEY_ROTATION.md — safe to rotate any time)."
echo "JWT_SECRET=$(rand_b64)"
echo
echo "# Encrypts national ID, phone, address, spouse info, bank account at rest."
echo "# ⚠️ If this is a FIRST-TIME set (new environment, empty database), just use it."
echo "# ⚠️ If you are ROTATING an existing key, read the warning at the top of this"
echo "#    script first — swapping this value alone will break decryption of"
echo "#    already-encrypted rows."
echo "APP_ENCRYPTION_KEY=$(rand_b64)"
echo
echo "# Separate key used only to compute searchable lookup hashes for the"
echo "# encrypted fields above. Must be different from APP_ENCRYPTION_KEY."
echo "APP_INDEX_KEY=$(rand_b64)"
echo
echo "# Encrypts nightly database backups (deploy/scripts/backup.sh)."
echo "# Store this separately from where the backups themselves are kept —"
echo "# an attacker with both the backup and this key has everything."
echo "BACKUP_ENCRYPTION_KEY=$(rand_b64)"
echo
echo "# Suggested password for the real BOOTSTRAP_ADMIN_PASSWORD (first admin"
echo "# account created on a fresh production database). Set BOOTSTRAP_ADMIN_EMAIL"
echo "# and BOOTSTRAP_ADMIN_NAME too — see .env.example. Change this password"
echo "# after first login if your policy requires it; this is just a strong,"
echo "# non-guessable starting value."
echo "BOOTSTRAP_ADMIN_PASSWORD=$(rand_password)"
echo
echo "# ─────────────────────────────────────────────────────────────────"
echo "# Still needed from real accounts — this script can't generate these:"
echo "#   MAIL_USERNAME / MAIL_PASSWORD        (real mailbox + app password)"
echo "#   FLUTTERWAVE_SECRET_KEY / PUBLIC_KEY / WEBHOOK_SECRET  (from Flutterwave dashboard)"
echo "# ─────────────────────────────────────────────────────────────────"
