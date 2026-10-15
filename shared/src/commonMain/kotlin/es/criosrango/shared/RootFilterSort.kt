package es.criosrango.shared
fun StoreProductSortOption.toBackendSort(): String = when (this) {
 StoreProductSortOption.RECENT -> "newest"
 StoreProductSortOption.PRICE_ASC -> "price_asc"
 StoreProductSortOption.PRICE_DESC -> "price_desc"
 StoreProductSortOption.NAME_ASC -> "title_asc"
}
