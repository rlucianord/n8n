# Mi Doble — Android application

This is the native Android application module. It is separate from the Python FastAPI development/test harness in the parent directory.

## Device baseline

- Android 14 or newer (minSdk 34)
- At least 4 GB total RAM
- At least 64 GB total internal storage
- HONOR and Google Pixel devices are supported based on OS and hardware requirements, not a fixed list of phone models
- Kotlin and Jetpack Compose
- No network permission and no model download in this initial milestone

The diagnostic screen reads Android version, total and available RAM, and total and available internal data-partition storage. A separate pure Kotlin policy evaluates Android API level, total RAM, and the declared storage capacity. Because Android's data-partition size is not the phone's advertised capacity, the 64 GB requirement remains pending until verified from the device specifications. Passing the installation baseline does not guarantee that a specific local language model will fit in memory.

The installation policy and model-runtime policy are covered by 15 JVM unit tests in `app/src/test`. Run them with `gradle --no-daemon :app:testDebugUnitTest`.

A CPU-first llama.cpp native library is wired through Android NDK, CMake, and JNI. The upstream source is pinned to the `b12003` tag. The app checks native backend initialization and reports system information; this does not load a GGUF model or perform inference. Model-specific memory and storage limits must be filled from measured model/device benchmarks, not estimated.

## Build

Open this directory in Android Studio, allow Gradle sync, then run the app on an Android 14+ device or emulator. The project targets Android API 36 and Java 17. The build uses Android Gradle Plugin 9.5.0, Gradle 9.6.0, Kotlin 2.4.21, and Compose BOM 2026.06.01 (Compose 1.11 line, compatible with compileSdk 36).

The Gradle wrapper scripts are not committed in this source-only bootstrap. The GitHub Actions workflow builds on `ubuntu-latest`, runs JVM unit tests, compiles the native llama.cpp/JNI library, assembles the debug APK, and uploads it as the `mi-doble-debug-apk` artifact.

### Codespaces on Alpine Linux

The Android SDK's Linux CMake binary requires a glibc-compatible environment. Alpine uses musl, so native configuration can fail with an error such as `Error relocating .../cmake/.../bin/cmake: fcntl64: symbol not found`. This is a host-environment incompatibility, not an application C++ source error. Do not remove the native build or bypass JNI to work around it. Run the complete APK build using the Ubuntu GitHub Actions workflow or an Ubuntu-based development environment.

## Next implementation steps

1. Run Android JVM tests and CI native build; resolve any toolchain errors.
2. Add a controlled local GGUF model import/install flow.
3. Define model-specific runtime limits from real measurements on physical devices.
4. Implement GGUF loading, chat-template handling, and token streaming through JNI.
5. Implement encrypted on-device storage and verify SQLCipher configuration.
6. Test offline inference on physical HONOR and Pixel devices.

Never commit model weights, database files, user conversations, or encryption keys to this repository.
