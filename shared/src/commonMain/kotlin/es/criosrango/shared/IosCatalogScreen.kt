package es.criosrango.shared

import androidx.compose.foundation.layout.*
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
    padding: PaddingValues,
    page: IosCatalogPage,
    cartStore: StoreCartStore,
    onOpenCategory: (StoreCategory) -> Unit,
    onOpenProduct: (StoreProduct) -> Unit,
    onBack: () -> Unit,
    catalogRestoration: MutableMap<Long, IosCatalogRestorationState>
) {
    when (page) {
        IosCatalogPage.Root -> IosCategoryRoot(storeApi, padding, onOpenCategory)
        is IosCatalogPage.Category -> IosCategoryPage(storeApi, padding, page.category, onOpenCategory, onOpenProduct, onBack, cartStore, catalogRestoration)
        is IosCatalogPage.Product -> IosProductDetail(storeApi, padding, page.product, onBack, cartStore)
        IosCatalogPage.Novedades -> IosNovedadesScreen(storeApi, padding, cartStore, onOpenProduct, onBack)
        IosCatalogPage.Search -> IosSearchScreen(storeApi, padding, cartStore, onOpenProduct, onOpenCategory, onBack)
        IosCatalogPage.Brands -> IosBrandsScreen(storeApi, padding, cartStore, onOpenProduct, onBack)
        IosCatalogPage.Outlet -> {
            var categories by remember { mutableStateOf(emptyList<StoreCategory>()) }
            LaunchedEffect(Unit) { categories = runCatching { storeApi.categories(100) }.getOrDefault(emptyList()) }
            IosOutletScreen(storeApi, padding, cartStore, categories, onOpenProduct, onBack)
        }
    }
}

