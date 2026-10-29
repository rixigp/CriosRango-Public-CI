package es.criosrango.shared

import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.api.StoreFilterApiClient
import es.criosrango.shared.api.StoreRequestTimingRecorder
import es.criosrango.shared.api.NoOpStoreRequestTimingRecorder
import es.criosrango.shared.api.recordStoreOperationTiming
import es.criosrango.shared.model.StoreGlobalAttributeTerm

/** Stateless transport adapter. StoreOutletController owns every Outlet query decision. */
fun storeOutletDataSource(
    storeApi: StoreApiClient,
    filterApi: StoreFilterApiClient = StoreFilterApiClient(),
    timingRecorder: StoreRequestTimingRecorder = NoOpStoreRequestTimingRecorder
): StoreOutletDataSource = object : StoreOutletDataSource {
    override suspend fun loadFilterOptions(): StoreFilterOptions = recordStoreOperationTiming(timingRecorder,"outlet_filter_options_operation","/wp-json/wc/store/v1/products/attributes",scope="outlet"){
        val attrs = storeApi.globalAttributes()
        fun canonical(value: String) = value.lowercase().trim()
            .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
            .replace("à", "a").replace("è", "e").replace("ì", "i").replace("ò", "o").replace("ù", "u")
        val sizeAttr = attrs.firstOrNull { canonical(it.name) in setOf("tallas", "talla") }
            ?: throw IllegalStateException("No se encontró el atributo global Tallas; atributos recibidos: ${attrs.map { it.name }}")
        val colorAttr = attrs.firstOrNull { canonical(it.name) in setOf("color", "colores") }
            ?: throw IllegalStateException("No se encontró el atributo global Color; atributos recibidos: ${attrs.map { it.name }}")
        fun StoreGlobalAttributeTerm.toOption(taxonomy: String) = StoreFilterOption(
            id = id,
            name = normalizeFilterMetadataDisplayName(name),
            slug = slug,
            taxonomy = taxonomy,
            count = count
        )
        val sizes = storeApi.globalAttributeTerms(sizeAttr.id).map { it.toOption(sizeAttr.taxonomy) }
        val colors = storeApi.globalAttributeTerms(colorAttr.id).map { it.toOption(colorAttr.taxonomy) }
        val brands = storeApi.productTags().map {
            StoreFilterOption(
                id = it.id,
                name = normalizeFilterMetadataDisplayName(it.name),
                slug = it.slug,
                count = it.count
            )
        }
        StoreFilterOptions(sizes, colors, brands)
    }

    override suspend fun loadPage(query:StoreOutletQuery,page:Int,perPage:Int):CatalogPage<es.criosrango.shared.model.StoreProduct> =
        recordStoreOperationTiming(timingRecorder,"outlet_datasource_page","/wp-json/criosrango-filter/v1/query",page,perPage,"outlet"){
        require(page >= 1 && perPage == CatalogPaginator.PAGE_SIZE) { "Outlet pages must use the canonical page size." }
        val q = query.canonical()
        val response = filterApi.query(
            scope = "outlet",
            outletCategoryId = q.categoryId,
            originCategoryIds = q.originCategoryIds,
            sizeIds = q.sizeIds,
            colorIds = q.colorIds,
            brandIds = q.brandIds,
            page = page,
            perPage = perPage,
            sort = q.sort.toBackendSort()
        )
        val products = if (response.productIds.isEmpty()) emptyList() else storeApi.productsByIds(response.productIds)
        CatalogPage(products, response.hasMore, response.total)
    }

    override suspend fun loadFacet(query:StoreOutletQuery,dimension:StoreOutletFacetDimension):Map<Int,Int> =
        recordStoreOperationTiming(timingRecorder,"outlet_facet_operation","/wp-json/criosrango-filter/v1/query",1,CatalogPaginator.PAGE_SIZE,"outlet"){
        val q = query.canonical()
        val response = filterApi.query(
            scope = "outlet",
            outletCategoryId = q.categoryId,
            originCategoryIds = q.originCategoryIds,
            sizeIds = q.sizeIds,
            colorIds = q.colorIds,
            brandIds = q.brandIds,
            page = 1,
            perPage = CatalogPaginator.PAGE_SIZE,
            sort = "newest",
            facet = dimension.apiValue
        )
        response.facets[dimension.apiValue].orEmpty().associate { it.termId to it.count }
    }
}

internal fun StoreOutletSort.toBackendSort(): String = when (this) {
    StoreOutletSort.RECENT -> "newest"
    StoreOutletSort.PRICE_ASC -> "price_asc"
    StoreOutletSort.PRICE_DESC -> "price_desc"
    StoreOutletSort.NAME_ASC -> "title_asc"
}
