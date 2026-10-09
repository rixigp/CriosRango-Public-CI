package es.criosrango.shared

import es.criosrango.shared.loyalty.LoyaltyReward
import es.criosrango.shared.model.StoreCartCoupon

enum class CouponPresentationKind { WALLET, DISCOUNT, BIRTHDAY, WELCOME, OTHER }
data class CouponPresentation(val title: String, val kind: CouponPresentationKind)

/** Resolves the visible name using the real wallet-reward relationship, not UI text or code prefixes. */
fun resolveCouponPresentation(
    coupon: StoreCartCoupon,
    pendingRewards: List<LoyaltyReward> = emptyList()
): CouponPresentation {
    val reward = pendingRewards.firstOrNull {
        it.code.isNotBlank() && it.code.equals(coupon.code, ignoreCase = true)
    }
    if (reward != null) {
        val amount = reward.amount.trim().replace('.', ',')
        return CouponPresentation(
            if (amount.isNotBlank()) "Saldo de monedero · $amount €" else "Saldo de monedero",
            CouponPresentationKind.WALLET
        )
    }
    val key = "${coupon.label} ${coupon.code}".lowercase()
    return when {
        key.contains("blackcrios") || key.contains("black friday") -> CouponPresentation("Black Friday 20%", CouponPresentationKind.DISCOUNT)
        key.contains("bienvenida") || key.contains("welcome") -> CouponPresentation("Bienvenida 10%", CouponPresentationKind.WELCOME)
        key.contains("cr-cumple") || key.contains("cumple") || key.contains("birthday") -> CouponPresentation("Cumpleaños 15%", CouponPresentationKind.BIRTHDAY)
        coupon.label.isNotBlank() && !coupon.label.equals(coupon.code, ignoreCase = true) &&
            !coupon.label.startsWith("cr-", ignoreCase = true) -> CouponPresentation(coupon.label, CouponPresentationKind.OTHER)
        else -> CouponPresentation("Cupón aplicado", CouponPresentationKind.OTHER)
    }
}
