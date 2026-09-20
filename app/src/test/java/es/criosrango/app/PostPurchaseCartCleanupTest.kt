package es.criosrango.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PostPurchaseCartCleanupTest {
    private fun line(key: String) = CartLine(key = key, id = 100, quantity = 1)

    @Test fun paidSingleLineSuccessLeavesPaidAndRemovesLine() = runBlocking {
        var paid = true
        val remaining = mutableListOf(line("A"))
        val result = clearConfirmedPaymentLines(remaining) { item ->
            remaining.removeIf { it.key == item.key }
            true
        }
        assertTrue(result)
        assertTrue(paid)
        assertTrue(remaining.isEmpty())
    }

    @Test fun paidMultipleLinesRemovesEachLine() = runBlocking {
        val remaining = mutableListOf(line("A"), line("B"))
        val removed = mutableListOf<String>()
        val result = clearConfirmedPaymentLines(remaining) { item ->
            removed += item.key
            remaining.removeIf { it.key == item.key }
            true
        }
        assertTrue(result)
        assertEquals(listOf("A", "B"), removed)
        assertTrue(remaining.isEmpty())
    }

    @Test fun failedRemovalKeepsPaidAndRequestsRetry() = runBlocking {
        var paid = true
        val remaining = mutableListOf(line("A"))
        var attempts = 0
        val first = clearConfirmedPaymentLines(remaining) {
            attempts++
            false
        }
        assertFalse(first)
        assertTrue(paid)
        assertEquals(1, attempts)
        assertEquals(1, remaining.size)

        val retry = clearConfirmedPaymentLines(remaining) { item ->
            remaining.removeIf { it.key == item.key }
            true
        }
        assertTrue(retry)
        assertTrue(remaining.isEmpty())
    }

    @Test fun pendingFailedOrCancelledPathsDoNotInvokeCleanup() = runBlocking {
        for (paid in listOf(false, null)) {
            var calls = 0
            val remaining = mutableListOf(line("A"))
            if (paid == true) {
                clearConfirmedPaymentLines(remaining) { calls++; true }
            }
            assertEquals(0, calls)
            assertEquals(1, remaining.size)
        }
    }
}
