package com.midoble.app

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.SystemInstruction
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking

/**
 * Optional access to the device-managed Gemini Nano runtime through Android AICore.
 * This never reads private model files and never replaces the bundled GGUF model.
 */
internal object NativeDeviceAiProvider {
    enum class Availability { AVAILABLE, DOWNLOADABLE, DOWNLOADING, UNAVAILABLE, ERROR }

    data class CheckResult(val availability: Availability, val detail: String)

    private val model by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        Generation.getClient()
    }

    fun checkAvailability(): CheckResult = try {
        when (runBlocking { model.checkStatus() }) {
            FeatureStatus.AVAILABLE -> CheckResult(
                Availability.AVAILABLE,
                "Gemini Nano está disponible mediante Android AICore."
            )
            FeatureStatus.DOWNLOADABLE -> CheckResult(
                Availability.DOWNLOADABLE,
                "El dispositivo permite descargar Gemini Nano."
            )
            FeatureStatus.DOWNLOADING -> CheckResult(
                Availability.DOWNLOADING,
                "Android está descargando Gemini Nano."
            )
            else -> CheckResult(
                Availability.UNAVAILABLE,
                "Android AICore no ofrece Gemini Nano para esta aplicación o dispositivo."
            )
        }
    } catch (error: Throwable) {
        CheckResult(
            Availability.ERROR,
            "No se pudo consultar Android AICore: ${error.message ?: error.javaClass.simpleName}"
        )
    }

    fun download(onProgress: (String) -> Unit): CheckResult = try {
        runBlocking {
            model.download().collect { status ->
                when (status) {
                    is DownloadStatus.DownloadStarted ->
                        onProgress("Android inició la descarga de Gemini Nano.")
                    is DownloadStatus.DownloadProgress ->
                        onProgress("Descargando el modelo nativo: ${status.totalBytesDownloaded} bytes recibidos.")
                    DownloadStatus.DownloadCompleted ->
                        onProgress("Descarga de Gemini Nano completada.")
                    is DownloadStatus.DownloadFailed ->
                        throw status.e
                }
            }
        }
        checkAvailability()
    } catch (error: Throwable) {
        CheckResult(
            Availability.ERROR,
            "No se pudo descargar Gemini Nano: ${error.message ?: error.javaClass.simpleName}"
        )
    }

    fun generate(prompt: String, systemInstruction: String): String = runBlocking {
        val status = model.checkStatus()
        check(status == FeatureStatus.AVAILABLE) {
            "Gemini Nano no está disponible. Estado de AICore: $status"
        }
        val request = generateContentRequest(
            SystemInstruction(systemInstruction),
            TextPart(prompt)
        ) {
            temperature = 0.4f
            candidateCount = 1
            maxOutputTokens = 512
        }
        model.generateContent(request).text.orEmpty().ifBlank {
            "El motor nativo no devolvió texto."
        }
    }
}
