plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val stableKeystorePath = System.getenv("MIDOBLE_KEYSTORE_PATH")
val stableKeystorePassword = System.getenv("MIDOBLE_KEYSTORE_PASSWORD")
val stableKeyAlias = System.getenv("MIDOBLE_KEY_ALIAS")
val stableKeyPassword = System.getenv("MIDOBLE_KEY_PASSWORD")

android {
    namespace = "com.midoble.app"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.midoble.app"
        minSdk = 34
        targetSdk = 36
        versionCode = 10
        versionName = "0.3.0-beta7"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17")
                arguments += listOf(
                    "-DGGML_NATIVE=OFF",
                    "-DGGML_OPENMP=OFF",
                    "-DGGML_LLAMAFILE=OFF",
                    "-DLLAMA_OPENSSL=OFF",
                    "-DLLAMA_BUILD_TESTS=OFF",
                    "-DLLAMA_BUILD_EXAMPLES=OFF",
                    "-DLLAMA_BUILD_SERVER=OFF",
                    "-DLLAMA_BUILD_COMMON=OFF",
                    "-DBUILD_SHARED_LIBS=OFF"
                )
            }
        }
    }

    signingConfigs {
        create("stableBeta") {
            if (!stableKeystorePath.isNullOrBlank()) {
                storeFile = file(stableKeystorePath)
                storePassword = stableKeystorePassword
                keyAlias = stableKeyAlias
                keyPassword = stableKeyPassword
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (!stableKeystorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("stableBeta")
            }
        }
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    // Compose 1.11 line remains compatible with compileSdk 36.
    // Compose 1.12+ requires compileSdk 37.
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
