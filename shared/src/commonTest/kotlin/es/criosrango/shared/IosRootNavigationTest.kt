package es.criosrango.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class IosRootNavigationTest {
    @Test
    fun rootStartsOnHome() {
        assertEquals(IosRootSection.HOME, IosRootSection.entries.first())
    }

    @Test
    fun novedadesAndBrandsOpenedFromHomeKeepHomeSelected() {
        assertEquals(StoreMainTab.HOME, iosSelectedRootTab(IosRootSection.CATEGORIES, catalogOriginHome = true))
    }

    @Test
    fun homeOutletSeasonUsesOutletRootRatherThanSeasonGenderCategory() {
        val winter = iosHomeOutletPage(IosOutletSeason.WINTER)
        val summer = iosHomeOutletPage(IosOutletSeason.SUMMER)
        val all = iosHomeOutletPage(null)
        assertEquals(445, winter.categoryId)
        assertEquals(IosOutletSeason.WINTER, winter.season)
        assertEquals(445, summer.categoryId)
        assertEquals(IosOutletSeason.SUMMER, summer.season)
        assertEquals(445, all.categoryId)
        assertEquals(null, all.season)
        assertEquals(StoreMainTab.HOME, iosSelectedRootTab(IosRootSection.OUTLET, catalogOriginHome = true))
    }

    @Test
    fun mainSectionsAreHomeCategoriesOutletCartAccount() {
        assertEquals(
            listOf(IosRootSection.HOME, IosRootSection.CATEGORIES, IosRootSection.OUTLET, IosRootSection.CART, IosRootSection.ACCOUNT),
            IosRootSection.entries.toList()
        )
    }
}
