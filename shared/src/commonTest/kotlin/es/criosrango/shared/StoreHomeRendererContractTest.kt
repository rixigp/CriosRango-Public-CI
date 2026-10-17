package es.criosrango.shared

import es.criosrango.shared.model.StoreGlobalBrand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoreHomeRendererContractTest {
    @Test
    fun visualBrandPagingPreservesSharedOrderAndOnlyGroupsEightAtATime() {
        val brands = (1..10).map {
            StoreGlobalBrand(id = it, name = "Brand-$it", slug = "brand-$it", count = 100 - it)
        }
        val pages = storeHomeBrandPages(StoreHomeState(brands = brands))
        assertEquals(listOf(8, 2), pages.map { it.size })
        assertEquals((1..10).toList(), pages.flatten().map { it.id })
    }

    @Test
    fun sectionContentVisibilityDoesNotTreatLoadingErrorOrEmptyAsContent() {
        assertTrue(storeHomeSectionHasRenderableContent(StoreHomeSectionStatus.CONTENT, true))
        assertFalse(storeHomeSectionHasRenderableContent(StoreHomeSectionStatus.LOADING, true))
        assertFalse(storeHomeSectionHasRenderableContent(StoreHomeSectionStatus.ERROR, true))
        assertFalse(storeHomeSectionHasRenderableContent(StoreHomeSectionStatus.EMPTY, true))
        assertFalse(storeHomeSectionHasRenderableContent(StoreHomeSectionStatus.CONTENT, false))
    }

    @Test
    fun outletSeasonDestinationsResolveFromSharedOutletSnapshot() {
        val state = StoreHomeState(outletCategories = HOME_OUTLET_SEASON_CATEGORIES)
        assertEquals(HOME_OUTLET_SEASON_CATEGORIES[0], storeHomeOutletSeasonCategory(state, "invierno"))
        assertEquals(HOME_OUTLET_SEASON_CATEGORIES[1], storeHomeOutletSeasonCategory(state, "verano"))
        assertEquals(446, iosHomeOutletPage(storeHomeOutletSeasonCategory(state, "invierno")).categoryId)
        assertEquals(475, iosHomeOutletPage(storeHomeOutletSeasonCategory(state, "verano")).categoryId)
    }
}
