package es.criosrango.shared

import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSUserDefaults
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNUserNotificationCenter

actual object PushPreferencesPlatform {
    private val d = NSUserDefaults.standardUserDefaults
    private const val NEWS = "criosrango_push_new_products"
    private const val ORDERS = "criosrango_push_order_updates"
    private const val CONFIRMED_NEWS = "criosrango_push_confirmed_new_products"
    private const val CONFIRMED_ORDERS = "criosrango_push_confirmed_order_updates"
    private const val PENDING = "criosrango_push_sync_pending"

    actual fun newProducts() = if (d.objectForKey(NEWS) == null) true else d.boolForKey(NEWS)
    actual fun orderUpdates() = if (d.objectForKey(ORDERS) == null) true else d.boolForKey(ORDERS)
    actual fun setNewProducts(value: Boolean) { d.setBool(value, NEWS) }
    actual fun setOrderUpdates(value: Boolean) { d.setBool(value, ORDERS) }

    actual fun loadSyncState(): PushPreferencesSyncState {
        val desired = PushPreferencesSnapshot(newProducts(), orderUpdates())
        val confirmed = PushPreferencesSnapshot(
            if (d.objectForKey(CONFIRMED_NEWS) == null) true else d.boolForKey(CONFIRMED_NEWS),
            if (d.objectForKey(CONFIRMED_ORDERS) == null) true else d.boolForKey(CONFIRMED_ORDERS)
        )
        val pending = if (d.objectForKey(PENDING) == null) desired != confirmed else d.boolForKey(PENDING)
        return PushPreferencesSyncState(desired, confirmed, pending)
    }

    actual fun saveDesired(snapshot: PushPreferencesSnapshot) {
        d.setBool(snapshot.newProducts, NEWS)
        d.setBool(snapshot.orderUpdates, ORDERS)
    }

    actual fun saveConfirmed(snapshot: PushPreferencesSnapshot) {
        d.setBool(snapshot.newProducts, CONFIRMED_NEWS)
        d.setBool(snapshot.orderUpdates, CONFIRMED_ORDERS)
    }

    actual fun savePending(pending: Boolean) { d.setBool(pending, PENDING) }

    actual fun requestPermission() {
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            val status = when (settings?.authorizationStatus) {
                UNAuthorizationStatusAuthorized, UNAuthorizationStatusProvisional, UNAuthorizationStatusEphemeral -> "AUTORIZADO"
                UNAuthorizationStatusDenied -> "DENEGADO"
                UNAuthorizationStatusNotDetermined -> "NO DETERMINADO"
                else -> "NO DETERMINADO"
            }
            IosPushDiagnostics.recordPermission(status)
            if (status == "AUTORIZADO") {
                IosPushDiagnostics.recordRegistrationRequested()
                NSNotificationCenter.defaultCenter.postNotificationName("criosrango.push.refresh-registration", null)
            } else if (status == "NO DETERMINADO") {
                UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
                    UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound
                ) { granted, error ->
                    IosPushDiagnostics.recordPermission(if (granted) "AUTORIZADO" else "DENEGADO")
                    if (error != null) IosPushDiagnostics.recordRegistrationFailure(error::class.simpleName ?: "NSError", error.localizedDescription)
                    if (granted) {
                        IosPushDiagnostics.recordRegistrationRequested()
                        NSNotificationCenter.defaultCenter.postNotificationName("criosrango.push.refresh-registration", null)
                    }
                }
            }
        }
    }
}
