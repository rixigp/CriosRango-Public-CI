package es.criosrango.shared

import es.criosrango.shared.account.AccountClient
import es.criosrango.shared.account.AccountRepository
import es.criosrango.shared.account.AccountTokenStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoreAccountAuthStateTest {
    private class MemoryTokenStore(
        initialToken: String? = null,
        private val saveSucceeds: Boolean = true
    ) : AccountTokenStore {
        private var token: String? = initialToken
        override fun load(): String? = token
        override fun save(token: String): Boolean {
            if (!saveSucceeds || token.isBlank()) return false
            this.token = token
            return true
        }
        override fun clear() { token = null }
    }

    private fun repository(
        tokenStore: MemoryTokenStore,
        responseBody: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        onRequest: () -> Unit = {}
    ): AccountRepository {
        val client = HttpClient(MockEngine {
            onRequest()
            respond(
                content = responseBody,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return AccountRepository(
            tokenStore = tokenStore,
            client = AccountClient("https://test.example/wp-json/criosrango/v1/", client)
        )
    }

    private val loginResponse =
        """{"token":"session-token","user":{"id":7,"email":"user@example.com","display_name":"Test User"}}"""
    private val meResponse =
        """{"user":{"id":7,"email":"user@example.com","display_name":"Test User"}}"""

    @Test
    fun initialStateIsCheckingAndRestoreWithoutTokenBecomesUnauthenticated() = runBlocking {
        val state = StoreAccountSessionState(repository(MemoryTokenStore(), meResponse))
        assertEquals(StoreAccountAuthStatus.CHECKING, state.snapshot.value.status)
        state.restore()
        assertEquals(StoreAccountAuthStatus.UNAUTHENTICATED, state.snapshot.value.status)
        assertEquals(false, state.snapshot.value.loading)
    }

    @Test
    fun restoringSessionMoreThanOnceOnlyPerformsOneValidationRequest() = runBlocking {
        var requests = 0
        val state = StoreAccountSessionState(
            repository(MemoryTokenStore(initialToken = "session-token"), meResponse, onRequest = { requests++ })
        )

        state.restore()
        assertEquals(StoreAccountAuthStatus.AUTHENTICATED, state.snapshot.value.status)
        state.restore()

        assertEquals(1, requests, "Account remounts must not revalidate the session repeatedly")
        assertEquals(StoreAccountAuthStatus.AUTHENTICATED, state.snapshot.value.status)
    }

    @Test
    fun loginActionGateRejectsDuplicateSubmissionsUntilReleased() {
        val gate = StoreAccountActionGate()
        assertEquals(true, gate.tryAcquire())
        assertEquals(false, gate.tryAcquire())
        gate.release()
        assertEquals(true, gate.tryAcquire())
        gate.release()
    }

    @Test
    fun loginRequiresBothFieldsAndNormalizesServerErrors() = runBlocking {
        val state = StoreAccountSessionState(repository(MemoryTokenStore(), loginResponse))
        assertNull(state.login(" ", "password"))
        assertEquals("Introduce tu correo y contraseña.", state.snapshot.value.error)
        assertNull(state.login("user@example.com", " "))
        assertEquals("Introduce tu correo y contraseña.", state.snapshot.value.error)
        assertEquals("user@example.com", normalizeStoreLoginCredentials(" user@example.com ", "  pass  ").first)
        assertEquals("  pass  ", normalizeStoreLoginCredentials(" user@example.com ", "  pass  ").second)
        assertEquals("Correo electrónico o contraseña incorrectos.", normalizeStoreLoginError(401))
        assertEquals("Demasiados intentos. Espera unos minutos.", normalizeStoreLoginError(429))
        assertEquals("No hemos podido iniciar sesión.", normalizeStoreLoginError(500))
    }

    @Test
    fun successfulLoginPersistsTokenAndPublishesAuthenticatedUser() = runBlocking {
        val tokenStore = MemoryTokenStore()
        val state = StoreAccountSessionState(repository(tokenStore, loginResponse))
        val user = state.login(" user@example.com ", "  secret  ")
        assertEquals(7, user?.id)
        assertEquals("session-token", tokenStore.load())
        assertEquals(StoreAccountAuthStatus.AUTHENTICATED, state.snapshot.value.status)
        assertEquals("Test User", state.snapshot.value.user?.displayName)
        assertEquals(false, state.snapshot.value.loading)
    }

    @Test
    fun tokenPersistenceFailureDoesNotAuthenticate() = runBlocking {
        val tokenStore = MemoryTokenStore(saveSucceeds = false)
        val state = StoreAccountSessionState(repository(tokenStore, loginResponse))
        assertNull(state.login("user@example.com", "secret"))
        assertEquals(null, tokenStore.load())
        assertEquals(StoreAccountAuthStatus.UNAUTHENTICATED, state.snapshot.value.status)
        assertEquals("No se ha podido guardar la sesión de cuenta.", state.snapshot.value.error)
    }

    @Test
    fun restoreSuccessAndUnauthorizedFailureUpdateSessionState() = runBlocking {
        val tokenStore = MemoryTokenStore(initialToken = "session-token")
        val state = StoreAccountSessionState(repository(tokenStore, meResponse))
        state.restore()
        assertEquals(StoreAccountAuthStatus.AUTHENTICATED, state.snapshot.value.status)
        assertEquals(7, state.snapshot.value.user?.id)

        val expiredStore = MemoryTokenStore(initialToken = "expired-token")
        val expiredState = StoreAccountSessionState(repository(expiredStore, """{"message":"expired"}""", HttpStatusCode.Unauthorized))
        expiredState.restore()
        assertEquals(null, expiredStore.load())
        assertEquals(StoreAccountAuthStatus.UNAUTHENTICATED, expiredState.snapshot.value.status)
    }

    @Test
    fun transientRestoreFailureKeepsTokenAndCheckingStateAndLogoutClearsIt() = runBlocking {
        val tokenStore = MemoryTokenStore(initialToken = "session-token")
        val state = StoreAccountSessionState(repository(tokenStore, """{"message":"temporary"}""", HttpStatusCode.InternalServerError))
        state.restore()
        assertEquals("session-token", tokenStore.load())
        assertEquals(StoreAccountAuthStatus.CHECKING, state.snapshot.value.status)
        assertEquals("No hemos podido recuperar la sesión.", state.snapshot.value.error)
        state.logout()
        assertEquals(null, tokenStore.load())
        assertEquals(StoreAccountAuthStatus.UNAUTHENTICATED, state.snapshot.value.status)
    }
}
