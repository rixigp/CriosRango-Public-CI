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
        productDetailToggleAttributeSelection(selected, "Color", "azul", chosen = false, existsGlobally = true)
        assertEquals("azul", selected["Color"])
    }

    @Test
    fun toggleDeselectsChosenOption() {
        val selected = mutableMapOf("Color" to "azul")
        productDetailToggleAttributeSelection(selected, "Color", "azul", chosen = true, existsGlobally = true)
        assertFalse(selected.containsKey("Color"))
    }

    @Test
    fun chosenOptionCanBeDeselectedWhenUnavailable() {
        val selected = mutableMapOf("Color" to "azul")
        productDetailToggleAttributeSelection(selected, "Color", "azul", chosen = true, existsGlobally = false)
        assertFalse(selected.containsKey("Color"))
    }

    @Test
    fun deselectingColorKeepsSizeSelection() {
        val selected = mutableMapOf("Color" to "azul", "Talla" to "M")
        productDetailToggleAttributeSelection(selected, "Color", "azul", chosen = true, existsGlobally = false)
        assertEquals("M", selected["Talla"])
        assertFalse(selected.containsKey("Color"))
    }

    @Test
    fun deselectingSizeKeepsColorSelection() {
        val selected = mutableMapOf("Color" to "azul", "Talla" to "M")
        productDetailToggleAttributeSelection(selected, "Talla", "M", chosen = true, existsGlobally = false)
        assertEquals("azul", selected["Color"])
        assertFalse(selected.containsKey("Talla"))
    }

    @Test
    fun deselectThenSelectAnotherOptionWorks() {
        val selected = mutableMapOf("Color" to "azul")
        productDetailToggleAttributeSelection(selected, "Color", "azul", chosen = true, existsGlobally = false)
        productDetailToggleAttributeSelection(selected, "Color", "rojo", chosen = false, existsGlobally = true)
        assertEquals("rojo", selected["Color"])
    }

    private fun variation(
        vararg attributes: Pair<String, String>,
        available: Boolean = true
    ) = ProductDetailVariationSnapshot(
        availableForPurchase = available,
        attributes = attributes.toList()
    )

    @Test
    fun selectedColor_canBeDeselected() {
        val selected = mutableMapOf("Color" to "granate")
        productDetailToggleAttributeSelection(
            selected = selected,
            attributeName = "Color",
            termSlug = "granate",
            chosen = true,
            existsGlobally = true
        )
        assertFalse(selected.containsKey("Color"))
    }

    @Test
    fun selectedSize_canBeDeselected() {
        val selected = mutableMapOf("Tallas" to "50")
        productDetailToggleAttributeSelection(
            selected = selected,
            attributeName = "Tallas",
            termSlug = "50",
            chosen = true,
            existsGlobally = true
        )
        assertFalse(selected.containsKey("Tallas"))
    }

    @Test
    fun incompatibleColor_replacesColorAndClearsConflictingSize() {
        val selected = mutableMapOf("Color" to "granate", "Tallas" to "50")
        val variations = listOf(
            variation("Color" to "granate", "Tallas" to "50"),
            variation("Color" to "granate", "Tallas" to "52"),
            variation("Color" to "marron", "Tallas" to "52")
        )

        productDetailSelectAttributeOption(
            selected = selected,
            attributeName = "Color",
            termSlug = "marron",
            termName = "Marrón",
            variations = variations
        )

        assertEquals("marron", selected["Color"])
        assertFalse(selected.containsKey("Tallas"))
    }

    @Test
    fun incompatibleSize_replacesSizeAndClearsConflictingColor() {
        val selected = mutableMapOf("Color" to "marron", "Tallas" to "50")
        val variations = listOf(
            variation("Color" to "marron", "Tallas" to "50"),
            variation("Color" to "granate", "Tallas" to "52")
        )

        productDetailSelectAttributeOption(
            selected = selected,
            attributeName = "Tallas",
            termSlug = "52",
            termName = "52",
            variations = variations
        )

        assertEquals("52", selected["Tallas"])
        assertFalse(selected.containsKey("Color"))
    }

    @Test
    fun globallyUnavailableOption_remainsDisabled() {
        val variations = listOf(
            variation("Color" to "granate", "Tallas" to "50"),
            variation("Color" to "marron", "Tallas" to "52"),
            variation("Color" to "azul", "Tallas" to "50", available = false)
        )
        val state = productDetailAttributeOptionState(
            variations = variations,
            selected = mapOf("Color" to "granate", "Tallas" to "50"),
            attributeName = "Color",
            termSlug = "azul",
            termName = "Azul"
        )

        assertFalse(state.existsGlobally)
        assertFalse(state.compatibleWithCurrentSelection)
        assertFalse(productDetailAttributeOptionClickable(chosen = false, existsGlobally = state.existsGlobally))
    }

    @Test
    fun globallyExistingButCurrentlyIncompatibleOption_remainsClickable() {
        val variations = listOf(
            variation("Color" to "granate", "Tallas" to "50"),
            variation("Color" to "marron", "Tallas" to "52")
        )
        val state = productDetailAttributeOptionState(
            variations = variations,
            selected = mapOf("Color" to "granate", "Tallas" to "50"),
            attributeName = "Color",
            termSlug = "marron",
            termName = "Marrón"
        )

        assertTrue(state.existsGlobally)
        assertFalse(state.compatibleWithCurrentSelection)
        assertTrue(productDetailAttributeOptionClickable(chosen = false, existsGlobally = state.existsGlobally))
    }

    @Test
    fun nonConflictingSelection_isPreserved() {
        val selected = mutableMapOf("Color" to "granate", "Tallas" to "50")
        val variations = listOf(
            variation("Color" to "granate", "Tallas" to "50"),
            variation("Color" to "marron", "Tallas" to "50"),
            variation("Color" to "marron", "Tallas" to "52")
        )

        productDetailSelectAttributeOption(
            selected = selected,
            attributeName = "Color",
            termSlug = "marron",
            termName = "Marrón",
            variations = variations
        )

        assertEquals(mapOf("Color" to "marron", "Tallas" to "50"), selected)
    }

    @Test
    fun switchingAtoBtoA_keepsSelectionFlowWorking() {
        val selected = mutableMapOf("Color" to "azul")
        val variations = listOf(
            variation("Color" to "azul", "Tallas" to "M"),
            variation("Color" to "rojo", "Tallas" to "M")
        )

        productDetailSelectAttributeOption(
            selected = selected,
            attributeName = "Color",
            termSlug = "rojo",
            termName = "Rojo",
            variations = variations
        )
        productDetailSelectAttributeOption(
            selected = selected,
            attributeName = "Color",
            termSlug = "azul",
            termName = "Azul",
            variations = variations
        )

        assertEquals("azul", selected["Color"])
    }

    @Test
    fun currentCombinationAdded_stillBlocksAdd() {
        assertFalse(
            productDetailAddButtonEnabled(
                currentCombinationAdded = true,
                canAdd = true,
                quantity = 1,
                minimumQuantity = 1,
                multipleOf = 1,
                availableMaximum = null
            )
        )
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
