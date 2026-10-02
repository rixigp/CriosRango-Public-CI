package es.criosrango.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeRootCategoriesTest {
    @Test
    fun homeRootCategoriesHaveExactVerifiedIdsAndOrder() {
        assertEquals(listOf(560, 292, 294, 420, 504, 509, 70, 71, 68, 310, 67), HOME_ROOT_CATEGORIES.map { it.id })
    }

    @Test
    fun homeRootCategoriesHaveExactNames() {
        assertEquals(
            listOf("Bautizo", "Bebe niña", "Bebé niño", "Calzado", "Comunión niña", "Comunión niño", "Hombre", "Mujer", "Niña", "Niño", "Recién nacido"),
            HOME_ROOT_CATEGORIES.map { it.name }
        )
    }

    @Test
    fun outletIsNotPresentAndListContainsExactlyElevenCategories() {
        assertEquals(11, HOME_ROOT_CATEGORIES.size)
        assertFalse(HOME_ROOT_CATEGORIES.any { it.id == 445 })
    }

    @Test
    fun homeRootCategoriesAreLocalRootRepresentations() {
        assertTrue(HOME_ROOT_CATEGORIES.all { it.parent == 0 && it.count == 0 && it.slug.isEmpty() })
    }
}
