package es.criosrango.shared

import es.criosrango.shared.account.AccountClient
import es.criosrango.shared.account.AccountRepository
import es.criosrango.shared.account.AccountTokenStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoreAccountAuthFormsTest {
    private class TokenStore(private val allowSave: Boolean = true) : AccountTokenStore {
        var token: String? = null
        override fun load(): String? = token
        override fun save(token: String): Boolean { if (!allowSave) return false; this.token = token; return true }
        override fun clear() { token = null }
    }

    private fun repository(
        response: suspend (io.ktor.client.request.HttpRequestData) -> io.ktor.client.statement.HttpResponseData,
        store: TokenStore = TokenStore()
    ): Pair<AccountRepository, TokenStore> {
        val http = HttpClient(MockEngine { request -> response(request) }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return AccountRepository(store, AccountClient("https://unit.test/", http)) to store
    }

    @Test
    fun registerValidationMatchesGoldenAndLeavesPasswordUntrimmed() {
        val owner = StoreRegisterOwner(repository { error("should not call network") }.first)
        assertTrue(owner.validate().containsKey("firstName"))
        assertTrue(owner.validate().containsKey("lastName"))
        assertTrue(owner.validate().containsKey("email"))
        owner.update { it.copy(firstName = " Ana ", lastName = " Ruiz ", email = " ana@example.test ", password = "1234567", repeatPassword = "1234567") }
        assertTrue(owner.validate().containsKey("password"))
        owner.update { it.copy(password = " pass123 ", repeatPassword = " pass123 ") }
        assertTrue(owner.validate().isEmpty())
        assertEquals(" pass123 ", owner.state.value.password)
        assertEquals("", owner.state.value.phone)
        assertNull(owner.state.value.birthDate)
        owner.update { it.copy(repeatPassword = "different") }
        assertTrue(owner.validate().containsKey("repeatPassword"))
    }

    @Test
    fun registerSuccessPersistsTokenAndReturnsUser() = runTest {
        val (repository, store) = repository({
            respond("""{"token":"saved-token","user":{"id":7,"email":"a@example.test"}}""", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val owner = StoreRegisterOwner(repository)
        owner.update { it.copy(firstName = "Ana", lastName = "Ruiz", email = "a@example.test", password = "12345678", repeatPassword = "12345678") }
        assertEquals(7, owner.submit()?.id)
        assertEquals("saved-token", store.token)
    }

    @Test
    fun registerTokenPersistenceFailureIsSafeAndDoesNotRetry() = runTest {
        var calls = 0
        val (repository, _) = repository({
            calls++
            respond("""{"token":"token","user":{"id":8,"email":"b@example.test"}}""", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }, TokenStore(allowSave = false))
        val owner = StoreRegisterOwner(repository)
        owner.update { it.copy(firstName = "B", lastName = "Test", email = "b@example.test", password = "12345678", repeatPassword = "12345678") }
        assertNull(owner.submit())
        assertTrue(owner.state.value.requestError.orEmpty().contains("No vuelvas a registrarte"))
        assertEquals(1, calls)
    }

    @Test
    fun registerDuplicateAndRateLimitErrorsAreNormalized() = runTest {
        for ((status, expected) in listOf(409 to "Ya existe", 429 to "Demasiados intentos")) {
            val (repository, _) = repository({
                respond("""{"message":"technical backend detail"}""", HttpStatusCode.fromValue(status),
                    headersOf(HttpHeaders.ContentType, "application/json"))
            })
            val owner = StoreRegisterOwner(repository)
            owner.update { it.copy(firstName = "A", lastName = "B", email = "a@example.test", password = "12345678", repeatPassword = "12345678") }
            assertNull(owner.submit())
            assertTrue(owner.state.value.requestError.orEmpty().contains(expected))
            assertFalse(owner.state.value.requestError.orEmpty().contains("technical backend"))
        }
    }

    @Test
    fun forgotSuccessFalseIsNotSuccessAndServerMessageIsHidden() = runTest {
        val (repository, _) = repository({
            respond("""{"success":false,"message":"account exists"}""", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val owner = StoreForgotPasswordOwner(repository)
        owner.updateEmail(" a@example.test ")
        assertFalse(owner.submit())
        assertFalse(owner.state.value.success)
        assertTrue(owner.state.value.requestError.orEmpty().contains("No hemos podido"))
        assertFalse(owner.state.value.requestError.orEmpty().contains("account exists"))
    }

    @Test
    fun forgotSuccessUsesGenericResultAndRejectsBlankEmail() = runTest {
        val (repository, _) = repository({
            respond("""{"success":true,"message":"account-specific response"}""", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val owner = StoreForgotPasswordOwner(repository)
        assertFalse(owner.submit())
        assertEquals("Introduce tu correo electrónico.", owner.state.value.validationError)
        owner.updateEmail("a@example.test")
        assertTrue(owner.submit())
        assertTrue(owner.state.value.success)
    }
}
