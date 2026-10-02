package es.criosrango.app

import kotlin.test.Test
import kotlin.test.assertEquals

class HomeCategoryContentStateTest {
    @Test fun existingCategoriesAlwaysShowRealContentDuringRefresh() {
        assertEquals(HomeCategoryContentState.CONTENT, homeCategoryContentState(hasCategories = true, loading = true))
        assertEquals(HomeCategoryContentState.CONTENT, homeCategoryContentState(hasCategories = true, loading = false))
    }

    @Test fun emptyCategoriesWhileLoadingShowSkeleton() {
        assertEquals(HomeCategoryContentState.SKELETON, homeCategoryContentState(hasCategories = false, loading = true))
    }

    @Test fun emptyCategoriesAfterLoadingShowEmptyState() {
        assertEquals(HomeCategoryContentState.EMPTY, homeCategoryContentState(hasCategories = false, loading = false))
    }
}