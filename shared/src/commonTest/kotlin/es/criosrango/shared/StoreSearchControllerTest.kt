package es.criosrango.shared

import es.criosrango.shared.model.StoreProduct
import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreGlobalBrand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class StoreSearchControllerTest {
    private class FakeSource : StoreSearchDataSource {
        val requests = mutableListOf<Pair<String, CompletableDeferred<List<StoreProduct>>>>()
        private val requestCount = MutableStateFlow(0)
        var fail = false

        suspend fun awaitRequestCount(count: Int) {
            requestCount.first { it >= count }
        }
        override suspend fun search(query: String): List<StoreProduct> {
            if (fail) error("network down")
            val deferred = CompletableDeferred<List<StoreProduct>>()
            requests += query to deferred
            requestCount.value = requests.size
            return deferred.await()
        }
        override suspend fun categories() = emptyList<es.criosrango.shared.model.StoreCategory>()
        override suspend fun brands() = emptyList<es.criosrango.shared.model.StoreGlobalBrand>()
    }
    @Test fun debounceIs350msAndClearCancelsPendingSearch() = runTest {
        val source = FakeSource()
        val controller = StoreSearchController(backgroundScope, source)
        controller.onQueryChanged("camiseta")
        advanceTimeBy(349)
        assertTrue(source.requests.isEmpty())
        controller.clear()
        advanceTimeBy(400)
        assertTrue(source.requests.isEmpty())
        assertEquals("", controller.state.value.query)
        assertFalse(controller.state.value.isLoading)
    }
    @Test fun debounceStartsRequestAt350ms() = runTest {
        val source = FakeSource()
        val controller = StoreSearchController(backgroundScope, source)
        controller.onQueryChanged("camiseta")
        advanceTimeBy(349)
        runCurrent()
        assertTrue(source.requests.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("camiseta"), source.requests.map { it.first })
        source.requests.single().second.complete(emptyList())
        runCurrent()
    }
    @Test fun normalizationAndApproximateMatching() {
        assertEquals("camiseta azul", normalizeStoreSearch("  CAMISETÁ---Azúl "))
        assertTrue(storeSearchTokenMatches("camiseta", "camiseta azul"))
        assertTrue(storeSearchTokenMatches("camiseta", "camisete"))
    }
    @Test fun rankingAndDeduplicationById() {
        val exact = StoreProduct(id=1, name="Camiseta")
        val prefix = StoreProduct(id=2, name="Camiseta azul")
        val duplicate = StoreProduct(id=1, name="Camiseta otra")
        val ranked = rankStoreSearchResults(listOf(prefix, duplicate, exact), "camiseta")
        assertEquals(listOf(1, 2), ranked.map { it.id })
        assertEquals(1000, storeSearchRanking(exact, "camiseta"))
    }
    @Test fun oneAndMultipleWordsAreMatched() {
        assertEquals(listOf(1), rankStoreSearchResults(listOf(StoreProduct(id=1,name="Camiseta azul"),StoreProduct(id=2,name="Pantalón")), "camiseta").map { it.id })
        assertEquals(listOf(1), rankStoreSearchResults(listOf(StoreProduct(id=1,name="Camiseta azul"),StoreProduct(id=2,name="Camiseta roja")), "camiseta azul").map { it.id })
    }
    @Test fun categoryAndBrandSuggestionsUseSharedApproximateMatching() {
        val categories = listOf(StoreCategory(id=1, name="Camisetas"), StoreCategory(id=2, name="Pantalones"))
        val brands = listOf(StoreGlobalBrand(id=3, name="Nike", count=1), StoreGlobalBrand(id=4, name="Adidas", count=1))
        assertEquals(listOf(1), storeSearchCategorySuggestions(categories, "camiseta").map { it.id })
        assertEquals(listOf(3), storeSearchBrandSuggestions(brands, "nike").map { it.id })
    }
    @Test fun productSuggestionsUseSharedRankingAndDeduplicateById() {
        val products = listOf(StoreProduct(id=2, name="Camiseta azul"), StoreProduct(id=1, name="Camiseta"), StoreProduct(id=1, name="Camiseta duplicada"), StoreProduct(id=3, name="Pantalón"))
        assertEquals(listOf(1, 2), storeSearchProductSuggestions(products, "camiseta").map { it.id })
        assertTrue(storeSearchProductSuggestions(products, "").isEmpty())
    }
    @Test fun staleResponseCannotReplaceLatestQuery() = runTest {
        val source = FakeSource()
        val controller = StoreSearchController(backgroundScope, source, 0)
        controller.onQueryChanged("A")
        source.awaitRequestCount(1)
        val a = source.requests.first().second
        controller.onQueryChanged("B")
        source.awaitRequestCount(2)
        val b = source.requests.last().second
        b.complete(listOf(StoreProduct(id=2,name="B")))
        controller.state.first { it.products.map(StoreProduct::id) == listOf(2) }
        a.complete(listOf(StoreProduct(id=1,name="A")))
        runCurrent()
        assertEquals("B", controller.state.value.query)
        assertEquals(listOf(2), controller.state.value.products.map { it.id })
    }
    @Test fun errorCanRetry() = runTest {
        val source = FakeSource()
        source.fail = true
        val controller = StoreSearchController(backgroundScope, source, 0)
        controller.onQueryChanged("camiseta")
        controller.state.first { it.errorMessage == "network down" }
        assertEquals("network down", controller.state.value.errorMessage)
        source.fail = false
        controller.retry()
        source.awaitRequestCount(1)
        source.requests.last().second.complete(listOf(StoreProduct(id=7,name="camiseta")))
        controller.state.first { it.products.map(StoreProduct::id) == listOf(7) && it.errorMessage == null }
        assertNull(controller.state.value.errorMessage)
        assertEquals(listOf(7), controller.state.value.products.map { it.id })
    }
    @Test fun emptyStateIsReachedAfterEmptyResponse() = runTest {
        val source = FakeSource()
        val controller = StoreSearchController(backgroundScope, source, 0)
        controller.onQueryChanged("none")
        source.awaitRequestCount(1)
        source.requests.last().second.complete(emptyList())
        controller.state.first { it.hasSearched && it.products.isEmpty() }
        assertTrue(controller.state.value.hasSearched)
        assertTrue(controller.state.value.products.isEmpty())
    }
}
