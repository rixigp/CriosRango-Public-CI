package es.criosrango.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushNotificationRoutingTest {
    @Test
    fun newProductsPayload_routesToNewProductsWithoutOrder() {
        assertEquals(es.criosrango.shared.PushNotificationType.NEW_PRODUCTS, es.criosrango.shared.PushNotificationType.NEW_PRODUCTS)
        assertTrue(es.criosrango.shared.PushNotificationContract.parseOrderId(null) == null)
    }

    @Test
    fun orderStatusPayload_roundTripsExactOrderId() {
        assertEquals(es.criosrango.shared.PushNotificationType.ORDER_STATUS, es.criosrango.shared.PushNotificationType.ORDER_STATUS)
        assertEquals(123, es.criosrango.shared.PushNotificationContract.parseOrderId("123"))
    }

    @Test
    fun registrationPayload_usesRealFcmTokenAndNeverFid() {
        val json = pushRegistrationPayload("TEST_FCM_TOKEN", true, false).toJson()
        assertEquals("android", json.getString("platform"))
        assertEquals("fcm_token", json.getString("identifier_type"))
        assertEquals("TEST_FCM_TOKEN", json.getString("identifier"))
        assertEquals(true, json.getBoolean("new_products"))
        assertEquals(false, json.getBoolean("order_updates"))
        assertFalse(json.has("token"))
    }

    @Test
    fun unregisterPayload_usesFcmTokenIdentity() {
        val json = PushNotificationController.buildUnregisterPayload("TEST_FCM_TOKEN")
        assertEquals("android", json.getString("platform"))
        assertEquals("fcm_token", json.getString("identifier_type"))
        assertEquals("TEST_FCM_TOKEN", json.getString("identifier"))
        assertFalse(json.has("token"))
    }

    @Test
    fun sameToken_postsAgainButDoesNotDeactivatePrevious() {
        assertEquals(listOf(PushRegistrationAction.REGISTER_NEW),
            pushRegistrationActions("TEST_FCM_TOKEN", "TEST_FCM_TOKEN", registrationSucceeded = true))
    }

    @Test
    fun changedToken_registersNewThenDeactivatesOld() {
        assertEquals(listOf(PushRegistrationAction.REGISTER_NEW, PushRegistrationAction.UNREGISTER_OLD),
            pushRegistrationActions("OLD_FCM_TOKEN", "NEW_FCM_TOKEN", registrationSucceeded = true))
    }

    @Test
    fun failedNewRegistration_neverDeactivatesOld() {
        assertEquals(listOf(PushRegistrationAction.REGISTER_NEW),
            pushRegistrationActions("OLD_FCM_TOKEN", "NEW_FCM_TOKEN", registrationSucceeded = false))
    }

    @Test
    fun failedUnregister_retainsPendingIdentifierForRecovery() {
        assertEquals(setOf("OLD_FCM_TOKEN"),
            pendingUnregisterTokensAfterAttempt(emptySet(), "OLD_FCM_TOKEN", unregisterSucceeded = false))
        assertEquals(emptySet<String>(),
            pendingUnregisterTokensAfterAttempt(setOf("OLD_FCM_TOKEN"), "OLD_FCM_TOKEN", unregisterSucceeded = true))
    }
}
