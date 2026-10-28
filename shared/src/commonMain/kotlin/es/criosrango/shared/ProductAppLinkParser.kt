package es.criosrango.shared

import io.ktor.http.Url
import androidx.compose.runtime.mutableStateOf

/** Pending Universal Link consumed by the shared iOS root navigation. */
val iosPendingProductAppLink = mutableStateOf<String?>(null)

/** Accept only first-party HTTPS product URLs. Payment URLs use their existing handler. */
fun parseProductAppLinkUrl(rawUrl: String): String? {
    val url = try { Url(rawUrl) } catch (_: Exception) { return null }
    if (url.protocol.name.lowercase() != "https") return null
    if (url.host.lowercase() !in setOf("criosrango.es", "www.criosrango.es")) return null
    val segments = url.encodedPath.split('/').filter { it.isNotBlank() }
    if (segments.size != 2 || !segments[0].equals("producto", ignoreCase = true)) return null
    val slug = segments[1].lowercase()
    if (slug.isBlank() || slug.any { !(it in 'a'..'z' || it in '0'..'9' || it == '-') }) return null
    return slug
}
