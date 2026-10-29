package es.criosrango.shared

import es.criosrango.shared.model.OutletAvailability
import es.criosrango.shared.model.OutletAvailabilityBucket
import es.criosrango.shared.model.OutletAvailabilityCount
import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreProduct
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class StoreOutletControllerTest {
    private fun TestScope.controllerScope(): CoroutineScope =
        CoroutineScope(SupervisorJob(backgroundScope.coroutineContext[Job]) + StandardTestDispatcher(testScheduler))

    private fun options() = StoreFilterOptions(
        sizes = listOf(StoreFilterOption(342, "3XL", "3xl", "pa_tallas"), StoreFilterOption(341, "XXL", "xxl", "pa_tallas")),
        colors = listOf(StoreFilterOption(353, "Azul", "azul", "pa_color")),
        brands = listOf(StoreFilterOption(777, "Marca", "marca"))
    )

    private fun availability(
        outletId: Int = 446,
        categoryId: Int = 430,
        count: Int = 1,
        schemaVersion: Int = 1
    ) = OutletAvailability(
        schemaVersion = schemaVersion,
        outlets = listOf(
            OutletAvailabilityBucket(
                outletCategoryId = outletId,
                counts = listOf(OutletAvailabilityCount(categoryId = categoryId, count = count))
            )
        )
    )

    private class RecordingSource(
        private val filterOptions: StoreFilterOptions,
        private val pageResponse: suspend (StoreOutletQuery, Int, Int) -> CatalogPage<StoreProduct> = { query, page, _ ->
            CatalogPage(listOf(StoreProduct(id = query.categoryId * 100 + page)), hasMore = false, totalItems = 1)
        },
        private val facetResponse: suspend (StoreOutletQuery, StoreOutletFacetDimension) -> Map<Int, Int> = { _, _ -> emptyMap() }
    ) : StoreOutletDataSource {
        val pageCalls = mutableListOf<Triple<StoreOutletQuery, Int, Int>>()
        val facetCalls = mutableListOf<Pair<StoreOutletQuery, StoreOutletFacetDimension>>()
        var optionsCalls = 0

        override suspend fun loadFilterOptions(): StoreFilterOptions {
            optionsCalls++
            return filterOptions
        }

        override suspend fun loadPage(query: StoreOutletQuery, page: Int, perPage: Int): CatalogPage<StoreProduct> {
            pageCalls += Triple(query, page, perPage)
            return pageResponse(query, page, perPage)
        }

        override suspend fun loadFacet(query: StoreOutletQuery, dimension: StoreOutletFacetDimension): Map<Int, Int> {
            facetCalls += query to dimension
            return facetResponse(query, dimension)
        }
    }

    @Test fun query_identity_contains_category_bubble_filters_and_sort_but_not_page() {
        val a = StoreOutletQueryKey.from(
            StoreOutletQuery(477, listOf(287, 78, 78), listOf(342), listOf(353), listOf(777), StoreOutletSort.PRICE_ASC)
        )
        val b = StoreOutletQueryKey.from(
            StoreOutletQuery(477, listOf(78, 287), listOf(342), listOf(353), listOf(777), StoreOutletSort.PRICE_ASC)
        )
        assertEquals(a, b)
        assertEquals(listOf(78, 287), a.originCategoryIds)
        assertTrue(a.paginatorKey().contains("origin=78,287"))
        assertTrue(a.paginatorKey().contains("sizes=342"))
        assertTrue(a.paginatorKey().contains("sort=PRICE_ASC"))
    }

    @Test fun category_page_uses_backend_query_and_canonical_page_size() = runTest {
        val source = RecordingSource(options())
        val controller = StoreOutletController(controllerScope(), source)
        controller.selectCategory(446)
        advanceUntilIdle()
        assertEquals(1, source.pageCalls.size)
        val (query, page, perPage) = source.pageCalls.single()
        assertEquals(446, query.categoryId)
        assertEquals(emptyList(), query.originCategoryIds)
        assertEquals(1, page)
        assertEquals(CatalogPaginator.PAGE_SIZE, perPage)
        assertEquals(listOf(44601), controller.state.value.sortedProducts.map { it.id })
    }

    @Test fun draft_changes_do_not_reload_products_until_apply_and_apply_resets_page_one() = runTest {
        val source = RecordingSource(options())
        val controller = StoreOutletController(controllerScope(), source)
        controller.selectCategory(446)
        advanceUntilIdle()
        controller.openFilters()
        advanceUntilIdle()
        val before = source.pageCalls.size
        controller.changeDraftSizes(setOf("3XL"))
        controller.changeDraftColors(setOf("Azul"))
        controller.changeDraftBrands(setOf("Marca"))
        advanceUntilIdle()
        assertEquals(before, source.pageCalls.size)
        assertTrue(controller.applyFilters())
        advanceUntilIdle()
        assertEquals(before + 1, source.pageCalls.size)
        val query = source.pageCalls.last().first
        assertEquals(listOf(342), query.sizeIds)
        assertEquals(listOf(353), query.colorIds)
        assertEquals(listOf(777), query.brandIds)
        assertEquals(1, source.pageCalls.last().second)
    }

    @Test fun bubble_selection_becomes_origin_context_and_restarts_query() = runTest {
        val source = RecordingSource(options())
        val controller = StoreOutletController(controllerScope(), source)
        controller.updateAvailability(availability(outletId = 477, categoryId = 78, count = 2))
        controller.selectCategory(477)
        advanceUntilIdle()
        controller.selectBubble("ropa de bano")
        advanceUntilIdle()
        assertEquals(listOf(78, 287), source.pageCalls.last().first.originCategoryIds)
        assertEquals(1, source.pageCalls.last().second)
    }

    @Test fun sort_is_backend_query_identity_and_restarts_page_one() = runTest {
        val source = RecordingSource(options())
        val controller = StoreOutletController(controllerScope(), source)
        controller.selectCategory(446)
        advanceUntilIdle()
        controller.selectSort(StoreOutletSort.PRICE_DESC)
        advanceUntilIdle()
        assertEquals(StoreOutletSort.PRICE_DESC, source.pageCalls.last().first.sort)
        assertEquals(1, source.pageCalls.last().second)
    }

    @Test fun near_end_prefetch_appends_exactly_one_next_page() = runTest {
        val source = RecordingSource(
            options(),
            pageResponse = { _, page, _ ->
                CatalogPage(
                    items = (1..12).map { StoreProduct(id = (page - 1) * 12 + it) },
                    hasMore = page == 1,
                    totalItems = 24
                )
            }
        )
        val controller = StoreOutletController(controllerScope(), source)
        controller.selectCategory(446)
        advanceUntilIdle()
        controller.onVisibleRangeChanged(6)
        controller.onVisibleRangeChanged(7)
        advanceUntilIdle()
        assertEquals(listOf(1, 2), source.pageCalls.map { it.second })
        assertEquals(24, controller.state.value.sortedProducts.size)
        assertFalse(controller.state.value.paging.hasMore)
    }

    @Test fun facet_query_preserves_category_bubble_and_other_draft_dimensions() = runTest {
        val source = RecordingSource(options())
        val controller = StoreOutletController(controllerScope(), source)
        controller.updateAvailability(availability(outletId = 477, categoryId = 78, count = 2))
        controller.selectCategory(477)
        advanceUntilIdle()
        controller.selectBubble("ropa de bano")
        controller.openFilters()
        advanceUntilIdle()
        controller.changeDraftColors(setOf("Azul"))
        controller.changeDraftBrands(setOf("Marca"))
        controller.selectFacetDimension("sizes")
        advanceUntilIdle()
        val (query, dimension) = source.facetCalls.last()
        assertEquals(StoreOutletFacetDimension.SIZES, dimension)
        assertEquals(477, query.categoryId)
        assertEquals(listOf(78, 287), query.originCategoryIds)
        assertEquals(emptyList(), query.sizeIds)
        assertEquals(listOf(353), query.colorIds)
        assertEquals(listOf(777), query.brandIds)
    }

    @Test fun stale_facet_response_cannot_overwrite_newer_draft() = runTest {
        val firstGate = CompletableDeferred<Unit>()
        var call = 0
        val source = RecordingSource(options(), facetResponse = { _, _ ->
            call++
            if (call == 1) {
                firstGate.await()
                mapOf(342 to 99)
            } else mapOf(342 to 1)
        })
        val controller = StoreOutletController(controllerScope(), source)
        controller.selectCategory(446)
        advanceUntilIdle()
        controller.openFilters()
        runCurrent()
        controller.changeDraftColors(setOf("Azul"))
        controller.selectFacetDimension("sizes")
        runCurrent()
        firstGate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, controller.state.value.facetCounts["sizes"]?.get(342))
    }

    @Test fun repeated_open_filters_reuses_same_in_flight_facet_request() = runTest {
        val gate = CompletableDeferred<Unit>()
        val source = RecordingSource(options(), facetResponse = { _, _ ->
            gate.await()
            mapOf(342 to 2)
        })
        val controller = StoreOutletController(controllerScope(), source)
        controller.selectCategory(446)
        advanceUntilIdle()

        controller.openFilters()
        runCurrent()
        assertEquals(1, source.facetCalls.size)

        controller.openFilters()
        controller.openFilters()
        runCurrent()
        assertEquals(1, source.facetCalls.size)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(2, controller.state.value.facetCounts["sizes"]?.get(342))
    }

    @Test fun facet_cache_hit_does_not_start_another_request() = runTest {
        val source = RecordingSource(options(), facetResponse = { _, _ -> mapOf(342 to 3) })
        val controller = StoreOutletController(controllerScope(), source)
        controller.selectCategory(446)
        advanceUntilIdle()

        controller.openFilters()
        advanceUntilIdle()
        assertEquals(1, source.facetCalls.size)

        controller.openFilters()
        advanceUntilIdle()
        assertEquals(1, source.facetCalls.size)
        assertEquals(3, controller.state.value.facetCounts["sizes"]?.get(342))
    }

    @Test fun failed_facet_request_releases_guard_for_retry() = runTest {
        var attempts = 0
        val source = RecordingSource(options(), facetResponse = { _, _ ->
            attempts++
            if (attempts == 1) error("temporary facet failure")
            mapOf(342 to 4)
        })
        val controller = StoreOutletController(controllerScope(), source)
        controller.selectCategory(446)
        advanceUntilIdle()

        controller.openFilters()
        runCurrent()
        assertEquals(1, source.facetCalls.size)

        controller.openFilters()
        advanceUntilIdle()
        assertEquals(2, source.facetCalls.size)
        assertEquals(4, controller.state.value.facetCounts["sizes"]?.get(342))
    }

    @Test fun successful_facet_response_is_cached_and_reused() = runTest {
        val source = RecordingSource(options(), facetResponse = { _, _ -> mapOf(342 to 5) })
        val controller = StoreOutletController(controllerScope(), source)
        controller.selectCategory(446)
        advanceUntilIdle()

        controller.openFilters()
        advanceUntilIdle()
        val callsAfterSuccess = source.facetCalls.size
        assertEquals(1, callsAfterSuccess)
        assertEquals(5, controller.state.value.facetCounts["sizes"]?.get(342))

        controller.openFilters()
        advanceUntilIdle()
        assertEquals(callsAfterSuccess, source.facetCalls.size)
    }

    @Test fun controller_retains_paginator_per_full_query_identity() = runTest {
        val source = RecordingSource(options())
        val controller = StoreOutletController(controllerScope(), source)
        controller.selectCategory(446)
        advanceUntilIdle()
        controller.selectSort(StoreOutletSort.PRICE_ASC)
        advanceUntilIdle()
        controller.selectSort(StoreOutletSort.RECENT)
        advanceUntilIdle()
        assertEquals(2, controller.retainedPaginatorCount())
        assertEquals(2, source.pageCalls.size)
    }

    @Test fun missing_availability_fails_closed() = runTest {
        val controller = StoreOutletController(controllerScope(), RecordingSource(options()))
        controller.updateAvailability(null)
        controller.selectCategory(446)
        advanceUntilIdle()
        assertTrue(controller.state.value.visibleBubbles.isEmpty())
    }

    @Test fun unsupported_availability_schema_fails_closed() = runTest {
        val controller = StoreOutletController(controllerScope(), RecordingSource(options()))
        controller.updateAvailability(availability(schemaVersion = 99))
        controller.selectCategory(446)
        advanceUntilIdle()
        assertTrue(controller.state.value.visibleBubbles.isEmpty())
    }

    @Test fun positive_availability_shows_bubble() = runTest {
        val controller = StoreOutletController(controllerScope(), RecordingSource(options()))
        controller.updateAvailability(availability())
        controller.selectCategory(446)
        advanceUntilIdle()
        assertTrue(controller.state.value.visibleBubbles.any { it.key == "abrigos y cazadoras" })
    }

    @Test fun unavailable_bubble_selection_is_ignored() = runTest {
        val controller = StoreOutletController(controllerScope(), RecordingSource(options()))
        controller.updateAvailability(availability(categoryId = 430, count = 0))
        controller.selectCategory(446)
        advanceUntilIdle()
        controller.selectBubble("abrigos y cazadoras")
        assertEquals(null, controller.state.value.selectedBubbleKey)
    }

    @Test fun bubble_selection_does_not_change_common_screen_title() = runTest {
        val controller = StoreOutletController(controllerScope(), RecordingSource(options()))
        controller.updateCategories(listOf(StoreCategory(id = 446, parent = 445, name = "Hombre invierno")))
        controller.updateAvailability(availability())
        controller.selectCategory(446)
        advanceUntilIdle()
        val before = controller.state.value.screenTitle
        controller.selectBubble("abrigos y cazadoras")
        advanceUntilIdle()
        assertEquals(before, controller.state.value.screenTitle)
    }
}
