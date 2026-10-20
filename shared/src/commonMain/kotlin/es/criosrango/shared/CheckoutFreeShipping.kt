package es.criosrango.shared

/**
 * Free-shipping progress uses Woo Store API currency minor units.
 * The eligible amount must be supplied by StoreCartTotals.consumerSubtotal()
 * (total_items + total_items_tax); shipping, coupons, wallet and final total
 * must not be substituted here.
 */
data class CheckoutFreeShippingProgress(
    val eligibleSubtotalMinorUnits: Long,
    val thresholdMinorUnits: Long,
    val remainingMinorUnits: Long,
    val reached: Boolean
)

fun checkoutFreeShippingProgress(
    consumerSubtotalMinorUnits: String,
    currencyMinorUnit: Int
): CheckoutFreeShippingProgress {
    val scale = currencyMinorUnit.coerceIn(0, 6)
    var threshold = 50L
    repeat(scale) { threshold *= 10L }
    val subtotal = consumerSubtotalMinorUnits.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
    val remaining = (threshold - subtotal).coerceAtLeast(0L)
    return CheckoutFreeShippingProgress(
        eligibleSubtotalMinorUnits = subtotal,
        thresholdMinorUnits = threshold,
        remainingMinorUnits = remaining,
        reached = subtotal >= threshold
    )
}
