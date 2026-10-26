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

    @Test
    fun walletAvailableAndNotAppliedShowsSummaryAndApplyCandidate() {
        val visibility = cartWalletUiVisibility(true, true, hasUnappliedReward = true)
        assertTrue(visibility.showSummary)
        assertTrue(visibility.showApplyCandidates)
    }

    @Test
    fun appliedWalletKeepsSummaryButHidesDuplicateApplyCandidate() {
        val visibility = cartWalletUiVisibility(true, true, hasUnappliedReward = false)
        assertTrue(visibility.showSummary)
        assertFalse(visibility.showApplyCandidates)
    }

    @Test
    fun removingWalletDiscountRestoresApplyCandidateWhenRewardIsEligible() {
        val visibility = cartWalletUiVisibility(true, true, hasUnappliedReward = true)
        assertTrue(visibility.showSummary)
        assertTrue(visibility.showApplyCandidates)
    }

    @Test
    fun unavailableWalletOrNoEligibleRewardDoesNotOfferApply() {
        val unavailable = cartWalletUiVisibility(false, false, hasUnappliedReward = false)
        val noEligibleReward = cartWalletUiVisibility(true, true, hasUnappliedReward = false)
        assertFalse(unavailable.showSummary)
        assertFalse(unavailable.showApplyCandidates)
        assertTrue(noEligibleReward.showSummary)
        assertFalse(noEligibleReward.showApplyCandidates)
    }

    @Test
    fun appliedWalletWithOtherCouponsKeepsSummaryWithoutDuplicateApply() {
        // Applied discount rows remain driven by cart.coupons in StoreCartScreen.
        val visibility = cartWalletUiVisibility(true, true, hasUnappliedReward = false)
        assertTrue(visibility.showSummary)
        assertFalse(visibility.showApplyCandidates)
    }
}
