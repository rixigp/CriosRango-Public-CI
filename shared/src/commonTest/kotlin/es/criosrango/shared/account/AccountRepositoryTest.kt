package es.criosrango.shared.account

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

private class FakeAccountTokenStore(private var value: String? = null) : AccountTokenStore {
    override fun load(): String? = value
    override fun save(token: String): Boolean {
        if (token.isBlank()) return false
        value = token
        return true
    }
    override fun clear() { value = null }
}

private class AccountRepositoryTestClaimOrderStore(private var value: PendingClaimOrder? = null) : ClaimOrderStore {
    override fun load(): PendingClaimOrder? = value
    override fun save(order: PendingClaimOrder): Boolean { value = order; return true }
    override fun clear() { value = null }
}

private class TrackingStoreSession {
    var cartToken: String? = "cart-A"
    var nonce: String? = "nonce-A"
    var cookies: String? = "cookie-A"
}

class AccountRepositoryTest {
    private fun client(
        status: HttpStatusCode = HttpStatusCode.OK,
        body: String
    ): AccountClient {
        val engine = MockEngine { request: HttpRequestData ->
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
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        )
    }

    @Test
    fun loginSuccess_persistsTokenAndReturnsUser() = runBlocking {
        val store = FakeAccountTokenStore()
        val repo = AccountRepository(store, client(body = """{"token":"tok-1","user":{"id":7,"email":"a@b.es","display_name":"Ana","birth_date":"1990-02-03"}}"""))
        val user = repo.login("  ana  ", "secret")
        assertEquals("tok-1", store.load())
        assertEquals(7, user.id)
        assertEquals("Ana", user.displayName)
        assertEquals("1990-02-03", user.birthDate)
    }

    @Test
    fun loginError_doesNotReplaceExistingToken() = runBlocking {
        val store = FakeAccountTokenStore("old-token")
        val repo = AccountRepository(store, client(HttpStatusCode.Unauthorized, """{"message":"bad"}"""))
        assertFailsWith<Exception> { repo.login("ana", "bad") }
        assertEquals("old-token", store.load())
    }

    @Test
    fun registerSuccess_persistsToken() = runBlocking {
        val store = FakeAccountTokenStore()
        val repo = AccountRepository(store, client(body = """{"token":"tok-r","user":{"id":8,"email":"r@b.es"}}"""))
        repo.register("r@b.es", "pw", "R", "User", "600")
        assertEquals("tok-r", store.load())
    }

    @Test
    fun forgotPassword_parsesMessage() = runBlocking {
        val repo = AccountRepository(FakeAccountTokenStore(), client(body = """{"success":true,"message":"Correo enviado"}"""))
        assertEquals("Correo enviado", repo.forgotPassword(" user@example.com "))
    }

