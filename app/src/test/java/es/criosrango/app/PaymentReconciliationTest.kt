package es.criosrango.app

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class PaymentReconciliationTest {
    private fun order(status: String, paid: Boolean = false, terminal: Boolean = false) =
        OrderStatusResponse(id = 55841, status = status, paid = paid, terminal = terminal)

    @Test fun firstIOExceptionThenProcessingConfirmsPayment() = runBlocking {
        val calls = AtomicInteger(0)
        val result = reconcilePaymentStatus(maxRetries = 2, delayMs = 0) {
            if (calls.getAndIncrement() == 0) throw IOException("temporary")
            order("processing")
        }
        assertTrue(result is PaymentReconciliationResult.PAID)
        assertEquals(2, calls.get())
    }

    @Test fun firstTimeoutThenPaidConfirmsPayment() = runBlocking {
        val calls = AtomicInteger(0)
        val result = reconcilePaymentStatus(maxRetries = 2, delayMs = 0) {
            if (calls.getAndIncrement() == 0) withTimeout(1) { delay(50) }
            order("pending", paid = true)
        }
        assertTrue(result is PaymentReconciliationResult.PAID)
        assertEquals(2, calls.get())
    }

    @Test fun pendingPendingProcessingConfirmsPayment() = runBlocking {
        val states = ArrayDeque(listOf(order("pending"), order("pending"), order("processing")))
        val result = reconcilePaymentStatus(maxRetries = 3, delayMs = 0) { states.removeFirst() }
        assertTrue(result is PaymentReconciliationResult.PAID)
    }

    @Test fun processingWithPaidFalseIsConfirmed() = runBlocking {
        val result = reconcilePaymentStatus(maxRetries = 0, delayMs = 0) { order("processing") }
        assertTrue(result is PaymentReconciliationResult.PAID)
        assertTrue(isPaymentConfirmed(order("processing")))
    }

    @Test fun completedWithPaidFalseIsConfirmed() = runBlocking {
        val result = reconcilePaymentStatus(maxRetries = 0, delayMs = 0) { order("completed") }
        assertTrue(result is PaymentReconciliationResult.PAID)
        assertTrue(isPaymentConfirmed(order("completed")))
    }

    @Test fun pendingAfterExhaustionRemainsPending() = runBlocking {
        val result = reconcilePaymentStatus(maxRetries = 2, delayMs = 0) { order("pending") }
        assertEquals(PaymentReconciliationResult.EXHAUSTED, result)
    }

    @Test fun failedAndCancelledAreTerminalUnpaid() = runBlocking {
        val failed = reconcilePaymentStatus(maxRetries = 0, delayMs = 0) { order("failed") }
        val cancelled = reconcilePaymentStatus(maxRetries = 0, delayMs = 0) { order("cancelled") }
        assertEquals(PaymentReconciliationResult.TERMINAL_UNPAID, failed)
        assertEquals(PaymentReconciliationResult.TERMINAL_UNPAID, cancelled)
    }

    @Test fun paidRecoveredAfterProcessDeathIsConfirmed() = runBlocking {
        val result = reconcilePaymentStatus(maxRetries = 1, delayMs = 0) { order("processing") }
        assertTrue(result is PaymentReconciliationResult.PAID)
    }
}
