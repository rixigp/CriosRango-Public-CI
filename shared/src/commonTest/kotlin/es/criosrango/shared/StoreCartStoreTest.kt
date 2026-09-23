package es.criosrango.shared

import es.criosrango.shared.api.InMemoryStoreSessionStore
import es.criosrango.shared.api.StoreSessionStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoreCartStoreTest {
    @Test
    fun sessionStoreKeepsCartCredentialsAcrossReads() {
        val session = InMemoryStoreSessionStore()
        session.cartToken = "cart-token"
        session.nonce = "nonce"
        session.cookieHeader = "wordpress_logged_in=x"
        assertEquals("cart-token", session.cartToken)
        assertEquals("nonce", session.nonce)
        assertEquals("wordpress_logged_in=x", session.cookieHeader)
    }

    @Test
    fun emptySessionStartsWithoutCartCredentials() {
        val session: StoreSessionStore = InMemoryStoreSessionStore()
        assertNull(session.cartToken)
        assertNull(session.nonce)
        assertNull(session.cookieHeader)
    }
}
