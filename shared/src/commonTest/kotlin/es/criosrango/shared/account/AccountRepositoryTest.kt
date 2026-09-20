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

private class FakeAccountTokenStore(private var value: String? = null) : AccountTokenStore {
    override fun load(): String? = value
    override fun save(token: String): Boolean {
        if (token.isBlank()) return false
        value = token
        return true
    }
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
            client = HttpClient(engine)
        )
    }

    @Test
    fun loginSuccess_persistsTokenAndReturnsUser() = runBlocking {
        val store = FakeAccountTokenStore()
        val repo = AccountRepository(store, client(body = """{"token":"tok-1","user":{"id":7,"email":"a@b.es","display_name":"Ana"}}"""))
        val user = repo.login("  ana  ", "secret")
        assertEquals("tok-1", store.load())
        assertEquals(7, user.id)
        assertEquals("Ana", user.displayName)
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
    fun me_sendsBearerAnd401ClearsOnlyAccountToken() = runBlocking {
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
        val repo = AccountRepository(
            store,
            AccountClient("https://test.invalid/wp-json/criosrango/v1/", HttpClient(engine))
        )
        assertFailsWith<Exception> { repo.me() }
        assertEquals("Bearer account-token", authorization)
        assertNull(store.load())
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
    fun processDeath_newRepositoryRestoresPersistedToken() = runBlocking {
        val store = FakeAccountTokenStore()
        val first = AccountRepository(store, client(body = """{"token":"tok-p","user":{"id":9,"email":"p@b.es"}}"""))
        first.login("p", "pw")
        val recreated = AccountRepository(store, client(body = """{"user":{"id":9,"email":"p@b.es"}}"""))
        assertTrue(recreated.hasSession)
        assertEquals(9, recreated.me().id)
    }
}
