package com.midoble.app

/** JNI access to the on-device llama.cpp runtime. Calls must run off the UI thread. */
internal object NativeLlamaBridge {
    @Volatile
    private var libraryLoaded = false

    @Synchronized
    private fun ensureLibraryLoaded() {
        if (!libraryLoaded) {
            System.loadLibrary("midoble_native")
            libraryLoaded = true
        }
    }

    fun backendInfo(): String {
        ensureLibraryLoaded()
        return nativeGetBackendInfo()
    }

    fun loadModel(path: String): String {
        ensureLibraryLoaded()
        return nativeLoadModel(path)
    }

    fun generate(prompt: String, maxTokens: Int = 256): String {
        ensureLibraryLoaded()
        return nativeGenerate(prompt, maxTokens)
    }

    private external fun nativeGetBackendInfo(): String
    private external fun nativeLoadModel(path: String): String
    private external fun nativeGenerate(prompt: String, maxTokens: Int): String
}
