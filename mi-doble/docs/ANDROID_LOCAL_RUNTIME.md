# Mi Doble Android local runtime

## Target decision

The production target is an Android phone. The development Codespace is not a production inference host and must never contain personal conversations, personal documents, credentials, or the production database.

The initial model candidate is Qwen3-4B in GGUF format, Q4_K_M quantization. This is a benchmark candidate, not a final compatibility guarantee. The exact model release, license, tokenizer/chat template, memory use, speed, and quality must be checked before packaging. Do not commit model weights to Git.

## Android architecture

- Native Android application, Kotlin UI and lifecycle.
- llama.cpp built as an Android native library and called through JNI for on-device GGUF inference.
- Qwen3-4B GGUF model stored in app-private storage; never in a public repository or shared folder by default.
- SQLCipher-backed local database for profile, conversations, memories, settings, and audit records.
- Context assembly and policy checks run locally on the phone.
- No hosted LLM, no Ollama, no automatic cloud fallback, and no upload of prompts or memory.
- Model loading, generation, and database access must work in airplane mode after the model has been installed.
- Network permission, if needed for explicitly authorized communications or an intentional model download, must not be used by the inference component.

## Model and device constraints

Q4_K_M is the first quantization to benchmark because it substantially reduces memory compared with full precision. The model file itself will still occupy multiple gigabytes, and runtime memory also includes the context/KV cache, tokenizer, application, and Android OS.

The agreed minimum installation baseline is Android 14 (API 34)+, 4 GB total RAM, and 64 GB total internal storage. The app is intended to support HONOR and Google Pixel devices without restricting compatibility to particular phone models; the actual Android version and hardware capabilities determine eligibility, not the brand name. These minimums do not guarantee that Qwen3-4B will fit or perform acceptably. Runtime model eligibility must additionally inspect currently available RAM, chipset/ABI, free storage, thermal limits, and the selected context/KV-cache size. On constrained devices, use a smaller model or a more memory-efficient quantization rather than attempting an unsafe allocation.

## Current Android implementation status

The native Android project is under `android/`. It currently includes a Compose device-diagnostics screen, a tested baseline compatibility policy, and a model-specific runtime eligibility policy. The runtime policy evaluates Android API, currently available RAM, free storage, supported ABI, and Android's low-RAM-device classification against requirements supplied by a model manifest. Those model limits are deliberately not guessed before real device benchmarks.

A CPU-first llama.cpp JNI smoke check is configured with Android NDK and CMake. The upstream source is pinned to tag `b12003` for reproducibility. The JNI method initializes the backend and reads system information only; it does not load a GGUF file or generate tokens. The Android CI workflow runs JVM tests and attempts a native debug APK build.

## Cross-platform contract

The Python LanguageModelProvider interface is the development contract: role/content messages in, generated text out. The Android implementation must preserve that contract and the same safety/policy boundary. The Python llama-cpp-python adapter is for Codespace experiments only; it is not shipped inside Android.

The SQLCipher database format and key derivation settings must be explicitly matched and tested between Python and Android before any database is moved between runtimes. Do not assume database-file compatibility solely because both sides use SQLCipher.

## Delivery sequence

1. Complete the Android JVM tests and native CI build; fix any toolchain or JNI integration errors.
2. Add a controlled local GGUF model import/install flow.
3. Implement model loading, the model's chat template, generation, cancellation, and token streaming through JNI.
4. Install on representative physical HONOR and Pixel devices and measure load time, tokens/second, peak RAM, context size, storage use, and thermal behavior.
5. Populate model-specific runtime requirements from those measurements; do not infer Qwen3-4B compatibility from the 4 GB installation minimum.
6. Implement Android local storage and verify SQLCipher interoperability separately.
7. Add offline-mode tests proving that inference and memory retrieval make no network requests.
8. Add optional communications only behind explicit user permissions and confirmation.

No model file, personal data, or database key belongs in this repository or Codespace.
