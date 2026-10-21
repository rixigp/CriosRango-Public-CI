package es.criosrango.shared

import es.criosrango.shared.loyalty.LoyaltyReward
import es.criosrango.shared.loyalty.LoyaltyWallet
import es.criosrango.shared.loyalty.RedeemWalletCoupon
import es.criosrango.shared.loyalty.RedeemWalletResponse
import es.criosrango.shared.promotions.Promotion
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoreWalletOwnerTest {
    private fun wallet(value: String = "9.35", rewards: List<LoyaltyReward> = emptyList()) = LoyaltyWallet(
        points = 187,
        walletValue = value,
        minimumRedeemPoints = 100,
        minimumRedeemValue = "5.00",
        maximumRedeemPoints = 300,
        conversionPoints = 100,
        conversionValue = "5.00",
        pendingRewards = rewards
    )

    private fun reward(code: String = "CR-MONEDERO-42") = LoyaltyReward(
        id = 42, code = code, amount = "5.00", points = 100, status = "pending"
    )

    private fun response(code: String = "CR-MONEDERO-42") = RedeemWalletResponse(
        success = true,
        pointsRedeemed = 100,
        value = "5.00",
        remainingPoints = 87,
        remainingWalletValue = "4.35",
        coupon = RedeemWalletCoupon(1, 42, 1, code, "5.00")
    )

    @Test fun spanishMoneyFormattingUsesCommaAndEuro() {
        assertEquals("1,40 €", formatWalletMoney("1.40"))
        assertEquals("5,00 €", formatWalletMoney("5.00"))
        assertEquals("9,35 €", formatWalletMoney("9.35"))
        assertFalse(formatWalletMoney("9.35").contains('.'))
    }

    @Test fun minimumAndProgressLabelsUseSpanishMoneyFormatting() {
        val belowMinimum = wallet("1.40")
        assertEquals("Has acumulado 1,40 € de 5,00 €", walletProgressLabel(belowMinimum))
        assertEquals("Te faltan 3,60 € para desbloquear tu saldo.", walletRemainingLabel(belowMinimum))
    }

    @Test fun initialStateSeparatesLoadingAndErrors() = runTest {
        val owner = owner(backgroundScope)
        assertNull(owner.state.value.wallet)
        assertFalse(owner.state.value.walletLoading)
        assertFalse(owner.state.value.promotionsLoading)
        assertNull(owner.state.value.walletError)
        assertNull(owner.state.value.promotionsError)
    }

    @Test fun sessionLoadsWalletAndPriorityOrderedPromotions() = runTest {
        val owner = owner(backgroundScope, promotions = listOf(
            Promotion("low", "Baja", priority = 1),
            Promotion("high", "Alta", priority = 9)
        ))
        owner.onSessionChanged("user-A")
        advanceUntilIdle()
        assertEquals("9.35", owner.state.value.wallet?.walletValue)
        assertEquals(listOf("high", "low"), owner.state.value.promotions.map { it.id })
    }

    @Test fun logoutClearsSensitiveWalletAndPromotionsImmediately() = runTest {
        val owner = owner(backgroundScope, promotions = listOf(Promotion("p", "Promo")))
        owner.onSessionChanged("user-A")
        advanceUntilIdle()
        owner.clearForLogout()
        assertNull(owner.state.value.wallet)
        assertTrue(owner.state.value.promotions.isEmpty())
        assertFalse(owner.state.value.walletLoading)
        assertFalse(owner.state.value.promotionsLoading)
    }

    @Test fun switchingUsersDoesNotExposePreviousUserData() = runTest {
        var current = wallet("9.35")
        val owner = StoreWalletOwner(
            loadWallet = { current },
            loadPromotions = { listOf(Promotion(current.walletValue, current.walletValue)) },
            redeemPoints = { _, _ -> response() },
            createRequestId = { "request-1" },
            scope = backgroundScope
        )
        owner.onSessionChanged("user-A")
        advanceUntilIdle()
        assertEquals("9.35", owner.state.value.wallet?.walletValue)
        current = wallet("1.40")
        owner.onSessionChanged("user-B")
        assertNull(owner.state.value.wallet)
        assertTrue(owner.state.value.promotions.isEmpty())
        advanceUntilIdle()
        assertEquals("1.40", owner.state.value.wallet?.walletValue)
    }

    @Test fun restoredSessionRefreshesWalletAndPromotions() = runTest {
        var reads = 0
        val owner = StoreWalletOwner(
            loadWallet = { reads++; wallet() },
            loadPromotions = { listOf(Promotion("restored", "Restaurada")) },
            redeemPoints = { _, _ -> response() },
            createRequestId = { "request" },
            scope = backgroundScope
        )
        owner.onSessionChanged("restored-user")
        advanceUntilIdle()
        owner.refresh()
        advanceUntilIdle()
        assertTrue(reads >= 2)
        assertEquals("restored", owner.state.value.promotions.single().id)
    }

    @Test fun walletAndPromotionErrorsRemainIndependentAndRetryable() = runTest {
        var failWallet = true
        var failPromotions = false
        val owner = StoreWalletOwner(
            loadWallet = { if (failWallet) error("wallet unavailable") else wallet() },
            loadPromotions = { if (failPromotions) error("promotions unavailable") else listOf(Promotion("p", "Promo")) },
            redeemPoints = { _, _ -> response() },
            createRequestId = { "request" },
            scope = backgroundScope
        )
        owner.onSessionChanged("user-A")
        advanceUntilIdle()
        assertNotNull(owner.state.value.walletError)
        assertNull(owner.state.value.promotionsError)
        failWallet = false
        failPromotions = true
        owner.refresh()
        advanceUntilIdle()
        assertNull(owner.state.value.walletError)
        assertNotNull(owner.state.value.promotionsError)
        failPromotions = false
        owner.refresh()
        advanceUntilIdle()
        assertNull(owner.state.value.walletError)
        assertNull(owner.state.value.promotionsError)
    }

    @Test fun redeemSuccessRefreshesAndAppliesCreatedCoupon() = runTest {
        var redeems = 0
        var appliedCode: String? = null
        val owner = StoreWalletOwner(
            loadWallet = { wallet("4.35", listOf(reward())) },
            loadPromotions = { emptyList() },
            redeemPoints = { _, _ -> redeems++; response() },
            createRequestId = { "stable-id" },
            scope = backgroundScope
        )
        owner.onSessionChanged("user-A")
        advanceUntilIdle()
        owner.redeem(100) { code -> appliedCode = code; CouponApplicationResult.Success }
        advanceUntilIdle()
        assertEquals(1, redeems)
        assertEquals("CR-MONEDERO-42", appliedCode)
        assertNull(owner.state.value.pendingRequestId)
        assertEquals("4.35", owner.state.value.wallet?.walletValue)
    }

    @Test fun doubleSubmitIsBlockedByOwner() = runTest {
        var redeems = 0
        val owner = StoreWalletOwner(
            loadWallet = { wallet() }, loadPromotions = { emptyList() },
            redeemPoints = { _, _ -> redeems++; response() },
            createRequestId = { "one-id" }, scope = backgroundScope
        )
        owner.onSessionChanged("user-A")
        advanceUntilIdle()
        owner.redeem(100) { CouponApplicationResult.Success }
        owner.redeem(100) { CouponApplicationResult.Success }
        advanceUntilIdle()
        assertEquals(1, redeems)
    }

    @Test fun failedRedeemRetryReusesRequestId() = runTest {
        var attempt = 0
        val ids = mutableListOf<String>()
        val owner = StoreWalletOwner(
            loadWallet = { wallet() }, loadPromotions = { emptyList() },
            redeemPoints = { _, id -> ids += id; attempt++; if (attempt == 1) error("network") else response() },
            createRequestId = { "same-id" }, scope = backgroundScope
        )
        owner.onSessionChanged("user-A")
        advanceUntilIdle()
        owner.redeem(100) { CouponApplicationResult.Success }
        advanceUntilIdle()
        assertEquals("same-id", owner.state.value.pendingRequestId)
        owner.redeem(100) { CouponApplicationResult.Success }
        advanceUntilIdle()
        assertEquals(listOf("same-id", "same-id"), ids)
    }

    @Test fun createdCreditRemainsRecoverableWhenCartRejectsCouponAndDoesNotRedeemAgain() = runTest {
        var redeems = 0
        var walletReads = 0
        val pending = reward()
        val owner = StoreWalletOwner(
            loadWallet = { walletReads++; wallet("4.35", listOf(pending)) },
            loadPromotions = { emptyList() },
            redeemPoints = { _, _ -> redeems++; response() },
            createRequestId = { "only-once" },
            scope = backgroundScope
        )
        owner.onSessionChanged("user-A")
        advanceUntilIdle()
        owner.redeem(100) { CouponApplicationResult.failure("Cupón rechazado") }
        advanceUntilIdle()
        assertEquals(1, redeems)
        assertNull(owner.state.value.pendingRequestId)
        assertTrue(owner.state.value.operationError?.contains("El crédito se ha creado") == true)
        assertEquals("CR-MONEDERO-42", owner.state.value.wallet?.pendingRewards?.single()?.code)
        assertTrue(walletReads >= 2)
        owner.applyReward(pending) { CouponApplicationResult.failure("Sigue rechazado") }
        advanceUntilIdle()
        assertEquals(1, redeems)
    }

    @Test fun appliedRewardAndPromotionCodesAreMatchedIgnoringCase() = runTest {
        val owner = owner(backgroundScope)
        owner.setAppliedCouponCodes(setOf(" cr-monedero-42 ", "promo-10"))
        assertTrue(owner.state.value.appliedCouponCodes.any { it.equals("CR-MONEDERO-42", true) })
        assertTrue(owner.state.value.appliedCouponCodes.any { it.equals("PROMO-10", true) })
    }

    @Test fun ownerRejectsPointsThatAreNotInCurrentRedeemableOptions() = runTest {
        var redeems = 0
        val owner = owner(backgroundScope)
        owner.onSessionChanged("user-A")
        advanceUntilIdle()
        owner.redeem(1) { redeems++; CouponApplicationResult.Success }
        advanceUntilIdle()
        assertEquals(0, redeems)
        assertNull(owner.state.value.pendingRequestId)
    }

    @Test fun pendingRewardCanBeAppliedWithoutRedeemingAgain() = runTest {
        var redeems = 0
        var applied: String? = null
        val owner = StoreWalletOwner(
            loadWallet = { wallet("4.35", listOf(reward())) },
            loadPromotions = { emptyList() },
            redeemPoints = { _, _ -> redeems++; response() },
            createRequestId = { "unused" },
            scope = backgroundScope
        )
        owner.onSessionChanged("user-A")
        advanceUntilIdle()
        owner.applyReward(reward()) { code -> applied = code; CouponApplicationResult.Success }
        advanceUntilIdle()
        assertEquals("CR-MONEDERO-42", applied)
        assertEquals(0, redeems)
        assertNull(owner.state.value.operationError)
    }

    @Test fun callbackExceptionIsShownAsRecoverableOperationError() = runTest {
        val owner = owner(backgroundScope)
        owner.onSessionChanged("user-A")
        advanceUntilIdle()
        owner.applyPromotion("PROMO-10") { error("cart adapter failed") }
        advanceUntilIdle()
        assertTrue(owner.state.value.operationError?.contains("cart adapter failed") == true)
    }

    @Test fun successfulServerRedemptionWithoutCouponDoesNotKeepRetryToken() = runTest {
        val owner = StoreWalletOwner(
            loadWallet = { wallet("4.35", listOf(reward())) },
            loadPromotions = { emptyList() },
            redeemPoints = { _, _ -> RedeemWalletResponse(success = true, pointsRedeemed = 100) },
            createRequestId = { "no-coupon-id" },
            scope = backgroundScope
        )
        owner.onSessionChanged("user-A")
        advanceUntilIdle()
        owner.redeem(100) { CouponApplicationResult.Success }
        advanceUntilIdle()
        assertNull(owner.state.value.pendingRequestId)
        assertTrue(owner.state.value.operationError?.contains("no ha devuelto un cupón") == true)
    }

    @Test fun promotionsWithoutCodeRemainInSharedStateWithoutInventedCode() = runTest {
        val noCode = Promotion("info", "Promoción informativa", description = "Sin cupón", code = null, priority = 2)
        val withCode = Promotion("code", "Descuento", code = "PROMO-10", priority = 1)
        val owner = owner(backgroundScope, promotions = listOf(withCode, noCode))
        owner.onSessionChanged("user-A")
        advanceUntilIdle()
        assertEquals(listOf("info", "code"), owner.state.value.promotions.map { it.id })
        assertNull(owner.state.value.promotions.first().code)
        assertEquals("PROMO-10", owner.state.value.promotions.last().code)
    }

    @Test fun createdCreditRemainsAvailableWhenWalletRefreshAlsoFails() = runTest {
        var walletCalls = 0
        val owner = StoreWalletOwner(
            loadWallet = {
                walletCalls++
                if (walletCalls == 1) wallet() else error("wallet refresh failed")
            },
            loadPromotions = { emptyList() },
            redeemPoints = { _, _ -> response() },
            createRequestId = { "one-credit" },
            scope = backgroundScope
        )
        owner.onSessionChanged("user-A")
        advanceUntilIdle()
        owner.redeem(100) { CouponApplicationResult.failure("cart rejected") }
        advanceUntilIdle()
        assertNull(owner.state.value.pendingRequestId)
        assertEquals("CR-MONEDERO-42", owner.state.value.pendingReward?.code)
        assertTrue(owner.state.value.operationError?.contains("El crédito se ha creado") == true)
    }

    private fun owner(
        scope: kotlinx.coroutines.CoroutineScope,
        promotions: List<Promotion> = emptyList()
    ) = StoreWalletOwner(
        loadWallet = { wallet() },
        loadPromotions = { promotions },
        redeemPoints = { _, _ -> response() },
        createRequestId = { "request-id" },
        scope = scope
    )
}
