package es.criosrango.shared

import androidx.compose.foundation.layout.*
import kotlin.coroutines.cancellation.CancellationException
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreProduct
import es.criosrango.shared.model.StoreCartVariation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun IosCatalogScreen(
    storeApi: es.criosrango.shared.api.StoreApiClient,
    rootFilterOwner: StoreRootFilterOwner,
    brandsController: StoreBrandsController,
    padding: PaddingValues,
    page: IosCatalogPage,
    cartStore: StoreCartStore,
    outletController: StoreOutletController,
    onOpenCategory: (StoreCategory) -> Unit,
    onOpenRootFilter: (StoreRootFilterScope) -> Unit,
    onOpenProduct: (StoreProduct) -> Unit,
    onBack: () -> Unit,
    onShareProductText: (String) -> Unit,
    onReserveProductMessage: (String) -> Unit,
    catalogRestoration: MutableMap<Long, IosCatalogRestorationState>,
    categorySnapshot: List<StoreCategory>?,
    categorySnapshotLoading: Boolean,
    categorySnapshotError: String?,
    onRetryCategorySnapshot: () -> Unit
) {
    val rootFilterState by rootFilterOwner.state.collectAsState()
    // This state is owned above the page switch so Outlet → Product → Outlet restores presentation.
    var showOutletChooser by remember(outletController) { mutableStateOf(true) }
    val outletGridState = rememberLazyGridState()
    if (rootFilterState.resultsVisible && page == IosCatalogPage.Root && rootFilterState.scope == StoreRootFilterScope.CATALOG) {
        StoreRootFilterResultsScreen(rootFilterOwner, padding, onOpenProduct)
    } else when (page) {
        IosCatalogPage.Root -> IosCategoryRoot(padding, onOpenCategory, onOpenRootFilter, onBack, categorySnapshot.orEmpty(), categorySnapshotLoading, categorySnapshotError, onRetryCategorySnapshot)
        is IosCatalogPage.RootFilter -> IosPagedProductScreen(
            title = page.scope.title, padding = padding, cartStore = cartStore,
            onProduct = onOpenProduct, onBack = onBack,
            load = { perPage, pageNumber -> storeApi.products(perPage = perPage, page = pageNumber, category = page.scope.categoryQueryId) },
            queryKey = page.scope.queryKey, enableProductControls = true, initialFiltersOpen = true
        )
        is IosCatalogPage.Category -> IosCategoryPage(
            storeApi, padding, page.category, onOpenCategory, onOpenProduct, onBack, cartStore,
            catalogRestoration, categorySnapshot, categorySnapshotLoading, categorySnapshotError, onRetryCategorySnapshot
        )
        is IosCatalogPage.Product -> IosProductDetail(storeApi, padding, page.product, onBack, cartStore, onShareProductText, onReserveProductMessage)
        IosCatalogPage.Novedades -> StoreNovedadesScreen(storeApi, padding, null, onOpenProduct, onBack)
        IosCatalogPage.Search -> StoreSearchScreen(storeApi = storeApi, controller = storeSearchController(storeApi), padding = padding, onProduct = onOpenProduct, onCategory = onOpenCategory, onBack = onBack)
        IosCatalogPage.Brands -> IosBrandsScreen(brandsController, padding, onOpenProduct, onBack)
        is IosCatalogPage.Outlet -> {
            var categories by remember { mutableStateOf<List<StoreCategory>?>(null) }
            var loading by remember { mutableStateOf(true) }
            var loadError by remember { mutableStateOf<String?>(null) }
            var retryToken by remember { mutableStateOf(0) }
            LaunchedEffect(retryToken) {
                loading = true
                loadError = null
                try {
                    categories = storeApi.categories(100)
                } catch (exception: Exception) {
                    // Keep the last successful snapshot visible during a failed refresh.
                    if (categories == null) loadError = exception.message ?: "No se han podido cargar las categorías."
                } finally {
                    loading = false
                }
            }
            IosOutletScreen(
                storeApi = storeApi,
                padding = padding,
                cartStore = cartStore,
                controller = outletController,
                showOutletChooser = showOutletChooser,
                onShowOutletChooserChange = { showOutletChooser = it },
                gridState = outletGridState,
                categories = categories,
                initialCategoryId = page.categoryId,
                season = page.season,
                categoriesLoading = loading,
                categoriesError = loadError,
                onRetry = { retryToken++ },
                onProduct = onOpenProduct,
                onBack = onBack
            )
        }
    }
    StoreRootFilterDialog(rootFilterOwner)
}

