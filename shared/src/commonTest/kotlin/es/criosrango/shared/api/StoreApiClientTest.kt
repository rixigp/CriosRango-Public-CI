
package es.criosrango.shared.api

import es.criosrango.shared.account.AccountTokenStore
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestData
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoreApiClientTest {

    @Test
    fun non2xxWooCommerceJsonBecomesStoreApiException() = kotlinx.coroutines.test.runTest {
        val client = mockClient(HttpStatusCode.BadRequest, """{"code":"woocommerce_test_code","message":"Mensaje real de WooCommerce"}""")
        val api = StoreApiClient(client = client)
        val exception = assertFailsWith<StoreApiException> { api.applyCoupon("TEST") }
        assertEquals(400, exception.statusCode)
        assertEquals("woocommerce_test_code", exception.apiCode)
        assertEquals("Mensaje real de WooCommerce", exception.message)
    }

    @Test
    fun non2xxUnparseableBodyUsesNonEmptyFallback() = kotlinx.coroutines.test.runTest {
        val client = mockClient(HttpStatusCode.BadGateway, "")
        val api = StoreApiClient(client = client)
        val exception = assertFailsWith<StoreApiException> { api.applyCoupon("TEST") }
        assertEquals(502, exception.statusCode)
        assertEquals(null, exception.apiCode)
        assertTrue(exception.message.isNotBlank())
    }

    @Test
    fun applyCoupon2xxStillDecodesCartNormally() = kotlinx.coroutines.test.runTest {
        val client = mockClient(HttpStatusCode.OK, "{}")
        val api = StoreApiClient(client = client)
        val cart = api.applyCoupon("TEST")
        assertTrue(cart.items.isEmpty())
        assertTrue(cart.coupons.isEmpty())
        assertTrue(cart.errors.isEmpty())
    }

    @Test
    fun withoutAccountTokenAuthorizationIsAbsent() = kotlinx.coroutines.test.runTest {
        val session = InMemoryStoreSessionStore(
            cartToken = "cart-token",
            nonce = "nonce",
            cookieHeader = "wordpress_logged_in=fake"
        )
        val tokenStore = FakeAccountTokenStore()
        var requestHeaders: io.ktor.http.Headers? = null
        val client = mockClient(HttpStatusCode.OK, "{}") { requestHeaders = it.headers }
        val api = StoreApiClient(
            client = client,
            session = session,
            accountTokenStore = tokenStore
        )

        api.applyCoupon("TEST")

        assertNull(requestHeaders?.get(HttpHeaders.Authorization))
    }

    @Test
    fun accountTokenIsSentAndExistingStoreHeadersArePreserved() = kotlinx.coroutines.test.runTest {
        val session = InMemoryStoreSessionStore(
            cartToken = "cart-token",
            nonce = "nonce",
            cookieHeader = "wordpress_logged_in=fake"
        )
        val tokenStore = FakeAccountTokenStore("123.abc")
        var requestHeaders: io.ktor.http.Headers? = null
        val client = mockClient(HttpStatusCode.OK, "{}") { requestHeaders = it.headers }
        val api = StoreApiClient(
            client = client,
            session = session,
            accountTokenStore = tokenStore
        )

        api.applyCoupon("TEST")

        assertEquals("Bearer 123.abc", requestHeaders?.get(HttpHeaders.Authorization))
        assertEquals("cart-token", requestHeaders?.get("Cart-Token"))
        assertEquals("nonce", requestHeaders?.get("Nonce"))
        assertEquals("wordpress_logged_in=fake", requestHeaders?.get(HttpHeaders.Cookie))
    }

    @Test
    fun accountTokenIsReadDynamicallyOnEachRequest() = kotlinx.coroutines.test.runTest {
        val tokenStore = FakeAccountTokenStore()
        var requestHeaders: io.ktor.http.Headers? = null
        val client = mockClient(HttpStatusCode.OK, "{}") { requestHeaders = it.headers }
        val api = StoreApiClient(
            client = client,
            accountTokenStore = tokenStore
        )

        api.applyCoupon("TEST")
        assertNull(requestHeaders?.get(HttpHeaders.Authorization))

        tokenStore.token = "123.abc"
        api.applyCoupon("TEST")
        assertEquals("Bearer 123.abc", requestHeaders?.get(HttpHeaders.Authorization))

        tokenStore.token = null
        api.applyCoupon("TEST")
        assertNull(requestHeaders?.get(HttpHeaders.Authorization))
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
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        return HttpClient(engine)
    }

    private class FakeAccountTokenStore(
        var token: String? = null
    ) : AccountTokenStore {
        override fun load(): String? = token
        override fun save(token: String): Boolean {
            this.token = token
            return true
        }
        override fun clear() {
            token = null
        }
    }
}
