package es.criosrango.shared

import es.criosrango.shared.api.StoreApiClient

/** Thin transport adapter: the common controller owns all Outlet query decisions. */
internal fun storeOutletDataSource(storeApi: StoreApiClient): StoreOutletDataSource =
    StoreOutletDataSource { query, page, perPage ->
        val products = storeApi.products(
            perPage = perPage,
            page = page,
            category = query.categoryId
        )
        CatalogPage(products, hasMore = products.size >= perPage)
    }
