package es.criosrango.shared

import es.criosrango.shared.model.ProductAttribute
import es.criosrango.shared.model.AttributeTerm
import es.criosrango.shared.model.OutletAvailability
import es.criosrango.shared.model.OutletAvailabilityBucket
import es.criosrango.shared.model.OutletAvailabilityCount
import es.criosrango.shared.model.ProductTag
import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreProduct
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle

class StoreOutletControllerTest {
    /**
     * Controller jobs outlive an individual test body. Keep them on the test scheduler,
     * but under backgroundScope so runTest does not wait for long-lived controller jobs.
     * backgroundScope cancellation deterministically cancels this child scope at teardown.
     */
    private fun TestScope.controllerScope(): CoroutineScope =
        CoroutineScope(
            SupervisorJob(backgroundScope.coroutineContext[Job]) +
                StandardTestDispatcher(testScheduler)
        )

    private fun categories() = mapOf(
        10 to StoreCategory(id = 10, parent = 0),
        20 to StoreCategory(id = 20, parent = 10),
        30 to StoreCategory(id = 30, parent = 20),
        40 to StoreCategory(id = 40, parent = 0),
        50 to StoreCategory(id = 50, parent = 51),
        51 to StoreCategory(id = 51, parent = 50)
    )

    private fun availability(outletId: Int = 446, categoryId: Int = 430, count: Int = 1, schemaVersion: Int = 1) =
        OutletAvailability(schemaVersion = schemaVersion, outlets = listOf(
            OutletAvailabilityBucket(outletCategoryId = outletId,
                counts = listOf(OutletAvailabilityCount(categoryId = categoryId, count = count)))
        ))

    @Test fun matching_supports_direct_ancestor_original_ids_and_any_bubble_id() {
        val cats = categories()
        val direct = StoreProduct(id = 1, categories = listOf(StoreCategory(id = 20)))
        val ancestor = StoreProduct(id = 2, categories = listOf(StoreCategory(id = 30)))
        val original = StoreProduct(id = 3, extensions = es.criosrango.shared.model.StoreProductExtensions(
            criosrangoOutlet = es.criosrango.shared.model.OutletOriginExtension(listOf(40))
        ))
        assertTrue(productBelongsToOutletOriginCategory(direct, 20, cats))
        assertTrue(productBelongsToOutletOriginCategory(ancestor, 10, cats))
        assertTrue(productBelongsToOutletOriginCategory(original, 40, cats))
        assertTrue(productBelongsToOutletBubble(ancestor, outletBubble("test", 999, 10), cats))
        assertFalse(productBelongsToOutletOriginCategory(direct, 999, cats))
        assertFalse(productBelongsToOutletOriginCategory(
            StoreProduct(id = 4, categories = listOf(StoreCategory(id = 777))),
            999,
            cats
        ))
        assertFalse(productBelongsToOutletOriginCategory(
            StoreProduct(id = 5, categories = listOf(StoreCategory(id = 50))),
            999,
            cats
        ))
    }

    @Test fun query_identity_is_category_only() {
        assertEquals(StoreOutletQueryKey(446), StoreOutletQueryKey(446))
        assertEquals("446", StoreOutletQueryKey(446).paginatorKey())
        assertFalse(StoreOutletQueryKey(446) == StoreOutletQueryKey(447))
    }

    @Test fun controller_retains_paginators_when_switching_categories() = runTest {
        val calls = mutableListOf<Pair<Int, Int>>()
        val controller = StoreOutletController(controllerScope(), StoreOutletDataSource { query, page, perPage ->
            calls += (query.categoryId to page)
            CatalogPage(listOf(StoreProduct(id = query.categoryId * 100 + page)), hasMore = true)
        })
        controller.selectCategory(446)
        advanceUntilIdle()
        controller.selectCategory(447)
        advanceUntilIdle()
        controller.selectCategory(446)
        advanceUntilIdle()
        assertEquals(listOf(446 to 1, 447 to 1), calls)
        assertEquals(1, controller.state.value.paging.currentPage)
        assertEquals(2, controller.retainedPaginatorCount())
    }

    @Test fun bubble_filter_and_sort_are_local_and_recent_preserves_input_order() = runTest {
        val first = StoreProduct(id = 2, name = "Z", categories = listOf(StoreCategory(id = 430)), attributes = listOf(ProductAttribute(name = "Color", terms = listOf(AttributeTerm(name = "Azúl")))), tags = listOf(ProductTag(name = "Marca")))
        val second = StoreProduct(id = 1, name = "A", categories = listOf(StoreCategory(id = 430)), attributes = listOf(ProductAttribute(name = "Color", terms = listOf(AttributeTerm(name = "azul")))), tags = listOf(ProductTag(name = "Marca")))
        val controller = StoreOutletController(controllerScope(), StoreOutletDataSource { _, _, _ -> CatalogPage(listOf(first, second), false) })
        controller.updateCategories(listOf(StoreCategory(id = 430, parent = 0)))
        controller.updateAvailability(availability())
        controller.selectCategory(446)
        advanceUntilIdle()
        assertEquals(listOf(2, 1), controller.state.value.sortedProducts.map { it.id })
        controller.selectSort(StoreOutletSort.NAME_ASC)
        assertEquals(listOf(1, 2), controller.state.value.sortedProducts.map { it.id })
        val queryBeforeLocalChanges = controller.state.value.queryKey
        controller.selectBubble("abrigos y cazadoras")
        assertEquals(queryBeforeLocalChanges, controller.state.value.queryKey)
        controller.selectColors(setOf("azul"))
        assertEquals(listOf(1, 2), controller.state.value.sortedProducts.map { it.id })
        controller.selectSort(StoreOutletSort.PRICE_ASC)
        assertEquals(queryBeforeLocalChanges, controller.state.value.queryKey)
    }

