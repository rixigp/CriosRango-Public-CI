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
    fun invalidOrderPush_isIgnoredInsteadOfOpeningAccountRoot() {
        assertTrue(shouldIgnoreInvalidAndroidOrderPush("order_status", null))
        assertTrue(shouldIgnoreInvalidAndroidOrderPush("order_status", 0))
        assertTrue(shouldIgnoreInvalidAndroidOrderPush("order_status", -4))
        assertFalse(shouldIgnoreInvalidAndroidOrderPush("order_status", 57628))
        assertFalse(shouldIgnoreInvalidAndroidOrderPush("new_products", null))
    }

    @Test
    fun pendingOrderIsNotConsumedBeforeAuthenticationAndExactSelection() {
        assertFalse(shouldConsumePendingOrder(57628, 57628, authenticated = false))
        assertFalse(shouldConsumePendingOrder(57628, 57627, authenticated = true))
        assertTrue(shouldConsumePendingOrder(57628, 57628, authenticated = true))
    }

    @Test
    fun hostPendingOrder_isNotClearedUntilMatchingExactOrderIsConsumed() {
        assertEquals(57628, pendingOrderAfterConsumption(57628, 57627))
        assertEquals(null, pendingOrderAfterConsumption(57628, 57628))
    }

    @Test
    fun coldStartAndBackgroundIntentParseTheExactPositiveOrderId() {
        assertEquals(57628, es.criosrango.shared.PushNotificationContract.parseOrderId("57628"))
        assertEquals(es.criosrango.shared.PushDestination.Order(57628),
            es.criosrango.shared.PushNotificationContract.destination("order_status", "57628"))
    }

    @Test
    fun pushFromAccountRootProfileOrExistingDetailUsesIncomingIdAsAuthority() {
        assertEquals(57628, incomingPushOrderSelection(null, 57628))
        assertEquals(57628, incomingPushOrderSelection(57627, 57628))
        assertEquals(57631, incomingPushOrderSelection(57628, 57631))
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
    fun secondPushOrderWinsWhileFirstIsStillPending() {
        val pendingAfterSecondPush = 57631
        assertEquals(57631, pendingOrderAfterConsumption(pendingAfterSecondPush, 57628))
        assertEquals(null, pendingOrderAfterConsumption(pendingAfterSecondPush, 57631))
    }

    @Test
    fun invalidIncomingPushOrderId_doesNotCrashOrReplaceCurrentSelection() {
        assertEquals(57624, incomingPushOrderSelection(57624, 0))
        assertEquals(null, incomingPushOrderSelection(null, null))
    }

    @Test
    fun registrationPayload_usesFidIdentity() {
        val json = pushRegistrationPayload("TEST_FIREBASE_INSTALLATION_ID", true, false).toJson()
        assertEquals("android", json.getString("platform"))
        assertEquals("fid", json.getString("identifier_type"))
        assertEquals("TEST_FIREBASE_INSTALLATION_ID", json.getString("identifier"))
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
    fun unregisterPayload_usesFidIdentity() {
        val json = PushNotificationController.buildUnregisterPayload("TEST_FIREBASE_INSTALLATION_ID")
        assertEquals("android", json.getString("platform"))
        assertEquals("fid", json.getString("identifier_type"))
        assertEquals("TEST_FIREBASE_INSTALLATION_ID", json.getString("identifier"))
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
