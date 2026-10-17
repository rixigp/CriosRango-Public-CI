package es.criosrango.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.model.StoreGlobalBrand
import es.criosrango.shared.model.StoreProduct
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private data class StoreBrandPreferences(
    val sort: StoreProductSortOption = StoreProductSortOption.RECENT,
    val sizes: Set<String> = emptySet(),
    val colors: Set<String> = emptySet()
)

data class StoreBrandsState(
    val brands: List<StoreGlobalBrand> = emptyList(),
    val brandsLoading: Boolean = false,
    val brandsError: String? = null,
    val selectedBrand: StoreGlobalBrand? = null,
    val paging: CatalogPagingState<StoreProduct> = CatalogPagingState(),
    val sort: StoreProductSortOption = StoreProductSortOption.RECENT,
    val selectedSizes: Set<String> = emptySet(),
    val selectedColors: Set<String> = emptySet(),
    val filterDialogVisible: Boolean = false
)

/** Single functional owner for the WooCommerce product_brand flow on every platform. */
class StoreBrandsController(
    private val api: StoreApiClient,
    private val scope: CoroutineScope
) {
    companion object { const val PAGE_SIZE = CatalogPaginator.PAGE_SIZE }
    private val _state = MutableStateFlow(StoreBrandsState())
    val state: StateFlow<StoreBrandsState> = _state.asStateFlow()
    private val paginators = mutableMapOf<String, CatalogPaginator<StoreProduct>>()
    private val brandCache = mutableMapOf<String, CatalogPagingState<StoreProduct>>()
    private val brandPreferences = mutableMapOf<String, StoreBrandPreferences>()
    private var listGeneration = 0L
    private var brandSyncJob: Job? = null
    private var loadingBrands = false

    init { loadBrands() }

    fun loadBrands() {
        if (loadingBrands || (_state.value.brands.isNotEmpty() && _state.value.brandsError == null)) return
        loadingBrands = true
        val generation = ++listGeneration
        _state.value = _state.value.copy(brandsLoading = true, brandsError = null)
        scope.launch {
            runCatching {
                val all = mutableListOf<StoreGlobalBrand>()
                var page = 1
                while (true) {
                    val batch = api.productBrands(perPage = 100, page = page, hideEmpty = true)
                    all += batch
                    if (batch.size < 100) break
                    page++
                }
                all.distinctBy { it.id }
                    .filter { it.id > 0 && it.count > 0 && it.name.isNotBlank() && it.slug.isNotBlank() }
                    .sortedBy { it.name.trim().lowercase() }
            }.onSuccess { brands ->
                if (generation == listGeneration) _state.value = _state.value.copy(brands = brands, brandsLoading = false, brandsError = null)
            }.onFailure { error ->
                if (generation == listGeneration) _state.value = _state.value.copy(brandsLoading = false, brandsError = error.message ?: "No se han podido cargar las marcas.")
            }
            if (generation == listGeneration) loadingBrands = false
        }
    }

    fun selectBrand(brand: StoreGlobalBrand) {
        val key = brand.slug.trim().lowercase()
        if (key.isBlank()) return
        brandSyncJob?.cancel()
        val paginator = paginators.getOrPut(key) { CatalogPaginator(PAGE_SIZE) { product -> product.id } }
        val preferences = brandPreferences[key] ?: StoreBrandPreferences()
        _state.value = _state.value.copy(
            selectedBrand = brand, paging = brandCache[key] ?: CatalogPagingState(),
            sort = preferences.sort, selectedSizes = preferences.sizes, selectedColors = preferences.colors,
            filterDialogVisible = false
        )
        brandSyncJob = scope.launch {
            paginator.state.collect { pageState ->
                brandCache[key] = pageState
                if (_state.value.selectedBrand?.slug?.trim()?.lowercase() == key) _state.value = _state.value.copy(paging = pageState)
            }
        }
        if (paginator.state.value.currentPage == 0 && !paginator.state.value.isInitialLoading) {
            scope.launch { paginator.start(key) { page, perPage ->
                val items = api.products(brand = brand.slug, page = page, perPage = perPage, orderBy = "date", order = "desc")
                CatalogPage(items, hasMore = items.size >= perPage)
            } }
        }
    }

    fun selectBrandBySlug(slug: String) {
        val key = slug.trim().lowercase()
        _state.value.brands.firstOrNull { it.slug.trim().lowercase() == key }?.let(::selectBrand)
    }

    fun backToBrands() {
        brandSyncJob?.cancel()
        _state.value = _state.value.copy(selectedBrand = null, paging = CatalogPagingState(), filterDialogVisible = false)
    }

    fun loadNextPage() {
        val brand = _state.value.selectedBrand ?: return
        val paginator = paginators[brand.slug.trim().lowercase()] ?: return
        scope.launch { paginator.loadNext { page, perPage ->
            val items = api.products(brand = brand.slug, page = page, perPage = perPage, orderBy = "date", order = "desc")
            CatalogPage(items, hasMore = items.size >= perPage)
        } }
    }

    fun retryBrands() { _state.value = _state.value.copy(brandsError = null); loadingBrands = false; loadBrands() }
    fun retryProducts() {
        val brand = _state.value.selectedBrand ?: return
        val key = brand.slug.trim().lowercase()
        val paginator = paginators.getOrPut(key) { CatalogPaginator(PAGE_SIZE) { it.id } }
        scope.launch { paginator.start(key) { page, perPage ->
            val items = api.products(brand = brand.slug, page = page, perPage = perPage, orderBy = "date", order = "desc")
            CatalogPage(items, hasMore = items.size >= perPage)
        } }
    }
    private fun updateActiveBrandPreferences(update: (StoreBrandPreferences) -> StoreBrandPreferences) {
        val key = _state.value.selectedBrand?.slug?.trim()?.lowercase() ?: return
        brandPreferences[key] = update(brandPreferences[key] ?: StoreBrandPreferences())
    }
    fun setSort(sort: StoreProductSortOption) { updateActiveBrandPreferences { it.copy(sort = sort) }; _state.value = _state.value.copy(sort = sort) }
    fun openFilters() { _state.value = _state.value.copy(filterDialogVisible = true) }
    fun closeFilters() { _state.value = _state.value.copy(filterDialogVisible = false) }
    fun setSizes(values: Set<String>) { updateActiveBrandPreferences { it.copy(sizes = values) }; _state.value = _state.value.copy(selectedSizes = values) }
    fun setColors(values: Set<String>) { updateActiveBrandPreferences { it.copy(colors = values) }; _state.value = _state.value.copy(selectedColors = values) }
    fun clearFilters() { setSizes(emptySet()); setColors(emptySet()) }
}

