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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

internal fun safePushTokenErrorMessage(message: String?): String {
    val normalized = message.orEmpty().replace(Regex("[\\r\\n\\t]+"), " ").trim()
    if (normalized.isBlank()) return "(sin mensaje)"
    return normalized
        .replace(Regex("(?i)Bearer\\s+[^\\s,;]+"), "Bearer [REDACTED]")
        .replace(Regex("(?i)(token|api[_-]?key|secret|password)=([^\\s&]+)")) { match ->
            "${match.groupValues[1]}=[REDACTED]"
        }
        .replace(Regex("AIza[0-9A-Za-z_-]{30,}"), "[REDACTED]")
        .replace(Regex("[A-Za-z0-9_\\-]{80,}"), "[REDACTED]")
        .take(240)
}

data class PushRegistrationDiagnostics(
    val tokenAvailable: Boolean? = null,
    val registerAttempted: Boolean? = null,
    val httpResult: String = "Sin intento registrado",
    val tokenErrorClass: String? = null,
    val tokenErrorMessage: String? = null,
    val authenticated: Boolean? = null,
    val registerSuccess: Boolean? = null,
    val syncPending: Boolean = true,
    val orderUpdatesSent: Boolean? = null
)

internal fun pushAuthorizationHeader(accountToken: String?): String? =
    accountToken?.takeIf { it.isNotBlank() }?.let { "Bearer $it" }

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
    private const val TAG = "CriosRangoPush"
    private const val DIAG_TOKEN = "criosrango_push_diag_token_available"
    private const val DIAG_ATTEMPTED = "criosrango_push_diag_register_attempted"
    private const val DIAG_HTTP = "criosrango_push_diag_http_result"
    private const val DIAG_TOKEN_ERROR_CLASS = "criosrango_push_diag_token_error_class"
    private const val DIAG_TOKEN_ERROR_MESSAGE = "criosrango_push_diag_token_error_message"
    private const val DIAG_AUTH = "criosrango_push_diag_authenticated"
    private const val DIAG_SUCCESS = "criosrango_push_diag_register_success"
    private const val DIAG_PENDING = "criosrango_push_diag_sync_pending"
    private const val DIAG_ORDER_UPDATES = "criosrango_push_diag_order_updates_sent"

    private val _diagnostics = MutableStateFlow(PushRegistrationDiagnostics())
    val diagnostics: StateFlow<PushRegistrationDiagnostics> = _diagnostics.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()
    private val httpClient = OkHttpClient.Builder().callTimeout(4, TimeUnit.SECONDS)
        .connectTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS).writeTimeout(2, TimeUnit.SECONDS).build()

    fun initialize(context: Context) {
        createChannels(context)
        // onResume is a bounded retry opportunity for any previously failed registration.
        syncRegistration(context)
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

    fun syncPreferences(context: Context) = syncRegistration(context)

    /** Explicit retry hook for login success, session restore, resume and preference changes. */
    fun loadDiagnostics(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(SESSION, Context.MODE_PRIVATE)
        _diagnostics.value = PushRegistrationDiagnostics(
            tokenAvailable = prefs.getNullableBoolean(DIAG_TOKEN),
            registerAttempted = prefs.getNullableBoolean(DIAG_ATTEMPTED),
            httpResult = prefs.getString(DIAG_HTTP, "Sin intento registrado") ?: "Sin intento registrado",
            tokenErrorClass = prefs.getString(DIAG_TOKEN_ERROR_CLASS, null),
            tokenErrorMessage = prefs.getString(DIAG_TOKEN_ERROR_MESSAGE, null),
            authenticated = prefs.getNullableBoolean(DIAG_AUTH),
            registerSuccess = prefs.getNullableBoolean(DIAG_SUCCESS),
            syncPending = prefs.getBoolean(DIAG_PENDING, prefs.getBoolean(PREFERENCES_SYNC_PENDING_KEY, true)),
            orderUpdatesSent = prefs.getNullableBoolean(DIAG_ORDER_UPDATES)
        )
    }

    private fun android.content.SharedPreferences.getNullableBoolean(key: String): Boolean? =
        if (contains(key)) getBoolean(key, false) else null

    private fun persistDiagnostics(context: Context, value: PushRegistrationDiagnostics) {
        val prefs = context.applicationContext.getSharedPreferences(SESSION, Context.MODE_PRIVATE)
        prefs.edit().apply {
            value.tokenAvailable?.let { putBoolean(DIAG_TOKEN, it) } ?: remove(DIAG_TOKEN)
            value.registerAttempted?.let { putBoolean(DIAG_ATTEMPTED, it) } ?: remove(DIAG_ATTEMPTED)
            putString(DIAG_HTTP, value.httpResult)
            value.tokenErrorClass?.let { putString(DIAG_TOKEN_ERROR_CLASS, it) } ?: remove(DIAG_TOKEN_ERROR_CLASS)
            value.tokenErrorMessage?.let { putString(DIAG_TOKEN_ERROR_MESSAGE, it) } ?: remove(DIAG_TOKEN_ERROR_MESSAGE)
            value.authenticated?.let { putBoolean(DIAG_AUTH, it) } ?: remove(DIAG_AUTH)
            value.registerSuccess?.let { putBoolean(DIAG_SUCCESS, it) } ?: remove(DIAG_SUCCESS)
            putBoolean(DIAG_PENDING, value.syncPending)
            value.orderUpdatesSent?.let { putBoolean(DIAG_ORDER_UPDATES, it) } ?: remove(DIAG_ORDER_UPDATES)
        }.apply()
        _diagnostics.value = value
    }

    fun syncRegistration(context: Context) {
        val app = context.applicationContext
        persistDiagnostics(app, _diagnostics.value.copy(
            tokenAvailable = null,
            registerAttempted = false,
            httpResult = "Esperando token FCM",
            tokenErrorClass = null,
            tokenErrorMessage = null,
            authenticated = null,
            registerSuccess = null,
            syncPending = true,
            orderUpdatesSent = null
        ))
        app.getSharedPreferences(SESSION, Context.MODE_PRIVATE)
            .edit().putBoolean(PREFERENCES_SYNC_PENDING_KEY, true).apply()
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                if (token.isBlank()) {
                    persistDiagnostics(app, _diagnostics.value.copy(
                        tokenAvailable = false, httpResult = "Token FCM vacío", syncPending = true
                    ))
                    android.util.Log.w(TAG, "FCM token available NO; sync pending YES")
                } else {
                    persistDiagnostics(app, _diagnostics.value.copy(tokenAvailable = true))
                    android.util.Log.i(TAG, "FCM token available YES")
                    onRegistered(app, token)
                }
            }
            .addOnFailureListener { error ->
                persistDiagnostics(app, _diagnostics.value.copy(
                    tokenAvailable = false,
                    httpResult = "TOKEN_ERROR ${error.javaClass.simpleName}",
                    tokenErrorClass = error.javaClass.simpleName,
                    tokenErrorMessage = safePushTokenErrorMessage(error.message),
                    syncPending = true
                ))
                android.util.Log.w(TAG, "FCM token available NO (${error.javaClass.simpleName}: ${safePushTokenErrorMessage(error.message)}); sync pending YES")
            }
    }

    fun onRegistered(context: Context, token: String) {
        if (token.isBlank()) {
            persistDiagnostics(context, _diagnostics.value.copy(
                tokenAvailable = false, httpResult = "Token FCM vacío", syncPending = true
            ))
            android.util.Log.w(TAG, "Registration skipped: blank FCM token; sync pending YES")
            return
        }
        persistDiagnostics(context, _diagnostics.value.copy(tokenAvailable = true))
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
        val authenticated = !accountToken.isNullOrBlank()
        persistDiagnostics(context, _diagnostics.value.copy(
            tokenAvailable = true,
            registerAttempted = true,
            httpResult = "POST en curso",
            authenticated = authenticated,
            registerSuccess = null,
            syncPending = true,
            orderUpdatesSent = pushPrefs.orderUpdates
        ))
        android.util.Log.i(TAG, "Registration attempt started; authenticated=$authenticated; order_updates=${pushPrefs.orderUpdates}")
        if (!postRegistration(context, payload, accountToken)) {
            prefs.edit().putBoolean(PREFERENCES_SYNC_PENDING_KEY, true).apply()
            persistDiagnostics(context, _diagnostics.value.copy(syncPending = true))
            android.util.Log.w(TAG, "Registration failed; sync pending YES; old unregisters postponed=${pending.isNotEmpty() || shouldDeactivatePreviousToken(previousToken, token)}")
            // Register-first invariant: never deactivate an old identity unless the new POST succeeded.
            return
        }
        pending.filter { it != token }.forEach { stale ->
            val removed = deactivateToken(stale, accountToken)
            if (removed) pending.remove(stale)
            android.util.Log.i(TAG, "Pending unregister ${if (removed) "executed" else "retained for retry"}")
        }
        if (previousToken != null && previousToken != token) {
            val removed = deactivateToken(previousToken, accountToken)
            if (!removed) pending.add(previousToken)
            android.util.Log.i(TAG, "Previous token unregister ${if (removed) "executed" else "retained for retry"}")
        }
        pending.remove(token)
        prefs.edit().putString(IDENTIFIER_TYPE_KEY, "fcm_token").putString(IDENTIFIER_KEY, token)
            .putStringSet(PENDING_UNREGISTER_KEY, pending).remove(PREFERENCES_SYNC_PENDING_KEY).apply()
        persistDiagnostics(context, _diagnostics.value.copy(
            registerSuccess = true,
            syncPending = false
        ))
        android.util.Log.i(TAG, "Registration successful; authenticated=${!accountToken.isNullOrBlank()}; sync pending NO")
    }

    private fun postRegistration(
        context: Context,
        payload: PushRegistrationPayload,
        accountToken: String?
    ): Boolean {
        val request = Request.Builder()
            .url(ENDPOINT)
            .post(payload.toJson().toString().toRequestBody("application/json".toMediaType()))
            .apply {
                pushAuthorizationHeader(accountToken)?.let { header("Authorization", it) }
            }
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val successful = response.isSuccessful
                val category = "HTTP ${response.code}"
                persistDiagnostics(context, _diagnostics.value.copy(
                    httpResult = category,
                    authenticated = !accountToken.isNullOrBlank(),
                    registerSuccess = successful,
                    syncPending = !successful
                ))
                android.util.Log.i(TAG, "Registration $category; authenticated=${!accountToken.isNullOrBlank()}; success=$successful; sync pending=${!successful}")
                successful
            }
        } catch (error: Exception) {
            val category = "NETWORK_ERROR ${error.javaClass.simpleName}"
            persistDiagnostics(context, _diagnostics.value.copy(
                httpResult = category,
                authenticated = !accountToken.isNullOrBlank(),
                registerSuccess = false,
                syncPending = true
            ))
            android.util.Log.w(TAG, "Registration $category; authenticated=${!accountToken.isNullOrBlank()}; success NO; sync pending YES")
            false
        }
    }

    private fun deactivateToken(token: String, accountToken: String?): Boolean {
        if (token.isBlank()) return false
        val request = Request.Builder().url(ENDPOINT)
            .delete(buildUnregisterPayload(token).toString().toRequestBody("application/json".toMediaType()))
            .apply { pushAuthorizationHeader(accountToken)?.let { header("Authorization", it) } }.build()
        return try {
            httpClient.newCall(request).execute().use { response ->
                android.util.Log.i(TAG, "Unregister HTTP ${response.code}; authenticated=${!accountToken.isNullOrBlank()}")
                response.isSuccessful
            }
        } catch (error: Exception) {
            android.util.Log.w(TAG, "Unregister failed (${error.javaClass.simpleName}); authenticated=${!accountToken.isNullOrBlank()}")
            false
        }
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
