package es.criosrango.app

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushNotificationRoutingTest {
    @Test
    fun newProductsPayload_usesSharedDestinationAndNoOrder() {
        assertEquals(es.criosrango.shared.PushDestination.NewProducts,
            es.criosrango.shared.PushNotificationContract.destination(es.criosrango.shared.PushNotificationType.NEW_PRODUCTS))
        assertTrue(es.criosrango.shared.PushNotificationContract.parseOrderId(null) == null)
    }

    @Test
    fun orderStatusPayload_usesExactSharedOrderDestination() {
        assertEquals(es.criosrango.shared.PushDestination.Order(123),
            es.criosrango.shared.PushNotificationContract.destination(es.criosrango.shared.PushNotificationType.ORDER_STATUS, "123"))
        assertEquals(es.criosrango.shared.PushDestination.Account,
            es.criosrango.shared.PushNotificationContract.destination(es.criosrango.shared.PushNotificationType.ORDER_STATUS, "bad"))
    }

    @Test
    fun incomingPushOrderWhileAccountIsAlreadyOpen_selectsNewExactOrder() {
        assertEquals(57625, incomingPushOrderSelection(57624, 57625))
    }

    @Test
    fun incomingPushOrder_twice_selectsSecondOrderInsteadOfStaleFirst() {
        val first = incomingPushOrderSelection(null, 57624)
        assertEquals(57625, incomingPushOrderSelection(first, 57625))
    }

    @Test
    fun invalidIncomingPushOrderId_doesNotCrashOrReplaceCurrentSelection() {
        assertEquals(57624, incomingPushOrderSelection(57624, 0))
        assertEquals(null, incomingPushOrderSelection(null, null))
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
    fun tokenErrorMessageShowsSafeFirebaseReasonWithoutSecretsOrStacktrace() {
        assertEquals(
            "Default FirebaseApp is not initialized in this process.",
            safePushTokenErrorMessage("Default FirebaseApp is not initialized in this process.")
        )
        assertEquals(
            "Missing API key; Bearer [REDACTED]",
            safePushTokenErrorMessage("Missing API key; Bearer abc.def.ghi")
        )
        assertEquals("(sin mensaje)", safePushTokenErrorMessage("  "))
    }

    @Test
    fun authenticatedRegistrationUsesPersistedBearerTokenOnly() {
        assertEquals("Bearer ACCOUNT_TOKEN", pushAuthorizationHeader("ACCOUNT_TOKEN"))
        assertEquals(null, pushAuthorizationHeader(null))
        assertEquals(null, pushAuthorizationHeader(""))
    }

    @Test
    fun orderUpdatesTrueIsPreservedInRegistrationPayload() {
        val json = pushRegistrationPayload("TEST_FCM_TOKEN", false, true).toJson()
        assertEquals(false, json.getBoolean("new_products"))
        assertTrue(json.getBoolean("order_updates"))
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
