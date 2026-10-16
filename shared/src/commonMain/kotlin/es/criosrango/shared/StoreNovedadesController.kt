package es.criosrango.shared

import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.api.StoreFilterApiClient
import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreProduct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

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
    val paging: CatalogPagingState<StoreProduct> = CatalogPagingState(hasMore = false),
    val filterDialogVisible: Boolean = false,
    val filterOptionsState: StoreFilterOptionsState = StoreFilterOptionsState.Idle,
    val selectedSizes: Set<String> = emptySet(), val selectedColors: Set<String> = emptySet(),
    val selectedBrands: Set<String> = emptySet(), val draftSizes: Set<String> = emptySet(),
    val draftColors: Set<String> = emptySet(), val draftBrands: Set<String> = emptySet(),
    val isFiltering: Boolean = false, val filterError: Throwable? = null
) {
    val activeFilterCount: Int get() = selectedSizes.size + selectedColors.size + selectedBrands.size
}
interface StoreNovedadesDataSource {
    suspend fun categories(page: Int, perPage: Int): List<StoreCategory>
    suspend fun products(page: Int, perPage: Int, categoryIds: List<Int>, sort: StoreNovedadesSort): CatalogPage<StoreProduct>
    suspend fun loadFilterOptions(): StoreFilterOptions = StoreFilterOptions(emptyList(), emptyList(), emptyList())
    suspend fun matchingProductIds(sizeIds: List<Int>, colorIds: List<Int>, brandIds: List<Int>): Set<Int> = emptySet()
}
class StoreApiNovedadesDataSource(
    private val api: StoreApiClient, private val filterApi: StoreFilterApiClient = StoreFilterApiClient()
) : StoreNovedadesDataSource {
    private val filterOptionsSource = storeOutletDataSource(api, filterApi)
    override suspend fun categories(page: Int, perPage: Int) = api.categories(perPage = perPage, page = page)
    override suspend fun products(page: Int, perPage: Int, categoryIds: List<Int>, sort: StoreNovedadesSort): CatalogPage<StoreProduct> {
        val result = api.productsPage(perPage, page, categoryIds, sort.orderBy, sort.order)
        val more = result.totalPages?.let { page < it } ?: (result.items.size >= perPage)
        return CatalogPage(result.items, more, result.totalItems)
    }
    override suspend fun loadFilterOptions(): StoreFilterOptions = filterOptionsSource.loadFilterOptions()
    override suspend fun matchingProductIds(sizeIds: List<Int>, colorIds: List<Int>, brandIds: List<Int>): Set<Int> {
        // Filter API returns IDs newest-first. A member of the global newest 24 that matches
        // these filters must occur within the first 24 filtered matches; intersect IDs locally.
        return filterApi.query(
            scope = "catalog", sizeIds = sizeIds, colorIds = colorIds, brandIds = brandIds,
            page = 1, perPage = StoreNovedadesController.GLOBAL_NOVEDADES_LIMIT, sort = "newest"
        ).productIds.toSet()
    }
}

/** The global latest-24 dataset is immutable between explicit refreshes; all UI state is owned here. */
class StoreNovedadesController(private val scope: CoroutineScope, private val source: StoreNovedadesDataSource) {
    companion object { const val PAGE_SIZE = 24; const val GLOBAL_NOVEDADES_LIMIT = 24 }
    private val mutableState = MutableStateFlow(StoreNovedadesState())
    val state: StateFlow<StoreNovedadesState> = mutableState
    private var started = false
    private var loading = false
    private var generation = 0L
    private var filterGeneration = 0L
    private var globalProducts: List<StoreProduct> = emptyList()
    private var categoryIdsByAudience: Map<StoreNovedadesAudience, Set<Int>> = emptyMap()
    private var cachedFilterOptions: StoreFilterOptions? = null
    private var filterOptionsLoading = false
    private var matchingFilterProductIds: Set<Int>? = null

