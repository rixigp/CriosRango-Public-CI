package es.criosrango.app

import es.criosrango.shared.api.StoreApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.runBlocking

class CheckoutCouponInvalidationTest {
    @Test fun friendlyMessageIsExactAndRecognizedByCheckoutUi() {
        val message = couponInvalidationUserMessage(listOf("Bienvenida"))
        assertEquals(COUPON_INVALIDATED_CHECKOUT_MESSAGE, message)
        assertEquals("El cupón ya no está disponible y se ha eliminado del carrito. Revisa el nuevo total y vuelve a pagar.", message)
        assertFalse(message.contains("woocommerce_rest_cart_coupon_errors"))
        assertTrue(isCouponInvalidationMessage(message))
        assertFalse(isCouponInvalidationMessage("Inténtalo de nuevo."))
    }

    @Test fun technicalCouponIdentifiersAreNeverShownToTheUser() {
        assertEquals(COUPON_INVALIDATED_CHECKOUT_MESSAGE, couponInvalidationUserMessage(listOf("blackcrios", "cr-monedero-1234", "cr-cumple-5678")))
    }

    @Test fun reconciliationReplacesCartInvalidatesQuoteAndStartsExactlyOneCheckoutRefresh() {
        val updatedCart = WooCart(totals = CartTotals(totalPrice = "8000", totalDiscount = "0"))
        var replacedCart: WooCart? = null
        var assignedCheckout: CheckoutResponse? = CheckoutResponse(orderId = 999)
        var phase = CheckoutPhase.CREATING_ORDER
        var refreshCount = 0
        val events = mutableListOf<String>()
        runBlocking {
            reconcileCouponInvalidation(
                updatedCart = updatedCart,
                removedCouponCodes = setOf("blackcrios"),
                replaceCart = { replacedCart = it; events += "cart" },
                refreshCart = { events += "cart-fallback" },
                currentCartCouponCodes = { replacedCart?.coupons?.map { it.code }.orEmpty() },
                sanitizeCurrentCart = {},
                onCheckout = { assignedCheckout = it; events += "checkout-invalidated" },
                onPhase = { phase = it; events += "phase:$it" },
                refreshCheckoutOnce = { refreshCount++; events += "checkout-refresh" }
            )
        }
        assertEquals(updatedCart, replacedCart)
        assertEquals(null, assignedCheckout)
        assertEquals(CheckoutPhase.QUOTING, phase)
        assertEquals(1, refreshCount)
        assertTrue(events.indexOf("cart") < events.indexOf("checkout-invalidated"))
        assertTrue(events.indexOf("checkout-invalidated") < events.indexOf("checkout-refresh"))
    }

    @Test fun missingOrStale409CartIsRefreshedBeforeSingleCheckoutRefresh() {
        var currentCodes = listOf("blackcrios")
        val events = mutableListOf<String>()
        var refreshCount = 0
        runBlocking {
            reconcileCouponInvalidation(
                updatedCart = WooCart(coupons = listOf(CartCoupon(code = "blackcrios", label = "Blackcrios"))),
                removedCouponCodes = setOf("blackcrios"),
                replaceCart = { currentCodes = it.coupons.map { coupon -> coupon.code }; events += "replace" },
                refreshCart = { events += "cart-refresh"; currentCodes = emptyList() },
                currentCartCouponCodes = { currentCodes },
                sanitizeCurrentCart = {},
                onCheckout = { events += "checkout-invalidated" },
                onPhase = { events += "phase:$it" },
                refreshCheckoutOnce = { refreshCount++; events += "checkout-refresh" },
                onCartVerified = { codes, stillInvalid ->
                    assertTrue(codes.isEmpty())
                    assertFalse(stillInvalid)
                    events += "verified"
                }
            )
        }
        assertEquals(1, refreshCount)
        assertTrue(events.indexOf("replace") < events.indexOf("cart-refresh"))
        assertTrue(events.indexOf("cart-refresh") < events.indexOf("verified"))
        assertTrue(events.indexOf("verified") < events.indexOf("checkout-refresh"))
    }

