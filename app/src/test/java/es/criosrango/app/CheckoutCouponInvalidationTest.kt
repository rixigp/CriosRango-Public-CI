package es.criosrango.app

import es.criosrango.shared.api.StoreApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class CheckoutCouponInvalidationTest {
    @Test fun friendlyMessageUsesPromotionNameWithoutTechnicalCode() {
        val message = couponInvalidationUserMessage(listOf("Bienvenida"))
        assertEquals("El cupón Bienvenida ya no está disponible y se ha eliminado del carrito. Revisa el nuevo total y vuelve a pagar.", message)
        assertFalse(message.contains("woocommerce_rest_cart_coupon_errors"))
        assertTrue(isCouponInvalidationMessage(message))
    }

    @Test fun technicalCouponIdentifiersAreNeverShownToTheUser() {
        assertEquals("El cupón aplicado ya no está disponible y se ha eliminado del carrito. Revisa el nuevo total y vuelve a pagar.", couponInvalidationUserMessage(listOf("blackcrios", "cr-monedero-1234", "cr-cumple-5678")))
    }

    @Test fun reconciliationReplacesCartBeforePublishingErrorAndInvalidatesCheckout() {
        val updatedCart = WooCart(totals = CartTotals(totalPrice = "4613"))
        var replacedCart: WooCart? = null
        var assignedCheckout: CheckoutResponse? = CheckoutResponse(orderId = 999)
        var visibleError: String? = null
        var phase = CheckoutPhase.CREATING_ORDER
        val events = mutableListOf<String>()
        val message = couponInvalidationUserMessage(listOf("Bienvenida"))
        reconcileCouponInvalidation(updatedCart, { replacedCart = it; events += "cart" }, { assignedCheckout = it; events += "checkout" }, { visibleError = it; events += "error" }, { phase = it; events += "phase:$it" }, message)
        assertEquals(updatedCart, replacedCart)
        assertEquals(null, assignedCheckout)
        assertEquals(message, visibleError)
        assertEquals(CheckoutPhase.FAILED, phase)
        assertTrue(events.indexOf("cart") < events.indexOf("error"))
        assertTrue(events.indexOf("checkout") < events.indexOf("error"))
        assertTrue(events.indexOf("phase:FAILED") < events.indexOf("error"))
    }

    @Test fun missingUpdatedCartStillInvalidatesCheckoutAndShowsFriendlyError() {
        var assignedCheckout: CheckoutResponse? = CheckoutResponse(orderId = 999)
        var visibleError: String? = null
        var phase = CheckoutPhase.CREATING_ORDER
        val message = couponInvalidationUserMessage(emptyList())
        reconcileCouponInvalidation(null, { error("No cart should be replaced") }, { assignedCheckout = it }, { visibleError = it }, { phase = it }, message)
        assertEquals(null, assignedCheckout)
        assertEquals(message, visibleError)
        assertEquals(CheckoutPhase.FAILED, phase)
    }

    @Test fun couponInvalidationExceptionPreservesOriginalRemovedCouponPayload() {
        val removedCoupons = mapOf("bienvenida" to buildJsonObject { put("code", "bienvenida"); put("label", "Bienvenida"); put("reason", "usage_limit_reached") })
        val exception = CouponInvalidatedCheckoutException(409, "woocommerce_rest_cart_coupon_errors", listOf("Bienvenida"), removedCoupons, null, "El cupón se ha eliminado del carrito.")
        assertEquals(409, exception.httpStatus)
        assertEquals("woocommerce_rest_cart_coupon_errors", exception.backendCode)
        assertEquals("El cupón se ha eliminado del carrito.", exception.backendMessage)
        assertEquals(removedCoupons, exception.removedCoupons)
        assertEquals("Bienvenida", exception.removedCoupons["bienvenida"]?.let { (it as? kotlinx.serialization.json.JsonObject)?.get("label")?.toString()?.trim('"') })
    }

    @Test fun unrelatedCheckoutErrorStillUsesGenericStoreMessage() {
        val exception = StoreApiException(409, "woocommerce_rest_checkout_error", "Generic checkout failure")
        assertEquals("Inténtalo de nuevo.", exception.toStoreUiError().message)
    }
}
