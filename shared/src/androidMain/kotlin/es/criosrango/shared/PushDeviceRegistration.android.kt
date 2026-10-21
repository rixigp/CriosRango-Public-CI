package es.criosrango.shared

actual object PushDeviceRegistration {
    // Android token registration and logout are owned by PushNotificationController in :app.
    actual suspend fun registerCurrentDevice() = Unit
    actual suspend fun unregisterCurrentDevice() = Unit
}
