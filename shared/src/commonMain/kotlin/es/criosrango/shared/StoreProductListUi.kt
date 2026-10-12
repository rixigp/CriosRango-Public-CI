package es.criosrango.shared

/** Presentation-only sort labels; platform adapters retain sort behavior. */
enum class StoreProductSortOption(val label: String) {
    RECENT("Más recientes"),
    PRICE_ASC("Precio: menor a mayor"),
    PRICE_DESC("Precio: mayor a menor"),
    NAME_ASC("Nombre A-Z")
}

/** Display-ready product data for the shared product-list presentation. */
data class StoreProductItemUi(
    val id: Int,
    val nameText: String,
    val imageIdentity: String?,
    val displayPriceText: String,
    val regularPriceText: String,
    val showPrice: Boolean,
    val isOnSale: Boolean
)

/** Stable neutral identifier plus visible label for one active filter. */
data class StoreActiveFilterChipUi(
    val id: String,
    val label: String
)

/** Pure UI state. Filtering, sorting, loading, and paging are owned by each platform. */
data class StoreProductListUiState(
    val title: String,
    val items: List<StoreProductItemUi>,
    val selectedSort: StoreProductSortOption,
    val availableSortOptions: List<StoreProductSortOption>,
    val activeFilterCount: Int,
    val activeFilterChips: List<StoreActiveFilterChipUi>,
    val isInitialLoading: Boolean,
    val isLoadingMore: Boolean,
    val isEmpty: Boolean,
    val emptyMessage: String,
    val initialErrorMessage: String?,
    val appendErrorMessage: String?,
    val appendRetryLabel: String,
    val canLoadMore: Boolean
)
