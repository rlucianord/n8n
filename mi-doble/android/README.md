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

The policy is covered by eight JVM unit tests in `app/src/test`. Run them with `gradle --no-daemon :app:testDebugUnitTest`.

## Build

Open this directory in Android Studio, allow Gradle sync, then run the app on an Android 14+ device or emulator. The project targets Android API 36 and Java 17. The build uses Android Gradle Plugin 9.5.0, Gradle 9.6.0, Kotlin 2.4.21, and the stable Compose BOM 2026.09.00.

The Gradle wrapper scripts are not committed in this first source-only bootstrap. A GitHub Actions workflow builds the app using a pinned Gradle version. Android Studio can also generate a wrapper for local development.

## Next implementation steps

1. Add automated tests for the device capability policy.
2. Add runtime model-fit checks separate from the installation baseline.
3. Integrate llama.cpp as an Android native library through JNI.
4. Add a controlled local GGUF model import/install flow.
5. Implement encrypted on-device storage and verify SQLCipher configuration.
6. Test offline inference on physical HONOR and Pixel devices.

Never commit model weights, database files, user conversations, or encryption keys to this repository.
