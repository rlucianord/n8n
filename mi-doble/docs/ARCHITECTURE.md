# Mi Doble architecture

## Components

- **n8n:** event orchestration and integrations.
- **Mi Doble API:** local policy boundary and application service.
- **Local model runtime (planned):** Transformers/PyTorch on PC; Android runtime to be benchmarked separately.
- **SQLCipher/SQLite:** encrypted local profile, conversation, memory, policy, and audit data.
- **Speech and avatar (planned):** local speech-to-text/text-to-speech and a visual avatar; not part of this bootstrap.

## Data flow

External event -> n8n trigger -> explicit policy/approval -> local Mi Doble API -> local model and encrypted memory -> minimal response.

## Modes

- **PRIVATE MODE:** no external communications initiated by Mi Doble; local processing only.
- **COMMUNICATION MODE:** external communication is allowed only for specifically authorized channels and actions. This does not authorize sending personal data to hosted AI services.

## Deployment boundary

GitHub and Codespaces are development infrastructure, not private production storage. Production data and model files must remain on the user's own PC/phone. The service currently refuses non-loopback binding until authentication and deployment security are implemented.
