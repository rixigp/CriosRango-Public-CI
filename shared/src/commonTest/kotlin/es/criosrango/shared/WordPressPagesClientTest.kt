package es.criosrango.shared

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WordPressPagesClientTest {
    private fun client(body: String, status: HttpStatusCode = HttpStatusCode.OK, capture: ((HttpRequestData) -> Unit)? = null): WordPressPagesClient {
        val engine = MockEngine { request ->
            capture?.invoke(request)
            respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        return WordPressPagesClient(
            client = HttpClient(engine) {
                expectSuccess = true
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
            baseUrl = "https://test.invalid/wp-json/wp/v2/pages"
        )
    }

    @Test
    fun slugIsSentAndResponseMaps() = runBlocking {
        var query: String? = null
        val page = client(
            """[{"id":12,"slug":"aviso-legal","title":{"rendered":"<b>Aviso legal</b>"},"content":{"rendered":"<p>Contenido</p>"}}]""",
            capture = { query = it.url.parameters["slug"] }
        ).getPageBySlug("aviso-legal")
        assertEquals("aviso-legal", query)
        assertEquals(12L, page?.id)
        assertEquals("aviso-legal", page?.slug)
        assertEquals("<b>Aviso legal</b>", page?.title?.rendered)
        assertEquals("<p>Contenido</p>", page?.content?.rendered)
    }

    @Test
    fun emptyResponseIsNotFound() = runBlocking {
        assertNull(client("[]").getPageBySlug("politica-de-cookies"))
    }

    @Test
    fun malformedPayloadFailsForRetryLayer() = runBlocking {
        val error = runCatching { client("""{"broken":""").getPageBySlug("aviso-legal") }.exceptionOrNull()
        assertTrue(error != null)
    }

    @Test
    fun htmlParagraphsBreaksAndLists() {
        assertEquals("Texto", wordpressHtmlToText("<p>Texto</p>"))
        assertEquals("Uno\nDos", wordpressHtmlToText("Uno<br>Dos"))
        assertEquals("• Uno\n• Dos", wordpressHtmlToText("<ul><li>Uno</li><li>Dos</li></ul>"))
    }

    @Test
    fun htmlEntitiesDecodeSafely() {
        assertEquals("& < > \" ' ", wordpressHtmlToText("&amp; &lt; &gt; &quot; &apos; &nbsp;"))
        assertEquals("–", wordpressHtmlToText("&#8211;"))
        assertEquals("–", wordpressHtmlToText("&#x2013;"))
        assertEquals("😀", wordpressHtmlToText("&#x1F600;"))
        assertEquals("&#x110000; &#xD800; &#bad;", wordpressHtmlToText("&#x110000; &#xD800; &#bad;"))
    }

    @Test
    fun shortcodesNestedTagsAndRepeatedBreaks() {
        val html = """[vc_row][vc_column]<div><p>Uno <strong>dos</strong></p><div><br><span>tres</span></div></div>[/vc_column][/vc_row][nectar_cta text="x"]"""
        assertEquals("Uno dos\n\ntres", wordpressHtmlToText(html))
    }

    @Test
    fun realisticWordPressPayload() {
        val html = """<div class="vc_row wpb_row"><div class="vc_column"><h2>Cambios &amp; devoluciones</h2><p>Plazo: 14&nbsp;días.</p><ul><li>Artículo &#8211; sin usar</li><li>Embalaje &quot;original&quot;</li></ul><p>Más información &lt;aquí&gt;.</p></div></div>"""
        assertEquals("Cambios & devoluciones\n\nPlazo: 14 días.\n\n• Artículo – sin usar\n• Embalaje \"original\"\n\nMás información <aquí>.", wordpressHtmlToText(html))
    }

    @Test
    fun namedEntitiesOutsideSupportedSetRemainSafe() {
        assertEquals("&copy;", wordpressHtmlToText("&copy;"))
    }
}
