package es.criosrango.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProductDetailAddButtonPolicyTest {

    @Test
    fun addedCombination_cannotBeAddedAgain() {
        assertFalse(
            productDetailAddButtonEnabled(
                currentCombinationAdded = true,
                canAdd = true,
                quantity = 2,
                minimumQuantity = 1,
                multipleOf = 1,
                availableMaximum = null
            )
        )
    }

    @Test
    fun anotherValidCombination_remainsAddable() {
        assertTrue(
            productDetailAddButtonEnabled(
                currentCombinationAdded = false,
                canAdd = true,
                quantity = 2,
                minimumQuantity = 1,
                multipleOf = 1,
                availableMaximum = 5
            )
        )
    }

    @Test
    fun selectedQuantity_isStillAcceptedWhenValid() {
        assertTrue(
            productDetailAddButtonEnabled(
                currentCombinationAdded = false,
                canAdd = true,
                quantity = 3,
                minimumQuantity = 1,
                multipleOf = 2,
                availableMaximum = 5
            )
        )
        assertFalse(
            productDetailAddButtonEnabled(
                currentCombinationAdded = false,
                canAdd = true,
                quantity = 2,
                minimumQuantity = 1,
                multipleOf = 2,
                availableMaximum = 2
            )
        )
    }

    @Test
    fun selectionKey_distinguishesVariationsAndAttributes() {
        val combinationA = productCombinationSelectionKey(10, 101, mapOf("Talla" to "4", "Color" to "azul"))
        val combinationASameOrder = productCombinationSelectionKey(10, 101, mapOf("Color" to "azul", "Talla" to "4"))
        val combinationB = productCombinationSelectionKey(10, 102, mapOf("Talla" to "4", "Color" to "azul"))

        assertEquals(combinationA, combinationASameOrder)
        assertNotEquals(combinationA, combinationB)
    }
}
