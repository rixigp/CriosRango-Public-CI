package es.criosrango.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier

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
    }
}