    @Test fun absent409CartStillAttemptsOneFallbackAndOneCheckoutRefresh() {
        var cartRefreshCount = 0
        var checkoutRefreshCount = 0
        runBlocking {
            reconcileCouponInvalidation(
                updatedCart = null,
                removedCouponCodes = setOf("blackcrios"),
                replaceCart = { error("No cart should be replaced") },
                refreshCart = { cartRefreshCount++ },
                currentCartCouponCodes = { emptyList() },
                sanitizeCurrentCart = {},
                onCheckout = {},
                onPhase = {},
                refreshCheckoutOnce = { checkoutRefreshCount++ }
            )
        }
        assertEquals(1, cartRefreshCount)
        assertEquals(1, checkoutRefreshCount)
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

    @Test fun invalidCouponCartIsUpdatedInCheckoutScreenSourceBeforeRefresh() {
        val original = WooCart(coupons = listOf(CartCoupon(code = "blackcrios", label = "Blackcrios")), totals = CartTotals(totalPrice = "4990", totalDiscount = "1600", totalDiscountTax = "0"))
        var screenCart = original
        val events = mutableListOf<String>()
        runBlocking {
            reconcileCouponInvalidation(
                updatedCart = original,
                removedCouponCodes = setOf("blackcrios"),
                replaceCart = { screenCart = cartWithoutInvalidatedCoupons(it, setOf("blackcrios")); events += "cart-updated" },
                refreshCart = {},
                currentCartCouponCodes = { screenCart.coupons.map { it.code } },
                sanitizeCurrentCart = { screenCart = cartWithoutInvalidatedCoupons(screenCart, setOf("blackcrios")) },
                onCheckout = { events += "checkout-invalidated" },
                onPhase = {},
                refreshCheckoutOnce = {
                    assertTrue(screenCart.coupons.none { it.code.equals("blackcrios", true) })
                    assertEquals("1600", screenCart.totals.totalDiscount)
                    assertEquals("4990", screenCart.totals.totalPrice)
                    assertEquals(listOf("cart-updated", "checkout-invalidated"), events)
                    events += "checkout-refresh"
                },
                onCartVerified = { codes, stillInvalid -> assertTrue(codes.none { it.equals("blackcrios", true) }); assertFalse(stillInvalid) }
            )
        }
        assertEquals(listOf("cart-updated", "checkout-invalidated", "checkout-refresh"), events)
    }

    @Test fun failedAutomaticCheckoutRefreshKeepsCouponSpecificMessage() {
        val specific = COUPON_INVALIDATED_CHECKOUT_MESSAGE
        assertEquals(specific, checkoutErrorAfterRefreshFailure("Inténtalo de nuevo.", specific))
        assertEquals(specific, checkoutErrorAfterRefreshFailure("Network failure", specific))
    }

    @Test fun fallbackRemovesCouponWithoutCalculatingWooCommerceAmounts() {
        val cart = cartWithoutInvalidatedCoupons(
            WooCart(coupons = listOf(CartCoupon(code = "blackcrios", label = "Blackcrios")), totals = CartTotals(totalPrice = "4990", totalDiscount = "1600", totalDiscountTax = "0")),
            setOf("blackcrios")
        )
        assertTrue(cart.coupons.isEmpty())
        assertEquals("1600", cart.totals.totalDiscount)
        assertEquals("4990", cart.totals.totalPrice)
        assertEquals(COUPON_INVALIDATED_CHECKOUT_MESSAGE, checkoutErrorAfterRefreshFailure(null, COUPON_INVALIDATED_CHECKOUT_MESSAGE))
    }

    @Test fun unrelatedCheckoutErrorStillUsesGenericStoreMessage() {
        val exception = StoreApiException(409, "woocommerce_rest_checkout_error", "Generic checkout failure")
        assertEquals("Inténtalo de nuevo.", exception.toStoreUiError().message)
    }
}