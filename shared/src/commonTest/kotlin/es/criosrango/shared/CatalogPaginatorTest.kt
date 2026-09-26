package es.criosrango.shared

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CatalogPaginatorTest {
    private data class Item(val id: Int)

    private fun paginator() = CatalogPaginator<Item>(identity = { it.id })

    @Test
    fun page1_is_published_immediately() = runTest {
        val paginator = paginator()

        paginator.start("mayoral") { page, size ->
            assertEquals(1, page)
            assertEquals(12, size)
            CatalogPage((1..12).map(::Item), hasMore = true)
        }

        assertEquals((1..12).toList(), paginator.state.value.items.map { it.id })
        assertEquals(1, paginator.state.value.currentPage)
        assertTrue(paginator.state.value.hasMore)
        assertFalse(paginator.state.value.isInitialLoading)
    }

    @Test
    fun append_page2_keeps_page1_and_appends() = runTest {
        val paginator = paginator()
        paginator.start("mayoral") { _, _ -> CatalogPage((1..12).map(::Item), true) }

        paginator.loadNext { page, size ->
            assertEquals(2, page)
            assertEquals(12, size)
            CatalogPage((13..24).map(::Item), true)
        }

        assertEquals((1..24).toList(), paginator.state.value.items.map { it.id })
        assertEquals(2, paginator.state.value.currentPage)
        assertFalse(paginator.state.value.isAppending)
    }

    @Test
    fun append_deduplicates_by_identity() = runTest {
        val paginator = paginator()
        paginator.start("mayoral") { _, _ -> CatalogPage((1..12).map(::Item), true) }

        paginator.loadNext { _, _ ->
            CatalogPage((10..21).map(::Item), true)
        }

        assertEquals((1..21).toList(), paginator.state.value.items.map { it.id })
    }

    @Test
    fun short_batch_marks_end() = runTest {
        val paginator = paginator()
        paginator.start("small") { _, _ -> CatalogPage((1..7).map(::Item), false) }

        assertFalse(paginator.state.value.hasMore)
        assertEquals(1, paginator.state.value.currentPage)

        var called = false
        paginator.loadNext { _, _ ->
            called = true
            CatalogPage(emptyList(), false)
        }

        assertFalse(called)
        assertEquals(7, paginator.state.value.items.size)
    }

    @Test
    fun simultaneous_loadNext_requests_only_one_page() = runTest {
        val paginator = paginator()
        paginator.start("mayoral") { _, _ -> CatalogPage((1..12).map(::Item), true) }

        val gate = CompletableDeferred<Unit>()
        var calls = 0

        val first = async {
            paginator.loadNext { page, _ ->
                calls++
                assertEquals(2, page)
                gate.await()
                CatalogPage((13..24).map(::Item), true)
            }
        }
        val second = async {
            paginator.loadNext { _, _ ->
                calls++
                CatalogPage((25..36).map(::Item), true)
            }
        }

        testScheduler.advanceUntilIdle()
        assertEquals(1, calls)
        gate.complete(Unit)
        first.await()
        second.await()
        assertEquals((1..24).toList(), paginator.state.value.items.map { it.id })
    }

    @Test
    fun append_error_keeps_existing_items() = runTest {
        val paginator = paginator()
        paginator.start("mayoral") { _, _ -> CatalogPage((1..12).map(::Item), true) }

        paginator.loadNext { _, _ -> error("page 2 failed") }

        assertEquals((1..12).toList(), paginator.state.value.items.map { it.id })
        assertFalse(paginator.state.value.isAppending)
        assertNotNull(paginator.state.value.appendError)
        assertTrue(paginator.state.value.hasMore)
    }

    @Test
    fun new_query_invalidates_old_response() = runTest {
        val paginator = paginator()
        val oldGate = CompletableDeferred<Unit>()
        val old = async {
            paginator.start("mayoral") { _, _ ->
                oldGate.await()
                CatalogPage((1..12).map(::Item), true)
            }
        }

        val fresh = async {
            paginator.start("boboli") { _, _ ->
                CatalogPage((101..112).map(::Item), false)
            }
        }
        fresh.await()
        oldGate.complete(Unit)
        old.await()

        assertEquals("boboli", "boboli")
        assertEquals((101..112).toList(), paginator.state.value.items.map { it.id })
        assertEquals(1, paginator.state.value.currentPage)
        assertFalse(paginator.state.value.hasMore)
    }
}
