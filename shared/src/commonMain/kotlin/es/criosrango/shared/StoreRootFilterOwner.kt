package es.criosrango.shared

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.api.StoreFilterApiClient
import es.criosrango.shared.model.StoreGlobalAttributeTerm
import es.criosrango.shared.model.StoreProduct
import es.criosrango.shared.model.StoreProductAttributeFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class StoreFilterOption(val id: Int, val name: String, val slug: String, val taxonomy: String? = null, val count: Int? = null)
data class StoreFilterOptions(val sizes: List<StoreFilterOption>, val colors: List<StoreFilterOption>, val brands: List<StoreFilterOption>)
sealed interface StoreFilterOptionsState {
    data object Idle : StoreFilterOptionsState
    data object Loading : StoreFilterOptionsState
    data class Ready(val options: StoreFilterOptions) : StoreFilterOptionsState
    data class Error(val message: String) : StoreFilterOptionsState
}

data class StoreRootFilterState(
    val scope: StoreRootFilterScope? = null,
    val dialogVisible: Boolean = false,
    val optionsState: StoreFilterOptionsState = StoreFilterOptionsState.Idle,
    val draftSizes: Set<String> = emptySet(),
    val draftColors: Set<String> = emptySet(),
    val draftBrands: Set<String> = emptySet(),
    val appliedSizes: Set<String> = emptySet(),
    val appliedColors: Set<String> = emptySet(),
    val appliedBrands: Set<String> = emptySet(),
    val resultsVisible: Boolean = false,
    val selectedSort: StoreProductSortOption = StoreProductSortOption.RECENT,
    val paging: CatalogPagingState<StoreProduct> = CatalogPagingState(),
    val facetCounts: Map<String, Map<Int, Int>> = emptyMap(),
    val loadedFacetKeys: Map<String, String> = emptyMap()
) {
    val hasDraftFilters get() = draftSizes.isNotEmpty() || draftColors.isNotEmpty() || draftBrands.isNotEmpty()
    val hasAppliedFilters get() = appliedSizes.isNotEmpty() || appliedColors.isNotEmpty() || appliedBrands.isNotEmpty()
}