    fun start() { if (!started) { started = true; reloadGlobalDataset() } }
    fun selectAudience(value: StoreNovedadesAudience) {
        val s = mutableState.value
        if (value == s.selectedAudience) return
        mutableState.value = s.copy(selectedAudience=value, queryIdentity=identity(value,s.selectedSort,s.selectedSizes,s.selectedColors,s.selectedBrands))
        publishProjection()
    }
    fun selectSort(value: StoreNovedadesSort) {
        val s = mutableState.value
        if (value == s.selectedSort) return
        mutableState.value = s.copy(selectedSort=value, queryIdentity=identity(s.selectedAudience,value,s.selectedSizes,s.selectedColors,s.selectedBrands))
        publishProjection()
    }
    fun openFilters() {
        val s=mutableState.value
        mutableState.value=s.copy(filterDialogVisible=true,draftSizes=s.selectedSizes,draftColors=s.selectedColors,draftBrands=s.selectedBrands)
        ensureFilterOptions()
    }
    fun cancelFilters() {
        val s=mutableState.value
        mutableState.value=s.copy(filterDialogVisible=false,draftSizes=s.selectedSizes,draftColors=s.selectedColors,draftBrands=s.selectedBrands)
    }
    fun changeDraftSizes(values:Set<String>) { mutableState.value=mutableState.value.copy(draftSizes=values) }
    fun changeDraftColors(values:Set<String>) { mutableState.value=mutableState.value.copy(draftColors=values) }
    fun changeDraftBrands(values:Set<String>) { mutableState.value=mutableState.value.copy(draftBrands=values) }
    fun selectFilterDimension(dimension:String) { if (dimension in setOf("sizes","colors","brands")) Unit }
    fun clearDraftFilters() { mutableState.value=mutableState.value.copy(draftSizes=emptySet(),draftColors=emptySet(),draftBrands=emptySet()) }
    fun applyFilters() {
        val s=mutableState.value
        mutableState.value=s.copy(
            filterDialogVisible=false,selectedSizes=s.draftSizes,selectedColors=s.draftColors,selectedBrands=s.draftBrands,
            queryIdentity=identity(s.selectedAudience,s.selectedSort,s.draftSizes,s.draftColors,s.draftBrands)
        )
        if(mutableState.value.activeFilterCount==0) {
            filterGeneration++; matchingFilterProductIds=null
            mutableState.value=mutableState.value.copy(isFiltering=false,filterError=null); publishProjection()
        } else reloadFilterProjection()
    }
    fun clearFilters() {
        filterGeneration++; matchingFilterProductIds=null
        val s=mutableState.value
        mutableState.value=s.copy(filterDialogVisible=false,selectedSizes=emptySet(),selectedColors=emptySet(),selectedBrands=emptySet(),
            draftSizes=emptySet(),draftColors=emptySet(),draftBrands=emptySet(),isFiltering=false,filterError=null,
            queryIdentity=identity(s.selectedAudience,s.selectedSort,emptySet(),emptySet(),emptySet()))
        publishProjection()
    }
    fun retryFilters() = reloadFilterProjection()
    fun retryFilterOptions() = ensureFilterOptions(force=true)
    fun refresh() = reloadGlobalDataset()
    fun retry() = reloadGlobalDataset()
    fun loadNextPage(expectedIdentity:String?=null) = Unit

