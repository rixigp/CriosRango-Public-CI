package es.criosrango.shared

import es.criosrango.shared.account.AccountClient
import es.criosrango.shared.account.AccountCustomerAddress
import es.criosrango.shared.account.AccountRepository
import es.criosrango.shared.account.AccountTokenStore
import es.criosrango.shared.account.AccountUser
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class ProfileAddressTokenStore(var token: String? = "token") : AccountTokenStore {
    override fun load(): String? = token
    override fun save(token: String): Boolean { this.token = token; return true }
    override fun clear() { token = null }
}

private val addressJson = """{"first_name":"Ana","last_name":"Pérez","email":"ana@example.com","phone":"+34600111222","address_1":"Calle Uno 12","address_2":"3º B","postcode":"28013","city":"Madrid","state":"M","country":"ES"}"""

private fun profileAddressRepository(
    tokenStore: ProfileAddressTokenStore,
    onRequest: suspend (String) -> Pair<HttpStatusCode, String>,
): AccountRepository {
    val engine = MockEngine { request ->
        val (status, body) = onRequest(request.url.encodedPath)
        respond(
            content = body,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
        )
    }
    val client = AccountClient(
        baseUrl = "https://test.invalid/wp-json/criosrango/v1/",
        client = HttpClient(engine) {
            expectSuccess = true
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    )
    return AccountRepository(tokenStore, client)
}

private fun successfulProfileAddressResponse(path: String): Pair<HttpStatusCode, String> =
    if (path.endsWith("/customer-address")) HttpStatusCode.OK to addressJson
    else HttpStatusCode.OK to "{}"

class StoreProfileAddressOwnerTest {
    @Test
    fun profileSavePreservesLoadedAddressAndPublishesUserToSession() = runBlocking {
        val tokens = ProfileAddressTokenStore()
        var savedRequests = 0
        val repository = profileAddressRepository(tokens) { path ->
            if (path.endsWith("/customer-address-save")) savedRequests++
            successfulProfileAddressResponse(path)
        }
        val session = StoreAccountSessionState(repository)
        val owner = StoreProfileOwner(repository, session)
        val originalUser = AccountUser(id = 7, email = "ana@example.com", firstName = "Old", lastName = "Name")

        val updated = owner.save(originalUser, " Ana ", " Pérez ")

        assertEquals("Ana", updated?.firstName)
        assertEquals("Pérez", updated?.lastName)
        assertEquals(1, savedRequests)
        assertEquals(StoreAccountAuthStatus.AUTHENTICATED, session.snapshot.value.status)
        assertEquals(updated, session.snapshot.value.user)
        // The profile owner sends only a copy with the two editable fields changed.
        val loaded = repository.customerAddress()
        val merged = StoreAccountFormValidation.mergeProfileNames(loaded, "Ana", "Pérez")
        assertEquals(loaded.copy(firstName = "Ana", lastName = "Pérez"), merged)
        assertEquals("Calle Uno 12", merged.address1)
        assertEquals("3º B", merged.address2)
        assertEquals("28013", merged.postcode)
        assertEquals("Madrid", merged.city)
        assertEquals("M", merged.state)
        assertEquals("ES", merged.country)
        assertEquals("ana@example.com", merged.email)
        assertEquals("+34600111222", merged.phone)
    }

    @Test
    fun profileBackendErrorDoesNotPublishUpdatedUser() = runBlocking {
        val tokens = ProfileAddressTokenStore()
        val repository = profileAddressRepository(tokens) { path ->
            if (path.endsWith("/customer-address-save")) HttpStatusCode.InternalServerError to """{"message":"failed"}"""
            else successfulProfileAddressResponse(path)
        }
        val session = StoreAccountSessionState(repository)
        val owner = StoreProfileOwner(repository, session)
        val user = AccountUser(id = 7, email = "ana@example.com", firstName = "Old", lastName = "Name")

        val failure = runCatching { owner.save(user, "Ana", "Pérez") }.exceptionOrNull()

        assertNotNull(failure)
        assertNull(session.snapshot.value.user)
        assertTrue(repository.hasSession)
    }

    @Test
    fun profileSaveInvalidatesExpiredSession() = runBlocking {
        val tokens = ProfileAddressTokenStore()
        val repository = profileAddressRepository(tokens) { path ->
            if (path.endsWith("/customer-address")) HttpStatusCode.Unauthorized to """{"message":"expired"}"""
            else successfulProfileAddressResponse(path)
        }
        val owner = StoreProfileOwner(repository, StoreAccountSessionState(repository))
        val user = AccountUser(id = 7, email = "ana@example.com", firstName = "Old", lastName = "Name")

        runCatching { owner.save(user, "Ana", "Pérez") }

        assertFalse(repository.hasSession)
        assertNull(tokens.load())
    }

    @Test
    fun addressOwnerCachesLoadAndPublishesSavedAddressWithoutDroppingEmailOrPhone() = runBlocking {
        val tokens = ProfileAddressTokenStore()
        var addressLoads = 0
        val repository = profileAddressRepository(tokens) { path ->
            if (path.endsWith("/customer-address")) addressLoads++
            successfulProfileAddressResponse(path)
        }
        val owner = StoreAddressOwner(repository)

        owner.load()
        owner.load()
        assertEquals(1, addressLoads)
        owner.edit(owner.snapshot.value.address.copy(city = " Córdoba ", address2 = " 4º A "))
        assertTrue(owner.save())

        val saved = owner.snapshot.value.address
        assertEquals("Córdoba", saved.city)
        assertEquals("4º A", saved.address2)
        assertEquals("ES", saved.country)
        assertEquals("ana@example.com", saved.email)
        assertEquals("+34600111222", saved.phone)
        assertEquals("Dirección actualizada", owner.snapshot.value.notice)
        assertFalse(owner.snapshot.value.saving)
    }

    @Test
    fun addressBackendErrorIsExposedAndDoesNotInvalidateSession() = runBlocking {
        val tokens = ProfileAddressTokenStore()
        val repository = profileAddressRepository(tokens) { path ->
            if (path.endsWith("/customer-address-save")) HttpStatusCode.InternalServerError to """{"message":"failed"}"""
            else successfulProfileAddressResponse(path)
        }
        val owner = StoreAddressOwner(repository)
        owner.load()
        owner.edit(owner.snapshot.value.address.copy(city = "Córdoba"))

        assertFalse(owner.save())
        assertNotNull(owner.snapshot.value.error)
        assertTrue(repository.hasSession)
        assertFalse(owner.snapshot.value.saving)
    }

    @Test
    fun concurrentAddressSaveIsBlockedAtOwnerLevel() = runBlocking {
        val tokens = ProfileAddressTokenStore()
        val saveStarted = CompletableDeferred<Unit>()
        val allowSaveToFinish = CompletableDeferred<Unit>()
        val repository = profileAddressRepository(tokens) { path ->
            if (path.endsWith("/customer-address-save")) {
                saveStarted.complete(Unit)
                allowSaveToFinish.await()
            }
            successfulProfileAddressResponse(path)
        }
        val owner = StoreAddressOwner(repository)
        owner.load()
        val first = async { owner.save() }
        saveStarted.await()

        val duplicateAccepted = owner.save()
        assertFalse(duplicateAccepted)
        allowSaveToFinish.complete(Unit)
        assertTrue(first.await())
    }

    @Test
    fun concurrentProfileSaveIsBlockedAtOwnerLevel() = runBlocking {
        val tokens = ProfileAddressTokenStore()
        val addressRead = CompletableDeferred<Unit>()
        val allowAddressRead = CompletableDeferred<Unit>()
        val repository = profileAddressRepository(tokens) { path ->
            if (path.endsWith("/customer-address")) {
                addressRead.complete(Unit)
                allowAddressRead.await()
            }
            successfulProfileAddressResponse(path)
        }
        val session = StoreAccountSessionState(repository)
        val owner = StoreProfileOwner(repository, session)
        val user = AccountUser(id = 7, email = "ana@example.com", firstName = "Old", lastName = "Name")
        val first = async { owner.save(user, "Ana", "Pérez") }
        addressRead.await()

        assertNull(owner.save(user, "Ana", "Pérez"))
        allowAddressRead.complete(Unit)
        assertNotNull(first.await())
        Unit
    }
}
