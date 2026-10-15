#!/usr/bin/env bash
# Compatibility entrypoint. Use the canonical production restore utility so
# backup verification, encryption handling, explicit confirmation, and health
# checks remain consistent with the current deployment architecture.
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "$SCRIPT_DIR/../../../.." && pwd)"
exec "$PROJECT_ROOT/deploy/scripts/restore.sh" "$@"
