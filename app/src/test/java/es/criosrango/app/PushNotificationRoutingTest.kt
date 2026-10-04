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
    fun fidRegistrationPayload_hasExplicitFidIdentityAndNeverToken() {
        val json = pushRegistrationPayload("TEST_FID", true, false).toJson()
        assertEquals("android", json.getString("platform"))
        assertEquals("fid", json.getString("identifier_type"))
        assertEquals("TEST_FID", json.getString("identifier"))
        assertEquals(true, json.getBoolean("new_products"))
        assertEquals(false, json.getBoolean("order_updates"))
        assertFalse(json.has("token"))
    }

    @Test
    fun fidUnregisterPayload_hasExplicitFidIdentityAndNeverToken() {
        val json = PushNotificationController.buildUnregisterPayload("TEST_FID")
        assertEquals("android", json.getString("platform"))
        assertEquals("fid", json.getString("identifier_type"))
        assertEquals("TEST_FID", json.getString("identifier"))
        assertFalse(json.has("token"))
    }

    @Test
    fun sameFid_postsAgainButDoesNotDeactivatePrevious() {
        assertEquals(
            listOf(PushRegistrationAction.REGISTER_NEW),
            pushRegistrationActions("TEST_FID", "TEST_FID", registrationSucceeded = true)
        )
    }

    @Test
    fun changedFid_registersNewThenDeactivatesOld() {
        assertEquals(
            listOf(
                PushRegistrationAction.REGISTER_NEW,
                PushRegistrationAction.UNREGISTER_OLD
            ),
            pushRegistrationActions("OLD_FID", "NEW_FID", registrationSucceeded = true)
        )
    }

    @Test
    fun failedNewRegistration_neverDeactivatesOld() {
        assertEquals(
            listOf(PushRegistrationAction.REGISTER_NEW),
            pushRegistrationActions("OLD_FID", "NEW_FID", registrationSucceeded = false)
        )
    }
}
