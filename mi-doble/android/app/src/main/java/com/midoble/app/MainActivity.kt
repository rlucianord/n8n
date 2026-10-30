package com.midoble.app

import android.app.ActivityManager
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

private const val GIB = 1024.0 * 1024.0 * 1024.0

private data class ModelOption(val name: String, val description: String, val artifact: String?)

private val modelOptions = listOf(
    ModelOption("Phi-4", "Un modelo de Microsoft para razonamiento y tareas generales.", null),
    ModelOption("Qwen3", "Modelo multilingüe para tareas generales. Disponible para instalación.", "Qwen3-4B-Q4_K_M.gguf"),
    ModelOption("Llama 3.2", "Modelo compacto para asistencia cotidiana.", null),
    ModelOption("Gemma 3", "Modelo compacto de Google.", null)
)

class MainActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val backendInfo = mutableStateOf("Comprobando motor local…")
    private val modelStatus = mutableStateOf("Selecciona un modelo.")
    private val modelProgress = mutableStateOf(0f)
    private val modelReady = mutableStateOf(false)
    private val busy = mutableStateOf(false)
    private val screen = mutableStateOf("home")
    private val selectedModel = mutableStateOf("Qwen3")
    private val voiceMode = mutableStateOf("Voz del dispositivo")
    private val customVoiceLabel = mutableStateOf("")
    private val voiceEnabledForCalls = mutableStateOf(false)
    private val voiceEnabledForMeetings = mutableStateOf(false)
    private val profileName = mutableStateOf("")
    private val profileRole = mutableStateOf("")
    private val profileLanguages = mutableStateOf("Español, inglés")
    private val profileGoals = mutableStateOf("")
    private val profileStyle = mutableStateOf("Directo, claro y práctico")
    private val profileValues = mutableStateOf("Privacidad, autonomía y continuidad")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("mi_doble_profile", MODE_PRIVATE)
        profileName.value = prefs.getString("name", "") ?: ""
        profileRole.value = prefs.getString("role", "") ?: ""
        profileLanguages.value = prefs.getString("languages", "Español, inglés") ?: "Español, inglés"
        profileGoals.value = prefs.getString("goals", "") ?: ""
        profileStyle.value = prefs.getString("style", "Directo, claro y práctico") ?: "Directo, claro y práctico"
        profileValues.value = prefs.getString("values", "Privacidad, autonomía y continuidad") ?: "Privacidad, autonomía y continuidad"
        selectedModel.value = prefs.getString("active_model", "Qwen3") ?: "Qwen3"
        voiceMode.value = prefs.getString("voice_mode", "Voz del dispositivo") ?: "Voz del dispositivo"
        customVoiceLabel.value = prefs.getString("custom_voice_label", "") ?: ""
        voiceEnabledForCalls.value = prefs.getBoolean("voice_use_calls", false)
        voiceEnabledForMeetings.value = prefs.getBoolean("voice_use_meetings", false)

        val manager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        val storage = StatFs(Environment.getDataDirectory().absolutePath)
        val snapshot = DeviceSnapshot(
            manufacturer = android.os.Build.MANUFACTURER,
            model = android.os.Build.MODEL,
            androidVersion = android.os.Build.VERSION.RELEASE,
            apiLevel = android.os.Build.VERSION.SDK_INT,
            totalRamBytes = memory.totalMem,
            availableRamBytes = memory.availMem,
            availableStorageBytes = storage.availableBytes,
            supportedAbis = android.os.Build.SUPPORTED_ABIS.joinToString(),
            lowRam = manager.isLowRamDevice
        )

        val installed = File(filesDir, "models/Qwen3-4B-Q4_K_M.gguf")
        if (ModelDownloader.isValidModel(installed)) loadModel(installed)
        executor.execute {
            val info = try { NativeLlamaBridge.backendInfo() }
            catch (error: Throwable) { "Motor nativo: ${error.javaClass.simpleName}: ${error.message ?: ""}" }
            runOnUiThread { backendInfo.value = info }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    when (screen.value) {
                        "profile" -> PersonalizationScreen(
                            name = profileName.value, onName = { profileName.value = it },
                            role = profileRole.value, onRole = { profileRole.value = it },
                            languages = profileLanguages.value, onLanguages = { profileLanguages.value = it },
                            goals = profileGoals.value, onGoals = { profileGoals.value = it },
                            style = profileStyle.value, onStyle = { profileStyle.value = it },
                            values = profileValues.value, onValues = { profileValues.value = it },
                            onSave = ::saveProfile, onBack = { screen.value = "home" }
                        )
                        "model" -> ModelSettingsScreen(
                            options = modelOptions, selected = selectedModel.value,
                            onSelected = ::selectModel, status = modelStatus.value,
                            progress = modelProgress.value, busy = busy.value,
                            backend = backendInfo.value, device = snapshot,
                            onInstall = ::installSelectedModel, onBack = { screen.value = "home" }
                        )
                        "agency" -> AgencySettingsScreen(
                            voiceMode = voiceMode.value, onVoiceMode = { voiceMode.value = it },
                            customVoiceLabel = customVoiceLabel.value,
                            onCustomVoiceLabel = { customVoiceLabel.value = it },
                            calls = voiceEnabledForCalls.value, onCalls = { voiceEnabledForCalls.value = it },
                            meetings = voiceEnabledForMeetings.value, onMeetings = { voiceEnabledForMeetings.value = it },
                            onSave = ::saveAgencySettings, onBack = { screen.value = "home" }
                        )
                        else -> DigitalSelfHome(
                            name = profileName.value, role = profileRole.value, goals = profileGoals.value,
                            modelReady = modelReady.value, selectedModel = selectedModel.value,
                            modelStatus = modelStatus.value,
                            onProfile = { screen.value = "profile" }, onModel = { screen.value = "model" },
                            onAgency = { screen.value = "agency" }
                        )
                    }
                }
            }
        }
    }

    private fun saveProfile() {
        getSharedPreferences("mi_doble_profile", MODE_PRIVATE).edit()
            .putString("name", profileName.value.trim())
            .putString("role", profileRole.value.trim())
            .putString("languages", profileLanguages.value.trim())
            .putString("goals", profileGoals.value.trim())
            .putString("style", profileStyle.value.trim())
            .putString("values", profileValues.value.trim())
            .apply()
        screen.value = "home"
    }

    private fun saveAgencySettings() {
        getSharedPreferences("mi_doble_profile", MODE_PRIVATE).edit()
            .putString("voice_mode", voiceMode.value)
            .putString("custom_voice_label", customVoiceLabel.value.trim())
            .putBoolean("voice_use_calls", voiceEnabledForCalls.value)
            .putBoolean("voice_use_meetings", voiceEnabledForMeetings.value)
            .apply()
        screen.value = "home"
    }

    private fun selectModel(name: String) {
        if (modelOptions.none { it.name == name }) return
        selectedModel.value = name
        getSharedPreferences("mi_doble_profile", MODE_PRIVATE).edit().putString("active_model", name).apply()
        val file = when (name) {
            "Qwen3" -> File(filesDir, "models/Qwen3-4B-Q4_K_M.gguf")
            else -> null
        }
        modelReady.value = file?.let(ModelDownloader::isValidModel) == true
        modelStatus.value = when {
            modelReady.value -> "Modelo seleccionado e instalado."
            name == "Qwen3" -> "Qwen3 está seleccionado y listo para instalar."
            else -> "Modelo incluido en el catálogo. Su instalador verificado se habilitará cuando se valide el archivo oficial."
        }
        if (modelReady.value && file != null) loadModel(file)
    }

    private fun installSelectedModel() {
        if (busy.value || modelReady.value) return
        if (selectedModel.value != "Qwen3") {
            modelStatus.value = "El instalador de este modelo aún no está habilitado: falta verificar su archivo, tamaño y SHA-256. Qwen3 permanece disponible."
            return
        }
        busy.value = true
        modelStatus.value = "Descargando Qwen3 desde su repositorio oficial…"
        modelProgress.value = 0f
        executor.execute {
            try {
                val target = File(filesDir, "models/Qwen3-4B-Q4_K_M.gguf")
                val downloaded = ModelDownloader.download(this, target) { progress, bytes, total ->
                    runOnUiThread {
                        modelProgress.value = progress
                        modelStatus.value = "Descargando Qwen3: %.2f / %.2f GiB".format(bytes / GIB, total / GIB)
                    }
                }
                val result = NativeLlamaBridge.loadModel(downloaded.absolutePath)
                runOnUiThread {
                    modelStatus.value = result
                    modelReady.value = result.startsWith("Modelo GGUF cargado correctamente")
                    busy.value = false
                    if (modelReady.value) getSharedPreferences("mi_doble_profile", MODE_PRIVATE).edit()
                        .putString("active_model", "Qwen3").apply()
                }
            } catch (error: Exception) {
                runOnUiThread {
                    modelStatus.value = "No se pudo completar la instalación: ${error.message ?: error.javaClass.simpleName}"
                    busy.value = false
                }
            }
        }
    }

    private fun loadModel(file: File) {
        if (busy.value) return
        busy.value = true
        executor.execute {
            val result = try { NativeLlamaBridge.loadModel(file.absolutePath) }
            catch (error: Throwable) { "No se pudo iniciar el modelo: ${error.message ?: error.javaClass.simpleName}" }
            runOnUiThread {
                modelStatus.value = result
                modelReady.value = result.startsWith("Modelo GGUF cargado correctamente")
                busy.value = false
            }
        }
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}

