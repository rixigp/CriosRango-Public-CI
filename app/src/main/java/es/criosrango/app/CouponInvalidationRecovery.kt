package es.criosrango.app

import kotlinx.serialization.json.JsonElement

internal class CouponInvalidatedCheckoutException(
    val httpStatus: Int,
    val backendCode: String,
    val removedCouponNames: List<String>,
    val removedCoupons: Map<String, JsonElement>,
    val updatedCart: WooCart?,
    val backendMessage: String
) : Exception(backendMessage) {
    val userMessage: String = couponInvalidationUserMessage(removedCouponNames)
}

internal fun couponInvalidationUserMessage(removedCouponNames: List<String>): String {
    val friendlyName = removedCouponNames.firstOrNull { candidate ->
        val name = candidate.trim()
        name.isNotEmpty() &&
            !name.startsWith("cr-", ignoreCase = true) &&
            !name.startsWith("cr_", ignoreCase = true) &&
            !name.contains("woocommerce_", ignoreCase = true) &&
            !name.matches(Regex("[a-z0-9_-]{3,}"))
    }?.trim()
    return if (friendlyName != null) {
        "El cupón $friendlyName ya no está disponible y se ha eliminado del carrito. Revisa el nuevo total y vuelve a pagar."
    } else {
        "El cupón aplicado ya no está disponible y se ha eliminado del carrito. Revisa el nuevo total y vuelve a pagar."
    }
}

internal fun isCouponInvalidationMessage(message: String?): Boolean =
    message?.startsWith("El cupón ") == true &&
        message.contains("ya no está disponible y se ha eliminado del carrito. Revisa el nuevo total y vuelve a pagar.")

internal fun reconcileCouponInvalidation(
    updatedCart: WooCart?,
    replaceCart: (WooCart) -> Unit,
    onCheckout: (CheckoutResponse?) -> Unit,
    onError: (String) -> Unit,
    onPhase: (CheckoutPhase) -> Unit,
    userMessage: String
) {
    // Reconcile the authoritative cart before publishing the visible error.
    updatedCart?.let(replaceCart)
    // Invalidate the previous quote/order state; the user must request delivery again.
    onCheckout(null)
    onPhase(CheckoutPhase.FAILED)
    onError(userMessage)
}
