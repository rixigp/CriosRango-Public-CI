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
    actual fun newProducts() = if (d.objectForKey("criosrango_push_new_products") == null) true else d.boolForKey("criosrango_push_new_products")
    actual fun orderUpdates() = if (d.objectForKey("criosrango_push_order_updates") == null) true else d.boolForKey("criosrango_push_order_updates")
    actual fun setNewProducts(value: Boolean) { d.setBool(value, "criosrango_push_new_products") }
    actual fun setOrderUpdates(value: Boolean) { d.setBool(value, "criosrango_push_order_updates") }
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
                        NSNotificationCenter.defaultCenter.postNotificationName("criosrango.push.refresh-registration", object = null)
                    }
                }
            }
        }
    }
}
