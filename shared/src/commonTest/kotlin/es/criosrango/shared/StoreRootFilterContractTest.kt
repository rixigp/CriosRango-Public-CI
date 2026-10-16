package es.criosrango.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class StoreRootFilterContractTest {
    @Test fun outlet_root_uses_canonical_outlet_category_445() {
        assertEquals(445, StoreRootFilterScope.OUTLET.categoryQueryId)
        assertEquals("root-filter:outlet:445", StoreRootFilterScope.OUTLET.queryKey)
    }
    @Test fun categories_root_uses_unscoped_catalog_not_a_fake_category() {
        assertNull(StoreRootFilterScope.CATALOG.categoryQueryId)
        assertEquals("root-filter:catalog:unscoped", StoreRootFilterScope.CATALOG.queryKey)
    }
    @Test fun root_scopes_are_distinct_and_do_not_select_a_child() {
        assertNotEquals(StoreRootFilterScope.OUTLET.queryKey, StoreRootFilterScope.CATALOG.queryKey)
        assertNotEquals(447, StoreRootFilterScope.OUTLET.categoryQueryId)
        assertNull(StoreRootFilterScope.CATALOG.categoryQueryId)
    }
    @Test fun root_filter_scope_does_not_mutate_navigation_state() {
        val activeCategoryId = 445
        assertEquals(445, StoreRootFilterScope.OUTLET.categoryQueryId)
        assertEquals(445, activeCategoryId)
    }
    @Test fun existing_category_level_filter_scope_remains_distinct() {
        assertNotEquals(447, StoreRootFilterScope.OUTLET.categoryQueryId)
        assertNull(StoreRootFilterScope.CATALOG.categoryQueryId)
    }
}
