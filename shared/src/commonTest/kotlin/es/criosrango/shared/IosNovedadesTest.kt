package es.criosrango.shared

import es.criosrango.shared.model.ProductPrices
import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreProduct
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosNovedadesTest {
    private class Fake : StoreNovedadesDataSource {
        val tree = listOf(
            StoreCategory(id = 10, name = "Bautizo", slug = "bautizo"),
            StoreCategory(id = 11, parent = 10, name = "Ropa bautizo"),
            StoreCategory(id = 20, name = "Bebé niña", slug = "bebe-nina"),
            StoreCategory(id = 21, parent = 20, name = "Vestidos bebé"),
            StoreCategory(id = 30, name = "Bebé niño", slug = "bebe-nino"),
            StoreCategory(id = 31, parent = 30, name = "Pijamas bebé niño"),
            StoreCategory(id = 40, name = "Niña", slug = "nina"),
            StoreCategory(id = 41, parent = 40, name = "Vestidos niña"),
            StoreCategory(id = 50, name = "Niño", slug = "nino"),
            StoreCategory(id = 51, parent = 50, name = "Pantalones niño"),
            StoreCategory(id = 60, name = "Comunión niña", slug = "comunion-nina"),
            StoreCategory(id = 61, parent = 60, name = "Vestidos comunión"),
            StoreCategory(id = 70, name = "Comunión niño", slug = "comunion-nino"),
            StoreCategory(id = 71, parent = 70, name = "Trajes comunión"),
            StoreCategory(id = 80, name = "Mujer", slug = "mujer"),
            StoreCategory(id = 81, parent = 80, name = "Vestidos mujer"),
            StoreCategory(id = 90, name = "Hombre", slug = "hombre"),
            StoreCategory(id = 91, parent = 90, name = "Camisas hombre"),
            StoreCategory(id = 100, name = "Recién nacido", slug = "recien-nacido"),
            StoreCategory(id = 101, parent = 100, name = "Pijamas recién nacido"),
            StoreCategory(id = 110, name = "Calzado", slug = "calzado"),
            StoreCategory(id = 111, parent = 110, name = "Zapatos")
        )
        var productCalls = 0
        var categoryCalls = 0
        val pages = mutableListOf<Int>()
        val sizes = mutableListOf<Int>()
        val categoryQueries = mutableListOf<List<Int>>()
        val sorts = mutableListOf<StoreNovedadesSort>()
        var firstGate: CompletableDeferred<CatalogPage<StoreProduct>>? = null
        var products = (1..11).map { id ->
            val categoryId = when (id) {
                1 -> 11
                2 -> 21
                3 -> 31
                4 -> 41
                5 -> 51
                6 -> 61
                7 -> 71
                8 -> 81
                9 -> 91
                10 -> 101
                else -> 111
            }
            val name = when (id) {
                1 -> "Bautizo"
                2 -> "Bebé niña"
                3 -> "Bebé niño"
                4 -> "Niña"
                5 -> "Niño"
                6 -> "Comunión niña"
                7 -> "Comunión niño"
                8 -> "Mujer"
                9 -> "Hombre"
                10 -> "Recién nacido"
                else -> "Calzado"
            }
            StoreProduct(
                id = id,
                name = name,
                prices = ProductPrices(price = (id * 10).toString()),
                categories = listOf(StoreCategory(id = categoryId))
            )
        }

        override suspend fun categories(page: Int, perPage: Int): List<StoreCategory> {
            categoryCalls++
            return if (page == 1) tree else emptyList()
        }

        override suspend fun products(
            page: Int,
            perPage: Int,
            categoryIds: List<Int>,
            sort: StoreNovedadesSort
        ): CatalogPage<StoreProduct> {
            productCalls++
            pages += page
            sizes += perPage
            categoryQueries += categoryIds
            sorts += sort
            if (productCalls == 1) firstGate?.let { return it.await() }
            return CatalogPage(products, hasMore = true, totalItems = 999)
        }
    }

    @Test
    fun initialQueryLoadsOnlyOneGlobalRecentPageOf24() = runTest {
        val fake = Fake()
        val controller = StoreNovedadesController(this, fake)
        controller.start()
        advanceUntilIdle()
        assertEquals(1, fake.productCalls)
        assertEquals(listOf(1), fake.pages)
        assertEquals(listOf(24), fake.sizes)
        assertEquals(listOf(emptyList()), fake.categoryQueries)
        assertEquals(listOf(StoreNovedadesSort.RECENT), fake.sorts)
        assertEquals(11, controller.state.value.paging.items.size)
        assertFalse(controller.state.value.paging.hasMore)
        assertEquals(controller.state.value.paging.items.size, controller.state.value.paging.totalItems)
    }

    @Test
    fun responseIsCappedAt24AndPaginationIsDisabled() = runTest {
        val fake = Fake()
        fake.products = (1..30).map { StoreProduct(id = it, prices = ProductPrices(price = it.toString())) }
        val controller = StoreNovedadesController(this, fake)
        controller.start()
        advanceUntilIdle()
        controller.loadNextPage()
        assertEquals(24, controller.state.value.paging.items.size)
        assertFalse(controller.state.value.paging.hasMore)
        assertEquals(1, fake.productCalls)
        assertEquals(listOf(24), fake.sizes)
    }

    @Test
    fun selectorOrderIsExactAndCalzadoIsNotAnAudience() {
        assertEquals(
            listOf("Todas", "Bautizo", "Críos", "Comunión", "Mujer", "Hombre", "Recién nacido"),
            StoreNovedadesAudience.values().map { it.label }
        )
    }

    @Test
    fun everyAudienceUsesOnlyTheLoadedSnapshotAndIncludesDescendants() = runTest {
        val fake = Fake()
        val controller = StoreNovedadesController(this, fake)
        controller.start()
        advanceUntilIdle()
        controller.selectAudience(StoreNovedadesAudience.BAUTIZO)
        assertEquals(listOf(1), controller.state.value.paging.items.map { it.id })
        controller.selectAudience(StoreNovedadesAudience.CRIOS)
        assertEquals(listOf(2, 3, 4, 5), controller.state.value.paging.items.map { it.id }.sorted())
        controller.selectAudience(StoreNovedadesAudience.COMUNION)
        assertEquals(listOf(6, 7), controller.state.value.paging.items.map { it.id }.sorted())
        controller.selectAudience(StoreNovedadesAudience.MUJER)
        assertEquals(listOf(8), controller.state.value.paging.items.map { it.id })
        controller.selectAudience(StoreNovedadesAudience.HOMBRE)
        assertEquals(listOf(9), controller.state.value.paging.items.map { it.id })
        controller.selectAudience(StoreNovedadesAudience.RECIEN_NACIDO)
        assertEquals(listOf(10), controller.state.value.paging.items.map { it.id })
        controller.selectAudience(StoreNovedadesAudience.ALL)
        assertEquals((1..11).toList(), controller.state.value.paging.items.map { it.id })
        assertEquals(1, fake.productCalls)
    }

    @Test
    fun recienNacidoIsExcludedFromCrios() = runTest {
        val fake = Fake()
        val controller = StoreNovedadesController(this, fake)
        controller.start()
        advanceUntilIdle()
        controller.selectAudience(StoreNovedadesAudience.CRIOS)
        assertFalse(controller.state.value.paging.items.any { it.id == 10 })
        assertFalse(controller.state.value.paging.items.any { it.id == 11 })
    }

    @Test
    fun repeatedCategoryChangesAreLocalAndStable() = runTest {
        val fake = Fake()
        val controller = StoreNovedadesController(this, fake)
        controller.start()
        advanceUntilIdle()
        val expected = listOf(
            StoreNovedadesAudience.ALL,
            StoreNovedadesAudience.CRIOS,
            StoreNovedadesAudience.COMUNION,
            StoreNovedadesAudience.MUJER,
            StoreNovedadesAudience.HOMBRE,
            StoreNovedadesAudience.RECIEN_NACIDO,
            StoreNovedadesAudience.BAUTIZO,
            StoreNovedadesAudience.ALL
        )
        repeat(8) { expected.forEach(controller::selectAudience) }
        assertEquals(1, fake.productCalls)
        assertEquals(1, fake.categoryCalls)
        assertEquals((1..11).toList(), controller.state.value.paging.items.map { it.id })
        assertFalse(controller.state.value.paging.isInitialLoading)
    }

    @Test
    fun sortingOnlyReordersTheCurrentSubsetAndNeverReloadsProducts() = runTest {
        val fake = Fake()
        val controller = StoreNovedadesController(this, fake)
        controller.start()
        advanceUntilIdle()
        controller.selectAudience(StoreNovedadesAudience.CRIOS)
        val subset = controller.state.value.paging.items.map { it.id }.toSet()
        controller.selectSort(StoreNovedadesSort.PRICE_ASC)
        assertEquals(listOf(2, 3, 4, 5), controller.state.value.paging.items.map { it.id })
        controller.selectSort(StoreNovedadesSort.PRICE_DESC)
        assertEquals(listOf(5, 4, 3, 2), controller.state.value.paging.items.map { it.id })
        controller.selectSort(StoreNovedadesSort.NAME_ASC)
        assertEquals(subset, controller.state.value.paging.items.map { it.id }.toSet())
        assertEquals(1, fake.productCalls)
        assertEquals(StoreNovedadesSort.RECENT, fake.sorts.single())
    }

    @Test
    fun counterMatchesVisibleSubsetAndFinalEmptyCategoryIsNotLoading() = runTest {
        val fake = Fake()
        fake.products = fake.products.filter { it.id != 10 }
        val controller = StoreNovedadesController(this, fake)
        controller.start()
        advanceUntilIdle()
        controller.selectAudience(StoreNovedadesAudience.CRIOS)
        assertEquals(4, controller.state.value.paging.totalItems)
        assertEquals(controller.state.value.paging.items.size, controller.state.value.paging.totalItems)
        controller.selectAudience(StoreNovedadesAudience.RECIEN_NACIDO)
        assertTrue(controller.state.value.paging.items.isEmpty())
        assertEquals(0, controller.state.value.paging.totalItems)
        assertFalse(shouldShowNovedadesInitialLoading(controller.state.value))
        assertTrue(shouldShowNovedadesCountAndSort(controller.state.value))
    }

    @Test
    fun initialLoadUsesSkeletonWithoutCountOrSpinner() = runTest {
        val fake = Fake()
        val gate = CompletableDeferred<CatalogPage<StoreProduct>>()
        fake.firstGate = gate
        val controller = StoreNovedadesController(this, fake)
        controller.start()
        runCurrent()
        assertTrue(shouldShowNovedadesInitialLoading(controller.state.value))
        assertFalse(shouldShowNovedadesCountAndSort(controller.state.value))
        assertTrue(controller.state.value.paging.items.isEmpty())
        gate.complete(CatalogPage(fake.products, hasMore = true, totalItems = 999))
        advanceUntilIdle()
        assertFalse(shouldShowNovedadesInitialLoading(controller.state.value))
        assertTrue(shouldShowNovedadesCountAndSort(controller.state.value))
    }

    @Test
    fun staleInitialResponseCannotOverwriteNewerRefresh() = runTest {
        val fake = Fake()
        val gate = CompletableDeferred<CatalogPage<StoreProduct>>()
        fake.firstGate = gate
        val controller = StoreNovedadesController(this, fake)
        controller.start()
        runCurrent()
        controller.refresh()
        advanceUntilIdle()
        gate.complete(CatalogPage(listOf(StoreProduct(id = 999)), hasMore = false, totalItems = 1))
        advanceUntilIdle()
        assertFalse(controller.state.value.paging.items.any { it.id == 999 })
        assertEquals((1..11).toList(), controller.state.value.paging.items.map { it.id })
    }

    @Test
    fun iosNovedadesBackReturnsHomeOnlyWhenCatalogOriginWasHome() {
        assertTrue(shouldReturnHomeAfterCatalogBack(historyIsEmpty = true, pageIsRoot = true, catalogOriginHome = true))
        assertFalse(shouldReturnHomeAfterCatalogBack(historyIsEmpty = false, pageIsRoot = true, catalogOriginHome = true))
        assertFalse(shouldReturnHomeAfterCatalogBack(historyIsEmpty = true, pageIsRoot = false, catalogOriginHome = true))
        assertFalse(shouldReturnHomeAfterCatalogBack(historyIsEmpty = true, pageIsRoot = true, catalogOriginHome = false))
    }
}
