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
    planning_read = rules.split("match /users/{userId}/planning/{documentId}", 1)[1].split("allow create, update:", 1)[0]
    if "allow read: if request.auth != null" not in planning_read or "request.resource" in planning_read:
        raise SystemExit("La lectura de planning debe autorizarse sin request.resource")
    print("Firestore rules: OK")


if __name__ == "__main__":
    main()
