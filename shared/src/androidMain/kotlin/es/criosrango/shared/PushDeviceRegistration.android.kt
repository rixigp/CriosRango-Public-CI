package es.criosrango.shared

actual object PushDeviceRegistration {
    // Android token registration and logout remain owned by PushNotificationController in :app.
    actual suspend fun registerCurrentDevice() = Unit
    actual suspend fun unregisterCurrentDevice() = Unit
    actual fun hasPendingUnregister() = false
    actual fun captureUnregisterSnapshot(registrationGeneration: Long): PushUnregisterSnapshot? = null
    actual fun loadPendingUnregisterSnapshot(): PushUnregisterSnapshot? = null
    actual fun persistPendingUnregisterSnapshot(snapshot: PushUnregisterSnapshot) = Unit
    actual fun clearPendingUnregisterSnapshot(snapshot: PushUnregisterSnapshot) = Unit
    actual suspend fun unregisterSnapshot(snapshot: PushUnregisterSnapshot): Boolean = true
}
