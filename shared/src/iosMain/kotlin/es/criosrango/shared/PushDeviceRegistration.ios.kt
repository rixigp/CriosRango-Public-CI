package es.criosrango.shared

import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.withTimeout
import platform.Foundation.NSUserDefaults

private const val PUSH_DEVICE_ENDPOINT = "https://criosrango.es/wp-json/criosrango/v1/push/device"
private const val PUSH_TOKEN_KEY = "criosrango_apns_token"
private const val PUSH_PENDING_UNREGISTER_KEY = "criosrango_push_pending_unregister_token"

actual object PushDeviceRegistration {
    actual suspend fun registerCurrentDevice() {
        val defaults = NSUserDefaults.standardUserDefaults
        val token = defaults.stringForKey(PUSH_TOKEN_KEY)?.takeIf { it.isNotBlank() } ?: return
        val accountToken = IosAccountTokenStore().load() ?: return
        val client = createStoreHttpClient()
        try {
            val pending = defaults.stringForKey(PUSH_PENDING_UNREGISTER_KEY)
            val response = runCatching {
                withTimeout(4_000L) {
                    client.post(PUSH_DEVICE_ENDPOINT) {
                        contentType(ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $accountToken")
                        setBody(mapOf("platform" to "ios", "token" to token,
                            "new_products" to (defaults.objectForKey("criosrango_push_new_products") as? Boolean ?: true),
                            "order_updates" to (defaults.objectForKey("criosrango_push_order_updates") as? Boolean ?: true)))
                    }
                }
            }.getOrNull()
            if (response == null || response.status.value !in 200..299) {
                // If logout left a pending association, retire it even when reassignment fails.
                if (!pending.isNullOrBlank()) {
                    val removed = runCatching { withTimeout(3_000L) {
                        client.delete(PUSH_DEVICE_ENDPOINT) {
                            contentType(ContentType.Application.Json)
                            header(HttpHeaders.Authorization, "Bearer $accountToken")
                            setBody(mapOf("platform" to "ios", "token" to pending))
                        }.status.value in 200..299
                    } }.getOrDefault(false)
                    if (removed) defaults.removeObjectForKey(PUSH_PENDING_UNREGISTER_KEY)
                }
                return
            }
            if (pending == token) {
                // Successful registration reassigns this unique token row to the current account.
                defaults.removeObjectForKey(PUSH_PENDING_UNREGISTER_KEY)
            } else if (!pending.isNullOrBlank()) {
                val removed = runCatching { withTimeout(3_000L) {
                    client.delete(PUSH_DEVICE_ENDPOINT) {
                        contentType(ContentType.Application.Json)
                        header(HttpHeaders.Authorization, "Bearer $accountToken")
                        setBody(mapOf("platform" to "ios", "token" to pending))
                    }.status.value in 200..299
                } }.getOrDefault(false)
                if (removed) defaults.removeObjectForKey(PUSH_PENDING_UNREGISTER_KEY)
            }
        } finally { client.close() }
    }

    actual suspend fun unregisterCurrentDevice() {
        val defaults = NSUserDefaults.standardUserDefaults
        val token = defaults.stringForKey(PUSH_TOKEN_KEY)?.takeIf { it.isNotBlank() } ?: return
        defaults.setObject(token, forKey = PUSH_PENDING_UNREGISTER_KEY)
        val client = createStoreHttpClient()
        try {
            val response = withTimeout(4_000L) {
                client.delete(PUSH_DEVICE_ENDPOINT) {
                    contentType(ContentType.Application.Json)
                    IosAccountTokenStore().load()?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                    setBody(mapOf("platform" to "ios", "token" to token))
                }
            }
            if (response.status.value in 200..299) defaults.removeObjectForKey(PUSH_PENDING_UNREGISTER_KEY)
        } finally { client.close() }
        // Keep APNs token locally so a later login can register it for the current account.
    }
}
