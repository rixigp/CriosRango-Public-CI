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
    "El cupón ya no está disponible y se ha eliminado del carrito. Revisa el nuevo total y vuelve a pagar."

@Suppress("UNUSED_PARAMETER")
internal fun couponInvalidationUserMessage(removedCouponNames: List<String>): String =
    COUPON_INVALIDATED_CHECKOUT_MESSAGE

internal fun isCouponInvalidationMessage(message: String?): Boolean =
    message?.startsWith("El cupón ") == true &&
        message.contains("ya no está disponible y se ha eliminado del carrito. Revisa el nuevo total y vuelve a pagar.")

internal suspend fun reconcileCouponInvalidation(
    updatedCart: WooCart?,
    removedCouponCodes: Set<String>,
    replaceCart: (WooCart) -> Unit,
    refreshCart: suspend () -> Unit,
    currentCartCouponCodes: () -> List<String>,
    onCheckout: (CheckoutResponse?) -> Unit,
    onError: (String) -> Unit,
    onPhase: (CheckoutPhase) -> Unit,
    userMessage: String,
    onCartVerified: (List<String>, Boolean) -> Unit = { _, _ -> }
) {
    // Apply the cart returned in the 409 first, before changing visible checkout state.
    updatedCart?.let(replaceCart)
    val containsInvalidCoupon = {
        currentCartCouponCodes().any { it.trim().lowercase() in removedCouponCodes }
    }
    // If data.cart could not be decoded, or still contains the invalidated coupon,
    // immediately reconcile against the authoritative Store API cart. Never re-POST /checkout.
    if (updatedCart == null || containsInvalidCoupon()) {
        runCatching { refreshCart() }
    }
    val remainingCodes = currentCartCouponCodes()
    val stillContainsInvalidCoupon = remainingCodes.any { it.trim().lowercase() in removedCouponCodes }
    onCartVerified(remainingCodes, stillContainsInvalidCoupon)
    // Invalidate the old quote/shipping state only after the cart reconciliation attempt.
    onCheckout(null)
    onPhase(CheckoutPhase.FAILED)
    onError(userMessage)
}
