package es.criosrango.app

import es.criosrango.shared.api.StoreApiException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckoutCouponInvalidationTest {
    @Test
    fun friendlyMessageUsesPromotionNameWithoutTechnicalCode() {
        val message = couponInvalidationUserMessage(listOf("Bienvenida"))
        assertEquals(
            "El cupón Bienvenida ya no está disponible y se ha eliminado del carrito. Revisa el nuevo total y vuelve a pagar.",
            message
        )
        assertFalse(message.contains("woocommerce_rest_cart_coupon_errors"))
        assertTrue(isCouponInvalidationMessage(message))
    }

    @Test
    fun reconciliationReplacesCartRefreshesQuoteAndLeavesPaymentForManualTap() = runBlocking {
        val updatedCart = WooCart(totals = CartTotals(totalPrice = "4613"))
        var replacedCart: WooCart? = null
        var assignedCheckout: CheckoutResponse? = CheckoutResponse(orderId = 999)
        var visibleError: String? = null
        var phase = CheckoutPhase.CREATING_ORDER
        var checkoutGetCalls = 0
        val message = couponInvalidationUserMessage(listOf("Bienvenida"))
        val refreshed = CheckoutResponse(orderId = 0, totals = CartTotals(totalPrice = "4613"))

        reconcileCouponInvalidation(
            updatedCart = updatedCart,
            replaceCart = { replacedCart = it },
            reloadCheckout = { checkoutGetCalls++; refreshed },
            onCheckout = { assignedCheckout = it },
            onError = { visibleError = it },
            onPhase = { phase = it },
            userMessage = message
        )

        assertEquals(updatedCart, replacedCart)
        assertEquals(1, checkoutGetCalls)
        assertEquals(0, assignedCheckout?.orderId)
        assertEquals("4613", assignedCheckout?.totals?.totalPrice)
        assertEquals(message, visibleError)
        assertEquals(CheckoutPhase.READY, phase)
    }

    @Test
    fun missingUpdatedCartDoesNotQuoteOrHideFriendlyError() = runBlocking {
        var assignedCheckout: CheckoutResponse? = CheckoutResponse(orderId = 999)
        var visibleError: String? = null
        var phase = CheckoutPhase.CREATING_ORDER
        var checkoutGetCalls = 0
        val message = couponInvalidationUserMessage(emptyList())

        reconcileCouponInvalidation(
            updatedCart = null,
            replaceCart = { error("No cart should be replaced") },
            reloadCheckout = { checkoutGetCalls++; CheckoutResponse(orderId = 0) },
            onCheckout = { assignedCheckout = it },
            onError = { visibleError = it },
            onPhase = { phase = it },
            userMessage = message
        )

        assertEquals(null, assignedCheckout)
        assertEquals(message, visibleError)
        assertEquals(CheckoutPhase.FAILED, phase)
        assertEquals(0, checkoutGetCalls)
    }

    @Test
    fun unrelatedCheckoutErrorStillUsesGenericStoreMessage() {
        val exception = StoreApiException(409, "woocommerce_rest_checkout_error", "Generic checkout failure")
        assertEquals("Inténtalo de nuevo.", exception.toStoreUiError().message)
    }
}
