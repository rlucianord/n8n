from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path


def test_agency_settings_and_action_policies(tmp_path: Path):
    env = os.environ.copy()
    env["MIDOBLE_DB_KEY"] = "c" * 64
    env["MIDOBLE_DB_PATH"] = str(tmp_path / "agency-test.db")
    env["MIDOBLE_HOST"] = "127.0.0.1"

    code = r"""
from fastapi.testclient import TestClient
from app.main import app

with TestClient(app) as client:
    policies = client.get("/agency/policies")
    assert policies.status_code == 200
    assert policies.json()
    assert all(not item["enabled"] for item in policies.json())

    settings = client.put("/agency/settings", json={"values": {
        "voice_mode": "custom_user_voice",
        "voice_name": "Mi voz",
        "active_model": "Phi-4",
    }})
    assert settings.status_code == 200
    assert settings.json()["voice_mode"] == "custom_user_voice"
    assert settings.json()["active_model"] == "Phi-4"

    forbidden = client.put("/agency/settings", json={"values": {
        "voice_disclose_ai": False
    }})
    assert forbidden.status_code == 422

    denied = client.post("/agency/decisions/evaluate", json={
        "action": "email.send", "user_confirmed": True
    })
    assert denied.status_code == 200
    assert denied.json()["status"] == "denied"

    configured = client.put("/agency/policies/email.send", json={
        "enabled": True, "requires_confirmation": True
    })
    assert configured.status_code == 200

    pending = client.post("/agency/decisions/evaluate", json={
        "action": "email.send", "user_confirmed": False
    })
    assert pending.json()["status"] == "pending"

    approved = client.post("/agency/decisions/evaluate", json={
        "action": "email.send", "user_confirmed": True
    })
    assert approved.json()["authorized"] is True
    assert approved.json()["execution"] == "not_connected"

    catalog = client.get("/agency/model-catalog")
    assert [item["name"] for item in catalog.json()["models"]] == [
        "Phi-4", "Qwen3", "Llama 3.2", "Gemma 3"
    ]
    assert all("GPT-OSS" not in item["name"] for item in catalog.json()["models"])
"""
    root = Path(__file__).resolve().parents[1]
    subprocess.run(
        [sys.executable, "-c", code],
        cwd=root,
        env=env,
        check=True,
    )