@Composable
internal fun IosCategoryRoot(
    padding: PaddingValues,
    onOpenCategory: (StoreCategory) -> Unit,
    onOpenRootFilter: (StoreRootFilterScope) -> Unit,
    onBack: () -> Unit,
    categories: List<StoreCategory>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit
) {
    StoreCategoriesScreen(categories=categories, loading=loading, errorMessage=error, onBack=onBack,
        onCategory=onOpenCategory, onRootFilter=onOpenRootFilter, onRetry=onRetry, modifier=Modifier.fillMaxSize().padding(padding))
}

@Composable
internal fun IosCategoryPage(
    storeApi: es.criosrango.shared.api.StoreApiClient,
    padding: PaddingValues,
    category: StoreCategory,
    onOpenCategory: (StoreCategory) -> Unit,
    onOpenProduct: (StoreProduct) -> Unit,
    onBack: () -> Unit,
    cartStore: StoreCartStore,
    catalogRestoration: MutableMap<Long, IosCatalogRestorationState>,
    categorySnapshot: List<StoreCategory>?,
    categorySnapshotLoading: Boolean,
    categorySnapshotError: String?,
    onRetryCategorySnapshot: () -> Unit
) {
    val categories = categorySnapshot
    val error = categorySnapshotError
    val children = categories?.filter { it.parent == category.id }?.distinctBy { it.id } ?: emptyList()
    var showProductsFromFilters by remember(category.id) { mutableStateOf(false) }

    when {
        error != null || categories == null -> StoreSubcategoriesScreen(
            parent = category,
            categories = emptyList(),
            loading = categorySnapshotLoading && error == null,
            errorMessage = error,
            onBack = onBack,
            onCategory = onOpenCategory,
            onFilterClick = { showProductsFromFilters = true },
            onRetry = onRetryCategorySnapshot,
            modifier = Modifier.fillMaxSize().padding(padding)
        )
        children.isNotEmpty() && !showProductsFromFilters -> StoreSubcategoriesScreen(
            parent = category,
            categories = children,
            loading = false,
            onBack = onBack,
            onCategory = onOpenCategory,
            onFilterClick = { showProductsFromFilters = true },
            modifier = Modifier.fillMaxSize().padding(padding)
        )
        else -> IosCategoryProducts(
            storeApi = storeApi,
            padding = padding,
            category = category,
            onOpenProduct = onOpenProduct,
            onBack = onBack,
            cartStore = cartStore,
            catalogRestoration = catalogRestoration,
            initialFiltersOpen = children.isNotEmpty() && showProductsFromFilters,
            onNoActiveFilters = { if (children.isNotEmpty()) showProductsFromFilters = false }
        )
    }
}

private fun isRestorableCatalogSnapshot(snapshot: CatalogPagingState<*>): Boolean =
    snapshot.currentPage > 0 || snapshot.initialError != null

private fun CatalogPagingState<StoreProduct>.asRestorableSnapshot(): CatalogPagingState<StoreProduct> =
    copy(isInitialLoading = false, isAppending = false)

