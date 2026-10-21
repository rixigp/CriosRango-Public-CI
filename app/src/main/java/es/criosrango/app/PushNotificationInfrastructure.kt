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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
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
        .put("identifier_type", "fcm_token")
        .put("identifier", identifier)
        .put("new_products", newProducts)
        .put("order_updates", orderUpdates)
}

internal fun pushRegistrationPayload(
    identifier: String,
    newProducts: Boolean,
    orderUpdates: Boolean
): PushRegistrationPayload = PushRegistrationPayload(identifier, newProducts, orderUpdates)

internal fun shouldDeactivatePreviousToken(previousToken: String?, currentToken: String): Boolean =
    !previousToken.isNullOrBlank() && previousToken != currentToken

internal fun pendingUnregisterTokensAfterAttempt(pendingTokens: Set<String>, token: String, unregisterSucceeded: Boolean): Set<String> =
    pendingTokens.toMutableSet().apply { if (unregisterSucceeded) remove(token) else add(token) }

internal enum class PushRegistrationAction { REGISTER_NEW, UNREGISTER_OLD }

internal fun pushRegistrationActions(previousToken: String?, currentToken: String, registrationSucceeded: Boolean): List<PushRegistrationAction> {
    if (!registrationSucceeded) return listOf(PushRegistrationAction.REGISTER_NEW)
    return buildList {
        add(PushRegistrationAction.REGISTER_NEW)
        if (shouldDeactivatePreviousToken(previousToken, currentToken)) add(PushRegistrationAction.UNREGISTER_OLD)
    }
}

object PushNotificationController {
    private const val ENDPOINT = "https://criosrango.es/wp-json/criosrango/v1/push/device"
    private const val SESSION = "criosrango_account_session_v2"
    private const val IDENTIFIER_TYPE_KEY = "criosrango_push_identifier_type"
    private const val IDENTIFIER_KEY = "criosrango_push_identifier"
    private const val PENDING_UNREGISTER_KEY = "criosrango_push_pending_unregister_tokens"
    private const val PREFERENCES_SYNC_PENDING_KEY = "criosrango_push_preferences_sync_pending"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val httpClient = OkHttpClient.Builder().callTimeout(4, TimeUnit.SECONDS)
        .connectTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS).writeTimeout(2, TimeUnit.SECONDS).build()

    fun initialize(context: Context) {
        createChannels(context)
        // Product preferences are independent from the OS notification permission.
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token -> onRegistered(context, token) }
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
        context.applicationContext.getSharedPreferences(SESSION, Context.MODE_PRIVATE)
            .edit().putBoolean(PREFERENCES_SYNC_PENDING_KEY, true).apply()
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token -> onRegistered(context, token) }
    }

    fun onRegistered(context: Context, token: String) {
        if (token.isBlank()) return
        scope.launch { syncMutex.withLock { syncRegisteredToken(context.applicationContext, token) } }
    }

    /** Bounded best-effort deassociation; retain token and pending marker on failure. */
    suspend fun unregisterAwait(context: Context): Boolean {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(SESSION, Context.MODE_PRIVATE)
        val token = prefs.getString(IDENTIFIER_KEY, null)?.takeIf { it.isNotBlank() } ?: return true
        // Persist recovery intent before waiting for the registration mutex, so lock timeout cannot lose it.
        val pending = pendingUnregisterTokensAfterAttempt(
            prefs.getStringSet(PENDING_UNREGISTER_KEY, emptySet()).orEmpty(), token, unregisterSucceeded = false
        )
        prefs.edit().putStringSet(PENDING_UNREGISTER_KEY, pending).apply()
        return withTimeoutOrNull(4_500L) {
            syncMutex.withLock {
                val removed = withContext(Dispatchers.IO) { deactivateToken(token, prefs.getString("account_token", null)) }
                val updatedPending = pendingUnregisterTokensAfterAttempt(
                    prefs.getStringSet(PENDING_UNREGISTER_KEY, emptySet()).orEmpty(), token, unregisterSucceeded = removed
                )
                prefs.edit().putStringSet(PENDING_UNREGISTER_KEY, updatedPending).apply()
                removed
            }
        } ?: false
    }

    internal fun buildUnregisterPayload(identifier: String): JSONObject = JSONObject()
        .put("platform", "android").put("identifier_type", "fcm_token").put("identifier", identifier)

    private fun syncRegisteredToken(context: Context, token: String) {
        val prefs = context.getSharedPreferences(SESSION, Context.MODE_PRIVATE)
        val previousToken = prefs.getString(IDENTIFIER_KEY, null)
        val accountToken = prefs.getString("account_token", null)
        val pending = prefs.getStringSet(PENDING_UNREGISTER_KEY, emptySet()).orEmpty().toMutableSet()
        val pushPrefs = PushPreferencesStore(context)
        val payload = pushRegistrationPayload(token, pushPrefs.newProducts, pushPrefs.orderUpdates)
        if (!postRegistration(payload, accountToken)) {
            prefs.edit().putBoolean(PREFERENCES_SYNC_PENDING_KEY, true).apply()
            // A pending logout must still detach the previous account even when the new registration fails.
            pending.toList().forEach { stale -> if (deactivateToken(stale, accountToken)) pending.remove(stale) }
            prefs.edit().putStringSet(PENDING_UNREGISTER_KEY, pending).apply()
            return
        }
        // Register/reassign first; only then retire old identities.
        pending.filter { it != token }.forEach { stale -> if (deactivateToken(stale, accountToken)) pending.remove(stale) }
        if (previousToken != null && previousToken != token && !deactivateToken(previousToken, accountToken)) pending.add(previousToken)
        pending.remove(token)
        prefs.edit().putString(IDENTIFIER_TYPE_KEY, "fcm_token").putString(IDENTIFIER_KEY, token)
            .putStringSet(PENDING_UNREGISTER_KEY, pending).remove(PREFERENCES_SYNC_PENDING_KEY).apply()
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

    private fun deactivateToken(token: String, accountToken: String?): Boolean {
        if (token.isBlank()) return false
        val request = Request.Builder().url(ENDPOINT)
            .delete(buildUnregisterPayload(token).toString().toRequestBody("application/json".toMediaType()))
            .apply { accountToken?.let { header("Authorization", "Bearer $it") } }.build()
        return runCatching { httpClient.newCall(request).execute().use { it.isSuccessful } }.getOrDefault(false)
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

    override fun onNewToken(token: String) {
        PushNotificationController.onRegistered(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val type = message.data[PushNotificationContract.TYPE_KEY] ?: return
        val payload = PushNotificationContract.parsePayload(
            type = type,
            orderId = message.data[PushNotificationContract.ORDER_ID_KEY],
            notificationTitle = message.notification?.title,
            notificationBody = message.notification?.body,
            dataTitle = message.data[PushNotificationContract.TITLE_KEY],
            dataBody = message.data[PushNotificationContract.BODY_KEY]
        )
        val orderId = payload.orderId?.toString()
        val title = payload.title ?: PushNotificationContract.GENERIC_TITLE
        val body = payload.body ?: PushNotificationContract.GENERIC_BODY
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
