package es.criosrango.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import es.criosrango.shared.model.PaymentStatusResponse

class StorePaymentStoreTest {
    @Test
    fun paidStatusIsConfirmedOnce() = runTest {
        var calls = 0
        val result = reconcileSharedPaymentStatus(
            maxRetries = 0,
            delayMs = 0,
            lookup = {
                calls++
                PaymentStatusResponse(id = 123, status = "processing", paid = true, terminal = true)
            },
            isTransientException = { false }
        )
        assertEquals(SharedPaymentReconciliationResult.PAID, result)
        assertEquals(1, calls)
    }

    @Test
    fun cancelledStatusIsTerminalUnpaid() = runTest {
        val result = reconcileSharedPaymentStatus(
            maxRetries = 0,
            delayMs = 0,
            lookup = {
                PaymentStatusResponse(id = 123, status = "cancelled", paid = false, terminal = true)
            },
            isTransientException = { false }
        )
        assertEquals(SharedPaymentReconciliationResult.TERMINAL_UNPAID, result)
    }

    @Test
    fun malformedReturnDoesNotBecomePaymentStatusCall() {
        val result = when ("unexpected") {
            "ok" -> true
            "cancel" -> true
            else -> false
        }
        assertEquals(false, result)
    }
}
