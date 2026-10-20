package es.criosrango.shared

import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import io.ktor.http.Url
import io.ktor.http.decodeURLPart
import org.jetbrains.compose.resources.painterResource
import es.criosrango.shared.generated.resources.Res
import es.criosrango.shared.generated.resources.home_logo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.clip
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
import es.criosrango.shared.promotions.cartVisiblePromotions
import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreProduct
import es.criosrango.shared.model.StoreCartVariation
import es.criosrango.shared.model.consumerDiscount
import es.criosrango.shared.model.consumerUnitPrice
import es.criosrango.shared.model.consumerLineTotal
import es.criosrango.shared.model.consumerSubtotal
import es.criosrango.shared.model.consumerShipping
import es.criosrango.shared.model.consumerTotal
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

internal enum class IosRootSection { HOME, CATEGORIES, OUTLET, CART, ACCOUNT }
internal enum class PostOrderSuccessType { CARD, BIZUM }
internal data class PostOrderSuccessSnapshot(val type: PostOrderSuccessType, val orderId: Int)
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

internal enum class IosOutletSeason { WINTER, SUMMER }

internal sealed class IosCatalogPage {
    data object Novedades : IosCatalogPage()
    data object Search : IosCatalogPage()
    data object Brands : IosCatalogPage()
    data class Outlet(val categoryId: Int? = 445, val season: IosOutletSeason? = null) : IosCatalogPage()
    data object Root : IosCatalogPage()
    data class RootFilter(val scope: StoreRootFilterScope) : IosCatalogPage()
    data class Category(val category: StoreCategory) : IosCatalogPage()
    data class Product(val product: StoreProduct) : IosCatalogPage()
}

internal fun iosHomeOutletPage(season: IosOutletSeason?): IosCatalogPage.Outlet =
    IosCatalogPage.Outlet(categoryId = 445, season = season)

internal fun iosSelectedRootTab(section: IosRootSection, catalogOriginHome: Boolean): StoreMainTab =
    if (catalogOriginHome) StoreMainTab.HOME else when (section) {
        IosRootSection.HOME -> StoreMainTab.HOME
        IosRootSection.CATEGORIES -> StoreMainTab.CATEGORIES
        IosRootSection.OUTLET -> StoreMainTab.OUTLET
        IosRootSection.CART -> StoreMainTab.CART
        IosRootSection.ACCOUNT -> StoreMainTab.ACCOUNT
    }

