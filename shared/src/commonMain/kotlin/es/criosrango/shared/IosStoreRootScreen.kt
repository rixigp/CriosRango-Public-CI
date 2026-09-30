package es.criosrango.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.criosrango.shared.account.AccountRepository
import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class IosRootSection { HOME, CATEGORIES, CART, ACCOUNT }
internal sealed class IosCatalogPage {
    data object Root : IosCatalogPage()
    data class Category(val category: StoreCategory) : IosCatalogPage()
    data class Product(val product: StoreProduct) : IosCatalogPage()
    data object Search : IosCatalogPage()
    data object Novedades : IosCatalogPage()
    data object Brands : IosCatalogPage()
    data class Brand(val name: String, val slug: String) : IosCatalogPage()
    data class Outlet(val categoryId: Int, val name: String) : IosCatalogPage()
}

private data class IosOutletCategory(val id: Int, val name: String)
private val IOS_OUTLET_CATEGORIES = listOf(
    IosOutletCategory(446, "Hombre invierno"), IosOutletCategory(475, "Hombre verano"),
    IosOutletCategory(447, "Mujer invierno"), IosOutletCategory(476, "Mujer verano"),
    IosOutletCategory(449, "Niña invierno"), IosOutletCategory(478, "Niña verano"),
    IosOutletCategory(448, "Niño invierno"), IosOutletCategory(477, "Niño verano")
)
private data class IosOutletBubble(val label: String, val categoryIds: Set<Int>)
private fun iosOutletBubbles(id: Int): List<IosOutletBubble> = when (id) {
    446, 475 -> listOf(
        IosOutletBubble("Abrigos", setOf(430)), IosOutletBubble("Americanas", setOf(320)),
        IosOutletBubble("Camisas", setOf(318)), IosOutletBubble("Camisetas", setOf(319)),
        IosOutletBubble("Complementos", setOf(317)), IosOutletBubble("Jerséis", setOf(459)),
        IosOutletBubble("Pantalones", setOf(321)), IosOutletBubble("Sudaderas", setOf(471))
    )
    447, 476 -> listOf(
        IosOutletBubble("Abrigos", setOf(431)), IosOutletBubble("Camisas", setOf(324)),
        IosOutletBubble("Chaquetas", setOf(326)), IosOutletBubble("Complementos", setOf(468)),
        IosOutletBubble("Jerséis", setOf(461)), IosOutletBubble("Pantalones", setOf(325)),
        IosOutletBubble("Fiesta", setOf(323)), IosOutletBubble("Vestidos y conjuntos", setOf(322))
    )
    449, 478 -> listOf(
        IosOutletBubble("Abrigos", setOf(433, 428)), IosOutletBubble("Calzado", setOf(421)),
        IosOutletBubble("Baño", setOf(80, 286)), IosOutletBubble("Sport", setOf(313, 289)),
        IosOutletBubble("Vestir", setOf(316, 311))
    )
    448, 477 -> listOf(
        IosOutletBubble("Abrigos", setOf(434, 429)), IosOutletBubble("Baño", setOf(78, 287)),
        IosOutletBubble("Sport", setOf(314, 290)), IosOutletBubble("Vestir", setOf(315, 312))
    )
    else -> emptyList()
}
private fun iosProductBelongsToBubble(product: StoreProduct, bubble: IosOutletBubble, byId: Map<Int, StoreCategory>): Boolean {
    val assigned = product.categories.map { it.id } + product.originalCategoryIds
    return assigned.any { assignedId ->
        var current = assignedId
        val visited = mutableSetOf<Int>()
        while (current != 0 && visited.add(current)) {
            if (current in bubble.categoryIds) return@any true
            current = byId[current]?.parent ?: 0
        }
        false
    }
}

