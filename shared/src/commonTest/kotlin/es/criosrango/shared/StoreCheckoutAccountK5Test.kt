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
                else -> respond("{}", headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
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

        val storeEngine = MockEngine {
            respond(
                "{}",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
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
