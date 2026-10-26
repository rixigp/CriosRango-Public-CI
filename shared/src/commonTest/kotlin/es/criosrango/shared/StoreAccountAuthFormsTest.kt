package es.criosrango.shared

import es.criosrango.shared.account.AccountClient
import es.criosrango.shared.account.AccountRepository
import es.criosrango.shared.account.AccountTokenStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
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
        response: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
        store: TokenStore = TokenStore()
    ): Pair<AccountRepository, TokenStore> {
        val http = HttpClient(MockEngine { request -> response(request) }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
        return AccountRepository(store, AccountClient("https://unit.test/", http)) to store
    }

    @Test
    fun registerValidationMatchesGoldenAndLeavesPasswordUntrimmed() {
        val owner = StoreRegisterOwner(repository(response = { _ -> error("should not call network") }).first)
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
    @Test
    fun registerDoubleSubmitIsBlockedAtOwner() = runTest {
        var calls = 0
        val (repository, _) = repository({
            calls++
            delay(100)
            respond("""{"token":"token","user":{"id":9,"email":"c@example.test"}}""", HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val owner = StoreRegisterOwner(repository)
        owner.update { it.copy(firstName = "C", lastName = "Test", email = "c@example.test", password = "12345678", repeatPassword = "12345678") }
        val first = async { owner.submit() }
        yield()
        val second = async { owner.submit() }
        assertNull(second.await())
        assertEquals(9, first.await()?.id)
        assertEquals(1, calls)
    }

    @Test
    fun malformedRegisterResponseUsesSafeCopy() = runTest {
        val (repository, _) = repository({
            respond("not-json", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })
        val owner = StoreRegisterOwner(repository)
        owner.update { it.copy(firstName = "D", lastName = "Test", email = "d@example.test", password = "12345678", repeatPassword = "12345678") }
        assertNull(owner.submit())
        assertEquals("No hemos podido crear la cuenta.", owner.state.value.requestError)
    }

    @Test
    fun networkExceptionIsNotExposedToRegisterOrForgotUi() = runTest {
        val (registerRepository, _) = repository({ throw IllegalStateException("secret socket stack") })
        val register = StoreRegisterOwner(registerRepository)
        register.update { it.copy(firstName = "E", lastName = "Test", email = "e@example.test", password = "12345678", repeatPassword = "12345678") }
        assertNull(register.submit())
        assertEquals("No hemos podido crear la cuenta.", register.state.value.requestError)
        assertFalse(register.state.value.requestError.orEmpty().contains("secret socket stack"))

        val (forgotRepository, _) = repository({ throw IllegalStateException("secret socket stack") })
        val forgot = StoreForgotPasswordOwner(forgotRepository)
        forgot.updateEmail("e@example.test")
        assertFalse(forgot.submit())
        assertEquals("No hemos podido solicitar el cambio de contraseña.", forgot.state.value.requestError)
        assertFalse(forgot.state.value.requestError.orEmpty().contains("secret socket stack"))
    }

}