@Composable
internal fun IosCategoryRoot(
    storeApi: es.criosrango.shared.api.StoreApiClient,
    padding: PaddingValues,
    onOpenCategory: (StoreCategory) -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var categories by remember { mutableStateOf(emptyList<StoreCategory>()) }
    LaunchedEffect(loading) {
        if (!loading) return@LaunchedEffect
        runCatching { storeApi.categories(perPage = 100) }
            .onSuccess { categories = it; loading = false }
            .onFailure { error = it.message ?: "No se han podido cargar las categorías."; loading = false }
    }
    Column(Modifier.fillMaxSize().padding(padding)) {
        Text("Categorías", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(20.dp))
        when {
            loading -> IosStoreLoading()
            error != null -> IosStoreError(error!!) { loading = true; error = null }
            categories.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No hay categorías.") }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, top = 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(categories.filter { it.parent == 0 && !it.name.equals("Outlet", true) }.distinctBy { it.id }, key = { it.id }) {
                    Button(onClick = { onOpenCategory(it) }) { Text(it.name) }
                }
            }
        }
    }
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
    catalogRestoration: MutableMap<Long, IosCatalogRestorationState>
) {
    var categories by remember { mutableStateOf<List<StoreCategory>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryGeneration by remember { mutableIntStateOf(0) }

    LaunchedEffect(category.id, retryGeneration) {
        categories = null
        error = null
        runCatching { storeApi.categories(perPage = 100) }
            .onSuccess { categories = it }
            .onFailure { error = it.message ?: "No se han podido cargar las categorías." }
    }

    val children = categories
        ?.filter { it.parent == category.id }
        ?.distinctBy { it.id }
        ?: emptyList()

    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("Atrás") }
            Text(category.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        when {
            error != null -> IosStoreError(error!!) {
                categories = null
                error = null
                retryGeneration++
            }
            categories == null -> IosStoreLoading()
            children.isNotEmpty() -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(children, key = { it.id }) { child ->
                    Button(onClick = { onOpenCategory(child) }) {
                        Text(child.name)
                    }
                }
            }
            else -> IosCategoryProducts(
                storeApi = storeApi,
                padding = PaddingValues(),
                category = category,
                onOpenProduct = onOpenProduct,
                onBack = onBack,
                cartStore = cartStore,
                catalogRestoration = catalogRestoration
            )
        }
    }
}

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
    catalogRestoration: MutableMap<Long, IosCatalogRestorationState>
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
    var filtersOpen by remember(category.id) { mutableStateOf(false) }
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
        if (savedState?.queryKey == queryKey) paginator.restore(queryKey, savedState.pagingState)
        else paginator.start(queryKey, loadPage)
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

    LaunchedEffect(savedState?.firstVisibleItemIndex, savedState?.firstVisibleItemOffset) {
        val index = savedState?.firstVisibleItemIndex ?: return@LaunchedEffect
        gridState.scrollToItem(index, savedState.firstVisibleItemOffset)
    }

    fun filterValues(product: StoreProduct, attribute: String): Set<String> =
        product.attributes
            .filter { it.name.equals(attribute, ignoreCase = true) }
            .flatMap { it.terms.map { term -> term.name } }
            .filter { it.isNotBlank() }
            .toSet()

    fun normalized(value: String): String = value.trim().lowercase()

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

    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("Atrás") }
            Text(
                category.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Ordenar por", style = MaterialTheme.typography.bodyMedium)
                Box {
                    var sortExpanded by remember(category.id) { mutableStateOf(false) }
                    TextButton(onClick = { sortExpanded = true }) {
                        Text("\${sortMode.label}  ▾")
                    }
                    DropdownMenu(
                        expanded = sortExpanded,
                        onDismissRequest = { sortExpanded = false }
                    ) {
                        IosCatalogSortMode.values().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    sortExpanded = false
                                    sortMode = option
                                }
                            )
                        }
                    }
                }
            }
            OutlinedButton(onClick = { filtersOpen = true }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                Text(if (activeFilterCount == 0) "Filtros" else "Filtros (\$activeFilterCount)")
            }
        }

        if (activeFilterCount > 0) {
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                selectedSizes.sorted().forEach { value ->
                    FilterChip(
                        selected = true,
                        onClick = { selectedSizes = selectedSizes - value },
                        label = { Text("Talla \$value") },
                        trailingIcon = { Text("×") }
                    )
                }
                selectedColors.sorted().forEach { value ->
                    FilterChip(
                        selected = true,
                        onClick = { selectedColors = selectedColors - value },
                        label = { Text(value) },
                        trailingIcon = { Text("×") }
                    )
                }
                selectedBrands.sorted().forEach { value ->
                    FilterChip(
                        selected = true,
                        onClick = { selectedBrands = selectedBrands - value },
                        label = { Text(value) },
                        trailingIcon = { Text("×") }
                    )
                }
                TextButton(
                    onClick = {
                        selectedSizes = emptySet()
                        selectedColors = emptySet()
                        selectedBrands = emptySet()
                    }
                ) {
                    Text("Borrar filtros")
                }
            }
        }

        when {
            pagingState.isInitialLoading -> IosStoreLoading()
            pagingState.initialError != null -> IosStoreError(
                pagingState.initialError?.message ?: "No se han podido cargar los productos."
            ) {
                paginator.start(queryKey, loadPage)
            }
            filteredProducts.isEmpty() && !pagingState.hasMore -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (activeFilterCount > 0) {
                        "No hay productos con estos filtros."
                    } else {
                        "No hay productos en esta categoría."
                    }
                )
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, top = 6.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredProducts, key = { it.id }) { IosProductCard(it, onOpenProduct, cartStore) }
                if (pagingState.isAppending) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                }
                if (pagingState.appendError != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        IosStoreError(
                            pagingState.appendError?.message ?: "No se han podido cargar más productos."
                        ) {
                            paginator.loadNext(loadPage)
                        }
                    }
                }
            }
        }
    }

    if (filtersOpen) {
        AlertDialog(
            onDismissRequest = { filtersOpen = false },
            title = { Text("Filtros") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    IosFilterChoices(
                        title = "Talla",
                        values = sizes,
                        selected = selectedSizes,
                        enabledValues = possibleSizes,
                        onChange = { selectedSizes = it }
                    )
                    Spacer(Modifier.height(20.dp))
                    IosFilterChoices(
                        title = "Color",
                        values = colors,
                        selected = selectedColors,
                        enabledValues = possibleColors,
                        onChange = { selectedColors = it }
                    )
                    Spacer(Modifier.height(20.dp))
                    IosFilterChoices(
                        title = "Marca",
                        values = brands,
                        selected = selectedBrands,
                        enabledValues = possibleBrands,
                        onChange = { selectedBrands = it }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { filtersOpen = false }) { Text("Aplicar") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        selectedSizes = emptySet()
                        selectedColors = emptySet()
                        selectedBrands = emptySet()
                    }
                ) {
                    Text("Limpiar")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IosFilterChoices(
    title: String,
    values: List<String>,
    selected: Set<String>,
    enabledValues: Set<String>,
    onChange: (Set<String>) -> Unit
) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        values.forEach { value ->
            FilterChip(
                selected = value in selected,
                enabled = value in selected || value in enabledValues,
                onClick = {
                    onChange(
                        if (value in selected) selected - value else selected + value
                    )
                },
                label = { Text(value) }
            )
        }
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
    cartStore: StoreCartStore? = null
) {
    var product by remember(initialProduct.id) { mutableStateOf(initialProduct) }
    var loading by remember(initialProduct.id) { mutableStateOf(true) }
    var error by remember(initialProduct.id) { mutableStateOf<String?>(null) }
    var quantity by remember(initialProduct.id) { mutableStateOf(1) }
    var fullscreenPage by remember(initialProduct.id) { mutableStateOf<Int?>(null) }
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
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(top = 2.dp, bottom = 72.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("Atrás") }
                Text(product.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
        }
        item {
            if (currentImages.isNotEmpty()) {
                val pagerState = rememberPagerState(pageCount = { currentImages.size })
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp), pageSpacing = 10.dp) { index ->
                    RemoteStoreImage(currentImages[index].src, product.name, Modifier.fillMaxWidth().aspectRatio(.78f).clickable { fullscreenPage = index }.semantics { role = Role.Button; contentDescription = "Ampliar imagen ${index + 1}" }, ContentScale.Crop)
                }
                if (currentImages.size > 1) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                        repeat(currentImages.size) { index ->
                            Box(Modifier.padding(horizontal = 3.dp).size(if (pagerState.currentPage == index) 8.dp else 6.dp).background(if (pagerState.currentPage == index) Color(0xFF183B35) else Color(0xFFD8D4D7), RoundedCornerShape(50)))
                        }
                    }
                }
            }
            Text(product.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp, 16.dp, 20.dp, 4.dp))
            if (currentPrices.price.toLongOrNull()?.let { it > 0L } == true) {
                Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(formatStorePrice(currentPrices.price, currentPrices.currencyMinorUnit, currentPrices.currencySymbol), fontWeight = FontWeight.Bold, color = Color(0xFF183B35))
                    if (product.onSale && currentPrices.regularPrice != currentPrices.price) Text(formatStorePrice(currentPrices.regularPrice, currentPrices.currencyMinorUnit, currentPrices.currencySymbol), color = Color(0xFF9E9E9E), textDecoration = TextDecoration.LineThrough)
                }
            }
            product.attributes.filter { it.terms.isNotEmpty() }.forEach { attribute ->
                Text(attribute.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp, 18.dp, 20.dp, 6.dp))
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    attribute.terms.forEach { term ->
                        run {
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
                            val existsGlobally = optionState.existsGlobally
                            val compatibleNow = optionState.compatibleWithCurrentSelection
                            val clickable = chosen || existsGlobally
                            OutlinedButton(
                                onClick = {
                                    iosProductDetailToggleAttributeSelection(
                                        selected = selected,
                                        attributeName = attribute.name,
                                        termSlug = term.slug,
                                        termName = term.name,
                                        chosen = chosen,
                                        existsGlobally = existsGlobally,
                                        variations = variationSnapshots
                                    )
                                },
                                enabled = clickable,
                                shape = RoundedCornerShape(if (attribute.name.equals("Color", true)) 50.dp else 14.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    if (chosen) 2.dp else 1.dp,
                                    when {
                                        chosen -> Color(0xFF183B35)
                                        existsGlobally && !compatibleNow -> Color(0xFFD0CDD0)
                                        else -> Color(0xFF8B878B)
                                    }
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (chosen) Color(0xFFE4EFEA) else Color.Transparent,
                                    contentColor = if (existsGlobally && !compatibleNow) Color(0xFF777277) else LocalContentColor.current
                                ),
                                modifier = Modifier.iosProductDetailIncompatibleSlash(
                                    show = existsGlobally && !compatibleNow && !chosen,
                                    shape = RoundedCornerShape(if (attribute.name.equals("Color", true)) 50.dp else 14.dp)
                                )
                            ) {
                                Text(
                                    term.name,
                                    textDecoration = if (!existsGlobally && attribute.name.equals("Tallas", true)) TextDecoration.LineThrough else TextDecoration.None
                                )
                            }
                        }
                    }
                }
            }
            Text(
                if (!currentInStock) "Sin stock" else if (selectedVariation?.lowStockRemaining != null) "Últimas unidades" else "Disponible",
                color = if (currentInStock) Color(0xFF183B35) else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            if (product.type == "variable" && selectedVariation == null) Text("Elige una combinación para continuar.", modifier = Modifier.padding(20.dp))
            Text("Cantidad", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp, 18.dp, 20.dp, 6.dp))
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { quantity = (quantity - multiple).coerceAtLeast(minimum) }, enabled = quantity > minimum) { Text("−") }
                Text(quantity.toString(), modifier = Modifier.padding(horizontal = 18.dp), fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { quantity = maximum?.let { (quantity + multiple).coerceAtMost(it) } ?: (quantity + multiple) }, enabled = maximum == null || quantity < maximum) { Text("+") }
            }
            cartStore?.let { store ->
                Button(onClick = {
                    val attrs = product.attributes.mapNotNull { attribute ->
                        selected[attribute.name]?.let { value ->
                            val taxonomy = attribute.taxonomy?.takeIf { it.isNotBlank() } ?: "pa_${attribute.name.lowercase().replace(Regex("[^a-z0-9]+"), "_")}"
                            StoreCartVariation(taxonomy, value)
                        }
                    }
                    store.add(currentId, quantity, attrs)
                }, enabled = canAdd, modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(if (product.type == "variable" && selectedVariation == null) "Elige una combinación" else "Añadir al carrito")
                }
            }
            if (loading) CircularProgressIndicator(Modifier.padding(20.dp).size(24.dp))
            error?.let { message -> IosStoreError(message) { error = null; loading = true } }
            if (product.shortDescription.isNotBlank()) Text(product.shortDescription, modifier = Modifier.padding(20.dp))
            if (product.description.isNotBlank()) Text(product.description, modifier = Modifier.padding(20.dp))
        }
    }
    fullscreenPage?.let { initialPage ->
        if (currentImages.isNotEmpty()) {
            Dialog(onDismissRequest = { fullscreenPage = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
                val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, currentImages.lastIndex), pageCount = { currentImages.size })
                Box(Modifier.fillMaxSize().background(Color.Black)) {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        var scale by remember(page) { mutableStateOf(1f) }
                        var offsetX by remember(page) { mutableStateOf(0f) }
                        var offsetY by remember(page) { mutableStateOf(0f) }
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            RemoteStoreImage(
                                currentImages[page].src,
                                product.name,
                                Modifier.fillMaxWidth().pointerInput(page) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        scale = (scale * zoom).coerceIn(1f, 4f)
                                        if (scale > 1f) { offsetX += pan.x; offsetY += pan.y } else { offsetX = 0f; offsetY = 0f }
                                    }
                                }.graphicsLayer { scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY },
                                ContentScale.Fit
                            )
                        }
                    }
                    TextButton(onClick = { fullscreenPage = null }, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) { Text("✕", color = Color.White) }
                    if (currentImages.size > 1) {
                        Row(Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp), horizontalArrangement = Arrangement.Center) {
                            repeat(currentImages.size) { index ->
                                Box(Modifier.padding(horizontal = 4.dp).size(if (pagerState.currentPage == index) 8.dp else 6.dp).background(if (pagerState.currentPage == index) Color.White else Color.White.copy(alpha = .4f), RoundedCornerShape(50)))
                            }
                        }
                    }
                }
            }
        }
    }
}