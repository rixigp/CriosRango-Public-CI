package es.criosrango.app

import es.criosrango.shared.StoreAccountAuthReturnDestination
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
    fun pendingAccountRootReturnsToAccountRootAfterAuthentication() {
        assertEquals(AccountSection.HOME, accountSectionForAuthDestination(StoreAccountAuthReturnDestination.ACCOUNT_ROOT))
    }

    @Test
    fun pendingProtectedDestinationsAreRestoredAfterAuthentication() {
        assertEquals(AccountSection.ORDERS, accountSectionForAuthDestination(StoreAccountAuthReturnDestination.ORDERS))
        assertEquals(AccountSection.WALLET, accountSectionForAuthDestination(StoreAccountAuthReturnDestination.COUPONS))
        assertEquals(AccountSection.PROFILE, accountSectionForAuthDestination(StoreAccountAuthReturnDestination.PROFILE))
    }

    @Test
    fun noPendingDestinationDefaultsToAccountRoot() {
        assertEquals(AccountSection.HOME, accountSectionForAuthDestination(StoreAccountAuthReturnDestination.NONE))
    }

    @Test
    fun expiredSessionPreservesTheProtectedAccountSection() {
        assertEquals(StoreAccountAuthReturnDestination.ORDERS, authDestinationForAccountSection(AccountSection.ORDERS))
        assertEquals(StoreAccountAuthReturnDestination.COUPONS, authDestinationForAccountSection(AccountSection.WALLET))
        assertEquals(StoreAccountAuthReturnDestination.PROFILE, authDestinationForAccountSection(AccountSection.DATA))
        assertEquals(StoreAccountAuthReturnDestination.ACCOUNT_ROOT, authDestinationForAccountSection(AccountSection.HELP))
    }

    @Test
    fun passwordRecoveryBackReturnsToLogin() {
        assertEquals(
            AccountAuthDestination.LOGIN,
            accountAuthBackDestination(AccountAuthDestination.FORGOT_PASSWORD)
        )
    }
}
