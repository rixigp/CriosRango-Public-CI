package es.criosrango.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import es.criosrango.shared.StorePushPreferencesScreen

@Composable
fun PushNotificationPreferences() {
    val context = LocalContext.current
    val store = remember { PushPreferencesStore(context) }
    var newsEnabled by remember { mutableStateOf(store.newProducts) }
    var orderUpdatesEnabled by remember { mutableStateOf(store.orderUpdates) }

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
}
