# Mi Doble — Android application

## Beta 0.2.0-beta1

The first functional beta provides:

- A native Android chat interface built with Kotlin and Jetpack Compose.
- Local GGUF file selection through Android's system document picker.
- Copying the selected model into the app-private `files/models` directory.
- Loading the model through the JNI bridge into the CPU llama.cpp runtime.
- Local text generation using the Qwen3 chat-message format and a greedy sampler.
- A basic device/runtime diagnostic panel.

The model weights are not bundled in the APK and are not downloaded by the application. The user must provide a compatible GGUF file. The app does not request Internet permission and does not transmit prompts or model data. The current beta keeps conversation history only in memory for the current app session; persistent encrypted conversation storage is not yet integrated.

## Device baseline

- Android 14 or newer (minSdk 34)
- At least 4 GB total RAM
- At least 64 GB total internal storage
- Kotlin and Jetpack Compose
- CPU-first llama.cpp native runtime, pinned to upstream tag `b12003`
- Supported APK ABIs: `arm64-v8a` and `x86_64`

The diagnostic screen reports Android version, total and available RAM, ABIs, and data-partition storage. Android's data-partition size is not the phone's advertised storage capacity.

## Beta test flow

1. Install the debug APK on an Android 14+ device.
2. Obtain a compatible GGUF model file separately. Qwen3-4B Q4_K_M remains the target model, but this beta has not yet validated a specific model build on every device.
3. Open Mi Doble and tap **Seleccionar GGUF**.
4. Select the local `.gguf` file. The app copies it into private app storage.
5. Tap **Cargar modelo** and wait for the native load operation to finish.
6. Send a short test message in the conversation screen.

The initial generation path uses a Qwen3-style prompt template, a 2048-token context, a maximum of 256 generated tokens, and greedy sampling. Long prompts are rejected by the beta guard. Model loading and generation run off the UI thread.

## Known beta limitations

- No model download or model catalogue.
- No persistent chat history or encrypted SQLCipher storage yet.
- No streaming token display; the response appears after generation completes.
- No multi-model management or model unload control yet.
- No voice, external communications, n8n integration, or cloud services.
- The beta has not yet been validated with a real Qwen3-4B GGUF on the target HONOR device. Native library initialization was verified separately; model load and inference must be tested on device.

## Build and tests

The GitHub Actions workflow builds on `ubuntu-latest`, runs Android JVM unit tests, compiles llama.cpp and the JNI library, assembles the debug APK, verifies it, and uploads the `mi-doble-debug-apk` artifact.

Run locally from this directory with the configured Android SDK/NDK:

```sh
gradle --no-daemon :app:testDebugUnitTest
gradle --no-daemon :app:assembleDebug
```

The Gradle wrapper scripts are not committed in this source-only bootstrap. Codespaces based on Alpine Linux may fail because the Android SDK CMake binary requires a glibc-compatible host; use the Ubuntu GitHub Actions workflow or an Ubuntu-based development environment.

Never commit model weights, database files, user conversations, or encryption keys to this repository.
