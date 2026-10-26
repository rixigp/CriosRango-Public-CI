package es.criosrango.shared

import es.criosrango.shared.model.StoreCartLine

/** Woo Store API quantity increment, defaulting invalid/missing multipleOf to one. */
fun StoreCartLine.quantityStep(): Int =
    quantityLimits?.multipleOf?.takeIf { it > 0 } ?: 1

/** True only when adding one step stays within the server-provided maximum. */
fun StoreCartLine.canIncreaseQuantity(): Boolean {
    val maximum = quantityLimits?.maximum?.takeUnless { it == 9999 }
    return maximum == null || quantity + quantityStep() <= maximum
}

/** Null means the minus action must remove the line rather than update it below minimum. */
fun StoreCartLine.quantityAfterDecrement(): Int? {
    val candidate = quantity - quantityStep()
    val minimum = quantityLimits?.minimum ?: 1
    return candidate.takeIf { it >= minimum }
}
