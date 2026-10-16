package es.criosrango.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreProduct
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

internal fun String.normalizeForIosSearch(): String =
    lowercase()
        .replace(Regex("[áàäâã]"), "a")
        .replace(Regex("[éèëê]"), "e")
        .replace(Regex("[íìïî]"), "i")
        .replace(Regex("[óòöôõ]"), "o")
        .replace(Regex("[úùüû]"), "u")
        .replace('ñ', 'n')
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

internal fun iosSearchDistance(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var previous = IntArray(b.length + 1) { it }
    for (i in a.indices) {
        val current = IntArray(b.length + 1)
        current[0] = i + 1
        for (j in b.indices) {
            val cost = if (a[i] == b[j]) 0 else 1
            current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + cost)
        }
        previous = current
    }
    return previous[b.length]
}

internal fun iosSearchTokenMatches(token: String, text: String): Boolean {
    if (token.isBlank()) return true
    if (text.contains(token)) return true
    if (token.length < 4) return false
    val maxDistance = if (token.length >= 7) 2 else 1
    return text.split(" ").any { word ->
        word.isNotBlank() &&
            kotlin.math.abs(word.length - token.length) <= maxDistance &&
            iosSearchDistance(word, token) <= maxDistance
    }
}

internal fun iosProductSearchText(product: StoreProduct): String =
    buildString {
        append(product.name).append(' ')
        product.categories.forEach {
            append(it.name).append(' ')
            append(it.slug).append(' ')
        }
        product.tags.forEach {
            append(it.name).append(' ')
            append(it.slug).append(' ')
        }
        product.attributes.forEach { attribute ->
            append(attribute.name).append(' ')
            attribute.taxonomy?.let { append(it).append(' ') }
            attribute.terms.forEach {
                append(it.name).append(' ')
                append(it.slug).append(' ')
            }
        }
    }.normalizeForIosSearch()

internal fun iosProductMatchesSearch(product: StoreProduct, normalizedQuery: String): Boolean =
    normalizedQuery.isNotBlank() &&
        normalizedQuery.split(" ").filter { it.isNotBlank() }.all {
            iosSearchTokenMatches(it, iosProductSearchText(product))
        }

internal fun iosSearchRanking(product: StoreProduct, normalizedQuery: String): Int {
    val name = product.name.normalizeForIosSearch()
    val tokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
    return when {
        name == normalizedQuery -> 1000
        name.startsWith(normalizedQuery) -> 800
        name.contains(normalizedQuery) -> 600
        else -> tokens.count { name.contains(it) } * 100
    }
}