private enum class IosCatalogSortMode(val label: String) {
    RECENT("Más recientes"),
    PRICE_ASC("Precio: menor a mayor"),
    PRICE_DESC("Precio: mayor a menor"),
    NAME_ASC("Nombre A-Z")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IosCategoryProducts(
    storeApi: es.criosrango.shared.api.StoreApiClient,
    padding: PaddingValues,
    category: StoreCategory,
    onOpenProduct: (StoreProduct) -> Unit,
    onBack: () -> Unit,
    cartStore: StoreCartStore,
    catalogRestoration: MutableMap<Long, IosCatalogRestorationState>,
    initialFiltersOpen: Boolean = false,
    onNoActiveFilters: (() -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
    val paginator = remember(category.id) {
        CatalogPaginatorStore<StoreProduct>(scope, identity = { it.id })
    }
    val pagingState by paginator.state.collectAsState()
    val gridState = rememberLazyGridState()

    val savedState = catalogRestoration[category.id.toLong()]
    val initialSortMode = when (savedState?.sortMode) {
        IosCatalogSortModeState.PRICE_ASC -> IosCatalogSortMode.PRICE_ASC
        IosCatalogSortModeState.PRICE_DESC -> IosCatalogSortMode.PRICE_DESC
        IosCatalogSortModeState.NAME_ASC -> IosCatalogSortMode.NAME_ASC
        else -> IosCatalogSortMode.RECENT
    }
    var filtersOpen by remember(category.id) { mutableStateOf(initialFiltersOpen) }
    var sortMode by remember(category.id) { mutableStateOf(initialSortMode) }
    var selectedSizes by remember(category.id) { mutableStateOf(savedState?.selectedSizes ?: emptySet()) }
    var selectedColors by remember(category.id) { mutableStateOf(savedState?.selectedColors ?: emptySet()) }
    var selectedBrands by remember(category.id) { mutableStateOf(savedState?.selectedBrands ?: emptySet()) }

    val queryKey = remember(
        category.id,
        sortMode,
        selectedSizes,
        selectedColors,
        selectedBrands
    ) {
        buildString {
            append("category:")
            append(category.id)
            append("|sort:")
            append(sortMode.name)
            append("|sizes:")
            append(selectedSizes.sorted().joinToString(","))
            append("|colors:")
            append(selectedColors.sorted().joinToString(","))
            append("|brands:")
            append(selectedBrands.sorted().joinToString(","))
        }
    }

    val orderBy = when (sortMode) {
        IosCatalogSortMode.RECENT -> "date"
        IosCatalogSortMode.PRICE_ASC,
        IosCatalogSortMode.PRICE_DESC -> "price"
        IosCatalogSortMode.NAME_ASC -> "title"
    }
    val order = when (sortMode) {
        IosCatalogSortMode.PRICE_DESC,
        IosCatalogSortMode.RECENT -> "desc"
        else -> "asc"
    }

    val loadPage: suspend (Int, Int) -> CatalogPage<StoreProduct> = { page, perPage ->
        val items = storeApi.products(
            perPage = perPage,
            page = page,
            category = category.id,
            orderBy = orderBy,
            order = order
        )
        CatalogPage(items, hasMore = items.size >= perPage)
    }

    LaunchedEffect(queryKey) {
        val restoredState = savedState?.takeIf {
            it.queryKey == queryKey &&
                isRestorableCatalogSnapshot(it.pagingState)
        }
        if (restoredState != null) {
            paginator.restore(queryKey, restoredState.pagingState.asRestorableSnapshot()).join()
            if (restoredState.firstVisibleItemIndex > 0 || restoredState.firstVisibleItemOffset > 0) {
                withFrameNanos { }
                gridState.scrollToItem(restoredState.firstVisibleItemIndex, restoredState.firstVisibleItemOffset)
            }
        } else {
            paginator.start(queryKey, loadPage)
        }
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                val current = catalogRestoration[category.id.toLong()]
                if (current?.queryKey == queryKey &&
                    (current.firstVisibleItemIndex != index || current.firstVisibleItemOffset != offset)
                ) catalogRestoration[category.id.toLong()] = current.copy(firstVisibleItemIndex = index, firstVisibleItemOffset = offset)
            }
    }

    LaunchedEffect(pagingState, sortMode, selectedSizes, selectedColors, selectedBrands) {
        val restoredSort = when (sortMode) {
            IosCatalogSortMode.PRICE_ASC -> IosCatalogSortModeState.PRICE_ASC
            IosCatalogSortMode.PRICE_DESC -> IosCatalogSortModeState.PRICE_DESC
            IosCatalogSortMode.NAME_ASC -> IosCatalogSortModeState.NAME_ASC
            IosCatalogSortMode.RECENT -> IosCatalogSortModeState.RECENT
        }
        catalogRestoration[category.id.toLong()] = IosCatalogRestorationState(
            queryKey, restoredSort, selectedSizes, selectedColors, selectedBrands,
            pagingState, gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset
        )
    }

    DisposableEffect(category.id, queryKey, pagingState, sortMode, selectedSizes, selectedColors, selectedBrands) {
        onDispose {
            val restoredSort = when (sortMode) {
                IosCatalogSortMode.PRICE_ASC -> IosCatalogSortModeState.PRICE_ASC
                IosCatalogSortMode.PRICE_DESC -> IosCatalogSortModeState.PRICE_DESC
                IosCatalogSortMode.NAME_ASC -> IosCatalogSortModeState.NAME_ASC
                IosCatalogSortMode.RECENT -> IosCatalogSortModeState.RECENT
            }
            catalogRestoration[category.id.toLong()] = IosCatalogRestorationState(
                queryKey, restoredSort, selectedSizes, selectedColors, selectedBrands,
                pagingState.asRestorableSnapshot(), gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset
            )
        }
    }

    fun filterValues(product: StoreProduct, attribute: String) =
        product.attributes
            .filter { it.name.equals(attribute, ignoreCase = true) }
            .flatMap { it.terms.map { term -> term.name } }
            .filter { it.isNotBlank() }
            .toSet()

    fun normalized(value: String): String = storeCatalogFilterKey(value)

    fun matchesFilters(product: StoreProduct): Boolean {
        val productSizes = filterValues(product, "Tallas").map(::normalized).toSet()
        val productColors = filterValues(product, "Color").map(::normalized).toSet()
        val productBrands = product.tags.map { it.name }.filter { it.isNotBlank() }.map(::normalized).toSet()
        val wantedSizes = selectedSizes.map(::normalized).toSet()
        val wantedColors = selectedColors.map(::normalized).toSet()
        val wantedBrands = selectedBrands.map(::normalized).toSet()

        return (wantedSizes.isEmpty() || productSizes.any { it in wantedSizes }) &&
            (wantedColors.isEmpty() || productColors.any { it in wantedColors }) &&
            (wantedBrands.isEmpty() || productBrands.any { it in wantedBrands })
    }

    val rawProducts = pagingState.items
    val filteredProducts = rawProducts.filter(::matchesFilters)

    val sizes = (rawProducts.flatMap { filterValues(it, "Tallas") } + selectedSizes)
        .filter { it.isNotBlank() }
        .distinct()
        .sortedWith(compareBy(String::lowercase))

    val colors = (rawProducts.flatMap { filterValues(it, "Color") } + selectedColors)
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()

    val brands = (rawProducts.flatMap { it.tags.map { tag -> tag.name } } + selectedBrands)
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()

    val possibleSizes = rawProducts
        .filter { product ->
            val colorsOnly = selectedColors.map(::normalized).toSet()
            val brandsOnly = selectedBrands.map(::normalized).toSet()
            val productColors = filterValues(product, "Color").map(::normalized)
            val productBrands = product.tags.map { it.name }.map(::normalized)
            (colorsOnly.isEmpty() || productColors.any { it in colorsOnly }) &&
                (brandsOnly.isEmpty() || productBrands.any { it in brandsOnly })
        }
        .flatMap { filterValues(it, "Tallas") }
        .toSet() + selectedSizes

    val possibleColors = rawProducts
        .filter { product ->
            val sizesOnly = selectedSizes.map(::normalized).toSet()
            val brandsOnly = selectedBrands.map(::normalized).toSet()
            val productSizes = filterValues(product, "Tallas").map(::normalized)
            val productBrands = product.tags.map { it.name }.map(::normalized)
            (sizesOnly.isEmpty() || productSizes.any { it in sizesOnly }) &&
                (brandsOnly.isEmpty() || productBrands.any { it in brandsOnly })
        }
        .flatMap { filterValues(it, "Color") }
        .toSet() + selectedColors

    val possibleBrands = rawProducts
        .filter { product ->
            val sizesOnly = selectedSizes.map(::normalized).toSet()
            val colorsOnly = selectedColors.map(::normalized).toSet()
            val productSizes = filterValues(product, "Tallas").map(::normalized)
            val productColors = filterValues(product, "Color").map(::normalized)
            (sizesOnly.isEmpty() || productSizes.any { it in sizesOnly }) &&
                (colorsOnly.isEmpty() || productColors.any { it in colorsOnly })
        }
        .flatMap { it.tags.map { tag -> tag.name } }
        .filter { it.isNotBlank() }
        .toSet() + selectedBrands

    val activeFilterCount = selectedSizes.size + selectedColors.size + selectedBrands.size

    LaunchedEffect(
        pagingState.items.size,
        pagingState.currentPage,
        pagingState.hasMore,
        pagingState.isInitialLoading,
        pagingState.isAppending,
        selectedSizes,
        selectedColors,
        selectedBrands
    ) {
        if (
            activeFilterCount > 0 &&
            filteredProducts.size < CatalogPaginator.PREFETCH_DISTANCE &&
            pagingState.hasMore &&
            !pagingState.isInitialLoading &&
            !pagingState.isAppending &&
            pagingState.currentPage > 0
        ) {
            paginator.loadNext(loadPage)
        }
    }

    LaunchedEffect(gridState, filteredProducts.size, pagingState.hasMore, pagingState.isInitialLoading, pagingState.isAppending) {
        snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.collect { lastVisible ->
            if (
                pagingState.hasMore &&
                !pagingState.isInitialLoading &&
                !pagingState.isAppending &&
                filteredProducts.isNotEmpty() &&
                lastVisible >= filteredProducts.size - CatalogPaginator.PREFETCH_DISTANCE
            ) {
                paginator.loadNext(loadPage)
            }
        }
    }

    val activeFilterChips = buildList {
        selectedSizes.sortedWith(compareBy({ storeCatalogFilterSizeSortKey(it) }, { it })).forEach { add(StoreActiveFilterChipUi("size:$it", "Talla $it")) }
        selectedColors.sorted().forEach { add(StoreActiveFilterChipUi("color:$it", it)) }
        selectedBrands.sorted().forEach { add(StoreActiveFilterChipUi("brand:$it", it)) }
    }
    val productListState = StoreProductListUiState(
        title = category.name,
        items = filteredProducts.map { product ->
            StoreProductItemUi(
                id = product.id,
                nameText = normalizeWooPlainText(product.name),
                imageIdentity = product.images.firstOrNull()?.src,
                displayPriceText = formatStorePrice(
                    product.prices.price,
                    product.prices.currencyMinorUnit,
                    product.prices.currencySymbol
                ),
                regularPriceText = formatStorePrice(
                    product.prices.regularPrice,
                    product.prices.currencyMinorUnit,
                    product.prices.currencySymbol
                ),
                showPrice = product.prices.price.toLongOrNull()?.let { it > 0L } == true,
                isOnSale = product.onSale
            )
        },
        selectedSort = when (sortMode) {
            IosCatalogSortMode.RECENT -> StoreProductSortOption.RECENT
            IosCatalogSortMode.PRICE_ASC -> StoreProductSortOption.PRICE_ASC
            IosCatalogSortMode.PRICE_DESC -> StoreProductSortOption.PRICE_DESC
            IosCatalogSortMode.NAME_ASC -> StoreProductSortOption.NAME_ASC
        },
        availableSortOptions = StoreProductSortOption.values().toList(),
        activeFilterCount = activeFilterCount,
        activeFilterChips = activeFilterChips,
        isInitialLoading = pagingState.isInitialLoading,
        isLoadingMore = pagingState.isAppending,
        isEmpty = filteredProducts.isEmpty() && !pagingState.hasMore,
        emptyMessage = if (activeFilterCount > 0) {
            "No hay productos con estos filtros."
        } else {
            "No hay productos en esta categoría."
        },
        initialErrorMessage = if (pagingState.initialError != null) "No se han podido cargar los productos." else null,
        appendErrorMessage = if (pagingState.appendError != null) "No se han podido cargar más productos." else null,
        appendRetryLabel = "Reintentar",
        canLoadMore = pagingState.hasMore
    )

    StoreProductListScreen(
        state = productListState,
        gridState = gridState,
        modifier = Modifier.fillMaxSize().padding(padding),
        onBack = onBack,
        onProductSelected = { id -> filteredProducts.firstOrNull { it.id == id }?.let(onOpenProduct) },
        onSortSelected = { option ->
            sortMode = when (option) {
                StoreProductSortOption.RECENT -> IosCatalogSortMode.RECENT
                StoreProductSortOption.PRICE_ASC -> IosCatalogSortMode.PRICE_ASC
                StoreProductSortOption.PRICE_DESC -> IosCatalogSortMode.PRICE_DESC
                StoreProductSortOption.NAME_ASC -> IosCatalogSortMode.NAME_ASC
            }
        },
        onFilterClick = { filtersOpen = true },
        onRemoveFilter = { id ->
            val nextSizes = if (id.startsWith("size:")) selectedSizes - id.removePrefix("size:") else selectedSizes
            val nextColors = if (id.startsWith("color:")) selectedColors - id.removePrefix("color:") else selectedColors
            val nextBrands = if (id.startsWith("brand:")) selectedBrands - id.removePrefix("brand:") else selectedBrands
            selectedSizes = nextSizes
            selectedColors = nextColors
            selectedBrands = nextBrands
            if (nextSizes.isEmpty() && nextColors.isEmpty() && nextBrands.isEmpty()) onNoActiveFilters?.invoke()
        },
        onClearFilters = {
            selectedSizes = emptySet()
            selectedColors = emptySet()
            selectedBrands = emptySet()
            onNoActiveFilters?.invoke()
        },
        onRetryInitial = { paginator.start(queryKey, loadPage) },
        onRetryAppend = { paginator.loadNext(loadPage) },
        imageContent = { item, imageModifier ->
            val product = filteredProducts.firstOrNull { it.id == item.id }
            RemoteStoreImage(
                product?.images?.firstOrNull()?.src.orEmpty(),
                item.nameText,
                imageModifier,
                ContentScale.Crop
            )
        }
    )

    if (filtersOpen) {
        StoreCatalogFilterDialog(
            sizes=sizes.sortedWith(compareBy(String::lowercase)), colors=colors, brands=brands,
            selectedSizes=selectedSizes, selectedColors=selectedColors, selectedBrands=selectedBrands,
            enabledSizes=possibleSizes, enabledColors=possibleColors, enabledBrands=possibleBrands,
            onSizesChanged={selectedSizes=it}, onColorsChanged={selectedColors=it}, onBrandsChanged={selectedBrands=it},
            onApply={
                filtersOpen = false
                if (selectedSizes.isEmpty() && selectedColors.isEmpty() && selectedBrands.isEmpty()) onNoActiveFilters?.invoke()
            },
            onClear={
                selectedSizes = emptySet()
                selectedColors = emptySet()
                selectedBrands = emptySet()
                onNoActiveFilters?.invoke()
            },
            onDismiss={
                filtersOpen = false
                if (selectedSizes.isEmpty() && selectedColors.isEmpty() && selectedBrands.isEmpty()) onNoActiveFilters?.invoke()
            }
        )
    }
}

internal data class IosProductDetailVariationSnapshot(
    val availableForPurchase: Boolean,
    val attributes: List<Pair<String, String>>
)

internal data class IosProductDetailAttributeOptionState(
    val existsGlobally: Boolean,
    val compatibleWithCurrentSelection: Boolean
)

private fun iosProductDetailNormalized(value: String): String =
    value.trim().lowercase().removePrefix("pa_").replace(Regex("[^a-z0-9]+"), "-").trim('-')

private fun iosProductDetailAttributesMatch(left: String, right: String): Boolean =
    iosProductDetailNormalized(left) == iosProductDetailNormalized(right)

private fun iosProductDetailValuesMatch(left: String, right: String): Boolean =
    iosProductDetailNormalized(left) == iosProductDetailNormalized(right)

private fun iosProductDetailVariationHasValue(
    variation: IosProductDetailVariationSnapshot,
    attributeName: String,
    selectedValue: String
): Boolean = variation.attributes.any { (name, value) ->
    iosProductDetailAttributesMatch(name, attributeName) && iosProductDetailValuesMatch(value, selectedValue)
}

private fun iosProductDetailVariationHasTerm(
    variation: IosProductDetailVariationSnapshot,
    attributeName: String,
    termSlug: String,
    termName: String
): Boolean = variation.attributes.any { (name, value) ->
    iosProductDetailAttributesMatch(name, attributeName) &&
        (iosProductDetailValuesMatch(value, termSlug) || iosProductDetailValuesMatch(value, termName))
}

internal fun iosProductDetailAttributeOptionState(
    variations: List<IosProductDetailVariationSnapshot>,
    selected: Map<String, String>,
    attributeName: String,
    termSlug: String,
    termName: String
): IosProductDetailAttributeOptionState {
    val existsGlobally = variations.any { variation ->
        variation.availableForPurchase && iosProductDetailVariationHasTerm(variation, attributeName, termSlug, termName)
    }
    val compatibleWithCurrentSelection = variations.any { variation ->
        variation.availableForPurchase &&
            iosProductDetailVariationHasTerm(variation, attributeName, termSlug, termName) &&
            selected.all { (selectedName, selectedValue) ->
                iosProductDetailAttributesMatch(selectedName, attributeName) ||
                    iosProductDetailVariationHasValue(variation, selectedName, selectedValue)
            }
    }
    return IosProductDetailAttributeOptionState(existsGlobally, compatibleWithCurrentSelection)
}

internal fun iosProductDetailSelectAttributeOption(
    selected: MutableMap<String, String>,
    attributeName: String,
    termSlug: String,
    termName: String,
    variations: List<IosProductDetailVariationSnapshot>
) {
    selected[attributeName] = termSlug
    val conflictingAttributes = selected.toMap()
        .filterKeys { !iosProductDetailAttributesMatch(it, attributeName) }
        .filterNot { (selectedName, selectedValue) ->
            variations.any { variation ->
                variation.availableForPurchase &&
                    iosProductDetailVariationHasTerm(variation, attributeName, termSlug, termName) &&
                    iosProductDetailVariationHasValue(variation, selectedName, selectedValue)
            }
        }
        .keys
    conflictingAttributes.forEach(selected::remove)
}

internal fun iosProductDetailToggleAttributeSelection(
    selected: MutableMap<String, String>,
    attributeName: String,
    termSlug: String,
    termName: String,
    chosen: Boolean,
    existsGlobally: Boolean,
    variations: List<IosProductDetailVariationSnapshot>
) {
    if (chosen) selected.remove(attributeName)
    else if (existsGlobally) iosProductDetailSelectAttributeOption(selected, attributeName, termSlug, termName, variations)
}

private fun Modifier.iosProductDetailIncompatibleSlash(
    show: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    color: Color = Color(0xFF8B878B)
): Modifier = if (!show) this else {
    clip(shape).drawWithContent {
        drawContent()
        drawLine(
            color = color,
            start = Offset(0f, size.height),
            end = Offset(size.width, 0f),
            strokeWidth = 1.25.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
internal fun IosProductDetail(
    storeApi: es.criosrango.shared.api.StoreApiClient,
    padding: PaddingValues,
    initialProduct: StoreProduct,
    onBack: () -> Unit,
    cartStore: StoreCartStore? = null,
    onShareProductText: (String) -> Unit,
    onReserveProductMessage: (String) -> Unit
) {
    var product by remember(initialProduct.id) { mutableStateOf(initialProduct) }
    var loading by remember(initialProduct.id) { mutableStateOf(true) }
    var error by remember(initialProduct.id) { mutableStateOf<String?>(null) }
    var quantity by remember(initialProduct.id) { mutableStateOf(1) }
    val selected = remember(initialProduct.id) { mutableStateMapOf<String, String>() }
    LaunchedEffect(initialProduct.id, loading) {
        if (!loading) return@LaunchedEffect
        runCatching { storeApi.productWithVariationAvailability(initialProduct.id) }
            .onSuccess { loaded ->
                product = loaded
                loaded.attributes.forEach { attribute ->
                    attribute.terms.firstOrNull { it.default }?.let { term -> if (!selected.containsKey(attribute.name)) selected[attribute.name] = term.slug }
                }
            }
            .onFailure { error = it.message ?: "No se ha podido cargar el producto." }
        loading = false
    }
    val variationSnapshots = remember(product.variations) {
        product.variations.map { variation ->
            IosProductDetailVariationSnapshot(
                availableForPurchase = variation.isInStock != false && variation.isPurchasable != false,
                attributes = variation.attributes.map { it.name to it.value }
            )
        }
    }
    val selectableAttributes = product.attributes.filter { it.terms.isNotEmpty() }
    val selectedVariation = remember(product, selected.toMap()) {
        if (product.type != "variable") null else product.variations.firstOrNull { variation ->
            selectableAttributes.all { attribute ->
                val wanted = selected[attribute.name]
                wanted != null && variation.attributes.any { value ->
                    iosProductDetailAttributesMatch(value.name, attribute.name) &&
                        (iosProductDetailValuesMatch(value.value, wanted) ||
                            attribute.terms.any { it.slug == wanted && iosProductDetailValuesMatch(value.value, it.name) })
                }
            }
        }
    }
    val currentId = selectedVariation?.id ?: product.id
    val currentImages = selectedVariation?.images?.takeIf { it.isNotEmpty() } ?: product.images
    val currentPrices = selectedVariation?.prices ?: product.prices
    val displayProductName = normalizeWooPlainText(product.name)
    val displayShortDescription = normalizeWooDescriptionText(product.shortDescription)
    val displayDescription = normalizeWooDescriptionText(product.description)
    val currentInStock = selectedVariation?.isInStock ?: product.isInStock
    val currentPurchasable = selectedVariation?.isPurchasable ?: product.isPurchasable
    val minimum = selectedVariation?.quantityLimits?.minimum ?: selectedVariation?.addToCart?.minimum ?: product.quantityLimits?.minimum ?: product.addToCart?.minimum ?: 1
    val maximum = selectedVariation?.quantityLimits?.maximum ?: selectedVariation?.addToCart?.maximum ?: product.quantityLimits?.maximum ?: product.addToCart?.maximum
    val multiple = (selectedVariation?.quantityLimits?.multipleOf ?: selectedVariation?.addToCart?.multipleOf ?: product.quantityLimits?.multipleOf ?: product.addToCart?.multipleOf)?.takeIf { it > 0 } ?: 1
    val canAdd = currentInStock && currentPurchasable != false && (product.type != "variable" || selectedVariation != null)
    LaunchedEffect(currentId, minimum, maximum, multiple) {
        quantity = quantity.coerceAtLeast(minimum)
        maximum?.let { quantity = quantity.coerceAtMost(it) }
        if ((quantity - minimum) % multiple != 0) quantity = minimum
    }

    val infoText = normalizeWooDescriptionText(product.description.ifBlank { product.shortDescription })
    val infoLines = infoText.lines().map { it.trim() }.filter { it.isNotBlank() }
    val brandLine = infoLines.firstOrNull { it.startsWith("Marca:", ignoreCase = true) }
    val compositionLine = infoLines.firstOrNull {
        it.startsWith("Composición:", ignoreCase = true) || it.startsWith("Composicion:", ignoreCase = true)
    }
    val remainingInfo = infoLines.filterNot { it == brandLine || it == compositionLine }.joinToString("\n")
    val detailUiState = StoreProductDetailUiState(
        title = displayProductName,
        displayPrice = if (currentPrices.price.toLongOrNull()?.let { it > 0L } == true) formatStorePrice(currentPrices.price, currentPrices.currencyMinorUnit, currentPrices.currencySymbol) else "",
        regularPrice = formatStorePrice(currentPrices.regularPrice, currentPrices.currencyMinorUnit, currentPrices.currencySymbol),
        showRegularPrice = product.onSale && currentPrices.regularPrice != currentPrices.price,
        isOnSale = product.onSale,
        attributeGroups = selectableAttributes.map { attribute ->
            StoreProductDetailAttributeGroupUi(
                label = attribute.name,
                options = attribute.terms.map { term ->
                    val chosen = selected[attribute.name]?.let {
                        iosProductDetailValuesMatch(it, term.slug) || iosProductDetailValuesMatch(it, term.name)
                    } == true
                    val optionState = iosProductDetailAttributeOptionState(
                        variations = variationSnapshots,
                        selected = selected,
                        attributeName = attribute.name,
                        termSlug = term.slug,
                        termName = term.name
                    )
                    StoreProductDetailOptionUi(
                        value = term.name,
                        selected = chosen,
                        enabled = chosen || optionState.existsGlobally,
                        incompatible = optionState.existsGlobally && !optionState.compatibleWithCurrentSelection && !chosen,
                        strikeThrough = attribute.name.equals("Tallas", true) && !optionState.existsGlobally,
                        kind = when {
                            attribute.name.equals("Color", true) -> StoreProductDetailOptionKind.Color
                            attribute.name.equals("Tallas", true) -> StoreProductDetailOptionKind.Size
                            else -> StoreProductDetailOptionKind.Default
                        },
                        swatchColor = if (attribute.name.equals("Color", true)) {
                            productColorSwatchMapping(term.name)
                        } else null
                    )
                }
            )
        },
        quantity = quantity,
        quantityMinusEnabled = quantity > minimum,
        quantityPlusEnabled = maximum == null || quantity < maximum,
        primaryActionLabel = if (product.type == "variable" && selectedVariation == null) "Elige una combinación" else "Añadir al carrito",
        primaryActionEnabled = canAdd && cartStore != null,
        shareEnabled = true,
        reserveEnabled = product.type != "variable" || (selectedVariation != null && selectedVariation.isInStock == true && selectedVariation.isPurchasable == true),
        descriptionText = remainingInfo,
        brandText = brandLine,
        compositionText = compositionLine
    )
    StoreProductDetailScreen(
        modifier = Modifier.fillMaxSize().padding(padding),
        state = detailUiState,
        imageCount = currentImages.size,
        showStoreTopBar = false,
        galleryKey = initialProduct.id,
        imageSlot = { index, imageModifier ->
            RemoteStoreImage(currentImages[index].src, product.name, imageModifier, ContentScale.Crop)
        },
        fullscreenImageSlot = { index, imageModifier ->
            RemoteStoreImage(currentImages[index].src, product.name, imageModifier, ContentScale.Fit)
        },
        isLoading = loading,
        errorMessage = error,
        onRetry = { error = null; loading = true },
        onBack = onBack,
        onShare = {
            val shareText = buildString {
                append(displayProductName)
                if (product.permalink.isNotBlank()) { append("\n\n"); append(product.permalink) }
            }
            onShareProductText(shareText)
        },
        onReserve = {
            val details = selected.entries.filter { it.value.isNotBlank() }.joinToString("\n") { entry ->
                val label = if (entry.key.equals("Tallas", true)) "Talla" else entry.key
                val value = if (entry.key.equals("Color", true)) entry.value.replaceFirstChar { it.uppercase() } else entry.value.uppercase()
                "$label: $value"
            }
            val message = buildString {
                append("Hola, quiero reservar:\n")
                append(displayProductName)
                if (details.isNotBlank()) { append("\n"); append(details) }
                if (product.permalink.isNotBlank()) { append("\n"); append(product.permalink) }
            }
            onReserveProductMessage(message)
        },
        onAttributeOptionSelected = { groupLabel, value ->
            val attribute = selectableAttributes.firstOrNull { it.name == groupLabel }
            val term = attribute?.terms?.firstOrNull { it.name == value }
            if (attribute != null && term != null) {
                val chosen = selected[attribute.name]?.let {
                    iosProductDetailValuesMatch(it, term.slug) || iosProductDetailValuesMatch(it, term.name)
                } == true
                val optionState = iosProductDetailAttributeOptionState(
                    variations = variationSnapshots,
                    selected = selected,
                    attributeName = attribute.name,
                    termSlug = term.slug,
                    termName = term.name
                )
                iosProductDetailToggleAttributeSelection(
                    selected = selected,
                    attributeName = attribute.name,
                    termSlug = term.slug,
                    termName = term.name,
                    chosen = chosen,
                    existsGlobally = optionState.existsGlobally,
                    variations = variationSnapshots
                )
            }
        },
        onQuantityMinus = { quantity = (quantity - multiple).coerceAtLeast(minimum) },
        onQuantityPlus = { quantity = maximum?.let { (quantity + multiple).coerceAtMost(it) } ?: (quantity + multiple) },
        onAddToCart = {
            cartStore?.let { store ->
                val attrs = product.attributes.mapNotNull { attribute ->
                    selected[attribute.name]?.let { value ->
                        val taxonomy = attribute.taxonomy?.takeIf { it.isNotBlank() } ?: "pa_${attribute.name.lowercase().replace(Regex("[^a-z0-9]+"), "_")}"
                        StoreCartVariation(taxonomy, value)
                    }
                }
                store.add(currentId, quantity, attrs)
            }
        }
    )

}
