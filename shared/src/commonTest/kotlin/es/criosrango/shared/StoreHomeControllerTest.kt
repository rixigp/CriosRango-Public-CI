package es.criosrango.shared

import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreGlobalBrand
import es.criosrango.shared.model.StoreProduct
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StoreHomeControllerTest {
    private class FakeSource : StoreHomeDataSource {
        var productsFailure = false
        var brandsFailure = false
        var outletFailure = false
        var requestedLimit = 0
        var requestedOrderBy = ""
        var requestedOrder = ""
        var productRows: List<StoreProduct> = (1..10).map { StoreProduct(id = it, name = "Product-$it") }
        var brandRows: List<StoreGlobalBrand> = listOf(
            StoreGlobalBrand(id = 1, name = "Zulu", slug = "zulu", count = 50),
            StoreGlobalBrand(id = 2, name = "Alpha", slug = "alpha", count = 50),
            StoreGlobalBrand(id = 3, name = "Empty", slug = "empty", count = 0),
            StoreGlobalBrand(id = 4, name = "Missing slug", slug = "", count = 10),
            StoreGlobalBrand(id = 5, name = "   ", slug = "blank-name", count = 10)
        ) + (6..25).map { StoreGlobalBrand(id = it, name = "Brand-$it", slug = "brand-$it", count = it) }
        var outletRows: List<StoreCategory> = listOf(StoreCategory(id = 445, parent = 0, name = "Outlet", slug = "outlet"))

        override suspend fun products(perPage: Int, orderBy: String, order: String): List<StoreProduct> {
            requestedLimit = perPage
            requestedOrderBy = orderBy
            requestedOrder = order
            if (productsFailure) error("products failed")
            return productRows
        }

        override suspend fun brands(): List<StoreGlobalBrand> {
            if (brandsFailure) error("brands failed")
            return brandRows
        }

        override suspend fun outletCategories(): List<StoreCategory> {
            if (outletFailure) error("outlet failed")
            return outletRows
        }
    }

    @Test
    fun homeCategoriesAreTheExactCuratedSharedList() {
        assertEquals(HOME_CATEGORY_COUNT, HOME_ROOT_CATEGORIES.size)
        assertEquals(
            listOf(560, 292, 294, 420, 504, 509, 70, 71, 68, 310, 67),
            HOME_ROOT_CATEGORIES.map { it.category.id }
        )
        assertEquals(
            listOf("Bautizo", "Bebe niña", "Bebé niño", "Calzado", "Comunión niña", "Comunión niño", "Hombre", "Mujer", "Niña", "Niño", "Recién nacido"),
            HOME_ROOT_CATEGORIES.map { it.category.name }
        )
        assertTrue(HOME_ROOT_CATEGORIES.all { it.imageKey.isNotBlank() })
    }

    @Test
    fun novedadesPreviewUsesDateDescendingQueryAndKeepsOnlyEightInSourceOrder() = runTest {
        val source = FakeSource()
        val controller = StoreHomeController(this, source)

        controller.start()
        advanceUntilIdle()

        assertEquals(HOME_NOVEDADES_LIMIT, source.requestedLimit)
        assertEquals("date", source.requestedOrderBy)
        assertEquals("desc", source.requestedOrder)
        assertEquals((1..8).toList(), controller.state.value.novedadesPreview.map { it.id })
        assertEquals(StoreHomeSectionStatus.CONTENT, controller.state.value.novedadesStatus)
    }

    @Test
    fun brandsFilterInvalidAndEmptyRowsSortByCountThenNameAndCapAtSixteen() = runTest {
        val source = FakeSource()
        val controller = StoreHomeController(this, source)

        controller.start()
        advanceUntilIdle()

        val brands = controller.state.value.brands
        assertEquals(HOME_BRANDS_MAX, brands.size)
        assertTrue(brands.all { it.id > 0 && it.count > 0 && it.name.isNotBlank() && it.slug.isNotBlank() })
        assertEquals(listOf(2, 1, 25), brands.take(3).map { it.id })
        assertEquals("alpha", brands.first().slug)
        assertEquals("zulu", brands[1].slug)
        assertEquals(StoreHomeSectionStatus.CONTENT, controller.state.value.brandsStatus)
    }

    @Test
    fun brandFailureDoesNotRemoveProductsCategoriesOrOutlet() = runTest {
        val source = FakeSource().apply { brandsFailure = true }
        val controller = StoreHomeController(this, source)

        controller.start()
        advanceUntilIdle()

        val state = controller.state.value
        assertEquals(StoreHomeSectionStatus.ERROR, state.brandsStatus)
        assertNotNull(state.brandsError)
        assertEquals(StoreHomeSectionStatus.CONTENT, state.novedadesStatus)
        assertEquals(8, state.novedadesPreview.size)
        assertEquals(StoreHomeSectionStatus.CONTENT, state.categoriesStatus)
        assertEquals(HOME_CATEGORY_COUNT, state.categories.size)
        assertEquals(StoreHomeSectionStatus.CONTENT, state.outletStatus)
        assertEquals(445, state.outletCategories.single().id)
    }

    @Test
    fun productFailureDoesNotRemoveCategoriesBrandsOrOutlet() = runTest {
        val source = FakeSource().apply { productsFailure = true }
        val controller = StoreHomeController(this, source)

        controller.start()
        advanceUntilIdle()

        val state = controller.state.value
        assertEquals(StoreHomeSectionStatus.ERROR, state.novedadesStatus)
        assertNotNull(state.novedadesError)
        assertEquals(StoreHomeSectionStatus.CONTENT, state.categoriesStatus)
        assertEquals(HOME_CATEGORY_COUNT, state.categories.size)
        assertEquals(StoreHomeSectionStatus.CONTENT, state.brandsStatus)
        assertTrue(state.brands.isNotEmpty())
        assertEquals(StoreHomeSectionStatus.CONTENT, state.outletStatus)
    }

    @Test
    fun failedRefreshKeepsPreviouslyLoadedBrandContentAndOtherHealthySections() = runTest {
        val source = FakeSource()
        val controller = StoreHomeController(this, source)
        controller.start()
        advanceUntilIdle()
        val priorBrands = controller.state.value.brands

        source.brandsFailure = true
        controller.refresh()
        advanceUntilIdle()

        val state = controller.state.value
        assertEquals(priorBrands, state.brands)
        assertEquals(StoreHomeSectionStatus.CONTENT, state.brandsStatus)
        assertNotNull(state.brandsError)
        assertEquals(StoreHomeSectionStatus.CONTENT, state.novedadesStatus)
        assertEquals(StoreHomeSectionStatus.CONTENT, state.categoriesStatus)
        assertEquals(StoreHomeSectionStatus.CONTENT, state.outletStatus)
        assertTrue(!state.refreshing)
    }
}
