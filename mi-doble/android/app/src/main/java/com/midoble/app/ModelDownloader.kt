package com.midoble.app

import android.content.Context
import android.os.StatFs
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads the pinned official Qwen3-4B Q4_K_M GGUF into app-private storage.
 * The model is never placed in shared storage or the APK.
 */
internal object ModelDownloader {
    private const val MODEL_REPOSITORY = "Qwen/Qwen3-4B-GGUF"
    private const val MODEL_REVISION = "34778e26c8fa5e8bc0daa2389a9f958cffb1aedd"
    private const val MODEL_FILE = "Qwen3-4B-Q4_K_M.gguf"
    private const val MIN_MODEL_BYTES = 2_000_000_000L
    private const val REQUIRED_FREE_BYTES = 3_000_000_000L
    private const val BUFFER_SIZE = 1024 * 1024

    private val downloadUrl =
        "https://huggingface.co/$MODEL_REPOSITORY/resolve/$MODEL_REVISION/$MODEL_FILE?download=true"

    fun isValidModel(file: File): Boolean {
        if (!file.isFile || file.length() < MIN_MODEL_BYTES) return false
        return try {
            file.inputStream().use { input ->
                val magic = ByteArray(4)
                input.read(magic) == 4 && magic.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))
            }
        } catch (_: Exception) {
            false
        }
    }

    fun download(
        context: Context,
        destination: File,
        onProgress: (progress: Float, downloadedBytes: Long, totalBytes: Long) -> Unit
    ): File {
        if (isValidModel(destination)) {
            onProgress(1f, destination.length(), destination.length())
            return destination
        }

        val stats = StatFs(context.filesDir.absolutePath)
        if (stats.availableBytes < REQUIRED_FREE_BYTES) {
            throw IllegalStateException("Se necesitan al menos 3 GB libres para instalar el modelo.")
        }

        destination.parentFile?.mkdirs()
        val partial = File(destination.parentFile, destination.name + ".part")
        if (partial.exists()) partial.delete()

        val connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", "MiDoble-Android/0.2.0")
            setRequestProperty("Accept", "application/octet-stream")
        }

        try {
            val response = connection.responseCode
            if (response != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("El servidor del modelo respondió HTTP $response.")
            }
            val totalBytes = connection.contentLengthLong
            if (totalBytes in 1 until MIN_MODEL_BYTES) {
                throw IllegalStateException("El archivo remoto no tiene el tamaño esperado para Qwen3-4B.")
            }

            var downloaded = 0L
            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var count: Int
                    while (input.read(buffer).also { count = it } != -1) {
                        output.write(buffer, 0, count)
                        downloaded += count
                        val progress = if (totalBytes > 0L) {
                            (downloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
                        } else 0f
                        onProgress(progress, downloaded, totalBytes)
                    }
                    output.fd.sync()
                }
            }

            if (downloaded < MIN_MODEL_BYTES || (totalBytes > 0L && downloaded != totalBytes)) {
                throw IllegalStateException("La descarga quedó incompleta. Inténtalo de nuevo con una conexión estable.")
            }
            if (!hasGgufMagic(partial)) {
                throw IllegalStateException("El archivo descargado no tiene formato GGUF válido.")
            }
            if (destination.exists() && !destination.delete()) {
                throw IllegalStateException("No se pudo reemplazar el modelo anterior.")
            }
            if (!partial.renameTo(destination)) {
                throw IllegalStateException("No se pudo guardar el modelo en el almacenamiento privado.")
            }
            onProgress(1f, downloaded, downloaded)
            return destination
        } catch (error: Exception) {
            partial.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun hasGgufMagic(file: File): Boolean =
        file.inputStream().use { input ->
            val magic = ByteArray(4)
            input.read(magic) == 4 && magic.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))
        }
}
