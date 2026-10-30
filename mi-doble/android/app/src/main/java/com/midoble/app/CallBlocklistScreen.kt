package com.midoble.app

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CallBlocklistScreen(
    numbers: List<String>,
    screeningEnabled: Boolean,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onEnableScreening: () -> Unit,
    onBack: () -> Unit
) {
    val input = remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Números bloqueados", style = MaterialTheme.typography.headlineMedium)
        Text("Solo los números que agregues aquí serán rechazados automáticamente. Todos los demás podrán entrar normalmente.")
        OutlinedTextField(value = input.value, onValueChange = { input.value = it },
            modifier = Modifier.fillMaxWidth(), label = { Text("Número de teléfono") },
            placeholder = { Text("+1 809 000 0000") }, singleLine = true)
        Button(onClick = { onAdd(input.value); input.value = "" },
            enabled = input.value.count { it.isDigit() } in 7..15,
            modifier = Modifier.fillMaxWidth()) { Text("Agregar a bloqueados") }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (screeningEnabled) "Bloqueo automático habilitado" else "Falta activar el filtrado de llamadas",
                    style = MaterialTheme.typography.titleMedium)
                Text("Android solicitará permiso para que Mi Doble filtre llamadas. Si autorizas el acceso a contactos, también podrá aplicar tu lista a números guardados en tu agenda.")
                if (!screeningEnabled) Button(onClick = onEnableScreening, modifier = Modifier.fillMaxWidth()) {
                    Text("Activar bloqueo automático")
                }
            }
        }
        Text("Tu lista (" + numbers.size + ")", style = MaterialTheme.typography.titleMedium)
        if (numbers.isEmpty()) Text("Todavía no has agregado números.")
        numbers.forEach { number ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(number, modifier = Modifier.weight(1f))
                    Button(onClick = { onRemove(number) }) { Text("Quitar") }
                }
            }
        }
        Text("El bloqueo depende de que Android permita a Mi Doble actuar como identificador y filtro de llamadas. La respuesta de voz personalizada para números bloqueados todavía requiere una integración telefónica adicional.",
            style = MaterialTheme.typography.bodySmall)
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Volver") }
    }
}
