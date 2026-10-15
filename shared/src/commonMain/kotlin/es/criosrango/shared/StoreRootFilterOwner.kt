package es.criosrango.shared

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.model.StoreGlobalAttributeTerm
import es.criosrango.shared.model.StoreProduct
import es.criosrango.shared.model.StoreProductAttributeFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val scope: StoreRootFilterScope? = null, val dialogVisible: Boolean = false,
    val optionsState: StoreFilterOptionsState = StoreFilterOptionsState.Idle,
    val draftSizes: Set<String> = emptySet(), val draftColors: Set<String> = emptySet(), val draftBrands: Set<String> = emptySet(),
    val appliedSizes: Set<String> = emptySet(), val appliedColors: Set<String> = emptySet(), val appliedBrands: Set<String> = emptySet(),
    val resultsVisible: Boolean = false
) {
    val hasDraftFilters get() = draftSizes.isNotEmpty() || draftColors.isNotEmpty() || draftBrands.isNotEmpty()
    val hasAppliedFilters get() = appliedSizes.isNotEmpty() || appliedColors.isNotEmpty() || appliedBrands.isNotEmpty()
}

class StoreRootFilterOwner(private val api: StoreApiClient, private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(StoreRootFilterState())
    val state: StateFlow<StoreRootFilterState> = mutableState.asStateFlow()
    private var cachedOptions: StoreFilterOptions? = null
    private var loading = false
    fun open(scope: StoreRootFilterScope) {
        val old = mutableState.value
        val sameScope = old.scope == scope
        val appliedSizes = if (sameScope) old.appliedSizes else emptySet()
        val appliedColors = if (sameScope) old.appliedColors else emptySet()
        val appliedBrands = if (sameScope) old.appliedBrands else emptySet()
        mutableState.value = old.copy(scope = scope, dialogVisible = true, resultsVisible = false,
            draftSizes = appliedSizes, draftColors = appliedColors, draftBrands = appliedBrands,
            appliedSizes = appliedSizes, appliedColors = appliedColors, appliedBrands = appliedBrands)
        val cached = cachedOptions
        if (cached != null) {
            mutableState.value = mutableState.value.copy(optionsState = StoreFilterOptionsState.Ready(cached))
        } else if (!loading) {
            loadOptions()
        }
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
                val sizes = sizeAttr?.let { a ->
                    api.globalAttributeTerms(a.id).map { it.toOption(a.taxonomy) }
                }.orEmpty()
                val colors = colorAttr?.let { a ->
                    api.globalAttributeTerms(a.id).map { it.toOption(a.taxonomy) }
                }.orEmpty()
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
            } catch (e: Exception) {
                mutableState.value = mutableState.value.copy(optionsState = StoreFilterOptionsState.Error(e.message ?: "No se han podido cargar los filtros."))
            } finally { loading = false }
        }
    }
    fun changeSizes(v: Set<String>) { mutableState.value = mutableState.value.copy(draftSizes = v) }
    fun changeColors(v: Set<String>) { mutableState.value = mutableState.value.copy(draftColors = v) }
    fun changeBrands(v: Set<String>) { mutableState.value = mutableState.value.copy(draftBrands = v) }
    fun apply(): Boolean {
        val s = mutableState.value
        if (!s.hasDraftFilters) {
            mutableState.value = s.copy(dialogVisible = false, resultsVisible = false, appliedSizes = emptySet(), appliedColors = emptySet(), appliedBrands = emptySet())
            return false
        }
        mutableState.value = s.copy(dialogVisible = false, resultsVisible = true, appliedSizes = s.draftSizes, appliedColors = s.draftColors, appliedBrands = s.draftBrands)
        return true
    }
    fun cancel() { val s = mutableState.value; mutableState.value = s.copy(dialogVisible = false, draftSizes = s.appliedSizes, draftColors = s.appliedColors, draftBrands = s.appliedBrands) }
    fun clearDraft() { mutableState.value = mutableState.value.copy(draftSizes = emptySet(), draftColors = emptySet(), draftBrands = emptySet()) }
    fun removeAppliedSize(v: String) = updateApplied { it.copy(appliedSizes = it.appliedSizes - v, draftSizes = it.draftSizes - v) }
    fun removeAppliedColor(v: String) = updateApplied { it.copy(appliedColors = it.appliedColors - v, draftColors = it.draftColors - v) }
    fun removeAppliedBrand(v: String) = updateApplied { it.copy(appliedBrands = it.appliedBrands - v, draftBrands = it.draftBrands - v) }
    fun clearApplied() = updateApplied { it.copy(appliedSizes = emptySet(), appliedColors = emptySet(), appliedBrands = emptySet(), draftSizes = emptySet(), draftColors = emptySet(), draftBrands = emptySet()) }
    private fun updateApplied(f: (StoreRootFilterState) -> StoreRootFilterState) { val n = f(mutableState.value); mutableState.value = if (n.hasAppliedFilters) n else n.copy(resultsVisible = false, dialogVisible = false) }
    fun backFromResults() { val s = mutableState.value; mutableState.value = s.copy(resultsVisible = false, dialogVisible = false, draftSizes = s.appliedSizes, draftColors = s.appliedColors, draftBrands = s.appliedBrands) }
    fun queryAttributeFilters(): List<StoreProductAttributeFilter> {
        val s = mutableState.value
        val o = (s.optionsState as? StoreFilterOptionsState.Ready)?.options ?: return emptyList()
        return listOf(o.sizes.filter { it.name in s.appliedSizes }, o.colors.filter { it.name in s.appliedColors }).mapNotNull { selected ->
            val taxonomy = selected.firstOrNull()?.taxonomy
            val ids = selected.map { it.id }.distinct()
            if (taxonomy.isNullOrBlank() || ids.isEmpty()) null else StoreProductAttributeFilter(taxonomy, ids)
        }
    }
    fun queryTagIds(): List<Int> = (mutableState.value.optionsState as? StoreFilterOptionsState.Ready)?.options?.brands.orEmpty()
        .filter { it.name in mutableState.value.appliedBrands }.map { it.id }.distinct()
    suspend fun loadProducts(perPage: Int, page: Int): List<StoreProduct> {
        val s = mutableState.value
        return api.products(perPage = perPage, page = page, category = s.scope?.categoryQueryId, attributeFilters = queryAttributeFilters(), tagIds = queryTagIds())
    }
    fun queryKey(): String { val s = mutableState.value; return "root-filter:${s.scope?.name}:${s.appliedSizes.sorted()}:${s.appliedColors.sorted()}:${s.appliedBrands.sorted()}" }
    private fun canonical(v: String) = v.lowercase().trim().replace("á","a").replace("é","e").replace("í","i").replace("ó","o").replace("ú","u").replace("à","a").replace("è","e").replace("ì","i").replace("ò","o").replace("ù","u")
    private fun StoreGlobalAttributeTerm.toOption(taxonomy: String) =
        StoreFilterOption(
            id = id,
            name = normalizeFilterMetadataDisplayName(name),
            slug = slug,
            taxonomy = taxonomy,
            count = count
        )
}