/** Catalog-root filter owner. Outlet filtering is owned exclusively by StoreOutletController. */
class StoreRootFilterOwner(
    private val api: StoreApiClient,
    private val scope: CoroutineScope,
    private val filterApi: StoreFilterApiClient = StoreFilterApiClient()
) {
    private val mutableState = MutableStateFlow(StoreRootFilterState())
    val state: StateFlow<StoreRootFilterState> = mutableState.asStateFlow()
    private var cachedOptions: StoreFilterOptions? = null
    private var loading = false
    private var activeFacetDimension = "sizes"
    private val facetGenerations = mutableMapOf<String, Int>()
    private val facetCache = mutableMapOf<String, Map<Int, Int>>()
    private val resultPaginator = CatalogPaginator<StoreProduct>(identity = { it.id })

    init {
        scope.launch {
            resultPaginator.state.collect { paging ->
                val current = mutableState.value
                if (current.resultsVisible) mutableState.value = current.copy(paging = paging)
            }
        }
    }

    private fun facetKey(dimension: String, s: StoreRootFilterState): String {
        val options = (s.optionsState as? StoreFilterOptionsState.Ready)?.options
        fun ids(names: Set<String>, values: List<StoreFilterOption>) = values.filter { it.name in names }.map { it.id }.distinct().sorted()
        val sizes = ids(s.draftSizes, options?.sizes.orEmpty())
        val colors = ids(s.draftColors, options?.colors.orEmpty())
        val brands = ids(s.draftBrands, options?.brands.orEmpty())
        return when (dimension) {
            "sizes" -> listOf("sizes", "CATALOG", colors.joinToString(","), brands.joinToString(",")).joinToString("|")
            "colors" -> listOf("colors", "CATALOG", sizes.joinToString(","), brands.joinToString(",")).joinToString("|")
            else -> listOf("brands", "CATALOG", sizes.joinToString(","), colors.joinToString(",")).joinToString("|")
        }
    }

    private fun requestFacet(dimension: String) {
        val before = mutableState.value
        val options = (before.optionsState as? StoreFilterOptionsState.Ready)?.options ?: return
        val key = facetKey(dimension, before)
        facetCache[key]?.let { counts ->
            mutableState.value = before.copy(
                facetCounts = before.facetCounts + (dimension to counts),
                loadedFacetKeys = before.loadedFacetKeys + (dimension to key)
            )
            return
        }
        val generation = (facetGenerations[dimension] ?: 0) + 1
        facetGenerations[dimension] = generation
        scope.launch {
            try {
                val response = filterApi.query(
                    scope = "catalog",
                    sizeIds = options.sizes.filter { it.name in before.draftSizes }.map { it.id }.distinct().sorted(),
                    colorIds = options.colors.filter { it.name in before.draftColors }.map { it.id }.distinct().sorted(),
                    brandIds = options.brands.filter { it.name in before.draftBrands }.map { it.id }.distinct().sorted(),
                    page = 1,
                    perPage = CatalogPaginator.PAGE_SIZE,
                    facet = dimension
                )
                val counts = response.facets[dimension].orEmpty().associate { it.termId to it.count }
                val current = mutableState.value
                if (facetGenerations[dimension] == generation && facetKey(dimension, current) == key) {
                    facetCache[key] = counts
                    mutableState.value = current.copy(
                        facetCounts = current.facetCounts + (dimension to counts),
                        loadedFacetKeys = current.loadedFacetKeys + (dimension to key)
                    )
                }
            } catch (_: Exception) {
                // Remains retryable. Never derive facet counts from paged products.
            }
        }
    }

    private fun invalidateFacets(vararg dimensions: String) {
        val current = mutableState.value
        val loaded = current.loadedFacetKeys.toMutableMap()
        dimensions.forEach { dimension ->
            facetGenerations[dimension] = (facetGenerations[dimension] ?: 0) + 1
            loaded.remove(dimension)
        }
        mutableState.value = current.copy(loadedFacetKeys = loaded)
        if (activeFacetDimension in dimensions) requestFacet(activeFacetDimension)
    }

    fun open(scope: StoreRootFilterScope) {
        require(scope == StoreRootFilterScope.CATALOG) { "Outlet filters are owned by StoreOutletController." }
        val old = mutableState.value
        activeFacetDimension = "sizes"
        mutableState.value = old.copy(
            scope = StoreRootFilterScope.CATALOG,
            dialogVisible = true,
            resultsVisible = false,
            draftSizes = old.appliedSizes,
            draftColors = old.appliedColors,
            draftBrands = old.appliedBrands
        )
        val cached = cachedOptions
        if (cached != null) {
            mutableState.value = mutableState.value.copy(optionsState = StoreFilterOptionsState.Ready(cached))
            requestFacet(activeFacetDimension)
        } else if (!loading) loadOptions()
    }

    fun retry() { if (cachedOptions == null && !loading) loadOptions() }

    private fun loadOptions() {
        loading = true
        mutableState.value = mutableState.value.copy(optionsState = StoreFilterOptionsState.Loading)
        scope.launch {
            try {
                val attrs = api.globalAttributes()
                val sizeAttr = attrs.firstOrNull { canonical(it.name) in setOf("tallas", "talla") }
                    ?: throw IllegalStateException("No se encontró el atributo global Tallas; atributos recibidos: ${attrs.map { it.name }}")
                val colorAttr = attrs.firstOrNull { canonical(it.name) in setOf("color", "colores") }
                    ?: throw IllegalStateException("No se encontró el atributo global Color; atributos recibidos: ${attrs.map { it.name }}")
                val sizes = api.globalAttributeTerms(sizeAttr.id).map { it.toOption(sizeAttr.taxonomy) }
                val colors = api.globalAttributeTerms(colorAttr.id).map { it.toOption(colorAttr.taxonomy) }
                val brands = api.productTags().map {
                    StoreFilterOption(
                        id = it.id,
                        name = normalizeFilterMetadataDisplayName(it.name),
                        slug = it.slug,
                        count = it.count
                    )
                }
                val options = StoreFilterOptions(sizes, colors, brands)
                cachedOptions = options
                mutableState.value = mutableState.value.copy(optionsState = StoreFilterOptionsState.Ready(options))
                if (mutableState.value.dialogVisible) requestFacet(activeFacetDimension)
            } catch (e: Exception) {
                mutableState.value = mutableState.value.copy(
                    optionsState = StoreFilterOptionsState.Error(e.message ?: "No se han podido cargar los filtros.")
                )
            } finally {
                loading = false
            }
        }
    }

    fun selectFacetDimension(dimension: String) { activeFacetDimension = dimension; requestFacet(dimension) }
    fun changeSizes(values: Set<String>) { mutableState.value = mutableState.value.copy(draftSizes = values); invalidateFacets("colors", "brands") }
    fun changeColors(values: Set<String>) { mutableState.value = mutableState.value.copy(draftColors = values); invalidateFacets("sizes", "brands") }
    fun changeBrands(values: Set<String>) { mutableState.value = mutableState.value.copy(draftBrands = values); invalidateFacets("sizes", "colors") }

    fun apply(): Boolean {
        val s = mutableState.value
        if (!s.hasDraftFilters) {
            mutableState.value = s.copy(
                dialogVisible = false,
                resultsVisible = false,
                appliedSizes = emptySet(),
                appliedColors = emptySet(),
                appliedBrands = emptySet(),
                paging = CatalogPagingState()
            )
            return false
        }
        mutableState.value = s.copy(
            dialogVisible = false,
            resultsVisible = true,
            appliedSizes = s.draftSizes,
            appliedColors = s.draftColors,
            appliedBrands = s.draftBrands
        )
        startResults()
        return true
    }

    fun cancel() {
        val s = mutableState.value
        mutableState.value = s.copy(
            dialogVisible = false,
            draftSizes = s.appliedSizes,
            draftColors = s.appliedColors,
            draftBrands = s.appliedBrands
        )
    }

    fun clearDraft() {
        mutableState.value = mutableState.value.copy(draftSizes = emptySet(), draftColors = emptySet(), draftBrands = emptySet())
        invalidateFacets("sizes", "colors", "brands")
    }

    fun removeAppliedSize(value: String) = updateApplied { it.copy(appliedSizes = it.appliedSizes - value, draftSizes = it.draftSizes - value) }
    fun removeAppliedColor(value: String) = updateApplied { it.copy(appliedColors = it.appliedColors - value, draftColors = it.draftColors - value) }
    fun removeAppliedBrand(value: String) = updateApplied { it.copy(appliedBrands = it.appliedBrands - value, draftBrands = it.draftBrands - value) }
    fun clearApplied() = updateApplied {
        it.copy(
            appliedSizes = emptySet(), appliedColors = emptySet(), appliedBrands = emptySet(),
            draftSizes = emptySet(), draftColors = emptySet(), draftBrands = emptySet()
        )
    }

    private fun updateApplied(transform: (StoreRootFilterState) -> StoreRootFilterState) {
        val next = transform(mutableState.value)
        mutableState.value = if (next.hasAppliedFilters) next else next.copy(resultsVisible = false, dialogVisible = false, paging = CatalogPagingState())
        if (mutableState.value.resultsVisible) startResults()
    }

    fun backFromResults() {
        val s = mutableState.value
        mutableState.value = s.copy(
            resultsVisible = false,
            dialogVisible = false,
            paging = CatalogPagingState(),
            draftSizes = s.appliedSizes,
            draftColors = s.appliedColors,
            draftBrands = s.appliedBrands
        )
    }

    fun selectSort(sort: StoreProductSortOption) {
        val current = mutableState.value
        if (current.selectedSort == sort) return
        mutableState.value = current.copy(selectedSort = sort)
        if (current.resultsVisible) startResults()
    }

    fun onVisibleRangeChanged(lastVisibleIndex: Int) {
        val paging = mutableState.value.paging
        if (paging.hasMore && !paging.isInitialLoading && !paging.isAppending && paging.appendError == null &&
            lastVisibleIndex >= paging.items.size - CatalogPaginator.PREFETCH_DISTANCE
        ) {
            scope.launch { resultPaginator.loadNext { page, perPage -> loadRootPage(page, perPage, mutableState.value.selectedSort) } }
        }
    }

    fun retryInitial() = startResults()

    fun retryAppend() {
        val paging = mutableState.value.paging
        if (paging.currentPage <= 0 || paging.isAppending || !paging.hasMore) return
        scope.launch { resultPaginator.loadNext { page, perPage -> loadRootPage(page, perPage, mutableState.value.selectedSort) } }
    }

    fun queryAttributeFilters(): List<StoreProductAttributeFilter> {
        val s = mutableState.value
        val options = (s.optionsState as? StoreFilterOptionsState.Ready)?.options ?: return emptyList()
        return listOf(
            options.sizes.filter { it.name in s.appliedSizes },
            options.colors.filter { it.name in s.appliedColors }
        ).mapNotNull { selected ->
            val taxonomy = selected.firstOrNull()?.taxonomy
            val ids = selected.map { it.id }.distinct()
            if (taxonomy.isNullOrBlank() || ids.isEmpty()) null else StoreProductAttributeFilter(taxonomy, ids)
        }
    }

    fun queryTagIds(): List<Int> = (mutableState.value.optionsState as? StoreFilterOptionsState.Ready)?.options?.brands.orEmpty()
        .filter { it.name in mutableState.value.appliedBrands }.map { it.id }.distinct()

    suspend fun loadRootPage(page: Int, perPage: Int, sort: StoreProductSortOption): CatalogPage<StoreProduct> {
        require(page >= 1 && perPage == CatalogPaginator.PAGE_SIZE) { "Root filter pages must use the canonical page size." }
        val s = mutableState.value
        val options = (s.optionsState as? StoreFilterOptionsState.Ready)?.options
            ?: throw IllegalStateException("Filter metadata is not ready.")
        val response = filterApi.query(
            scope = "catalog",
            sizeIds = options.sizes.filter { it.name in s.appliedSizes }.map { it.id }.distinct(),
            colorIds = options.colors.filter { it.name in s.appliedColors }.map { it.id }.distinct(),
            brandIds = options.brands.filter { it.name in s.appliedBrands }.map { it.id }.distinct(),
            page = page,
            perPage = perPage,
            sort = sort.toBackendSort()
        )
        val products = if (response.productIds.isEmpty()) emptyList() else api.productsByIds(response.productIds)
        return CatalogPage(products, response.hasMore, response.total)
    }

    fun queryKey(sort: StoreProductSortOption = mutableState.value.selectedSort): String {
        val s = mutableState.value
        return "root-filter:CATALOG:${s.appliedSizes.sorted()}:${s.appliedColors.sorted()}:${s.appliedBrands.sorted()}:sort=${sort.toBackendSort()}"
    }

    private fun startResults() {
        val current = mutableState.value
        if (!current.resultsVisible || !current.hasAppliedFilters) return
        val sort = current.selectedSort
        val key = queryKey(sort)
        scope.launch { resultPaginator.start(key) { page, perPage -> loadRootPage(page, perPage, sort) } }
    }

    private fun canonical(value: String) = value.lowercase().trim()
        .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
        .replace("à", "a").replace("è", "e").replace("ì", "i").replace("ò", "o").replace("ù", "u")

    private fun StoreGlobalAttributeTerm.toOption(taxonomy: String) = StoreFilterOption(
        id = id,
        name = normalizeFilterMetadataDisplayName(name),
        slug = slug,
        taxonomy = taxonomy,
        count = count
    )
}

