package com.midoble.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Best-effort, on-device audio bridge for an ordinary cellular call.
 *
 * Android does not expose a SIM call's PCM uplink/downlink to third-party apps.
 * This class tests the non-privileged fallback: Telecom speakerphone output and
 * local TTS, while on-device speech recognition attempts to hear the caller
 * acoustically through the phone microphone. Android may still give the
 * recognizer silence while Telecom owns the microphone.
 */
class CallAudioAssistant(
    context: Context,
    private val onTranscript: (String) -> Unit,
    private val onStatus: (String) -> Unit
) {
    companion object {
        fun isOfflineRecognitionAvailable(context: Context): Boolean =
            SpeechRecognizer.isRecognitionAvailable(context) &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var prepared = false
    private var started = false
    private var stopped = false
    private var retryCount = 0

    fun prepare(onPrepared: (Boolean, String) -> Unit) {
        if (appContext.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onPrepared(false, "Falta el permiso de micrófono.")
            return
        }
        if (!isOfflineRecognitionAvailable(appContext)) {
            onPrepared(false, "No hay reconocimiento de voz sin conexión disponible en este teléfono.")
            return
        }

        try {
            speechRecognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit

                override fun onResults(results: Bundle?) {
                    retryCount = 0
                    val transcript = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                    if (transcript.isNullOrBlank()) {
                        listenAgain()
                    } else {
                        onTranscript(transcript)
                    }
                }

                override fun onError(error: Int) {
                    if (stopped || !started) return
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        onStatus("Android bloqueó el micrófono durante la llamada. Puedes continuar la conversación manualmente.")
                        return
                    }
                    retryCount += 1
                    if (retryCount <= 2) {
                        mainHandler.postDelayed({ listenAgain() }, 700L)
                    } else {
                        onStatus("No pude captar la voz del interlocutor. Android puede reservar el micrófono para la llamada SIM; puedes tomarla manualmente.")
                    }
                }
            })

            textToSpeech = TextToSpeech(appContext) { status ->
                mainHandler.post {
                    val engine = textToSpeech
                    if (stopped) return@post
                    if (status != TextToSpeech.SUCCESS || engine == null) {
                        onPrepared(false, "No se pudo iniciar la síntesis de voz de Android.")
                        return@post
                    }

                    try {
                        engine.setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .build()
                        )
                        val languageStatus = engine.setLanguage(Locale("es"))
                        if (languageStatus == TextToSpeech.LANG_MISSING_DATA ||
                            languageStatus == TextToSpeech.LANG_NOT_SUPPORTED
                        ) {
                            onPrepared(false, "El motor de voz no tiene español instalado.")
                            return@post
                        }

                        val offlineVoice = engine.voices
                            ?.filter { it.locale.language == "es" && !it.isNetworkConnectionRequired }
                            ?.maxByOrNull { it.quality }
                        if (offlineVoice == null) {
                            onPrepared(false, "No hay una voz española local instalada. Instala una voz sin conexión en Android y vuelve a intentarlo.")
                            return@post
                        }
                        engine.voice = offlineVoice
                        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) = Unit
                            override fun onDone(utteranceId: String?) {
                                mainHandler.post { listenAgain() }
                            }
                            override fun onError(utteranceId: String?) {
                                mainHandler.post {
                                    onStatus("La voz del teléfono no pudo reproducirse por el canal de llamada.")
                                }
                            }
                        })
                        prepared = true
                        onPrepared(true, "Audio local preparado.")
                    } catch (error: Exception) {
                        onPrepared(false, "No se pudo preparar el audio local: " + (error.message ?: error.javaClass.simpleName))
                    }
                }
            }
        } catch (error: Exception) {
            onPrepared(false, "No se pudo iniciar el reconocimiento de voz: " + (error.message ?: error.javaClass.simpleName))
        }
    }

    fun startConversation() {
        if (!prepared || stopped) return
        started = true
        speak("Hola. Soy Mi Doble, el asistente virtual autorizado para atender esta llamada. ¿En qué puedo ayudarle?")
    }

    fun speak(text: String) {
        if (!prepared || !started || stopped) return
        try {
            speechRecognizer?.cancel()
            val result = textToSpeech?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                Bundle(),
                "midoble-call-" + System.nanoTime()
            )
            if (result == TextToSpeech.ERROR) {
                onStatus("Android no pudo reproducir la respuesta por el altavoz.")
            }
        } catch (error: Exception) {
            onStatus("Error al reproducir la respuesta: " + (error.message ?: error.javaClass.simpleName))
        }
    }

    private fun listenAgain() {
        if (!prepared || !started || stopped) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            speechRecognizer?.startListening(intent)
        } catch (error: Exception) {
            onStatus("No pude abrir el micrófono para escuchar la llamada: " + (error.message ?: error.javaClass.simpleName))
        }
    }

    fun stop() {
        stopped = true
        started = false
        try { speechRecognizer?.cancel() } catch (_: Exception) { }
        try { speechRecognizer?.destroy() } catch (_: Exception) { }
        speechRecognizer = null
        try { textToSpeech?.stop() } catch (_: Exception) { }
        try { textToSpeech?.shutdown() } catch (_: Exception) { }
        textToSpeech = null
    }
}
