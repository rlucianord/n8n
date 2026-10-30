package com.midoble.app

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

private const val GIB = 1024.0 * 1024.0 * 1024.0

data class ChatMessage(val role: String, val content: String)

class MainActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val nativeBackendInfo = mutableStateOf("Verificación pendiente")
    private val modelPath = mutableStateOf<String?>(null)
    private val modelStatus = mutableStateOf("Preparando el modelo oficial Qwen3-4B...")
    private val downloadProgress = mutableStateOf(0f)
    private val isBusy = mutableStateOf(false)
    private val messages = mutableStateListOf<ChatMessage>()
    private val isModelLoaded = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        val storage = StatFs(Environment.getDataDirectory().absolutePath)
        val snapshot = DeviceSnapshot(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            totalRamBytes = memory.totalMem,
            availableRamBytes = memory.availMem,
            dataPartitionBytes = storage.totalBytes,
            availableDataPartitionBytes = storage.availableBytes,
            supportedAbis = Build.SUPPORTED_ABIS.joinToString(),
            isLowRamDevice = manager.isLowRamDevice
        )

        val savedPath = getSharedPreferences("mi_doble_beta", MODE_PRIVATE)
            .getString("model_path", null)
        if (savedPath != null && File(savedPath).exists()) {
            modelPath.value = savedPath
            modelStatus.value = "Modelo encontrado. Preparando carga automática..."
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BetaHome(
                        device = snapshot,
                        backendInfo = nativeBackendInfo.value,
                        modelStatus = modelStatus.value,
                        modelName = modelPath.value?.let { File(it).name },
                        isBusy = isBusy.value,
                        downloadProgress = downloadProgress.value,
                        messages = messages.toList(),
                        onDownloadModel = ::downloadAndLoadModel,
                        onSend = ::sendMessage,
                        onClearChat = { messages.clear() }
                    )
                }
            }
        }

        if (savedPath != null && File(savedPath).exists()) loadModel(savedPath)

        executor.execute {
            val info = try {
                NativeLlamaBridge.backendInfo()
            } catch (error: Throwable) {
                "Motor nativo no disponible: ${error.javaClass.simpleName}"
            }
            runOnUiThread { nativeBackendInfo.value = info }
        }
    }

    private fun downloadAndLoadModel() {
        if (isBusy.value || isModelLoaded.value) return
        val cached = File(filesDir, "models/Qwen3-4B-Q4_K_M.gguf")
        if (ModelDownloader.isValidModel(cached)) {
            modelPath.value = cached.absolutePath
            loadModel(cached.absolutePath)
            return
        }
        isBusy.value = true
        downloadProgress.value = 0f
        modelStatus.value = "Descargando Qwen3-4B (aprox. 2.5 GB). Puedes dejar la aplicación abierta..."
        executor.execute {
            try {
                val downloaded = ModelDownloader.download(
                    context = this,
                    destination = cached,
                    onProgress = { progress, downloadedBytes, totalBytes ->
                        runOnUiThread {
                            downloadProgress.value = progress
                            modelStatus.value = if (totalBytes > 0L) {
                                "Descargando modelo: %.1f / %.1f GiB".format(
                                    downloadedBytes / GIB, totalBytes / GIB
                                )
                            } else {
                                "Descargando modelo: %.1f GiB".format(downloadedBytes / GIB)
                            }
                        }
                    }
                )
                modelPath.value = downloaded.absolutePath
                getSharedPreferences("mi_doble_beta", MODE_PRIVATE).edit()
                    .putString("model_path", downloaded.absolutePath).apply()
                runOnUiThread {
                    downloadProgress.value = 1f
                    modelStatus.value = "Descarga verificada. Cargando el modelo..."
                }
                val result = NativeLlamaBridge.loadModel(downloaded.absolutePath)
                runOnUiThread {
                    modelStatus.value = result
                    isModelLoaded.value = result.startsWith("Modelo GGUF cargado correctamente")
                    isBusy.value = false
                }
            } catch (error: Exception) {
                runOnUiThread {
                    modelStatus.value = "No se pudo descargar o cargar el modelo: ${error.message ?: error.javaClass.simpleName}"
                    isBusy.value = false
                }
            }
        }
    }

    private fun loadModel(path: String) {
        if (isBusy.value) return
        isBusy.value = true
        isModelLoaded.value = false
        modelStatus.value = "Cargando modelo local. Este proceso puede tardar..."
        executor.execute {
            val result = try {
                NativeLlamaBridge.loadModel(path)
            } catch (error: Throwable) {
                "Error del motor: ${error.javaClass.simpleName}: ${error.message ?: ""}"
            }
            runOnUiThread {
                modelStatus.value = result
                isModelLoaded.value = result.startsWith("Modelo GGUF cargado correctamente")
                isBusy.value = false
            }
        }
    }

    private fun sendMessage(text: String) {
        val cleaned = text.trim()
        if (cleaned.isEmpty() || isBusy.value) return
        if (!isModelLoaded.value) {
            modelStatus.value = "Carga un modelo GGUF antes de iniciar una conversación."
            return
        }
        messages.add(ChatMessage("user", cleaned))
        isBusy.value = true
        val history = messages.takeLast(10)
        val prompt = buildString {
            append("<|im_start|>system\nEres Mi Doble, un asistente personal útil, claro y honesto. Responde en el idioma del usuario.\n<|im_end|>\n")
            history.forEach { message ->
                append("<|im_start|>")
                append(if (message.role == "user") "user" else "assistant")
                append("\n")
                append(message.content)
                append("\n<|im_end|>\n")
            }
            append("<|im_start|>assistant\n")
        }
        executor.execute {
            val answer = try {
                NativeLlamaBridge.generate(prompt, 256)
            } catch (error: Throwable) {
                "Error de inferencia: ${error.javaClass.simpleName}: ${error.message ?: ""}"
            }
            runOnUiThread {
                messages.add(ChatMessage("assistant", answer.ifBlank { "El modelo no generó texto en esta respuesta." }))
                isBusy.value = false
            }
        }
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}

