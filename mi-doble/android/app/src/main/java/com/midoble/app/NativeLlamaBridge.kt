package com.midoble.app

/** Minimal JNI bridge. This milestone verifies the native backend only; no model is loaded. */
internal object NativeLlamaBridge {
    @Volatile
    private var libraryLoaded = false

    @Synchronized
    fun backendInfo(): String {
        if (!libraryLoaded) {
            System.loadLibrary("midoble_native")
            libraryLoaded = true
        }
        return nativeGetBackendInfo()
    }

    private external fun nativeGetBackendInfo(): String
}
