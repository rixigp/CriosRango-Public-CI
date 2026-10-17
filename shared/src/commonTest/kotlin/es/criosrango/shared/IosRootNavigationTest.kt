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
        val winter = es.criosrango.shared.model.StoreCategory(id = 901, parent = 445, name = "Invierno", slug = "invierno", count = 0)
        val summer = es.criosrango.shared.model.StoreCategory(id = 902, parent = 445, name = "Verano", slug = "verano", count = 0)
        assertEquals(445, iosHomeOutletPage(winter).categoryId)
        assertEquals(445, iosHomeOutletPage(summer).categoryId)
        assertEquals(null, iosHomeOutletPage(null).categoryId)
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
