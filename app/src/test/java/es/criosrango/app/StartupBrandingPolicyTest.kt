package es.criosrango.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartupBrandingPolicyTest {
    @Test
    fun warmStartNeverShowsBranding() {
        assertFalse(shouldShowStartupBranding(false, false, false))
        assertFalse(shouldShowStartupBranding(false, false, true))
    }

    @Test
    fun coldStartKeepsBrandingUntilMinimumDurationAndReady() {
        assertTrue(shouldShowStartupBranding(true, false, true))
        assertFalse(shouldShowStartupBranding(true, true, false))
        assertFalse(shouldShowStartupBranding(true, true, true))
    }
}
