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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun IosPushPreferences() {
    val coordinator = IosPushPreferenceSyncRuntime.coordinator
    var syncState by remember { mutableStateOf(coordinator.state) }
    DisposableEffect(coordinator) {
        val unsubscribe = coordinator.observe { syncState = it }
        onDispose { unsubscribe() }
    }

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
    }
}
