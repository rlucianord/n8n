package com.midoble.app

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    private val modelStatus = mutableStateOf("Selecciona un archivo GGUF para comenzar.")
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
        if (savedPath != null && File(savedPath).exists()) modelPath.value = savedPath

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    BetaHome(
                        device = snapshot,
                        backendInfo = nativeBackendInfo.value,
                        modelStatus = modelStatus.value,
                        modelName = modelPath.value?.let { File(it).name },
                        isBusy = isBusy.value,
                        messages = messages.toList(),
                        onImportModel = ::importModel,
                        onLoadModel = ::loadModel,
                        onSend = ::sendMessage,
                        onClearChat = { messages.clear() }
                    )
                }
            }
        }

        executor.execute {
            val info = try {
                NativeLlamaBridge.backendInfo()
            } catch (error: Throwable) {
                "Motor nativo no disponible: ${error.javaClass.simpleName}"
            }
            runOnUiThread { nativeBackendInfo.value = info }
        }
    }

    private fun importModel(uri: Uri) {
        if (isBusy.value) return
        isBusy.value = true
        modelStatus.value = "Copiando modelo al almacenamiento privado..."
        executor.execute {
            try {
                val name = queryDisplayName(uri).ifBlank { "modelo-importado.gguf" }
                    .replace(Regex("[^A-Za-z0-9._-]"), "_")
                if (!name.lowercase(Locale.ROOT).endsWith(".gguf")) {
                    throw IllegalArgumentException("Selecciona un archivo con extensión .gguf.")
                }
                val directory = File(filesDir, "models").apply { mkdirs() }
                val destination = File(directory, name)
                contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "No se pudo abrir el archivo seleccionado." }
                    destination.outputStream().use { output -> input.copyTo(output) }
                }
                modelPath.value = destination.absolutePath
                getSharedPreferences("mi_doble_beta", MODE_PRIVATE).edit()
                    .putString("model_path", destination.absolutePath).apply()
                isModelLoaded.value = false
                modelStatus.value = "Modelo copiado. Pulsa «Cargar modelo» para inicializar llama.cpp."
            } catch (error: Exception) {
                modelStatus.value = "Error al importar: ${error.message ?: error.javaClass.simpleName}"
            } finally {
                isBusy.value = false
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) return cursor.getString(index) ?: ""
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "modelo.gguf"
    }

    private fun loadModel() {
        val path = modelPath.value ?: run {
            modelStatus.value = "Primero selecciona un archivo GGUF."
            return
        }
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
    messages: List<ChatMessage>,
    onImportModel: (Uri) -> Unit,
    onLoadModel: () -> Unit,
    onSend: (String) -> Unit,
    onClearChat: () -> Unit
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImportModel(uri)
    }
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
                Text(modelName ?: "Ningún modelo seleccionado")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { picker.launch(arrayOf("*/*")) }, enabled = !isBusy) {
                        Text("Seleccionar GGUF")
                    }
                    Button(onClick = onLoadModel, enabled = !isBusy && modelName != null) {
                        Text("Cargar modelo")
                    }
                }
                Text(modelStatus, style = MaterialTheme.typography.bodySmall)
                Text("El archivo se copia al almacenamiento privado. No se descarga ni se envía a servidores.", style = MaterialTheme.typography.bodySmall)
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
