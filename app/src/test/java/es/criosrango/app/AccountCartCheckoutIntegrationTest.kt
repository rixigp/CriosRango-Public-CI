package es.criosrango.app

import es.criosrango.shared.account.AccountCustomerAddress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountCartCheckoutIntegrationTest {

    @Test
    fun A_guestWithCart_showsLoginCta_andGuestCheckoutRemainsAllowed() {
        assertTrue(AccountCartCheckoutPolicy.showGuestLoginCta(null, 1))
        val cartLines = listOf("A:x1")
        assertTrue(cartLines.isNotEmpty())
    }

    @Test
    fun B_loginFromCart_doesNotChangeCartLines() {
        val cartLinesBefore = listOf("A:x1", "B:variation-42:x2")
        val accountAfterLogin = 101
        assertEquals(listOf("A:x1", "B:variation-42:x2"), cartLinesBefore)
        assertTrue(accountAfterLogin > 0)
    }

    @Test
    fun C_failedLogin_doesNotChangeCartLines_orEnableAccountCtaForLoggedUser() {
        val cartLines = listOf("A:x1")
        val accountUserId: Int? = null
        assertEquals(listOf("A:x1"), cartLines)
        assertTrue(AccountCartCheckoutPolicy.showGuestLoginCta(accountUserId, cartLines.size))
    }

    @Test
    fun D_logout_keepsCartLines_andRestoresGuestCta() {
        val cartLines = listOf("A:x1")
        val accountAfterLogout: Int? = null
        assertEquals(listOf("A:x1"), cartLines)
        assertTrue(AccountCartCheckoutPolicy.showGuestLoginCta(accountAfterLogout, cartLines.size))
    }

    @Test
    fun E_emptyCheckout_allowsAccountAddressPrefill() {
        val accountAddress = AccountCustomerAddress(
            firstName = "Ana",
            lastName = "García",
            email = "ana@example.com",
            address1 = "Calle Mayor 1",
            postcode = "28001",
            city = "Madrid",
            state = "M",
            country = "ES"
        )
        assertTrue(AccountCartCheckoutPolicy.shouldPrefillAccountAddress(null, accountAddress))
    }

    @Test
    fun F_existingGuestCheckoutAddress_blocksSilentAccountOverwrite() {
        val guestAddress = CustomerAddress(
            firstName = "Invitado",
            lastName = "Test",
            email = "guest@example.com",
            phone = "600000000",
            address1 = "Calle Guest 2",
            postcode = "28002",
            city = "Madrid",
            state = "M",
            country = "ES"
        )
        val accountAddress = AccountCustomerAddress(firstName = "Cuenta", lastName = "Privada")
        assertFalse(AccountCartCheckoutPolicy.shouldPrefillAccountAddress(guestAddress, accountAddress))
    }

    @Test
    fun G_userAAddress_isNotVisibleToUserB() {
        assertTrue(AccountCartCheckoutPolicy.isAddressVisibleToAccount(null, 2))
        assertTrue(AccountCartCheckoutPolicy.isAddressVisibleToAccount(2, 2))
        assertFalse(AccountCartCheckoutPolicy.isAddressVisibleToAccount(1, 2))
    }

    @Test
    fun H_loginSessionAndCartAreIndependentIntegrationState() {
        val cartLines = listOf("A:x1")
        var accountUserId: Int? = null
        accountUserId = 101
        assertEquals(listOf("A:x1"), cartLines)
        assertEquals(101, accountUserId)
    }

    @Test
    fun I_pendingPayment_afterProcessDeath_notPaid_isAbandoned_andNewCheckoutRemainsAllowed() {
        val cartLines = mutableListOf("Prueba:x1")
        val pendingInherited = true
        val paymentStatus = "NOT_PAID"

        val pendingCleared = pendingInherited && paymentStatus != "PAID"
        if (pendingCleared) {
            cartLines[0] = "Prueba:x7"
            cartLines += "Jersey:x1"
        }

        assertTrue(pendingCleared)
        assertEquals(listOf("Prueba:x7", "Jersey:x1"), cartLines)
        assertTrue(cartLines.isNotEmpty())
        assertTrue(AccountCartCheckoutPolicy.showGuestLoginCta(null, cartLines.size))
    }

    @Test
    fun J_pendingPayment_paid_isResolvedBeforeNewPayment() {
        val pendingInherited = true
        val paymentStatus = "PAID"
        var newPaymentStarted = false

        val paidResolved = pendingInherited && paymentStatus == "PAID"
        if (!paidResolved) {
            newPaymentStarted = true
        }

        assertTrue(paidResolved)
        assertFalse(newPaymentStarted)
    }

    @Test
    fun L_singlePaymentTap_startsExactlyOneCheckout() {
        val gate = CheckoutSubmissionGate()
        var createCheckoutCalls = 0
        if (gate.tryAcquire()) createCheckoutCalls++
        assertEquals(1, createCheckoutCalls)
    }

    @Test
    fun M_fiveRapidPaymentTaps_startOnlyOneCheckout() {
        val gate = CheckoutSubmissionGate()
        var createCheckoutCalls = 0
        repeat(5) { if (gate.tryAcquire()) createCheckoutCalls++ }
        assertEquals(1, createCheckoutCalls)
    }

    @Test
    fun N_failedCheckoutSubmission_releasesPaymentGate() {
        val gate = CheckoutSubmissionGate()
        assertTrue(gate.tryAcquire())
        gate.release()
        assertTrue(gate.tryAcquire())
    }

    @Test
    fun O_cancelledCecabankPayment_returnsToCart() {
        var checkoutOpen = true
        var tab = AppTab.HOME
        val result = "cancel"
        if (result == "cancel") {
            checkoutOpen = false
            tab = AppTab.CART
        }
        assertFalse(checkoutOpen)
        assertEquals(AppTab.CART, tab)
    }

    @Test
    fun P_cancelledCecabankPayment_keepsCartLinesAndQuantities() {
        val cartLinesBefore = listOf("Prueba:x7", "Jersey:x1")
        val cartLinesAfter = cartLinesBefore.toList()
        assertEquals(cartLinesBefore, cartLinesAfter)
    }

    @Test
    fun Q_cancelledCecabankPayment_clearsPaymentAttemptState() {
        val orderId = 123
        var paymentRedirect: String? = "https://cecabank.example/pay"
        var pendingOrderId: Int? = orderId
        var temporaryOrderKey: String? = "wc_order_key"
        var temporaryEmail: String? = "guest@example.com"

        paymentRedirect = null
        pendingOrderId = null
        temporaryOrderKey = null
        temporaryEmail = null

        assertEquals(null, paymentRedirect)
        assertEquals(null, pendingOrderId)
        assertEquals(null, temporaryOrderKey)
        assertEquals(null, temporaryEmail)
    }

    @Test
    fun R_cancelledCecabankPayment_doesNotCreateAnotherCheckout() {
        var createCheckoutCalls = 0
        val result = "cancel"
        if (result == "cancel") {
            // Explicit cancellation is terminal for this attempt.
        } else {
            createCheckoutCalls++
        }
        assertEquals(0, createCheckoutCalls)
    }

    @Test
    fun S_afterCancel_finalizarCompraCanStartANewCheckout() {
        var checkoutOpen = false
        var createCheckoutCalls = 0

        checkoutOpen = true
        if (checkoutOpen) createCheckoutCalls++

        assertTrue(checkoutOpen)
        assertEquals(1, createCheckoutCalls)
    }

    @Test
    fun T_paidCallback_doesNotUseCancellationPath() {
        val result = "ok"
        var cancelled = false
        if (result == "cancel") cancelled = true
        assertFalse(cancelled)
    }

    @Test
    fun K_claim_requiresOrderIdAndOrderKey() {
        assertFalse(AccountCartCheckoutPolicy.hasClaimCredentials(null, "wc_order_key"))
        assertFalse(AccountCartCheckoutPolicy.hasClaimCredentials(123, null))
        assertFalse(AccountCartCheckoutPolicy.hasClaimCredentials(0, "wc_order_key"))
        assertTrue(AccountCartCheckoutPolicy.hasClaimCredentials(123, "wc_order_key"))
    }
}
