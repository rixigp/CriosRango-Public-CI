package es.criosrango.app

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal class CouponInvalidatedCheckoutException(
    val httpStatus: Int,
    val backendCode: String,
    val removedCouponNames: List<String>,
    val removedCoupons: Map<String, JsonElement>,
    val updatedCart: WooCart?,
    val backendMessage: String
) : Exception(backendMessage) {
    val userMessage: String = couponInvalidationUserMessage(removedCouponNames)
    val removedCouponCodes: Set<String> = removedCoupons.flatMap { (key, value) ->
        val payloadCode = runCatching { value.jsonObject["code"]?.jsonPrimitive?.contentOrNull }.getOrNull()
        listOfNotNull(key, payloadCode).map { it.trim().lowercase() }
    }.filter(String::isNotBlank).toSet()
}

internal const val COUPON_INVALIDATED_CHECKOUT_MESSAGE =
    "El cupón ya no está disponible y se ha eliminado del carrito. Hemos actualizado el total de tu pedido."

@Suppress("UNUSED_PARAMETER")
internal fun couponInvalidationUserMessage(removedCouponNames: List<String>): String =
    COUPON_INVALIDATED_CHECKOUT_MESSAGE

internal fun isCouponInvalidationMessage(message: String?): Boolean =
    message == COUPON_INVALIDATED_CHECKOUT_MESSAGE

internal suspend fun reconcileCouponInvalidation(
    updatedCart: WooCart?,
    removedCouponCodes: Set<String>,
    replaceCart: (WooCart) -> Unit,
    refreshCart: suspend () -> Unit,
    currentCartCouponCodes: () -> List<String>,
    onCheckout: (CheckoutResponse?) -> Unit,
    onPhase: (CheckoutPhase) -> Unit,
    refreshCheckoutOnce: () -> Unit,
    onCartVerified: (List<String>, Boolean) -> Unit = { _, _ -> }
) {
    // Stop payment in the caller. First apply the cart carried by the 409, if valid.
    updatedCart?.let(replaceCart)
    val containsInvalidCoupon = {
        currentCartCouponCodes().any { it.trim().lowercase() in removedCouponCodes }
    }
    // If data.cart is absent/unusable or still has the invalid coupon, refresh the cart
    // synchronously before starting the one allowed checkout/shipping refresh.
    if (updatedCart == null || containsInvalidCoupon()) {
        runCatching { refreshCart() }
    }
    val remainingCodes = currentCartCouponCodes()
    val stillContainsInvalidCoupon = remainingCodes.any { it.trim().lowercase() in removedCouponCodes }
    onCartVerified(remainingCodes, stillContainsInvalidCoupon)
    // Discard the stale quote and launch one normal delivery/checkout refresh. It never
    // retries payment; failure is surfaced by loadCheckout and manual consultation remains available.
    onCheckout(null)
    onPhase(CheckoutPhase.QUOTING)
    refreshCheckoutOnce()
}
