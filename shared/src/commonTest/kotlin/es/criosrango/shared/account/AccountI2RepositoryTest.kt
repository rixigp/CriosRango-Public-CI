package es.criosrango.shared.account

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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeClaimOrderStore(initial: PendingClaimOrder? = null) : ClaimOrderStore {
    private var value = initial
    override fun load(): PendingClaimOrder? = value
    override fun save(order: PendingClaimOrder): Boolean {
        value = order
        return true
    }
    override fun clear() { value = null }
}

private class FakeI2TokenStore(private var value: String? = null) : AccountTokenStore {
    override fun load(): String? = value
    override fun save(token: String): Boolean { value = token; return true }
    override fun clear() { value = null }
}

class AccountI2RepositoryTest {
    private fun client(handler: suspend (HttpRequestData) -> io.ktor.client.engine.mock.MockHttpResponseData): AccountClient =
        AccountClient(
            baseUrl = "https://test.invalid/wp-json/criosrango/v1/",
            client = HttpClient(MockEngine { request -> handler(request) }) {
                expectSuccess = true
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        )

    @Test
    fun customerAddress_getUsesBearer() = runBlocking {
        var authorization: String? = null
        val repo = AccountRepository(
            FakeI2TokenStore("tok"),
            client = client { request ->
                authorization = request.headers[HttpHeaders.Authorization]
                respond(
                    """{"first_name":"Ana","last_name":"Test","address_1":"Calle 1","postcode":"28001","city":"Madrid","state":"M","country":"ES"}""",
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            }
        )
        assertEquals("Madrid", repo.customerAddress().city)
        assertEquals("Bearer tok", authorization)
    }

    @Test
    fun customerAddressSave_sendsBearerAndAddress() = runBlocking {
        var authorization: String? = null
        var requestPath: String? = null
        val repo = AccountRepository(
            FakeI2TokenStore("tok"),
            client = client { request ->
                authorization = request.headers[HttpHeaders.Authorization]
                requestPath = request.url.encodedPath
                respond("", headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            }
        )
        repo.saveCustomerAddress(AccountCustomerAddress(firstName = "Ana", city = "Madrid"))
        assertEquals("Bearer tok", authorization)
        assertEquals("/wp-json/criosrango/v1/customer-address-save", requestPath)
    }

    @Test
    fun customerAddress401_clearsOnlyAccountToken() = runBlocking {
        val repo = AccountRepository(
            FakeI2TokenStore("tok"),
            client = client {
                respond("""{"message":"unauthorized"}""", HttpStatusCode.Unauthorized,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            }
        )
        val store = repoTokenStore(repo)
        kotlin.test.assertFailsWith<Exception> { repo.customerAddress() }
        assertNull(store.load())
    }

    @Test
    fun ordersDetailed_parsesStatusAmountsProductsVariationsShippingAndPayment() = runBlocking {
        val repo = AccountRepository(
            FakeI2TokenStore("tok"),
            client = client {
                respond(
                    """{"orders":[{"id":42,"number":"1042","status":"processing","status_label":"Procesando","date_created":"2026-09-20T10:00:00","total":"59.90","currency":"EUR","payment_method":"bizum","payment_method_title":"Bizum","subtotal":"49.90","shipping_total":"10.00","shipping_method":"Envío 24/48h","shipping_address":{"address_1":"Calle 1","address_2":"","city":"Madrid","state":"M","postcode":"28001","country":"ES"},"items":[{"name":"Vestido","quantity":2,"variations":[{"name":"Tallas","value":"4A"},{"name":"Color","value":"Azul"}]}]}],"total":1}""",
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            }
        )
        val order = repo.orders().orders.single()
        assertEquals("processing", order.status)
        assertEquals("59.90", order.total)
        assertEquals("Bizum", order.paymentMethodTitle)
        assertEquals("Envío 24/48h", order.shippingMethod)
        assertEquals("4A", order.items.single().variations.first().value)
        assertEquals("Madrid", order.shippingAddress.city)
    }

    @Test
    fun claimSuccess_usesOrderIdAndOrderKey() = runBlocking {
        var requestBody: String? = null
        var authorization: String? = null
        val repo = AccountRepository(
            FakeI2TokenStore("tok"),
            client = client { request ->
                authorization = request.headers[HttpHeaders.Authorization]
                requestBody = request.body.toString()
                respond(
                    """{"success":true,"order_id":42,"customer_id":7}""",
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            }
        )
        val result = repo.claimOrder(42, "wc_order_key")
        assertTrue(result.success)
        assertEquals("Bearer tok", authorization)
        assertTrue(requestBody!!.contains("42"))
        assertTrue(requestBody!!.contains("wc_order_key"))
    }

    @Test
    fun claimTransportFailure_keepsPendingClaim() = runBlocking {
        val pending = FakeClaimOrderStore()
        val repo = AccountRepository(
            FakeI2TokenStore("tok"),
            client = client { throw io.ktor.utils.io.errors.IOException("network") },
            claimOrderStore = pending
        )
        repo.prepareClaimOrder(42, "key")
        kotlin.test.assertFailsWith<Exception> { repo.claimPendingOrder() }
        assertEquals(PendingClaimOrder(42, "key"), pending.load())
    }

    @Test
    fun repeatedClaim_reusesSameOrderIdentityWithoutCreatingOrder() = runBlocking {
        var claimCalls = 0
        val pending = FakeClaimOrderStore(PendingClaimOrder(42, "key"))
        val repo = AccountRepository(
            FakeI2TokenStore("tok"),
            client = client {
                claimCalls++
                respond(
                    """{"success":true,"order_id":42,"customer_id":7}""",
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            },
            claimOrderStore = pending
        )
        repo.claimPendingOrder()
        repo.prepareClaimOrder(42, "key")
        repo.claimPendingOrder()
        assertEquals(2, claimCalls)
        assertNull(pending.load())
    }

    @Test
    fun logoutBeforeClaim_makesNoClaimRequest() = runBlocking {
        var claimCalls = 0
        val pending = FakeClaimOrderStore(PendingClaimOrder(42, "key"))
        val repo = AccountRepository(
            FakeI2TokenStore("tok"),
            client = client {
                claimCalls++
                respond("""{"success":true,"order_id":42,"customer_id":7}""",
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            },
            claimOrderStore = pending
        )
        repo.logout()
        assertNull(repo.claimPendingOrder())
        assertEquals(0, claimCalls)
    }

    @Test
    fun processDeath_restoresPendingClaimWithoutSecondOrderOrPayment() = runBlocking {
        val tokenStore = FakeI2TokenStore("tok")
        val pending = FakeClaimOrderStore()
        val first = AccountRepository(tokenStore, client = client { respond("{}") }, claimOrderStore = pending)
        assertTrue(first.prepareClaimOrder(42, "key"))

        var claimCalls = 0
        val recreated = AccountRepository(
            tokenStore,
            client = client {
                claimCalls++
                respond("""{"success":true,"order_id":42,"customer_id":7}""",
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            },
            claimOrderStore = pending
        )
        val result = recreated.claimPendingOrder()
        assertNotNull(result)
        assertEquals(1, claimCalls)
        assertNull(pending.load())
    }

    @Test
    fun guestOrderThenLogin_claimsOnlyStoredOrderIdentity() = runBlocking {
        val tokenStore = FakeI2TokenStore()
        val pending = FakeClaimOrderStore()
        var claimCalls = 0
        val repo = AccountRepository(
            tokenStore,
            client = client { request ->
                when (request.url.encodedPath.substringAfterLast('/')) {
                    "login" -> respond(
                        """{"token":"tok","user":{"id":7,"email":"u@b.es"}}""",
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                    else -> {
                        claimCalls++
                        respond("""{"success":true,"order_id":42,"customer_id":7}""",
                            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                    }
                }
            },
            claimOrderStore = pending
        )
        assertTrue(repo.prepareClaimOrder(42, "key"))
        repo.login("u@b.es", "pw")
        repo.claimPendingOrder()
        assertEquals(1, claimCalls)
        assertNull(pending.load())
    }

    private fun repoTokenStore(repo: AccountRepository): AccountTokenStore {
        return repo.javaClass.getDeclaredField("tokenStore").let {
            it.isAccessible = true
            it.get(repo) as AccountTokenStore
        }
    }
}
