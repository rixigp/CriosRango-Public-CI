package es.criosrango.shared.account

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeK5TokenStore(private var value: String? = null) : AccountTokenStore {
    override fun load(): String? = value
    override fun save(token: String): Boolean { value = token; return true }
    override fun clear() { value = null }
}
private class FakeK5ClaimStore(private var value: PendingClaimOrder? = null) : ClaimOrderStore {
    override fun load(): PendingClaimOrder? = value
    override fun save(order: PendingClaimOrder): Boolean { value = order; return true }
    override fun clear() { value = null }
}
class K5ClaimOrderSemanticsTest {
    private fun client(): AccountClient {
        val engine = MockEngine { request ->
            when (request.url.encodedPath.substringAfterLast('/')) {
                "claim-order" -> respond("""{"success":true,"order_id":42,"customer_id":7}""", headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
                else -> respond("""{"token":"tok","user":{"id":7,"email":"u@b.es"}}""", headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
            }
        }
        return AccountClient("https://test.invalid/wp-json/criosrango/v1/", HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        })
    }
    @Test
    fun prepareThenAuthenticatedClaimClearsPendingWithoutTouchingSession() = runBlocking {
        val token = FakeK5TokenStore("tok")
        val pending = FakeK5ClaimStore()
        val repo = AccountRepository(token, client(), pending)
        assertTrue(repo.prepareClaimOrder(42, "key"))
        assertEquals(PendingClaimOrder(42, "key"), repo.pendingClaimOrder())
        assertEquals(42, repo.claimPendingOrder()?.orderId)
        assertNull(repo.pendingClaimOrder())
        assertTrue(repo.hasSession)
    }
}
