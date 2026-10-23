package es.criosrango.shared

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/** Single serialized owner for iOS preference registration and device unregistration. */
class IosPushPreferenceSyncCoordinator(
    initialNewProducts: Boolean,
    initialOrderUpdates: Boolean,
    private val persistDesired: (Boolean, Boolean) -> Unit,
    private val postSnapshot: suspend (Boolean, Boolean) -> Boolean,
    private val onStateChanged: (State) -> Unit = {},
    initialConfirmedNewProducts: Boolean = initialNewProducts,
    initialConfirmedOrderUpdates: Boolean = initialOrderUpdates,
    initialPending: Boolean = initialNewProducts != initialConfirmedNewProducts || initialOrderUpdates != initialConfirmedOrderUpdates,
    private val persistConfirmed: (Boolean, Boolean) -> Unit = { _, _ -> },
    private val persistPending: (Boolean) -> Unit = {},
    initialPendingUnregisterSnapshot: PushUnregisterSnapshot? = null,
    private val captureUnregisterSnapshot: (Long) -> PushUnregisterSnapshot? = { null },
    private val persistUnregisterSnapshot: (PushUnregisterSnapshot?) -> Unit = {},
    private val unregister: suspend (PushUnregisterSnapshot) -> Boolean = { true }
) {
    data class Snapshot(val newProducts: Boolean, val orderUpdates: Boolean)
    data class State(
        val desired: Snapshot,
        val confirmed: Snapshot,
        val syncInProgress: Boolean = false,
        val syncPending: Boolean = desired != confirmed,
        val error: Boolean = false,
        val revision: Long = 0
    )

    private val mutex = Mutex()
    private val observers = mutableSetOf<(State) -> Unit>()
    var currentRegistrationGeneration: Long = 0
        private set
    private var pendingUnregisterSnapshot: PushUnregisterSnapshot? = initialPendingUnregisterSnapshot
    internal fun pendingUnregisterSnapshotForTest(): PushUnregisterSnapshot? = pendingUnregisterSnapshot
    var state: State = State(
        Snapshot(initialNewProducts, initialOrderUpdates),
        Snapshot(initialConfirmedNewProducts, initialConfirmedOrderUpdates),
        syncPending = initialPending
    )
        private set

    fun observe(observer: (State) -> Unit): () -> Unit {
        observers += observer
        observer(state)
        return { observers -= observer }
    }

    private fun publish(next: State) {
        state = next
        onStateChanged(next)
        IosPushDiagnostics.recordCoordinatorState(
            desiredOrderUpdates = next.desired.orderUpdates,
            confirmedOrderUpdates = next.confirmed.orderUpdates,
            syncInProgress = next.syncInProgress,
            dirty = next.syncPending,
            revision = next.revision,
            generation = currentRegistrationGeneration
        )
        observers.toList().forEach { it(next) }
    }

    fun setNewProducts(value: Boolean) = update(state.desired.copy(newProducts = value))
    fun setOrderUpdates(value: Boolean) = update(state.desired.copy(orderUpdates = value))

    private fun update(desired: Snapshot) {
        if (desired == state.desired) return
        persistDesired(desired.newProducts, desired.orderUpdates)
        persistPending(true)
        publish(state.copy(desired = desired, revision = state.revision + 1, syncPending = true, error = false))
    }

    fun registrationContextChanged() {
        currentRegistrationGeneration++
        persistPending(true)
        publish(state.copy(syncPending = true, error = false, revision = state.revision + 1))
    }

    /** Captures and persists A's immutable DELETE context synchronously, before caller clears A's session. */
    fun requestUnregister(scope: CoroutineScope) {
        val generation = ++currentRegistrationGeneration
        val snapshot = pendingUnregisterSnapshot
            ?: captureUnregisterSnapshot(generation)
        if (snapshot != null) {
            pendingUnregisterSnapshot = snapshot
            persistUnregisterSnapshot(snapshot)
        }
        publish(state.copy(syncPending = true, error = false, revision = state.revision + 1))
        scope.launch { drainAndSync(force = false, allowPost = false) }
    }

    suspend fun synchronize(force: Boolean = false) = drainAndSync(force, allowPost = true)

    private suspend fun drainAndSync(force: Boolean, allowPost: Boolean) {
        val generationAtQueue = currentRegistrationGeneration
        IosPushDiagnostics.recordRequestStage("WAITING_FOR_MUTEX", revision = state.revision, generation = generationAtQueue)
        mutex.lock()
        // A queued ON may wait while enabling notifications refreshes APNs/session context.
        // Read generation after the lock, so it cannot be discarded using a stale queue-time value.
        val requestGeneration = currentRegistrationGeneration
        IosPushDiagnostics.recordRequestStage("MUTEX_ACQUIRED", revision = state.revision, generation = requestGeneration)
        try {
            val pendingUnregister = pendingUnregisterSnapshot
            if (pendingUnregister != null) {
                publish(state.copy(syncInProgress = true, syncPending = true, error = false))
                val removed = runCatching { unregister(pendingUnregister) }.getOrDefault(false)
                if (removed) {
                    if (pendingUnregisterSnapshot == pendingUnregister) {
                        pendingUnregisterSnapshot = null
                        persistUnregisterSnapshot(null)
                    }
                } else {
                    persistPending(true)
                    publish(state.copy(syncInProgress = false, syncPending = true, error = true))
                    return
                }
            }
            if (!allowPost) {
                publish(state.copy(syncInProgress = false, syncPending = state.desired != state.confirmed || pendingUnregisterSnapshot != null))
                return
            }
            if (requestGeneration != currentRegistrationGeneration) {
                publish(state.copy(syncInProgress = false, syncPending = true))
                return
            }
            if (!force && state.desired == state.confirmed && !state.syncPending) return
            publish(state.copy(syncInProgress = true, syncPending = true, error = false))
            persistPending(true)
            while (true) {
                if (requestGeneration != currentRegistrationGeneration) {
                    IosPushDiagnostics.recordRequestStage("DROPPED_AS_STALE", revision = state.revision, generation = requestGeneration, exceptionStage = "STALE_GENERATION")
                    persistPending(true)
                    publish(state.copy(syncInProgress = false, syncPending = true))
                    return
                }
                val sent = state.desired
                val sentRevision = state.revision
                IosPushDiagnostics.recordRequestStage(
                    "REQUEST_CREATED", sent.orderUpdates, sent.newProducts, sentRevision, requestGeneration
                )
                val accepted = try {
                    val result = postSnapshot(sent.newProducts, sent.orderUpdates)
                    IosPushDiagnostics.recordRequestStage("CALLBACK_RETURNED", revision = sentRevision, generation = requestGeneration)
                    result
                } catch (error: Throwable) {
                    IosPushDiagnostics.recordRequestStage(
                        "CALLBACK_EXCEPTION", revision = sentRevision, generation = requestGeneration, exceptionStage = "CALLBACK"
                    )
                    false
                }
                if (requestGeneration != currentRegistrationGeneration) {
                    IosPushDiagnostics.recordRequestStage(
                        "DROPPED_AS_STALE",
                        revision = state.revision,
                        generation = requestGeneration,
                        exceptionStage = "STALE_GENERATION"
                    )
                    persistPending(true)
                    publish(state.copy(syncInProgress = false, syncPending = true))
                    return
                }
                if (!accepted) {
                    persistPending(true)
                    // A failed request must not swallow a newer desired value selected in flight.
                    val newerDesiredExists = state.desired != sent || state.revision != sentRevision
                    if (newerDesiredExists) {
                        publish(state.copy(syncInProgress = true, syncPending = true, error = false))
                        continue
                    }
                    publish(state.copy(syncInProgress = false, syncPending = true, error = true))
                    return
                }
                persistConfirmed(sent.newProducts, sent.orderUpdates)
                val pending = state.desired != sent || state.revision != sentRevision
                persistPending(pending)
                publish(state.copy(confirmed = sent, syncPending = pending, error = false))
                if (!pending) {
                    publish(state.copy(syncInProgress = false, syncPending = false, error = false))
                    return
                }
            }
        } finally {
            // Never leave the runtime believing a request is active after any exceptional exit.
            val pendingAfterExit = state.syncPending || state.desired != state.confirmed || pendingUnregisterSnapshot != null
            if (state.syncInProgress || state.syncPending != pendingAfterExit) {
                runCatching {
                    persistPending(pendingAfterExit)
                    publish(state.copy(syncInProgress = false, syncPending = pendingAfterExit))
                }
            }
            mutex.unlock()
        }
    }
}

