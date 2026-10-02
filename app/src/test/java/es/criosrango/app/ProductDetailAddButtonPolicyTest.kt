package es.criosrango.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ProductDetailAddButtonPolicyTest {

    @Test
    fun toggleSelectsAvailableOption() {
        val selected = mutableMapOf<String, String>()
        productDetailToggleAttributeSelection(selected, "Color", "azul", chosen = false, available = true)
        assertEquals("azul", selected["Color"])
    }

    @Test
    fun toggleDeselectsChosenOption() {
        val selected = mutableMapOf("Color" to "azul")
        productDetailToggleAttributeSelection(selected, "Color", "azul", chosen = true, available = true)
        assertFalse(selected.containsKey("Color"))
    }

    @Test
    fun chosenOptionCanBeDeselectedWhenUnavailable() {
        val selected = mutableMapOf("Color" to "azul")
        productDetailToggleAttributeSelection(selected, "Color", "azul", chosen = true, available = false)
        assertFalse(selected.containsKey("Color"))
    }

    @Test
    fun deselectingColorKeepsSizeSelection() {
        val selected = mutableMapOf("Color" to "azul", "Talla" to "M")
        productDetailToggleAttributeSelection(selected, "Color", "azul", chosen = true, available = false)
        assertEquals("M", selected["Talla"])
        assertFalse(selected.containsKey("Color"))
    }

    @Test
    fun deselectingSizeKeepsColorSelection() {
        val selected = mutableMapOf("Color" to "azul", "Talla" to "M")
        productDetailToggleAttributeSelection(selected, "Talla", "M", chosen = true, available = false)
        assertEquals("azul", selected["Color"])
        assertFalse(selected.containsKey("Talla"))
    }

    @Test
    fun deselectThenSelectAnotherOptionWorks() {
        val selected = mutableMapOf("Color" to "azul")
        productDetailToggleAttributeSelection(selected, "Color", "azul", chosen = true, available = false)
        productDetailToggleAttributeSelection(selected, "Color", "rojo", chosen = false, available = true)
        assertEquals("rojo", selected["Color"])
    }

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
