package es.criosrango.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.criosrango.shared.account.AccountRepository
import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreProduct
import es.criosrango.shared.model.StoreCartVariation
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

internal enum class IosRootSection { HOME, CATEGORIES, CART, ACCOUNT }
internal sealed class IosCatalogPage {
    data object Root : IosCatalogPage()
    data class Category(val category: StoreCategory) : IosCatalogPage()
    data class Product(val product: StoreProduct) : IosCatalogPage()
}

@Composable
fun CriosRangoIOSRootScreen(
    storeApi: es.criosrango.shared.api.StoreApiClient,
    accountRepository: AccountRepository,
    cartStore: StoreCartStore,
    checkoutStore: StoreCheckoutStore,
    paymentStore: StorePaymentStore,
    onOpenPayment: (String) -> Unit
) {
    var section by remember { mutableStateOf(IosRootSection.HOME) }
    var checkoutOpen by remember { mutableStateOf(false) }
    var catalogPage by remember { mutableStateOf<IosCatalogPage>(IosCatalogPage.Root) }

    val createdOrder by checkoutStore.createdOrder.collectAsState()
    LaunchedEffect(createdOrder?.orderId, createdOrder?.orderKey) {
        val order = createdOrder ?: return@LaunchedEffect
        val orderId = order.orderId ?: return@LaunchedEffect
        val orderKey = order.orderKey?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        accountRepository.prepareClaimOrder(orderId, orderKey)
        if (accountRepository.hasSession) {
            runCatching { accountRepository.claimPendingOrder() }
                .onFailure { println("KMP_ACCOUNT_CLAIM_FAILED=${it.message}") }
        }
    }

    MaterialTheme {
        androidx.compose.runtime.LaunchedEffect(Unit) { println("KMP_RUNTIME_ROOT_SCREEN_READY") }
        Scaffold(
            bottomBar = {
                IosMainTabBar(
                    selected = section,
                    cartCount = cartStore.cart.collectAsState().value.itemsCount,
                    onSelected = {
                        section = it
                        if (it == IosRootSection.CATEGORIES) catalogPage = IosCatalogPage.Root
                    }
                )
            }
        ) { padding ->
            if (checkoutOpen) {
                IosCheckoutScreen(
                    checkoutStore = checkoutStore,
                    paymentStore = paymentStore,
                    accountRepository = accountRepository,
                    padding = padding,
                    onBack = { checkoutOpen = false },
                    onOpenPayment = onOpenPayment
                )
            } else when (section) {
                IosRootSection.HOME -> IosHomeScreen(
                    storeApi = storeApi,
                    cartStore = cartStore,
                    padding = padding,
                    onCategory = {
                        section = IosRootSection.CATEGORIES
                        catalogPage = IosCatalogPage.Category(it)
                    },
                    onProduct = {
                        section = IosRootSection.CATEGORIES
                        catalogPage = IosCatalogPage.Product(it)
                    }
                )
                IosRootSection.CATEGORIES -> IosCatalogScreen(
                    storeApi = storeApi,
                    padding = padding,
                    page = catalogPage,
                    cartStore = cartStore,
                    onOpenCategory = { catalogPage = IosCatalogPage.Category(it) },
                    onOpenProduct = { catalogPage = IosCatalogPage.Product(it) },
                    onBack = {
                        catalogPage = when (catalogPage) {
                            IosCatalogPage.Root -> IosCatalogPage.Root
                            is IosCatalogPage.Category,
                            is IosCatalogPage.Product -> IosCatalogPage.Root
                        }
                    }
                )
                IosRootSection.CART -> IosCartScreen(cartStore, padding, onCheckout = { checkoutOpen = true }) { product -> section = IosRootSection.CATEGORIES; catalogPage = IosCatalogPage.Product(product) }
                IosRootSection.ACCOUNT -> CriosRangoIOSAccountScreen(
                    repository = accountRepository,
                    modifier = Modifier.padding(padding)
                )
            }
        }
    }
}

