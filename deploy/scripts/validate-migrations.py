#!/usr/bin/env python3
"""Fail-closed structural validation for the complete Flyway SQL migration chain."""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MIGRATIONS = ROOT / "backend/loan-management-api/loan-backend/src/main/resources/db/migration"
PATTERN = re.compile(r"^V([1-9][0-9]*)__[ ]*([A-Za-z0-9][A-Za-z0-9_. -]*)\.sql$")


def main() -> int:
    if not MIGRATIONS.is_dir():
        print(f"ERROR: Flyway migration directory not found: {MIGRATIONS}", file=sys.stderr)
        return 2

    files = sorted(MIGRATIONS.glob("*.sql"))
    if not files:
        print("ERROR: no Flyway SQL migrations found", file=sys.stderr)
        return 2

    versions: dict[int, Path] = {}
    errors: list[str] = []
    for path in files:
        match = PATTERN.fullmatch(path.name)
        if not match:
            errors.append(f"invalid migration filename: {path.name}")
            continue
        version = int(match.group(1))
        if version in versions:
            errors.append(f"duplicate migration version V{version}: {versions[version].name}, {path.name}")
        versions[version] = path
        try:
            content = path.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            errors.append(f"migration is not UTF-8: {path.name}")
            continue
        if not content.strip():
            errors.append(f"empty migration: {path.name}")
        if "\x00" in content:
            errors.append(f"NUL byte in migration: {path.name}")

    if versions:
        maximum = max(versions)
        missing = sorted(set(range(1, maximum + 1)) - set(versions))
        if missing:
            errors.append("missing migration versions: " + ", ".join(f"V{v}" for v in missing))

    if errors:
        print("Flyway migration validation FAILED:", file=sys.stderr)
        for error in errors:
            print(f" - {error}", file=sys.stderr)
        return 1

    print(f"Flyway migration structural validation PASSED: {len(files)} SQL migrations, V1..V{max(versions)}")
    print("Note: this check does not execute SQL or validate live-database checksums; run PostgreSQL migration integration tests for that.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
