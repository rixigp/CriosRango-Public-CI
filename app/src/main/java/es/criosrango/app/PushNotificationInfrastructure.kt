package es.criosrango.app

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PushPreferencesStore(context: Context) {
    private val prefs=context.getSharedPreferences("criosrango_push_preferences",Context.MODE_PRIVATE)
    var newProducts: Boolean
        get() = prefs.getBoolean("new_products", true)
        set(value) {
            prefs.edit().putBoolean("new_products", value).apply()
        }
    var orderUpdates: Boolean
        get() = prefs.getBoolean("order_updates", true)
        set(value) {
            prefs.edit().putBoolean("order_updates", value).apply()
        }
}

object PushNotificationController {
    private const val ENDPOINT="https://criosrango.es/wp-json/criosrango/v1/push/device"
    private const val SESSION="criosrango_account_session_v2"
    fun initialize(context:Context){createChannels(context);FirebaseMessaging.getInstance().token.addOnSuccessListener{register(context,it)}}
    fun requestPermission(context:Context){if(Build.VERSION.SDK_INT>=33&&context is Activity&&!notificationsAllowed(context))ActivityCompat.requestPermissions(context,arrayOf(Manifest.permission.POST_NOTIFICATIONS),4101)}
    fun register(context:Context,token:String){
        if(!notificationsAllowed(context))return
        context.getSharedPreferences(SESSION,Context.MODE_PRIVATE).edit().putString("criosrango_fcm_token",token).apply()
        val prefs=PushPreferencesStore(context)
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch{runCatching{
            val client=okhttp3.OkHttpClient()
            val json=org.json.JSONObject().put("platform","android").put("token",token).put("new_products",prefs.newProducts).put("order_updates",prefs.orderUpdates)
            val body=okhttp3.RequestBody.create(okhttp3.MediaType.get("application/json"),json.toString())
            val builder=okhttp3.Request.Builder().url(ENDPOINT).post(body)
            context.getSharedPreferences(SESSION,Context.MODE_PRIVATE).getString("account_token",null)?.let{builder.header("Authorization","Bearer $it")}
            client.newCall(builder.build()).execute().close()
        }}
    }
    fun unregister(context:Context){
        val prefs=context.getSharedPreferences(SESSION,Context.MODE_PRIVATE)
        val token=prefs.getString("criosrango_fcm_token",null)?:return
        CoroutineScope(SupervisorJob()+Dispatchers.IO).launch{runCatching{
            val json=org.json.JSONObject().put("platform","android").put("token",token)
            val body=okhttp3.RequestBody.create(okhttp3.MediaType.get("application/json"),json.toString())
            val b=okhttp3.Request.Builder().url(ENDPOINT).delete(body)
            prefs.getString("account_token",null)?.let{b.header("Authorization","Bearer $it")}
            okhttp3.OkHttpClient().newCall(b.build()).execute().close()
        }}
    }
    fun createChannels(context:Context){if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)context.getSystemService(NotificationManager::class.java).createNotificationChannels(listOf(NotificationChannel("criosrango_general","Críos&Rango",NotificationManager.IMPORTANCE_DEFAULT),NotificationChannel("criosrango_orders","Pedidos",NotificationManager.IMPORTANCE_DEFAULT)))}
    fun notificationsAllowed(context:Context)=Build.VERSION.SDK_INT<33||ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
}

class PushFirebaseMessagingService:FirebaseMessagingService(){
    override fun onCreate(){super.onCreate();PushNotificationController.initialize(this)}
    override fun onNewToken(token:String){PushNotificationController.register(this,token)}
    override fun onMessageReceived(message:RemoteMessage){
        val type=message.data["type"]?:return
        val orderId=message.data["order_id"]?.toIntOrNull()
        val title=message.notification?.title?:message.data["title"]?:"Críos&Rango"
        val body=message.notification?.body?:message.data["body"]?:return
        if(!PushNotificationController.notificationsAllowed(this))return
        val intent=Intent(this,MainActivity::class.java).apply{putExtra("push_type",type);orderId?.let{putExtra("order_id",it)};flags=Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP}
        val pending=PendingIntent.getActivity(this,type.hashCode()*31+(orderId?:0),intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val channel=if(type=="order_status")"criosrango_orders" else "criosrango_general"
        val n=NotificationCompat.Builder(this,channel).setSmallIcon(R.drawable.ic_stat_notification).setContentTitle(title).setContentText(body).setAutoCancel(true).setContentIntent(pending).setPriority(NotificationCompat.PRIORITY_DEFAULT).build()
        getSystemService(NotificationManager::class.java).notify((System.currentTimeMillis() and 0x7fffffff).toInt(),n)
    }
}