@Composable
fun StoreRootFilterDialog(owner: StoreRootFilterOwner) {
    val state by owner.state.collectAsState()
    if (!state.dialogVisible) return
    when (val options = state.optionsState) {
        StoreFilterOptionsState.Idle, StoreFilterOptionsState.Loading -> AlertDialog(
            onDismissRequest = owner::cancel,
            title = { Text("Filtros") },
            text = { Text("Cargando opciones de filtros…") },
            confirmButton = {},
            dismissButton = { TextButton(onClick = owner::cancel) { Text("Cancelar") } }
        )
        is StoreFilterOptionsState.Error -> AlertDialog(
            onDismissRequest = owner::cancel,
            title = { Text("No se han podido cargar los filtros") },
            text = { Text(options.message) },
            confirmButton = { TextButton(onClick = owner::retry) { Text("Reintentar") } },
            dismissButton = { TextButton(onClick = owner::cancel) { Text("Cancelar") } }
        )
        is StoreFilterOptionsState.Ready -> StoreCatalogFilterDialog(
            sizes = options.options.sizes.map { it.name },
            colors = options.options.colors.map { it.name },
            brands = options.options.brands.map { it.name },
            selectedSizes = state.draftSizes,
            selectedColors = state.draftColors,
            selectedBrands = state.draftBrands,
            enabledSizes = options.options.sizes.filter {
                (!state.loadedFacetKeys.containsKey("sizes") || (state.facetCounts["sizes"]?.get(it.id) ?: 0) > 0) || it.name in state.draftSizes
            }.map { it.name }.toSet(),
            enabledColors = options.options.colors.filter {
                (!state.loadedFacetKeys.containsKey("colors") || (state.facetCounts["colors"]?.get(it.id) ?: 0) > 0) || it.name in state.draftColors
            }.map { it.name }.toSet(),
            enabledBrands = options.options.brands.filter {
                (!state.loadedFacetKeys.containsKey("brands") || (state.facetCounts["brands"]?.get(it.id) ?: 0) > 0) || it.name in state.draftBrands
            }.map { it.name }.toSet(),
            onDimensionSelected = owner::selectFacetDimension,
            onSizesChanged = owner::changeSizes,
            onColorsChanged = owner::changeColors,
            onBrandsChanged = owner::changeBrands,
            onApply = { owner.apply() },
            onClear = owner::clearDraft,
            onDismiss = owner::cancel,
            onCancel = owner::cancel
        )
    }
}