@Composable
fun CriosRangoIOSRootScreen(
    storeApi: StoreApiClient,
    accountRepository: AccountRepository,
    cartStore: StoreCartStore,
    checkoutStore: StoreCheckoutStore,
    paymentStore: StorePaymentStore,
    outletAvailabilityStore: IosOutletAvailabilityStore,
    onOpenPayment: (String) -> Unit,
    onOpenExternalUrl: (String) -> Unit
) {
    var section by remember { mutableStateOf(IosRootSection.HOME) }
    var checkoutOpen by remember { mutableStateOf(false) }
    var catalogPage by remember { mutableStateOf<IosCatalogPage>(IosCatalogPage.Root) }
    val createdOrder by checkoutStore.createdOrder.collectAsState()
    LaunchedEffect(createdOrder?.orderId, createdOrder?.orderKey) {
        val order = createdOrder ?: return@LaunchedEffect
        val id = order.orderId ?: return@LaunchedEffect
        val key = order.orderKey?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        accountRepository.prepareClaimOrder(id, key)
        if (accountRepository.hasSession) runCatching { accountRepository.claimPendingOrder() }
    }
    MaterialTheme {
        Scaffold(bottomBar = {
            IosMainTabBar(section, cartStore.cart.collectAsState().value.itemsCount) {
                section = it
                if (it == IosRootSection.CATEGORIES) catalogPage = IosCatalogPage.Root
            }
        }) { padding ->
            if (checkoutOpen) IosCheckoutScreen(
                checkoutStore, paymentStore, accountRepository, padding,
                onBack = { checkoutOpen = false }, onOpenPayment = onOpenPayment
            ) else when (section) {
                IosRootSection.HOME -> IosHomeScreen(
                    storeApi, cartStore, outletAvailabilityStore, padding,
                    onCategory = { section = IosRootSection.CATEGORIES; catalogPage = IosCatalogPage.Category(it) },
                    onProduct = { section = IosRootSection.CATEGORIES; catalogPage = IosCatalogPage.Product(it) },
                    onPage = { section = IosRootSection.CATEGORIES; catalogPage = it }
                )
                IosRootSection.CATEGORIES -> IosCatalogScreen(
                    storeApi, cartStore, outletAvailabilityStore, padding, catalogPage,
                    onOpenCategory = { catalogPage = IosCatalogPage.Category(it) },
                    onOpenProduct = { catalogPage = IosCatalogPage.Product(it) },
                    onOpenPage = { catalogPage = it },
                    onBack = { catalogPage = IosCatalogPage.Root }
                )
                IosRootSection.CART -> IosCartScreen(cartStore, padding, { checkoutOpen = true }) {
                    section = IosRootSection.CATEGORIES; catalogPage = IosCatalogPage.Product(it)
                }
                IosRootSection.ACCOUNT -> CriosRangoIOSAccountScreen(
                    repository = accountRepository, modifier = Modifier.padding(padding),
                    onOpenExternalUrl = onOpenExternalUrl
                )
            }
        }
    }
}

