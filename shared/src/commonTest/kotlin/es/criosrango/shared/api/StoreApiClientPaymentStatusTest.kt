package es.criosrango.shared.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StoreApiClientPaymentStatusTest {
    private fun client(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String
    ): HttpClient = HttpClient(MockEngine { request ->
        assertEquals("/wp-json/criosrango/v1/payment-status", request.url.encodedPath)
        assertEquals("55841", request.url.parameters["order_id"])
        assertEquals("wc_order_abc", request.url.parameters["key"])
        assertEquals("cart-123", request.headers["Cart-Token"])
        assertEquals("nonce-456", request.headers["Nonce"])
        assertEquals("woocommerce_cart_hash=hash-789", request.headers["Cookie"])
        respond(
            body,
            status,
            headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        )
    }) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    @Test
    fun paidResponseUsesSharedPaymentStatusContract() = runTest {
        val api = StoreApiClient(
            client = client(body = """{"order_id":55841,"status":"processing","paid":true,"needs_payment":false,"terminal":true}"""),
            session = InMemoryStoreSessionStore("cart-123", "nonce-456", "woocommerce_cart_hash=hash-789")
        )

        val result = api.paymentStatus(55841, "wc_order_abc")

        assertEquals(55841, result.id)
        assertEquals("processing", result.status)
        assertTrue(result.paid)
        assertEquals(false, result.needsPayment)
        assertTrue(result.terminal)
        api.close()
    }

    @Test
    fun notPaidResponseUsesSharedPaymentStatusContract() = runTest {
        val api = StoreApiClient(
            client = client(body = """{"order_id":55841,"status":"pending","paid":false,"needs_payment":true,"terminal":false}"""),
            session = InMemoryStoreSessionStore("cart-123", "nonce-456", "woocommerce_cart_hash=hash-789")
        )

        val result = api.paymentStatus(55841, "wc_order_abc")

        assertEquals(55841, result.id)
        assertEquals("pending", result.status)
        assertEquals(false, result.paid)
        assertTrue(result.needsPayment)
        assertEquals(false, result.terminal)
        api.close()
    }

    @Test
    fun httpErrorPreservesSharedApiError() = runTest {
        val api = StoreApiClient(
            client = client(HttpStatusCode.InternalServerError, """{"code":"payment_status_error","message":"temporary"}"""),
            session = InMemoryStoreSessionStore("cart-123", "nonce-456", "woocommerce_cart_hash=hash-789")
        )

        val error = assertFailsWith<StoreApiException> {
            api.paymentStatus(55841, "wc_order_abc")
        }

        assertEquals(500, error.statusCode)
        assertEquals("payment_status_error", error.apiCode)
        assertEquals("temporary", error.message)
        api.close()
    }

    @Test
    fun malformedResponseIsReportedAsDecodeErrorWithoutProcessCrash() = runTest {
        val api = StoreApiClient(
            client = client(body = """{"order_id":"""),
            session = InMemoryStoreSessionStore("cart-123", "nonce-456", "woocommerce_cart_hash=hash-789")
        )

        assertFailsWith<SerializationException> {
            api.paymentStatus(55841, "wc_order_abc")
        }
        api.close()
    }
}
