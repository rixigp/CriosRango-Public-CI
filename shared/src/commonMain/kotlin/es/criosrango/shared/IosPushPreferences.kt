package es.criosrango.shared
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.criosrango.shared.StoreAccountColors

@Composable
fun IosPushPreferences(){
    var newProducts by remember { mutableStateOf(PushPreferencesPlatform.newProducts()) }
    var orderUpdates by remember { mutableStateOf(PushPreferencesPlatform.orderUpdates()) }
    Column(Modifier.fillMaxWidth().padding(vertical=4.dp)){
        Text("Notificaciones",style=MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.SpaceBetween){
            Column(Modifier.weight(1f)) { Text("Novedades y nuevos productos"); Text("Resumen diario de novedades.", style=MaterialTheme.typography.bodySmall) };Switch(newProducts,{newProducts=it;PushPreferencesPlatform.setNewProducts(it);if(it)PushPreferencesPlatform.requestPermission()}, colors = SwitchDefaults.colors(checkedThumbColor = StoreAccountColors.switchCheckedThumb, checkedTrackColor = StoreAccountColors.switchCheckedTrack, checkedBorderColor = StoreAccountColors.primary))
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            Column(Modifier.weight(1f)) { Text("Actualizaciones de pedidos"); Text("Cambios relevantes de tus pedidos.", style=MaterialTheme.typography.bodySmall) };Switch(orderUpdates,{orderUpdates=it;PushPreferencesPlatform.setOrderUpdates(it);if(it)PushPreferencesPlatform.requestPermission()}, colors = SwitchDefaults.colors(checkedThumbColor = StoreAccountColors.switchCheckedThumb, checkedTrackColor = StoreAccountColors.switchCheckedTrack, checkedBorderColor = StoreAccountColors.primary))
        }
    }
}