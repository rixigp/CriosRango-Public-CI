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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.path
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import es.criosrango.shared.account.AccountRepository
import es.criosrango.shared.loyalty.LoyaltyRepository
import es.criosrango.shared.loyalty.LoyaltyReward
import es.criosrango.shared.loyalty.LoyaltyWallet
import es.criosrango.shared.loyalty.redeemableOptions
import es.criosrango.shared.loyalty.addMoneyAmounts
import es.criosrango.shared.loyalty.subtractMoneyAmounts
import es.criosrango.shared.promotions.Promotion
import es.criosrango.shared.promotions.PromotionRepository
import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreProduct
import es.criosrango.shared.model.StoreCartVariation
import es.criosrango.shared.model.consumerDiscount
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

internal enum class IosRootSection { HOME, CATEGORIES, OUTLET, CART, ACCOUNT }
internal enum class IosCatalogSortModeState { RECENT, PRICE_ASC, PRICE_DESC, NAME_ASC }

internal data class IosCatalogRestorationState(
    val queryKey: String,
    val sortMode: IosCatalogSortModeState,
    val selectedSizes: Set<String>,
    val selectedColors: Set<String>,
    val selectedBrands: Set<String>,
    val pagingState: CatalogPagingState<StoreProduct>,
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemOffset: Int = 0
)

internal sealed class IosCatalogPage {
    data object Novedades : IosCatalogPage()
    data object Search : IosCatalogPage()
    data object Brands : IosCatalogPage()
    data object Outlet : IosCatalogPage()
    data object Root : IosCatalogPage()
    data class Category(val category: StoreCategory) : IosCatalogPage()
    data class Product(val product: StoreProduct) : IosCatalogPage()
}

@Composable
fun CriosRangoIOSRootScreen(
    storeApi: es.criosrango.shared.api.StoreApiClient,
    accountRepository: AccountRepository,
    loyaltyRepository: LoyaltyRepository,
    cartStore: StoreCartStore,
    checkoutStore: StoreCheckoutStore,
    paymentStore: StorePaymentStore,
    onOpenPayment: (String) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    pushNavigation: IosPushNavigation? = null,
    onPushNavigationConsumed: () -> Unit = {}
) {
    var section by remember { mutableStateOf(IosRootSection.HOME) }
    var openPromotionsOnStart by remember { mutableStateOf(false) }
    var checkoutOpen by remember { mutableStateOf(false) }
    var catalogPage by remember { mutableStateOf<IosCatalogPage>(IosCatalogPage.Root) }
    var catalogOriginHome by remember { mutableStateOf(false) }
    val catalogHistory = remember { mutableStateListOf<IosCatalogPage>() }
    val catalogRestoration = remember { mutableStateMapOf<Long, IosCatalogRestorationState>() }
    fun openCatalog(page: IosCatalogPage) { if (catalogPage != page) catalogHistory.add(catalogPage); catalogPage = page }
    fun resetCatalog() { catalogHistory.clear(); catalogRestoration.clear(); catalogPage = IosCatalogPage.Root; catalogOriginHome = false }
    fun backCatalog() { catalogPage = if (catalogHistory.isNotEmpty()) catalogHistory.removeAt(catalogHistory.lastIndex) else IosCatalogPage.Root }

    LaunchedEffect(pushNavigation) {
        val n = pushNavigation ?: return@LaunchedEffect
        when (n.type) {
            PushNotificationType.NEW_PRODUCTS -> { section = IosRootSection.CATEGORIES; resetCatalog(); openCatalog(IosCatalogPage.Novedades) }
            PushNotificationType.ORDER_STATUS -> section = IosRootSection.ACCOUNT
            PushNotificationType.BIRTHDAY_COUPON -> {
                openPromotionsOnStart = true
                section = IosRootSection.ACCOUNT
            }
        }
        onPushNavigationConsumed()
    }

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
                        if (it == IosRootSection.CATEGORIES) resetCatalog()
                        if (it == IosRootSection.OUTLET) { resetCatalog(); openCatalog(IosCatalogPage.Outlet) }
                    }
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize()) {
                IosStoreTopBar(
                    cartQuantity = cartStore.cart.collectAsState().value.itemsCount,
                    onSearch = { section = IosRootSection.CATEGORIES; resetCatalog(); openCatalog(IosCatalogPage.Search) },
                    onCart = { section = IosRootSection.CART }
                )
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
                        resetCatalog()
                        catalogOriginHome = true
                        openCatalog(IosCatalogPage.Category(it))
                    },
                    onProduct = {
                        section = IosRootSection.CATEGORIES
                        resetCatalog()
                        openCatalog(IosCatalogPage.Product(it))
                    },
                    onNovedades = { section = IosRootSection.CATEGORIES; resetCatalog(); openCatalog(IosCatalogPage.Novedades) },
                    onSearch = { section = IosRootSection.CATEGORIES; resetCatalog(); openCatalog(IosCatalogPage.Search) },
                    onBrands = { section = IosRootSection.CATEGORIES; resetCatalog(); openCatalog(IosCatalogPage.Brands) },
                    onOutlet = { section = IosRootSection.OUTLET; resetCatalog(); openCatalog(IosCatalogPage.Outlet) }
                )
                IosRootSection.OUTLET, IosRootSection.CATEGORIES -> IosCatalogScreen(
                    storeApi = storeApi,
                    padding = padding,
                    page = catalogPage,
                    cartStore = cartStore,
                    onOpenCategory = { openCatalog(IosCatalogPage.Category(it)) },
                    onOpenProduct = { openCatalog(IosCatalogPage.Product(it)) },
                    onBack = {
                        if (section == IosRootSection.OUTLET && catalogPage == IosCatalogPage.Outlet) {
                            section = IosRootSection.HOME
                            resetCatalog()
                        } else if (catalogPage == IosCatalogPage.Root) {
                            section = IosRootSection.HOME
                            resetCatalog()
                        } else {
                            backCatalog()
                            if (catalogHistory.isEmpty() && catalogPage == IosCatalogPage.Root && catalogOriginHome) {
                                section = IosRootSection.HOME
                                resetCatalog()
                            }
                        }
                    },
                    catalogRestoration = catalogRestoration
                )
                IosRootSection.CART -> IosCartScreen(
                    cartStore = cartStore,
                    loyaltyRepository = loyaltyRepository,
                    walletEnabled = accountRepository.hasSession,
                    padding = padding,
                    onCheckout = { checkoutOpen = true }
                ) { product -> section = IosRootSection.CATEGORIES; catalogPage = IosCatalogPage.Product(product) }
                IosRootSection.ACCOUNT -> CriosRangoIOSAccountScreen(
                    repository = accountRepository,
                    loyaltyRepository = loyaltyRepository,
                    cartCouponCodes = cartStore.cart.collectAsState().value.coupons.map { it.code }.toSet(),
                    onApplyWalletCoupon = { code -> cartStore.applyCoupon(code) },
                    modifier = Modifier.padding(padding),
                    initialOrderId = pushNavigation?.orderId,
                    openPromotionsOnStart = openPromotionsOnStart,
                    onPromotionsOpened = { openPromotionsOnStart = false },
                    onOpenExternalUrl = onOpenExternalUrl
                )
            }
            }
        }
    }
}