private data class DeviceSnapshot(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val dataPartitionBytes: Long,
    val availableDataPartitionBytes: Long,
    val supportedAbis: String,
    val isLowRamDevice: Boolean
) {
    val compatibility: DeviceCompatibilityReport
        get() = DeviceCompatibilityPolicy.evaluate(
            androidApi = apiLevel,
            totalRamBytes = totalRamBytes,
            declaredStorageGb = null
        )
}

@Composable
private fun BetaHome(
    device: DeviceSnapshot,
    backendInfo: String,
    modelStatus: String,
    modelName: String?,
    isBusy: Boolean,
    downloadProgress: Float,
    messages: List<ChatMessage>,
    onDownloadModel: () -> Unit,
    onSend: (String) -> Unit,
    onClearChat: () -> Unit
) {
    val input = androidx.compose.runtime.remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Mi Doble", style = MaterialTheme.typography.headlineLarge)
        Text("BETA TEST · IA local en tu dispositivo", style = MaterialTheme.typography.labelLarge)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Modelo local", style = MaterialTheme.typography.titleMedium)
                Text(modelName ?: "Qwen3-4B · Q4_K_M · Modelo oficial")
                if (isBusy && downloadProgress > 0f) {
                    LinearProgressIndicator(progress = { downloadProgress }, modifier = Modifier.fillMaxWidth())
                }
                Button(
                    onClick = onDownloadModel,
                    enabled = !isBusy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (isBusy) "Preparando modelo..." else if (modelName == null) "Descargar e instalar modelo" else "Volver a cargar modelo") }
                Text(modelStatus, style = MaterialTheme.typography.bodySmall)
                Text("La primera descarga requiere Internet y aproximadamente 2.5 GB. Después, el modelo funciona localmente y no se envían tus conversaciones a servidores.", style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Conversación", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.weight(1f))
                    Button(onClick = onClearChat, enabled = messages.isNotEmpty() && !isBusy) { Text("Limpiar") }
                }
                if (messages.isEmpty()) {
                    Text("Cuando cargues un modelo, escribe tu primer mensaje aquí.", style = MaterialTheme.typography.bodyMedium)
                } else {
                    messages.forEach { message ->
                        Text(if (message.role == "user") "Tú" else "Mi Doble", style = MaterialTheme.typography.labelLarge)
                        Text(message.content, style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
                OutlinedTextField(
                    value = input.value,
                    onValueChange = { input.value = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Escribe un mensaje") },
                    enabled = !isBusy,
                    minLines = 2
                )
                Button(
                    onClick = {
                        val message = input.value
                        input.value = ""
                        onSend(message)
                    },
                    enabled = !isBusy && input.value.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (isBusy) "Procesando..." else "Enviar") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Diagnóstico del dispositivo", style = MaterialTheme.typography.titleMedium)
                Text("${device.manufacturer} ${device.model}")
                Text("Android ${device.androidVersion} (API ${device.apiLevel}) · ${status(device.compatibility.androidSupported)}")
                Text("RAM total: ${formatBytes(device.totalRamBytes)} · ${status(device.compatibility.ramSupported)}")
                Text("Arquitecturas: ${device.supportedAbis}")
                Text("Almacenamiento disponible: ${formatBytes(device.availableDataPartitionBytes)}")
                Text("llama.cpp: $backendInfo")
            }
        }
        Text("Beta experimental. El historial se mantiene en memoria durante esta sesión; aún no se guarda de forma persistente.", style = MaterialTheme.typography.bodySmall)
    }
}

private fun status(compatible: Boolean): String = if (compatible) "Cumple" else "No cumple"
private fun formatBytes(bytes: Long): String = String.format(Locale.getDefault(), "%.1f GiB", bytes / GIB)
