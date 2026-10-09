package es.criosrango.shared

import es.criosrango.shared.loyalty.LoyaltyReward
import es.criosrango.shared.model.StoreCartCoupon
import kotlin.test.Test
import kotlin.test.assertEquals

class FriendlyCouponPresentationTest {
    @Test
    fun walletRewardUsesRewardRelationshipWhenCouponLabelIsGeneric() {
        val reward = LoyaltyReward(42, "REAL-REWARD-CODE-42", "8.90", 890, "pending")
        val coupon = StoreCartCoupon(code = "REAL-REWARD-CODE-42", label = "Descuento aplicado")
        assertEquals("Saldo de monedero · 8,90 €", resolveCouponPresentation(coupon, listOf(reward)).title)
    }

    @Test
    fun knownPromotionsKeepFriendlyNames() {
        assertEquals("Black Friday 20%", resolveCouponPresentation(StoreCartCoupon(code = "blackcrios")).title)
        assertEquals("Bienvenida 10%", resolveCouponPresentation(StoreCartCoupon(code = "bienvenida")).title)
        assertEquals("Cumpleaños 15%", resolveCouponPresentation(StoreCartCoupon(code = "cr-cumple")).title)
    }
}
