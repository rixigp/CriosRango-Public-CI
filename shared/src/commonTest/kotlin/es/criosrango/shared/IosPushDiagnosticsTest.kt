package es.criosrango.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class IosPushDiagnosticsTest {
    @Test
    fun permissionAndRegistrationCallbacksUpdateVisibleState() {
        IosPushDiagnostics.recordPermission("AUTORIZADO")
        IosPushDiagnostics.recordRegistrationRequested()
        IosPushDiagnostics.recordToken("0123456789abcdef0123456789abcdef")
        val snapshot = IosPushDiagnostics.snapshot
        assertEquals("AUTORIZADO", snapshot.permission)
        assertTrue(snapshot.registrationRequested)
        assertTrue(snapshot.didRegisterReceived)
        assertTrue(snapshot.tokenAvailable)
        assertNotEquals("0123456789abcdef0123456789abcdef", snapshot.installationHash)
        assertEquals(8, snapshot.installationHash.length)
    }

    @Test
    fun postAttemptRecordsAuthAndPreferenceValuesWithoutToken() {
        IosPushDiagnostics.recordPostAttempt(authenticated = true, newProducts = false, orderUpdates = true)
        val snapshot = IosPushDiagnostics.snapshot
        assertTrue(snapshot.postAttempted)
        assertEquals("SÍ", snapshot.authenticated)
        assertEquals("NO", snapshot.newProductsSent)
        assertEquals("SÍ", snapshot.orderUpdatesSent)
    }

    @Test
    fun failedApnsRegistrationRedactsBearerAndLongHexTokens() {
        IosPushDiagnostics.recordRegistrationFailure(
            "NSError",
            "Bearer abcdef0123456789abcdef0123456789abcdef and 0123456789abcdef0123456789abcdef"
        )
        val snapshot = IosPushDiagnostics.snapshot
        assertEquals("NSError", snapshot.errorClass)
        assertFalse(snapshot.errorMessage.contains("abcdef0123456789abcdef0123456789abcdef"))
        assertFalse(snapshot.errorMessage.contains("Bearer abcdef"))
        assertTrue(snapshot.errorMessage.contains("redactado"))
    }

    @Test
    fun backendRejectionLeavesSyncPendingAndRecordsHttpStatus() {
        IosPushDiagnostics.recordPostResult("HTTP 401", "NO", true, "HttpError", "Registro no autorizado")
        val snapshot = IosPushDiagnostics.snapshot
        assertEquals("HTTP 401", snapshot.registrationResult)
        assertEquals("NO", snapshot.accepted)
        assertTrue(snapshot.pendingSync)
        assertEquals("HttpError", snapshot.errorClass)
    }

    @Test
    fun selectedTabPreservesOriginAndCheckoutSelectsCart() {
        assertEquals(StoreMainTab.OUTLET, iosRootShellSelectedTab(IosRootSection.OUTLET, false, false))
        assertEquals(StoreMainTab.CATEGORIES, iosRootShellSelectedTab(IosRootSection.CATEGORIES, false, false))
        assertEquals(StoreMainTab.HOME, iosRootShellSelectedTab(IosRootSection.CATEGORIES, true, false))
        assertEquals(StoreMainTab.CART, iosRootShellSelectedTab(IosRootSection.CATEGORIES, false, true))
    }
}
