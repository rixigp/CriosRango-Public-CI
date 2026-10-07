package es.criosrango.shared.promotions

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PromotionClientTest {

    @Test
    fun getPromotionsUsesPublicEndpointAndDecodesContract() = kotlinx.coroutines.test.runTest {
        var request: HttpRequestData? = null
        val client = mockClient(
            HttpStatusCode.OK,
            """{
                "promotions": [{
                    "id": "42",
                    "title": "10% de descuento",
                    "description": "Promoción de prueba",
                    "code": "TEST10",
                    "requires_login": false,
                    "starts_at": null,
                    "expires_at": "2026-12-31T23:59:59",
                    "priority": 7
                }]
            }"""
        ) { request = it }

        val api = PromotionClient(
            baseUrl = "https://example.test/wp-json/criosrango/v1/",
            client = client
        )

        val promotions = api.getPromotions()

        assertEquals("/wp-json/criosrango/v1/promotions", request?.url?.encodedPath)
        assertEquals(1, promotions.size)
        assertEquals("42", promotions.single().id)
        assertEquals("10% de descuento", promotions.single().title)
        assertEquals("Promoción de prueba", promotions.single().description)
        assertEquals("TEST10", promotions.single().code)
        assertEquals(false, promotions.single().requiresLogin)
        assertEquals(null, promotions.single().startsAt)
        assertEquals("2026-12-31T23:59:59", promotions.single().expiresAt)
        assertEquals(7, promotions.single().priority)
    }

    @Test
    fun emptyPromotionsResponseIsValid() = kotlinx.coroutines.test.runTest {
        val api = PromotionClient(
            client = mockClient(HttpStatusCode.OK, """{"promotions":[]}""")
        )

        assertTrue(api.getPromotions().isEmpty())
    }

    @Test
    fun non2xxResponseBecomesPromotionApiException() = kotlinx.coroutines.test.runTest {
        val api = PromotionClient(
            client = mockClient(
                HttpStatusCode.ServiceUnavailable,
                """{"success":false,"code":"SERVICE_UNAVAILABLE","message":"Servicio no disponible"}"""
            )
        )

        val exception = assertFailsWith<PromotionApiException> {
            api.getPromotions()
        }

        assertEquals(503, exception.statusCode)
        assertEquals("SERVICE_UNAVAILABLE", exception.code)
        assertEquals("Servicio no disponible", exception.message)
    }

    private fun mockClient(
        status: HttpStatusCode,
        body: String,
        onRequest: (HttpRequestData) -> Unit = {}
    ): HttpClient {
        val engine = MockEngine { request ->
            onRequest(request)
            respond(
                content = body,
                status = status,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        return HttpClient(engine)
    }
}
