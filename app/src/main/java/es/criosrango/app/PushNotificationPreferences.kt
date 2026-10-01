package es.criosrango.app
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun PushNotificationPreferences(){
    val context=LocalContext.current
    val store=remember{PushPreferencesStore(context)}
    fun sync(){FirebaseMessagingCompat.sync(context)}
    Column(Modifier.fillMaxWidth()){
        Text("Notificaciones",style=MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().padding(top=10.dp),horizontalArrangement=Arrangement.SpaceBetween){
            Column(Modifier.weight(1f)){Text("Novedades y nuevos productos");Text("Resumen diario de novedades.",style=MaterialTheme.typography.bodySmall)}
            Switch(checked=store.newProducts,onCheckedChange={store.newProducts=it;if(it)PushNotificationController.requestPermission(context);sync()})
        }
        Row(Modifier.fillMaxWidth().padding(top=10.dp),horizontalArrangement=Arrangement.SpaceBetween){
            Column(Modifier.weight(1f)){Text("Actualizaciones de pedidos");Text("Cambios relevantes de tus pedidos.",style=MaterialTheme.typography.bodySmall)}
            Switch(checked=store.orderUpdates,onCheckedChange={store.orderUpdates=it;if(it)PushNotificationController.requestPermission(context);sync()})
        }
    }
}
private object FirebaseMessagingCompat{
    fun sync(context:android.content.Context){com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnSuccessListener{PushNotificationController.register(context,it)}}
}