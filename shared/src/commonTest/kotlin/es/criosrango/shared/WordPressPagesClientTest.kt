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
    fun htmlNormalizationRemovesWordPressShortcodesAndPreservesStructure() {
        val text = wordpressHtmlToText("""<p>Uno</p><ul><li>Dos</li><li>Tres</li></ul>[vc_row][nectar_cta text="x"]""")
        assertEquals("Uno\n\n• Dos\n• Tres", text)
    }
}