@Composable
fun CriosRangoIOSRootScreen(
    storeApi: es.criosrango.shared.api.StoreApiClient,
    accountRepository: AccountRepository,
    accountTokenStore: es.criosrango.shared.account.AccountTokenStore,
    loyaltyRepository: LoyaltyRepository,
    cartStore: StoreCartStore,
    checkoutStore: StoreCheckoutStore,
    paymentStore: StorePaymentStore,
    onOpenPayment: (String) -> Unit,
    onOpenExternalUrl: (String) -> Unit,
    onShareProductText: (String) -> Unit,
    onReserveProductMessage: (String) -> Unit,
    pushNavigation: IosPushNavigation? = null,
    onPushNavigationConsumed: () -> Unit = {}
) {
    var section by remember { mutableStateOf(IosRootSection.HOME) }
    var openPromotionsOnStart by remember { mutableStateOf(false) }
    var checkoutOpen by remember { mutableStateOf(false) }
    var postOrderSuccess by remember { mutableStateOf<PostOrderSuccessSnapshot?>(null) }
    var catalogPage by remember { mutableStateOf<IosCatalogPage>(IosCatalogPage.Root) }
    var catalogOriginHome by remember { mutableStateOf(false) }
    val catalogHistory = remember { mutableStateListOf<IosCatalogPage>() }
    val catalogRestoration = remember { mutableStateMapOf<Long, IosCatalogRestorationState>() }
    val outletControllerScope = rememberCoroutineScope()
    val rootFilterOwner = remember(storeApi) { StoreRootFilterOwner(storeApi, outletControllerScope) }
    val brandsController = remember(storeApi) { StoreBrandsController(storeApi, outletControllerScope) }
    val homeController = remember(storeApi, brandsController) { storeHomeController(storeApi, brandsController) }
    var startupMinimumDurationReached by remember(homeController) { mutableStateOf(false) }
    var startupHeroPreparationFinished by remember(homeController) { mutableStateOf(false) }
    LaunchedEffect(homeController) {
        homeController.start()
        homeController.prepareInitialHero()
        startupHeroPreparationFinished = true
    }
    LaunchedEffect(homeController) {
        delay(2_000)
        startupMinimumDurationReached = true
    }
    val showStartupBranding = !startupMinimumDurationReached || !startupHeroPreparationFinished
    val outletController = remember(storeApi) {
        StoreOutletController(outletControllerScope, storeOutletDataSource(storeApi))
    }
    var categorySnapshot by remember { mutableStateOf<List<StoreCategory>?>(null) }
    var categorySnapshotLoading by remember { mutableStateOf(true) }
    var categorySnapshotError by remember { mutableStateOf<String?>(null) }
    var categorySnapshotRetryGeneration by remember { mutableStateOf(0) }
    fun openCatalog(page: IosCatalogPage) { if (catalogPage != page) catalogHistory.add(catalogPage); catalogPage = page }
    fun resetCatalog() { catalogHistory.clear(); catalogPage = IosCatalogPage.Root; catalogOriginHome = false }
    fun retryCategorySnapshot() {
        categorySnapshot = null
        categorySnapshotError = null
        categorySnapshotLoading = true
        categorySnapshotRetryGeneration++
    }
    fun backCatalog() { catalogPage = if (catalogHistory.isNotEmpty()) catalogHistory.removeAt(catalogHistory.lastIndex) else IosCatalogPage.Root }

    LaunchedEffect(section, catalogPage, categorySnapshotRetryGeneration) {
        val needsCategoryTree = section == IosRootSection.CATEGORIES &&
            (catalogPage == IosCatalogPage.Root || catalogPage is IosCatalogPage.Category)
        if (!needsCategoryTree || categorySnapshot != null || categorySnapshotError != null) return@LaunchedEffect
        categorySnapshotLoading = true
        try {
            categorySnapshot = storeApi.categories(perPage = 100)
            categorySnapshotError = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            categorySnapshotError = "No se han podido cargar las categorías."
        } finally {
            categorySnapshotLoading = false
        }
    }

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

    val cartController = remember(cartStore) { StoreCartController(cartStore) }
    LaunchedEffect(cartController) {
        runCatching { cartController.refreshAwait() }
    }
    val observedCart by cartStore.cart.collectAsState()
    val cartQuantity = cartUnitCount(observedCart.items.map { it.quantity })
    val createdOrder by checkoutStore.createdOrder.collectAsState()
    val paymentState by paymentStore.state.collectAsState()
    val paymentOrderId by paymentStore.orderId.collectAsState()
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

    LaunchedEffect(createdOrder?.orderId, createdOrder?.paymentMethod, paymentState, paymentOrderId) {
        if (postOrderSuccess != null) return@LaunchedEffect
        val order = createdOrder
        val orderId = order?.orderId
        val method = order?.paymentMethod.orEmpty()
        if (orderId != null &&
            (method.equals("bizum", ignoreCase = true) || method.equals("cheque", ignoreCase = true))
        ) {
            postOrderSuccess = PostOrderSuccessSnapshot(PostOrderSuccessType.BIZUM, orderId)
            return@LaunchedEffect
        }
        if (paymentState == StoreCardPaymentState.PAID && paymentOrderId != null) {
            postOrderSuccess = PostOrderSuccessSnapshot(PostOrderSuccessType.CARD, paymentOrderId!!)
        }
    }

    MaterialTheme {
        androidx.compose.runtime.LaunchedEffect(Unit) { println("KMP_RUNTIME_ROOT_SCREEN_READY") }
        Box(Modifier.fillMaxSize()) {
        StoreGlobalShell(
            selectedTab = iosSelectedRootTab(section, catalogOriginHome),
            cartQuantity = cartQuantity,
            onTabSelected = { item ->
                val destination = when (item) {
                    StoreMainTab.HOME -> IosRootSection.HOME
                    StoreMainTab.CATEGORIES -> IosRootSection.CATEGORIES
                    StoreMainTab.OUTLET -> IosRootSection.OUTLET
                    StoreMainTab.CART -> IosRootSection.CART
                    StoreMainTab.ACCOUNT -> IosRootSection.ACCOUNT
                }
                section = destination
                // Every root-tab selection exits the current child route, including Home.
                resetCatalog()
                if (destination == IosRootSection.OUTLET) openCatalog(IosCatalogPage.Outlet())
            },
            topBar = {
                if (!checkoutOpen) StoreGlobalTopBar(
                    cartQuantity = cartQuantity,
                    onSearch = { section = IosRootSection.CATEGORIES; resetCatalog(); openCatalog(IosCatalogPage.Search) },
                    onCart = { resetCatalog(); section = IosRootSection.CART }
                )
            },
            bottomNavigationModifier = Modifier.navigationBarsPadding()
        ) { paddingValues ->
            val padding = paddingValues
            Column(Modifier.fillMaxSize()) {
            if (checkoutOpen && postOrderSuccess == null) {
                StoreCheckoutScreen(
                    checkoutStore = checkoutStore,
                    paymentStore = paymentStore,
                    accountRepository = accountRepository,
                    padding = padding,
                    onBack = { checkoutOpen = false },
                    onOpenPayment = onOpenPayment,
                    loyaltyRepository = loyaltyRepository,
                    walletEnabled = accountRepository.hasSession,
                    cartQuantity = cartQuantity,
                    onSearch = { section = IosRootSection.CATEGORIES; resetCatalog(); openCatalog(IosCatalogPage.Search) },
                    onCart = { resetCatalog(); section = IosRootSection.CART }
                )
            } else if (!checkoutOpen) when (section) {
                IosRootSection.HOME -> Box(Modifier.fillMaxSize()) {
                    StoreHomeScreen(
                        storeApi = storeApi,
                        brandsController = brandsController,
                        homeController = homeController,
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
                            catalogOriginHome = true
                            openCatalog(IosCatalogPage.Product(it))
                        },
                        onNovedades = { section = IosRootSection.CATEGORIES; resetCatalog(); catalogOriginHome = true; openCatalog(IosCatalogPage.Novedades) },
                        onBrand = {
                            section = IosRootSection.CATEGORIES
                            resetCatalog()
                            catalogOriginHome = true
                            brandsController.selectBrand(it)
                            openCatalog(IosCatalogPage.Brands)
                        },
                        onAllBrands = {
                            resetCatalog()
                            catalogOriginHome = true
                            openCatalog(IosCatalogPage.Brands)
                        },
                        onOutletWinter = { _ -> section = IosRootSection.OUTLET; resetCatalog(); catalogOriginHome = true; openCatalog(iosHomeOutletPage(IosOutletSeason.WINTER)) },
                        onOutletSummer = { _ -> section = IosRootSection.OUTLET; resetCatalog(); catalogOriginHome = true; openCatalog(iosHomeOutletPage(IosOutletSeason.SUMMER)) },
                        onOutletAll = { section = IosRootSection.OUTLET; resetCatalog(); catalogOriginHome = true; openCatalog(iosHomeOutletPage(null)) }
                    )
                    if (catalogOriginHome && catalogPage != IosCatalogPage.Root) {
                        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                            IosCatalogScreen(
                                storeApi = storeApi,
                                rootFilterOwner = rootFilterOwner,
                                brandsController = brandsController,
                                padding = padding,
                                page = catalogPage,
                                cartStore = cartStore,
                                outletController = outletController,
                                onOpenCategory = { openCatalog(IosCatalogPage.Category(it)) },
                                onOpenRootFilter = { scope -> rootFilterOwner.open(scope) },
                                onOpenProduct = { openCatalog(IosCatalogPage.Product(it)) },
                                onBack = {
                                    if (section == IosRootSection.OUTLET && catalogPage is IosCatalogPage.Outlet) {
                                        section = IosRootSection.HOME
                                        resetCatalog()
                                    } else if (catalogPage == IosCatalogPage.Root) {
                                        section = IosRootSection.HOME
                                        resetCatalog()
                                    } else {
                                        backCatalog()
                                        if (shouldReturnHomeAfterCatalogBack(catalogHistory.isEmpty(), catalogPage == IosCatalogPage.Root, catalogOriginHome)) {
                                            section = IosRootSection.HOME
                                            resetCatalog()
                                        }
                                    }
                                },
                                onShareProductText = onShareProductText,
                                onReserveProductMessage = onReserveProductMessage,
                                catalogRestoration = catalogRestoration,
                                categorySnapshot = categorySnapshot,
                                categorySnapshotLoading = categorySnapshotLoading,
                                categorySnapshotError = categorySnapshotError,
                                onRetryCategorySnapshot = ::retryCategorySnapshot
                            )
                        }
                    }
                }
                IosRootSection.OUTLET, IosRootSection.CATEGORIES -> IosCatalogScreen(
                    storeApi = storeApi,
                    rootFilterOwner = rootFilterOwner,
                    brandsController = brandsController,
                    padding = padding,
                    page = catalogPage,
                    cartStore = cartStore,
                    outletController = outletController,
                    onOpenCategory = { openCatalog(IosCatalogPage.Category(it)) },
                    onOpenRootFilter = { scope -> rootFilterOwner.open(scope) },
                    onOpenProduct = { openCatalog(IosCatalogPage.Product(it)) },
                    onBack = {
                        if (section == IosRootSection.OUTLET && catalogPage is IosCatalogPage.Outlet) {
                            section = IosRootSection.HOME
                            resetCatalog()
                        } else if (catalogPage == IosCatalogPage.Root) {
                            section = IosRootSection.HOME
                            resetCatalog()
                        } else {
                            backCatalog()
                            if (shouldReturnHomeAfterCatalogBack(catalogHistory.isEmpty(), catalogPage == IosCatalogPage.Root, catalogOriginHome)) {
                                section = IosRootSection.HOME
                                resetCatalog()
                            }
                        }
                    },
                    onShareProductText = onShareProductText,
                    onReserveProductMessage = onReserveProductMessage,
                    catalogRestoration = catalogRestoration,
                    categorySnapshot = categorySnapshot,
                    categorySnapshotLoading = categorySnapshotLoading,
                    categorySnapshotError = categorySnapshotError,
                    onRetryCategorySnapshot = ::retryCategorySnapshot
                )
                IosRootSection.CART -> StoreCartScreen(
                    storeApi = storeApi,
                    cartController = cartController,
                    loyaltyRepository = loyaltyRepository,
                    walletEnabled = accountRepository.hasSession,
                    padding = padding,
                    onCheckout = { checkoutOpen = true },
                    onOpenProduct = { product -> section = IosRootSection.CATEGORIES; catalogPage = IosCatalogPage.Product(product) },
                    isAuthenticated = accountRepository.hasSession,
                    onLogin = { resetCatalog(); section = IosRootSection.ACCOUNT },
                    promotionBearerTokenProvider = { accountTokenStore.load() },
                    refreshOnEnter = false
                )
                IosRootSection.ACCOUNT -> CriosRangoIOSAccountScreen(
                    repository = accountRepository,
                    loyaltyRepository = loyaltyRepository,
                    cartCouponCodes = cartStore.cart.collectAsState().value.coupons.map { it.code }.toSet(),
                    onApplyWalletCoupon = { code -> cartStore.applyCoupon(code) },
                    modifier = Modifier.padding(padding),
                    initialOrderId = pushNavigation?.orderId,
                    openPromotionsOnStart = openPromotionsOnStart,
                    onPromotionsOpened = { openPromotionsOnStart = false },
                    onBeforeLogout = { cartStore.clearForLogoutAwait(); checkoutStore.resetForLogout(); checkoutOpen = false },
                    onOpenExternalUrl = onOpenExternalUrl
                )
            }
            }
        }
        if (showStartupBranding) StoreStartupBranding()
        }
        postOrderSuccess?.let { success ->
            AlertDialog(
                onDismissRequest = {},
                title = { Text("Pedido recibido") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when (success.type) {
                            PostOrderSuccessType.CARD -> Text("Tu pedido #${success.orderId} se ha pagado correctamente.")
                            PostOrderSuccessType.BIZUM -> {
                                Text("Pedido #${success.orderId} creado correctamente.")
                                Text("Realiza el pago por Bizum al 679 97 28 88.")
                                Text("Utiliza el número de pedido #${success.orderId} como referencia de pago.")
                                Text("El pedido no se procesará hasta que recibamos el importe.")
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        postOrderSuccess = null
                        checkoutStore.clearForNewProcess()
                        paymentStore.clearForNewProcess()
                        checkoutOpen = false
                        section = IosRootSection.HOME
                    }) { Text("Seguir comprando") }
                }
            )
        }
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
        Text(normalizeWooPlainText(product.name), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
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

@Composable
private fun IosCartApplyButton(onClick: () -> Unit, enabled: Boolean = true, loading: Boolean = false, modifier: Modifier = Modifier.height(36.dp)) = Button(onClick=onClick, enabled=enabled, shape=RoundedCornerShape(50), colors=androidx.compose.material3.ButtonDefaults.buttonColors(containerColor=Color(0xFF0F5C4D), contentColor=Color.White), contentPadding=PaddingValues(horizontal=12.dp, vertical=0.dp), modifier=modifier) { if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth=2.dp, color=Color.White) else Text("Aplicar", style=MaterialTheme.typography.labelMedium) }
private fun iosBuildLineIcon(name:String, content: androidx.compose.ui.graphics.vector.PathBuilder.()->Unit): androidx.compose.ui.graphics.vector.ImageVector = androidx.compose.ui.graphics.vector.ImageVector.Builder(name=name,defaultWidth=24.dp,defaultHeight=24.dp,viewportWidth=24f,viewportHeight=24f).apply { path(fill=null,stroke=androidx.compose.ui.graphics.SolidColor(Color.Black),strokeLineWidth=1.8f,strokeLineCap=androidx.compose.ui.graphics.StrokeCap.Round,strokeLineJoin=androidx.compose.ui.graphics.StrokeJoin.Round,pathBuilder=content) }.build()
private val IosCartWalletLineIcon = iosBuildLineIcon("IosCartWalletLineIcon") { moveTo(3.5f,6.5f); lineTo(18.5f,6.5f); lineTo(20.5f,8.5f); lineTo(20.5f,18f); lineTo(3.5f,18f); close(); moveTo(3.5f,6.5f); lineTo(3.5f,5f); lineTo(17f,5f); moveTo(15.5f,12.5f); lineTo(20.5f,12.5f); moveTo(17.5f,12.5f); lineTo(17.5f,12.5f) }
private val IosCartDiscountLineIcon = iosBuildLineIcon("IosCartDiscountLineIcon") { moveTo(4f,7f); lineTo(20f,7f); lineTo(20f,17f); lineTo(4f,17f); close(); moveTo(8f,12f); lineTo(16f,12f) }
private val IosCartTagLineIcon = iosBuildLineIcon("IosCartTagLineIcon") { moveTo(3.5f,11f); lineTo(11f,3.5f); lineTo(20f,12.5f); lineTo(12.5f,20f); close(); moveTo(8f,8f); lineTo(8f,8f) }
private val IosCartCakeLineIcon = iosBuildLineIcon("IosCartCakeLineIcon") { moveTo(4f,10f); lineTo(20f,10f); lineTo(20f,19f); lineTo(4f,19f); close(); moveTo(4f,14f); lineTo(20f,14f); moveTo(8f,10f); lineTo(8f,7f); moveTo(12f,10f); lineTo(12f,6f); moveTo(16f,10f); lineTo(16f,7f) }
private val IosCartCloseLineIcon = iosBuildLineIcon("IosCartCloseLineIcon") { moveTo(7f,7f); lineTo(17f,17f); moveTo(17f,7f); lineTo(7f,17f) }
private val IosCartDeleteLineIcon = iosBuildLineIcon("IosCartDeleteLineIcon") { moveTo(4f,7f); lineTo(20f,7f); moveTo(9f,7f); lineTo(9f,19f); moveTo(15f,7f); lineTo(15f,19f); moveTo(6f,7f); lineTo(7f,21f); lineTo(17f,21f); lineTo(18f,7f); moveTo(9f,4f); lineTo(15f,4f) }
private val IosCartInfoLineIcon = iosBuildLineIcon("IosCartInfoLineIcon") { moveTo(12f,3f); lineTo(16.5f,4.2f); lineTo(19.8f,7.5f); lineTo(21f,12f); lineTo(19.8f,16.5f); lineTo(16.5f,19.8f); lineTo(12f,21f); lineTo(7.5f,19.8f); lineTo(4.2f,16.5f); lineTo(3f,12f); lineTo(4.2f,7.5f); lineTo(7.5f,4.2f); close(); moveTo(12f,11f); lineTo(12f,16f); moveTo(12f,8f); lineTo(12f,8.1f) }
@Composable private fun IosCartAppliedCouponIcon(kind:String) { val icon=when(kind){"wallet"->IosCartWalletLineIcon;"credit"->IosCartWalletLineIcon;"discount"->IosCartDiscountLineIcon;"cake"->IosCartCakeLineIcon;else->IosCartTagLineIcon}; androidx.compose.material3.Icon(icon,null,tint=Color(0xFF0F5C4D),modifier=Modifier.size(20.dp)) }

