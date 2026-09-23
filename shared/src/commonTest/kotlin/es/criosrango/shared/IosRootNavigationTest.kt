package es.criosrango.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class IosRootNavigationTest {
    @Test
    fun rootStartsOnHome() {
        assertEquals(IosRootSection.HOME, IosRootSection.entries.first())
    }

    @Test
    fun mainSectionsAreHomeCategoriesAccount() {
        assertEquals(
            listOf(IosRootSection.HOME, IosRootSection.CATEGORIES, IosRootSection.ACCOUNT),
            IosRootSection.entries.toList()
        )
    }
}
