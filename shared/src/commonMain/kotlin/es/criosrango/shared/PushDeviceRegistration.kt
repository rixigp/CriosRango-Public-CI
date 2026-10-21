package es.criosrango.shared

expect object PushDeviceRegistration {
    suspend fun registerCurrentDevice()
    suspend fun unregisterCurrentDevice()
}
