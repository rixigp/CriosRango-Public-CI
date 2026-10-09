package es.criosrango.app

import kotlinx.coroutines.CancellationException

internal class CouponInvalidatedCheckoutException(
    val httpStatus: Int,
    val backendCode: String,
    val removedCouponNames: List<String>,
    val updatedCart: WooCart?,
    val backendMessage: String
) : Exception(backendMessage) {
    val userMessage: String = couponInvalidationUserMessage(removedCouponNames)
}

internal fun couponInvalidationUserMessage(removedCouponNames: List<String>): String {
    val friendlyName = removedCouponNames.firstOrNull { it.isNotBlank() }
    return if (friendlyName != null) {
        "El cupón $friendlyName ya no está disponible y se ha eliminado del carrito. Revisa el nuevo total y vuelve a pagar."
    } else {
        "El cupón aplicado ya no está disponible y se ha eliminado del carrito. Revisa el nuevo total y vuelve a pagar."
    }
}

internal fun isCouponInvalidationMessage(message: String?): Boolean =
    message?.startsWith("El cupón ") == true &&
        message.contains("ya no está disponible y se ha eliminado del carrito. Revisa el nuevo total y vuelve a pagar.")

internal suspend fun reconcileCouponInvalidation(
    updatedCart: WooCart?,
    replaceCart: (WooCart) -> Unit,
    reloadCheckout: suspend () -> CheckoutResponse,
    onCheckout: (CheckoutResponse?) -> Unit,
    onError: (String) -> Unit,
    onPhase: (CheckoutPhase) -> Unit,
    userMessage: String
) {
    updatedCart?.let(replaceCart)
    onCheckout(null)
    onError(userMessage)
    if (updatedCart == null) {
        onPhase(CheckoutPhase.FAILED)
        return
    }
    try {
        val refreshedCheckout = reloadCheckout()
        onCheckout(refreshedCheckout)
        onPhase(CheckoutPhase.READY)
    } catch (exception: CancellationException) {
        throw exception
    } catch (_: Exception) {
        onCheckout(null)
        onPhase(CheckoutPhase.FAILED)
    }
}
