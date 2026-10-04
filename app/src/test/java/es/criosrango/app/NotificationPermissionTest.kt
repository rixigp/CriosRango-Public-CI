package es.criosrango.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationPermissionTest {
    @Test
    fun doesNotRequestBelowApi33() {
        assertFalse(shouldRequestNotificationPermission(32, false, false))
    }

    @Test
    fun requestsOnceOnApi33WhenNotGranted() {
        assertTrue(shouldRequestNotificationPermission(33, false, false))
        assertFalse(shouldRequestNotificationPermission(33, true, false))
    }

    @Test
    fun doesNotRequestWhenAlreadyGranted() {
        assertFalse(shouldRequestNotificationPermission(33, false, true))
        assertFalse(shouldRequestNotificationPermission(36, true, true))
    }
}
