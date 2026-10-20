package es.criosrango.shared

import es.criosrango.shared.account.AccountRepository
import es.criosrango.shared.account.AccountTokenStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class StoreAccountAuthStateTest {
    private class MemoryTokenStore : AccountTokenStore {
        private var token: String? = null
        override fun load(): String? = token
        override fun save(token: String): Boolean { this.token = token; return true }
        override fun clear() { token = null }
    }

    @Test
    fun initialStateIsCheckingAndRestoreWithoutTokenBecomesUnauthenticated() = runBlocking {
        val state = StoreAccountSessionState(AccountRepository(MemoryTokenStore()))
        assertEquals(StoreAccountAuthStatus.CHECKING, state.snapshot.value.status)
        state.restore()
        assertEquals(StoreAccountAuthStatus.UNAUTHENTICATED, state.snapshot.value.status)
        assertEquals(false, state.snapshot.value.loading)
    }

    @Test
    fun loginRequiresBothFieldsAndNormalizesServerErrors() = runBlocking {
        val state = StoreAccountSessionState(AccountRepository(MemoryTokenStore()))
        assertNull(state.login(" ", "password"))
        assertEquals("Introduce tu correo y contraseña.", state.snapshot.value.error)
        assertNull(state.login("user@example.com", " "))
        assertEquals("Introduce tu correo y contraseña.", state.snapshot.value.error)
        // The repository contract trims login identifiers, but never trims passwords.
        assertEquals("Correo electrónico o contraseña incorrectos.", normalizeStoreLoginError(401))
        assertEquals("Demasiados intentos. Espera unos minutos.", normalizeStoreLoginError(429))
        assertEquals("No hemos podido iniciar sesión.", normalizeStoreLoginError(500))
    }
}
