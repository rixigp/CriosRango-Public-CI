package es.criosrango.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class CartBadgeCountTest {
    @Test
    fun countsUnitsInsteadOfCartLines() {
        assertEquals(0, cartUnitCount(emptyList()))
        assertEquals(1, cartUnitCount(listOf(1)))
        assertEquals(3, cartUnitCount(listOf(2, 1)))
        assertEquals(5, cartUnitCount(listOf(3, 2)))
    }

    @Test
    fun badgeTextCapsCountsAboveNinetyNine() {
        assertEquals("1", cartBadgeText(1))
        assertEquals("99", cartBadgeText(99))
        assertEquals("99+", cartBadgeText(100))
        assertEquals("99+", cartBadgeText(150))
    }
}