/** App-lifetime owner; startup restores the persisted immutable unregister snapshot before any register. */
object IosPushPreferenceSyncRuntime {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val persisted = PushPreferencesPlatform.loadSyncState()
    val coordinator = IosPushPreferenceSyncCoordinator(
        initialNewProducts = persisted.desired.newProducts,
        initialOrderUpdates = persisted.desired.orderUpdates,
        initialConfirmedNewProducts = persisted.confirmed.newProducts,
        initialConfirmedOrderUpdates = persisted.confirmed.orderUpdates,
        initialPending = persisted.pending || PushDeviceRegistration.loadPendingUnregisterSnapshot() != null,
        initialPendingUnregisterSnapshot = PushDeviceRegistration.loadPendingUnregisterSnapshot(),
        persistDesired = { n, o -> PushPreferencesPlatform.saveDesired(PushPreferencesSnapshot(n, o)) },
        persistConfirmed = { n, o -> PushPreferencesPlatform.saveConfirmed(PushPreferencesSnapshot(n, o)) },
        persistPending = PushPreferencesPlatform::savePending,
        captureUnregisterSnapshot = PushDeviceRegistration::captureUnregisterSnapshot,
        persistUnregisterSnapshot = { snapshot ->
            if (snapshot == null) {
                PushDeviceRegistration.loadPendingUnregisterSnapshot()?.let(PushDeviceRegistration::clearPendingUnregisterSnapshot)
            } else PushDeviceRegistration.persistPendingUnregisterSnapshot(snapshot)
        },
        postSnapshot = { n, o ->
            PushPreferencesPlatform.saveDesired(PushPreferencesSnapshot(n, o))
            runCatching { PushDeviceRegistration.registerCurrentDevice() }.isSuccess &&
                IosPushDiagnostics.snapshot.accepted == "SÍ" && !IosPushDiagnostics.snapshot.pendingSync
        },
        unregister = PushDeviceRegistration::unregisterSnapshot
    )

    fun synchronize(force: Boolean = false) { scope.launch { coordinator.synchronize(force) } }
    fun apnsTokenChanged() { coordinator.registrationContextChanged(); synchronize(force = true) }
    fun sessionChanged() { coordinator.registrationContextChanged(); synchronize(force = true) }
    fun logout() { coordinator.requestUnregister(scope) }

    fun onForeground() {
        if (PushDeviceRegistration.hasPendingUnregister() ||
            coordinator.state.syncPending ||
            coordinator.state.desired != coordinator.state.confirmed ||
            IosPushDiagnostics.snapshot.pendingSync) synchronize()
    }
}
