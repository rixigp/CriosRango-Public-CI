package es.criosrango.shared

/**
 * Owns desired and server-confirmed push preferences. Only one snapshot is sent at a time.
 */
class IosPushPreferenceSyncCoordinator(
    initialNewProducts: Boolean,
    initialOrderUpdates: Boolean,
    private val persistDesired: (newProducts: Boolean, orderUpdates: Boolean) -> Unit,
    private val postSnapshot: suspend (newProducts: Boolean, orderUpdates: Boolean) -> Boolean,
    private val onStateChanged: (State) -> Unit = {}
) {
    data class Snapshot(val newProducts: Boolean, val orderUpdates: Boolean)
    data class State(
        val desired: Snapshot,
        val confirmed: Snapshot,
        val syncInProgress: Boolean = false,
        val syncPending: Boolean = false,
        val error: Boolean = false
    )

    var state: State = State(
        desired = Snapshot(initialNewProducts, initialOrderUpdates),
        confirmed = Snapshot(initialNewProducts, initialOrderUpdates)
    )
        private set

    private fun publish(next: State) {
        state = next
        onStateChanged(next)
    }

    fun setNewProducts(value: Boolean) = update(state.desired.copy(newProducts = value))
    fun setOrderUpdates(value: Boolean) = update(state.desired.copy(orderUpdates = value))

    private fun update(desired: Snapshot) {
        if (desired == state.desired) return
        persistDesired(desired.newProducts, desired.orderUpdates)
        publish(state.copy(desired = desired, syncPending = true, error = false))
    }

    suspend fun synchronize(force: Boolean = false) {
        if (state.syncInProgress) return
        if (!force && state.desired == state.confirmed && !state.syncPending) return
        publish(state.copy(syncInProgress = true, syncPending = true, error = false))
        while (true) {
            val sent = state.desired
            val accepted = try {
                postSnapshot(sent.newProducts, sent.orderUpdates)
            } catch (_: Throwable) {
                false
            }
            if (!accepted) {
                publish(state.copy(syncInProgress = false, syncPending = true, error = true))
                return
            }
            publish(state.copy(confirmed = sent, error = false))
            if (state.desired == state.confirmed) {
                publish(state.copy(syncInProgress = false, syncPending = false, error = false))
                return
            }
        }
    }
}
