package es.criosrango.shared

import io.ktor.http.Url

/**
 * Validates absolute HTTPS URLs that the app may open as a card-payment page.
 *
 * Payment-return callbacks use a separate policy and must not use this validator.
 */
object PaymentUrlValidator {
    val allowedHosts: Set<String> = setOf(
        "criosrango.es",
        "www.criosrango.es",
        "pgw.ceca.es",
        "tpv.ceca.es"
    )

    fun isAllowed(rawUrl: String): Boolean {
        if (rawUrl.isBlank() || rawUrl.any { it.isWhitespace() || it.code < 0x20 }) return false

        val url = try {
            Url(rawUrl)
        } catch (_: Exception) {
            return false
        }

        val host = url.host.lowercase()
        return url.protocol.name.equals("https", ignoreCase = true) &&
            host.isNotBlank() &&
            host in allowedHosts &&
            url.user == null &&
            url.password == null &&
            (url.specifiedPort == null || url.specifiedPort == 443)
    }
}
