package com.midoble.app

import android.Manifest
import android.app.role.RoleManager
import android.app.ActivityManager
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import android.widget.Toast
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

private const val GIB = 1024.0 * 1024.0 * 1024.0

private data class ModelOption(val name: String, val description: String, val artifact: String?)

private val modelOptions = listOf(
    ModelOption("Phi-4", "Modelo de Microsoft para razonamiento y tareas generales.", null),
    ModelOption("Qwen3", "Modelo multilingüe para tareas generales. Disponible para instalación.", "Qwen3-4B-Q4_K_M.gguf"),
    ModelOption("Gemma 3", "Modelo compacto de Google.", null),
    ModelOption("Llama 3.2", "Modelo compacto de Meta para tareas generales.", null),
    ModelOption("SmolLM3", "Modelo pequeño multilingüe de Hugging Face; compatibilidad del dispositivo pendiente de validación.", null),
    ModelOption("Gemini Nano", "Motor de IA gestionado por Android AICore. Se activa solo si el sistema confirma compatibilidad.", null)
)

class MainActivity : ComponentActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val backendInfo = mutableStateOf("Comprobando motor local…")
    private val nativeAiStatus = mutableStateOf("Comprobando compatibilidad con Android AICore…")
    private val nativeAiAvailable = mutableStateOf(false)
    private val nativeAiDownloadable = mutableStateOf(false)
    private val nativeAiOutput = mutableStateOf("")
    private val modelStatus = mutableStateOf("Selecciona un modelo.")
    private val modelProgress = mutableStateOf(0f)
    private val modelReady = mutableStateOf(false)
    private val busy = mutableStateOf(false)
    private val screen = mutableStateOf("home")
    private val settingsMenuExpanded = mutableStateOf(false)
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
    private val simLines = mutableStateOf<List<SimLine>>(emptyList())
    private val selectedSimSlot = mutableStateOf(-1)
    private val simPermissionGranted = mutableStateOf(false)
    private val blockedNumbers = mutableStateOf<List<String>>(emptyList())
    private val screeningRoleHeld = mutableStateOf(false)
    private val dialerRoleHeld = mutableStateOf(false)
    private val incomingCallNumber = mutableStateOf<String?>(null)
    private val includeModelsInBackup = mutableStateOf(true)
    private val backupStatus = mutableStateOf("")
    private lateinit var createBackupLauncher: ActivityResultLauncher<String>
    private lateinit var restoreBackupLauncher: ActivityResultLauncher<Array<String>>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingCallNumber.value = intent.getStringExtra("incoming_call_number")?.takeIf { it.isNotBlank() }
        createBackupLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri != null) {
                backupStatus.value = try { BackupManager.export(this, uri, includeModelsInBackup.value) }
                catch (error: Exception) { "No se pudo crear el respaldo: ${error.message ?: error.javaClass.simpleName}" }
            }
        }
        restoreBackupLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                backupStatus.value = try {
                    val message = BackupManager.restore(this, uri)
                    val restored = getSharedPreferences("mi_doble_profile", MODE_PRIVATE)
                    profileName.value = restored.getString("name", "") ?: ""
                    profileRole.value = restored.getString("role", "") ?: ""
                    profileLanguages.value = restored.getString("languages", "Español, inglés") ?: "Español, inglés"
                    profileGoals.value = restored.getString("goals", "") ?: ""
                    profileStyle.value = restored.getString("style", "Directo, claro y práctico") ?: "Directo, claro y práctico"
                    profileValues.value = restored.getString("values", "Privacidad, autonomía y continuidad") ?: "Privacidad, autonomía y continuidad"
                    voiceMode.value = restored.getString("voice_mode", "Voz del dispositivo") ?: "Voz del dispositivo"
                    customVoiceLabel.value = restored.getString("custom_voice_label", "") ?: ""
                    voiceEnabledForCalls.value = restored.getBoolean("voice_use_calls", false)
                    voiceEnabledForMeetings.value = restored.getBoolean("voice_use_meetings", false)
                    selectedSimSlot.value = restored.getInt("preferred_sim_slot", -1)
                    blockedNumbers.value = BlockedNumbersStore.read(this)
                    selectModel(restored.getString("active_model", "Qwen3") ?: "Qwen3")
                    message
                } catch (error: Exception) { "No se pudo restaurar el respaldo: ${error.message ?: error.javaClass.simpleName}" }
            }
        }
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
        selectedSimSlot.value = prefs.getInt("preferred_sim_slot", -1)
        blockedNumbers.value = BlockedNumbersStore.read(this)
        refreshScreeningRole()
        refreshDialerRole()
        refreshSimLines()
        // CallScreeningService is never invoked until Android grants ROLE_CALL_SCREENING.
        // Request the role on first launch instead of silently leaving call detection inactive.
        if (!screeningRoleHeld.value) requestScreeningRole()
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 414)
        }

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

        executor.execute {
            val check = NativeDeviceAiProvider.checkAvailability()
            runOnUiThread {
                nativeAiStatus.value = check.detail
                nativeAiAvailable.value = check.availability == NativeDeviceAiProvider.Availability.AVAILABLE
                nativeAiDownloadable.value = check.availability == NativeDeviceAiProvider.Availability.DOWNLOADABLE
                if (nativeAiAvailable.value) {
                    selectedModel.value = "Gemini Nano"
                    modelReady.value = true
                    modelStatus.value = "Gemini Nano está activo como motor local preferido."
                    getSharedPreferences("mi_doble_profile", MODE_PRIVATE).edit().putString("active_model", "Gemini Nano").apply()
                }
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Box(Modifier.fillMaxSize()) {
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
                            nativeStatus = nativeAiStatus.value, nativeAvailable = nativeAiAvailable.value,
                            nativeDownloadable = nativeAiDownloadable.value, nativeOutput = nativeAiOutput.value,
                            onNativeTest = ::testNativeAi,
                            onInstall = ::installSelectedModel, onBack = { screen.value = "home" }
                        )
                        "blocked" -> CallBlocklistScreen(numbers = blockedNumbers.value, screeningEnabled = screeningRoleHeld.value, onAdd = ::addBlockedNumber, onRemove = ::removeBlockedNumber, onEnableScreening = ::requestScreeningRole, onBack = { screen.value = "home" })
                        "phone" -> PhoneSettingsScreen(
                            lines = simLines.value, selectedSlot = selectedSimSlot.value,
                            permissionGranted = simPermissionGranted.value,
                            onRequestPermission = { requestPermissions(arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS), 410) },
                            onSelect = { slot -> selectedSimSlot.value = slot; getSharedPreferences("mi_doble_profile", MODE_PRIVATE).edit().putInt("preferred_sim_slot", slot).apply() },
                            onRefresh = ::refreshSimLines, onBack = { screen.value = "home" }
                        )
                        "backup" -> BackupRestoreScreen(
                            includeModels = includeModelsInBackup.value,
                            onIncludeModels = { includeModelsInBackup.value = it },
                            status = backupStatus.value,
                            onCreateBackup = { createBackupLauncher.launch("MiDoble-respaldo.zip") },
                            onRestoreBackup = { restoreBackupLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                            onBack = { screen.value = "home" }
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
                            menuExpanded = settingsMenuExpanded.value,
                            onMenu = { settingsMenuExpanded.value = true },
                            onDismissMenu = { settingsMenuExpanded.value = false },
                            onProfile = { settingsMenuExpanded.value = false; screen.value = "profile" },
                            onModel = { settingsMenuExpanded.value = false; screen.value = "model" },
                            onAgency = { settingsMenuExpanded.value = false; screen.value = "agency" },
                            onPhone = { settingsMenuExpanded.value = false; screen.value = "phone" },
                            onBlocked = { settingsMenuExpanded.value = false; screen.value = "blocked" },
                            onBackup = { settingsMenuExpanded.value = false; backupStatus.value = ""; screen.value = "backup" },
                            dialerRoleHeld = dialerRoleHeld.value,
                            onRequestDialerRole = ::requestDialerRole
                        )
                    }
                    incomingCallNumber.value?.let { caller ->
                        AlertDialog(
                            onDismissRequest = { incomingCallNumber.value = null },
                            title = { Text("Llamada detectada por Mi Doble") },
                            text = {
                                Text(
                                    "${caller.ifBlank { "Número no disponible" }} está llamando. " +
                                        "Mi Doble ha recibido el aviso y puede abrir esta aplicación. " +
                                        "Esta versión todavía no puede contestar ni escuchar llamadas celulares."
                                )
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    val answered = MiDobleInCallService.answerCurrentCall()
                                    if (!answered) Toast.makeText(this@MainActivity, "Android no permitió contestar desde Mi Doble. La llamada sigue disponible en la aplicación telefónica.", Toast.LENGTH_LONG).show()
                                    incomingCallNumber.value = null
                                }) {
                                    Text("La atiendo yo")
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    Toast.makeText(this@MainActivity, "La gestión telefónica está integrada, pero el audio bidireccional del asistente aún no está habilitado. Atiende tú la llamada por ahora.", Toast.LENGTH_LONG).show()
                                }) {
                                    Text("Que atienda Mi Doble")
                                }
                            }
                        )
                    }
                    }
                }
            }
        }
    }

    private fun refreshSimLines() {
        simPermissionGranted.value = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.READ_PHONE_NUMBERS) == PackageManager.PERMISSION_GRANTED
        if (!simPermissionGranted.value) { simLines.value = emptyList(); return }
        try {
            val manager = getSystemService(TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            simLines.value = manager.activeSubscriptionInfoList.orEmpty().map { info ->
                SimLine(info.simSlotIndex, info.subscriptionId, info.carrierName?.toString()?.takeIf { it.isNotBlank() } ?: "Operador móvil", info.displayName?.toString()?.takeIf { it.isNotBlank() } ?: "SIM ${info.simSlotIndex + 1}", info.number?.takeIf { it.isNotBlank() })
            }
        } catch (_: SecurityException) { simLines.value = emptyList() } catch (_: UnsupportedOperationException) { simLines.value = emptyList() }
    }
    override fun onResume() { super.onResume(); refreshSimLines(); refreshScreeningRole(); refreshDialerRole() }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra("incoming_call_number")?.takeIf { it.isNotBlank() }?.let {
            incomingCallNumber.value = it
        }
    }

    private fun refreshDialerRole() {
        val manager = getSystemService(RoleManager::class.java)
        dialerRoleHeld.value = manager?.isRoleAvailable(RoleManager.ROLE_DIALER) == true &&
            manager.isRoleHeld(RoleManager.ROLE_DIALER)
    }

    private fun requestDialerRole() {
        val manager = getSystemService(RoleManager::class.java) ?: return
        if (manager.isRoleAvailable(RoleManager.ROLE_DIALER) &&
            !manager.isRoleHeld(RoleManager.ROLE_DIALER)) {
            startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_DIALER), 415)
        }
    }

    private fun refreshScreeningRole() {
        val manager = getSystemService(RoleManager::class.java)
        screeningRoleHeld.value = manager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true && manager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
    }

    private fun requestScreeningRole() {
        val manager = getSystemService(RoleManager::class.java) ?: return
        if (manager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            startActivityForResult(manager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING), 413)
        }
    }

    private fun addBlockedNumber(raw: String) {
        val cleaned = raw.trim()
        if (cleaned.count { it.isDigit() } !in 7..15) return
        blockedNumbers.value = (blockedNumbers.value + cleaned).distinct()
        BlockedNumbersStore.write(this, blockedNumbers.value)
    }

    private fun removeBlockedNumber(number: String) {
        blockedNumbers.value = blockedNumbers.value.filterNot { it == number }
        BlockedNumbersStore.write(this, blockedNumbers.value)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 410) refreshSimLines()
        if (requestCode == 413) refreshScreeningRole()
        if (requestCode == 415) refreshDialerRole()
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
        if (name == "Gemini Nano") {
            selectedModel.value = name
            getSharedPreferences("mi_doble_profile", MODE_PRIVATE).edit().putString("active_model", name).apply()
            modelReady.value = nativeAiAvailable.value
            modelStatus.value = if (nativeAiAvailable.value) "Gemini Nano está disponible y seleccionado." else nativeAiStatus.value
            return
        }
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
        if (selectedModel.value == "Gemini Nano") {
            if (!nativeAiDownloadable.value) { modelStatus.value = nativeAiStatus.value; return }
            busy.value = true
            modelStatus.value = "Solicitando a Android la descarga del modelo nativo…"
            executor.execute {
                val result = NativeDeviceAiProvider.download { message -> runOnUiThread { modelStatus.value = message } }
                runOnUiThread {
                    nativeAiStatus.value = result.detail
                    nativeAiAvailable.value = result.availability == NativeDeviceAiProvider.Availability.AVAILABLE
                    nativeAiDownloadable.value = result.availability == NativeDeviceAiProvider.Availability.DOWNLOADABLE
                    modelReady.value = nativeAiAvailable.value
                    modelStatus.value = result.detail
                    busy.value = false
                }
            }
            return
        }
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

    private fun testNativeAi() {
        if (!nativeAiAvailable.value || busy.value) return
        busy.value = true
        nativeAiOutput.value = "Ejecutando una prueba local…"
        executor.execute {
            val result = try {
                ModelInferenceRouter.generate(this, "Preséntate brevemente y confirma que estás ejecutándote localmente en este teléfono.", buildNativeSystemInstruction())
            } catch (error: Throwable) {
                "La prueba no pudo completarse: ${error.message ?: error.javaClass.simpleName}"
            }
            runOnUiThread { nativeAiOutput.value = result; busy.value = false }
        }
    }

    private fun buildNativeSystemInstruction(): String {
        val prefs = getSharedPreferences("mi_doble_profile", MODE_PRIVATE)
        return listOf(
            "Eres Mi Doble, un asistente local y transparente. Respeta la identidad, el estilo y los límites del usuario.",
            "Nombre: ${prefs.getString("name", "").orEmpty()}",
            "Rol: ${prefs.getString("role", "").orEmpty()}",
            "Idiomas: ${prefs.getString("languages", "Español, inglés").orEmpty()}",
            "Estilo: ${prefs.getString("style", "Directo, claro y práctico").orEmpty()}",
            "Valores: ${prefs.getString("values", "Privacidad, autonomía y continuidad").orEmpty()}"
        ).joinToString("\n")
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

private data class SimLine(val slot: Int, val subscriptionId: Int, val carrier: String, val displayName: String, val number: String?)

@Composable
private fun PhoneSettingsScreen(lines: List<SimLine>, selectedSlot: Int, permissionGranted: Boolean, onRequestPermission: () -> Unit, onSelect: (Int) -> Unit, onRefresh: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Llamadas y líneas SIM", style = MaterialTheme.typography.headlineMedium)
        Text("Mi Doble detecta las tarjetas SIM activas. Puedes marcar una como línea preferida para futuras funciones telefónicas.")
        Text("Android puede ocultar el número de teléfono de la SIM. Si no aparece, eso no significa que la tarjeta no esté instalada.")
        if (!permissionGranted) {
            Text("Necesitamos permiso para consultar las SIM instaladas. Mi Doble solo lo utilizará para mostrar las líneas disponibles.")
            Button(onClick = onRequestPermission, modifier = Modifier.fillMaxWidth()) { Text("Permitir consultar las SIM") }
        } else if (lines.isEmpty()) {
            Text("No se detectaron SIM activas. Comprueba que estén instaladas y habilitadas.")
            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Volver a buscar SIM") }
        } else {
            lines.forEach { line -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("SIM " + (line.slot + 1) + " · " + line.carrier, style = MaterialTheme.typography.titleMedium)
                Text(line.displayName)
                Text("Número: " + (line.number ?: "No informado por Android/operador"))
                Button(onClick = { onSelect(line.slot) }, modifier = Modifier.fillMaxWidth()) { Text(if (selectedSlot == line.slot) "Línea preferida" else "Usar como línea preferida") }
            } } }
            Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Actualizar lista") }
        }
        Text("La línea preferida queda guardada en Mi Doble. En llamadas entrantes, la red entrega la llamada a la SIM cuyo número fue marcado; esta preferencia no cambia ese enrutamiento ni habilita todavía que la IA escuche la llamada.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
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
    modelStatus: String, menuExpanded: Boolean, onMenu: () -> Unit, onDismissMenu: () -> Unit,
    onProfile: () -> Unit, onModel: () -> Unit, onAgency: () -> Unit, onPhone: () -> Unit, onBlocked: () -> Unit, onBackup: () -> Unit,
    dialerRoleHeld: Boolean, onRequestDialerRole: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box {
                IconButton(onClick = onMenu) { Text("⚙", style = MaterialTheme.typography.headlineMedium) }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = onDismissMenu) {
                    DropdownMenuItem(text = { Text("Personalización") }, onClick = onProfile)
                    DropdownMenuItem(text = { Text("Modelos") }, onClick = onModel)
                    DropdownMenuItem(text = { Text("Voz y permisos") }, onClick = onAgency)
                    DropdownMenuItem(text = { Text("Llamadas y SIM") }, onClick = onPhone)
                    DropdownMenuItem(text = { Text("Números bloqueados") }, onClick = onBlocked)
                    DropdownMenuItem(text = { Text("Respaldo y restauración") }, onClick = onBackup)
                }
            }
            Column {
                Text("Mi Doble", style = MaterialTheme.typography.headlineMedium)
                Text("Tu asistente virtual", style = MaterialTheme.typography.bodySmall)
            }
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("🤖", fontSize = 84.sp)
                Text("ASISTENTE VIRTUAL", style = MaterialTheme.typography.labelMedium)
                Text("Siempre identificado como IA", style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (name.isBlank()) "Configura tu identidad digital" else "Hola, $name", style = MaterialTheme.typography.titleLarge)
                Text(if (role.isBlank()) "Tu perfil personal todavía necesita configurarse." else role)
                if (goals.isNotBlank()) Text("Enfoque actual: $goals")
                Text("Modelo: $selectedModel · ${if (modelReady) "Activo" else "Pendiente"}")
                Text(modelStatus, style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Llamadas telefónicas", style = MaterialTheme.typography.titleMedium)
                Text("Mi Doble puede asumir la interfaz telefónica de Android para gestionar llamadas entrantes y en curso.")
                Text(if (dialerRoleHeld) "Mi Doble es la aplicación telefónica predeterminada." else "Para gestionar llamadas, Android debe autorizar a Mi Doble como aplicación telefónica predeterminada.")
                if (!dialerRoleHeld) {
                    Button(onClick = onRequestDialerRole, modifier = Modifier.fillMaxWidth()) { Text("Establecer Mi Doble como aplicación telefónica") }
                }
                Text("La conversación automática con el asistente local requiere validar por separado el acceso bidireccional al audio de llamadas SIM. No se activa solo por asumir el rol telefónico.")
                Button(onClick = onPhone, modifier = Modifier.fillMaxWidth()) { Text("Configurar líneas SIM") }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Tu asistente, bajo tu control", style = MaterialTheme.typography.titleMedium)
                Text("Abre el menú de la esquina superior izquierda para cambiar modelos, personalizar tu identidad y configurar la voz.")
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
    nativeStatus: String, nativeAvailable: Boolean, nativeDownloadable: Boolean, nativeOutput: String,
    onNativeTest: () -> Unit, onInstall: () -> Unit, onBack: () -> Unit
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
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("IA nativa del teléfono", style = MaterialTheme.typography.titleMedium)
                Text("Gemini Nano / Android AICore", style = MaterialTheme.typography.bodyMedium)
                Text(nativeStatus, style = MaterialTheme.typography.bodySmall)
                if (nativeAvailable) {
                    Button(onClick = onNativeTest, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Probar IA nativa ahora") }
                    if (nativeOutput.isNotBlank()) Text(nativeOutput, style = MaterialTheme.typography.bodySmall)
                } else if (nativeDownloadable) {
                    Button(onClick = onInstall, enabled = !busy && selected == "Gemini Nano", modifier = Modifier.fillMaxWidth()) { Text("Descargar y activar Gemini Nano") }
                } else {
                    Text("Mi Doble volverá a comprobar la disponibilidad al iniciar. Si HONOR habilita el motor para este modelo, podrá activarlo sin sustituir Qwen3.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Button(onClick = onInstall, enabled = !busy && selected != "Gemini Nano", modifier = Modifier.fillMaxWidth()) { Text(if (selected == "Qwen3") "Descargar e instalar" else "Instalador no disponible todavía") }
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
