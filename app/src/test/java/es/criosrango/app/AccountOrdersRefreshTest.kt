package es.criosrango.app

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class AccountOrdersRefreshTest {

    @Test
    fun refreshGate_allowsOneRequestAndRejectsConcurrentRequest() {
        val gate = SingleActionGate()

        assertTrue(gate.tryAcquire())
        assertFalse(gate.tryAcquire())

        gate.release()

        assertTrue(gate.tryAcquire())
        gate.release()
    }
}
