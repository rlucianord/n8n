from __future__ import annotations

from typing import Any
from uuid import uuid4

from app.database import connection
from app.services.memory import log_action

DEFAULT_POLICIES = {
    "phone.answer": ("Atender llamadas permitidas", False, True),
    "phone.reject": ("Rechazar llamadas según reglas", False, True),
    "meeting.join": ("Participar en reuniones seleccionadas", False, True),
    "meeting.speak": ("Hablar durante reuniones", False, True),
    "email.read": ("Leer correos autorizados", False, True),
    "email.draft": ("Preparar respuestas de correo", False, True),
    "email.send": ("Enviar respuestas de correo", False, True),
}

DEFAULT_SETTINGS = {
    "voice_mode": "system",
    "voice_name": "Voz del dispositivo",
    "voice_use_calls": "false",
    "voice_use_meetings": "false",
    "voice_disclose_ai": "true",
    "active_model": "Qwen3",
}


def initialize_agency() -> None:
    with connection() as conn:
        conn.execute("""
            CREATE TABLE IF NOT EXISTS agency_settings (
                setting_key TEXT PRIMARY KEY,
                setting_value TEXT NOT NULL,
                updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            )
        """)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS agency_action_requests (
                id TEXT PRIMARY KEY,
                action TEXT NOT NULL,
                status TEXT NOT NULL CHECK(status IN ('pending','approved','denied')),
                reason TEXT,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                decided_at TEXT
            )
        """)
        for key, value in DEFAULT_SETTINGS.items():
            conn.execute(
                "INSERT OR IGNORE INTO agency_settings(setting_key, setting_value) VALUES (?, ?)",
                (key, value),
            )
        for action, (description, enabled, confirmation) in DEFAULT_POLICIES.items():
            conn.execute(
                """INSERT OR IGNORE INTO policies(id, action, enabled, requires_confirmation, description)
                   VALUES (?, ?, ?, ?, ?)""",
                (str(uuid4()), action, int(enabled), int(confirmation), description),
            )
        conn.commit()


def get_settings() -> dict[str, str]:
    initialize_agency()
    with connection() as conn:
        return {row[0]: row[1] for row in conn.execute(
            "SELECT setting_key, setting_value FROM agency_settings ORDER BY setting_key"
        ).fetchall()}


def update_settings(values: dict[str, Any]) -> dict[str, str]:
    allowed = set(DEFAULT_SETTINGS)
    if not values or set(values) - allowed:
        raise ValueError("Solo se permiten opciones de configuración reconocidas.")
    normalized: dict[str, str] = {}
    for key, value in values.items():
        if not isinstance(value, (str, bool)):
            raise ValueError(f"Valor no válido para {key}.")
        normalized[key] = str(value).lower() if isinstance(value, bool) else value.strip()
        if not normalized[key]:
            raise ValueError(f"El valor de {key} no puede estar vacío.")
    if normalized.get("voice_disclose_ai", "true") != "true":
        raise ValueError("La identificación del asistente de IA no se puede desactivar.")
    with connection() as conn:
        for key, value in normalized.items():
            conn.execute(
                """INSERT INTO agency_settings(setting_key, setting_value, updated_at)
                   VALUES (?, ?, CURRENT_TIMESTAMP)
                   ON CONFLICT(setting_key) DO UPDATE SET
                     setting_value=excluded.setting_value, updated_at=CURRENT_TIMESTAMP""",
                (key, value),
            )
        conn.commit()
    log_action("agency.settings.update", "allowed", ",".join(sorted(normalized)))
    return get_settings()


def get_policies() -> list[dict[str, Any]]:
    initialize_agency()
    with connection() as conn:
        rows = conn.execute(
            "SELECT action, enabled, requires_confirmation, description FROM policies ORDER BY action"
        ).fetchall()
    return [
        {"action": r[0], "enabled": bool(r[1]), "requires_confirmation": bool(r[2]), "description": r[3]}
        for r in rows
    ]


def update_policy(action: str, enabled: bool, requires_confirmation: bool) -> dict[str, Any]:
    initialize_agency()
    if action not in DEFAULT_POLICIES:
        raise ValueError("Acción no reconocida.")
    if not isinstance(enabled, bool) or not isinstance(requires_confirmation, bool):
        raise ValueError("Las opciones deben ser booleanas.")
    with connection() as conn:
        conn.execute(
            "UPDATE policies SET enabled=?, requires_confirmation=? WHERE action=?",
            (int(enabled), int(requires_confirmation), action),
        )
        conn.commit()
    log_action("agency.policy.update", "allowed", action)
    return next(p for p in get_policies() if p["action"] == action)


def evaluate_action(action: str, user_confirmed: bool = False) -> dict[str, Any]:
    initialize_agency()
    with connection() as conn:
        row = conn.execute(
            "SELECT enabled, requires_confirmation, description FROM policies WHERE action=?",
            (action,),
        ).fetchone()
    if row is None:
        log_action("agency.action.evaluate", "denied", f"Unknown action: {action}")
        return {"authorized": False, "status": "denied", "reason": "Acción no reconocida."}
    enabled, confirmation, description = bool(row[0]), bool(row[1]), row[2]
    if not enabled:
        status, authorized, reason = "denied", False, "La acción está desactivada por el usuario."
    elif confirmation and not user_confirmed:
        status, authorized, reason = "pending", False, "Se requiere aprobación explícita del usuario."
    else:
        status, authorized, reason = "approved", True, "La política permite la acción."
    log_action("agency.action.evaluate", status, f"{action}: {reason}")
    return {"authorized": authorized, "status": status, "action": action,
            "description": description, "reason": reason,
            "execution": "not_connected" if authorized else "not_executed"}