    @Test fun empty_filter_does_not_activate_bubble_probe() = runTest {
        val controller = StoreOutletController(controllerScope(), StoreOutletDataSource { _, _, _ -> CatalogPage(emptyList(), true) })
        controller.selectCategory(446)
        advanceUntilIdle()
        controller.selectSizes(setOf("impossible"))
        assertFalse(controller.state.value.isBubbleProbeRequired)
    }

    @Test fun near_end_prefetch_loads_one_next_page_and_has_more_false_blocks_it() = runTest {
        val calls = mutableListOf<Int>()
        val controller = StoreOutletController(controllerScope(), StoreOutletDataSource { query, page, _ ->
            calls += page
            val products = if (page == 1) (1..12).map { StoreProduct(id = it, categories = listOf(StoreCategory(id = 430))) }
                else (13..24).map { StoreProduct(id = it, categories = listOf(StoreCategory(id = 430))) }
            CatalogPage(products, hasMore = page == 1)
        })
        controller.selectCategory(446)
        advanceUntilIdle()
        controller.onVisibleRangeChanged(6)
        advanceUntilIdle()
        assertEquals(listOf(1, 2), calls)
        assertFalse(controller.state.value.paging.hasMore)
        controller.onVisibleRangeChanged(20)
        advanceUntilIdle()
        assertEquals(listOf(1, 2), calls)
    }

