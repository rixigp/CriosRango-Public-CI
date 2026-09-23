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

fun wordpressHtmlToText(html: String): String {
    if (html.isBlank()) return ""
    val normalized = html
        .replace(Regex("""[(?:/)?vc_[^]]*]""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""[(?:/)?(?:nectar|salient)_[^]]*]""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""<li[^>]*>""", RegexOption.IGNORE_CASE), "• ")
        .replace(Regex("""</li>""", RegexOption.IGNORE_CASE), "
")
        .replace(Regex("""</(?:p|div|h1|h2|h3|h4|h5|h6|ul|ol)>""", RegexOption.IGNORE_CASE), "

")
    return platformHtmlToText(normalized)
        .replace(" ", " ")
        .replace("»", "")
        .replace(Regex("""[ 	]+
"""), "
")
        .replace(Regex("""
[ 	]+"""), "
")
        .replace(Regex("""
{3,}"""), "

")
        .trim()
}

expect fun platformHtmlToText(html: String): String
