from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path


def test_memory_api_crud_uses_encrypted_database(tmp_path: Path):
    env = os.environ.copy()
    env["MIDOBLE_DB_KEY"] = "b" * 64
    env["MIDOBLE_DB_PATH"] = str(tmp_path / "api-test.db")
    env["MIDOBLE_HOST"] = "127.0.0.1"

    code = """
from fastapi.testclient import TestClient
from app.main import app

with TestClient(app) as client:
    health = client.get("/health")
    assert health.status_code == 200
    assert health.json()["database"] == "encrypted"

    profile = client.get("/profile")
    assert profile.status_code == 200
    assert profile.json()["display_name"] == "Mi Doble"

    updated = client.put(
        "/profile",
        json={"display_name": "Mi Doble de prueba", "language": "es"},
    )
    assert updated.status_code == 200
    assert updated.json()["display_name"] == "Mi Doble de prueba"

    conversation = client.post(
        "/conversations",
        json={"title": "Conversación de prueba"},
    )
    assert conversation.status_code == 201
    conversation_id = conversation.json()["id"]

    message = client.post(
        f"/conversations/{conversation_id}/messages",
        json={"role": "user", "content": "Mensaje ficticio de prueba"},
    )
    assert message.status_code == 201

    messages = client.get(f"/conversations/{conversation_id}/messages")
    assert messages.status_code == 200
    assert len(messages.json()) == 1

    saved_memory = client.post(
        "/memories",
        json={
            "category": "test",
            "content": "Recuerdo ficticio para la prueba",
            "source": "user",
        },
    )
    assert saved_memory.status_code == 201
    memory_id = saved_memory.json()["id"]

    found = client.get("/memories", params={"q": "ficticio", "category": "test"})
    assert found.status_code == 200
    assert len(found.json()) == 1

    deleted = client.delete(f"/memories/{memory_id}")
    assert deleted.status_code == 204

    missing = client.get("/memories", params={"category": "test"})
    assert missing.status_code == 200
    assert missing.json() == []

    audit = client.get("/audit")
    assert audit.status_code == 200
    assert len(audit.json()) >= 4
"""

    root = Path(__file__).resolve().parents[1]
    subprocess.run(
        [sys.executable, "-c", code],
        cwd=root,
        env=env,
        check=True,
    )