@Composable
fun StoreRootFilterResultsScreen(owner: StoreRootFilterOwner, padding: PaddingValues, onProduct: (StoreProduct) -> Unit) {
    val state by owner.state.collectAsState()
    val rootScope = state.scope ?: return
    val pageState = state.paging
    val grid = rememberLazyGridState()
    val activeChips = buildList {
        state.appliedSizes.sorted().forEach { add(StoreActiveFilterChipUi("size:$it", "Talla $it")) }
        state.appliedColors.sorted().forEach { add(StoreActiveFilterChipUi("color:$it", it)) }
        state.appliedBrands.sorted().forEach { add(StoreActiveFilterChipUi("brand:$it", it)) }
    }
    val displayed = pageState.items
    val listState = StoreProductListUiState(
        title = rootScope.title,
        items = displayed.map { product ->
            StoreProductItemUi(
                product.id,
                normalizeWooPlainText(product.name),
                product.images.firstOrNull()?.src,
                formatStorePrice(product.prices.price, product.prices.currencyMinorUnit, product.prices.currencySymbol),
                formatStorePrice(product.prices.regularPrice, product.prices.currencyMinorUnit, product.prices.currencySymbol),
                product.prices.price.toLongOrNull()?.let { it > 0L } == true,
                product.onSale
            )
        },
        selectedSort = state.selectedSort,
        availableSortOptions = StoreProductSortOption.values().toList(),
        activeFilterCount = state.appliedSizes.size + state.appliedColors.size + state.appliedBrands.size,
        activeFilterChips = activeChips,
        isInitialLoading = pageState.isInitialLoading,
        isLoadingMore = pageState.isAppending,
        isEmpty = pageState.items.isEmpty() && !pageState.isInitialLoading && !pageState.hasMore,
        emptyMessage = "No hay productos con estos filtros.",
        initialErrorMessage = pageState.initialError?.message,
        appendErrorMessage = pageState.appendError?.message,
        appendRetryLabel = "Reintentar",
        canLoadMore = pageState.hasMore
    )
    LaunchedEffect(grid, pageState.items.size, pageState.hasMore, pageState.isInitialLoading, pageState.isAppending) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }.collect { owner.onVisibleRangeChanged(it) }
    }
    StoreProductListScreen(
        state = listState,
        gridState = grid,
        modifier = Modifier.fillMaxSize().padding(padding),
        onBack = owner::backFromResults,
        onProductSelected = { id -> pageState.items.firstOrNull { it.id == id }?.let(onProduct) },
        onSortSelected = owner::selectSort,
        onFilterClick = { owner.open(StoreRootFilterScope.CATALOG) },
        onRemoveFilter = { id ->
            when {
                id.startsWith("size:") -> owner.removeAppliedSize(id.removePrefix("size:"))
                id.startsWith("color:") -> owner.removeAppliedColor(id.removePrefix("color:"))
                id.startsWith("brand:") -> owner.removeAppliedBrand(id.removePrefix("brand:"))
            }
        },
        onClearFilters = owner::clearApplied,
        onRetryInitial = owner::retryInitial,
        onRetryAppend = owner::retryAppend,
        imageContent = { item, imageModifier ->
            val product = pageState.items.firstOrNull { it.id == item.id }
            RemoteStoreImage(
                product?.images?.firstOrNull()?.src.orEmpty(),
                item.nameText,
                imageModifier,
                androidx.compose.ui.layout.ContentScale.Crop
            )
        }
    )
}
