package es.criosrango.app

import kotlinx.coroutines.flow.MutableStateFlow

/** Applies a refreshed category directly to the ViewModel state, avoiding a loading/skeleton reset. */
fun ShopViewModel.applyCategoryCacheUpdate(update: CategoryCacheUpdate) {
    runCatching {
        val field = ShopViewModel::class.java.getDeclaredField("_products")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val state = field.get(this) as MutableStateFlow<List<StoreProduct>>
        state.value = update.products.toList()
        categoryCatalogLoadStatus.value = CategoryLoadStatus(
            update.categoryId,
            if (update.products.isEmpty()) CategoryLoadState.LOADED_EMPTY else CategoryLoadState.LOADED_WITH_RESULTS
        )
    }
}
