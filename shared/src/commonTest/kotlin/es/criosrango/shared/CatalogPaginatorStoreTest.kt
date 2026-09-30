package es.criosrango.shared

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CatalogPaginatorStoreTest {
    @Test
    fun start_and_append_expose_shared_paginator_state() = runTest {
        val store = CatalogPaginatorStore<Int>(this) { it }
        store.start("test") { page, perPage ->
            assertEquals(12, perPage)
            if (page == 1) CatalogPage((1..12).toList(), true)
            else CatalogPage((13..14).toList(), false)
        }
        advanceUntilIdle()
        assertEquals((1..12).toList(), store.state.value.items)
        assertTrue(store.state.value.hasMore)

        store.loadNext { page, _ ->
            assertEquals(2, page)
            CatalogPage((13..14).toList(), false)
        }
        advanceUntilIdle()
        assertEquals((1..14).toList(), store.state.value.items)
        assertEquals(2, store.state.value.currentPage)
    }
}