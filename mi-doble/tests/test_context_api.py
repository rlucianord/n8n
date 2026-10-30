from __future__ import annotations

import os
import subprocess
import sys
from pathlib import Path


def test_context_endpoint_combines_history_and_relevant_memories(tmp_path: Path):
    env = os.environ.copy()
    env['MIDOBLE_DB_KEY'] = 'c' * 64
    env['MIDOBLE_DB_PATH'] = str(tmp_path / 'context-test.db')
    env['MIDOBLE_HOST'] = '127.0.0.1'

    code = r'''
from fastapi.testclient import TestClient
from app.main import app

with TestClient(app) as client:
    conversation = client.post('/conversations', json={'title': 'Context test'})
    assert conversation.status_code == 201
    conversation_id = conversation.json()['id']

    saved_message = client.post(
        f'/conversations/{conversation_id}/messages',
        json={'role': 'user', 'content': 'Antes hablamos de proyectos de software.'},
    )
    assert saved_message.status_code == 201

    relevant = client.post(
        '/memories',
        json={
            'category': 'work',
            'content': 'El proyecto DoctorPlagio utiliza Python y análisis documental.',
            'source': 'user',
        },
    )
    assert relevant.status_code == 201

    client.post(
        '/memories',
        json={
            'category': 'food',
            'content': 'La receta favorita es arroz con vegetales.',
            'source': 'user',
        },
    )

    response = client.post(
        f'/conversations/{conversation_id}/context',
        json={
            'current_message': 'Quiero continuar el proyecto de Python y software.',
            'message_limit': 10,
            'memory_limit': 5,
        },
    )
    assert response.status_code == 200, response.text
    payload = response.json()
    assert payload['persisted_current_message'] is False
    assert len(payload['messages']) >= 3
    assert payload['messages'][-1] == {
        'role': 'user',
        'content': 'Quiero continuar el proyecto de Python y software.',
    }
    assert any(item['category'] == 'work' for item in payload['memories'])
    assert all(item['category'] != 'food' for item in payload['memories'])
    assert 'No afirmes ser una persona humana' in payload['messages'][0]['content']

    history = client.get(f'/conversations/{conversation_id}/messages')
    assert history.status_code == 200
    assert len(history.json()) == 1
'''
    root = Path(__file__).resolve().parents[1]
    subprocess.run([sys.executable, '-c', code], cwd=root, env=env, check=True)


def test_context_endpoint_returns_404_for_unknown_conversation(tmp_path: Path):
    env = os.environ.copy()
    env['MIDOBLE_DB_KEY'] = 'd' * 64
    env['MIDOBLE_DB_PATH'] = str(tmp_path / 'context-missing.db')
    env['MIDOBLE_HOST'] = '127.0.0.1'
    code = r'''
from fastapi.testclient import TestClient
from app.main import app
with TestClient(app) as client:
    response = client.post(
        '/conversations/not-a-real-id/context',
        json={'current_message': 'test'},
    )
    assert response.status_code == 404
'''
    root = Path(__file__).resolve().parents[1]
    subprocess.run([sys.executable, '-c', code], cwd=root, env=env, check=True)
