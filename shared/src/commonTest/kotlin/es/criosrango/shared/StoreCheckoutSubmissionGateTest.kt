package es.criosrango.shared

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StoreCheckoutSubmissionGateTest {
    @Test
    fun multipleAcquires_allowOnlyOne() = runTest {
        val gate = StoreCheckoutSubmissionGate()
        val results = (1..20).map { async { gate.tryAcquire() } }.awaitAll()
        assertEquals(1, results.count { it })
        gate.release()
        assertEquals(true, gate.tryAcquire())
    }
}