@Composable
fun StoreCartScreen(
    storeApi: es.criosrango.shared.api.StoreApiClient,
    cartController: StoreCartController,
    loyaltyRepository: LoyaltyRepository,
    walletEnabled: Boolean,
    padding: PaddingValues,
    onCheckout: () -> Unit,
    onOpenProduct: (StoreProduct) -> Unit,
    accountUserId: Int? = null,
    onLogin: (() -> Unit)? = null,
    isAuthenticated: Boolean = accountUserId != null,
    promotionBearerTokenProvider: (() -> String?)? = null,
    refreshOnEnter: Boolean = true
) {
    var loyaltyWallet by remember { mutableStateOf<LoyaltyWallet?>(null) }
    var loyaltyLoading by remember { mutableStateOf(false) }
    var loyaltyError by remember { mutableStateOf<String?>(null) }
    var showWalletDialog by remember { mutableStateOf(false) }
    var showWalletInfoDialog by remember { mutableStateOf(false) }
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
                    cartController.applyCoupon(coupon.code)
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
                cartController.applyCoupon(reward.code)
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
    val cartUiState by cartController.state.collectAsState()
    val cart = cartUiState.cart
    val state = cartUiState.loadState
    val error = cartUiState.error
    val couponLoading = cartUiState.couponLoading
    val couponError = cartUiState.couponError
    var clearCartConfirm by remember { mutableStateOf(false) }
    var couponExpanded by remember { mutableStateOf(false) }
    var couponCode by remember { mutableStateOf("") }
    val promotionRepository = remember(promotionBearerTokenProvider) { PromotionRepository(bearerTokenProvider = promotionBearerTokenProvider) }
    var promotions by remember { mutableStateOf(emptyList<Promotion>()) }
    var promotionsLoading by remember { mutableStateOf(true) }
    var promotionsError by remember { mutableStateOf<String?>(null) }
    var openingProductKey by remember { mutableStateOf<String?>(null) }
    var productOpenError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (refreshOnEnter) cartController.refresh()
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
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Tu carrito", modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                if (cart.items.isNotEmpty()) TextButton(onClick = { clearCartConfirm = true }) {
                    androidx.compose.material3.Icon(IosCartDeleteLineIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Vaciar carrito")
                }
            }
        }
        if (state == StoreCartLoadState.LOADING && cart.items.isEmpty()) item { IosStoreLoading() }
        if (!error.isNullOrBlank()) item { IosStoreError(error!!, cartController::refresh) }
        if (!productOpenError.isNullOrBlank()) item {
            IosStoreError(productOpenError!!) { productOpenError = null }
        }
        if (state == StoreCartLoadState.SUCCESS_EMPTY) item {
            Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Tu carrito está vacío", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(cart.items, key = { it.key }) { line ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(enabled = openingProductKey == null) {
                    loyaltyScope.launch {
                        openingProductKey = line.key
                        productOpenError = null
                        try {
                            val path = Url(line.permalink).encodedPath.trimEnd('/')
                            val slug = path.substringAfterLast('/').decodeURLPart()
                            require(slug.isNotBlank()) { "La URL del producto no contiene un slug válido." }
                            val parentProduct = storeApi.productBySlug(slug)
                            onOpenProduct(parentProduct)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            productOpenError = e.message ?: "No se ha podido abrir el producto desde el carrito."
                        } finally {
                            openingProductKey = null
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFF3FAF7),
                tonalElevation = 0.dp,
                shadowElevation = 1.dp
            ) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    RemoteStoreImage(
                        line.images.firstOrNull()?.src,
                        line.name,
                        modifier = Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop,
                        backgroundColor = Color(0xFFF3FAF7)
                    )
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(
                            normalizeWooPlainText(line.name),
                            modifier = Modifier.fillMaxWidth(),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (openingProductKey == line.key) Text("Abriendo producto…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (line.variation.isNotEmpty()) Text(
                            line.variation.joinToString(" · ") { "${it.attribute.removePrefix("pa_")}: ${it.value}" },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(formatStorePrice(line.consumerUnitPrice(), line.prices.currencyMinorUnit, line.prices.currencySymbol) + " / ud.", fontWeight = FontWeight.Bold)
                        Text(
                            "Subtotal: " + formatStorePrice(line.consumerLineTotal(), line.prices.currencyMinorUnit, line.prices.currencySymbol),
                            style = MaterialTheme.typography.bodySmall
                        )
                        val increment = line.quantityStep()
                        val nextLowerQuantity = line.quantityAfterDecrement()
                        Box(Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        if (nextLowerQuantity == null) cartController.remove(line)
                                        else cartController.update(line, nextLowerQuantity)
                                    }
                                ) { Text("−", style = MaterialTheme.typography.titleLarge) }
                                Text(line.quantity.toString(), modifier = Modifier.padding(horizontal = 8.dp), fontWeight = FontWeight.SemiBold)
                                IconButton(
                                    onClick = { cartController.update(line, line.quantity + increment) },
                                    enabled = line.canIncreaseQuantity()
                                ) { Text("+", style = MaterialTheme.typography.titleLarge) }
                            }
                            IconButton(
                                onClick = { cartController.remove(line) },
                                modifier = Modifier.align(Alignment.CenterEnd).size(40.dp)
                            ) {
                                androidx.compose.material3.Icon(IosCartDeleteLineIcon, contentDescription = "Eliminar", tint = Color(0xFF5F6368), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
        if (cart.items.isNotEmpty()) item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Promociones y descuentos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                if (walletEnabled && loyaltyWallet != null) {
                    val eligibleSubtotal = subtractMoneyAmounts(
                        addMoneyAmounts(cart.totals.totalItems, cart.totals.totalItemsTax),
                        cart.totals.consumerDiscount()
                    )
                    val walletOptions = loyaltyWallet!!.redeemableOptions(eligibleSubtotal)
                    val walletApplied = cart.coupons.any { coupon ->
                        loyaltyWallet!!.pendingRewards.any { reward -> reward.code.equals(coupon.code, ignoreCase = true) }
                    }
                    val hasUnappliedReward = loyaltyWallet!!.pendingRewards.any { reward ->
                        cart.coupons.none { it.code.equals(reward.code, ignoreCase = true) }
                    }
                    val walletVisibility = cartWalletUiVisibility(
                        walletEnabled = walletEnabled,
                        walletLoaded = loyaltyWallet != null,
                        hasUnappliedReward = hasUnappliedReward
                    )

                    if (walletVisibility.showSummary) Surface(
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
                            if (walletOptions.isNotEmpty() && !walletApplied) IosCartApplyButton({ showWalletDialog=true }, enabled=!loyaltyLoading)
                            IconButton(
                                onClick = { showWalletInfoDialog = true },
                                modifier = Modifier.size(40.dp).semantics { contentDescription = "Información del monedero" }
                            ) {
                                androidx.compose.material3.Icon(
                                    IosCartInfoLineIcon,
                                    contentDescription = null,
                                    tint = Color(0xFF0F5C4D),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    if (walletVisibility.showApplyCandidates) {
                        Text("Saldo listo para usar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
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
                                        "Saldo de monedero · " + reward.amount.replace('.', ',') + " €",
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

                val visiblePromotions = cartVisiblePromotions(promotions, authenticated = isAuthenticated)
                when {
                    promotionsLoading -> CircularProgressIndicator(Modifier.size(20.dp))
                    !promotionsError.isNullOrBlank() -> Text(promotionsError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    visiblePromotions.isEmpty() -> Text("No hay promociones disponibles.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    else -> {
                        val hasAvailablePromotions = visiblePromotions.any { promotion ->
                            val code = promotion.code?.trim().orEmpty()
                            code.isBlank() || cart.coupons.none { it.code.equals(code, ignoreCase = true) }
                        }
                        if (hasAvailablePromotions) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Promociones disponibles", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        visiblePromotions.sortedByDescending { it.priority }.forEach { promotion ->
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
                                            IosCartApplyButton({ cartController.applyCoupon(code) }, enabled=!applied && !couponLoading)
                                        }
                                    }
                            }
                            }
                        }
                    }
                    }
                }

                if (!couponError.isNullOrBlank()) Text(couponError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)

                if (!couponExpanded) {
                    Row(Modifier.fillMaxWidth().clickable(enabled=!couponLoading) { couponExpanded=true }.padding(vertical=8.dp), verticalAlignment=Alignment.CenterVertically) { Text("+", fontWeight=FontWeight.Bold, color=Color(0xFF0F5C4D)); Spacer(Modifier.width(8.dp)); Text("Tengo otro código de descuento", Modifier.weight(1f), fontWeight=FontWeight.Medium); Text("⌄", color=Color(0xFF5F6368)) }
                } else {
                    Row(Modifier.fillMaxWidth().clickable(enabled=!couponLoading) { couponExpanded=false }.padding(vertical=8.dp), verticalAlignment=Alignment.CenterVertically) { Text("−", fontWeight=FontWeight.Bold, color=Color(0xFF0F5C4D)); Spacer(Modifier.width(8.dp)); Text("Tengo otro código de descuento", Modifier.weight(1f), fontWeight=FontWeight.Medium); Text("⌃", color=Color(0xFF5F6368)) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BasicTextField(
                            value = couponCode,
                            onValueChange = { couponCode = it },
                            singleLine = true,
                            enabled = !couponLoading,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                                .padding(horizontal = 18.dp),
                            decorationBox = { innerTextField ->
                                Box(
                                    Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (couponCode.isEmpty()) {
                                        Text("Código", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        IosCartApplyButton({ cartController.applyCoupon(couponCode.trim()) }, enabled=couponCode.trim().isNotEmpty() && !couponLoading, loading=couponLoading, modifier=Modifier.height(54.dp))
                    }
                }

                if (cart.coupons.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Descuentos aplicados", style=MaterialTheme.typography.titleSmall, fontWeight=FontWeight.SemiBold)
                        Surface(Modifier.fillMaxWidth(), shape=RoundedCornerShape(14.dp), color=Color(0xFFF9F9F7)) {
                            Column(Modifier.fillMaxWidth().padding(horizontal=10.dp, vertical=6.dp)) {
                                cart.coupons.forEachIndexed { index, coupon ->
                                    val presentation = resolveCouponPresentation(coupon, loyaltyWallet?.pendingRewards.orEmpty())
                                    val name = presentation.title
                                    val iconKind = when (presentation.kind) {
                                        CouponPresentationKind.WALLET -> "wallet"
                                        CouponPresentationKind.DISCOUNT, CouponPresentationKind.WELCOME -> "discount"
                                        CouponPresentationKind.BIRTHDAY -> "cake"
                                        CouponPresentationKind.OTHER -> "tag"
                                    }
                                    Row(Modifier.fillMaxWidth().heightIn(min=36.dp).padding(vertical=2.dp), verticalAlignment=Alignment.CenterVertically) {
                                        IosCartAppliedCouponIcon(iconKind)
                                        Spacer(Modifier.width(4.dp))
                                        Text(name, fontWeight=FontWeight.Medium, style=MaterialTheme.typography.labelSmall, modifier=Modifier.weight(1f), maxLines=1, overflow=TextOverflow.Clip)
                                        Text("-" + formatStorePrice(coupon.totals.consumerDiscount(), cart.totals.currencyMinorUnit, cart.totals.currencySymbol), fontWeight=FontWeight.SemiBold, style=MaterialTheme.typography.labelSmall, color=Color(0xFF0F5C4D), modifier=Modifier.padding(start=3.dp))
                                        IconButton({ cartController.removeCoupon(coupon.code) }, enabled=!couponLoading, modifier=Modifier.size(28.dp)) { androidx.compose.material3.Icon(IosCartCloseLineIcon, "Quitar", tint=Color(0xFF5F6368), modifier=Modifier.size(18.dp)) }
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
                        formatStorePrice(cart.totals.consumerSubtotal(), cart.totals.currencyMinorUnit, cart.totals.currencySymbol),
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
                    cart.totals.consumerShipping() == null -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Envío", modifier = Modifier.weight(1f), color = Color.Gray)
                        Text("Se calcula en el checkout", color = Color.Gray)
                    }
                    cart.totals.consumerShipping() == "0" -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Envío", modifier = Modifier.weight(1f))
                        Text("Gratis")
                    }
                    else -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Envío", modifier = Modifier.weight(1f))
                        Text(formatStorePrice(cart.totals.consumerShipping().orEmpty(), cart.totals.currencyMinorUnit, cart.totals.currencySymbol))
                    }
                }

                HorizontalDivider(
                    color = Color(0xFFE8E8E3),
                    modifier = Modifier.padding(vertical = 3.dp)
                )

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Total", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        formatStorePrice(cart.totals.consumerTotal(), cart.totals.currencyMinorUnit, cart.totals.currencySymbol),
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
                if (shouldShowCartLoginCta(isAuthenticated, onLogin != null)) {
                    OutlinedButton(
                        onClick = { onLogin?.invoke() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Inicia sesión para consultar tu cuenta")
                    }
                }
            }
        }
    }
    if (clearCartConfirm) {
        AlertDialog(
            onDismissRequest = { clearCartConfirm = false },
            title = { Text("Vaciar carrito") },
            text = { Text("¿Quieres quitar todos los productos del carrito?") },
            confirmButton = {
                TextButton(onClick = { clearCartConfirm = false; cartController.clear() }) { Text("Vaciar") }
            },
            dismissButton = {
                TextButton(onClick = { clearCartConfirm = false }) { Text("Cancelar") }
            }
        )
    }
    if (showWalletInfoDialog) {
        AlertDialog(
            onDismissRequest = { showWalletInfoDialog = false },
            title = { Text("¿Cómo funciona tu monedero?", fontWeight = FontWeight.SemiBold) },
            text = { Text("1 € gastado = 1 punto.\n\nCuando acumules 100 puntos, podrás convertirlos en 5 € de saldo para tus compras.\n\nLa barra te indica cuánto te falta para desbloquear el saldo.\n\nCuando tengas saldo disponible, pulsa “Usar” para elegir cuánto quieres aplicar a tu compra.") },
            confirmButton = { Button(onClick = { showWalletInfoDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F5C4D), contentColor = Color.White)) { Text("Entendido") } }
        )
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

internal fun shouldReturnHomeAfterCatalogBack(historyIsEmpty: Boolean, pageIsRoot: Boolean, catalogOriginHome: Boolean): Boolean =
    historyIsEmpty && pageIsRoot && catalogOriginHome
