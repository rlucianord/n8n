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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale

private const val GIB = 1024.0 * 1024.0 * 1024.0
private const val MIN_RAM_BYTES = 4.0 * GIB

class MainActivity : ComponentActivity() {
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
            totalStorageBytes = storage.totalBytes,
            availableStorageBytes = storage.availableBytes
        )

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CompatibilityDashboard(snapshot)
                }
            }
        }
    }
}

private data class DeviceSnapshot(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val apiLevel: Int,
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val totalStorageBytes: Long,
    val availableStorageBytes: Long
) {
    val osCompatible: Boolean get() = apiLevel >= 34
    val ramCompatible: Boolean get() = totalRamBytes >= MIN_RAM_BYTES
    val meetsBaseline: Boolean get() = osCompatible && ramCompatible
}

@Composable
private fun CompatibilityDashboard(device: DeviceSnapshot) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Mi Doble", style = MaterialTheme.typography.headlineLarge)
        Text("Diagnóstico inicial del dispositivo", style = MaterialTheme.typography.titleMedium)
        InfoCard("Dispositivo", device.manufacturer + " " + device.model)
        InfoCard(
            "Android",
            device.androidVersion + " (API " + device.apiLevel + ") — " + status(device.osCompatible)
        )
        InfoCard(
            "Memoria RAM",
            formatBytes(device.totalRamBytes) + " total; " +
                formatBytes(device.availableRamBytes) + " disponible — " + status(device.ramCompatible)
        )
        InfoCard(
            "Partición de datos interna",
            formatBytes(device.totalStorageBytes) + " total; " +
                formatBytes(device.availableStorageBytes) + " disponible"
        )
        Spacer(modifier = Modifier.height(4.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (device.meetsBaseline) "Android y RAM: cumplen" else "Android o RAM: no cumplen",
                    style = MaterialTheme.typography.titleMedium
                )
                Text("Requisitos del proyecto: Android 14+, 4 GB de RAM y 64 GB de almacenamiento total.")
                Text("Nota: Android informa aquí el tamaño de la partición de datos, no la capacidad comercial exacta del teléfono. Confirma los 64 GB en las especificaciones del dispositivo.")
                Text(
                    "Esto no garantiza que Qwen3-4B pueda ejecutarse. Antes de cargarlo " +
                        "se comprobarán la memoria disponible, la arquitectura y el rendimiento."
                )
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
    String.format(Locale.getDefault(), "%.1f GB", bytes / GIB)
