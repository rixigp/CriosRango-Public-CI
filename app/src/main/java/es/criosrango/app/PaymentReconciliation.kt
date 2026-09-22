package es.criosrango.app

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import es.criosrango.shared.api.StoreApiException

internal const val PAYMENT_RECONCILIATION_MAX_RETRIES = 10
internal const val PAYMENT_RECONCILIATION_DELAY_MS = 1500L

internal sealed interface PaymentReconciliationResult {
    data class PAID(val order: OrderStatusResponse) : PaymentReconciliationResult
    data object TERMINAL_UNPAID : PaymentReconciliationResult
    data object EXHAUSTED : PaymentReconciliationResult
}

internal fun isPaymentConfirmed(order: OrderStatusResponse): Boolean {
    val status = order.status.trim().lowercase()
    return order.paid || status == "processing" || status == "completed"
}

internal fun isTerminalUnpaid(order: OrderStatusResponse): Boolean {
    val status = order.status.trim().lowercase()
    return !isPaymentConfirmed(order) &&
        (order.terminal || status == "failed" || status == "cancelled" || status == "refunded")
}

internal fun isTransientPaymentStatusException(exception: Throwable): Boolean {
    return exception is IOException ||
        exception is SocketTimeoutException ||
        exception is TimeoutCancellationException ||
        (exception is StoreApiException && (exception.statusCode == 429 || exception.statusCode in 500..599))
}

internal suspend fun reconcilePaymentStatus(
    maxRetries: Int = PAYMENT_RECONCILIATION_MAX_RETRIES,
    delayMs: Long = PAYMENT_RECONCILIATION_DELAY_MS,
    lookup: suspend () -> OrderStatusResponse
): PaymentReconciliationResult {
    var retries = 0
    while (true) {
        val order = try {
            lookup()
        } catch (exception: Exception) {
            if (!isTransientPaymentStatusException(exception)) throw exception
            if (retries >= maxRetries) return PaymentReconciliationResult.EXHAUSTED
            retries++
            delay(delayMs)
            continue
        }

        when {
            isPaymentConfirmed(order) -> return PaymentReconciliationResult.PAID(order)
            isTerminalUnpaid(order) -> return PaymentReconciliationResult.TERMINAL_UNPAID
            retries >= maxRetries -> return PaymentReconciliationResult.EXHAUSTED
            else -> {
                retries++
                delay(delayMs)
            }
        }
    }
}
