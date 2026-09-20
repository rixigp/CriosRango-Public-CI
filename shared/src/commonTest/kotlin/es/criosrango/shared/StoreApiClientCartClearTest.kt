package es.criosrango.shared

import es.criosrango.shared.api.InMemoryStoreSessionStore
import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.model.StoreCartRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Headers
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StoreApiClientCartClearTest {
    @Test fun clearUsesSameSessionAndNewItemDoesNotResurrectOldItem() = runTest {
        val session = InMemoryStoreSessionStore("cart-token-A", "nonce-A", "cookie-A=1")
        val requests = mutableListOf<io.ktor.client.request.HttpRequestData>()
        val engine = MockEngine { req ->
            requests += req
            when {
                req.method == HttpMethod.Delete && req.url.encodedPath.contains("/cart/items") -> respond(cartJson(emptyList()), headers = jsonHeaders())
                req.method == HttpMethod.Get && req.url.encodedPath.contains("/cart") -> respond(cartJson(emptyList()), headers = jsonHeaders())
                req.method == HttpMethod.Post && req.url.encodedPath.contains("/cart/add-item") -> respond(cartJson(listOf(itemJson("B", 2))), headers = jsonHeaders())
                else -> error("Unexpected request: " + req.method + " " + req.url)
            }
        }
        val client = StoreApiClient(client = HttpClient(engine), session = session)
        val cleared = client.clearCart()
        val added = client.addCartItem(StoreCartRequest(2, 1))
        assertEquals(emptyList(), cleared.items)
        assertEquals(listOf(2), added.items.map { it.id })
        assertEquals(1, requests.count { it.method == HttpMethod.Delete && it.url.encodedPath.endsWith("/cart/items") })
        assertEquals(1, requests.count { it.method == HttpMethod.Get && it.url.encodedPath.endsWith("/cart") })
        val clearRequest = requests.first { it.method == HttpMethod.Delete }
        assertEquals("cart-token-A", clearRequest.headers["Cart-Token"])
        assertEquals("nonce-A", clearRequest.headers["Nonce"])
        assertEquals("cookie-A=1", clearRequest.headers["Cookie"])
        client.close()
    }
    private fun jsonHeaders(): Headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    private fun cartJson(items: List<String>): String = """{"items":[${items.joinToString(",")}],"items_count":${items.size}}"""
    private fun itemJson(name: String, id: Int): String = """{"key":"$name","id":$id,"name":"$name","quantity":1}"""
}
