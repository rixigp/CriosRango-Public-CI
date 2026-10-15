package es.criosrango.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RootFacetKeyTest {
    @Test fun size_key_excludes_size_and_canonicalizes_other_dimensions() {
        assertEquals(
            RootFacetKey.create(StoreRootFilterScope.OUTLET, RootFacetDimension.SIZES, listOf(1), listOf(8, 7), listOf(9)),
            RootFacetKey.create(StoreRootFilterScope.OUTLET, RootFacetDimension.SIZES, listOf(99), listOf(7, 8, 8), listOf(9, 9))
        )
    }
    @Test fun color_and_brand_keys_exclude_their_own_dimension() {
        assertEquals(
            RootFacetKey.create(StoreRootFilterScope.CATALOG, RootFacetDimension.COLORS, listOf(2), listOf(3), listOf(4)),
            RootFacetKey.create(StoreRootFilterScope.CATALOG, RootFacetDimension.COLORS, listOf(2, 2), listOf(88), listOf(4))
        )
        assertEquals(
            RootFacetKey.create(StoreRootFilterScope.OUTLET, RootFacetDimension.BRANDS, listOf(2), listOf(3), listOf(4)),
            RootFacetKey.create(StoreRootFilterScope.OUTLET, RootFacetDimension.BRANDS, listOf(2), listOf(3, 77), listOf(90))
        )
    }
    @Test fun latest_draft_request_wins_even_when_old_response_finishes_later() {
        val guard = RootFacetRequestGeneration()
        val oldKey = RootFacetKey.create(StoreRootFilterScope.OUTLET, RootFacetDimension.SIZES, emptyList(), listOf(1), emptyList())
        val newKey = RootFacetKey.create(StoreRootFilterScope.OUTLET, RootFacetDimension.SIZES, emptyList(), listOf(2), emptyList())
        val oldGeneration = guard.begin(RootFacetDimension.SIZES)
        val newGeneration = guard.begin(RootFacetDimension.SIZES)
        assertTrue(guard.accepts(RootFacetDimension.SIZES, newGeneration, newKey, newKey))
        assertFalse(guard.accepts(RootFacetDimension.SIZES, oldGeneration, oldKey, newKey))
    }
}