@Composable
private fun IosStoreTopBar(
    cartQuantity: Int,
    onSearch: () -> Unit,
    onCart: () -> Unit
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
        Text("Críos&Rango", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        IconButton(onClick = onSearch) { Text("⌕", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { contentDescription = "Buscar" }) }
        Box(Modifier.width(56.dp).fillMaxHeight()) {
        BadgedBox(
            modifier = Modifier.size(48.dp).align(Alignment.CenterStart),
            badge = { if (cartQuantity > 0) Badge { Text(cartQuantity.toString(), maxLines = 1) } }
        ) {
            IconButton(onClick = onCart) { Text("🛍", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { contentDescription = "Carrito" }) }
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
            modifier = Modifier.fillMaxWidth().height(68.dp).navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                IosRootSection.HOME to "Inicio",
                IosRootSection.CATEGORIES to "Categorías",
                IosRootSection.OUTLET to "Outlet",
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
    onProduct: (StoreProduct) -> Unit,
    onNovedades: () -> Unit,
    onSearch: () -> Unit,
    onBrands: () -> Unit,
    onOutlet: () -> Unit
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
                    item {
                        IosHomeHero()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onSearch, modifier = Modifier.weight(1f)) { Text("Buscar") }
                            OutlinedButton(onClick = onBrands, modifier = Modifier.weight(1f)) { Text("Marcas") }
                        }
                    }
                    item {
                        IosSectionHeader("Categorías")
                        Spacer(Modifier.height(10.dp))
                        val rootCategories = categories.filter { it.parent == 0 && !it.name.equals("Outlet", true) }.distinctBy { it.id }.take(9)
                        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            rootCategories.chunked(3).forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    row.forEach { category ->
                                        IosHomeCategoryTile(category, onCategory, Modifier.weight(1f))
                                    }
                                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Novedades", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            TextButton(onClick = onNovedades) { Text("Ver todo ›") }
                        }
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
                        IosHomeOutletSection(categories, onOutlet)
                    }
                    item {
                        IosHomeBrandsSection(onBrands)
                    }
                }
            }
        }
    }
}

