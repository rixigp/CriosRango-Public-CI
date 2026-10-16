package es.criosrango.shared

import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreProduct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class StoreNovedadesAudience(val key: String, val label: String) {
    ALL("all", "Todas"), BAUTIZO("bautizo", "Bautizo"), CRIOS("crios", "Críos"),
    COMUNION("comunion", "Comunión"), MUJER("mujer", "Mujer"), HOMBRE("hombre", "Hombre"),
    RECIEN_NACIDO("recien_nacido", "Recién nacido")
}

enum class StoreNovedadesSort(val key: String, val label: String, val orderBy: String, val order: String) {
    RECENT("recent", "Más recientes", "date", "desc"),
    PRICE_ASC("price_asc", "Precio: menor a mayor", "price", "asc"),
    PRICE_DESC("price_desc", "Precio: mayor a menor", "price", "desc"),
    NAME_ASC("name_asc", "Nombre A-Z", "title", "asc")
}

data class StoreNovedadesState(
    val selectedAudience: StoreNovedadesAudience = StoreNovedadesAudience.ALL,
    val selectedSort: StoreNovedadesSort = StoreNovedadesSort.RECENT,
    val queryIdentity: String = "novedades:all:recent",
    val paging: CatalogPagingState<StoreProduct> = CatalogPagingState(hasMore = false)
)

interface StoreNovedadesDataSource {
    suspend fun categories(page: Int, perPage: Int): List<StoreCategory>
    suspend fun products(page: Int, perPage: Int, categoryIds: List<Int>, sort: StoreNovedadesSort): CatalogPage<StoreProduct>
}

class StoreApiNovedadesDataSource(private val api: StoreApiClient) : StoreNovedadesDataSource {
    override suspend fun categories(page: Int, perPage: Int) = api.categories(perPage = perPage, page = page)

    override suspend fun products(
        page: Int,
        perPage: Int,
        categoryIds: List<Int>,
        sort: StoreNovedadesSort
    ): CatalogPage<StoreProduct> {
        val result = api.productsPage(perPage, page, categoryIds, sort.orderBy, sort.order)
        val more = result.totalPages?.let { page < it } ?: (result.items.size >= perPage)
        return CatalogPage(result.items, more, result.totalItems)
    }
}

/**
 * Novedades has one fixed global universe: the newest 24 products.
 * Audience and sort selections are projections of that in-memory snapshot and never issue product requests.
 */
