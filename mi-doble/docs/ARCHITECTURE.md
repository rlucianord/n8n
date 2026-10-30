# Mi Doble architecture

## Components

- **Android app (production target):** native Kotlin application, Compose diagnostics UI, permissions, lifecycle, and device storage. Baseline compatibility and model-specific runtime eligibility are separate pure Kotlin policies with unit tests.
- **On-device inference:** llama.cpp is being built as an Android native library through JNI. The current milestone only initializes the CPU backend and reports system information; GGUF loading, chat formatting, and token generation are still pending. The intended model candidate remains local Qwen3-4B GGUF. No Ollama, hosted LLM, or automatic cloud fallback.
- **Mi Doble Python API (development/test harness):** FastAPI endpoints and services used in Codespaces with fictitious data. It is not required to run inference in production Android.
- **Model provider contract:** Python LanguageModelProvider protocol for development; Android implements the same role/content-in, text-out contract around native llama.cpp.
- **Local storage:** SQLCipher-backed SQLite on Android for profile, conversations, memories, settings, and audit records. Python SQLCipher is used for development tests; cross-platform file compatibility must be verified explicitly.
- **n8n:** optional event orchestration for separately authorized external workflows; it is not the personal memory store or production inference host.
- **Speech and avatar (planned):** on-device speech-to-text/text-to-speech and visual avatar, subject to mobile performance testing.

## Data flow

User input -> Android app -> local policy/context assembly -> local llama.cpp inference -> local encrypted memory -> response.

External communication is a separate, explicitly authorized path and must not send conversation context to hosted AI services.

## Modes

- **PRIVATE MODE:** local inference and local storage only; no external communication initiated by Mi Doble.
- **COMMUNICATION MODE:** external communication is permitted only for specifically authorized channels and actions. It does not authorize hosted AI inference or sharing private memory.

## Model candidate

- Initial candidate: Qwen3-4B GGUF, Q4_K_M.
- This quantization is a benchmark starting point, not a promise of compatibility with every Android device.
- Final context length, quantization, and model variant depend on phone RAM, chipset, Android version, storage, thermal behavior, and measured tokens/second.
- Codespace tests use fictitious prompts only. Never store personal data or model weights in the public repository.

## Deployment boundary

GitHub/Codespaces are development infrastructure, not private production storage. Production inference, personal data, and model files remain on the Android device. The app must work offline after model installation. Any optional network permission for communications or intentional model installation must remain separate from the inference component.
