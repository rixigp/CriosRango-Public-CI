package es.criosrango.app

import es.criosrango.shared.account.isAccountSessionExpiredStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FinalReleaseHardeningTest {
    @Test
    fun authentication403InvalidatesSessionLike401() {
        assertTrue(isAccountSessionExpiredStatus(401))
        assertTrue(isAccountSessionExpiredStatus(403))
        assertFalse(isAccountSessionExpiredStatus(400))
        assertFalse(isAccountSessionExpiredStatus(404))
        assertFalse(isAccountSessionExpiredStatus(500))
    }

    @Test
    fun cartAddGateRejectsSecondConcurrentAdd() {
        val gate = CartAddGate()
        assertTrue(gate.tryAcquire())
        assertFalse(gate.tryAcquire())
        gate.release()
        assertTrue(gate.tryAcquire())
    }

    @Test
    fun singleActionGateAllowsOnlyOneConcurrentAction() {
        val gate = SingleActionGate()
        assertTrue(gate.tryAcquire())
        assertFalse(gate.tryAcquire())
        gate.release()
        assertTrue(gate.tryAcquire())
    }
}