class StoreNovedadesController(
    private val scope: CoroutineScope,
    private val source: StoreNovedadesDataSource
) {
    companion object {
        const val PAGE_SIZE = 24
        const val GLOBAL_NOVEDADES_LIMIT = 24
    }

    private val mutableState = MutableStateFlow(StoreNovedadesState())
    val state: StateFlow<StoreNovedadesState> = mutableState
    private var started = false
    private var loading = false
    private var generation = 0L
    private var globalProducts: List<StoreProduct> = emptyList()
    private var categoryIdsByAudience: Map<StoreNovedadesAudience, Set<Int>> = emptyMap()

    fun start() {
        if (!started) {
            started = true
            reloadGlobalDataset()
        }
    }

    fun selectAudience(value: StoreNovedadesAudience) {
        if (value == mutableState.value.selectedAudience) return
        mutableState.value = mutableState.value.copy(
            selectedAudience = value,
            queryIdentity = identity(value, mutableState.value.selectedSort)
        )
        publishProjection()
    }

    fun selectSort(value: StoreNovedadesSort) {
        if (value == mutableState.value.selectedSort) return
        mutableState.value = mutableState.value.copy(
            selectedSort = value,
            queryIdentity = identity(mutableState.value.selectedAudience, value)
        )
        publishProjection()
    }

    /** Explicit user refresh is the only post-start action that fetches a new global snapshot. */
    fun refresh() = reloadGlobalDataset()
    fun retry() = reloadGlobalDataset()

    /** Kept as a harmless compatibility entry point: this screen has no pagination. */
    fun loadNextPage(expectedIdentity: String? = null) = Unit

    private fun reloadGlobalDataset() {
        val requestGeneration = ++generation
        loading = true
        mutableState.value = mutableState.value.copy(
            paging = CatalogPagingState(isInitialLoading = true, hasMore = false)
        )
        scope.launch {
            try {
                val categories = loadCategories()
                val allProducts = source.products(
                    page = 1,
                    perPage = GLOBAL_NOVEDADES_LIMIT,
                    categoryIds = emptyList(),
                    sort = StoreNovedadesSort.RECENT
                ).items.take(GLOBAL_NOVEDADES_LIMIT)
                if (requestGeneration != generation) return@launch

                globalProducts = allProducts
                categoryIdsByAudience = StoreNovedadesAudience.values()
                    .filter { it != StoreNovedadesAudience.ALL }
                    .associateWith { resolveAudienceCategoryIds(categories, it).toSet() }
                loading = false
                publishProjection()
            } catch (error: Throwable) {
                if (requestGeneration != generation) return@launch
                loading = false
                mutableState.value = mutableState.value.copy(
                    paging = CatalogPagingState(
                        isInitialLoading = false,
                        initialError = error,
                        hasMore = false
                    )
                )
            }
        }
    }

    private suspend fun loadCategories(): List<StoreCategory> {
        val all = mutableListOf<StoreCategory>()
        var page = 1
        while (true) {
            val batch = source.categories(page, 100)
            all += batch
            if (batch.size < 100) break
            page++
        }
        return all.distinctBy { it.id }
    }

    private fun publishProjection() {
        if (loading) return
        val snapshot = mutableState.value
        val audience = snapshot.selectedAudience
        val audienceIds = categoryIdsByAudience[audience].orEmpty()
        val filtered = when (audience) {
            StoreNovedadesAudience.ALL -> globalProducts
            else -> globalProducts.filter { product -> product.categories.any { it.id in audienceIds } }
        }
        val sorted = when (snapshot.selectedSort) {
            StoreNovedadesSort.RECENT -> filtered
            StoreNovedadesSort.PRICE_ASC -> filtered.sortedBy { it.prices.price.toLongOrNull() ?: Long.MAX_VALUE }
            StoreNovedadesSort.PRICE_DESC -> filtered.sortedByDescending { it.prices.price.toLongOrNull() ?: Long.MIN_VALUE }
            StoreNovedadesSort.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
        }
        mutableState.value = snapshot.copy(
            paging = CatalogPagingState(
                items = sorted,
                currentPage = if (globalProducts.isEmpty()) 0 else 1,
                hasMore = false,
                isInitialLoading = false,
                totalItems = sorted.size
            )
        )
    }

    private fun identity(audience: StoreNovedadesAudience, sort: StoreNovedadesSort) =
        "novedades:${audience.key}:${sort.key}"
}

internal fun resolveAudienceCategoryIds(categories: List<StoreCategory>, audience: StoreNovedadesAudience): List<Int> {
    if (audience == StoreNovedadesAudience.ALL) return emptyList()
    val byId = categories.associateBy { it.id }
    fun normalize(value: String) = value.lowercase().trim().replace('-', ' ').replace('_', ' ')
        .replace('á','a').replace('é','e').replace('í','i').replace('ó','o').replace('ú','u').replace('ü','u').replace('ñ','n')
        .replace(Regex("\\s+"), " ")
    val targets = when (audience) {
        StoreNovedadesAudience.ALL -> emptySet()
        StoreNovedadesAudience.BAUTIZO -> setOf("bautizo")
        StoreNovedadesAudience.CRIOS -> setOf("bebe nina", "bebe nino", "nina", "nino")
        StoreNovedadesAudience.COMUNION -> setOf("comunion nina", "comunion nino")
        StoreNovedadesAudience.MUJER -> setOf("mujer")
        StoreNovedadesAudience.HOMBRE -> setOf("hombre")
        StoreNovedadesAudience.RECIEN_NACIDO -> setOf("recien nacido")
    }
    return categories.filter { category ->
        var current: StoreCategory? = category
        val visited = mutableSetOf<Int>()
        var match = false
        while (current != null && current.id != 0 && visited.add(current.id)) {
            if (normalize(current.name) in targets || normalize(current.slug) in targets) { match = true; break }
            current = byId[current.parent]
        }
        match
    }.map { it.id }.distinct().sorted()
}
