# Mi Doble — Android application

## Beta 0.2.0-beta2

The Android beta now installs its predefined model automatically from the official Qwen Hugging Face repository:

- Model: `Qwen/Qwen3-4B-GGUF`
- File: `Qwen3-4B-Q4_K_M.gguf`
- Revision pinned to: `34778e26c8fa5e8bc0daa2389a9f958cffb1aedd`
- License shown by the upstream model repository: Apache-2.0.
- Download size: approximately 2.5 GB.

The user no longer selects or imports a GGUF file. The first run presents one simple **Descargar e instalar modelo** action, shows download progress, validates the GGUF header and expected minimum size, saves the file in app-private storage, and loads it through the JNI llama.cpp runtime. Subsequent launches reuse and load the installed model automatically. A partial download is discarded and can be retried.

The app requires Internet access only for the initial model download. Inference remains on-device; prompts and conversations are not sent to Hugging Face or any inference service. After installation, the app can run inference offline.

## Device baseline

- Android 14 or newer (minSdk 34)
- At least 4 GB total RAM baseline; this does not guarantee Qwen3-4B will run acceptably.
- At least 3 GB free app data storage for download and installation.
- CPU-first llama.cpp native runtime, pinned to upstream tag `b12003`
- Supported APK ABIs: `arm64-v8a` and `x86_64`

## Beta test flow

1. Install the debug APK on an Android 14+ device.
2. Connect to Wi-Fi or another suitable Internet connection for the first model installation.
3. Open Mi Doble and tap **Descargar e instalar modelo**.
4. Wait for the download and automatic model load to finish.
5. Send a short test message. Subsequent app launches reuse the downloaded model.

The initial generation path uses a Qwen3-style prompt template, a 2048-token context, a maximum of 256 generated tokens, and greedy sampling. Model loading and generation run off the UI thread.

## Known beta limitations

- Download currently runs while the app remains open; Android background-download service and pause/resume are not yet implemented.
- No persistent chat history or encrypted SQLCipher storage yet.
- No streaming token display; the response appears after generation completes.
- No voice, external communications, n8n integration, or cloud inference.
- The native model load and inference still require physical-device validation on representative Android 14+ hardware.

## Build and tests

The GitHub Actions workflow builds on `ubuntu-latest`, runs Android JVM unit tests, compiles llama.cpp and the JNI library, assembles the debug APK, verifies it, and uploads a versioned artifact.

Never commit model weights, database files, user conversations, or encryption keys to this repository.

## Persistent beta installation and model retention

Android only permits an in-place update when the new APK is signed with the same signing certificate as the installed app. GitHub-hosted runners generate different debug keys unless a persistent signing key is supplied. Configure these repository Actions secrets:

- `MIDOBLE_KEYSTORE_BASE64`: Base64-encoded private JKS/PKCS12 keystore.
- `MIDOBLE_KEYSTORE_PASSWORD`
- `MIDOBLE_KEY_ALIAS`
- `MIDOBLE_KEY_PASSWORD`

Keep the keystore private. Never commit it or place it in a build artifact. Once configured, subsequent beta APKs use the same signing identity. The first APK signed with the persistent key cannot update an already-installed APK signed by a different temporary key: uninstall the old beta once, install the persistent-key build, and future updates will preserve app data and the downloaded model.

If secrets are absent, CI falls back to a temporary debug key and emits a warning; those builds are not suitable for in-place upgrades.