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
        val account = IosSecureKeyValueStore.read(PENDING_ACCOUNT_KEY, defaults)?.takeIf { it.isNotBlank() }
        val generation = if (defaults.objectForKey(PENDING_GENERATION_KEY) == null) 0L else defaults.integerForKey(PENDING_GENERATION_KEY)
        return PushUnregisterSnapshot(token, account, generation)
    }

    actual fun persistPendingUnregisterSnapshot(snapshot: PushUnregisterSnapshot) {
        defaults.setObject(snapshot.deviceToken, forKey = PENDING_TOKEN_KEY)
        if (snapshot.accountToken == null) IosSecureKeyValueStore.delete(PENDING_ACCOUNT_KEY, defaults)
        else IosSecureKeyValueStore.write(PENDING_ACCOUNT_KEY, snapshot.accountToken, defaults)
        defaults.setObject(snapshot.registrationGeneration, forKey = PENDING_GENERATION_KEY)
    }

    actual fun clearPendingUnregisterSnapshot(snapshot: PushUnregisterSnapshot) {
        if (loadPendingUnregisterSnapshot() != snapshot) return
        defaults.removeObjectForKey(PENDING_TOKEN_KEY)
        IosSecureKeyValueStore.delete(PENDING_ACCOUNT_KEY, defaults)
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
        IosPushDiagnostics.recordRequestStage("REGISTER_HTTP_ENTERED", orderUpdates, newProducts)
        var stage = "BEFORE_SEND"
        var client: io.ktor.client.HttpClient? = null
        try {
            val httpClient = createStoreHttpClient()
            client = httpClient
            stage = "SEND"
            IosPushDiagnostics.recordRequestStage("REGISTER_HTTP_SEND_STARTED", orderUpdates, newProducts)
            val response = withTimeout(4_000L) {
                httpClient.post(PUSH_DEVICE_ENDPOINT) {
                    contentType(ContentType.Application.Json)
                    accountToken?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                    setBody(
                        PushDeviceRegistrationPayload(
                            platform = "ios",
                            identifierType = "apns_token",
                            identifier = token,
                            newProducts = newProducts,
                            orderUpdates = orderUpdates
                        )
                    )
                }
            }

            stage = "RECEIVE_STATUS"
            val status = response.status.value
            IosPushDiagnostics.recordRequestStage("REGISTER_HTTP_RESPONSE_RECEIVED", orderUpdates, newProducts)
            stage = "READ_BODY"
            val bodyResult = runCatching { response.bodyAsText() }
            if (bodyResult.isFailure) {
                IosPushDiagnostics.recordRequestStage("BODY_READ_FAILED", exceptionStage = "READ_BODY")
            }
            val body = bodyResult.getOrElse { "[body read failed: ${it::class.simpleName}: ${it.message}]" }
            val accepted = status in 200..299
            IosPushDiagnostics.recordPostResult(
                result = "HTTP $status",
                accepted = if (accepted) "SÍ" else "NO",
                pending = !accepted,
                errorClass = if (accepted) "" else "HTTPError",
                errorMessage = if (accepted) "" else "El backend rechazó el registro",
                httpStatus = status,
                responseBody = body,
                bodyReadOk = bodyResult.isSuccess
            )
        } catch (error: Throwable) {
            IosPushDiagnostics.recordRequestStage(
                "REGISTER_HTTP_EXCEPTION", orderUpdates, newProducts, exceptionStage = stage
            )
            IosPushDiagnostics.recordPostResult(
                result = if (stage == "BEFORE_SEND") "Error antes del envío" else "Error durante $stage",
                accepted = "NO",
                pending = true,
                errorClass = error::class.simpleName ?: "Throwable",
                errorMessage = error.message ?: error.toString(),
                httpStatus = null,
                responseBody = ""
            )
        } finally {
            try {
                client?.close()
            } catch (error: Throwable) {
                IosPushDiagnostics.recordRequestStage("CLOSE_CLIENT_EXCEPTION", exceptionStage = "CLOSE_CLIENT")
            }
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
                    setBody(
                        PushDeviceUnregisterPayload(
                            platform = "ios",
                            token = snapshot.deviceToken
                        )
                    )
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
