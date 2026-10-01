package es.criosrango.shared
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
private fun IosPushPreferences(){
    var newProducts by remember { mutableStateOf(PushPreferencesPlatform.newProducts()) }
    var orderUpdates by remember { mutableStateOf(PushPreferencesPlatform.orderUpdates()) }
    Column(Modifier.fillMaxWidth().padding(vertical=4.dp)){
        Text("Notificaciones",style=MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().padding(top=8.dp),horizontalArrangement=Arrangement.SpaceBetween){
            Text("Novedades y nuevos productos",Modifier.weight(1f));Switch(newProducts,{newProducts=it;PushPreferencesPlatform.setNewProducts(it);if(it)PushPreferencesPlatform.requestPermission()})
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
            Text("Actualizaciones de pedidos",Modifier.weight(1f));Switch(orderUpdates,{orderUpdates=it;PushPreferencesPlatform.setOrderUpdates(it);if(it)PushPreferencesPlatform.requestPermission()})
        }
    }
}