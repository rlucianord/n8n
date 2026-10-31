package com.midoble.app

import android.content.Context

/**
 * Single inference entry point for Mi Doble.
 * The active provider is selected from persisted user preferences.
 */
internal object ModelInferenceRouter {
    fun generate(context: Context, prompt: String, systemInstruction: String): String {
        val activeModel = context.getSharedPreferences("mi_doble_profile", Context.MODE_PRIVATE)
            .getString("active_model", "Qwen3") ?: "Qwen3"

        return when (activeModel) {
            "Gemini Nano" -> NativeDeviceAiProvider.generate(prompt, systemInstruction)
            else -> NativeLlamaBridge.generate(
                "$systemInstruction\n\nUsuario: $prompt\nMi Doble:",
                512
            )
        }
    }
}
