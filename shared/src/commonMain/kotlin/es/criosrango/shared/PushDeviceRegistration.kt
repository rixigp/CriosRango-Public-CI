package es.criosrango.shared

data class PushUnregisterSnapshot(
    val deviceToken: String,
    val accountToken: String?,
    val registrationGeneration: Long
)

expect object PushDeviceRegistration {
    suspend fun registerCurrentDevice()
    suspend fun unregisterCurrentDevice()
    fun hasPendingUnregister(): Boolean
    fun captureUnregisterSnapshot(registrationGeneration: Long): PushUnregisterSnapshot?
    fun loadPendingUnregisterSnapshot(): PushUnregisterSnapshot?
    fun persistPendingUnregisterSnapshot(snapshot: PushUnregisterSnapshot)
    fun clearPendingUnregisterSnapshot(snapshot: PushUnregisterSnapshot)
    suspend fun unregisterSnapshot(snapshot: PushUnregisterSnapshot): Boolean
}