@Composable
internal fun IosSearchScreen(
    storeApi: StoreApiClient,
    padding: PaddingValues,
    cartStore: StoreCartStore,
    onProduct: (StoreProduct) -> Unit,
    onCategory: (StoreCategory) -> Unit,
    onBack: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    var products by remember { mutableStateOf(emptyList<StoreProduct>()) }
    var categories by remember { mutableStateOf(emptyList<StoreCategory>()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryAttempt by remember { mutableStateOf(0) }
    val normalizedQuery = submitted.normalizeForIosSearch()
    val gridState = rememberLazyGridState()

    LaunchedEffect(Unit) {
        categories = runCatching { storeApi.categories(perPage = 100) }.getOrDefault(emptyList())
    }

    LaunchedEffect(normalizedQuery, retryAttempt) {
        if (normalizedQuery.isBlank()) {
            products = emptyList()
            loading = false
            error = null
            return@LaunchedEffect
        }
        loading = true
        error = null
        runCatching {
            val tokens = normalizedQuery.split(" ").filter { it.length >= 2 }.distinct()
            coroutineScope {
                if (tokens.size <= 1) {
                    storeApi.products(perPage = 24, page = 1, search = submitted.trim())
                } else {
                    tokens.map { token ->
                        async { storeApi.products(perPage = 24, page = 1, search = token) }
                    }.awaitAll().flatten().distinctBy { it.id }
                }
            }
        }.onSuccess {
            products = it
            loading = false
        }.onFailure {
            products = emptyList()
            error = it.message ?: "No se ha podido buscar."
            loading = false
        }
    }

    val displayedProducts = remember(products, normalizedQuery) {
        products
            .distinctBy { it.id }
            .filter { iosProductMatchesSearch(it, normalizedQuery) }
            .sortedByDescending { iosSearchRanking(it, normalizedQuery) }
    }
    val categorySuggestions = remember(categories, normalizedQuery) {
        if (normalizedQuery.length < 2) emptyList()
        else {
            val tokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
            categories.filter { category ->
                val text = category.name.normalizeForIosSearch()
                tokens.any { iosSearchTokenMatches(it, text) }
            }.distinctBy { it.name.normalizeForIosSearch() }.take(2)
        }
    }
    val brandSuggestions = remember(normalizedQuery) {
        if (normalizedQuery.length < 2) emptyList()
        else {
            val tokens = normalizedQuery.split(" ").filter { it.isNotBlank() }
            storeBrands.filter { brand ->
                val text = brand.name.normalizeForIosSearch()
                tokens.any { iosSearchTokenMatches(it, text) }
            }.take(2)
        }
    }

    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Atrás") }
            Text("Buscar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        OutlinedTextField(
            query,
            { query = it },
            singleLine = true,
            label = { Text("Buscar productos") },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            trailingIcon = { TextButton(onClick = { submitted = query.trim() }) { Text("Buscar") } }
        )
        if (submitted.isBlank()) {
            IosStoreEmpty("Introduce un término de búsqueda.")
        } else {
            if (categorySuggestions.isNotEmpty() || brandSuggestions.isNotEmpty() || displayedProducts.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text("Sugerencias", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    categorySuggestions.forEach { category ->
                        TextButton(
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                            onClick = { onCategory(category) }
                        ) { Text("Categoría · ${category.name}") }
                    }
                    brandSuggestions.forEach { brand ->
                        TextButton(
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                            onClick = { query = brand.name; submitted = brand.name }
                        ) { Text("Marca · ${brand.name}") }
                    }
                    displayedProducts.take(2).forEach { product ->
                        TextButton(
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                            onClick = { onProduct(product) }
                        ) { Text(normalizeWooPlainText(product.name)) }
                    }
                }
            }
            when {
                loading -> IosStoreLoading()
                error != null -> IosStoreError(error!!) { retryAttempt++ }
                displayedProducts.isEmpty() -> IosStoreEmpty("No hemos encontrado productos")
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    state = gridState,
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(displayedProducts, key = { it.id }) {
                        IosProductCard(it, onProduct, cartStore)
                    }
                }
            }
        }
    }
}

@Composable
internal fun IosBrandsScreen(storeApi:StoreApiClient,padding:PaddingValues,cartStore:StoreCartStore,onProduct:(StoreProduct)->Unit,onBack:()->Unit){
    var selected by remember { mutableStateOf<StoreBrand?>(null) }
    if(selected==null) Column(Modifier.fillMaxSize().padding(padding)){
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=4.dp),verticalAlignment=Alignment.CenterVertically){TextButton(onClick=onBack,contentPadding=PaddingValues(horizontal=8.dp,vertical=4.dp)){Text("Atrás")};Text("Nuestras marcas",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis)}
        LazyVerticalGrid(columns=GridCells.Fixed(2),contentPadding=PaddingValues(start=16.dp,top=4.dp,end=16.dp,bottom=28.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            items(storeBrands,key={it.slug}){brand->OutlinedButton(onClick={selected=brand},modifier=Modifier.fillMaxWidth()){Text(brand.name)}}
        }
    } else IosPagedProductScreen(selected!!.name,padding,cartStore,onProduct,{selected=null},{p,n->storeApi.products(p,n,tag=selected!!.slug)},"brand:"+selected!!.slug,enableProductControls=true)
}