    private fun ensureFilterOptions(force:Boolean=false) {
        if(cachedFilterOptions!=null && !force) {
            mutableState.value=mutableState.value.copy(filterOptionsState=StoreFilterOptionsState.Ready(cachedFilterOptions!!)); return
        }
        if(filterOptionsLoading) return
        filterOptionsLoading=true
        mutableState.value=mutableState.value.copy(filterOptionsState=StoreFilterOptionsState.Loading)
        scope.launch {
            try {
                val options=source.loadFilterOptions(); cachedFilterOptions=options
                mutableState.value=mutableState.value.copy(filterOptionsState=StoreFilterOptionsState.Ready(options))
            } catch(e:CancellationException) { throw e
            } catch(e:Exception) {
                mutableState.value=mutableState.value.copy(filterOptionsState=StoreFilterOptionsState.Error(e.message ?: "No se han podido cargar los filtros."))
            } finally { filterOptionsLoading=false }
        }
    }
    private fun reloadFilterProjection() {
        val request=++filterGeneration
        val s=mutableState.value
        if(s.activeFilterCount==0) {
            matchingFilterProductIds=null; mutableState.value=s.copy(isFiltering=false,filterError=null); publishProjection(); return
        }
        val options=cachedFilterOptions
        if(options==null) {
            mutableState.value=s.copy(isFiltering=false,filterError=IllegalStateException("Los metadatos de filtros aún no están disponibles.")); publishProjection(); return
        }
        fun ids(selected:Set<String>, values:List<StoreFilterOption>, label:String):List<Int> {
            val found=values.filter{it.name in selected}.map{it.id}.distinct().sorted()
            require(selected.isEmpty() || found.isNotEmpty()) { "No se han podido resolver las opciones de $label seleccionadas." }
            return found
        }
        val sizeIds:List<Int>; val colorIds:List<Int>; val brandIds:List<Int>
        try {
            sizeIds=ids(s.selectedSizes,options.sizes,"talla"); colorIds=ids(s.selectedColors,options.colors,"color"); brandIds=ids(s.selectedBrands,options.brands,"marca")
        } catch(e:Exception) {
            matchingFilterProductIds=null; mutableState.value=s.copy(isFiltering=false,filterError=e); publishProjection(); return
        }
        matchingFilterProductIds=null
        mutableState.value=s.copy(isFiltering=true,filterError=null); publishProjection()
        scope.launch {
            try {
                val returnedIds=source.matchingProductIds(sizeIds,colorIds,brandIds)
                if(request!=filterGeneration) return@launch
                matchingFilterProductIds=returnedIds intersect globalProducts.map{it.id}.toSet()
                mutableState.value=mutableState.value.copy(isFiltering=false,filterError=null); publishProjection()
            } catch(e:CancellationException) { throw e
            } catch(e:Exception) {
                if(request!=filterGeneration) return@launch
                matchingFilterProductIds=null; mutableState.value=mutableState.value.copy(isFiltering=false,filterError=e); publishProjection()
            }
        }
    }
    private fun reloadGlobalDataset() {
        val request=++generation; filterGeneration++; loading=true; matchingFilterProductIds=null
        mutableState.value=mutableState.value.copy(isFiltering=false,filterError=null,paging=CatalogPagingState(isInitialLoading=true,hasMore=false))
        scope.launch {
            try {
                val categories=loadCategories()
                val products=source.products(1,GLOBAL_NOVEDADES_LIMIT,emptyList(),StoreNovedadesSort.RECENT).items.take(GLOBAL_NOVEDADES_LIMIT)
                if(request!=generation) return@launch
                globalProducts=products
                categoryIdsByAudience=StoreNovedadesAudience.values().filter{it!=StoreNovedadesAudience.ALL}
                    .associateWith{resolveAudienceCategoryIds(categories,it).toSet()}
                loading=false
                if(mutableState.value.activeFilterCount>0) reloadFilterProjection() else publishProjection()
            } catch(e:CancellationException) { throw e
            } catch(e:Exception) {
                if(request!=generation) return@launch
                loading=false; mutableState.value=mutableState.value.copy(paging=CatalogPagingState(isInitialLoading=false,initialError=e,hasMore=false))
            }
        }
    }
    private suspend fun loadCategories():List<StoreCategory> {
        val all=mutableListOf<StoreCategory>(); var page=1
        while(true) { val batch=source.categories(page,100); all+=batch; if(batch.size<100) break; page++ }
        return all.distinctBy{it.id}
    }
    private fun publishProjection() {
        if(loading) return
        val s=mutableState.value
        val audienceIds=categoryIdsByAudience[s.selectedAudience].orEmpty()
        val categoryProducts=if(s.selectedAudience==StoreNovedadesAudience.ALL) globalProducts
            else globalProducts.filter{p->p.categories.any{it.id in audienceIds}}
        val filtered=if(s.activeFilterCount==0) categoryProducts else categoryProducts.filter{it.id in matchingFilterProductIds.orEmpty()}
        val sorted=when(s.selectedSort) {
            StoreNovedadesSort.RECENT -> filtered
            StoreNovedadesSort.PRICE_ASC -> filtered.sortedBy{it.prices.price.toLongOrNull()?:Long.MAX_VALUE}
            StoreNovedadesSort.PRICE_DESC -> filtered.sortedByDescending{it.prices.price.toLongOrNull()?:Long.MIN_VALUE}
            StoreNovedadesSort.NAME_ASC -> filtered.sortedBy{it.name.lowercase()}
        }
        mutableState.value=mutableState.value.copy(paging=CatalogPagingState(items=sorted,currentPage=if(globalProducts.isEmpty())0 else 1,hasMore=false,isInitialLoading=false,totalItems=sorted.size))
    }
    private fun identity(a:StoreNovedadesAudience,s:StoreNovedadesSort,sizes:Set<String>,colors:Set<String>,brands:Set<String>) =
        "novedades:${a.key}:${s.key}:sizes=${sizes.sorted().joinToString(",")}:colors=${colors.sorted().joinToString(",")}:brands=${brands.sorted().joinToString(",")}"
}

internal fun resolveAudienceCategoryIds(categories:List<StoreCategory>,audience:StoreNovedadesAudience):List<Int> {
    if(audience==StoreNovedadesAudience.ALL) return emptyList()
    val byId=categories.associateBy{it.id}
    fun normalize(v:String)=v.lowercase().trim().replace('-',' ').replace('_',' ').replace('á','a').replace('é','e').replace('í','i').replace('ó','o').replace('ú','u').replace('ü','u').replace('ñ','n').replace(Regex("\\s+")," ")
    val targets=when(audience) {
        StoreNovedadesAudience.ALL->emptySet()
        StoreNovedadesAudience.BAUTIZO->setOf("bautizo")
        StoreNovedadesAudience.CRIOS->setOf("bebe nina","bebe nino","nina","nino")
        StoreNovedadesAudience.COMUNION->setOf("comunion nina","comunion nino")
        StoreNovedadesAudience.MUJER->setOf("mujer")
        StoreNovedadesAudience.HOMBRE->setOf("hombre")
        StoreNovedadesAudience.RECIEN_NACIDO->setOf("recien nacido")
    }
    return categories.filter { category ->
        var current:StoreCategory?=category; val visited=mutableSetOf<Int>(); var match=false
        while(current!=null && current.id!=0 && visited.add(current.id)) {
            if(normalize(current.name) in targets || normalize(current.slug) in targets) { match=true; break }
            current=byId[current.parent]
        }
        match
    }.map{it.id}.distinct().sorted()
}
