package es.criosrango.shared

import es.criosrango.shared.account.AccountCustomerAddress
import es.criosrango.shared.account.AccountClient
import es.criosrango.shared.account.AccountRepository
import es.criosrango.shared.account.AccountTokenStore
import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.api.InMemoryStoreSessionStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class K5CheckoutTokenStore : AccountTokenStore {
    private var token: String? = "account-token"
    override fun load(): String? = token
    override fun save(token: String): Boolean { this.token = token; return true }
    override fun clear() { token = null }
}

class StoreCheckoutAccountK5Test {
    @Test
    fun restoredSessionPreloadsAddressAndUpdatedAddressIsUsedOnNextLoad() = runTest {
        var address = AccountCustomerAddress(
            firstName = "Ana",
            lastName = "Test",
            email = "ana@example.com",
            address1 = "Calle 1",
            postcode = "28001",
            city = "Madrid",
            state = "M",
            country = "ES"
        )
        var savedAddress = address.copy()
        val accountEngine = MockEngine { request ->
            when (request.url.encodedPath.substringAfterLast('/')) {
                "customer-address" -> respond(
                    Json.encodeToString(AccountCustomerAddress.serializer(), address),
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
                "customer-address-save" -> {
                    address = savedAddress
                    respond("", headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                }
                else -> error("Unexpected Account endpoint: ${request.url.encodedPath}")
            }
        }
        val accountClient = AccountClient(
            "https://test.invalid/wp-json/criosrango/v1/",
            HttpClient(accountEngine) {
                expectSuccess = true
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        )
        val repository = AccountRepository(K5CheckoutTokenStore(), accountClient)
        assertTrue(repository.hasSession, "Expected restored Account session")

        val storeCartJson = """
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
        val storeCheckoutJson = """{"order_id":321,"order_key":"wc_order_key","status":"pending","payment_method":"cecabank_gateway","payment_methods":["cecabank_gateway","cheque"],"payment_requirements":[],"redirect_url":"https://pay.example/321","totals":{"total_price":"12300"},"errors":[]}"""
        val storeEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/wp-json/wc/store/v1/cart" -> respond(
                    storeCartJson,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
                "/wp-json/wc/store/v1/checkout" -> respond(
                    storeCheckoutJson,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
                else -> error("Unexpected Store endpoint: ${request.url.encodedPath}")
            }
        }
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val api = StoreApiClient(
            "https://test.invalid/wp-json/wc/store/v1/",
            HttpClient(storeEngine) {
                expectSuccess = true
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
            InMemoryStoreSessionStore()
        )
        val cartStore = StoreCartStore(api, scope)
        val checkoutStore = StoreCheckoutStore(api, cartStore, repository, scope)

        checkoutStore.load()
        advanceUntilIdle()
        assertEquals("Madrid", checkoutStore.accountAddress.value?.city)

        val updated = address.copy(city = "Toledo")
        savedAddress = updated
        repository.saveCustomerAddress(updated)
        checkoutStore.load()
        advanceUntilIdle()
        assertEquals("Toledo", checkoutStore.accountAddress.value?.city)
    }
}