    @Test fun duplicate_near_end_notifications_do_not_duplicate_append() = runTest {
        val calls = mutableListOf<Int>()
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val controller = StoreOutletController(controllerScope(), StoreOutletDataSource { _, page, _ ->
            calls += page
            if (page == 2) gate.await()
            CatalogPage((page * 100..page * 100 + 11).map { StoreProduct(id = it) }, true)
        })
        controller.selectCategory(446)
        advanceUntilIdle()
        controller.onVisibleRangeChanged(6)
        controller.onVisibleRangeChanged(7)
        testScheduler.runCurrent()
        assertEquals(listOf(1, 2), calls)
        gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test fun empty_selected_bubble_probes_next_category_page() = runTest {
        val calls = mutableListOf<Int>()
        val controller = StoreOutletController(controllerScope(), StoreOutletDataSource { _, page, _ ->
            calls += page
            CatalogPage(emptyList(), hasMore = page == 1)
        })
        controller.updateAvailability(availability())
        controller.selectCategory(446)
        advanceUntilIdle()
        controller.selectBubble("abrigos y cazadoras")
        advanceUntilIdle()
        assertEquals(listOf(1, 2), calls)
        assertFalse(controller.state.value.paging.hasMore)
    }

    @Test fun invalid_price_fallbacks_and_recent_order_match_android() = runTest {
        val invalid = StoreProduct(id = 1, name = "B", prices = es.criosrango.shared.model.ProductPrices(price = "invalid"))
        val expensive = StoreProduct(id = 2, name = "A", prices = es.criosrango.shared.model.ProductPrices(price = "200"))
        val cheap = StoreProduct(id = 3, name = "C", prices = es.criosrango.shared.model.ProductPrices(price = "100"))
        val controller = StoreOutletController(controllerScope(), StoreOutletDataSource { _, _, _ ->
            CatalogPage(listOf(invalid, expensive, cheap), false)
        })
        controller.selectCategory(446)
        advanceUntilIdle()
        assertEquals(listOf(1, 2, 3), controller.state.value.sortedProducts.map { it.id })
        controller.selectSort(StoreOutletSort.PRICE_ASC)
        assertEquals(listOf(3, 2, 1), controller.state.value.sortedProducts.map { it.id })
        controller.selectSort(StoreOutletSort.PRICE_DESC)
        assertEquals(listOf(2, 3, 1), controller.state.value.sortedProducts.map { it.id })
        controller.selectSort(StoreOutletSort.NAME_ASC)
        assertEquals(listOf(2, 1, 3), controller.state.value.sortedProducts.map { it.id })
    }


    @Test fun filters_use_or_within_dimension_and_and_across_dimensions() = runTest {
        fun product(id: Int, color: String, size: String) = StoreProduct(
            id = id,
            attributes = listOf(
                ProductAttribute(name = "Color", terms = listOf(AttributeTerm(name = color))),
                ProductAttribute(name = "Tallas", terms = listOf(AttributeTerm(name = size)))
            )
        )
        val controller = StoreOutletController(controllerScope(), StoreOutletDataSource { _, _, _ ->
            CatalogPage(listOf(product(1, "Azul", "S"), product(2, "Rojo", "M"), product(3, "Azul", "M")), false)
        })
        controller.selectCategory(446)
        advanceUntilIdle()
        controller.selectColors(setOf("azul", "rojo"))
        assertEquals(listOf(1, 2, 3), controller.state.value.filteredProducts.map { it.id })
        controller.selectSizes(setOf("M"))
        assertEquals(listOf(2, 3), controller.state.value.filteredProducts.map { it.id })
    }


    @Test fun obsolete_category_response_never_replaces_active_query_state() = runTest {
        val oldGate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val controller = StoreOutletController(controllerScope(), StoreOutletDataSource { query, _, _ ->
            if (query.categoryId == 446) oldGate.await()
            CatalogPage(listOf(StoreProduct(id = query.categoryId)), false)
        })
        controller.selectCategory(446)
        testScheduler.runCurrent()
        controller.selectCategory(447)
        advanceUntilIdle()
        assertEquals(447, controller.state.value.activeCategoryId)
        assertEquals(listOf(447), controller.state.value.paging.items.map { it.id })
        oldGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(447, controller.state.value.activeCategoryId)
        assertEquals(listOf(447), controller.state.value.paging.items.map { it.id })
    }


    @Test fun missing_availability_fails_closed_for_named_bubbles() = runTest {
        val controller = StoreOutletController(controllerScope(), StoreOutletDataSource { _, _, _ -> CatalogPage(emptyList(), false) })
        controller.selectCategory(446)
        advanceUntilIdle()
        assertTrue(controller.state.value.visibleBubbles.isEmpty())
    }

    @Test fun unsupported_availability_schema_fails_closed_for_named_bubbles() = runTest {
        val controller = StoreOutletController(controllerScope(), StoreOutletDataSource { _, _, _ -> CatalogPage(emptyList(), false) })
        controller.updateAvailability(availability(schemaVersion = 2))
        controller.selectCategory(446)
        advanceUntilIdle()
        assertTrue(controller.state.value.visibleBubbles.isEmpty())
    }

    @Test fun positive_availability_shows_bubble_and_zero_count_hides_it() = runTest {
        val controller = StoreOutletController(controllerScope(), StoreOutletDataSource { _, _, _ -> CatalogPage(emptyList(), false) })
        controller.updateAvailability(availability(count = 1))
        controller.selectCategory(446)
        advanceUntilIdle()
        assertTrue(controller.state.value.visibleBubbles.any { it.key == "abrigos y cazadoras" })
        controller.updateAvailability(availability(count = 0))
        assertFalse(controller.state.value.visibleBubbles.any { it.key == "abrigos y cazadoras" })
    }

    @Test fun unavailable_bubble_selection_is_ignored_without_probe_or_query_change() = runTest {
        val calls = mutableListOf<Int>()
        val controller = StoreOutletController(controllerScope(), StoreOutletDataSource { _, page, _ ->
            calls += page
            CatalogPage(emptyList(), hasMore = true)
        })
        controller.selectCategory(446)
        advanceUntilIdle()
        val queryBefore = controller.state.value.queryKey
        controller.selectBubble("abrigos y cazadoras")
        advanceUntilIdle()
        assertEquals(null, controller.state.value.selectedBubbleKey)
        assertEquals(queryBefore, controller.state.value.queryKey)
        assertEquals(listOf(1), calls)
        assertFalse(controller.state.value.isProbingSelectedBubble)
    }

    @Test fun bubble_selection_does_not_change_common_screen_title_but_category_selection_does() = runTest {
        val controller = StoreOutletController(controllerScope(), StoreOutletDataSource { _, _, _ -> CatalogPage(emptyList(), false) })
        controller.updateCategories(listOf(
            StoreCategory(id = 446, parent = 445, name = "Niño invierno"),
            StoreCategory(id = 447, parent = 445, name = "Niño verano")
        ))
        controller.updateAvailability(OutletAvailability(schemaVersion = 1, outlets = listOf(
            OutletAvailabilityBucket(446, listOf(OutletAvailabilityCount(430, 1))),
            OutletAvailabilityBucket(447, listOf(OutletAvailabilityCount(431, 1)))
        )))
        controller.selectCategory(446)
        advanceUntilIdle()
        assertEquals("Niño invierno", controller.state.value.screenTitle)
        controller.selectBubble("abrigos y cazadoras")
        assertEquals("Niño invierno", controller.state.value.screenTitle)
        controller.selectCategory(447)
        advanceUntilIdle()
        assertEquals("Niño verano", controller.state.value.screenTitle)
    }

}
