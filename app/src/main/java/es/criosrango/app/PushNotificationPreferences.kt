package es.criosrango.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import es.criosrango.shared.StorePushPreferencesScreen

@Composable
fun PushNotificationPreferences() {
    val context = LocalContext.current
    val store = remember { PushPreferencesStore(context) }
    var newsEnabled by remember { mutableStateOf(store.newProducts) }
    var orderUpdatesEnabled by remember { mutableStateOf(store.orderUpdates) }
    val diagnostics by PushNotificationController.diagnostics.collectAsState()

    LaunchedEffect(Unit) {
        PushNotificationController.loadDiagnostics(context)
    }

    Column(Modifier.fillMaxWidth()) {
        StorePushPreferencesScreen(
            newsEnabled = newsEnabled,
            orderUpdatesEnabled = orderUpdatesEnabled,
            onNewsChanged = { enabled ->
                newsEnabled = enabled
                store.newProducts = enabled
                if (enabled) PushNotificationController.requestPermission(context)
                PushNotificationController.syncPreferences(context)
            },
            onOrderUpdatesChanged = { enabled ->
                orderUpdatesEnabled = enabled
                store.orderUpdates = enabled
                if (enabled) PushNotificationController.requestPermission(context)
                PushNotificationController.syncPreferences(context)
            }
        )
        Column(Modifier.fillMaxWidth().padding(top = 20.dp)) {
            Text("Diagnóstico técnico de notificaciones", style = MaterialTheme.typography.titleSmall)
            Text("Token FCM disponible: ${diagnostics.tokenAvailable.label()}", style = MaterialTheme.typography.bodySmall)
            Text("POST intentado: ${diagnostics.registerAttempted.label()}", style = MaterialTheme.typography.bodySmall)
            Text("Resultado del registro: ${diagnostics.httpResult}", style = MaterialTheme.typography.bodySmall)
            diagnostics.tokenErrorClass?.let { Text("TOKEN_ERROR_CLASS=$it", style = MaterialTheme.typography.bodySmall) }
            diagnostics.tokenErrorMessage?.let { Text("TOKEN_ERROR_MESSAGE=$it", style = MaterialTheme.typography.bodySmall) }
            Text("Registro autenticado: ${diagnostics.authenticated.label()}", style = MaterialTheme.typography.bodySmall)
            Text("Registro aceptado: ${diagnostics.registerSuccess.label()}", style = MaterialTheme.typography.bodySmall)
            Text("Sincronización pendiente: ${diagnostics.syncPending.label()}", style = MaterialTheme.typography.bodySmall)
            Text("order_updates enviado: ${diagnostics.orderUpdatesSent.label()}", style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = { PushNotificationController.syncRegistration(context) },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Comprobar registro ahora")
            }
        }
    }
}

private fun Boolean?.label(): String = when (this) {
    true -> "SÍ"
    false -> "NO"
    null -> "AÚN SIN DATO"
}