private val IOS_HOME_HERO_IMAGES = listOf(
    "https://images.pexels.com/photos/6617704/pexels-photo-6617704.jpeg?auto=compress&cs=tinysrgb&w=1600",
    "https://images.pexels.com/photos/19915135/pexels-photo-19915135.jpeg?auto=compress&cs=tinysrgb&w=1600",
    "https://images.pexels.com/photos/30804210/pexels-photo-30804210.jpeg?auto=compress&cs=tinysrgb&w=1600",
    "https://images.pexels.com/photos/29614374/pexels-photo-29614374.jpeg?auto=compress&cs=tinysrgb&w=1600"
)

private val IOS_HOME_CATEGORY_IMAGES = mapOf(
    67 to "https://www.criosrango.es/wp-content/uploads/2026/09/conjunto-peto-estampado-y-jersey-recien-nacida-laurel-XL-4.avif",
    68 to "https://www.criosrango.es/wp-content/uploads/2026/09/conjunto-sudadera-bolsillos-y-pantalon-nina-botella-XL-1.avif",
    70 to "https://www.criosrango.es/wp-content/uploads/2026/05/226_104164026f01_016955_1.webp",
    71 to "https://www.criosrango.es/wp-content/uploads/2026/08/556cado321-46-2.jpg",
    292 to "https://www.criosrango.es/wp-content/uploads/2026/09/conjunto-pantalon-y-blusa-con-chaleco-bebe-violeta-mezcla-XL-4.avif",
    294 to "https://www.criosrango.es/wp-content/uploads/2026/08/chaqueton-pelo-bolsillos-bebe-alaska-XL-1.avif",
    310 to "https://www.criosrango.es/wp-content/uploads/2026/09/polo-combinado-nino-lago-XL-1.avif",
    420 to "https://www.criosrango.es/wp-content/uploads/2026/01/Captura-de-pantalla-2026-01-21-a-las-17.48.57.png",
    504 to "https://www.criosrango.es/wp-content/uploads/2026/01/Captura-de-pantalla-2026-01-21-a-las-17.48.57.png"
)

@Composable
private fun IosHomeHero() {
    var index by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3500)
            index = (index + 1) % IOS_HOME_HERO_IMAGES.size
        }
    }
    RemoteStoreImage(IOS_HOME_HERO_IMAGES[index], null, Modifier.fillMaxWidth().aspectRatio(4f / 3f), ContentScale.Crop)
}

@Composable
private fun IosHomeCategoryTile(category: StoreCategory, onClick: (StoreCategory) -> Unit, modifier: Modifier) {
    Column(modifier.clickable { onClick(category) }.semantics { role = Role.Button; contentDescription = "Categoría ${category.name}" }, horizontalAlignment = Alignment.CenterHorizontally) {
        RemoteStoreImage(IOS_HOME_CATEGORY_IMAGES[category.id], category.name, Modifier.fillMaxWidth().aspectRatio(1f), ContentScale.Fit)
        Spacer(Modifier.height(6.dp))
        Text(category.name, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun IosHomeOutletSection(categories: List<StoreCategory>, onSeeAll: () -> Unit) {
    val outlet = categories.firstOrNull { it.parent == 0 && it.name.contains("outlet", true) } ?: return
    val seasons = categories.filter { it.parent == outlet.id && (it.name.contains("invierno", true) || it.name.contains("verano", true)) }.distinctBy { it.id }
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Outlet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onSeeAll) { Text("Ver todo ›") }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            seasons.take(2).forEach { season ->
                OutlinedButton(onClick = onSeeAll, modifier = Modifier.weight(1f)) { Text(season.name) }
            }
            if (seasons.isEmpty()) OutlinedButton(onClick = onSeeAll, modifier = Modifier.fillMaxWidth()) { Text("Ver Outlet") }
        }
    }
}

