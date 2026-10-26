package es.criosrango.shared

import es.criosrango.shared.model.StoreCartLine
import es.criosrango.shared.model.StoreQuantityLimits
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StoreCartQuantityRulesTest {
    @Test
    fun plusMustFitTheWholeWooStepWithinMaximum() {
        val line = StoreCartLine(
            quantity = 4,
            quantityLimits = StoreQuantityLimits(minimum = 1, maximum = 5, multipleOf = 2)
        )
        assertFalse(line.canIncreaseQuantity())
        assertEquals(2, line.quantityAfterDecrement())
    }

    @Test
    fun minusCrossingMinimumMeansRemoveInsteadOfZeroQuantityUpdate() {
        val line = StoreCartLine(
            quantity = 2,
            quantityLimits = StoreQuantityLimits(minimum = 2, maximum = 10, multipleOf = 2)
        )
        assertNull(line.quantityAfterDecrement())
    }

    @Test
    fun missingOrInvalidStepDefaultsToOne() {
        assertEquals(1, StoreCartLine(quantity = 3).quantityStep())
        assertEquals(1, StoreCartLine(quantity = 3, quantityLimits = StoreQuantityLimits(multipleOf = 0)).quantityStep())
        assertTrue(StoreCartLine(quantity = 4).canIncreaseQuantity())
    }
}
