package es.criosrango.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun IosPushPreferences() {
    val coordinator = IosPushPreferenceSyncRuntime.coordinator
    var syncState by remember { mutableStateOf(coordinator.state) }
    var checking by remember { mutableStateOf(false) }
    DisposableEffect(coordinator) {
        val unsubscribe = coordinator.observe { syncState = it }
        onDispose { unsubscribe() }
    }
    val diagnostic = IosPushDiagnostics.snapshot

    Column(Modifier.fillMaxWidth()) {
        StorePushPreferencesScreen(
            newsEnabled = syncState.desired.newProducts,
            orderUpdatesEnabled = syncState.desired.orderUpdates,
            onNewsChanged = { enabled ->
                coordinator.setNewProducts(enabled)
                if (enabled) PushPreferencesPlatform.requestPermission()
                IosPushPreferenceSyncRuntime.synchronize()
            },
            onOrderUpdatesChanged = { enabled ->
                coordinator.setOrderUpdates(enabled)
                if (enabled) PushPreferencesPlatform.requestPermission()
                IosPushPreferenceSyncRuntime.synchronize()
            }
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Confirmado por servidor: Novedades " +
                (if (syncState.confirmed.newProducts) "ACTIVADO" else "DESACTIVADO") +
                " · Pedidos " + (if (syncState.confirmed.orderUpdates) "ACTIVADO" else "DESACTIVADO"),
            style = MaterialTheme.typography.bodySmall
        )
        if (syncState.syncPending || syncState.syncInProgress || syncState.error) {
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    syncState.error -> "Error de sincronización. El estado confirmado se conserva; hay cambios pendientes."
                    syncState.syncInProgress -> "Sincronizando preferencias…"
                    else -> "Cambios pendientes de confirmación del servidor."
                },
                color = if (syncState.error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(Modifier.height(12.dp))
        Text("Diagnóstico técnico de notificaciones", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text("Permiso notificaciones: ${diagnostic.permission}")
        Text("Registro APNs solicitado: ${if (diagnostic.registrationRequested) "SÍ" else "NO"}")
        Text("Token APNs disponible: ${if (diagnostic.tokenAvailable) "SÍ" else "NO"}")
        Text("didRegister recibido: ${if (diagnostic.didRegisterReceived) "SÍ" else "NO"}")
        Text("POST intentado: ${if (diagnostic.postAttempted) "SÍ" else "NO"}")
        Text("Resultado del registro: ${diagnostic.registrationResult}")
        Text("HTTP status real: ${diagnostic.httpStatus?.toString() ?: "SIN RESPUESTA HTTP"}")
        if (diagnostic.responseBody.isNotBlank()) Text("HTTP response body: ${diagnostic.responseBody}")
        Text("Registro autenticado: ${diagnostic.authenticated}")
        Text("Registro aceptado: ${diagnostic.accepted}")
        Text("Sincronización pendiente: ${if (diagnostic.pendingSync || syncState.syncPending) "SÍ" else "NO"}")
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
                IosPushPreferenceSyncRuntime.synchronize(force = true)
                checking = false
            },
            enabled = !checking && !syncState.syncInProgress,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (checking || syncState.syncInProgress) CircularProgressIndicator()
            else Text("Comprobar registro ahora")
        }
    }
}
