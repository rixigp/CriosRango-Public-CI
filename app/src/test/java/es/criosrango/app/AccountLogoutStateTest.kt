package es.criosrango.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountLogoutStateTest {

    @Test
    fun unauthenticatedStateForcesAccountUiBackToLogin() {
        assertTrue(shouldResetAccountUiForAuthState(AccountAuthState.UNAUTHENTICATED))
    }

    @Test
    fun authenticatedAndCheckingStatesDoNotForceLogoutUiReset() {
        assertFalse(shouldResetAccountUiForAuthState(AccountAuthState.AUTHENTICATED))
        assertFalse(shouldResetAccountUiForAuthState(AccountAuthState.CHECKING))
    }
}
