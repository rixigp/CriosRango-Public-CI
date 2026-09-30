package es.criosrango.shared

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
    LaunchedEffect(initialProduct.id, loading) {
        runCatching { storeApi.product(initialProduct.id) }
            .onSuccess { product = it }
            .onFailure { error = it.message ?: "No se ha podido cargar el producto." }
        loading = false
    }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            TextButton(onClick = onBack) { Text("Atrás") }
            RemoteStoreImage(
                url = product.images.firstOrNull()?.src,
                contentDescription = product.name,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            )
            Text(product.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(20.dp, 12.dp, 20.dp, 4.dp))
            if (product.hasDisplayablePrice) {
                Text(formatStorePrice(product.prices.price, product.prices.currencyMinorUnit, product.prices.currencySymbol), fontWeight = FontWeight.Bold, color = Color(0xFF183B35), modifier = Modifier.padding(horizontal = 20.dp))
            }
            cartStore?.let { store ->
                var selectedVariation by remember(product.id) { mutableStateOf(product.variations.firstOrNull()) }
                if (product.variations.isNotEmpty()) {
                    Text("Variantes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(20.dp, 16.dp, 20.dp, 4.dp))
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        product.variations.forEach { variation ->
                            OutlinedButton(onClick = { selectedVariation = variation }, modifier = Modifier.fillMaxWidth()) {
                                Text(variation.attributes.joinToString(" · ") { it.value }.ifBlank { "Variación " + variation.id })
                            }
                        }
                    }
                }
                Button(onClick = {
                    val variation = selectedVariation
                    val attrs = variation?.attributes.orEmpty().map { attr -> StoreCartVariation(attr.name.ifBlank { "pa_attribute" }, attr.value) }
                    store.add(product.id, 1, attrs)
                }, enabled = product.isPurchasable != false && (selectedVariation?.isPurchasable != false), modifier = Modifier.fillMaxWidth().padding(20.dp)) { Text("Añadir al carrito") }
            }
            if (loading) CircularProgressIndicator(Modifier.padding(20.dp).size(24.dp))
            error?.let { message ->
                IosStoreError(message) {
                    error = null
                    loading = true
                }
            }
        }
    }
}

