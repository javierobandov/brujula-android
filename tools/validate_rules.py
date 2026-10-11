#!/usr/bin/env python3
"""Static guardrails for the Firestore rules shipped with Brújula."""

from pathlib import Path


def main() -> None:
    rules = Path(__file__).resolve().parents[1].joinpath("firestore.rules").read_text(encoding="utf-8")
    required = [
        "rules_version = '2';",
        "match /users/{userId}/events/{eventId}",
        "match /users/{userId}/planning/{documentId}",
        "request.auth.uid == userId",
        "documentId == 'snapshot'",
        "request.resource.data.schema_version == 1",
        "allow delete: if false;",
    ]
    missing = [fragment for fragment in required if fragment not in rules]
    if missing:
        raise SystemExit("Faltan garantías de Firestore: " + ", ".join(missing))
    if "allow read, write: if true" in rules or "allow write: if true" in rules:
        raise SystemExit("Las reglas no pueden quedar abiertas")
    print("Firestore rules: OK")


if __name__ == "__main__":
    main()
