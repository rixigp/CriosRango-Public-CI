package es.criosrango.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StoreApiClientCartSessionTest {
    private val cartJson = """
        {
          "items": [],
          "coupons": [],
          "totals": {
            "total_items":"0","total_items_tax":"0","total_fees":"0","total_fees_tax":"0",
            "total_discount":"0","total_discount_tax":"0","total_shipping":"0",
            "total_shipping_tax":"0","total_price":"0","total_tax":"0",
            "currency_symbol":"€","currency_minor_unit":2
          },
          "payment_methods":[],
          "shipping_rates":[],
          "items_count":0,
          "errors":[]
        }
    """.trimIndent()

    @Test
    fun cartOperationsReuseAndPersistWooSessionHeaders() = runTest {
        val requests = mutableListOf<io.ktor.client.request.HttpRequestData>()
        val engine = MockEngine { request ->
            requests += request
            respond(
                cartJson,
                HttpStatusCode.OK,
                headersOf(
                    HttpHeaders.ContentType, "application/json",
                    "Cart-Token", "cart-updated",
                    "Nonce", "nonce-updated",
                    HttpHeaders.SetCookie, "woocommerce_cart_hash=hash-1; Path=/"
                )
            )
        }
        val client = HttpClient(engine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; coerceInputValues = true })
            }
        }
        val session = InMemoryStoreSessionStore(
            cartToken = "cart-initial",
            nonce = "nonce-initial",
            cookieHeader = "woocommerce_cart_hash=old"
        )
        val api = StoreApiClient("https://example.test/wp-json/wc/store/v1/", client, session)

        api.cart()
        api.addCartItem(StoreCartRequest(id = 10, quantity = 1))
        api.updateCartItem("line-key", 2)
        api.removeCartItem("line-key")

        assertEquals(4, requests.size)
        assertTrue(requests.all { it.headers["Cart-Token"] == "cart-updated" || it.headers["Cart-Token"] == "cart-initial" })
        assertTrue(requests.drop(1).all { it.headers["Cart-Token"] == "cart-updated" })
        assertTrue(requests.drop(1).all { it.headers["Nonce"] == "nonce-updated" })
        assertTrue(requests.drop(1).all { it.headers["Cookie"]?.contains("woocommerce_cart_hash=hash-1") == true })
        assertEquals("cart-updated", session.cartToken)
        assertEquals("nonce-updated", session.nonce)
        assertTrue(session.cookieHeader.orEmpty().contains("woocommerce_cart_hash=hash-1"))
        assertEquals("/wp-json/wc/store/v1/cart", requests[0].url.encodedPath)
        assertEquals("/wp-json/wc/store/v1/cart/add-item", requests[1].url.encodedPath)
        assertEquals("/wp-json/wc/store/v1/cart/update-item", requests[2].url.encodedPath)
        assertEquals("/wp-json/wc/store/v1/cart/remove-item", requests[3].url.encodedPath)
        client.close()
    }
}
