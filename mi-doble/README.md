# Mi Doble — local-first companion service

Mi Doble is an independent local service intended to work alongside n8n. It is deliberately isolated under `mi-doble/`; it does not modify n8n packages, its runtime, or its database.

## Design principles

- Local-first: personal conversations, audio, documents, model files, and the encrypted database stay on the user's own device in production.
- No hosted AI APIs and no Ollama. The planned model runtime is Python Transformers/PyTorch on the PC; Android will use a separately evaluated mobile runtime.
- SQLite encrypted with SQLCipher. The service fails closed if SQLCipher or the database key is unavailable; it never silently falls back to ordinary SQLite.
- n8n is an orchestration layer, not the personal memory store or the AI model host.
- External communications (Zoom, phone networks, messaging) necessarily use their respective services. Mi Doble must identify itself as an AI assistant and require explicit policies/permissions before acting.
- Default network binding is loopback only.

## Current milestone

This commit is a **bootstrap foundation**, not a finished digital twin. It includes a small FastAPI service, encrypted database initialization, health endpoint, environment template, tests, and integration notes. Model loading, speech, avatar, Android app, telephony, and Zoom integration are intentionally not enabled yet.

## Requirements

- Python 3.12+
- SQLCipher development/runtime library and Python binding
- No personal data or secrets in GitHub/Codespaces

## Run locally (Linux)

```bash
cd mi-doble
sudo apt-get update
sudo apt-get install -y libsqlcipher-dev
python3.12 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
```

Generate a strong key locally and put it in your private `.env` file (never commit it):

```bash
python -c "import secrets; print(secrets.token_hex(32))"
```

Set the generated value as `MIDOBLE_DB_KEY` in `.env`, then:

```bash
uvicorn app.main:app --host 127.0.0.1 --port 8765
```

Open `http://127.0.0.1:8765/health`. The health endpoint reports service and SQLCipher status, never secrets.

## Tests

```bash
pip install -r requirements-dev.txt
pytest -q
```

Tests use a temporary encrypted database and require the SQLCipher binding. A passing import test alone is not proof of encryption.

## n8n integration boundary

See [n8n integration notes](./docs/N8N_INTEGRATION.md). In production, n8n should call this service only on a trusted local network or localhost and should not receive the database key. Do not expose this API to the public internet. Add authentication and explicit action authorization before enabling any non-loopback deployment.

## Repository layout

- `app/`: local API and SQLCipher access
- `database/`: schema
- `docs/`: architecture, privacy, and n8n integration
- `tests/`: basic service and encryption tests
- `n8n/`: integration guidance and workflow design notes

## Security

See [SECURITY.md](./SECURITY.md). The parent n8n fork is public, so only generic source code belongs here. Keep personal information, API credentials, model weights, audio, recordings, and database files outside GitHub.
