package es.criosrango.shared

import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreProduct
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutletCatalogTest {
    @Test
    fun all_outlet_groups_have_defined_bubbles() {
        listOf(446, 475, 447, 476, 449, 478, 448, 477).forEach { id ->
            assertTrue(fixedOutletBubbles(id).isNotEmpty(), "Missing bubbles for outlet $id")
        }
    }

    @Test
    fun all_bubble_is_not_required_and_labels_are_shared() {
        assertEquals("Abrigos", outletBubbleDisplayLabel("Abrigos y cazadoras"))
        assertEquals("Vestidos y conjuntos", outletBubbleDisplayLabel("Vestidos, conjuntos y monos casual"))
        assertFalse(fixedOutletBubbles(999).isNotEmpty())
    }

    @Test
    fun origin_category_matching_walks_parent_chain() {
        val categories = mapOf(
            430 to StoreCategory(id = 430, parent = 400),
            400 to StoreCategory(id = 400, parent = 0)
        )
        val product = StoreProduct(id = 1, categories = listOf(StoreCategory(id = 430)))
        assertTrue(productBelongsToOutletOriginCategory(product, 400, categories))
        assertTrue(productBelongsToOutletBubble(product, outletBubble("Abrigos y cazadoras", 400), categories))
    }
}