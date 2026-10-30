package com.midoble.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BackupRestoreScreen(
    includeModels: Boolean,
    onIncludeModels: (Boolean) -> Unit,
    status: String,
    onCreateBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Respaldo y restauración", style = MaterialTheme.typography.headlineMedium)
        Text("Antes de desinstalar o cambiar de teléfono, crea un archivo de respaldo y guárdalo en Descargas, Google Drive o donde prefieras.")
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Qué se guarda", style = MaterialTheme.typography.titleMedium)
                Text("Perfil, preferencias, modelo seleccionado, voz, SIM preferida y lista de números bloqueados.")
                Button(onClick = { onIncludeModels(!includeModels) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (includeModels) "✓ Incluir modelos descargados" else "Incluir modelos descargados")
                }
                Text(if (includeModels) "El archivo puede ser grande porque incluye los modelos locales." else "El respaldo será más pequeño; los modelos se podrán descargar otra vez.")
                Button(onClick = onCreateBackup, modifier = Modifier.fillMaxWidth()) { Text("Crear respaldo") }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Restaurar después de instalar", style = MaterialTheme.typography.titleMedium)
                Text("Selecciona el archivo ZIP que guardaste. Mi Doble recuperará la configuración y los modelos incluidos.")
                Button(onClick = onRestoreBackup, modifier = Modifier.fillMaxWidth()) { Text("Restaurar respaldo") }
            }
        }
        if (status.isNotBlank()) Text(status, style = MaterialTheme.typography.bodyMedium)
        Text("Importante: Android no permite que una aplicación intercepte la pantalla del sistema al desinstalarse. Por eso el respaldo se realiza desde aquí, antes de desinstalar. No borres la aplicación hasta confirmar que el archivo ZIP está guardado.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }
}
