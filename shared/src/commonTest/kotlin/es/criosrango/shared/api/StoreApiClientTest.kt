package es.criosrango.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    private fun mockClient(status: HttpStatusCode, body: String): HttpClient {
        val engine = MockEngine { _ ->
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        return HttpClient(engine)
    }
}