package es.criosrango.app

import kotlin.test.Test
import kotlin.test.assertEquals

class AccountAuthBackNavigationTest {

    @Test
    fun loginBackFromLoggedOutAccountReturnsToAccount() {
        assertEquals(
            AccountAuthDestination.ACCOUNT,
            accountAuthBackDestination(AccountAuthDestination.LOGIN)
        )
    }

    @Test
    fun loginBackFromCheckoutReturnsToCart() {
        assertEquals(
            AccountAuthDestination.CART,
            accountAuthBackDestination(
                AccountAuthDestination.LOGIN,
                returnToCartAfterLogin = true
            )
        )
    }

    @Test
    fun registerBackReturnsToLogin() {
        assertEquals(
            AccountAuthDestination.LOGIN,
            accountAuthBackDestination(AccountAuthDestination.REGISTER)
        )
    }

    @Test
    fun passwordRecoveryBackReturnsToLogin() {
        assertEquals(
            AccountAuthDestination.LOGIN,
            accountAuthBackDestination(AccountAuthDestination.FORGOT_PASSWORD)
        )
    }
}