    @Test
    fun me_sendsBearerAnd401ClearsTokenAndPendingClaim() = runBlocking {
        var authorization: String? = null
        val engine = MockEngine { request ->
            authorization = request.headers[HttpHeaders.Authorization]
            respond(
                content = """{"message":"unauthorized"}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val store = FakeAccountTokenStore("account-token")
        val claimStore = AccountRepositoryTestClaimOrderStore(PendingClaimOrder(101, "key-A"))
        val repo = AccountRepository(
            store,
            AccountClient("https://test.invalid/wp-json/criosrango/v1/", HttpClient(engine) {
                expectSuccess = true
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }),
            claimOrderStore = claimStore
        )
        assertFailsWith<Exception> { repo.me() }
        assertEquals("Bearer account-token", authorization)
        assertNull(store.load())
        assertNull(claimStore.load())
    }

    @Test
    fun logout_clearsTokenAndPendingThenNewSessionCannotClaimOldOrder() = runBlocking {
        var claimRequests = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/claim-order")) claimRequests++
            respond(content = """{"success":true,"order_id":101,"customer_id":7}""", status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        val tokenStore = FakeAccountTokenStore("token-A")
        val claimStore = AccountRepositoryTestClaimOrderStore(PendingClaimOrder(101, "key-A"))
        val repo = AccountRepository(tokenStore, AccountClient("https://test.invalid/wp-json/criosrango/v1/", HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }), claimStore)
        repo.logout()
        assertNull(tokenStore.load())
        assertNull(claimStore.load())
        tokenStore.save("token-B")
        assertNull(repo.claimPendingOrder())
        assertEquals(0, claimRequests)
    }

    @Test
    fun orders401_clearsTokenAndPendingClaim() = runBlocking {
        val engine = MockEngine {
            respond(content = """{"message":"unauthorized"}""", status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        val tokenStore = FakeAccountTokenStore("token-A")
        val claimStore = AccountRepositoryTestClaimOrderStore(PendingClaimOrder(101, "key-A"))
        val repo = AccountRepository(tokenStore, AccountClient("https://test.invalid/wp-json/criosrango/v1/", HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }), claimStore)
        assertFailsWith<io.ktor.client.plugins.ResponseException> { repo.orders() }
        assertNull(tokenStore.load())
        assertNull(claimStore.load())
    }

    @Test
    fun claimOrder401_clearsTokenAndPendingClaim() = runBlocking {
        val engine = MockEngine {
            respond(content = """{"message":"unauthorized"}""", status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        val tokenStore = FakeAccountTokenStore("token-A")
        val claimStore = AccountRepositoryTestClaimOrderStore(PendingClaimOrder(101, "key-A"))
        val repo = AccountRepository(tokenStore, AccountClient("https://test.invalid/wp-json/criosrango/v1/", HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }), claimStore)
        assertFailsWith<Exception> { repo.claimOrder(101, "key-A") }
        assertNull(tokenStore.load())
        assertNull(claimStore.load())
    }

    @Test
    fun processDeath_newRepositoryRestoresPersistedPendingClaim() = runBlocking {
        val tokenStore = FakeAccountTokenStore("token-A")
        val claimStore = AccountRepositoryTestClaimOrderStore(PendingClaimOrder(101, "key-A"))
        val first = AccountRepository(tokenStore, client(body = """{"user":{"id":7,"email":"a@b.es"}}"""), claimStore)
        val recreated = AccountRepository(tokenStore, client(body = """{"user":{"id":7,"email":"a@b.es"}}"""), claimStore)
        assertEquals("token-A", tokenStore.load())
        assertEquals(PendingClaimOrder(101, "key-A"), recreated.pendingClaimOrder())
        assertEquals(7, recreated.me().id)
    }

    @Test
    fun claim200_clearsMatchingPendingClaim() = runBlocking {
        val tokenStore = FakeAccountTokenStore("token-A")
        val claimStore = AccountRepositoryTestClaimOrderStore(PendingClaimOrder(101, "key-A"))
        val repo = AccountRepository(tokenStore, client(body = """{"success":true,"order_id":101,"customer_id":7}"""), claimStore)
        repo.claimOrder(101, "key-A")
        assertNull(claimStore.load())
    }

    @Test
    fun claim403_preservesTokenAndPendingAndMakesOneRequest() = runBlocking {
        var requests = 0
        val engine = MockEngine {
            requests++
            respond(content = """{"message":"forbidden"}""", status = HttpStatusCode.Forbidden,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        val tokenStore = FakeAccountTokenStore("token-A")
        val claimStore = AccountRepositoryTestClaimOrderStore(PendingClaimOrder(101, "key-A"))
        val repo = AccountRepository(tokenStore, AccountClient("https://test.invalid/wp-json/criosrango/v1/", HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }), claimStore)
        assertFailsWith<Exception> { repo.claimOrder(101, "key-A") }
        assertEquals("token-A", tokenStore.load())
        assertEquals(PendingClaimOrder(101, "key-A"), claimStore.load())
        assertEquals(1, requests)
    }

    @Test
    fun claim409_preservesTokenAndPendingAndMakesOneRequest() = runBlocking {
        var requests = 0
        val engine = MockEngine {
            requests++
            respond(content = """{"message":"conflict"}""", status = HttpStatusCode.Conflict,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        val tokenStore = FakeAccountTokenStore("token-A")
        val claimStore = AccountRepositoryTestClaimOrderStore(PendingClaimOrder(101, "key-A"))
        val repo = AccountRepository(tokenStore, AccountClient("https://test.invalid/wp-json/criosrango/v1/", HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }), claimStore)
        assertFailsWith<Exception> { repo.claimOrder(101, "key-A") }
        assertEquals("token-A", tokenStore.load())
        assertEquals(PendingClaimOrder(101, "key-A"), claimStore.load())
        assertEquals(1, requests)
    }

    @Test
    fun logout_clearsAccountTokenAndLeavesUnrelatedWooSessionUntouched() = runBlocking {
        val store = FakeAccountTokenStore("account-token")
        val woo = TrackingStoreSession()
        val repo = AccountRepository(store, client(body = ""))
        repo.logout()
        assertNull(store.load())
        assertEquals("cart-A", woo.cartToken)
        assertEquals("nonce-A", woo.nonce)
        assertEquals("cookie-A", woo.cookies)
    }

    @Test
    fun meTransportFailure_preservesPersistedToken() = runBlocking {
        val store = FakeAccountTokenStore("tok-persisted")
        val engine = MockEngine { throw io.ktor.utils.io.errors.IOException("network") }
        val repo = AccountRepository(
            store,
            AccountClient(
                "https://test.invalid/wp-json/criosrango/v1/",
                HttpClient(engine) {
                    expectSuccess = true
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }
            )
        )

        assertFailsWith<Exception> { repo.me() }
        assertEquals("tok-persisted", store.load())
        assertTrue(repo.hasSession)
    }

    @Test
    fun logoutTransportFailure_stillClearsPersistedToken() = runBlocking {
        val store = FakeAccountTokenStore("tok-logout")
        val engine = MockEngine { throw io.ktor.utils.io.errors.IOException("network") }
        val repo = AccountRepository(
            store,
            AccountClient(
                "https://test.invalid/wp-json/criosrango/v1/",
                HttpClient(engine) {
                    expectSuccess = true
                    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                }
            )
        )

        assertFailsWith<Exception> { repo.logout() }
        assertNull(store.load())
        assertTrue(!repo.hasSession)
    }

    @Test
    fun processDeath_newRepositoryRestoresPersistedToken() = runBlocking {
        val store = FakeAccountTokenStore()
        val first = AccountRepository(store, client(body = """{"token":"tok-p","user":{"id":9,"email":"p@b.es"}}"""))
        first.login("p", "pw")
        val recreated = AccountRepository(store, client(body = """{"user":{"id":9,"email":"p@b.es"}}"""))
        assertTrue(recreated.hasSession)
        assertEquals(9, recreated.me().id)
    }
}