private data class DeviceSnapshot(
    val manufacturer: String, val model: String, val androidVersion: String,
    val apiLevel: Int, val totalRamBytes: Long, val availableRamBytes: Long,
    val availableStorageBytes: Long, val supportedAbis: String, val lowRam: Boolean
)

@Composable
private fun DigitalSelfHome(
    name: String, role: String, goals: String, modelReady: Boolean, selectedModel: String,
    modelStatus: String, onProfile: () -> Unit, onModel: () -> Unit, onAgency: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Mi Doble", style = MaterialTheme.typography.headlineLarge)
        Text("Tu identidad digital. Tu memoria. Tus preferencias. Bajo tu control.")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (name.isBlank()) "Comencemos a configurar tu identidad" else "Hola, $name", style = MaterialTheme.typography.titleLarge)
                Text(if (role.isBlank()) "Tu perfil personal todavía necesita configurarse." else role)
                if (goals.isNotBlank()) Text("Enfoque actual: $goals")
                Text("Modelo: $selectedModel · ${if (modelReady) "Activo" else "Pendiente"}")
                Text(modelStatus, style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(onClick = onProfile, modifier = Modifier.fillMaxWidth()) { Text("Personalizar mi identidad digital") }
        Button(onClick = onModel, modifier = Modifier.fillMaxWidth()) { Text("Elegir o cambiar modelo") }
        Button(onClick = onAgency, modifier = Modifier.fillMaxWidth()) { Text("Configurar voz y permisos") }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Centro de acciones", style = MaterialTheme.typography.titleMedium)
                Text("Aquí podrás configurar correo, llamadas y reuniones cuando sus conectores autorizados estén instalados.")
            }
        }
    }
}

