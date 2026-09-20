package es.criosrango.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class StoreApiClientRemoveCartItemTest {
    private val emptyCart = """{"items":[],"coupons":[],"totals":{"total_items":"0","total_items_tax":"0","total_fees":"0","total_fees_tax":"0","total_discount":"0","total_discount_tax":"0","total_shipping":"0","total_shipping_tax":"0","total_price":"0","total_tax":"0","currency_symbol":"€","currency_minor_unit":2},"payment_methods":[],"shipping_rates":[],"items_count":0,"errors":[]}"""
    @Test fun removeCartItemUsesCurrentSession() = runBlocking {
        val methods = mutableListOf<String>()
        val engine = MockEngine {
            methods += it.method.value
            respond(emptyCart, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val session = InMemoryStoreSessionStore("cart-1","nonce-1","cookie-1")
        val api = StoreApiClient("https://example.test/wp-json/wc/store/v1/", client, session)
        api.removeCartItem("line-key-1")
        assertEquals(listOf("POST"), methods)
        client.close()
    }
}
