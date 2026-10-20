package es.criosrango.shared

/** Shared visibility rule; platforms provide auth state and a native login callback. */
fun shouldShowCartLoginCta(isAuthenticated: Boolean, hasLoginAction: Boolean): Boolean =
    !isAuthenticated && hasLoginAction

/** Shared Cart wallet visibility derived from account availability and reward eligibility. */
internal data class CartWalletUiVisibility(
    val showSummary: Boolean,
    val showApplyCandidates: Boolean
)

internal fun cartWalletUiVisibility(
    walletEnabled: Boolean,
    walletLoaded: Boolean,
    hasUnappliedReward: Boolean
): CartWalletUiVisibility {
    val showSummary = walletEnabled && walletLoaded
    return CartWalletUiVisibility(
        showSummary = showSummary,
        showApplyCandidates = showSummary && hasUnappliedReward
    )
}
