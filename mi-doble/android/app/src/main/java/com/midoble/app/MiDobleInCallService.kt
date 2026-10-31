package com.midoble.app

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile

/**
 * Integración nativa con Telecom para la aplicación telefónica predeterminada.
 * Gestiona el ciclo de vida de la llamada; no proporciona audio PCM de llamadas SIM.
 */
class MiDobleInCallService : InCallService() {
    @Volatile private var speakerRouteRequested = false

    override fun onCreate() {
        super.onCreate()
        currentService = this
    }

    override fun onDestroy() {
        if (currentService === this) currentService = null
        super.onDestroy()
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        if (speakerRouteRequested && (audioState.supportedRouteMask and CallAudioState.ROUTE_SPEAKER) != 0) {
            speakerRouteRequested = false
            setAudioRoute(CallAudioState.ROUTE_SPEAKER)
        }
    }
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCall = call
        val number = call.details.handle?.schemeSpecificPart.orEmpty()
        // Android restricts direct activity launches from background services.
        // A high-priority full-screen call notification is the supported wake-up path.
        IncomingCallNotifier.show(this, number)
    }

    override fun onCallRemoved(call: Call) {
        if (currentCall === call) {
            currentCall = null
            IncomingCallNotifier.dismiss(this)
        }
        super.onCallRemoved(call)
    }

    companion object {
        @Volatile private var currentCall: Call? = null
        @Volatile private var currentService: MiDobleInCallService? = null

        fun answerCurrentCall(): Boolean {
            val call = currentCall ?: return false
            return try {
                call.answer(VideoProfile.STATE_AUDIO_ONLY)
                true
            } catch (_: Exception) { false }
        }

        fun rejectCurrentCall(): Boolean {
            val call = currentCall ?: return false
            return try {
                call.reject(false, null)
                true
            } catch (_: Exception) { false }
        }

        fun disconnectCurrentCall(): Boolean {
            val call = currentCall ?: return false
            return try {
                call.disconnect()
                true
            } catch (_: Exception) { false }
        }

        fun hasActiveCall(): Boolean = currentCall?.state == Call.STATE_ACTIVE

        /** Request the Telecom speaker route for the local TTS acoustic fallback. */
        @Suppress("DEPRECATION")
        fun routeToSpeaker(): Boolean {
            val service = currentService ?: return false
            return try {
                service.speakerRouteRequested = true
                val audioState = service.callAudioState
                if (audioState != null && (audioState.supportedRouteMask and CallAudioState.ROUTE_SPEAKER) == 0) {
                    service.speakerRouteRequested = false
                    return false
                }
                if (audioState != null) {
                    service.speakerRouteRequested = false
                    service.setAudioRoute(CallAudioState.ROUTE_SPEAKER)
                }
                true
            } catch (_: Exception) { false }
        }
    }
}
