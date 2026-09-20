package es.criosrango.shared.api

import es.criosrango.shared.model.StoreCart
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoreApiClientCartClearTest {
    private fun emptyCartJson() = """{"items":[],"coupons":[],"totals":{"total_items":"0","total_items_tax":"0","total_fees":"0","total_fees_tax":"0","total_discount":"0","total_discount_tax":"0","total_price":"0","total_tax":"0","currency_symbol":"€","currency_minor_unit":2},"payment_methods":[],"shipping_rates":[],"items_count":0,"errors":[]}"""

    @Test
    fun clearCartItemsUsesDeleteThenVerifiedGetWithSameSessionHeaders() = kotlinx.coroutines.runBlocking {
        val methods = mutableListOf<String>()
        val headersSeen = mutableListOf<Map<String, String>>()
        val engine = MockEngine { request ->
            methods += request.method.value
            headersSeen += mapOf(
                "Cart-Token" to (request.headers["Cart-Token"] ?: ""),
                "Nonce" to (request.headers["Nonce"] ?: ""),
                "Cookie" to (request.headers["Cookie"] ?: "")
            )
            respond(
                content = emptyCartJson(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        val session = InMemoryStoreSessionStore(
            cartToken = "cart-token-123",
            nonce = "nonce-456",
            cookieHeader = "wordpress_logged_in=test"
        )
        val api = StoreApiClient(
            baseUrl = "https://example.test/wp-json/wc/store/v1/",
            client = client,
            session = session
        )

        val cleared = api.clearCartItems()
        val verified = api.cart()

        assertTrue(cleared.items.isEmpty())
        assertTrue(verified.items.isEmpty())
        assertEquals(listOf("DELETE", "GET"), methods)
        assertEquals(headersSeen[0], headersSeen[1])
        assertEquals("cart-token-123", headersSeen[0]["Cart-Token"])
        assertEquals("nonce-456", headersSeen[0]["Nonce"])
        assertEquals("wordpress_logged_in=test", headersSeen[0]["Cookie"])
        assertEquals("cart-token-123", session.cartToken)
        assertEquals("nonce-456", session.nonce)
        assertEquals("wordpress_logged_in=test", session.cookieHeader)
        client.close()
    }
}