private fun decodeBrandHtmlEntities(value: String): String = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|amp|quot|apos|lt|gt|nbsp);").replace(value) { match ->
    when (val entity = match.groupValues[1]) {
        "amp" -> "&"
        "quot" -> "\\\""
        "apos" -> "'"
        "lt" -> "<"
        "gt" -> ">"
        "nbsp" -> "\\u00A0"
        else -> {
            val codePoint = when {
                entity.startsWith("#x", ignoreCase = true) -> entity.drop(2).toIntOrNull(16)
                entity.startsWith("#") -> entity.drop(1).toIntOrNull()
                else -> null
            }
            when {
                codePoint == null || codePoint !in 0..0x10FFFF || codePoint in 0xD800..0xDFFF -> match.value
                codePoint <= 0xFFFF -> codePoint.toChar().toString()
                else -> {
                    val adjusted = codePoint - 0x10000
                    (0xD800 + (adjusted shr 10)).toChar().toString() + (0xDC00 + (adjusted and 0x3FF)).toChar()
                }
            }
        }
    }
}

private fun StoreProduct.brandFilterValues(size: Boolean): Set<String> =
    attributes.filter { attribute ->
        val key = "${attribute.name} ${attribute.taxonomy.orEmpty()}".lowercase()
        if (size) key.contains("size") || key.contains("talla") else key.contains("color")
    }.flatMap { it.terms.map { term -> term.name.trim() } }.filter { it.isNotBlank() }.toSet()

private fun StoreProduct.brandPriceText(value: String, minorUnit: Int, symbol: String): String {
    val digits = minorUnit.coerceIn(0, 6)
    val raw = value.toLongOrNull() ?: return ""
    val divisor = when (digits) { 0 -> 1L; 1 -> 10L; 2 -> 100L; 3 -> 1000L; 4 -> 10000L; 5 -> 100000L; else -> 1000000L }
    return "${raw / divisor},${(raw % divisor).toString().padStart(digits, '0')} ${symbol}".trim()
}