@Composable
private fun PersonalizationScreen(
    name: String, onName: (String) -> Unit, role: String, onRole: (String) -> Unit,
    languages: String, onLanguages: (String) -> Unit, goals: String, onGoals: (String) -> Unit,
    style: String, onStyle: (String) -> Unit, values: String, onValues: (String) -> Unit,
    onSave: () -> Unit, onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Personalización", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(name, onName, Modifier.fillMaxWidth(), label = { Text("Cómo quieres que te llame") })
        OutlinedTextField(role, onRole, Modifier.fillMaxWidth(), label = { Text("Profesión y responsabilidades") }, minLines = 2)
        OutlinedTextField(languages, onLanguages, Modifier.fillMaxWidth(), label = { Text("Idiomas") })
        OutlinedTextField(goals, onGoals, Modifier.fillMaxWidth(), label = { Text("Objetivos y proyectos prioritarios") }, minLines = 3)
        OutlinedTextField(style, onStyle, Modifier.fillMaxWidth(), label = { Text("Estilo de comunicación y trabajo") }, minLines = 2)
        OutlinedTextField(values, onValues, Modifier.fillMaxWidth(), label = { Text("Principios y límites que debe respetar") }, minLines = 2)
        Button(onClick = onSave, Modifier.fillMaxWidth()) { Text("Guardar mi perfil") }
        Button(onClick = onBack, Modifier.fillMaxWidth()) { Text("Volver") }
    }
}

