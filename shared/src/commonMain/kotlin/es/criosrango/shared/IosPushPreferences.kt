package es.criosrango.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

@Composable
fun IosPushPreferences() {
    var newsEnabled by remember { mutableStateOf(PushPreferencesPlatform.newProducts()) }
    var orderUpdatesEnabled by remember { mutableStateOf(PushPreferencesPlatform.orderUpdates()) }
    val scope = rememberCoroutineScope()

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
}