@Composable
fun StoreBrandsScreen(
    controller: StoreBrandsController,
    padding: PaddingValues,
    onBack: () -> Unit,
    onProduct: (StoreProduct) -> Unit,
    onBrandProductsBack: (() -> Unit)? = null
) {
    val state by controller.state.collectAsState()
    val selected = state.selectedBrand
    if (selected == null) {
        Column(Modifier.fillMaxSize().padding(padding)) {
            StoreCatalogHeader("Nuestras marcas", true, onBack)
            when {
                state.brandsLoading && state.brands.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.brandsError != null && state.brands.isEmpty() -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(state.brandsError!!)
                    TextButton(onClick = controller::retryBrands) { Text("Reintentar") }
                }
                state.brands.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No hay marcas disponibles") }
                else -> androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.brands, key = { it.id }) { brand ->
                        Column(
                            Modifier.fillMaxWidth().height(104.dp).clip(RoundedCornerShape(12.dp)).background(Color.White, RoundedCornerShape(12.dp)).clickable { controller.selectBrand(brand) }.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                        ) {
                            if (!brand.imageUrl.isNullOrBlank()) {
                                RemoteStoreImage(brand.imageUrl, null, Modifier.fillMaxWidth().height(64.dp), ContentScale.Fit)
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        decodeBrandHtmlEntities(brand.name).trim(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        val gridState = rememberLazyGridState()
        val products = remember(state.paging.items, state.sort, state.selectedSizes, state.selectedColors) {
            state.paging.items.filter { product ->
                (state.selectedSizes.isEmpty() || product.brandFilterValues(true).any { it in state.selectedSizes }) &&
                    (state.selectedColors.isEmpty() || product.brandFilterValues(false).any { it in state.selectedColors })
            }.let { filtered ->
                when (state.sort) {
                    StoreProductSortOption.RECENT -> filtered
                    StoreProductSortOption.PRICE_ASC -> filtered.sortedBy { it.prices.price.toLongOrNull() ?: Long.MAX_VALUE }
                    StoreProductSortOption.PRICE_DESC -> filtered.sortedByDescending { it.prices.price.toLongOrNull() ?: Long.MIN_VALUE }
                    StoreProductSortOption.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
                }
            }
        }
        val sizes = state.paging.items.flatMap { it.brandFilterValues(true) }.distinct().sortedWith(compareBy({ storeCatalogFilterSizeSortKey(it) }, { it }))
        val colors = state.paging.items.flatMap { it.brandFilterValues(false) }.distinct().sorted()
        val uiItems = products.map { product ->
            StoreProductItemUi(
                id = product.id, nameText = product.name,
                imageIdentity = product.images.firstOrNull()?.src?.takeIf { it.isNotBlank() } ?: product.images.firstOrNull()?.thumbnail,
                displayPriceText = product.brandPriceText(product.prices.price, product.prices.currencyMinorUnit, product.prices.currencySymbol),
                regularPriceText = product.brandPriceText(product.prices.regularPrice, product.prices.currencyMinorUnit, product.prices.currencySymbol),
                showPrice = product.hasDisplayablePrice, isOnSale = product.onSale
            )
        }
        val uiState = StoreProductListUiState(
            title = selected.name, items = uiItems, selectedSort = state.sort,
            availableSortOptions = StoreProductSortOption.values().toList(),
            activeFilterCount = state.selectedSizes.size + state.selectedColors.size,
            activeFilterChips = state.selectedSizes.map { StoreActiveFilterChipUi("size:$it", "Talla: $it") } +
                state.selectedColors.map { StoreActiveFilterChipUi("color:$it", "Color: $it") },
            isInitialLoading = state.paging.isInitialLoading, isLoadingMore = state.paging.isAppending,
            isEmpty = !state.paging.isInitialLoading && state.paging.initialError == null && products.isEmpty(),
            emptyMessage = if (state.paging.items.isEmpty()) "No hay productos disponibles de esta marca" else "No hay productos con estos filtros",
            initialErrorMessage = state.paging.initialError?.message, appendErrorMessage = state.paging.appendError?.message,
            appendRetryLabel = "Reintentar", canLoadMore = state.paging.hasMore
        )
        LaunchedEffect(selected.slug, state.paging.currentPage, state.paging.items.size, products.size) {
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }.collect { last ->
                if (last >= products.size - CatalogPaginator.PREFETCH_DISTANCE && state.paging.hasMore && !state.paging.isAppending && !state.paging.isInitialLoading && state.paging.currentPage > 0) controller.loadNextPage()
            }
        }
        StoreProductListScreen(
            state = uiState, gridState = gridState, modifier = Modifier.fillMaxSize().padding(padding),
            onBack = { (onBrandProductsBack ?: controller::backToBrands).invoke() },
            onProductSelected = { id -> products.firstOrNull { it.id == id }?.let(onProduct) },
            onSortSelected = controller::setSort, onFilterClick = controller::openFilters,
            onRemoveFilter = { id -> if (id.startsWith("size:")) controller.setSizes(state.selectedSizes - id.removePrefix("size:")) else controller.setColors(state.selectedColors - id.removePrefix("color:")) },
            onClearFilters = controller::clearFilters, onRetryInitial = controller::retryProducts,
            onRetryAppend = controller::loadNextPage,
            imageContent = { item, modifier -> RemoteStoreImage(item.imageIdentity, item.nameText, modifier, ContentScale.Crop) }
        )
        if (state.filterDialogVisible) {
            StoreCatalogFilterDialog(
                sizes = sizes, colors = colors, brands = emptyList(),
                selectedSizes = state.selectedSizes, selectedColors = state.selectedColors, selectedBrands = emptySet(),
                enabledSizes = sizes.toSet(), enabledColors = colors.toSet(), enabledBrands = emptySet(), showBrands = false,
                onSizesChanged = controller::setSizes, onColorsChanged = controller::setColors, onBrandsChanged = {},
                onApply = controller::closeFilters, onClear = controller::clearFilters,
                onDismiss = controller::closeFilters, onCancel = controller::closeFilters
            )
        }
    }
}
