package es.criosrango.shared

import es.criosrango.shared.model.PaymentStatusResponse
import kotlinx.coroutines.delay

enum class SharedPaymentReconciliationResult { PAID, TERMINAL_UNPAID, EXHAUSTED }

fun isSharedPaymentConfirmed(order: PaymentStatusResponse): Boolean {
    val status = order.status.trim().lowercase()
    return order.paid || status == "processing" || status == "completed"
}

fun isSharedPaymentTerminalUnpaid(order: PaymentStatusResponse): Boolean {
    val status = order.status.trim().lowercase()
    return !isSharedPaymentConfirmed(order) &&
        (order.terminal || status == "failed" || status == "cancelled" || status == "refunded")
}

suspend fun reconcileSharedPaymentStatus(
    maxRetries: Int,
    delayMs: Long,
    lookup: suspend () -> PaymentStatusResponse,
    isTransientException: (Throwable) -> Boolean
): SharedPaymentReconciliationResult {
    var retries = 0
    while (true) {
        val order = try {
            lookup()
        } catch (exception: Exception) {
            if (!isTransientException(exception)) throw exception
            if (retries >= maxRetries) return SharedPaymentReconciliationResult.EXHAUSTED
            retries++
            delay(delayMs)
            continue
        }
        when {
            isSharedPaymentConfirmed(order) -> return SharedPaymentReconciliationResult.PAID
            isSharedPaymentTerminalUnpaid(order) -> return SharedPaymentReconciliationResult.TERMINAL_UNPAID
            retries >= maxRetries -> return SharedPaymentReconciliationResult.EXHAUSTED
            else -> {
                retries++
                delay(delayMs)
            }
        }
    }
}