@Composable
private fun IosMainTabBar(selected: IosRootSection, cartCount: Int, onSelected: (IosRootSection) -> Unit) {
    Surface(shadowElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().height(68.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(IosRootSection.HOME to "Inicio", IosRootSection.CATEGORIES to "Categorías", IosRootSection.CART to "Carrito", IosRootSection.ACCOUNT to "Cuenta").forEach { (item, label) ->
                val active = item == selected
                Column(Modifier.weight(1f).fillMaxSize().clickable { onSelected(item) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(label, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                    if (item == IosRootSection.CART && cartCount > 0) Text(cartCount.toString(), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun IosHomeScreen(
    storeApi: StoreApiClient, cartStore: StoreCartStore, outletAvailabilityStore: IosOutletAvailabilityStore,
    padding: PaddingValues, onCategory: (StoreCategory) -> Unit, onProduct: (StoreProduct) -> Unit,
    onPage: (IosCatalogPage) -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var products by remember { mutableStateOf(emptyList<StoreProduct>()) }
    var categories by remember { mutableStateOf(emptyList<StoreCategory>()) }
    LaunchedEffect(loading) {
        if (!loading) return@LaunchedEffect
        runCatching {
            storeApi.products(perPage = 24, page = 1, orderBy = "date", order = "desc") to storeApi.categories(perPage = 100)
        }.onSuccess { (p, c) -> products = p; categories = c; loading = false; error = null }
            .onFailure { loading = false; error = iosCatalogErrorMessage(it) }
    }
    LaunchedEffect(Unit) { runCatching { outletAvailabilityStore.refresh() } }
    Column(Modifier.fillMaxSize().padding(padding)) {
        when {
            loading -> IosStoreLoading()
            error != null && products.isEmpty() && categories.isEmpty() -> IosStoreError(error!!) { loading = true }
            products.isEmpty() && categories.isEmpty() -> IosStoreEmpty("No hay contenido disponible.")
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                item { IosHomeHero() }
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Buscar" to IosCatalogPage.Search, "Novedades" to IosCatalogPage.Novedades, "Marcas" to IosCatalogPage.Brands, "Outlet" to IosCatalogPage.Outlet(446, "Outlet")).forEach { (label, target) ->
                            OutlinedButton(onClick = { onPage(target) }, modifier = Modifier.weight(1f)) { Text(label, maxLines = 1) }
                        }
                    }
                }
                item {
                    IosSectionHeader("Categorías")
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(categories.filter { it.parent == 0 && !it.name.equals("Outlet", true) }.distinctBy { it.id }.take(10), key = { it.id }) {
                            IosCategoryChip(it, onCategory)
                        }
                    }
                }
                item {
                    IosSectionHeader("Novedades")
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(products.take(8), key = { it.id }) { IosProductCard(it, onProduct, cartStore) }
                    }
                    TextButton(onClick = { onPage(IosCatalogPage.Novedades) }, Modifier.padding(horizontal = 20.dp)) { Text("Ver todo") }
                }
                item {
                    IosSectionHeader("Outlet")
                    IosOutletTopLevel(outletAvailabilityStore.snapshot.collectAsState().value) { onPage(IosCatalogPage.Outlet(it.id, it.name)) }
                }
            }
        }
    }
}

@Composable
private fun IosCatalogScreen(
    storeApi: StoreApiClient, cartStore: StoreCartStore, outletAvailabilityStore: IosOutletAvailabilityStore,
    padding: PaddingValues, page: IosCatalogPage, onOpenCategory: (StoreCategory) -> Unit,
    onOpenProduct: (StoreProduct) -> Unit, onOpenPage: (IosCatalogPage) -> Unit, onBack: () -> Unit
) {
    when (page) {
        IosCatalogPage.Root -> IosCategoryRoot(storeApi, padding, onOpenCategory)
        is IosCatalogPage.Category -> IosPagedProductGrid("category:" + page.category.id, page.category.name, storeApi, cartStore, padding, onBack, onOpenProduct) { p, size ->
            val items = storeApi.products(perPage = size, page = p, category = page.category.id)
            CatalogPage(items, items.size >= size)
        }
        IosCatalogPage.Search -> IosSearchScreen(storeApi, cartStore, padding, onOpenProduct, onBack)
        IosCatalogPage.Novedades -> IosPagedProductGrid("novedades", "Novedades", storeApi, cartStore, padding, onBack, onOpenProduct) { p, size ->
            val items = storeApi.products(perPage = size, page = p, orderBy = "date", order = "desc")
            CatalogPage(items, items.size >= size)
        }
        IosCatalogPage.Brands -> IosBrandsScreen(padding, onBack) { name, slug -> onOpenPage(IosCatalogPage.Brand(name, slug)) }
        is IosCatalogPage.Brand -> IosPagedProductGrid("brand:" + page.slug, page.name, storeApi, cartStore, padding, onBack, onOpenProduct) { p, size ->
            val items = storeApi.products(perPage = size, page = p, tag = page.slug)
            CatalogPage(items, items.size >= size)
        }
        is IosCatalogPage.Outlet -> IosOutletScreen(storeApi, cartStore, outletAvailabilityStore, padding, page.categoryId, page.name, onBack, onOpenProduct)
        is IosCatalogPage.Product -> IosProductDetail(storeApi, padding, page.product, onBack, cartStore)
    }
}

@Composable
private fun IosCategoryRoot(storeApi: StoreApiClient, padding: PaddingValues, onOpenCategory: (StoreCategory) -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var categories by remember { mutableStateOf(emptyList<StoreCategory>()) }
    LaunchedEffect(loading) {
        if (!loading) return@LaunchedEffect
        runCatching { storeApi.categories(perPage = 100) }
            .onSuccess { categories = it; loading = false; error = null }
            .onFailure { error = iosCatalogErrorMessage(it); loading = false }
    }
    Column(Modifier.fillMaxSize().padding(padding)) {
        Text("Categorías", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(20.dp))
        when {
            loading -> IosStoreLoading()
            error != null -> IosStoreError(error!!) { error = null; loading = true }
            categories.isEmpty() -> IosStoreEmpty("No hay categorías.")
            else -> LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(categories.filter { it.parent == 0 && !it.name.equals("Outlet", true) }.distinctBy { it.id }, key = { it.id }) { Button(onClick = { onOpenCategory(it) }) { Text(it.name) } }
                item { Button(onClick = { onOpenCategory(StoreCategory(id = 446, parent = 445, name = "Hombre invierno")) }) { Text("Outlet") } }
            }
        }
    }
}

@Composable
private fun IosPagedProductGrid(
    queryKey: String, title: String, storeApi: StoreApiClient, cartStore: StoreCartStore, padding: PaddingValues,
    onBack: () -> Unit, onProduct: (StoreProduct) -> Unit,
    loadPage: suspend (Int, Int) -> CatalogPage<StoreProduct>
) {
    val paginator = remember(queryKey) { CatalogPaginator<StoreProduct>(identity = { it.id }) }
    val state by paginator.state.collectAsState()
    val gridState = rememberLazyGridState()
    var retry by remember(queryKey) { mutableStateOf(0) }
    LaunchedEffect(queryKey, retry) { paginator.start(queryKey, loadPage) }
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }.collect { last ->
            val current = paginator.state.value
            val count = current.items.size
            if (count > 0 && last >= count - CatalogPaginator.PREFETCH_DISTANCE && current.hasMore && !current.isAppending) paginator.loadNext(loadPage)
        }
    }
    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Atrás") }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        when {
            state.isInitialLoading && state.items.isEmpty() -> IosStoreLoading()
            state.initialError != null && state.items.isEmpty() -> IosStoreError(iosCatalogErrorMessage(state.initialError!!)) { retry++ }
            state.items.isEmpty() -> IosStoreEmpty("No hay productos disponibles.")
            else -> LazyVerticalGrid(GridCells.Fixed(2), gridState, Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp, 8.dp, 12.dp, 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(state.items, key = { it.id }) { IosProductCard(it, onProduct, cartStore) }
                if (state.isAppending) item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp)) } }
                if (state.appendError != null && state.hasMore) item(span = { GridItemSpan(maxLineSpan) }) {
                    TextButton(onClick = { kotlinx.coroutines.MainScope().launch { paginator.loadNext(loadPage) } }, Modifier.fillMaxWidth()) { Text("Reintentar") }
                }
            }
        }
    }
}

