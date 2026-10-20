package es.criosrango.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CheckoutFreeShippingTest {
    @Test fun zeroSubtotalNeedsFiftyEuros() {
        val progress = checkoutFreeShippingProgress("0", 2)
        assertEquals(5000L, progress.thresholdMinorUnits)
        assertEquals(5000L, progress.remainingMinorUnits)
        assertFalse(progress.reached)
    }

    @Test fun oneCentNeedsFortyNineNinetyNine() {
        val progress = checkoutFreeShippingProgress("1", 2)
        assertEquals(4999L, progress.remainingMinorUnits)
        assertFalse(progress.reached)
    }

    @Test fun fortyNineNinetyNineNeedsOneCent() {
        val progress = checkoutFreeShippingProgress("4999", 2)
        assertEquals(1L, progress.remainingMinorUnits)
        assertFalse(progress.reached)
    }

    @Test fun fiftyEurosReachesFreeShipping() {
        val progress = checkoutFreeShippingProgress("5000", 2)
        assertEquals(0L, progress.remainingMinorUnits)
        assertTrue(progress.reached)
    }

    @Test fun aboveThresholdRemainsReached() {
        val progress = checkoutFreeShippingProgress("7250", 2)
        assertEquals(0L, progress.remainingMinorUnits)
        assertTrue(progress.reached)
    }

    @Test fun thresholdRespectsCurrencyMinorUnit() {
        assertEquals(50L, checkoutFreeShippingProgress("0", 0).thresholdMinorUnits)
        assertEquals(50000L, checkoutFreeShippingProgress("0", 3).thresholdMinorUnits)
    }
}
