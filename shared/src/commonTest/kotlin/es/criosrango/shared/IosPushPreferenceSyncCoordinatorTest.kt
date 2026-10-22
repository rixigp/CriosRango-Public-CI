package es.criosrango.shared

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosPushPreferenceSyncCoordinatorTest {
    @Test fun onToOffAcceptedConfirmsOff() = runTest {
        val c = coordinator { _, orders -> assertFalse(orders); true }
        c.setOrderUpdates(false)
        c.synchronize()
        assertFalse(c.state.confirmed.orderUpdates)
        assertEquals(c.state.desired, c.state.confirmed)
        assertFalse(c.state.syncPending)
    }

    @Test fun offToOnAcceptedConfirmsOn() = runTest {
        val c = coordinator(initialOrders = false) { _, orders -> assertTrue(orders); true }
        c.setOrderUpdates(true)
        c.synchronize()
        assertTrue(c.state.confirmed.orderUpdates)
    }

    @Test fun failurePreservesConfirmedDesiredAndPending() = runTest {
        val c = coordinator { _, _ -> false }
        c.setOrderUpdates(false)
        c.synchronize()
        assertTrue(c.state.confirmed.orderUpdates)
        assertFalse(c.state.desired.orderUpdates)
        assertTrue(c.state.syncPending)
        assertTrue(c.state.error)
    }

    @Test fun latestChangeDuringRequestIsSentAfterOldSnapshot() = runTest {
        val started = CompletableDeferred<Unit>()
        val finishFirst = CompletableDeferred<Boolean>()
        val sent = mutableListOf<Boolean>()
        val c = coordinator(post = { _, orders ->
            sent += orders
            if (sent.size == 1) {
                started.complete(Unit)
                finishFirst.await()
            } else true
        })
        c.setOrderUpdates(false)
        val request = async { c.synchronize() }
        started.await()
        c.setOrderUpdates(true)
        finishFirst.complete(true)
        request.await()
        assertEquals(listOf(false, true), sent)
        assertTrue(c.state.confirmed.orderUpdates)
        assertEquals(c.state.desired, c.state.confirmed)
        assertFalse(c.state.syncPending)
    }

    @Test fun simultaneousPreferenceChangesPersistBothLatestValues() = runTest {
        val sent = mutableListOf<IosPushPreferenceSyncCoordinator.Snapshot>()
        val c = coordinator(post = { news, orders ->
            sent += IosPushPreferenceSyncCoordinator.Snapshot(news, orders)
            true
        })
        c.setOrderUpdates(false)
        c.setNewProducts(false)
        c.synchronize()
        assertEquals(IosPushPreferenceSyncCoordinator.Snapshot(false, false), sent.last())
        assertEquals(sent.last(), c.state.confirmed)
    }

    @Test fun manualRetryAfterNetworkErrorConfirmsDesired() = runTest {
        var attempts = 0
        val c = coordinator(post = { _, _ -> attempts++; attempts > 1 })
        c.setOrderUpdates(false)
        c.synchronize()
        assertTrue(c.state.syncPending)
        c.synchronize(force = true)
        assertEquals(c.state.desired, c.state.confirmed)
        assertFalse(c.state.syncPending)
    }

    private fun coordinator(
        initialOrders: Boolean = true,
        post: suspend (Boolean, Boolean) -> Boolean
    ) = IosPushPreferenceSyncCoordinator(
        initialNewProducts = true,
        initialOrderUpdates = initialOrders,
        persistDesired = { _, _ -> },
        postSnapshot = post
    )
}
