package es.criosrango.shared

import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSUserDefaults

private const val PUSH_DEVICE_ENDPOINT = "https://criosrango.es/wp-json/criosrango/v1/push/device"
private const val PUSH_TOKEN_KEY = "criosrango_apns_token"
private const val PENDING_TOKEN_KEY = "criosrango_push_pending_unregister_token"
private const val PENDING_ACCOUNT_KEY = "criosrango_push_pending_unregister_account"
private const val PENDING_GENERATION_KEY = "criosrango_push_pending_unregister_generation"

actual object PushDeviceRegistration {
    private val defaults get() = NSUserDefaults.standardUserDefaults

    actual fun hasPendingUnregister(): Boolean = loadPendingUnregisterSnapshot() != null

    actual fun captureUnregisterSnapshot(registrationGeneration: Long): PushUnregisterSnapshot? {
        val token = defaults.stringForKey(PENDING_TOKEN_KEY)?.takeIf { it.isNotBlank() }
            ?: defaults.stringForKey(PUSH_TOKEN_KEY)?.takeIf { it.isNotBlank() }
            ?: return null
        return PushUnregisterSnapshot(
            deviceToken = token,
            accountToken = IosAccountTokenStore().load(),
            registrationGeneration = registrationGeneration
        )
    }

    actual fun loadPendingUnregisterSnapshot(): PushUnregisterSnapshot? {
        val token = defaults.stringForKey(PENDING_TOKEN_KEY)?.takeIf { it.isNotBlank() } ?: return null
        val account = defaults.stringForKey(PENDING_ACCOUNT_KEY)?.takeIf { it.isNotBlank() }
        val generation = if (defaults.objectForKey(PENDING_GENERATION_KEY) == null) 0L else defaults.integerForKey(PENDING_GENERATION_KEY)
        return PushUnregisterSnapshot(token, account, generation)
    }

    actual fun persistPendingUnregisterSnapshot(snapshot: PushUnregisterSnapshot) {
        defaults.setObject(snapshot.deviceToken, forKey = PENDING_TOKEN_KEY)
        if (snapshot.accountToken == null) defaults.removeObjectForKey(PENDING_ACCOUNT_KEY)
        else defaults.setObject(snapshot.accountToken, forKey = PENDING_ACCOUNT_KEY)
        defaults.setObject(snapshot.registrationGeneration, forKey = PENDING_GENERATION_KEY)
    }

    actual fun clearPendingUnregisterSnapshot(snapshot: PushUnregisterSnapshot) {
        if (loadPendingUnregisterSnapshot() != snapshot) return
        defaults.removeObjectForKey(PENDING_TOKEN_KEY)
        defaults.removeObjectForKey(PENDING_ACCOUNT_KEY)
        defaults.removeObjectForKey(PENDING_GENERATION_KEY)
    }

    actual suspend fun registerCurrentDevice() {
        val token = defaults.stringForKey(PUSH_TOKEN_KEY)?.takeIf { it.isNotBlank() }
        if (token == null) {
            IosPushDiagnostics.recordPostResult("NO ENVIADO: token APNs no disponible", "AÚN SIN DATO", true)
            return
        }
        val accountToken = IosAccountTokenStore().load()
        val newProducts = defaults.objectForKey("criosrango_push_new_products") as? Boolean ?: true
        val orderUpdates = defaults.objectForKey("criosrango_push_order_updates") as? Boolean ?: true
        IosPushDiagnostics.recordToken(token)
        IosPushDiagnostics.recordPostAttempt(accountToken != null, newProducts, orderUpdates)
        val client = createStoreHttpClient()
        try {
            val response = try {
                withTimeout(4_000L) {
                    client.post(PUSH_DEVICE_ENDPOINT) {
                        contentType(ContentType.Application.Json)
                        accountToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                        setBody(mapOf("platform" to "ios", "identifier_type" to "apns_token",
                            "identifier" to token, "new_products" to newProducts, "order_updates" to orderUpdates))
                    }
                }
            } catch (error: Throwable) {
                IosPushDiagnostics.recordPostResult(
                    result = "ERROR de red",
                    accepted = "NO",
                    pending = true,
                    errorClass = error::class.simpleName ?: "Throwable",
                    errorMessage = error.message ?: error.toString(),
                    httpStatus = null,
                    responseBody = ""
                )
                return
            }

            // HTTP status is authoritative. Body parsing is diagnostic only and must never
            // turn a valid 2xx response into a failed registration.
            val status = response.status.value
            val accepted = status in 200..299
            val body = runCatching { response.bodyAsText() }
                .getOrElse { "[body read failed: ${it::class.simpleName}: ${it.message}]" }
            IosPushDiagnostics.recordPostResult(
                result = "HTTP $status",
                accepted = if (accepted) "SÍ" else "NO",
                pending = !accepted,
                errorClass = if (accepted) "" else "HTTPError",
                errorMessage = if (accepted) "" else "El backend rechazó el registro",
                httpStatus = status,
                responseBody = body
            )
        } finally {
            // Closing a client is cleanup, not part of the server's acceptance contract.
            runCatching { client.close() }
        }
    }

    actual suspend fun unregisterCurrentDevice() {
        val snapshot = captureUnregisterSnapshot(0L) ?: return
        persistPendingUnregisterSnapshot(snapshot)
        unregisterSnapshot(snapshot)
    }

    actual suspend fun unregisterSnapshot(snapshot: PushUnregisterSnapshot): Boolean {
        persistPendingUnregisterSnapshot(snapshot)
        val client = createStoreHttpClient()
        return try {
            val response = withTimeout(4_000L) {
                client.delete(PUSH_DEVICE_ENDPOINT) {
                    contentType(ContentType.Application.Json)
                    snapshot.accountToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                    setBody(mapOf("platform" to "ios", "token" to snapshot.deviceToken))
                }
            }
            val accepted = response.status.value in 200..299
            if (accepted) clearPendingUnregisterSnapshot(snapshot)
            accepted
        } catch (_: Throwable) {
            false
        } finally { client.close() }
    }
}