@Composable
private fun IosMainTabBar(
    selected: IosRootSection,
    cartCount: Int,
    onSelected: (IosRootSection) -> Unit
) {
    Surface(shadowElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().height(68.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                IosRootSection.HOME to "Inicio",
                IosRootSection.CATEGORIES to "Categorías",
                IosRootSection.CART to "Carrito",
                IosRootSection.ACCOUNT to "Cuenta"
            ).forEach { (item, label) ->
                val active = item == selected
                Column(
                    modifier = Modifier.weight(1f).fillMaxSize().clickable { onSelected(item) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = label,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            color = if (active) Color(0xFF183B35) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (item == IosRootSection.CART && cartCount > 0) {
                            Text(" $cartCount", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IosHomeScreen(
    storeApi: es.criosrango.shared.api.StoreApiClient,
    cartStore: StoreCartStore,
    padding: PaddingValues,
    onCategory: (StoreCategory) -> Unit,
    onProduct: (StoreProduct) -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var products by remember { mutableStateOf(emptyList<StoreProduct>()) }
    var categories by remember { mutableStateOf(emptyList<StoreCategory>()) }

    fun retry() {
        loading = true
        error = null
    }

    LaunchedEffect(loading) {
        if (!loading) return@LaunchedEffect
        runCatching {
            val result = kotlinx.coroutines.coroutineScope {
                val productsDeferred = async { storeApi.products(perPage = 24, page = 1, orderBy = "date", order = "desc") }
                val categoriesDeferred = async { storeApi.categories(perPage = 100) }
                productsDeferred.await() to categoriesDeferred.await()
            }
            products = result.first
            categories = result.second
        }.onSuccess {
            loading = false
            println("KMP_RUNTIME_HOME_READY products=${products.size} categories=${categories.size}")
        }.onFailure {
            error = it.message ?: "No se ha podido cargar la tienda."
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(padding)) {
        when {
            loading -> IosStoreLoading()
            error != null -> IosStoreError(error!!, ::retry)
            products.isEmpty() && categories.isEmpty() -> IosStoreEmpty("No hay contenido disponible.")
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    item { IosHomeHero() }
                    item {
                        IosSectionHeader("Categorías")
                        Spacer(Modifier.height(10.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(categories.filter { it.parent == 0 && !it.name.equals("Outlet", true) }.distinctBy { it.id }.take(10), key = { it.id }) {
                                IosCategoryChip(it, onCategory)
                            }
                        }
                    }
                    item {
                        IosSectionHeader("Novedades")
                        Spacer(Modifier.height(10.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(products.take(8), key = { it.id }) { product ->
                                IosProductCard(product, onProduct, cartStore)
                            }
                        }
                    }
                    item {
                        IosSectionHeader("Outlet")
                        Spacer(Modifier.height(10.dp))
                        val outlet = categories.firstOrNull { it.parent == 0 && it.name.contains("outlet", true) }
                        if (outlet != null) {
                            IosCategoryChip(outlet, onCategory)
                        } else {
                            Text("Outlet", Modifier.padding(horizontal = 20.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IosHomeHero() {
    Box(
        Modifier.fillMaxWidth().aspectRatio(4f / 3f).background(Color(0xFFE8E2DD)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Críos & Rango", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Moda infantil y familiar", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IosCategoryChip(category: StoreCategory, onClick: (StoreCategory) -> Unit) {
    OutlinedButton(onClick = { onClick(category) }) {
        Text(category.name.ifBlank { "Categoría" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun IosProductCard(product: StoreProduct, onClick: (StoreProduct) -> Unit, cartStore: StoreCartStore? = null) {
    Column(Modifier.width(158.dp).clickable { onClick(product) }) {
        RemoteStoreImage(
            url = product.images.firstOrNull()?.src,
            contentDescription = product.name,
            modifier = Modifier.fillMaxWidth().aspectRatio(.78f),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(7.dp))
        Text(product.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        Text(
            product.prices.price + " " + product.prices.currencySymbol,
            color = Color(0xFF183B35),
            fontWeight = FontWeight.Bold
        )
        cartStore?.let { store ->
            if (product.variations.isEmpty()) {
                TextButton(onClick = { store.add(product.id) }) { Text("Añadir") }
            }
        }
    }
}

@Composable
private fun IosCatalogScreen(
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
private fun IosCategoryRoot(
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
private fun IosCategoryProducts(
    storeApi: es.criosrango.shared.api.StoreApiClient,
    padding: PaddingValues,
    category: StoreCategory,
    onOpenProduct: (StoreProduct) -> Unit,
    onBack: () -> Unit,
    cartStore: StoreCartStore
) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var products by remember { mutableStateOf(emptyList<StoreProduct>()) }
    LaunchedEffect(category.id, loading) {
        if (!loading) return@LaunchedEffect
        runCatching { storeApi.products(perPage = 24, page = 1, category = category.id) }
            .onSuccess { products = it; loading = false }
            .onFailure { error = it.message ?: "No se han podido cargar los productos."; loading = false }
    }
    Column(Modifier.fillMaxSize().padding(padding)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Atrás") }
            Text(category.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        when {
            loading -> IosStoreLoading()
            error != null -> IosStoreError(error!!) { loading = true; error = null }
            products.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No hay productos en esta categoría.") }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(products, key = { it.id }) { IosProductCard(it, onOpenProduct, cartStore) }
            }
        }
    }
}

@Composable
private fun IosProductDetail(
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
            Text(product.prices.price + " " + product.prices.currencySymbol, fontWeight = FontWeight.Bold, color = Color(0xFF183B35), modifier = Modifier.padding(horizontal = 20.dp))
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
