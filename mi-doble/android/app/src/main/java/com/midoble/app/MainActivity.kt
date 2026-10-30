package com.midoble.app

import android.app.ActivityManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

private const val GIB = 1024.0 * 1024.0 * 1024.0

class MainActivity : ComponentActivity() {
    private val nativeBackendInfo = mutableStateOf("Verificación pendiente")

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

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CompatibilityDashboard(snapshot, nativeBackendInfo.value)
                }
            }
        }

        Thread({
            val status = try {
                NativeLlamaBridge.backendInfo()
            } catch (error: LinkageError) {
                "Biblioteca nativa no disponible: ${error.javaClass.simpleName}"
            } catch (error: Exception) {
                "No se pudo iniciar el motor nativo: ${error.javaClass.simpleName}"
            }
            runOnUiThread { nativeBackendInfo.value = status }
        }, "MiDoble-NativeCheck").start()
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
    // The advertised storage capacity cannot be inferred reliably from StatFs.
    // It remains unverified until confirmed from the device specifications.
    val compatibility: DeviceCompatibilityReport
        get() = DeviceCompatibilityPolicy.evaluate(
            androidApi = apiLevel,
            totalRamBytes = totalRamBytes,
            declaredStorageGb = null
        )
}

@Composable
private fun CompatibilityDashboard(device: DeviceSnapshot, nativeInfo: String) {
    val report = device.compatibility
    val baselineLabel = when {
        !report.androidSupported -> "Android no compatible"
        !report.ramSupported -> "RAM inferior al mínimo"
        report.storageBelowMinimum -> "Almacenamiento inferior al mínimo"
        !report.storageVerified -> "Android y RAM cumplen; almacenamiento por confirmar"
        else -> "Requisitos mínimos confirmados"
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Mi Doble", style = MaterialTheme.typography.headlineLarge)
        Text("Diagnóstico inicial del dispositivo", style = MaterialTheme.typography.titleMedium)
        InfoCard("Dispositivo", device.manufacturer + " " + device.model)
        InfoCard(
            "Android",
            device.androidVersion + " (API " + device.apiLevel + ") — " + status(report.androidSupported)
        )
        InfoCard(
            "Memoria RAM",
            formatBytes(device.totalRamBytes) + " total; " +
                formatBytes(device.availableRamBytes) + " disponible — " + status(report.ramSupported)
        )
        InfoCard("Arquitecturas compatibles", device.supportedAbis)
        InfoCard("Motor llama.cpp", nativeInfo)
        InfoCard("Clasificación de memoria", if (device.isLowRamDevice) "Android identifica este equipo como dispositivo de RAM limitada" else "Android no lo identifica como dispositivo de RAM limitada")
        InfoCard(
            "Almacenamiento interno",
            formatBytes(device.dataPartitionBytes) + " en partición de datos; " +
                formatBytes(device.availableDataPartitionBytes) + " disponibles. Capacidad comercial: por confirmar."
        )
        Spacer(modifier = Modifier.height(4.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(baselineLabel, style = MaterialTheme.typography.titleMedium)
                Text("Requisitos del proyecto: Android 14+, 4 GB de RAM y 64 GB de almacenamiento total.")
                Text("La partición de datos que reporta Android no equivale a la capacidad comercial anunciada del teléfono. Los 64 GB deben confirmarse en las especificaciones del dispositivo.")
                Text("Cumplir los requisitos de instalación no garantiza que Qwen3-4B pueda ejecutarse. Antes de cargarlo se comprobarán la memoria disponible, la arquitectura y el rendimiento.")
            }
        }
        Text(
            "Esta versión no descarga modelos ni envía datos del dispositivo.",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun InfoCard(title: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

private fun status(compatible: Boolean): String = if (compatible) "Cumple" else "No cumple"

private fun formatBytes(bytes: Long): String =
    String.format(Locale.getDefault(), "%.1f GiB", bytes / GIB)