@Composable
private fun IosSearchScreen(storeApi: StoreApiClient, cartStore: StoreCartStore, padding: PaddingValues, onProduct: (StoreProduct) -> Unit, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    LaunchedEffect(query) { delay(350); submitted = query.trim() }
    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Atrás") }
            OutlinedTextField(query, { query = it }, Modifier.weight(1f), singleLine = true, label = { Text("Buscar") })
        }
        if (submitted.isBlank()) IosStoreEmpty("Escribe un producto, categoría o marca.")
        else IosPagedProductGrid("search:" + submitted, "Resultados", storeApi, cartStore, PaddingValues(0.dp), onBack, onProduct) { p, size ->
            val items = storeApi.products(perPage = size, page = p, search = submitted)
            CatalogPage(items, items.size >= size)
        }
    }
}

@Composable
private fun IosBrandsScreen(padding: PaddingValues, onBack: () -> Unit, onBrand: (String, String) -> Unit) {
    val brands = remember {
        listOf(
            "Mayoral" to "mayoral", "Boboli" to "boboli", "Tiffosi" to "tiffosi", "Spagnolo" to "spagnolo",
            "Surkana" to "surkana", "Ragussa" to "ragussa", "Abel & Lula" to "abel-lula", "Yoedu" to "yoedu",
            "Babidu" to "babidu", "Amaya" to "amaya", "Betzzia" to "betzzia", "Carla Ruiz" to "carla-ruiz",
            "Arggido" to "arggido", "Marta en Brazil" to "marta-en-brazil", "Micolino" to "micolino",
            "Carmy" to "carmy", "Varones" to "varones", "Moncho Heredia" to "moncho-heredia", "Dadati" to "dadati", "Selinac" to "selinac"
        )
    }
    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Atrás") }
            Text("Todas las marcas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        LazyVerticalGrid(GridCells.Fixed(2), Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(brands, key = { it.second }) { (name, slug) -> OutlinedButton(onClick = { onBrand(name, slug) }) { Text(name) } }
        }
    }
}