@Composable
private fun IosDiscoveryFilterChoices(
    title: String,
    values: List<String>,
    selected: Set<String>,
    onChange: (Set<String>) -> Unit
) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        values.forEach { value ->
            FilterChip(
                selected = value in selected,
                onClick = { onChange(if (value in selected) selected - value else selected + value) },
                label = { Text(value) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IosOutletScreen(
    storeApi: StoreApiClient,
    padding: PaddingValues,
    cartStore: StoreCartStore,
    controller: StoreOutletController,
    showOutletChooser: Boolean,
    onShowOutletChooserChange: (Boolean) -> Unit,
    gridState: LazyGridState,
    categories: List<StoreCategory>?,
    categoriesLoading: Boolean,
    categoriesError: String?,
    onRetry: () -> Unit,
    onProduct: (StoreProduct) -> Unit,
    onBack: () -> Unit
) {
    val outletRootFallback = StoreCategory(id = 445, parent = 0, name = "Outlet", slug = "outlet", count = 0)
    if (categories == null) {
        StoreSubcategoriesScreen(
            parent = outletRootFallback,
            categories = emptyList(),
            loading = categoriesLoading,
            errorMessage = if (categoriesLoading) null else categoriesError ?: "No se han podido cargar las categorías.",
            onBack = onBack,
            onCategory = {},
            showFilterButton = false,
            onRetry = onRetry,
            modifier = Modifier.fillMaxSize().padding(padding)
        )
        return
    }
    val outlet = categories.firstOrNull { it.parent == 0 && it.name.contains("outlet", true) }
    if (outlet == null) {
        StoreSubcategoriesScreen(
            parent = outletRootFallback,
            categories = emptyList(),
            loading = false,
            errorMessage = "Outlet no disponible.",
            onBack = onBack,
            onCategory = {},
            showFilterButton = false,
            onRetry = onRetry,
            modifier = Modifier.fillMaxSize().padding(padding)
        )
        return
    }

    val outletChildren = categories.filter { it.parent == outlet.id }.distinctBy { it.id }
    val controllerState by controller.state.collectAsState()
    var availabilityError by remember(outlet.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(controllerState.activeCategoryId, controllerState.activeFilterCount, outlet.id) {
        val activeCategoryId = controllerState.activeCategoryId
        val isRootCategory = activeCategoryId == null || activeCategoryId == outlet.id
        onShowOutletChooserChange(isRootCategory && controllerState.activeFilterCount == 0)
    }

    LaunchedEffect(controller, categories) {
        controller.updateCategories(categories)
    }
    LaunchedEffect(controller, outlet.id) {
        runCatching { storeApi.outletAvailability() }
            .onSuccess { controller.updateAvailability(it) }
            .onFailure {
                controller.updateAvailability(null)
                availabilityError = it.message ?: "No se ha podido cargar la disponibilidad."
            }
    }

    if (showOutletChooser) {
        StoreSubcategoriesScreen(
            parent = outlet,
            categories = outletChildren,
            loading = false,
            onBack = onBack,
            onCategory = { category ->
                controller.selectCategory(category.id)
                onShowOutletChooserChange(false)
            },
            onFilterClick = {
                controller.selectCategory(outlet.id)
                controller.openFilters()
            },
            showFilterButton = true,
            modifier = Modifier.fillMaxSize().padding(padding)
        )
    } else {
        val activeCategoryId = controllerState.activeCategoryId ?: outlet.id
        val showBubbles = activeCategoryId != outlet.id
        val paging = controllerState.paging
    
        LaunchedEffect(gridState, controller, controllerState.activeCategoryId) {
            snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
                .collect { lastVisible -> controller.onVisibleRangeChanged(lastVisible) }
        }
        LaunchedEffect(
            controllerState.activeCategoryId,
            controllerState.selectedBubbleKey,
            controllerState.selectedSort,
            controllerState.selectedSizes,
            controllerState.selectedColors,
            controllerState.selectedBrands
        ) {
            if (controllerState.sortedProducts.isNotEmpty()) gridState.scrollToItem(0)
        }
    
        val activeChips = buildList {
            controllerState.selectedSizes.sorted().forEach { add(StoreActiveFilterChipUi("size:$it", "Talla $it")) }
            controllerState.selectedColors.sorted().forEach { add(StoreActiveFilterChipUi("color:$it", it)) }
            controllerState.selectedBrands.sorted().forEach { add(StoreActiveFilterChipUi("brand:$it", it)) }
        }
        val listState = StoreProductListUiState(
            title = controllerState.screenTitle,
            items = controllerState.sortedProducts.map { product ->
                StoreProductItemUi(
                    id = product.id,
                    nameText = normalizeWooPlainText(product.name),
                    imageIdentity = product.images.firstOrNull()?.src,
                    displayPriceText = formatStorePrice(product.prices.price, product.prices.currencyMinorUnit, product.prices.currencySymbol),
                    regularPriceText = formatStorePrice(product.prices.regularPrice, product.prices.currencyMinorUnit, product.prices.currencySymbol),
                    showPrice = product.prices.price.toLongOrNull()?.let { it > 0L } == true,
                    isOnSale = product.onSale
                )
            },
            selectedSort = when (controllerState.selectedSort) {
                StoreOutletSort.RECENT -> StoreProductSortOption.RECENT
                StoreOutletSort.PRICE_ASC -> StoreProductSortOption.PRICE_ASC
                StoreOutletSort.PRICE_DESC -> StoreProductSortOption.PRICE_DESC
                StoreOutletSort.NAME_ASC -> StoreProductSortOption.NAME_ASC
            },
            availableSortOptions = StoreProductSortOption.values().toList(),
            activeFilterCount = controllerState.activeFilterCount,
            activeFilterChips = activeChips,
            isInitialLoading = paging.isInitialLoading,
            isLoadingMore = paging.isAppending,
            isEmpty = controllerState.sortedProducts.isEmpty() && !controllerState.isProbingSelectedBubble && !paging.hasMore,
            emptyMessage = if (controllerState.selectedSizes.isNotEmpty() || controllerState.selectedColors.isNotEmpty() || controllerState.selectedBrands.isNotEmpty()) "No hay productos con estos filtros." else "No hay productos.",
            initialErrorMessage = paging.initialError?.message ?: if (paging.initialError != null) "No se han podido cargar los productos." else null,
            appendErrorMessage = paging.appendError?.message ?: if (paging.appendError != null) "No se han podido cargar más productos." else null,
            appendRetryLabel = "Reintentar",
            canLoadMore = paging.hasMore,
            isProbingSelectedBubble = showBubbles && controllerState.isProbingSelectedBubble
        )
    
        StoreProductListScreen(
                state = listState,
                gridState = gridState,
                modifier = Modifier.fillMaxSize().padding(padding),
                onBack = {
                    if (showBubbles && controllerState.selectedBubbleKey != null) {
                        controller.selectBubble(null)
                    } else {
                        controller.selectCategory(outlet.id)
                        onShowOutletChooserChange(true)
                    }
                },
                onProductSelected = { id ->
                    controllerState.sortedProducts.firstOrNull { it.id == id }?.let(onProduct)
                },
                onSortSelected = { option ->
                    controller.selectSort(when (option) {
                        StoreProductSortOption.RECENT -> StoreOutletSort.RECENT
                        StoreProductSortOption.PRICE_ASC -> StoreOutletSort.PRICE_ASC
                        StoreProductSortOption.PRICE_DESC -> StoreOutletSort.PRICE_DESC
                        StoreProductSortOption.NAME_ASC -> StoreOutletSort.NAME_ASC
                    })
                },
                onFilterClick = controller::openFilters,
                onRemoveFilter = { id ->
                    when {
                        id.startsWith("size:") -> controller.selectSizes(controllerState.selectedSizes - id.removePrefix("size:"))
                        id.startsWith("color:") -> controller.selectColors(controllerState.selectedColors - id.removePrefix("color:"))
                        id.startsWith("brand:") -> controller.selectBrands(controllerState.selectedBrands - id.removePrefix("brand:"))
                    }
                },
                onClearFilters = controller::clearFilters,
                onRetryInitial = controller::retryInitial,
                onRetryAppend = controller::retryAppend,
                headerContent = {
                    if (showBubbles) {
                        StoreOutletBubbles(
                            bubbles = controllerState.visibleBubbles,
                            selectedKey = controllerState.selectedBubbleKey,
                            onSelected = controller::selectBubble
                        )
                    }
                    availabilityError?.let {
                        Text(it, modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp))
                    }
                },
                imageContent = { item, imageModifier ->
                    val product = controllerState.sortedProducts.firstOrNull { it.id == item.id }
                    RemoteStoreImage(
                        product?.images?.firstOrNull()?.src.orEmpty(),
                        item.nameText,
                        imageModifier,
                        androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
            )
    }

    if (controllerState.filterDialogVisible) {
        when (val optionsState = controllerState.filterOptionsState) {
            StoreFilterOptionsState.Idle, StoreFilterOptionsState.Loading -> AlertDialog(
                onDismissRequest = controller::cancelFilters,
                title = { Text("Filtros") },
                text = { Text("Cargando opciones de filtros…") },
                confirmButton = {},
                dismissButton = { TextButton(onClick = controller::cancelFilters) { Text("Cancelar") } }
            )
            is StoreFilterOptionsState.Error -> AlertDialog(
                onDismissRequest = controller::cancelFilters,
                title = { Text("No se han podido cargar los filtros") },
                text = { Text(optionsState.message) },
                confirmButton = { TextButton(onClick = controller::retryFilterOptions) { Text("Reintentar") } },
                dismissButton = { TextButton(onClick = controller::cancelFilters) { Text("Cancelar") } }
            )
            is StoreFilterOptionsState.Ready -> StoreCatalogFilterDialog(
                sizes = controllerState.sizes,
                colors = controllerState.colors,
                brands = controllerState.brands,
                selectedSizes = controllerState.draftSizes,
                selectedColors = controllerState.draftColors,
                selectedBrands = controllerState.draftBrands,
                enabledSizes = controllerState.enabledSizes,
                enabledColors = controllerState.enabledColors,
                enabledBrands = controllerState.enabledBrands,
                showBrands = true,
                onDimensionSelected = controller::selectFacetDimension,
                onSizesChanged = controller::changeDraftSizes,
                onColorsChanged = controller::changeDraftColors,
                onBrandsChanged = controller::changeDraftBrands,
                onApply = { controller.applyFilters() },
                onClear = controller::clearDraftFilters,
                onDismiss = controller::cancelFilters,
                onCancel = controller::cancelFilters
            )
        }
    }
}

@Composable
internal fun IosPagedProductScreen(
    title: String,
    padding: PaddingValues,
    cartStore: StoreCartStore,
    onProduct: (StoreProduct) -> Unit,
    onBack: () -> Unit,
    load: suspend (Int, Int) -> List<StoreProduct>,
    queryKey: String,
    transform: (List<StoreProduct>) -> List<StoreProduct> = { it },
    emptyMessage: String = "No hay productos.",
    emptyActionLabel: String? = null,
    onEmptyAction: (() -> Unit)? = null,
    headerContent: (@Composable ColumnScope.() -> Unit)? = null,
    enableProductControls: Boolean = false,
    initialFiltersOpen: Boolean = false
) {
    val scope=rememberCoroutineScope(); val paginator=remember(queryKey){CatalogPaginatorStore<StoreProduct>(scope, pageSize = if (queryKey.startsWith("novedades:")) 24 else CatalogPaginator.PAGE_SIZE) { it.id }}; val state by paginator.state.collectAsState(); val grid=rememberLazyGridState()
    var sortMode by rememberSaveable(queryKey) { mutableStateOf(IosCatalogSortModeState.RECENT) }
    var filtersOpen by rememberSaveable(queryKey) { mutableStateOf(initialFiltersOpen) }
    var selectedSizes by rememberSaveable(queryKey) { mutableStateOf(setOf<String>()) }
    var selectedColors by rememberSaveable(queryKey) { mutableStateOf(setOf<String>()) }
    var selectedBrands by rememberSaveable(queryKey) { mutableStateOf(setOf<String>()) }
    val rawItems = state.items
    fun productValues(product: StoreProduct, attribute: String): Set<String> =
        product.attributes.filter { it.name.equals(attribute, true) }.flatMap { it.terms.map { term -> term.name } }.filter { it.isNotBlank() }.toSet()
    fun matches(product: StoreProduct): Boolean {
        val sizes = productValues(product, "Tallas")
        val colors = productValues(product, "Color")
        val brands = product.tags.map { it.name }.filter { it.isNotBlank() }.toSet()
        return (selectedSizes.isEmpty() || sizes.any { it in selectedSizes }) &&
            (selectedColors.isEmpty() || colors.any { it in selectedColors }) &&
            (selectedBrands.isEmpty() || brands.any { it in selectedBrands })
    }
    val filteredItems = rawItems.filter(::matches)
    val displayedItems = when (sortMode) {
        IosCatalogSortModeState.RECENT -> filteredItems
        IosCatalogSortModeState.PRICE_ASC -> filteredItems.sortedBy { it.prices.price.toLongOrNull() ?: Long.MAX_VALUE }
        IosCatalogSortModeState.PRICE_DESC -> filteredItems.sortedByDescending { it.prices.price.toLongOrNull() ?: Long.MIN_VALUE }
        IosCatalogSortModeState.NAME_ASC -> filteredItems.sortedBy { it.name.lowercase() }
    }
    val availableSizes = rawItems.flatMap { productValues(it, "Tallas") }.distinct().sorted()
    val availableColors = rawItems.flatMap { productValues(it, "Color") }.distinct().sorted()
    val availableBrands = rawItems.flatMap { it.tags.map { tag -> tag.name } }.filter { it.isNotBlank() }.distinct().sorted()
    val activeFilterCount = selectedSizes.size + selectedColors.size + selectedBrands.size
    LaunchedEffect(queryKey){paginator.start(queryKey){p,n->val items=load(p,n);CatalogPage(items,items.size>=n)}}
    LaunchedEffect(grid,state.items.size,state.hasMore){snapshotFlow{grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index?:-1}.collect{last->if(state.hasMore&&!state.isInitialLoading&&!state.isAppending&&last>=state.items.size-CatalogPaginator.PREFETCH_DISTANCE)paginator.loadNext{p,n->val items=load(p,n);CatalogPage(items,items.size>=n)}}}
    Column(Modifier.fillMaxSize().padding(padding)){
        if(title.isNotBlank())Row(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=2.dp),verticalAlignment=Alignment.CenterVertically){
            TextButton(onClick=onBack){Text("Atrás")}
            Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
            if (enableProductControls) {
                OutlinedButton(onClick={filtersOpen=true},contentPadding=PaddingValues(horizontal=12.dp,vertical=4.dp)){
                    Text(if(activeFilterCount==0)"Filtros" else "Filtros ("+activeFilterCount+")")
                }
            }
        }
        if (enableProductControls && state.items.isNotEmpty()) {
            if (activeFilterCount > 0) {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    selectedSizes.sorted().forEach { value -> FilterChip(true, { selectedSizes = selectedSizes - value }, label = { Text("Talla " + value) }, trailingIcon = { Text("×") }) }
                    selectedColors.sorted().forEach { value -> FilterChip(true, { selectedColors = selectedColors - value }, label = { Text(value) }, trailingIcon = { Text("×") }) }
                    selectedBrands.sorted().forEach { value -> FilterChip(true, { selectedBrands = selectedBrands - value }, label = { Text(value) }, trailingIcon = { Text("×") }) }
                    TextButton(onClick = { selectedSizes = emptySet(); selectedColors = emptySet(); selectedBrands = emptySet() }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("Borrar filtros") }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Ordenar por", style = MaterialTheme.typography.bodyMedium)
                Box {
                    var expanded by remember(queryKey) { mutableStateOf(false) }
                    TextButton(onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("${when (sortMode) {
    IosCatalogSortModeState.RECENT -> "Más recientes"
    IosCatalogSortModeState.PRICE_ASC -> "Precio: menor a mayor"
    IosCatalogSortModeState.PRICE_DESC -> "Precio: mayor a menor"
    IosCatalogSortModeState.NAME_ASC -> "Nombre A-Z"
}}  ▾")
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        IosCatalogSortModeState.values().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(when(option) {
                                    IosCatalogSortModeState.RECENT -> "Más recientes"
                                    IosCatalogSortModeState.PRICE_ASC -> "Precio: menor a mayor"
                                    IosCatalogSortModeState.PRICE_DESC -> "Precio: mayor a menor"
                                    IosCatalogSortModeState.NAME_ASC -> "Nombre A-Z"
                                }) },
                                onClick = { expanded = false; sortMode = option }
                            )
                        }
                    }
                }

            }
        }
        if (enableProductControls && filtersOpen) {
            AlertDialog(
                onDismissRequest = { filtersOpen = false },
                title = { Text("Filtros") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        IosDiscoveryFilterChoices("Talla", availableSizes, selectedSizes) { selectedSizes = it }
                        Spacer(Modifier.height(16.dp))
                        IosDiscoveryFilterChoices("Color", availableColors, selectedColors) { selectedColors = it }
                        if (availableBrands.isNotEmpty()) {
                            Spacer(Modifier.height(16.dp))
                            IosDiscoveryFilterChoices("Marca", availableBrands, selectedBrands) { selectedBrands = it }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { filtersOpen = false }) { Text("Aplicar") } },
                dismissButton = { TextButton(onClick = { selectedSizes = emptySet(); selectedColors = emptySet(); selectedBrands = emptySet() }) { Text("Limpiar") } }
            )
        }

        when{
            state.isInitialLoading->IosStoreLoading()
            state.initialError!=null->IosStoreError(state.initialError!!.message?:"No se ha podido cargar."){paginator.start(queryKey){p,n->val items=load(p,n);CatalogPage(items,items.size>=n)}}
            state.items.isEmpty()->IosStoreEmpty(emptyMessage,onEmptyAction,emptyActionLabel)
            else->Column { headerContent?.invoke(this); LazyVerticalGrid(columns=GridCells.Fixed(2),state=grid,contentPadding=PaddingValues(start=12.dp,top=4.dp,end=12.dp,bottom=28.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                items(transform(displayedItems),key={it.id}){IosProductCard(it,onProduct,cartStore)}
                if(state.isAppending)item(span={GridItemSpan(maxLineSpan)}){IosStoreLoading()}
                state.appendError?.let{e->item(span={GridItemSpan(maxLineSpan)}){IosStoreError(e.message?:"Error"){paginator.loadNext{p,n->val items=load(p,n);CatalogPage(items,items.size>=n)}}}}
            }
            }
        }
    }
}