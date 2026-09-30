package es.criosrango.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreProduct
import es.criosrango.shared.model.StoreCartVariation

@Composable
internal fun IosCatalogScreen(
    storeApi: es.criosrango.shared.api.StoreApiClient,
    padding: PaddingValues,
    page: IosCatalogPage,
    cartStore: StoreCartStore,
    onOpenCategory: (StoreCategory) -> Unit,
    onOpenProduct: (StoreProduct) -> Unit,
    onBack: () -> Unit
) {
    when (page) {
        IosCatalogPage.Root -> IosCategoryRoot(storeApi, padding, onOpenCategory)
        is IosCatalogPage.Category -> IosCategoryProducts(storeApi, padding, page.category, onOpenProduct, onBack, cartStore)
        is IosCatalogPage.Product -> IosProductDetail(storeApi, padding, page.product, onBack, cartStore)
        IosCatalogPage.Novedades -> IosNovedadesScreen(storeApi, padding, cartStore, onOpenProduct, onBack)
        IosCatalogPage.Search -> IosSearchScreen(storeApi, padding, cartStore, onOpenProduct, onBack)
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
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
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
internal fun IosCategoryProducts(
    storeApi: es.criosrango.shared.api.StoreApiClient,
    padding: PaddingValues,
    category: StoreCategory,
    onOpenProduct: (StoreProduct) -> Unit,
    onBack: () -> Unit,
    cartStore: StoreCartStore
) {
    val scope = rememberCoroutineScope()
    val paginator = remember(category.id) {
        CatalogPaginatorStore<StoreProduct>(scope, identity = { it.id })
    }
    val pagingState by paginator.state.collectAsState()
    val gridState = rememberLazyGridState()

    LaunchedEffect(category.id) {
        paginator.start("category:" + category.id) { page, perPage ->
            val items = storeApi.products(perPage = perPage, page = page, category = category.id)
            CatalogPage(items, hasMore = items.size >= perPage)
        }
    }

    LaunchedEffect(gridState, pagingState.items.size, pagingState.hasMore) {
        snapshotFlow {
            gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.collect { lastVisible ->
            if (
                pagingState.hasMore &&
                !pagingState.isInitialLoading &&
                !pagingState.isAppending &&
                lastVisible >= pagingState.items.size - CatalogPaginator.PREFETCH_DISTANCE
            ) {
                paginator.loadNext { page, perPage ->
                    val items = storeApi.products(perPage = perPage, page = page, category = category.id)
                    CatalogPage(items, hasMore = items.size >= perPage)
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Atrás") }
            Text(category.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        when {
            pagingState.isInitialLoading -> IosStoreLoading()
            pagingState.initialError != null -> IosStoreError(pagingState.initialError?.message ?: "No se han podido cargar los productos.") {
                paginator.start("category:" + category.id) { page, perPage ->
                    val items = storeApi.products(perPage = perPage, page = page, category = category.id)
                    CatalogPage(items, hasMore = items.size >= perPage)
                }
            }
            pagingState.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No hay productos en esta categoría.") }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(pagingState.items, key = { it.id }) { IosProductCard(it, onOpenProduct, cartStore) }
                if (pagingState.isAppending) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp)) }
                    }
                }
                if (pagingState.appendError != null) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        IosStoreError(pagingState.appendError?.message ?: "No se han podido cargar más productos.") {
                            paginator.loadNext { page, perPage ->
                                val items = storeApi.products(perPage = perPage, page = page, category = category.id)
                                CatalogPage(items, hasMore = items.size >= perPage)
                            }
                        }
                    }
                }
            }
        }
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
    val selected = remember(initialProduct.id) { mutableStateMapOf<String, String>() }
    LaunchedEffect(initialProduct.id, loading) {
        if (!loading) return@LaunchedEffect
        runCatching { storeApi.productWithVariationAvailability(initialProduct.id) }
            .onSuccess { loaded ->
                product = loaded
                loaded.attributes.forEach { attribute ->
                    attribute.terms.firstOrNull { it.default }?.let { selected.putIfAbsent(attribute.name, it.slug) }
                }
            }
            .onFailure { error = it.message ?: "No se ha podido cargar el producto." }
        loading = false
    }
    val selectedVariation = remember(product, selected.toMap()) {
        if (product.type != "variable") null else product.variations.firstOrNull { variation ->
            product.attributes.filter { it.terms.isNotEmpty() }.all { attribute ->
                val wanted = selected[attribute.name]
                wanted != null && variation.attributes.any { it.name == attribute.name && it.value == wanted }
            }
        }
    }
    val currentId = selectedVariation?.id ?: product.id
    val currentImages = selectedVariation?.images?.takeIf { it.isNotEmpty() } ?: product.images
    val currentPrices = selectedVariation?.prices ?: product.prices
    val currentInStock = selectedVariation?.isInStock ?: product.isInStock
    val currentPurchasable = selectedVariation?.isPurchasable ?: product.isPurchasable
    val limits = selectedVariation?.quantityLimits ?: selectedVariation?.addToCart ?: product.quantityLimits ?: product.addToCart
    val minimum = limits?.minimum ?: 1
    val maximum = limits?.maximum
    val multiple = limits?.multipleOf?.takeIf { it > 0 } ?: 1
    val canAdd = currentInStock && currentPurchasable != false && (product.type != "variable" || selectedVariation != null)
    LaunchedEffect(currentId, minimum, maximum, multiple) {
        quantity = quantity.coerceAtLeast(minimum)
        maximum?.let { quantity = quantity.coerceAtMost(it) }
        if ((quantity - minimum) % multiple != 0) quantity = minimum
    }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("Atrás") }
                Text("Detalle", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
        item {
            if (product.images.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(currentImages, key = { it.src }) { image ->
                        RemoteStoreImage(image.src, product.name, Modifier.width(300.dp).aspectRatio(.78f))
                    }
                }
            }
            Text(product.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp, 16.dp, 20.dp, 4.dp))
            if (currentPrices.price.toLongOrNull()?.let { it > 0L } == true) Text(formatStorePrice(currentPrices.price, currentPrices.currencyMinorUnit, currentPrices.currencySymbol), fontWeight = FontWeight.Bold, color = Color(0xFF183B35), modifier = Modifier.padding(horizontal = 20.dp))
            product.attributes.filter { it.terms.isNotEmpty() }.forEach { attribute ->
                Text(attribute.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(20.dp, 18.dp, 20.dp, 6.dp))
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    attribute.terms.forEach { term ->
                        FilterChip(selected[attribute.name] == term.slug, { selected[attribute.name] = term.slug }, label = { Text(term.name) })
                    }
                }
            }
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
}