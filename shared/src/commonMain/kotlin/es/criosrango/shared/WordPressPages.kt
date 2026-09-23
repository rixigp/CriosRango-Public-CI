package es.criosrango.shared

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

private const val WORDPRESS_PAGES_BASE_URL = "https://www.criosrango.es/wp-json/wp/v2/pages"

@Serializable
data class WordPressRendered(val rendered: String = "")

@Serializable
data class WordPressPage(
    val id: Long = 0,
    val slug: String = "",
    val title: WordPressRendered = WordPressRendered(),
    val content: WordPressRendered = WordPressRendered()
)

enum class AccountInfoPage(val title: String, val slug: String) {
    RETURNS("Cambios y devoluciones", "cambios-y-devoluciones"),
    TERMS("Condiciones de contratación", "condiciones-generales-de-contratacion"),
    PRIVACY("Política de privacidad", "politica-de-privacidad"),
    LEGAL("Aviso legal", "aviso-legal"),
    COOKIES("Política de cookies", "politica-de-cookies")
}

class WordPressPagesClient(
    private val client: HttpClient = es.criosrango.shared.createStoreHttpClient(),
    private val baseUrl: String = WORDPRESS_PAGES_BASE_URL
) {
    suspend fun getPageBySlug(slug: String): WordPressPage? {
        require(slug.isNotBlank()) { "El slug no puede estar vacío." }
        return client.get(baseUrl) { parameter("slug", slug) }.body<List<WordPressPage>>().firstOrNull()
    }

    fun close() = client.close()
}

private val numericEntityRegex = Regex("""&#(?:([0-9]+)|x([0-9a-fA-F]+));""")

private val namedHtmlEntities = mapOf(
    "amp" to "&",
    "lt" to "<",
    "gt" to ">",
    "quot" to "\"",
    "apos" to "'",
    "nbsp" to " "
)

private fun decodeHtmlEntities(text: String): String {
    val numericDecoded = text.replace(numericEntityRegex) { match ->
        val decimal = match.groupValues[1]
        val hexadecimal = match.groupValues[2]
        val codePoint = runCatching {
            if (decimal.isNotEmpty()) decimal.toLong() else hexadecimal.toLong(16)
        }.getOrNull()
        codePoint?.let(::codePointToString) ?: match.value
    }

    return numericDecoded.replace(Regex("""&([A-Za-z][A-Za-z0-9]+);""")) { match ->
        namedHtmlEntities[match.groupValues[1]] ?: match.value
    }
}

private fun codePointToString(codePoint: Long): String? {
    if (codePoint !in 0L..0x10FFFFL || codePoint in 0xD800L..0xDFFFL) return null
    val value = codePoint.toInt()
    if (value <= 0xFFFF) return value.toChar().toString()
    val adjusted = value - 0x10000
    val high = (0xD800 + (adjusted ushr 10)).toChar()
    val low = (0xDC00 + (adjusted and 0x3FF)).toChar()
    return "$high$low"
}

fun wordpressHtmlToText(html: String): String {
    if (html.isBlank()) return ""

    val normalized = html
        .replace(Regex("""\[(?:/)?vc_[^\]]*\]""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""\[(?:/)?nectar_[^\]]*\]""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""<li[^>]*>""", RegexOption.IGNORE_CASE), "• ")
        .replace(Regex("""</li>""", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("""</(?:p|div|h1|h2|h3|h4|h5|h6|ul|ol)>""", RegexOption.IGNORE_CASE), "\n\n")
        .replace(Regex("""<[^>]+>""", RegexOption.IGNORE_CASE), "")
        .replace("\r\n", "\n")
        .replace('\r', '\n')

    return decodeHtmlEntities(normalized)
        .replace("\u00A0", " ")
        .replace(Regex("""[ \t]+\n"""), "\n")
        .replace(Regex("""\n[ \t]+"""), "\n")
        .replace(Regex("""[ \t]{2,}"""), " ")
        .replace(Regex("""\n{3,}"""), "\n\n")
        .trim()
}