@Composable
private fun ModelSettingsScreen(
    options: List<ModelOption>, selected: String, onSelected: (String) -> Unit,
    status: String, progress: Float, busy: Boolean, backend: String, device: DeviceSnapshot,
    onInstall: () -> Unit, onBack: () -> Unit
) {
    val appContext = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Elige el modelo de Mi Doble", style = MaterialTheme.typography.headlineMedium)
        Text("Puedes cambiarlo cuando quieras. Los modelos que descargues permanecerán guardados.")
        options.forEach { option ->
            val installed = option.artifact != null && ModelDownloader.isValidModel(File(appContext.filesDir, "models/${option.artifact}"))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(option.name, style = MaterialTheme.typography.titleMedium)
                    Text(option.description, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onSelected(option.name) }, enabled = !busy) { Text(if (selected == option.name) "Seleccionado" else "Elegir") }
                        Text(when { installed -> "Instalado"; option.artifact == null -> "Próximamente"; else -> "Disponible" })
                    }
                }
            }
        }
        if (busy) LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth())
        Text(status)
        Button(onClick = onInstall, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (selected == "Qwen3") "Descargar e instalar" else "Instalador no disponible todavía") }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Diagnóstico", style = MaterialTheme.typography.titleMedium)
                Text("${device.manufacturer} ${device.model}")
                Text("Android ${device.androidVersion} (API ${device.apiLevel})")
                Text("RAM: ${formatBytes(device.totalRamBytes)} total / ${formatBytes(device.availableRamBytes)} disponible")
                Text("Espacio libre: ${formatBytes(device.availableStorageBytes)}")
                Text("ABI: ${device.supportedAbis} · Low RAM: ${device.lowRam}")
                Text(backend)
            }
        }
        Button(onClick = onBack, Modifier.fillMaxWidth()) { Text("Volver") }
    }
}

@Composable
private fun AgencySettingsScreen(
    voiceMode: String, onVoiceMode: (String) -> Unit,
    customVoiceLabel: String, onCustomVoiceLabel: (String) -> Unit,
    calls: Boolean, onCalls: (Boolean) -> Unit, meetings: Boolean, onMeetings: (Boolean) -> Unit,
    onSave: () -> Unit, onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Voz y permisos", style = MaterialTheme.typography.headlineMedium)
        Text("Elige la voz que utilizará Mi Doble. La elección de voz no activa por sí sola una llamada o reunión.")
        listOf("Voz del dispositivo", "Mi voz personalizada", "Otra voz").forEach { label ->
            Button(onClick = { onVoiceMode(label) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (voiceMode == label) "✓ $label" else label)
            }
        }
        if (voiceMode == "Mi voz personalizada") {
            OutlinedTextField(customVoiceLabel, onCustomVoiceLabel, Modifier.fillMaxWidth(), label = { Text("Nombre de mi voz autorizada") })
            Text("La creación de la voz personalizada requerirá una muestra que aportes, autorización expresa y un motor compatible.")
        }
        Button(onClick = { onCalls(!calls) }, modifier = Modifier.fillMaxWidth()) { Text(if (calls) "Usar voz en llamadas: activado" else "Usar voz en llamadas: desactivado") }
        Button(onClick = { onMeetings(!meetings) }, modifier = Modifier.fillMaxWidth()) { Text(if (meetings) "Usar voz en reuniones: activado" else "Usar voz en reuniones: desactivado") }
        Text("Mi Doble se identificará como asistente de IA al intervenir. Esta preferencia no oculta esa identidad.")
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Guardar preferencias") }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }
}

private fun formatBytes(bytes: Long): String = String.format(Locale.getDefault(), "%.2f GiB", bytes / GIB)
