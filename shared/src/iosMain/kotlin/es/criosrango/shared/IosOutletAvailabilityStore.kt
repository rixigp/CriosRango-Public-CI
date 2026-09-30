package es.criosrango.shared

import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.model.OutletAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import platform.Foundation.NSUserDefaults

/**
 * iOS persistent stale-while-revalidate store for Outlet availability.
 * The endpoint itself remains owned by StoreApiClient.
 */
class IosOutletAvailabilityStore(
    private val storeApi: StoreApiClient,
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults
) {
    companion object {
        private const val SNAPSHOT_KEY = "outlet_availability_snapshot"
        private const val SCHEMA_VERSION = 1
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _snapshot = MutableStateFlow(loadSnapshot())
    val snapshot: StateFlow<OutletAvailability?> = _snapshot.asStateFlow()

    suspend fun refresh() {
        val fresh = storeApi.outletAvailability()
        require(fresh.schemaVersion == SCHEMA_VERSION) {
            "Unsupported Outlet availability schema_version=${fresh.schemaVersion}"
        }
        _snapshot.value = fresh
        defaults.setObject(
            json.encodeToString(OutletAvailability.serializer(), fresh),
            forKey = SNAPSHOT_KEY
        )
    }

    private fun loadSnapshot(): OutletAvailability? =
        defaults.stringForKey(SNAPSHOT_KEY)
            ?.let { raw -> runCatching { json.decodeFromString(OutletAvailability.serializer(), raw) }.getOrNull() }
            ?.takeIf { it.schemaVersion == SCHEMA_VERSION }
}
