package com.midoble.app

import android.content.Context
import android.os.StatFs
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Official Qwen model file, verified against the Hugging Face file metadata.
 * Download is resumable and the completed file is SHA-256 checked before use.
 */
internal object ModelDownloader {
    private const val MODEL_FILE = "Qwen3-4B-Q4_K_M.gguf"
    private const val MODEL_BYTES = 2_497_280_256L
    private const val REQUIRED_FREE_BYTES = 3_000_000_000L
    private const val EXPECTED_SHA256 = "7485fe6f11af29433bc51cab58009521f205840f5b4ae3a32fa7f92e8534fdf5"
    private const val BUFFER_SIZE = 1024 * 1024
    private const val DOWNLOAD_URL =
        "https://huggingface.co/Qwen/Qwen3-4B-GGUF/resolve/main/$MODEL_FILE"

    fun isValidModel(file: File): Boolean {
        if (!file.isFile || file.length() != MODEL_BYTES) return false
        return try {
            hasGgufMagic(file) && sha256(file) == EXPECTED_SHA256
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
            onProgress(1f, MODEL_BYTES, MODEL_BYTES)
            return destination
        }
        val stats = StatFs(context.filesDir.absolutePath)
        if (stats.availableBytes < REQUIRED_FREE_BYTES) {
            throw IllegalStateException("Se necesitan al menos 3 GB libres para instalar el modelo.")
        }
        destination.parentFile?.mkdirs()
        val partial = File(destination.parentFile, destination.name + ".part")
        var lastError: Exception? = null

        repeat(4) { attempt ->
            try {
                val currentLength = if (partial.exists()) partial.length() else 0L
                if (currentLength >= MODEL_BYTES) partial.delete()
                val offset = if (partial.exists()) partial.length() else 0L
                val connection = (URL(DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 45_000
                    readTimeout = 180_000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "MiDoble-Android/0.3.0")
                    setRequestProperty("Accept", "application/octet-stream")
                    setRequestProperty("Accept-Encoding", "identity")
                    if (offset > 0L) setRequestProperty("Range", "bytes=$offset-")
                }
                try {
                    val response = connection.responseCode
                    val append = offset > 0L && response == HttpURLConnection.HTTP_PARTIAL
                    if (response != HttpURLConnection.HTTP_OK && !append) {
                        if (response == HttpURLConnection.HTTP_PARTIAL || response == 416) {
                            partial.delete()
                            throw IllegalStateException("El servidor devolvió un rango de descarga inválido.")
                        }
                        throw IllegalStateException("Hugging Face respondió HTTP $response al descargar el modelo.")
                    }
                    val expectedRemaining = MODEL_BYTES - if (append) offset else 0L
                    val contentLength = connection.contentLengthLong
                    if (contentLength > 0L && contentLength != expectedRemaining) {
                        throw IllegalStateException("El tamaño anunciado por el servidor no coincide con el modelo esperado.")
                    }
                    if (!append && partial.exists()) partial.delete()
                    var downloaded = if (append) offset else 0L
                    connection.inputStream.use { input ->
                        FileOutputStream(partial, append).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                output.write(buffer, 0, count)
                                downloaded += count
                                onProgress((downloaded.toFloat() / MODEL_BYTES).coerceIn(0f, 1f), downloaded, MODEL_BYTES)
                            }
                            output.fd.sync()
                        }
                    }
                } finally {
                    connection.disconnect()
                }

                if (partial.length() != MODEL_BYTES) {
                    throw IllegalStateException("Descarga incompleta: ${partial.length()} de $MODEL_BYTES bytes. Se conservará el progreso para reanudar.")
                }
                if (!hasGgufMagic(partial)) throw IllegalStateException("El archivo recibido no tiene cabecera GGUF.")
                if (sha256(partial) != EXPECTED_SHA256) {
                    partial.delete()
                    throw IllegalStateException("La verificación SHA-256 falló; se descartó el archivo para proteger la instalación.")
                }
                if (destination.exists() && !destination.delete()) {
                    throw IllegalStateException("No se pudo reemplazar el archivo del modelo.")
                }
                if (!partial.renameTo(destination)) throw IllegalStateException("No se pudo guardar el modelo.")
                onProgress(1f, MODEL_BYTES, MODEL_BYTES)
                return destination
            } catch (error: Exception) {
                lastError = error
                if (error.message?.contains("SHA-256") == true || error.message?.contains("cabecera") == true) {
                    partial.delete()
                }
                if (attempt < 3) Thread.sleep(1500L * (attempt + 1))
            }
        }
        throw IllegalStateException(lastError?.message ?: "No fue posible completar la descarga.")
    }

    private fun hasGgufMagic(file: File): Boolean =
        file.inputStream().use { input ->
            val magic = ByteArray(4)
            input.read(magic) == 4 && magic.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
