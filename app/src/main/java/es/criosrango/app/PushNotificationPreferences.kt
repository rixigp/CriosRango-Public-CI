package es.criosrango.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import es.criosrango.shared.StoreAccountColors

@Composable
fun PushNotificationPreferences() {
    val context = LocalContext.current
    val store = remember { PushPreferencesStore(context) }
    var newProducts by remember { mutableStateOf(store.newProducts) }
    var orderUpdates by remember { mutableStateOf(store.orderUpdates) }

    Column(Modifier.fillMaxWidth()) {
        Text("Notificaciones", style = MaterialTheme.typography.titleMedium)
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Novedades y nuevos productos")
                Text("Resumen diario de novedades.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
            colors = SwitchDefaults.colors(checkedThumbColor = StoreAccountColors.switchCheckedThumb, checkedTrackColor = StoreAccountColors.switchCheckedTrack, checkedBorderColor = StoreAccountColors.primary),
            checked = newProducts,
                onCheckedChange = { enabled ->
                    newProducts = enabled
                    store.newProducts = enabled
                    if (enabled) PushNotificationController.requestPermission(context)
                    PushNotificationController.syncPreferences(context)
                }
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text("Actualizaciones de pedidos")
                Text("Cambios relevantes de tus pedidos.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
            colors = SwitchDefaults.colors(checkedThumbColor = StoreAccountColors.switchCheckedThumb, checkedTrackColor = StoreAccountColors.switchCheckedTrack, checkedBorderColor = StoreAccountColors.primary),
            checked = orderUpdates,
                onCheckedChange = { enabled ->
                    orderUpdates = enabled
                    store.orderUpdates = enabled
                    if (enabled) PushNotificationController.requestPermission(context)
                    PushNotificationController.syncPreferences(context)
                }
            )
        }
    }
}
