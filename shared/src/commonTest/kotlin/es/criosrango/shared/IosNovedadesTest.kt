package es.criosrango.shared

import es.criosrango.shared.model.ProductPrices
import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreProduct
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosNovedadesTest {
    private class Fake : StoreNovedadesDataSource {
        var tree = listOf(
            StoreCategory(id = 10, name = "Niña", slug = "nina"),
            StoreCategory(id = 11, parent = 10, name = "Vestidos", slug = "vestidos"),
            StoreCategory(id = 20, name = "Niño", slug = "nino"),
            StoreCategory(id = 30, name = "Bebé niña", slug = "bebe-nina")
        )
        var productCalls = 0
        val requestedPages = mutableListOf<Int>()
        val requestedSizes = mutableListOf<Int>()
        val requestedCategories = mutableListOf<List<Int>>()
        val requestedSorts = mutableListOf<StoreNovedadesSort>()
        var products = listOf(
            StoreProduct(id = 1, name = "Vestido", prices = ProductPrices(price = "100"), categories = listOf(StoreCategory(id = 11, name = "Vestidos"))),
            StoreProduct(id = 2, name = "Pantalón", prices = ProductPrices(price = "200"), categories = listOf(StoreCategory(id = 20, name = "Niño"))),
            StoreProduct(id = 3, name = "Body bebé", prices = ProductPrices(price = "50"), categories = listOf(StoreCategory(id = 30, name = "Bebé niña")))
        )
        override suspend fun categories(page: Int, perPage: Int) = if (page == 1) tree else emptyList()
        override suspend fun products(page: Int, perPage: Int, categoryIds: List<Int>, sort: StoreNovedadesSort): CatalogPage<StoreProduct> {
            productCalls++
            requestedPages += page
            requestedSizes += perPage
            requestedCategories += categoryIds
            requestedSorts += sort
            return CatalogPage(products, hasMore = true, totalItems = 999)
        }
    }

    @Test fun initialLoadRequestsOneGlobalNewestWindowOf24() = runTest {
        val source = Fake()
        val controller = StoreNovedadesController(this, source)
        controller.start()
        advanceUntilIdle()
        assertEquals(1, source.productCalls)
        assertEquals(listOf(1), source.requestedPages)
        assertEquals(listOf(24), source.requestedSizes)
        assertEquals(listOf(emptyList()), source.requestedCategories)
        assertEquals(listOf(StoreNovedadesSort.RECENT), source.requestedSorts)
        assertEquals(3, controller.state.value.paging.items.size)
        assertFalse(controller.state.value.paging.hasMore)
    }

    @Test fun audienceSelectionFiltersOnlyLoadedGlobalDatasetWithoutApiReload() = runTest {
        val source = Fake()
        val controller = StoreNovedadesController(this, source)
        controller.start(); advanceUntilIdle()
        controller.selectAudience(StoreNovedadesAudience.GIRL); advanceUntilIdle()
        assertEquals(1, source.productCalls)
        assertEquals(listOf(1), controller.state.value.paging.items.map { it.id })
        assertEquals(1, controller.state.value.paging.totalItems)
        controller.selectAudience(StoreNovedadesAudience.BOY)
        assertEquals(listOf(2), controller.state.value.paging.items.map { it.id })
        assertEquals(1, source.productCalls)
    }

    @Test fun babyAudienceUsesSameDatasetAndMatchesBabyCategory() = runTest {
        val source = Fake()
        val controller = StoreNovedadesController(this, source)
        controller.start(); advanceUntilIdle()
        controller.selectAudience(StoreNovedadesAudience.BABY)
        assertEquals(listOf(3), controller.state.value.paging.items.map { it.id })
        assertEquals(1, source.productCalls)
    }

    @Test fun allAudienceCounterEqualsGlobalDatasetSize() = runTest {
        val source = Fake()
        val controller = StoreNovedadesController(this, source)
        controller.start(); advanceUntilIdle()
        assertEquals(3, controller.state.value.paging.items.size)
        assertEquals(3, controller.state.value.paging.totalItems)
    }

    @Test fun sortChangesOrderOfSameLoadedDatasetWithoutApiReload() = runTest {
        val source = Fake()
        val controller = StoreNovedadesController(this, source)
        controller.start(); advanceUntilIdle()
        controller.selectSort(StoreNovedadesSort.PRICE_ASC)
        assertEquals(listOf(3, 1, 2), controller.state.value.paging.items.map { it.id })
        controller.selectSort(StoreNovedadesSort.PRICE_DESC)
        assertEquals(listOf(2, 1, 3), controller.state.value.paging.items.map { it.id })
        controller.selectSort(StoreNovedadesSort.NAME_ASC)
        assertEquals(listOf(3, 2, 1), controller.state.value.paging.items.map { it.id })
        assertEquals(1, source.productCalls)
        assertEquals(listOf(StoreNovedadesSort.RECENT), source.requestedSorts)
    }

    @Test fun audienceAndSortChangesNeverRequestOtherPagesOrCategoryQueries() = runTest {
        val source = Fake()
        val controller = StoreNovedadesController(this, source)
        controller.start(); advanceUntilIdle()
        controller.selectAudience(StoreNovedadesAudience.GIRL)
        controller.selectSort(StoreNovedadesSort.PRICE_ASC)
        controller.selectAudience(StoreNovedadesAudience.ALL)
        assertEquals(listOf(1), source.requestedPages)
        assertEquals(listOf(24), source.requestedSizes)
        assertEquals(listOf(emptyList()), source.requestedCategories)
        assertEquals(1, source.productCalls)
    }

    @Test fun pagingIsDisabledForGlobalWindow() = runTest {
        val source = Fake()
        val controller = StoreNovedadesController(this, source)
        controller.start(); advanceUntilIdle()
        controller.loadNextPage()
        assertEquals(1, source.productCalls)
        assertFalse(controller.state.value.paging.hasMore)
        assertEquals(1, controller.state.value.paging.currentPage)
    }

    @Test fun refreshIsExplicitlyTheOnlyActionThatReloadsGlobalWindow() = runTest {
        val source = Fake()
        val controller = StoreNovedadesController(this, source)
        controller.start(); advanceUntilIdle()
        controller.selectAudience(StoreNovedadesAudience.GIRL)
        controller.selectSort(StoreNovedadesSort.PRICE_ASC)
        assertEquals(1, source.productCalls)
        controller.refresh(); advanceUntilIdle()
        assertEquals(2, source.productCalls)
        assertEquals(listOf(1, 1), source.requestedPages)
        assertEquals(listOf(24, 24), source.requestedSizes)
    }

    @Test fun categoryResolutionIncludesDescendants() {
        assertEquals(
            listOf(10, 11),
            resolveAudienceCategoryIds(
                listOf(StoreCategory(id = 10, name = "Niña", slug = "nina"), StoreCategory(id = 11, parent = 10, name = "Vestidos")),
                StoreNovedadesAudience.GIRL
            )
        )
    }

    @Test fun queryIdentityReflectsUiSelectionButDoesNotCauseReload() = runTest {
        val source = Fake()
        val controller = StoreNovedadesController(this, source)
        controller.start(); advanceUntilIdle()
        val initial = controller.state.value.queryIdentity
        controller.selectAudience(StoreNovedadesAudience.GIRL)
        assertTrue(controller.state.value.queryIdentity != initial)
        assertEquals(1, source.productCalls)
    }

    @Test fun productsResponseLargerThanLimitIsCappedAt24() = runTest {
        val source = Fake()
        source.products = (1..30).map { StoreProduct(id = it) }
        val controller = StoreNovedadesController(this, source)
        controller.start(); advanceUntilIdle()
        assertEquals(24, controller.state.value.paging.items.size)
        assertFalse(controller.state.value.paging.hasMore)
    }

    @Test fun recentSortPreservesBackendNewestFirstOrder() = runTest {
        val source = Fake()
        source.products = listOf(StoreProduct(id = 3), StoreProduct(id = 2), StoreProduct(id = 1))
        val controller = StoreNovedadesController(this, source)
        controller.start(); advanceUntilIdle()
        controller.selectSort(StoreNovedadesSort.RECENT)
        assertEquals(listOf(3, 2, 1), controller.state.value.paging.items.map { it.id })
        assertEquals(1, source.productCalls)
    }
}
