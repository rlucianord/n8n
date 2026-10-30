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
import androidx.compose.foundation.layout.Spacer
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

class MainActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val backendInfo = mutableStateOf("Comprobando motor local…")
    private val modelStatus = mutableStateOf("El modelo todavía no está instalado.")
    private val modelProgress = mutableStateOf(0f)
    private val modelReady = mutableStateOf(false)
    private val busy = mutableStateOf(false)
    private val screen = mutableStateOf("home")
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
        val modelFile = File(filesDir, "models/Qwen3-4B-Q4_K_M.gguf")
        if (ModelDownloader.isValidModel(modelFile)) {
            modelStatus.value = "Modelo instalado y verificado. Inicializando motor…"
            loadModel(modelFile)
        } else {
            modelStatus.value = "Qwen3-4B Q4_K_M listo para instalar. La instalación se conserva al actualizar Mi Doble."
        }

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
                            status = modelStatus.value, progress = modelProgress.value, busy = busy.value,
                            backend = backendInfo.value, device = snapshot,
                            onInstall = ::installModel, onBack = { screen.value = "home" }
                        )
                        else -> DigitalSelfHome(
                            name = profileName.value, role = profileRole.value, goals = profileGoals.value,
                            modelReady = modelReady.value, modelStatus = modelStatus.value,
                            onProfile = { screen.value = "profile" }, onModel = { screen.value = "model" }
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

    private fun installModel() {
        if (busy.value || modelReady.value) return
        busy.value = true
        modelStatus.value = "Conectando con el repositorio oficial de Qwen…"
        modelProgress.value = 0f
        executor.execute {
            try {
                val target = File(filesDir, "models/Qwen3-4B-Q4_K_M.gguf")
                val downloaded = ModelDownloader.download(this, target) { progress, bytes, total ->
                    runOnUiThread {
                        modelProgress.value = progress
                        modelStatus.value = "Descargando modelo: %.2f / %.2f GiB".format(
                            bytes / GIB, total / GIB
                        )
                    }
                }
                runOnUiThread { modelStatus.value = "Modelo verificado. Inicializando llama.cpp…" }
                val result = NativeLlamaBridge.loadModel(downloaded.absolutePath)
                runOnUiThread {
                    modelStatus.value = result
                    modelReady.value = result.startsWith("Modelo GGUF cargado correctamente")
                    busy.value = false
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
            catch (error: Throwable) { "No se pudo iniciar el modelo: ${error.javaClass.simpleName}: ${error.message ?: ""}" }
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
    name: String, role: String, goals: String, modelReady: Boolean, modelStatus: String,
    onProfile: () -> Unit, onModel: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Mi Doble", style = MaterialTheme.typography.headlineLarge)
        Text("Tu identidad digital. Tu memoria. Tus preferencias. Bajo tu control.")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (name.isBlank()) "Comencemos a configurar tu identidad" else "Hola, $name", style = MaterialTheme.typography.titleLarge)
                Text(if (role.isBlank()) "Tu perfil personal todavía necesita configurarse." else role)
                if (goals.isNotBlank()) Text("Enfoque actual: $goals")
                Text("Estado del modelo: ${if (modelReady) "Activo" else "Pendiente"}")
                Text(modelStatus, style = MaterialTheme.typography.bodySmall)
            }
        }
        Button(onClick = onProfile, modifier = Modifier.fillMaxWidth()) { Text("Personalizar mi identidad digital") }
        Button(onClick = onModel, modifier = Modifier.fillMaxWidth()) { Text("Modelo e instalación") }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Qué estamos construyendo", style = MaterialTheme.typography.titleMedium)
                Text("Un sistema personal que conserva tu perfil, aprende de la información que decidas guardar, mantiene continuidad entre proyectos y preferencias, y te ayuda a organizar decisiones y tareas.")
                Text("La memoria persistente y el aprendizaje autorizado se incorporarán en las siguientes etapas; esta beta configura tu perfil local y prepara el motor.")
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
        Text("Define cómo debe representarte y asistirte tu yo digital.")
        OutlinedTextField(name, onName, Modifier.fillMaxWidth(), label = { Text("Cómo quieres que te llame") })
        OutlinedTextField(role, onRole, Modifier.fillMaxWidth(), label = { Text("Profesión y responsabilidades") }, minLines = 2)
        OutlinedTextField(languages, onLanguages, Modifier.fillMaxWidth(), label = { Text("Idiomas") })
        OutlinedTextField(goals, onGoals, Modifier.fillMaxWidth(), label = { Text("Objetivos y proyectos prioritarios") }, minLines = 3)
        OutlinedTextField(style, onStyle, Modifier.fillMaxWidth(), label = { Text("Estilo de comunicación y trabajo") }, minLines = 2)
        OutlinedTextField(values, onValues, Modifier.fillMaxWidth(), label = { Text("Principios y límites que debe respetar") }, minLines = 2)
        Button(onClick = onSave, Modifier.fillMaxWidth()) { Text("Guardar mi perfil") }
        Button(onClick = onBack, Modifier.fillMaxWidth()) { Text("Volver") }
        Text("Los datos se guardan localmente en esta beta. La protección con SQLCipher se integrará en la siguiente fase.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ModelSettingsScreen(
    status: String, progress: Float, busy: Boolean, backend: String, device: DeviceSnapshot,
    onInstall: () -> Unit, onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Motor de inteligencia", style = MaterialTheme.typography.headlineMedium)
        Text("Qwen3-4B · GGUF · Q4_K_M")
        Text("El archivo se descarga una sola vez y permanece en el almacenamiento privado al actualizar la aplicación.")
        if (busy) LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth())
        Text(status)
        Button(onClick = onInstall, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Preparando…" else "Instalar o reparar modelo")
        }
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

private fun formatBytes(bytes: Long): String = String.format(Locale.getDefault(), "%.2f GiB", bytes / GIB)
