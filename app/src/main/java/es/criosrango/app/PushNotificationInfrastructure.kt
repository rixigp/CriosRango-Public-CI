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
import es.criosrango.shared.PushNotificationContract
import es.criosrango.shared.PushNotificationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class PushPreferencesStore(context: Context) {
    private val prefs = context.getSharedPreferences("criosrango_push_preferences", Context.MODE_PRIVATE)

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

internal data class PushRegistrationPayload(
    val identifier: String,
    val newProducts: Boolean,
    val orderUpdates: Boolean
) {
    fun toJson(): JSONObject = JSONObject()
        .put("platform", "android")
        .put("identifier_type", "fid")
        .put("identifier", identifier)
        .put("new_products", newProducts)
        .put("order_updates", orderUpdates)
}

internal fun pushRegistrationPayload(
    identifier: String,
    newProducts: Boolean,
    orderUpdates: Boolean
): PushRegistrationPayload = PushRegistrationPayload(identifier, newProducts, orderUpdates)

internal fun shouldDeactivatePreviousFid(previousFid: String?, currentFid: String): Boolean =
    !previousFid.isNullOrBlank() && previousFid != currentFid

internal enum class PushRegistrationAction {
    REGISTER_NEW,
    UNREGISTER_OLD
}

internal fun pushRegistrationActions(
    previousFid: String?,
    currentFid: String,
    registrationSucceeded: Boolean
): List<PushRegistrationAction> {
    if (!registrationSucceeded) return listOf(PushRegistrationAction.REGISTER_NEW)
    return buildList {
        add(PushRegistrationAction.REGISTER_NEW)
        if (shouldDeactivatePreviousFid(previousFid, currentFid)) {
            add(PushRegistrationAction.UNREGISTER_OLD)
        }
    }
}

object PushNotificationController {
    private const val ENDPOINT = "https://criosrango.es/wp-json/criosrango/v1/push/device"
    private const val SESSION = "criosrango_account_session_v2"
    private const val IDENTIFIER_TYPE_KEY = "criosrango_push_identifier_type"
    private const val IDENTIFIER_KEY = "criosrango_push_identifier"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val httpClient = OkHttpClient()

    fun initialize(context: Context) {
        createChannels(context)
        if (!notificationsAllowed(context)) return
        FirebaseMessaging.getInstance().register()
    }

    fun requestPermission(context: Context) {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            context is Activity &&
            !notificationsAllowed(context)
        ) {
            ActivityCompat.requestPermissions(
                context,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                4101
            )
        }
    }

    fun syncPreferences(context: Context) {
        initialize(context)
    }

    fun onRegistered(context: Context, installationId: String) {
        if (installationId.isBlank() || !notificationsAllowed(context)) return
        scope.launch {
            syncMutex.withLock {
                syncRegisteredFid(context.applicationContext, installationId)
            }
        }
    }

    fun onUnregistered(context: Context, installationId: String) {
        if (installationId.isBlank()) return
        val applicationContext = context.applicationContext
        scope.launch {
            syncMutex.withLock {
                val preferences = applicationContext.getSharedPreferences(SESSION, Context.MODE_PRIVATE)
                val accountToken = preferences.getString("account_token", null)
                if (deactivateFid(installationId, accountToken)) {
                    if (preferences.getString(IDENTIFIER_KEY, null) == installationId) {
                        preferences.edit()
                            .remove(IDENTIFIER_TYPE_KEY)
                            .remove(IDENTIFIER_KEY)
                            .apply()
                    }
                }
            }
        }
    }

    fun unregister(context: Context) {
        val applicationContext = context.applicationContext
        val preferences = applicationContext.getSharedPreferences(SESSION, Context.MODE_PRIVATE)
        val fid = preferences.getString(IDENTIFIER_KEY, null)?.takeIf { it.isNotBlank() } ?: return
        val accountToken = preferences.getString("account_token", null)
        scope.launch {
            syncMutex.withLock {
                if (deactivateFid(fid, accountToken)) {
                    preferences.edit()
                        .remove(IDENTIFIER_TYPE_KEY)
                        .remove(IDENTIFIER_KEY)
                        .apply()
                }
            }
        }
    }

    internal fun buildUnregisterPayload(identifier: String): JSONObject =
        JSONObject()
            .put("platform", "android")
            .put("identifier_type", "fid")
            .put("identifier", identifier)

    private fun syncRegisteredFid(context: Context, fid: String) {
        val preferences = context.getSharedPreferences(SESSION, Context.MODE_PRIVATE)
        val previousFid = preferences.getString(IDENTIFIER_KEY, null)
        val accountToken = preferences.getString("account_token", null)
        val pushPreferences = PushPreferencesStore(context)
        val payload = pushRegistrationPayload(
            identifier = fid,
            newProducts = pushPreferences.newProducts,
            orderUpdates = pushPreferences.orderUpdates
        )

        if (!postRegistration(payload, accountToken)) return

        if (shouldDeactivatePreviousFid(previousFid, fid)) {
            deactivateFid(previousFid!!, accountToken)
        }

        preferences.edit()
            .putString(IDENTIFIER_TYPE_KEY, "fid")
            .putString(IDENTIFIER_KEY, fid)
            .apply()
    }

    private fun postRegistration(
        payload: PushRegistrationPayload,
        accountToken: String?
    ): Boolean {
        val request = Request.Builder()
            .url(ENDPOINT)
            .post(payload.toJson().toString().toRequestBody("application/json".toMediaType()))
            .apply {
                accountToken?.let { header("Authorization", "Bearer $it") }
            }
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        }.getOrDefault(false)
    }

    private fun deactivateFid(
        fid: String,
        accountToken: String?
    ): Boolean {
        if (fid.isBlank()) return false

        val request = Request.Builder()
            .url(ENDPOINT)
            .delete(buildUnregisterPayload(fid).toString().toRequestBody("application/json".toMediaType()))
            .apply {
                accountToken?.let { header("Authorization", "Bearer $it") }
            }
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        }.getOrDefault(false)
    }

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java).createNotificationChannels(
                listOf(
                    NotificationChannel(
                        "criosrango_general",
                        "Críos&Rango",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ),
                    NotificationChannel(
                        "criosrango_orders",
                        "Pedidos",
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
            )
        }
    }

    fun notificationsAllowed(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
}

