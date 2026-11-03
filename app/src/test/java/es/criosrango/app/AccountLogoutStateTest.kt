package es.criosrango.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountLogoutStateTest {
    @Test
    fun unauthenticatedAccountEntryDoesNotForceLogin() {
        assertFalse(shouldOpenLoginForAccountEntry(explicitLoginRequested = false))
    }

    @Test
    fun explicitLoginEntryFromProtectedFlowStillOpensLogin() {
        assertTrue(shouldOpenLoginForAccountEntry(explicitLoginRequested = true))
    }
}
