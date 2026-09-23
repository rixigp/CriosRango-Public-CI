package es.criosrango.app

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.TimeoutCancellationException
import es.criosrango.shared.api.StoreApiException
import es.criosrango.shared.reconcileSharedPaymentStatus
import es.criosrango.shared.SharedPaymentReconciliationResult
import es.criosrango.shared.model.PaymentStatusResponse

internal const val PAYMENT_RECONCILIATION_MAX_RETRIES = 10
internal const val PAYMENT_RECONCILIATION_DELAY_MS = 1500L

internal sealed interface PaymentReconciliationResult {
    data class PAID(val order: OrderStatusResponse) : PaymentReconciliationResult
    data object TERMINAL_UNPAID : PaymentReconciliationResult
    data object EXHAUSTED : PaymentReconciliationResult
}

internal fun isPaymentConfirmed(order: OrderStatusResponse): Boolean =
    es.criosrango.shared.isSharedPaymentConfirmed(PaymentStatusResponse(order.id, order.status, order.paid, order.needsPayment, order.terminal))

internal fun isTerminalUnpaid(order: OrderStatusResponse): Boolean =
    es.criosrango.shared.isSharedPaymentTerminalUnpaid(PaymentStatusResponse(order.id, order.status, order.paid, order.needsPayment, order.terminal))

internal fun isTransientPaymentStatusException(exception: Throwable): Boolean =
    exception is IOException ||
        exception is SocketTimeoutException ||
        exception is TimeoutCancellationException ||
        (exception is StoreApiException && (exception.statusCode == 429 || exception.statusCode in 500..599))

internal suspend fun reconcilePaymentStatus(
    maxRetries: Int = PAYMENT_RECONCILIATION_MAX_RETRIES,
    delayMs: Long = PAYMENT_RECONCILIATION_DELAY_MS,
    lookup: suspend () -> OrderStatusResponse
): PaymentReconciliationResult {
    var lastOrder: OrderStatusResponse? = null
    return when (
        val result = reconcileSharedPaymentStatus(
            maxRetries = maxRetries,
            delayMs = delayMs,
            lookup = {
                val order = lookup()
                lastOrder = order
                PaymentStatusResponse(order.id, order.status, order.paid, order.needsPayment, order.terminal)
            },
            isTransientException = ::isTransientPaymentStatusException
        )
    ) {
        SharedPaymentReconciliationResult.PAID -> PaymentReconciliationResult.PAID(lastOrder ?: lookup())
        SharedPaymentReconciliationResult.TERMINAL_UNPAID -> PaymentReconciliationResult.TERMINAL_UNPAID
        SharedPaymentReconciliationResult.EXHAUSTED -> PaymentReconciliationResult.EXHAUSTED
    }
}
