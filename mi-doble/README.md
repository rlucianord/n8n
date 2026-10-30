# Mi Doble — local-first companion service

Mi Doble is an independent local service intended to work alongside n8n. It is isolated under `mi-doble/` and does not modify n8n packages, its runtime, or its database.

## Design principles

- Local-first: personal conversations, audio, documents, model files, and the encrypted database stay on the user's own device in production.
- No hosted AI APIs and no Ollama. Production inference is designed for Android using llama.cpp locally with a Qwen3-4B GGUF model. Codespaces are only for development and fictitious-data tests.
- SQLCipher encrypts the database. The service fails closed if SQLCipher or the database key is unavailable; it never silently falls back to ordinary SQLite.
- n8n is an orchestration layer, not the personal memory store or AI model host.
- External communications (Zoom, phone networks, messaging) necessarily use their respective services. Mi Doble must identify itself as an AI assistant and require explicit policies/permissions before acting.
- The API binds to loopback only in this milestone.

## Current milestone

The bootstrap now includes:

- SQLCipher database initialization and health reporting.
- Persistent profile, conversation, message, memory, and audit operations.
- FastAPI endpoints for those operations.
- API integration tests using a temporary encrypted database.

The model provider contract and an offline llama.cpp GGUF adapter are present for development. The initial model candidate is Qwen3-4B GGUF Q4_K_M; weights have not been downloaded or committed. The Android production runtime will use llama.cpp through native JNI, with model inference and personal storage on the phone. See `docs/ANDROID_LOCAL_RUNTIME.md`. The older Transformers adapter is experimental and is not the Android production runtime. Speech, avatar, Android UI, telephony, Zoom integration, policy-management endpoints, and authentication for non-loopback deployments are not enabled yet.

## API

Run the service on loopback and open `/docs` for the interactive API reference.

| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/health` | Service and SQLCipher status |
| GET / PUT | `/profile` | Read or update the profile |
| GET / POST | `/conversations` | List or create conversations |
| GET | `/conversations/{id}` | Read a conversation |
| GET / POST | `/conversations/{id}/messages` | Read or append messages |
| GET / POST | `/memories` | Search or save memories |
| DELETE | `/memories/{id}` | Delete a memory |
| GET | `/audit` | Read the audit log |
| POST | `/conversations/{id}/context` | Assemble recent history and relevant memories for a model |

Memory is not automatically inferred from messages. A memory is saved only when an explicit API request creates it. The context endpoint ranks existing memories by lexical overlap with the current message, includes recent conversation history, and returns a model-ready message list. It does not persist the current message. The context includes instructions to disclose AI identity and require explicit authorization for external actions.

## Requirements

- Python 3.12+
- SQLCipher library and the `sqlcipher3` Python binding
- No personal data or secrets in GitHub/Codespaces

### Alpine Linux

Install the system packages with Alpine's package manager:

```sh
sudo apk update
sudo apk add python3 py3-pip py3-virtualenv sqlcipher-dev gcc musl-dev python3-dev
```

### Debian / Ubuntu

```sh
sudo apt-get update
sudo apt-get install -y libsqlcipher-dev build-essential pkg-config python3-venv
```

## Run locally

```sh
cd mi-doble
python3 -m venv .venv
. .venv/bin/activate
python -m pip install --upgrade pip
pip install -r requirements.txt
cp .env.example .env
```

Generate a strong key locally and put it in the private `.env` file (never commit it):

```sh
python -c "import secrets; print(secrets.token_hex(32))"
```

Set the generated value as `MIDOBLE_DB_KEY` in `.env`, then start the service:

```sh
uvicorn app.main:app --host 127.0.0.1 --port 8765
```

Open `http://127.0.0.1:8765/docs` or `http://127.0.0.1:8765/health`.

### Hardware diagnostic

Run `python scripts/diagnose_hardware.py` in the Codespace to record development limits, and later on the Android target use a dedicated Android diagnostic screen to capture device RAM, chipset, Android version, and free storage. Codespace hardware does not determine Android compatibility.

### Optional GGUF model testing

`requirements-local-llm.txt` installs the optional `llama-cpp-python` adapter for Codespace experiments. On Alpine/musl or Python versions without a compatible wheel, installation may compile llama.cpp from source. Do not install this dependency as part of the basic API test setup. The adapter loads only a local GGUF file and does not download models. The production Android app will use llama.cpp through JNI, not this Python package. See `docs/ANDROID_LOCAL_RUNTIME.md` for the mobile design.

## Tests

```sh
pip install -r requirements-dev.txt
pytest -q
```

Tests use temporary encrypted databases and fictitious records. A passing import test alone is not proof of encryption.

## Security boundary

The repository is public; assume every committed byte is public. Never commit `.env`, database files, model weights, audio, recordings, personal documents, or real conversation exports.

The API currently has no application-level authentication and is intended for loopback-only use. Do not expose it through a public Codespaces port, LAN, reverse proxy, or internet endpoint. Add authentication, authorization, rate limits, and a threat-model review before any non-loopback deployment. Use fictitious data in Codespaces; production personal data belongs on the user's own device.

Keep a protected backup of the SQLCipher key. Losing the key means losing access to the encrypted database.

No action involving phone calls, meetings, messages, purchases, or disclosure of personal information should run without a clear user policy and required confirmation. The assistant must disclose that it is an AI assistant when communicating with other people.

## n8n integration

See [n8n integration notes](./docs/N8N_INTEGRATION.md). n8n should orchestrate approved workflows; it must not receive the database key or become the personal memory store.