@Composable
private fun IosOutletTopLevel(availability: OutletAvailability?, onOpen: (IosOutletCategory) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Todas", fontWeight = FontWeight.Bold)
        IOS_OUTLET_CATEGORIES.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { category -> OutlinedButton(onClick = { onOpen(category) }, Modifier.weight(1f)) { Text(category.name) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun IosOutletScreen(
    storeApi: StoreApiClient, cartStore: StoreCartStore, outletAvailabilityStore: IosOutletAvailabilityStore,
    padding: PaddingValues, categoryId: Int, title: String, onBack: () -> Unit, onProduct: (StoreProduct) -> Unit
) {
    var selectedBubble by remember(categoryId) { mutableStateOf<String?>(null) }
    val availability by outletAvailabilityStore.snapshot.collectAsState()
    var categories by remember { mutableStateOf(emptyList<StoreCategory>()) }
    LaunchedEffect(Unit) {
        launch { runCatching { outletAvailabilityStore.refresh() } }
        categories = runCatching { storeApi.categories(perPage = 100) }.getOrDefault(emptyList())
    }
    val bubbles = remember(categoryId) { iosOutletBubbles(categoryId) }
    val visibleBubbles = bubbles.filter { bubble ->
        availability?.let { snapshot -> bubble.categoryIds.any { snapshot.countFor(categoryId, it) > 0 } } ?: true
    }
    LaunchedEffect(visibleBubbles, selectedBubble) {
        if (selectedBubble != null && visibleBubbles.none { it.label == selectedBubble }) selectedBubble = null
    }
    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Atrás") }
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { OutlinedButton(onClick = { selectedBubble = null }) { Text("Todas") } }
            items(visibleBubbles, key = { it.label }) { bubble -> OutlinedButton(onClick = { selectedBubble = bubble.label }) { Text(bubble.label) } }
        }
        val selected = visibleBubbles.firstOrNull { it.label == selectedBubble }
        IosPagedProductGrid("outlet:" + categoryId, if (selected == null) title else title + " · " + selected.label, storeApi, cartStore, PaddingValues(0.dp), onBack, onProduct) { p, size ->
            val page = storeApi.products(perPage = size, page = p, category = categoryId)
            val filtered = if (selected == null) page else page.filter { iosProductBelongsToBubble(it, selected, categories.associateBy { c -> c.id }) }
            CatalogPage(filtered, page.size >= size)
        }
    }
}

@Composable
private fun IosProductCard(product: StoreProduct, onClick: (StoreProduct) -> Unit, cartStore: StoreCartStore? = null) {
    Column(Modifier.width(158.dp).clickable { onClick(product) }) {
        RemoteStoreImage(product.images.firstOrNull()?.src, product.name, Modifier.fillMaxWidth().aspectRatio(.78f), ContentScale.Crop)
        Spacer(Modifier.height(7.dp))
        Text(product.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        if (product.hasDisplayablePrice) Text(formatStorePrice(product.prices.price, product.prices.currencyMinorUnit, product.prices.currencySymbol), color = Color(0xFF183B35), fontWeight = FontWeight.Bold)
        cartStore?.let { if (product.variations.isEmpty()) TextButton(onClick = { it.add(product.id) }) { Text("Añadir") } }
    }
}

@Composable
private fun IosProductDetail(storeApi: StoreApiClient, padding: PaddingValues, initialProduct: StoreProduct, onBack: () -> Unit, cartStore: StoreCartStore? = null) {
    var product by remember(initialProduct.id) { mutableStateOf(initialProduct) }
    var loading by remember(initialProduct.id) { mutableStateOf(true) }
    var error by remember(initialProduct.id) { mutableStateOf<String?>(null) }
    var selectedVariation by remember(initialProduct.id) { mutableStateOf(initialProduct.variations.firstOrNull()) }
    suspend fun load() {
        loading = true; error = null
        runCatching { storeApi.productWithVariationAvailability(initialProduct.id) }
            .onSuccess { product = it; selectedVariation = it.variations.firstOrNull() }
            .onFailure { error = iosCatalogErrorMessage(it) }
        loading = false
    }
    LaunchedEffect(initialProduct.id) { load() }
    if (loading && product.id == initialProduct.id && product.images.isEmpty()) IosStoreLoading()
    else LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            TextButton(onClick = onBack) { Text("Atrás") }
            if (product.images.isNotEmpty()) LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(product.images, key = { it.src }) { RemoteStoreImage(it.src, product.name, Modifier.width(260.dp).aspectRatio(.9f), ContentScale.Crop) }
            }
            Text(product.name, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(20.dp, 12.dp, 20.dp, 4.dp))
            if (product.hasDisplayablePrice) Text(formatStorePrice(product.prices.price, product.prices.currencyMinorUnit, product.prices.currencySymbol), fontWeight = FontWeight.Bold, color = Color(0xFF183B35), modifier = Modifier.padding(horizontal = 20.dp))
            if (product.variations.isNotEmpty()) {
                Text("Variantes", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(20.dp, 16.dp, 20.dp, 4.dp))
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    product.variations.forEach { variation -> OutlinedButton(onClick = { selectedVariation = variation }, Modifier.fillMaxWidth()) { Text(variation.attributes.joinToString(" · ") { it.value }.ifBlank { "Variación " + variation.id }) } }
                }
            }
            cartStore?.let {
                val variation = selectedVariation
                val attrs = variation?.attributes.orEmpty().map { StoreCartVariation(it.name.ifBlank { "pa_attribute" }, it.value) }
                Button(onClick = { it.add(product.id, 1, attrs) }, enabled = product.isPurchasable != false && (variation?.isPurchasable != false), modifier = Modifier.fillMaxWidth().padding(20.dp)) { Text("Añadir al carrito") }
            }
            if (loading) CircularProgressIndicator(Modifier.padding(20.dp).size(24.dp))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 20.dp)) }
            if (error != null) TextButton(onClick = { kotlinx.coroutines.MainScope().launch { load() } }, Modifier.padding(horizontal = 20.dp)) { Text("Reintentar") }
        }
    }
}

