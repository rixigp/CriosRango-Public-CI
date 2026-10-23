package es.criosrango.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class IosPushPreferenceSyncCoordinatorTest {
    private fun unregisterSnapshot(token: String = "apns-A", account: String? = "account-A", generation: Long = 7L) =
        PushUnregisterSnapshot(token, account, generation)

    @Test fun T1_pendingUnregisterIsRestoredByNewCoordinatorAfterRestart() {
        val persisted = unregisterSnapshot()
        val restarted = IosPushPreferenceSyncCoordinator(
            true, true, { _, _ -> }, { true },
            initialPendingUnregisterSnapshot = persisted,
            unregister = { true }
        )
        assertEquals(persisted, restarted.pendingUnregisterSnapshotForTest())
    }

    @Test fun T2_restoredPendingUnregisterRetriesAndClearsOnlyAfterSuccess() = runBlocking {
        val persisted = unregisterSnapshot()
        var saved: PushUnregisterSnapshot? = persisted
        var calls = 0
        val c = IosPushPreferenceSyncCoordinator(
            true, true, { _, _ -> }, { true },
            initialPendingUnregisterSnapshot = saved,
            persistUnregisterSnapshot = { saved = it },
            unregister = { calls++; calls > 1 }
        )
        c.synchronize(force = true)
        assertEquals(1, calls)
        assertEquals(persisted, saved)
        assertEquals(persisted, c.pendingUnregisterSnapshotForTest())
        c.synchronize(force = true)
        assertEquals(2, calls)
        assertNull(saved)
        assertNull(c.pendingUnregisterSnapshotForTest())
    }

    @Test fun T3_logoutCapturesAccountSnapshotSynchronouslyBeforeSessionClear() {
        var visibleAccount: String? = "account-A"
        var persisted: PushUnregisterSnapshot? = null
        val snapshots = mutableListOf<PushUnregisterSnapshot?>()
        val c = IosPushPreferenceSyncCoordinator(
            true, true, { _, _ -> }, { true },
            captureUnregisterSnapshot = { generation -> PushUnregisterSnapshot("apns-A", visibleAccount, generation) },
            persistUnregisterSnapshot = { persisted = it; snapshots += it },
            unregister = { true }
        )
        c.requestUnregister(CoroutineScope(Dispatchers.Unconfined))
        visibleAccount = null
        val captured = snapshots.first { it != null }
        assertEquals("account-A", captured?.accountToken)
        assertEquals("apns-A", captured?.deviceToken)
        assertEquals(1L, captured?.registrationGeneration)
    }

    @Test fun T4_registerAResponseAfterLogoutAndLoginBIsStale() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var confirmedWrites = 0
        val c = IosPushPreferenceSyncCoordinator(
            true, true, { _, _ -> }, {
                if (!entered.isCompleted) { entered.complete(Unit); release.await() }
                true
            },
            persistConfirmed = { _, _ -> confirmedWrites++ }
        )
        val a = launch { c.synchronize(force = true) }
        entered.await()
        c.registrationContextChanged() // logout A invalidates in-flight registration
        c.registrationContextChanged() // login B establishes a newer generation
        release.complete(Unit)
        a.join()
        assertEquals(0, confirmedWrites)
        assertTrue(c.state.syncPending)
    }

    @Test fun T5_pendingUnregisterAIsRetriedWithSnapshotABeforeRegisterB() = runBlocking {
        val snapshotA = unregisterSnapshot()
        var persisted: PushUnregisterSnapshot? = snapshotA
        val calls = mutableListOf<String>()
        val c = IosPushPreferenceSyncCoordinator(
            true, true, { _, _ -> }, { _, _ -> calls += "register-B"; true },
            initialPendingUnregisterSnapshot = snapshotA,
            persistUnregisterSnapshot = { persisted = it },
            unregister = { snapshot -> calls += "unregister-${snapshot.accountToken}"; snapshot == snapshotA }
        )
        c.registrationContextChanged() // B login; pending A must not be replaced with B
        c.synchronize(force = true)
        assertEquals(listOf("unregister-account-A", "register-B"), calls)
        assertNull(persisted)
        assertFalse(c.state.syncPending)
    }

    @Test fun T6_offThenOnDuringFirstRequestSendsLatestDesiredSnapshot() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val sent = mutableListOf<IosPushPreferenceSyncCoordinator.Snapshot>()
        var confirmed = IosPushPreferenceSyncCoordinator.Snapshot(true, true)
        var pending = false
        val c = IosPushPreferenceSyncCoordinator(
            true, true, { _, _ -> }, { n, o ->
                sent += IosPushPreferenceSyncCoordinator.Snapshot(n, o)
                if (sent.size == 1) { entered.complete(Unit); release.await() }
                true
            },
            persistConfirmed = { n, o -> confirmed = IosPushPreferenceSyncCoordinator.Snapshot(n, o) },
            persistPending = { pending = it }
        )
        c.setOrderUpdates(false)
        val job = launch { c.synchronize() }
        entered.await()
        c.setOrderUpdates(true)
        release.complete(Unit)
        job.join()
        assertEquals(
            listOf(
                IosPushPreferenceSyncCoordinator.Snapshot(true, false),
                IosPushPreferenceSyncCoordinator.Snapshot(true, true)
            ),
            sent
        )
        assertEquals(IosPushPreferenceSyncCoordinator.Snapshot(true, true), confirmed)
        assertEquals(IosPushPreferenceSyncCoordinator.Snapshot(true, true), c.state.desired)
        assertFalse(pending)
        assertFalse(c.state.syncPending)
        assertFalse(c.state.error)
    }

    @Test fun T7_networkErrorThenManualRetry200ClearsPendingAndError() = runBlocking {
        var calls = 0
        var confirmed = IosPushPreferenceSyncCoordinator.Snapshot(true, true)
        var pending = false
        val c = IosPushPreferenceSyncCoordinator(
            true, true, { _, _ -> }, { _, _ -> ++calls > 1 },
            persistConfirmed = { n, o -> confirmed = IosPushPreferenceSyncCoordinator.Snapshot(n, o) },
            persistPending = { pending = it }
        )
        c.setOrderUpdates(false)
        c.synchronize()
        assertEquals(IosPushPreferenceSyncCoordinator.Snapshot(true, true), confirmed)
        assertEquals(IosPushPreferenceSyncCoordinator.Snapshot(true, false), c.state.desired)
        assertTrue(pending)
        assertTrue(c.state.syncPending)
        assertTrue(c.state.error)

        c.synchronize(force = true)
        assertEquals(IosPushPreferenceSyncCoordinator.Snapshot(true, false), confirmed)
        assertEquals(IosPushPreferenceSyncCoordinator.Snapshot(true, false), c.state.desired)
        assertFalse(pending)
        assertFalse(c.state.syncPending)
        assertFalse(c.state.error)
    }
}
