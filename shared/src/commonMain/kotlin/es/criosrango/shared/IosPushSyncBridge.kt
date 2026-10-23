package es.criosrango.shared

/** Stable Swift-callable entry points. Swift owns APNs callbacks, never registration HTTP. */
fun requestIosPushSync() = IosPushPreferenceSyncRuntime.onForeground()
fun requestIosPushTokenSync() = IosPushPreferenceSyncRuntime.apnsTokenChanged()
fun requestIosPushSessionSync() = IosPushPreferenceSyncRuntime.sessionChanged()
fun requestIosPushLogout() = IosPushPreferenceSyncRuntime.logout()
