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
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeClaimOrderStore(initial: PendingClaimOrder? = null) : ClaimOrderStore {
    private var value = initial
    override fun load(): PendingClaimOrder? = value
    override fun save(order: PendingClaimOrder): Boolean { value = order; return true }
    override fun clear() { value = null }
}

private class FakeI2TokenStore(private var value: String? = null) : AccountTokenStore {
    override fun load(): String? = value
    override fun save(token: String): Boolean { value = token; return true }
    override fun clear() { value = null }
}

class AccountI2RepositoryTest {
    private fun client(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        capture: ((HttpRequestData) -> Unit)? = null
    ): AccountClient {
        val engine = MockEngine { request ->
            capture?.invoke(request)
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        return AccountClient(
            baseUrl = "https://test.invalid/wp-json/criosrango/v1/",
            client = HttpClient(engine) {
                expectSuccess = true
                install(ContentNegotiation) { json(Json {
                    ignoreUnknownKeys = true
                    coerceInputValues = true
                    exceptionsWithDebugInfo = false
                }) }
            }
        )
    }

    @Test
    fun customerAddress_getUsesBearer() = runBlocking {
        var authorization: String? = null
        val repo = AccountRepository(
            FakeI2TokenStore("tok"),
            client = client(
                """{"first_name":"Ana","last_name":"Test","address_1":"Calle 1","postcode":"28001","city":"Madrid","state":"M","country":"ES"}""",
                capture = { authorization = it.headers[HttpHeaders.Authorization] }
            )
        )
        assertEquals("Madrid", repo.customerAddress().city)
        assertEquals("Bearer tok", authorization)
    }

    @Test
    fun customerAddressSave_sendsBearerAndCorrectEndpoint() = runBlocking {
        var authorization: String? = null
        var requestPath: String? = null
        val repo = AccountRepository(
            FakeI2TokenStore("tok"),
            client = client(
                "",
                capture = {
                    authorization = it.headers[HttpHeaders.Authorization]
                    requestPath = it.url.encodedPath
                }
            )
        )
        repo.saveCustomerAddress(AccountCustomerAddress(firstName = "Ana", city = "Madrid"))
        assertEquals("Bearer tok", authorization)
        assertEquals("/wp-json/criosrango/v1/customer-address-save", requestPath)
    }

    @Test
    fun customerAddress401_clearsOnlyAccountToken() = runBlocking {
        val store = FakeI2TokenStore("tok")
        val repo = AccountRepository(
            store,
            client = client("""{"message":"unauthorized"}""", HttpStatusCode.Unauthorized)
        )
        assertFailsWith<Exception> { repo.customerAddress() }
        assertNull(store.load())
    }

    @Test
    fun ordersDetailed_parsesStatusAmountsProductsVariationsShippingAndPayment() = runBlocking {
        val repo = AccountRepository(
            FakeI2TokenStore("tok"),
            client = client(
                """{"orders":[{"id":42,"number":"1042","status":"processing","status_label":"Procesando","date_created":"2026-09-20T10:00:00","total":"59.90","currency":"EUR","payment_method":"bizum","payment_method_title":"Bizum","subtotal":"49.90","shipping_total":"10.00","shipping_method":"Envío 24/48h","shipping_address":{"address_1":"Calle 1","address_2":"","city":"Madrid","state":"M","postcode":"28001","country":"ES"},"items":[{"name":"Vestido","quantity":2,"variations":[{"name":"Tallas","value":"4A"},{"name":"Color","value":"Azul"}]}]}],"total":1}"""
            )
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
    fun ordersDetailed_legacyGsonCompatiblePayloadParsesNullsNumbersPartialAddressAndMultipleOrders() = runBlocking {
        val repo = AccountRepository(
            FakeI2TokenStore("tok"),
            client = client(
                """{
                    "orders":[
                        {
                            "id":42,
                            "number":1042,
                            "status":"processing",
                            "status_label":null,
                            "date_created":null,
                            "total":59.90,
                            "currency":"EUR",
                            "payment_method_title":null,
                            "items":[
                                {"name":"Vestido","quantity":2,"variations":null},
                                {"name":"Camiseta","quantity":1,"variations":[{"name":"Talla","value":4}]}
                            ],
                            "subtotal":49.90,
                            "shipping_total":10,
                            "shipping_method":null,
                            "shipping_address":{"city":"Madrid","postcode":28001}
                        },
                        {
                            "id":43,
                            "number":"1043",
                            "status":"completed",
                            "status_label":"",
                            "date_created":"2026-08-01T12:00:00",
                            "total":"29.90",
                            "currency":"EUR",
                            "payment_method_title":"Tarjeta",
                            "items":[],
                            "subtotal":"29.90",
                            "shipping_total":"0",
                            "shipping_method":"Envío",
                            "shipping_address":null
                        }
                    ],
                    "total":2
                }"""
            )
        )

        val orders = repo.orders().orders

        assertEquals(2, orders.size)
        assertEquals("1042", orders[0].number)
        assertEquals("", orders[0].statusLabel)
        assertNull(orders[0].dateCreated)
        assertEquals("59.9", orders[0].total)
        assertEquals("10", orders[0].shippingTotal)
        assertEquals("Madrid", orders[0].shippingAddress.city)
        assertEquals("", orders[0].shippingMethod)
        assertEquals(2, orders[0].items.size)
        assertTrue(orders[0].items[0].variations.isEmpty())
        assertEquals("4", orders[0].items[1].variations.single().value)
        assertEquals("1043", orders[1].number)
        assertEquals("Tarjeta", orders[1].paymentMethodTitle)
        assertEquals("", orders[1].shippingAddress.city)
    }

    @Test
    fun claimSuccess_usesOrderIdAndOrderKey() = runBlocking {
        var requestPath: String? = null
        var authorization: String? = null
        val repo = AccountRepository(
            FakeI2TokenStore("tok"),
            client = client(
                """{"success":true,"order_id":42,"customer_id":7}""",
                capture = {
                    requestPath = it.url.encodedPath
                    authorization = it.headers[HttpHeaders.Authorization]
                }
            )
        )
        val result = repo.claimOrder(42, "wc_order_key")
        assertTrue(result.success)
        assertEquals("/wp-json/criosrango/v1/claim-order", requestPath)
        assertEquals("Bearer tok", authorization)
    }

    @Test
    fun claimTransportFailure_keepsPendingClaim() = runBlocking {
        val pending = FakeClaimOrderStore()
        val engine = MockEngine { throw io.ktor.utils.io.errors.IOException("network") }
        val repo = AccountRepository(
            FakeI2TokenStore("tok"),
            AccountClient(
                "https://test.invalid/wp-json/criosrango/v1/",
                HttpClient(engine) {
                    expectSuccess = true
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }
            ),
            pending
        )
        assertTrue(repo.prepareClaimOrder(42, "key"))
        assertFailsWith<Exception> { repo.claimPendingOrder() }
        assertEquals(PendingClaimOrder(42, "key"), pending.load())
    }

    @Test
    fun repeatedClaim_reusesSameOrderIdentityWithoutCreatingOrder() = runBlocking {
        var claimCalls = 0
        val pending = FakeClaimOrderStore(PendingClaimOrder(42, "key"))
        val repo = AccountRepository(
            FakeI2TokenStore("tok"),
            client = client(
                """{"success":true,"order_id":42,"customer_id":7}""",
                capture = { claimCalls++ }
            ),
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
            client = client(
                """{"success":true,"order_id":42,"customer_id":7}""",
                capture = {
                    if (it.url.encodedPath.endsWith("/claim-order")) claimCalls++
                }
            ),
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
        val first = AccountRepository(
            tokenStore,
            client = client("{}"),
            claimOrderStore = pending
        )
        assertTrue(first.prepareClaimOrder(42, "key"))

        var claimCalls = 0
        val recreated = AccountRepository(
            tokenStore,
            client = client(
                """{"success":true,"order_id":42,"customer_id":7}""",
                capture = { claimCalls++ }
            ),
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
        val engine = MockEngine { request ->
            when (request.url.encodedPath.substringAfterLast('/')) {
                "login" -> respond(
                    """{"token":"tok","user":{"id":7,"email":"u@b.es"}}""",
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
                "claim-order" -> {
                    claimCalls++
                    respond(
                        """{"success":true,"order_id":42,"customer_id":7}""",
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
                else -> respond("{}", headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            }
        }
        val repo = AccountRepository(
            tokenStore,
            AccountClient(
                "https://test.invalid/wp-json/criosrango/v1/",
                HttpClient(engine) {
                    expectSuccess = true
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }
            ),
            pending
        )
        assertTrue(repo.prepareClaimOrder(42, "key"))
        repo.login("u@b.es", "pw")
        repo.claimPendingOrder()
        assertEquals(1, claimCalls)
        assertNull(pending.load())
    }
}
