package es.criosrango.app

/**
 * Single-page category data source for the UI paginator.
 *
 * This deliberately bypasses StoreRepository.products(category), whose existing
 * cache path may return the complete category snapshot. The page-based flow
 * must request exactly one backend page while CategoryCatalogCache/global sync
 * remains independent.
 */
class CategoryProductsPageDataSource(
    private val api: StoreApi
) {
    suspend fun productsByCategoryPage(
        categoryId: Int,
        page: Int,
        perPage: Int
    ): List<StoreProduct> = api.products(
        perPage = perPage,
        page = page,
        category = categoryId
    )
}
