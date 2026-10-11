#!/usr/bin/env python3
"""Run Brújula's end-to-end financial flow with fictional data only."""

from __future__ import annotations

import hashlib
import json
import re
from collections import Counter
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path


@dataclass
class Event:
    raw: str
    received_at: str
    source: str = "SIMULADO_SCOTIABANK"
    amount: int | None = None
    merchant: str | None = None
    category: str = "otros"
    payment_source: str = "tarjeta_credito"
    status: str = "captured"
    fingerprint: str = ""


def parse_event(raw: str, received_at: str) -> Event:
    amount_match = re.search(r"\$\s*([0-9.]+)", raw)
    amount = int(amount_match.group(1).replace(".", "")) if amount_match else None
    merchant_match = re.search(r"en\s+([A-ZÁÉÍÓÚÑ][A-ZÁÉÍÓÚÑ .-]+?)(?:\s+\.\s+|\s+Si\s+|$)", raw)
    merchant = merchant_match.group(1).strip().title() if merchant_match else "Comercio desconocido"
    category = classify(merchant)
    fingerprint = hashlib.sha256(f"{raw}|{received_at[:16]}".encode()).hexdigest()[:16]
    return Event(raw, received_at, amount=amount, merchant=merchant, category=category, fingerprint=fingerprint)


def classify(merchant: str) -> str:
    value = merchant.lower()
    if any(word in value for word in ("super", "jumbo", "lider")):
        return "supermercado"
    if "uber" in value or "recorrido" in value:
        return "transporte"
    if "netflix" in value:
        return "suscripciones"
    return "otros"


def run_simulation() -> dict:
    notifications = [
        ("2026-10-11T10:00:00-03:00", "SIMULADO · Se realizó compra con tu tarjeta por $32.450 en JUMBO COSTANERA"),
        ("2026-10-11T10:00:08-03:00", "SIMULADO · Se realizó compra con tu tarjeta por $32.450 en JUMBO COSTANERA"),
        ("2026-10-10T18:30:00-03:00", "SIMULADO · Se realizó compra con tu tarjeta por $7.800 en UBER"),
        ("2026-10-09T09:15:00-03:00", "SIMULADO · Se realizó compra con tu tarjeta por $7.190 en NETFLIX"),
    ]
    parsed = [parse_event(raw, received_at) for received_at, raw in notifications]
    unique: dict[str, Event] = {}
    for event in parsed:
        unique.setdefault(event.fingerprint, event)
    events = list(unique.values())
    statement = {("Jumbo Costanera", 32450), ("Uber", 7800)}
    reconciled = [event for event in events if (event.merchant, event.amount) in statement]
    for event in reconciled:
        event.status = "reconciled"
    total = sum(event.amount or 0 for event in events if event.category != "otros" or event.amount)
    categories = Counter(event.category for event in events)
    assistant_input = {
        "purpose": "analizar presupuesto personal",
        "privacy": "datos ficticios; sin credenciales ni identificadores bancarios",
        "budget": 1_000_000,
        "month_total": total,
        "events": [asdict(event) for event in events],
        "questions_for_chatgpt": [
            "Resume los tres patrones más importantes.",
            "¿Qué gastos conviene revisar o categorizar manualmente?",
            "Propón un límite para transporte y suscripciones el próximo mes.",
        ],
    }
    return {
        "mode": "SIMULADO",
        "captured": len(parsed),
        "deduplicated": len(events),
        "reconciled": len(reconciled),
        "month_total": total,
        "categories": dict(categories),
        "chatgpt_package": assistant_input,
    }


if __name__ == "__main__":
    print(json.dumps(run_simulation(), ensure_ascii=False, indent=2))
