package com.midoble.app

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.VideoProfile

/**
 * Integración nativa con Telecom para la aplicación telefónica predeterminada.
 * Gestiona el ciclo de vida de la llamada; no proporciona audio PCM de llamadas SIM.
 */
class MiDobleInCallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCall = call
        val number = call.details.handle?.schemeSpecificPart.orEmpty()
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            launch.putExtra("incoming_call_number", number)
            launch.putExtra("managed_by_incall_service", true)
            startActivity(launch)
        }
    }

    override fun onCallRemoved(call: Call) {
        if (currentCall === call) currentCall = null
        super.onCallRemoved(call)
    }

    companion object {
        @Volatile private var currentCall: Call? = null

        fun answerCurrentCall(): Boolean = try {
            val call = currentCall ?: return false
            call.answer(VideoProfile.STATE_AUDIO_ONLY)
            true
        } catch (_: Exception) { false }

        fun rejectCurrentCall(): Boolean = try {
            val call = currentCall ?: return false
            call.reject(false, null)
            true
        } catch (_: Exception) { false }

        fun disconnectCurrentCall(): Boolean = try {
            val call = currentCall ?: return false
            call.disconnect()
            true
        } catch (_: Exception) { false }

        fun hasActiveCall(): Boolean = currentCall != null
    }
}
