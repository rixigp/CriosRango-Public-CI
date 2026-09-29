package es.criosrango.app

import android.content.SharedPreferences
import es.criosrango.shared.model.OutletAvailability
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class OutletAvailabilityStore(
    private val repository: StoreRepository,
    private val preferences: SharedPreferences
) {
    companion object {
        private const val SNAPSHOT_KEY = "outlet_availability_snapshot"
        private const val SCHEMA_VERSION = 1
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _snapshot = MutableStateFlow(loadSnapshot())
    val snapshot: StateFlow<OutletAvailability?> = _snapshot.asStateFlow()

    private val refreshMutex = Mutex()
    private var refreshInFlight: CompletableDeferred<Unit>? = null

    suspend fun refresh() {
        val (owner, deferred) = refreshMutex.withLock {
            refreshInFlight?.let { false to it } ?: CompletableDeferred<Unit>().also {
                refreshInFlight = it
                true to it
            }
        }

        if (!owner) {
            deferred.await()
            return
        }

        try {
            val fresh = repository.outletAvailability()
            require(fresh.schemaVersion == SCHEMA_VERSION) {
                "Unsupported Outlet availability schema_version=${fresh.schemaVersion}"
            }

            _snapshot.value = fresh
            preferences.edit()
                .putString(SNAPSHOT_KEY, json.encodeToString(OutletAvailability.serializer(), fresh))
                .apply()

            deferred.complete(Unit)
        } catch (exception: Exception) {
            deferred.completeExceptionally(exception)
            throw exception
        } finally {
            refreshMutex.withLock {
                if (refreshInFlight === deferred) {
                    refreshInFlight = null
                }
            }
        }
    }

    private fun loadSnapshot(): OutletAvailability? =
        preferences.getString(SNAPSHOT_KEY, null)
            ?.let { raw ->
                runCatching {
                    json.decodeFromString(OutletAvailability.serializer(), raw)
                }.getOrNull()
            }
            ?.takeIf { it.schemaVersion == SCHEMA_VERSION }
}