@Composable
private fun IosHomeBrandsSection(onSeeAll: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Marcas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onSeeAll) { Text("Ver todas ›") }
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
            items(storeBrands.take(8), key = { it.slug }) { brand ->
                OutlinedButton(onClick = onSeeAll) { Text(brand.name) }
            }
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
internal fun IosProductCard(product: StoreProduct, onClick: (StoreProduct) -> Unit, cartStore: StoreCartStore? = null) {
    Column(Modifier.width(158.dp).clickable { onClick(product) }.semantics { role = Role.Button; contentDescription = "Abrir ${product.name}" }) {
        RemoteStoreImage(
            url = product.images.firstOrNull()?.src,
            contentDescription = product.name,
            modifier = Modifier.fillMaxWidth().aspectRatio(.78f),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(7.dp))
        Text(product.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        if (product.hasDisplayablePrice) {
            Text(
                formatStorePrice(product.prices.price, product.prices.currencyMinorUnit, product.prices.currencySymbol),
                color = Color(0xFF183B35),
                fontWeight = FontWeight.Bold
            )
        }
        cartStore?.let { store ->
            if (product.variations.isEmpty()) {
                TextButton(onClick = { store.add(product.id) }) { Text("Añadir") }
            }
        }
    }
}

private fun iosCartPromotionTitle(promotion: Promotion): String {
    val key = listOfNotNull(promotion.code, promotion.title, promotion.description).joinToString(" ").lowercase()
    return when {
        key.contains("blackcrios") || key.contains("black friday") -> "Black Friday 20%"
        key.contains("bienvenida") || key.contains("welcome") -> "Promoción de bienvenida"
        key.contains("cumple") || key.contains("birthday") -> "Tu regalo de cumpleaños"
        else -> promotion.title
    }
}

private fun iosCartPromotionIcon(promotion: Promotion): String {
    val key = listOfNotNull(promotion.code, promotion.title, promotion.description).joinToString(" ").lowercase()
    return when {
        key.contains("blackcrios") || key.contains("black friday") -> "％"
        key.contains("bienvenida") || key.contains("welcome") -> "🏷"
        key.contains("cumple") || key.contains("birthday") -> "🎂"
        else -> "🏷"
    }
}

@Composable
private fun iosCartPromotionBackground(promotion: Promotion): Color {
    val key = listOfNotNull(promotion.code, promotion.title, promotion.description).joinToString(" ").lowercase()
    return when {
        key.contains("blackcrios") || key.contains("black friday") -> Color(0xFFFFF7D6)
        key.contains("bienvenida") || key.contains("welcome") -> Color(0xFFEAF3FF)
        key.contains("cumple") || key.contains("birthday") -> Color(0xFFFDECEF)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
}

private fun iosCartPromotionDate(value: String): String? {
    val date = value.substringBefore("T").substringBefore(" ")
    val parts = date.split("-")
    return if (parts.size == 3 && parts[0].length == 4) parts[2].padStart(2, '0') + "/" + parts[1].padStart(2, '0') + "/" + parts[0] else null
}

private fun iosFriendlyCouponName(label: String, code: String): String {
    val key = "$label $code".lowercase()
    return when {
        key.contains("blackcrios") || key.contains("black friday") -> "Black Friday 20%"
        key.contains("bienvenida") || key.contains("welcome") -> "Bienvenida 10%"
        key.contains("cumple") || key.contains("birthday") -> "Cumpleaños 15%"
        label.isNotBlank() && !label.equals(code, ignoreCase = true) -> label
        else -> "Descuento aplicado"
    }
}

@Composable
private fun IosCartApplyButton(onClick: () -> Unit, enabled: Boolean = true, loading: Boolean = false, modifier: Modifier = Modifier.height(36.dp)) = Button(onClick=onClick, enabled=enabled, shape=RoundedCornerShape(50), colors=androidx.compose.material3.ButtonDefaults.buttonColors(containerColor=Color(0xFF0F5C4D), contentColor=Color.White), contentPadding=PaddingValues(horizontal=12.dp, vertical=0.dp), modifier=modifier) { if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth=2.dp, color=Color.White) else Text("Aplicar", style=MaterialTheme.typography.labelMedium) }
private fun iosCartIsWalletCoupon(code: String): Boolean = code.trim().startsWith("CR-MONEDERO-", ignoreCase=true)
private fun iosBuildLineIcon(name:String, content: androidx.compose.ui.graphics.vector.PathBuilder.()->Unit): androidx.compose.ui.graphics.vector.ImageVector = androidx.compose.ui.graphics.vector.ImageVector.Builder(name=name,defaultWidth=24.dp,defaultHeight=24.dp,viewportWidth=24f,viewportHeight=24f).apply { path(fill=null,stroke=androidx.compose.ui.graphics.SolidColor(Color.Black),strokeLineWidth=1.8f,strokeLineCap=androidx.compose.ui.graphics.StrokeCap.Round,strokeLineJoin=androidx.compose.ui.graphics.StrokeJoin.Round,pathBuilder=content) }.build()
private val IosCartWalletLineIcon = iosBuildLineIcon("IosCartWalletLineIcon") { moveTo(3.5f,6.5f); lineTo(18.5f,6.5f); lineTo(20.5f,8.5f); lineTo(20.5f,18f); lineTo(3.5f,18f); close(); moveTo(3.5f,6.5f); lineTo(3.5f,5f); lineTo(17f,5f); moveTo(15.5f,12.5f); lineTo(20.5f,12.5f); moveTo(17.5f,12.5f); lineTo(17.5f,12.5f) }
private val IosCartDiscountLineIcon = iosBuildLineIcon("IosCartDiscountLineIcon") { moveTo(4f,7f); lineTo(20f,7f); lineTo(20f,17f); lineTo(4f,17f); close(); moveTo(8f,12f); lineTo(16f,12f) }
private val IosCartTagLineIcon = iosBuildLineIcon("IosCartTagLineIcon") { moveTo(3.5f,11f); lineTo(11f,3.5f); lineTo(20f,12.5f); lineTo(12.5f,20f); close(); moveTo(8f,8f); lineTo(8f,8f) }
private val IosCartCakeLineIcon = iosBuildLineIcon("IosCartCakeLineIcon") { moveTo(4f,10f); lineTo(20f,10f); lineTo(20f,19f); lineTo(4f,19f); close(); moveTo(4f,14f); lineTo(20f,14f); moveTo(8f,10f); lineTo(8f,7f); moveTo(12f,10f); lineTo(12f,6f); moveTo(16f,10f); lineTo(16f,7f) }
private val IosCartCloseLineIcon = iosBuildLineIcon("IosCartCloseLineIcon") { moveTo(7f,7f); lineTo(17f,17f); moveTo(17f,7f); lineTo(7f,17f) }
@Composable private fun IosCartAppliedCouponIcon(kind:String) { val icon=when(kind){"wallet"->IosCartWalletLineIcon;"credit"->IosCartWalletLineIcon;"discount"->IosCartDiscountLineIcon;"cake"->IosCartCakeLineIcon;else->IosCartTagLineIcon}; androidx.compose.material3.Icon(icon,null,tint=Color(0xFF0F5C4D),modifier=Modifier.size(20.dp)) }

@Composable
private fun IosCartScreen(
    cartStore: StoreCartStore,
    loyaltyRepository: LoyaltyRepository,
    walletEnabled: Boolean,
    padding: PaddingValues,
    onCheckout: () -> Unit,
    onOpenProduct: (StoreProduct) -> Unit
) {
    var loyaltyWallet by remember { mutableStateOf<LoyaltyWallet?>(null) }
    var loyaltyLoading by remember { mutableStateOf(false) }
    var loyaltyError by remember { mutableStateOf<String?>(null) }
    var showWalletDialog by remember { mutableStateOf(false) }
    var pendingRedeemPoints by remember { mutableStateOf<Int?>(null) }
    var pendingRequestId by remember { mutableStateOf<String?>(null) }
    val loyaltyScope = rememberCoroutineScope()

    LaunchedEffect(walletEnabled) {
        if (!walletEnabled) {
            loyaltyWallet = null
            return@LaunchedEffect
        }
        loyaltyLoading = true
        loyaltyError = null
        try {
            loyaltyWallet = loyaltyRepository.getWallet()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            loyaltyError = e.message ?: "No se ha podido cargar el monedero."
        } finally {
            loyaltyLoading = false
        }
    }

    fun redeemWallet(points: Int) {
        val requestId = if (pendingRedeemPoints == points && !pendingRequestId.isNullOrBlank()) {
            pendingRequestId!!
        } else {
            pendingRedeemPoints = points
            loyaltyRepository.newRequestId().also { pendingRequestId = it }
        }
        loyaltyScope.launch {
            loyaltyLoading = true
            loyaltyError = null
            try {
                val response = loyaltyRepository.redeem(points, requestId)
                loyaltyWallet = loyaltyRepository.getWallet()
                val coupon = response.coupon
                if (coupon == null) {
                    loyaltyError = "El canje no ha devuelto un cupón utilizable."
                } else {
                    cartStore.applyCoupon(coupon.code)
                }
                pendingRedeemPoints = null
                pendingRequestId = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loyaltyError = e.message ?: "No se ha podido utilizar el monedero."
            } finally {
                loyaltyLoading = false
            }
        }
    }

    fun applyPendingReward(reward: LoyaltyReward) {
        loyaltyScope.launch {
            loyaltyLoading = true
            loyaltyError = null
            try {
                cartStore.applyCoupon(reward.code)
                loyaltyWallet = loyaltyRepository.getWallet()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loyaltyError = e.message ?: "No se ha podido aplicar el crédito."
            } finally {
                loyaltyLoading = false
            }
        }
    }
    val cart by cartStore.cart.collectAsState()
    val state by cartStore.state.collectAsState()
    val error by cartStore.error.collectAsState()
    val couponLoading by cartStore.couponLoading.collectAsState()
    val couponError by cartStore.couponError.collectAsState()
    var clearCartConfirm by remember { mutableStateOf(false) }
    var couponExpanded by remember { mutableStateOf(false) }
    var couponCode by remember { mutableStateOf("") }
    val promotionRepository = remember { PromotionRepository() }
    var promotions by remember { mutableStateOf(emptyList<Promotion>()) }
    var promotionsLoading by remember { mutableStateOf(true) }
    var promotionsError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        cartStore.refresh()
        promotionsLoading = true
        promotionsError = null
        try {
            promotions = promotionRepository.getPromotions()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            promotionsError = e.message ?: "No se han podido cargar las promociones."
        } finally {
            promotionsLoading = false
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Text("Tu carrito", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        if (state == StoreCartLoadState.LOADING && cart.items.isEmpty()) item { IosStoreLoading() }
        if (!error.isNullOrBlank()) item { IosStoreError(error!!, cartStore::refresh) }
        if (state == StoreCartLoadState.SUCCESS_EMPTY && error == null) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Tu carrito está vacío", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(cart.items, key = { it.key }) { line ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable {
                    onOpenProduct(StoreProduct(id = line.id, name = line.name, images = line.images, prices = line.prices))
                },
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 1.dp
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    RemoteStoreImage(line.images.firstOrNull()?.src, line.name, Modifier.size(84.dp), ContentScale.Crop)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(line.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (line.variation.isNotEmpty()) Text(
                            line.variation.joinToString(" · ") { "${it.attribute.removePrefix("pa_")}: ${it.value}" },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(formatStorePrice(line.prices.price, line.prices.currencyMinorUnit, line.prices.currencySymbol) + " / ud.", fontWeight = FontWeight.Bold)
                        Text(
                            "Subtotal: " + formatStorePrice(line.totals.lineTotal, line.prices.currencyMinorUnit, line.prices.currencySymbol),
                            style = MaterialTheme.typography.bodySmall
                        )
                        val increment = line.quantityLimits?.multipleOf?.takeIf { it > 0 } ?: 1
                        val maximum = line.quantityLimits?.maximum?.takeUnless { it == 9999 }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { cartStore.update(line, line.quantity - increment) }
                            ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                            Text(line.quantity.toString(), modifier = Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.SemiBold)
                            IconButton(
                                onClick = { cartStore.update(line, line.quantity + increment) },
                                enabled = maximum == null || line.quantity < maximum
                            ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                        }
                    }
                    IconButton(onClick = { cartStore.remove(line) }) { Text("×", style = MaterialTheme.typography.titleLarge) }
                }
            }
        }
        if (cart.items.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(horizontal = 20.dp)) {
                Text("Promociones y descuentos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                if (walletEnabled && loyaltyWallet != null) {
                    val eligibleSubtotal = subtractMoneyAmounts(
                        addMoneyAmounts(cart.totals.totalItems, cart.totals.totalItemsTax),
                        cart.totals.consumerDiscount()
                    )
                    val walletOptions = loyaltyWallet!!.redeemableOptions(eligibleSubtotal)
                    val walletApplied = cart.coupons.any { iosCartIsWalletCoupon(it.code) }

                    if (!walletApplied) Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFE8F5EF)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.Icon(IosCartWalletLineIcon,null,tint=Color(0xFF0F5C4D),modifier=Modifier.size(28.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Monedero", fontWeight = FontWeight.SemiBold, color = Color(0xFF0F5C4D))
                                Text(
                                    loyaltyWallet!!.walletValue.replace('.', ',') + " € disponibles",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF315B52)
                                )
                            }
                            if (walletOptions.isNotEmpty()) IosCartApplyButton({ showWalletDialog=true }, enabled=!loyaltyLoading)
                        }
                    }

                    if (loyaltyWallet!!.pendingRewards.any { reward ->
                        cart.coupons.none { it.code.equals(reward.code, ignoreCase = true) }
                    }) {
                        Text("Créditos listos para usar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Surface(Modifier.fillMaxWidth(), shape=RoundedCornerShape(14.dp), color=Color(0xFFF4F0F5)) {
                            Column(Modifier.fillMaxWidth().padding(horizontal=12.dp, vertical=8.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            loyaltyWallet!!.pendingRewards.forEach { reward ->
                                val applied = cart.coupons.any { it.code.equals(reward.code, ignoreCase = true) }
                                if (!applied) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    androidx.compose.material3.Icon(IosCartWalletLineIcon,null,tint=Color(0xFF0F5C4D),modifier=Modifier.size(22.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Crédito " + reward.amount.replace('.', ',') + " €",
                                        modifier = Modifier.weight(1f),
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (applied) {
                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = Color(0xFFE8F5EF)
                                        ) {
                                            Text(
                                                "Aplicado",
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                color = Color(0xFF0F5C4D),
                                                fontWeight = FontWeight.SemiBold,
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                    } else {
                                        IosCartApplyButton({ applyPendingReward(reward) }, enabled=!loyaltyLoading)
                                    }
                                }
                                }
                            }
                        }
                        }
                    }
                }

                when {
                    promotionsLoading -> CircularProgressIndicator(Modifier.size(20.dp))
                    !promotionsError.isNullOrBlank() -> Text(promotionsError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    promotions.isEmpty() -> Text("No hay promociones disponibles.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    else -> {
                        val hasAvailablePromotions = promotions.any { promotion ->
                            val code = promotion.code?.trim().orEmpty()
                            code.isBlank() || cart.coupons.none { it.code.equals(code, ignoreCase = true) }
                        }
                        if (hasAvailablePromotions) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Promociones disponibles", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        promotions.sortedByDescending { it.priority }.forEach { promotion ->
                            val code = promotion.code?.trim().orEmpty()
                            val applied = code.isNotBlank() && cart.coupons.any { it.code.equals(code, ignoreCase = true) }
                            val title = iosCartPromotionTitle(promotion)
                            val description = promotion.description.trim()
                            if (!applied) {
                            Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = iosCartPromotionBackground(promotion)
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(iosCartPromotionIcon(promotion), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 1.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(title, fontWeight = FontWeight.SemiBold)
                                            if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodySmall)
                                            promotion.expiresAt?.let { expires ->
                                                iosCartPromotionDate(expires)?.let { date ->
                                                    Text("Válido hasta $date", style = MaterialTheme.typography.bodySmall, color = Color(0xFF5F6368))
                                                }
                                            }
                                        }
                                        if (code.isNotBlank()) {
                                            IosCartApplyButton({ cartStore.applyCoupon(code) }, enabled=!applied && !couponLoading)
                                        }
                                    }
                            }
                            }
                        }
                    }
                    }
                }

                if (!couponExpanded) {
                    Row(Modifier.fillMaxWidth().clickable(enabled=!couponLoading) { couponExpanded=true }.padding(vertical=8.dp), verticalAlignment=Alignment.CenterVertically) { Text("+", fontWeight=FontWeight.Bold, color=Color(0xFF0F5C4D)); Spacer(Modifier.width(8.dp)); Text("Tengo otro código de descuento", Modifier.weight(1f), fontWeight=FontWeight.Medium); Text("⌄", color=Color(0xFF5F6368)) }
                } else {
                    Row(Modifier.fillMaxWidth().clickable(enabled=!couponLoading) { couponExpanded=false }.padding(vertical=8.dp), verticalAlignment=Alignment.CenterVertically) { Text("−", fontWeight=FontWeight.Bold, color=Color(0xFF0F5C4D)); Spacer(Modifier.width(8.dp)); Text("Tengo otro código de descuento", Modifier.weight(1f), fontWeight=FontWeight.Medium); Text("⌃", color=Color(0xFF5F6368)) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = couponCode,
                            onValueChange = { couponCode = it },
                            placeholder = { Text("Código") },
                             contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                            singleLine = true,
                            enabled = !couponLoading,
                            modifier = Modifier.weight(1f).height(48.dp)
                        )
                        IosCartApplyButton({ cartStore.applyCoupon(couponCode.trim()) }, enabled=couponCode.trim().isNotEmpty() && !couponLoading, loading=couponLoading, modifier=Modifier.height(58.dp))
                    }
                    if (!couponError.isNullOrBlank()) Text("🔴 Este cupón no es válido.", color=MaterialTheme.colorScheme.error, style=MaterialTheme.typography.bodySmall)
                }

                if (cart.coupons.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Descuentos aplicados", style=MaterialTheme.typography.titleSmall, fontWeight=FontWeight.SemiBold)
                        Surface(Modifier.fillMaxWidth(), shape=RoundedCornerShape(14.dp), color=Color(0xFFF9F9F7)) {
                            Column(Modifier.fillMaxWidth().padding(horizontal=10.dp, vertical=6.dp)) {
                                cart.coupons.forEachIndexed { index, coupon ->
                                    val isCredit = loyaltyWallet?.pendingRewards?.any { it.code.equals(coupon.code, ignoreCase=true) } == true
                                    val name = if (iosCartIsWalletCoupon(coupon.code)) "Monedero" else if (isCredit) "Crédito " + loyaltyWallet!!.pendingRewards.first { it.code.equals(coupon.code, ignoreCase=true) }.amount.replace('.', ',') + " €" else iosFriendlyCouponName(coupon.label, coupon.code)
                                    val key = "${coupon.label} ${coupon.code}".lowercase()
                                    val iconKind = when { iosCartIsWalletCoupon(coupon.code) -> "wallet"; isCredit -> "credit"; key.contains("blackcrios") || key.contains("black friday") -> "discount"; key.contains("cumple") || key.contains("birthday") -> "cake"; else -> "tag" }
                                    Row(Modifier.fillMaxWidth().heightIn(min=36.dp).padding(vertical=2.dp), verticalAlignment=Alignment.CenterVertically) {
                                        IosCartAppliedCouponIcon(iconKind)
                                        Spacer(Modifier.width(8.dp))
                                        Text(name, fontWeight=FontWeight.Medium, modifier=Modifier.weight(1f), maxLines=1, overflow=TextOverflow.Ellipsis)
                                        Text("-" + formatStorePrice(coupon.totals.consumerDiscount(), cart.totals.currencyMinorUnit, cart.totals.currencySymbol), fontWeight=FontWeight.SemiBold, color=Color(0xFF0F5C4D), modifier=Modifier.padding(start=8.dp))
                                        IconButton({ cartStore.removeCoupon(coupon.code) }, enabled=!couponLoading, modifier=Modifier.size(32.dp)) { androidx.compose.material3.Icon(IosCartCloseLineIcon, "Quitar", tint=Color(0xFF5F6368), modifier=Modifier.size(18.dp)) }
                                    }
                                    if (index < cart.coupons.lastIndex) HorizontalDivider(Modifier.padding(start=28.dp), color=Color(0xFFE8E8E3), thickness=1.dp)
                                }
                            }
                        }
                    }
                }

                loyaltyError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
        if (cart.items.isNotEmpty()) item {
            HorizontalDivider(color = Color(0xFFE8E8E3))
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Subtotal", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        formatStorePrice(cart.totals.totalItems, cart.totals.currencyMinorUnit, cart.totals.currencySymbol),
                        fontWeight = FontWeight.Medium
                    )
                }

                val discount = cart.totals.consumerDiscount()
                if (discount.toLongOrNull()?.let { it > 0L } == true) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Descuentos", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Color(0xFF315B52))
                        Text(
                            "-" + formatStorePrice(discount, cart.totals.currencyMinorUnit, cart.totals.currencySymbol),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF0F5C4D),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                when {
                    cart.totals.totalShipping == null -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Envío", modifier = Modifier.weight(1f), color = Color.Gray)
                        Text("Se calcula en el checkout", color = Color.Gray)
                    }
                    cart.totals.totalShipping == "0" || cart.totals.totalShipping == "0.00" -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Envío", modifier = Modifier.weight(1f))
                        Text("Gratis")
                    }
                    else -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Envío", modifier = Modifier.weight(1f))
                        Text(formatStorePrice(cart.totals.totalShipping.orEmpty(), cart.totals.currencyMinorUnit, cart.totals.currencySymbol))
                    }
                }

                HorizontalDivider(
                    color = Color(0xFFE8E8E3),
                    modifier = Modifier.padding(vertical = 3.dp)
                )

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Total", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        formatStorePrice(cart.totals.totalPrice, cart.totals.currencyMinorUnit, cart.totals.currencySymbol),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onCheckout,
                    enabled = state == StoreCartLoadState.SUCCESS_ITEMS,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF183B35))
                ) {
                    Text("Continuar compra")
                }
            }
        }
    }
    if (showWalletDialog && loyaltyWallet != null) {
        val eligibleSubtotal = subtractMoneyAmounts(
            addMoneyAmounts(cart.totals.totalItems, cart.totals.totalItemsTax),
            cart.totals.consumerDiscount()
        )
        val options = loyaltyWallet!!.redeemableOptions(eligibleSubtotal)
        if (options.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { if (!loyaltyLoading) showWalletDialog = false },
                title = { Text("¿Cuánto quieres utilizar?") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { option ->
                            Button(
                                onClick = { showWalletDialog = false; redeemWallet(option.points) },
                                enabled = !loyaltyLoading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (option.isMaximum) "Máximo: " + option.value + " €" else option.value + " €")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showWalletDialog = false }, enabled = !loyaltyLoading) { Text("Cancelar") }
                }
            )
        }
    }
    if (clearCartConfirm) AlertDialog(
        onDismissRequest = { clearCartConfirm = false },
        title = { Text("Vaciar carrito") },
        text = { Text("¿Quieres eliminar todos los productos del carrito?") },
        confirmButton = { TextButton(onClick = { clearCartConfirm = false; cartStore.clear() }) { Text("Vaciar") } },
        dismissButton = { TextButton(onClick = { clearCartConfirm = false }) { Text("Cancelar") } }
    )
}
@Composable
private fun IosSectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
}

@Composable
internal fun IosStoreLoading(label: String = "Cargando") {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(Modifier.size(28.dp))
            Spacer(Modifier.height(12.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun IosStoreEmpty(
    message: String,
    action: (() -> Unit)? = null,
    actionLabel: String? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (action != null && !actionLabel.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = action) { Text(actionLabel) }
            }
        }
    }
}

@Composable
internal fun IosStoreError(message: String, retry: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = retry) { Text("Reintentar") }
        }
    }
}

@Composable
private fun IosCategoryRootPreview(): Unit = Unit