@Composable
fun StoreRootFilterDialog(owner: StoreRootFilterOwner) {
    val s by owner.state.collectAsState()
    if (!s.dialogVisible) return
    when (val o = s.optionsState) {
        StoreFilterOptionsState.Idle, StoreFilterOptionsState.Loading -> AlertDialog(onDismissRequest = owner::cancel, title = { Text("Filtros") }, text = { Text("Cargando opciones de filtros…") }, confirmButton = {}, dismissButton = { TextButton(onClick = owner::cancel) { Text("Cancelar") } })
        is StoreFilterOptionsState.Error -> AlertDialog(onDismissRequest = owner::cancel, title = { Text("No se han podido cargar los filtros") }, text = { Text(o.message) }, confirmButton = { TextButton(onClick = owner::retry) { Text("Reintentar") } }, dismissButton = { TextButton(onClick = owner::cancel) { Text("Cancelar") } })
        is StoreFilterOptionsState.Ready -> StoreCatalogFilterDialog(
            sizes = o.options.sizes.map { it.name }, colors = o.options.colors.map { it.name }, brands = o.options.brands.map { it.name },
            selectedSizes = s.draftSizes, selectedColors = s.draftColors, selectedBrands = s.draftBrands,
            enabledSizes = o.options.sizes.map { it.name }.toSet(), enabledColors = o.options.colors.map { it.name }.toSet(), enabledBrands = o.options.brands.map { it.name }.toSet(),
            onSizesChanged = owner::changeSizes, onColorsChanged = owner::changeColors, onBrandsChanged = owner::changeBrands,
            onApply = { owner.apply() }, onClear = owner::clearDraft, onDismiss = owner::cancel, onCancel = owner::cancel
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoreRootFilterResultsScreen(owner: StoreRootFilterOwner, padding: PaddingValues, onProduct: (StoreProduct) -> Unit) {
    val s by owner.state.collectAsState()
    val rootScope = s.scope ?: return
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
    val paginator = remember(owner.queryKey()) { CatalogPaginatorStore<StoreProduct>(coroutineScope, pageSize = CatalogPaginator.PAGE_SIZE) { it.id } }
    val pageState by paginator.state.collectAsState()
    val grid = rememberLazyGridState()
    var sort by remember(owner.queryKey()) { mutableStateOf(StoreProductSortOption.RECENT) }
    val activeChips = buildList {
        s.appliedSizes.sorted().forEach { add(StoreActiveFilterChipUi("size:$it", "Talla $it")) }
        s.appliedColors.sorted().forEach { add(StoreActiveFilterChipUi("color:$it", it)) }
        s.appliedBrands.sorted().forEach { add(StoreActiveFilterChipUi("brand:$it", it)) }
    }
    val displayed = when (sort) {
        StoreProductSortOption.RECENT -> pageState.items
        StoreProductSortOption.PRICE_ASC -> pageState.items.sortedBy { it.prices.price.toLongOrNull() ?: Long.MAX_VALUE }
        StoreProductSortOption.PRICE_DESC -> pageState.items.sortedByDescending { it.prices.price.toLongOrNull() ?: Long.MIN_VALUE }
        StoreProductSortOption.NAME_ASC -> pageState.items.sortedBy { it.name.lowercase() }
    }
    val listState = StoreProductListUiState(
        title = rootScope.title,
        items = displayed.map { p -> StoreProductItemUi(p.id, normalizeWooPlainText(p.name), p.images.firstOrNull()?.src,
            formatStorePrice(p.prices.price, p.prices.currencyMinorUnit, p.prices.currencySymbol),
            formatStorePrice(p.prices.regularPrice, p.prices.currencyMinorUnit, p.prices.currencySymbol),
            p.prices.price.toLongOrNull()?.let { it > 0L } == true, p.onSale) },
        selectedSort = sort, availableSortOptions = StoreProductSortOption.values().toList(),
        activeFilterCount = s.appliedSizes.size + s.appliedColors.size + s.appliedBrands.size,
        activeFilterChips = activeChips, isInitialLoading = pageState.isInitialLoading, isLoadingMore = pageState.isAppending,
        isEmpty = pageState.items.isEmpty() && !pageState.isInitialLoading && !pageState.hasMore,
        emptyMessage = "No hay productos con estos filtros.",
        initialErrorMessage = pageState.initialError?.message, appendErrorMessage = pageState.appendError?.message,
        appendRetryLabel = "Reintentar", canLoadMore = pageState.hasMore
    )
    LaunchedEffect(owner.queryKey()) { paginator.start(owner.queryKey()) { p, n -> val items = owner.loadProducts(p, n); CatalogPage(items, items.size >= n) } }
    LaunchedEffect(grid, pageState.items.size, pageState.hasMore, pageState.isInitialLoading, pageState.isAppending) {
        androidx.compose.runtime.snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }.collect { last ->
            if (pageState.hasMore && !pageState.isInitialLoading && !pageState.isAppending && last >= pageState.items.size - CatalogPaginator.PREFETCH_DISTANCE) {
                paginator.loadNext { p, n -> val items = owner.loadProducts(p, n); CatalogPage(items, items.size >= n) }
            }
        }
    }
    StoreProductListScreen(
        state = listState, gridState = grid, modifier = Modifier.fillMaxSize().padding(padding),
        onBack = owner::backFromResults,
        onProductSelected = { id -> pageState.items.firstOrNull { it.id == id }?.let(onProduct) },
        onSortSelected = { sort = it },
        onFilterClick = { owner.open(rootScope) },
        onRemoveFilter = { id -> when {
            id.startsWith("size:") -> owner.removeAppliedSize(id.removePrefix("size:"))
            id.startsWith("color:") -> owner.removeAppliedColor(id.removePrefix("color:"))
            id.startsWith("brand:") -> owner.removeAppliedBrand(id.removePrefix("brand:"))
        } },
        onClearFilters = owner::clearApplied,
        onRetryInitial = { paginator.start(owner.queryKey()) { p, n -> val items = owner.loadProducts(p, n); CatalogPage(items, items.size >= n) } },
        onRetryAppend = { paginator.loadNext { p, n -> val items = owner.loadProducts(p, n); CatalogPage(items, items.size >= n) } },
        imageContent = { item, imageModifier ->
            val product = pageState.items.firstOrNull { it.id == item.id }
            RemoteStoreImage(product?.images?.firstOrNull()?.src.orEmpty(), item.nameText, imageModifier, androidx.compose.ui.layout.ContentScale.Crop)
        }
    )
}
