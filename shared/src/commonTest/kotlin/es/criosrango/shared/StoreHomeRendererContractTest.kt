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
        val winter = iosHomeOutletPage(IosOutletSeason.WINTER)
        val summer = iosHomeOutletPage(IosOutletSeason.SUMMER)
        val all = iosHomeOutletPage(null)
        assertEquals(445, winter.categoryId)
        assertEquals(IosOutletSeason.WINTER, winter.season)
        assertEquals(445, summer.categoryId)
        assertEquals(IosOutletSeason.SUMMER, summer.season)
        assertEquals(445, all.categoryId)
        assertEquals(null, all.season)

        val children = listOf(
            es.criosrango.shared.model.StoreCategory(446, 445, "Hombre invierno", "hombre-invierno", 0),
            es.criosrango.shared.model.StoreCategory(447, 445, "Mujer invierno", "mujer-invierno", 0),
            es.criosrango.shared.model.StoreCategory(448, 445, "Niño invierno", "nino-invierno", 0),
            es.criosrango.shared.model.StoreCategory(449, 445, "Niña invierno", "nina-invierno", 0),
            es.criosrango.shared.model.StoreCategory(475, 445, "Hombre verano", "hombre-verano", 0),
            es.criosrango.shared.model.StoreCategory(476, 445, "Mujer verano", "mujer-verano", 0),
            es.criosrango.shared.model.StoreCategory(477, 445, "Niño verano", "nino-verano", 0),
            es.criosrango.shared.model.StoreCategory(478, 445, "Niña verano", "nina-verano", 0)
        )
        assertEquals(listOf(446, 447, 448, 449), iosOutletVisibleChildren(children, 445, IosOutletSeason.WINTER).map { it.id })
        assertEquals(listOf(475, 476, 477, 478), iosOutletVisibleChildren(children, 445, IosOutletSeason.SUMMER).map { it.id })
        assertEquals(children.map { it.id }, iosOutletVisibleChildren(children, 445, null).map { it.id })
    }
}