private fun iosCatalogErrorMessage(error: Throwable): String {
    val text = error.message.orEmpty().lowercase()
    return when {
        "timeout" in text || "timed out" in text -> "Tiempo de espera agotado. Comprueba la conexión e inténtalo de nuevo."
        "500" in text || "502" in text || "503" in text || "504" in text -> "El servidor no está disponible temporalmente. Inténtalo de nuevo."
        "network" in text || "host" in text || "connection" in text || "offline" in text -> "No hay conexión con la tienda. Comprueba la red e inténtalo de nuevo."
        else -> error.message?.takeIf { it.isNotBlank() } ?: "No se ha podido cargar la tienda."
    }
}

@Composable
private fun IosCartScreen(
    cartStore: StoreCartStore,
    padding: PaddingValues,
    onCheckout: () -> Unit,
    onOpenProduct: (StoreProduct) -> Unit
) {
    val cart by cartStore.cart.collectAsState()
    val state by cartStore.state.collectAsState()
    val error by cartStore.error.collectAsState()
    LaunchedEffect(Unit) { cartStore.refresh() }
    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Tu carrito", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (state == StoreCartLoadState.LOADING && cart.items.isEmpty()) item { IosStoreLoading() }
        if (!error.isNullOrBlank()) item { IosStoreError(error!!, cartStore::refresh) }
        if (state == StoreCartLoadState.SUCCESS_EMPTY && error == null) item { Text("Tu carrito está vacío") }
        items(cart.items, key = { it.key }) { line ->
            Row(Modifier.fillMaxWidth().clickable { onOpenProduct(StoreProduct(id = line.id, name = line.name, images = line.images, prices = line.prices)) }, verticalAlignment = Alignment.CenterVertically) {
                RemoteStoreImage(line.images.firstOrNull()?.src, line.name, Modifier.size(78.dp), ContentScale.Crop)
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(line.name, fontWeight = FontWeight.SemiBold)
                    if (line.variation.isNotEmpty()) Text(line.variation.joinToString(" · ") { "${it.attribute.removePrefix("pa_")}: ${it.value}" }, style = MaterialTheme.typography.bodySmall)
                    Text("${line.prices.price} ${line.prices.currencySymbol} · Subtotal ${line.totals.lineTotal} ${line.prices.currencySymbol}")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton({ cartStore.update(line, line.quantity - 1) }) { Text("−") }
                        Text(line.quantity.toString(), modifier = Modifier.padding(horizontal = 8.dp))
                        IconButton({ cartStore.update(line, line.quantity + 1) }) { Text("+") }
                    }
                }
                IconButton({ cartStore.remove(line) }) { Text("×") }
            }
        }
        if (cart.items.isNotEmpty()) item {
            Button(onClick = onCheckout, modifier = Modifier.fillMaxWidth()) { Text("Finalizar compra") }
            HorizontalDivider()
            Text("Total: ${cart.totals.totalPrice} ${cart.totals.currencySymbol}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun IosSectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun IosStoreLoading() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text("Cargando")
    }
}

@Composable
private fun IosStoreEmpty(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(message) }
}

@Composable
private fun IosStoreError(message: String, retry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(12.dp))
        Button(onClick = retry) { Text("Reintentar") }
    }
}

@Composable
private fun IosCategoryRootPreview(): Unit = Unit
