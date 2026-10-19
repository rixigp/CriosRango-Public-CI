package es.criosrango.shared

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoreCartUiPolicyTest {
    @Test
    fun loginCtaIsVisibleOnlyForLoggedOutUsersWithLoginAction() {
        assertTrue(shouldShowCartLoginCta(isAuthenticated = false, hasLoginAction = true))
        assertFalse(shouldShowCartLoginCta(isAuthenticated = true, hasLoginAction = true))
        assertFalse(shouldShowCartLoginCta(isAuthenticated = false, hasLoginAction = false))
    }
}
