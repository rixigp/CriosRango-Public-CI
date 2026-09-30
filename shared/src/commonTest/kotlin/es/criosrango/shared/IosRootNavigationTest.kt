package es.criosrango.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class IosRootNavigationTest {
    @Test
    fun rootStartsOnHome() {
        assertEquals(IosRootSection.HOME, IosRootSection.entries.first())
    }

    @Test
    fun mainSectionsAreHomeCategoriesOutletCartAccount() {
        assertEquals(
            listOf(IosRootSection.HOME, IosRootSection.CATEGORIES, IosRootSection.OUTLET, IosRootSection.CART, IosRootSection.ACCOUNT),
            IosRootSection.entries.toList()
        )
    }
}
