package es.criosrango.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun IosPushPreferences() {
    var newsEnabled by remember { mutableStateOf(PushPreferencesPlatform.newProducts()) }
    var orderUpdatesEnabled by remember { mutableStateOf(PushPreferencesPlatform.orderUpdates()) }
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    val diagnostic = IosPushDiagnostics.snapshot

    Column(Modifier.fillMaxWidth()) {
    StorePushPreferencesScreen(
        newsEnabled = newsEnabled,
        orderUpdatesEnabled = orderUpdatesEnabled,
        onNewsChanged = { enabled ->
            newsEnabled = enabled
            PushPreferencesPlatform.setNewProducts(enabled)
            if (enabled) PushPreferencesPlatform.requestPermission()
            scope.launch { runCatching { PushDeviceRegistration.registerCurrentDevice() } }
        },
        onOrderUpdatesChanged = { enabled ->
            orderUpdatesEnabled = enabled
            PushPreferencesPlatform.setOrderUpdates(enabled)
            if (enabled) PushPreferencesPlatform.requestPermission()
            scope.launch { runCatching { PushDeviceRegistration.registerCurrentDevice() } }
        }
    )
    Spacer(Modifier.height(12.dp))
    Text("Diagnóstico técnico de notificaciones", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(6.dp))
    Text("Permiso notificaciones: ${diagnostic.permission}")
    Text("Registro APNs solicitado: ${if (diagnostic.registrationRequested) "SÍ" else "NO"}")
    Text("Token APNs disponible: ${if (diagnostic.tokenAvailable) "SÍ" else "NO"}")
    Text("didRegister recibido: ${if (diagnostic.didRegisterReceived) "SÍ" else "NO"}")
    Text("POST intentado: ${if (diagnostic.postAttempted) "SÍ" else "NO"}")
    Text("Resultado del registro: ${diagnostic.registrationResult}")
    Text("Registro autenticado: ${diagnostic.authenticated}")
    Text("Registro aceptado: ${diagnostic.accepted}")
    Text("Sincronización pendiente: ${if (diagnostic.pendingSync) "SÍ" else "NO"}")
    Text("new_products enviado: ${diagnostic.newProductsSent}")
    Text("order_updates enviado: ${diagnostic.orderUpdatesSent}")
    if (diagnostic.installationHash.isNotBlank()) Text("Instalación: ${diagnostic.installationHash}")
    if (diagnostic.errorClass.isNotBlank()) Text("APNS_ERROR_CLASS=${diagnostic.errorClass}", color = MaterialTheme.colorScheme.error)
    if (diagnostic.errorMessage.isNotBlank()) Text("APNS_ERROR_MESSAGE=${diagnostic.errorMessage}", color = MaterialTheme.colorScheme.error)
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            checking = true
            PushPreferencesPlatform.requestPermission()
            scope.launch {
                runCatching { PushDeviceRegistration.registerCurrentDevice() }
                    .onFailure { IosPushDiagnostics.recordPostResult("ERROR ${it::class.simpleName ?: "Exception"}", "AÚN SIN DATO", true, it::class.simpleName ?: "Exception", it.message.orEmpty()) }
                checking = false
            }
        },
        enabled = !checking,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (checking) CircularProgressIndicator()
        else Text("Comprobar registro ahora")
    }
    }
}
