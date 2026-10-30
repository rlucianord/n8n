from __future__ import annotations

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.services import agency


@pytest.fixture
def client(tmp_path, monkeypatch):
    from app import config, database
    monkeypatch.setattr(config, "DB_PATH", tmp_path / "agency.db")
    monkeypatch.setattr(config, "DB_KEY", "test-key-" + "x" * 40)
    database.initialize_database()
    agency.initialize_agency()
    return TestClient(app)


def test_default_policies_are_disabled(client):
    policies = client.get("/agency/policies").json()
    assert policies
    assert all(not item["enabled"] for item in policies)


def test_user_can_configure_voice_and_model(client):
    response = client.put("/agency/settings", json={
        "voice_mode": "custom_user_voice",
        "voice_name": "Mi voz",
        "active_model": "Phi-4",
    })
    assert response.status_code == 200
    assert response.json()["voice_mode"] == "custom_user_voice"
    assert response.json()["active_model"] == "Phi-4"


def test_ai_disclosure_cannot_be_disabled(client):
    response = client.put("/agency/settings", json={"voice_disclose_ai": False})
    assert response.status_code == 422


def test_action_requires_user_enablement_and_confirmation(client):
    denied = client.post("/agency/decisions/evaluate", json={
        "action": "email.send", "user_confirmed": True
    }).json()
    assert denied["status"] == "denied"

    client.put("/agency/policies/email.send", json={
        "enabled": True, "requires_confirmation": True
    })
    pending = client.post("/agency/decisions/evaluate", json={
        "action": "email.send", "user_confirmed": False
    }).json()
    assert pending["status"] == "pending"

    approved = client.post("/agency/decisions/evaluate", json={
        "action": "email.send", "user_confirmed": True
    }).json()
    assert approved["authorized"] is True
    assert approved["execution"] == "not_connected"