class PushFirebaseMessagingService : FirebaseMessagingService() {
    override fun onCreate() {
        super.onCreate()
        PushNotificationController.createChannels(this)
    }

    override fun onRegistered(installationId: String) {
        PushNotificationController.onRegistered(this, installationId)
    }

    override fun onUnregistered(installationId: String) {
        PushNotificationController.onUnregistered(this, installationId)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data[PushNotificationContract.TYPE_KEY] ?: return
        val orderId = message.data[PushNotificationContract.ORDER_ID_KEY]
        val title = if (type == PushNotificationType.BIRTHDAY_COUPON) {
            "🎂 ¡Feliz cumpleaños!"
        } else {
            message.data["title"] ?: "Críos&Rango"
        }
        val body = if (type == PushNotificationType.BIRTHDAY_COUPON) {
            "Tienes un 15% de descuento por tu cumpleaños. Disponible durante 15 días."
        } else {
            message.data["body"] ?: "Tienes una nueva notificación"
        }
        val channel = if (type == PushNotificationType.ORDER_STATUS) {
            "criosrango_orders"
        } else {
            "criosrango_general"
        }
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(PushNotificationContract.TYPE_KEY, type)
            orderId?.let { putExtra(PushNotificationContract.ORDER_ID_KEY, it) }
        }
        val pending = PendingIntent.getActivity(
            this,
            orderId?.hashCode() ?: type.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(es.criosrango.app.R.drawable.ic_stat_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        if (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            androidx.core.app.NotificationManagerCompat.from(this)
                .notify(orderId?.hashCode() ?: type.hashCode(), notification)
        }
    }
}
