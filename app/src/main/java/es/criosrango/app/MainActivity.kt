package es.criosrango.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.ui.draw.scale

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import kotlinx.coroutines.delay
import android.view.TextureView
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.material.icons.outlined.*
import android.os.Bundle
import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.util.Log
import es.criosrango.shared.PushNotificationContract
import es.criosrango.shared.PushNotificationType
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.text.HtmlCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import compose.icons.TablerIcons
import compose.icons.tablericons.Bed
import compose.icons.tablericons.Hanger
import compose.icons.tablericons.Shirt
import compose.icons.tablericons.Tag
import kotlinx.coroutines.launch

enum class BrandOrigin { HOME, ALL_BRANDS }
enum class AppTab(val label: String) { HOME("Inicio"), CATEGORIES("Categorías"), OUTLET("Outlet"), SEARCH("Buscar"), CART("Carrito"), ACCOUNT("Cuenta") }
private val paymentReturnUriState = androidx.compose.runtime.mutableStateOf<android.net.Uri?>(null)
private val pushTypeState = androidx.compose.runtime.mutableStateOf<String?>(null)
private val pushOrderIdState = androidx.compose.runtime.mutableStateOf<Int?>(null)
private const val NOTIFICATION_PERMISSION_REQUESTED_KEY = "notification_permission_requested"

internal fun shouldRequestNotificationPermission(apiLevel: Int, alreadyRequested: Boolean, granted: Boolean): Boolean =
    apiLevel >= Build.VERSION_CODES.TIRAMISU && !alreadyRequested && !granted

class MainActivity : ComponentActivity() {
    companion object {
        private var startupBrandingShownInProcess = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        paymentReturnUriState.value = intent?.data
        pushTypeState.value = intent?.getStringExtra(PushNotificationContract.TYPE_KEY)
        pushOrderIdState.value = PushNotificationContract.parseOrderId(intent?.getStringExtra(PushNotificationContract.ORDER_ID_KEY))
        val preferences = getSharedPreferences("criosrango", MODE_PRIVATE)
        val session = StoreSession(preferences)
        val sharedSession = AndroidStoreSessionStore(session)
        val accountTokenStore = AccountSessionStore.shared(applicationContext)
        val sharedCatalogClient = es.criosrango.shared.api.StoreApiClient(
            session = sharedSession,
            accountTokenStore = accountTokenStore
        )
        val catalogApi = SharedCatalogStoreApiAdapter(sharedCatalogClient, session)
        val cartStore = CartStore(catalogApi, session, preferences)
        val pendingCardPaymentStore = PendingCardPaymentStore.create(applicationContext)
        val categoryDatabase = CategoryProductCacheDatabase.create(applicationContext)
        val categoryCache = CategoryCatalogCache(categoryDatabase)
        val cachedApi = CategoryCacheStoreApi(catalogApi, categoryCache)
        val repository = StoreRepository(cachedApi)
        categoryCache.bindRepository(repository)
        val outletAvailabilityStore = OutletAvailabilityStore(repository, preferences)
        val shopViewModel = androidx.lifecycle.ViewModelProvider(this, ShopViewModel.Factory(repository, cartStore, DeliveryAddressStore(preferences), pendingCardPaymentStore))[ShopViewModel::class.java]
        val coldStartBranding = !startupBrandingShownInProcess
        startupBrandingShownInProcess = true
        setContent { CriosRangoApp(shopViewModel, categoryCache, outletAvailabilityStore, coldStartBranding) }
    }
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        paymentReturnUriState.value = intent.data
        pushTypeState.value = intent.getStringExtra(PushNotificationContract.TYPE_KEY)
        pushOrderIdState.value = PushNotificationContract.parseOrderId(intent.getStringExtra(PushNotificationContract.ORDER_ID_KEY))
    }
    override fun onResume() { super.onResume(); PushNotificationController.initialize(this) }
}

@Composable
private fun StartupBranding() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAF7F0)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 28.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.splash_logo),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

internal fun shouldShowStartupBranding(
    coldStart: Boolean,
    minimumDurationReached: Boolean,
    appReady: Boolean
): Boolean = coldStart && (!minimumDurationReached || !appReady)

@Composable
private fun CriosRangoApp(
    viewModel: ShopViewModel,
    categoryCache: CategoryCatalogCache,
    outletAvailabilityStore: OutletAvailabilityStore,
    coldStartBranding: Boolean
) {
    val accountViewModel: AccountViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val loyaltyViewModel: LoyaltyViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val loyaltyWallet by loyaltyViewModel.wallet.collectAsStateWithLifecycle()
    val accountUser by accountViewModel.user.collectAsStateWithLifecycle()
    val products by viewModel.products.collectAsStateWithLifecycle()
    val activeCategoryProducts by viewModel.activeCategoryProducts.collectAsStateWithLifecycle()
    val categoryPagingState by viewModel.categoryPagingState.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val homeProducts by viewModel.homeProducts.collectAsStateWithLifecycle()
    val brands by viewModel.brands.collectAsStateWithLifecycle()
    val normalizedBrands = remember(brands) { brands.map { brand -> brand.copy(name = brand.name.decodeBrandEntities()) }.filter { it.name.isNotBlank() }.distinctBy { if (it.slug.isNotBlank()) it.slug.lowercase().trim() else it.name.lowercase().trim() } }
    val selectedProduct by viewModel.selectedProduct.collectAsStateWithLifecycle()
    val selectedVariation by viewModel.selectedVariation.collectAsStateWithLifecycle()
    val remoteCart by viewModel.cartStore.cart.collectAsStateWithLifecycle()
    val cartState by viewModel.cartStore.state.collectAsStateWithLifecycle()
    val cartError by viewModel.cartStore.error.collectAsStateWithLifecycle()
    val couponLoading by viewModel.cartStore.couponLoading.collectAsStateWithLifecycle()
    val couponError by viewModel.cartStore.couponError.collectAsStateWithLifecycle()
    val checkout by viewModel.checkout.collectAsStateWithLifecycle()
    val checkoutError by viewModel.checkoutError.collectAsStateWithLifecycle()
    val checkoutLoading by viewModel.checkoutLoading.collectAsStateWithLifecycle()
    val checkoutPhase by viewModel.checkoutPhase.collectAsStateWithLifecycle()
    val paymentRedirect by viewModel.paymentRedirect.collectAsStateWithLifecycle()
    val cardPaymentResult by viewModel.cardPaymentResult.collectAsStateWithLifecycle()
    val paymentReturnUri = paymentReturnUriState.value
    val pushType = pushTypeState.value
    val pushOrderId = pushOrderIdState.value
    val bizumOrderId by viewModel.bizumOrderId.collectAsStateWithLifecycle()
    LaunchedEffect(checkout?.orderId, checkout?.orderKey) {
        val orderId = checkout?.orderId ?: return@LaunchedEffect
        val orderKey = checkout?.orderKey?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        accountViewModel.prepareClaimOrder(orderId, orderKey)
    }
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { PushNotificationController.initialize(context) }

    val cartItems = remoteCart.items.map { line -> CartItem(lineKey = line.key, productId = line.parentProductId ?: line.id, name = line.name.cleanWooText(), imageUrl = line.images.firstOrNull()?.src.orEmpty(), unitPrice = line.prices.price, variationId = line.id, quantity = line.quantity) }
    val loading by viewModel.isLoading.collectAsStateWithLifecycle()
    val initialLoading by viewModel.initialLoading.collectAsStateWithLifecycle()
    val activeBrandProducts by viewModel.activeBrandProducts.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(AppTab.HOME) }
    var utilityReturnTab by remember { mutableStateOf<AppTab?>(null) }
    var accountRootBackAvailable by remember { mutableStateOf(false) }
    var returnToCartAfterLogin by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var openPromotionsOnStart by remember { mutableStateOf(false) }

    fun openUtilityTab(destination: AppTab) {
        if (tab != AppTab.CART && tab != AppTab.ACCOUNT) {
            utilityReturnTab = tab
        }
        tab = destination
    }
    var homeShowAll by remember { mutableStateOf(false) }

    LaunchedEffect(pushType, pushOrderId) {
        when (pushType) {
            PushNotificationType.NEW_PRODUCTS -> {
                viewModel.refreshHome()
                tab = AppTab.HOME
                homeShowAll = true
            }
            PushNotificationType.ORDER_STATUS -> { tab = AppTab.ACCOUNT }
            PushNotificationType.BIRTHDAY_COUPON -> {
                openPromotionsOnStart = true
                tab = AppTab.ACCOUNT
            }
        }
        if (pushType != null) { pushTypeState.value = null; pushOrderIdState.value = null }
    }
    val homeListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val saveableStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var checkoutOpen by remember { mutableStateOf(false) }
    var paymentBrowserOpened by remember { mutableStateOf(false) }
    val categoryPath = remember { mutableStateListOf<Int>() }
    var selectedBrand by remember { mutableStateOf<BrandTerm?>(null) }
    var brandOrigin by remember { mutableStateOf(BrandOrigin.HOME) }
    var showAllBrands by remember { mutableStateOf(false) }
    var outletSeasonFilter by remember { mutableStateOf<HomeOutletSeason?>(null) }

    LaunchedEffect(categoryCache, tab, categoryPath.toList()) {
        categoryCache.updates.collect { update ->
            val isCatalogTab = tab == AppTab.CATEGORIES || tab == AppTab.OUTLET
            if (isCatalogTab && categoryPath.lastOrNull() == update.categoryId) {
                viewModel.applyCategoryCacheUpdate(update)
            }
        }
    }

    var startupMinimumDurationReached by remember(coldStartBranding) { mutableStateOf(!coldStartBranding) }
    LaunchedEffect(coldStartBranding) {
        if (coldStartBranding) {
            delay(2_000)
            startupMinimumDurationReached = true
        }
    }
    val startupReady = !initialLoading || error != null
    val showStartupBranding = shouldShowStartupBranding(coldStartBranding, startupMinimumDurationReached, startupReady)

    // Native Android Back reuses the exact checkout visual-back callback. Other screens already own their BackHandler; Home/Cart fall through to Android default.
    BackHandler(enabled = checkoutOpen && !showStartupBranding) {
        checkoutOpen = false
        viewModel.abandonCheckout()
    }

    BackHandler(
        enabled = !checkoutOpen && selectedProduct == null &&
            ((tab == AppTab.CART) || (tab == AppTab.ACCOUNT && accountRootBackAvailable))
    ) {
        tab = utilityReturnTab ?: AppTab.HOME
        utilityReturnTab = null
    }

    LaunchedEffect(showStartupBranding) {
        if (showStartupBranding) return@LaunchedEffect
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect

        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val preferences = context.getSharedPreferences("criosrango", Context.MODE_PRIVATE)
        val alreadyRequested = preferences.getBoolean(NOTIFICATION_PERMISSION_REQUESTED_KEY, false)

        if (shouldRequestNotificationPermission(Build.VERSION.SDK_INT, alreadyRequested, granted)) {
            preferences.edit()
                .putBoolean(NOTIFICATION_PERMISSION_REQUESTED_KEY, true)
                .apply()
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF183B35), secondary = Color(0xFFD18162))) {
        Crossfade(
            targetState = showStartupBranding,
            animationSpec = tween(durationMillis = 250),
            label = "startup-branding"
        ) { brandingVisible ->
            if (brandingVisible) {
                StartupBranding()
            } else Surface(Modifier.fillMaxSize(), color = Color(0xFFFCFAF7)) {
            if (checkoutOpen) {
                RedesignedCheckoutScreen(
                    remoteCart,
                    checkout,
                    checkoutLoading,
                    checkoutError,
                    checkoutPhase,
                    { checkoutOpen = false; viewModel.abandonCheckout() },
                    remoteCart.itemsCount,
                    { checkoutOpen = false; viewModel.abandonCheckout(); tab = AppTab.SEARCH },
                    { checkoutOpen = false; viewModel.abandonCheckout(); tab = AppTab.CART },
                    viewModel::loadCheckout,
                    viewModel::selectShippingRate,
                    viewModel::createOrder,
                    viewModel.deliveryAddressStore,
                    loyaltyWallet
                )
            } else if (selectedProduct != null) {
                ProductDetail(
                    product = selectedProduct!!,
                    variation = selectedVariation,
                    cartItems = cartItems,
                    loadVariation = viewModel::loadVariation,
                    onBack = viewModel::closeProduct,
                    onCart = { openUtilityTab(AppTab.CART); viewModel.closeProduct() },
                    onSearch = { selectedBrand = null; showAllBrands = false; tab = AppTab.SEARCH; viewModel.closeProduct() },
                    onAdd = viewModel::addToCart
                )
            } else Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = { StoreTopBar(remoteCart.itemsCount, { selectedBrand = null; showAllBrands = false; tab = AppTab.SEARCH }, { openUtilityTab(AppTab.CART) }) },
                bottomBar = {
                    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).navigationBarsPadding()) {
                        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf(AppTab.HOME, AppTab.CATEGORIES, AppTab.OUTLET, AppTab.CART, AppTab.ACCOUNT).forEach { item ->
                                val selected = tab == item
                                val itemColor = if (selected) Color(0xFF28252A) else Color(0xFF777277)
                                Column(Modifier.weight(1f).fillMaxHeight().clickable {
                                    selectedBrand = null; showAllBrands = false
                                    if (item == AppTab.CATEGORIES) categoryPath.clear()
                                    if (item == AppTab.OUTLET) { outletSeasonFilter = null; categoryPath.clear(); categoryPath += 445 }
                                    if (item == AppTab.CART || item == AppTab.ACCOUNT) openUtilityTab(item) else tab = item
                                }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Icon(imageVector = if (selected) when (item) { AppTab.HOME -> Icons.Default.Home; AppTab.CATEGORIES -> Icons.Default.Category; AppTab.OUTLET -> Icons.Default.LocalOffer; AppTab.SEARCH -> Icons.Default.Search; AppTab.CART -> Icons.Default.ShoppingBag; AppTab.ACCOUNT -> Icons.Default.Person } else when (item) { AppTab.HOME -> Icons.Outlined.Home; AppTab.CATEGORIES -> Icons.Outlined.Category; AppTab.OUTLET -> Icons.Outlined.LocalOffer; AppTab.SEARCH -> Icons.Outlined.Search; AppTab.CART -> Icons.Outlined.ShoppingBag; AppTab.ACCOUNT -> Icons.Outlined.Person }, contentDescription = item.label, modifier = Modifier.size(24.dp), tint = if (selected) Color.Black else Color(0xFF777277))
                                    Spacer(Modifier.height(2.dp))
                                    Text(text = item.label, style = MaterialTheme.typography.labelSmall, color = if (selected) Color.Black else Color(0xFF777277), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                when {
                    error != null && tab != AppTab.ACCOUNT && tab != AppTab.CART && !catalogHasUsableContent(tab, products, homeProducts, activeCategoryProducts) -> StoreErrorState(error!!, padding, viewModel::retryLastOperation)
                    showAllBrands -> AllBrandsScreen(brands = normalizedBrands, padding = padding, onBrand = { brand -> brandOrigin = BrandOrigin.ALL_BRANDS; showAllBrands = false; selectedBrand = brand; viewModel.loadBrand(brand) }, onBack = { showAllBrands = false; selectedBrand = null; tab = AppTab.HOME })
                    selectedBrand != null -> saveableStateHolder.SaveableStateProvider("BRAND:${selectedBrand!!.slug.ifBlank { selectedBrand!!.name.lowercase().trim() }}") { BrandProductsScreen(brand = selectedBrand!!, products = activeBrandProducts, loading = loading, padding = padding, onBack = { selectedBrand = null; when (brandOrigin) { BrandOrigin.HOME -> { showAllBrands = false; tab = AppTab.HOME }; BrandOrigin.ALL_BRANDS -> { showAllBrands = true; tab = AppTab.HOME } } }, onProduct = viewModel::openProduct) }
                    loading && products.isEmpty() && tab != AppTab.SEARCH && tab != AppTab.CATEGORIES && tab != AppTab.OUTLET -> LoadingState(padding)
                    tab == AppTab.HOME -> saveableStateHolder.SaveableStateProvider("HOME") { HomeScreen(products = homeProducts, allProducts = products, roots = categories, brands = normalizedBrands, padding = padding, onProduct = viewModel::openProduct, onAllCategories = { categoryPath.clear(); tab = AppTab.CATEGORIES }, onCategory = { category -> categoryPath.clear(); categoryPath += category.id; tab = AppTab.CATEGORIES }, onOutlet = { category -> outletSeasonFilter = null; categoryPath.clear(); categoryPath += category.id; tab = AppTab.OUTLET }, onBrand = { brand -> brandOrigin = BrandOrigin.HOME; selectedBrand = brand; showAllBrands = false; viewModel.loadBrand(brand) }, onAllBrands = { brandOrigin = BrandOrigin.HOME; selectedBrand = null; showAllBrands = true }, onOutletWinter = { outletSeasonFilter = HomeOutletSeason.WINTER; categoryPath.clear(); categoryPath += 445; tab = AppTab.OUTLET }, onOutletSummer = { outletSeasonFilter = HomeOutletSeason.SUMMER; categoryPath.clear(); categoryPath += 445; tab = AppTab.OUTLET }, onOutletAll = { outletSeasonFilter = null; categoryPath.clear(); categoryPath += 445; tab = AppTab.OUTLET }, homeListState = homeListState, showAll = homeShowAll, onShowAllChange = { homeShowAll = it }, isRefreshing = loading, onRefresh = { viewModel.refreshHome() }, onEnterNovedades = { viewModel.refreshHome(); homeShowAll = true }) }
                    tab == AppTab.CATEGORIES -> saveableStateHolder.SaveableStateProvider("CATEGORY:${categoryPath.joinToString("/")}") { CategoriesScreen(categories = categories, products = activeCategoryProducts, path = categoryPath, padding = padding, loading = loading, categoryPagingState = categoryPagingState, loadNextCategoryPage = viewModel::loadNextCategoryPage, loadCategory = viewModel::loadCategory, loadCategoryTree = viewModel::loadCategoryTree, onProduct = viewModel::openProduct, availabilityStore = outletAvailabilityStore, onRootBack = { categoryPath.clear(); tab = AppTab.HOME }, onOpen = { category: ProductCategory -> categoryPath += category.id }, outletSeasonFilter = null) }
                    tab == AppTab.OUTLET -> saveableStateHolder.SaveableStateProvider("OUTLET:${outletSeasonFilter?.name ?: "ALL"}:${categoryPath.joinToString("/")}") { CategoriesScreen(categories = categories, products = activeCategoryProducts, path = categoryPath, padding = padding, loading = loading, categoryPagingState = categoryPagingState, loadNextCategoryPage = viewModel::loadNextCategoryPage, loadCategory = viewModel::loadCategory, loadCategoryTree = viewModel::loadCategoryTree, onProduct = viewModel::openProduct, availabilityStore = outletAvailabilityStore, onRootBack = { categoryPath.clear(); outletSeasonFilter = null; tab = AppTab.HOME }, onOpen = { category: ProductCategory -> categoryPath += category.id; tab = AppTab.OUTLET }, outletSeasonFilter = outletSeasonFilter, onSingleLevelBack = if (outletSeasonFilter != null) { { outletSeasonFilter = null } } else { { categoryPath.clear(); tab = AppTab.HOME } }) }
                    tab == AppTab.SEARCH -> saveableStateHolder.SaveableStateProvider("SEARCH") { SearchScreen(products = products, allProducts = homeProducts, categories = categories, brands = normalizedBrands, padding = padding, search = viewModel::search, onProduct = viewModel::openProduct, onCategory = { category -> categoryPath.clear(); categoryPath += category.id; tab = AppTab.CATEGORIES }, loading = loading) }
                    tab == AppTab.ACCOUNT -> AccountLoginScreen(
                        padding = padding,
                        vm = accountViewModel,
                        openLoginOnStart = returnToCartAfterLogin,
                        initialOrderId = pushOrderId,
                        openPromotionsOnStart = openPromotionsOnStart,
                        onPromotionsOpened = { openPromotionsOnStart = false },
                        onRootBackAvailable = { accountRootBackAvailable = it },
                        loyaltyViewModel = loyaltyViewModel,
                        applyWalletCoupon = viewModel.cartStore::applyCoupon,
                        cartCouponCodes = remoteCart.coupons.map { it.code }.toSet(),
                        onAuthenticated = {
                            if (returnToCartAfterLogin) {
                                returnToCartAfterLogin = false
                                tab = AppTab.CART
                            }
                        },
                        onBackFromLogin = if (returnToCartAfterLogin) {
                            {
                                returnToCartAfterLogin = false
                                tab = AppTab.CART
                            }
                        } else null
                    )
                    else -> saveableStateHolder.SaveableStateProvider("CART") { CartScreen(
                        cart = remoteCart,
                        state = cartState,
                        error = cartError,
                        padding = padding,
                        updateQuantity = viewModel::updateCartQuantity,
                        canIncrease = viewModel::canIncreaseCart,
                        increment = viewModel::cartIncrement,
                        removeLine = viewModel::removeCartLine,
                        clearCart = viewModel::clearCart,
                        openLine = viewModel::openCartLine,
                        retry = viewModel::refreshCart,
                        onCheckout = { checkoutOpen = true },
                        couponLoading = couponLoading,
                        couponError = couponError,
                        applyCoupon = viewModel::applyCoupon,
                        removeCoupon = viewModel::removeCoupon,
                        loyaltyViewModel = loyaltyViewModel,
                        applyWalletCoupon = viewModel.cartStore::applyCoupon,
                        accountUserId = accountUser?.id,
                        onLogin = {
                            returnToCartAfterLogin = true
                            openUtilityTab(AppTab.ACCOUNT)
                        },
                    ) }
                }
            }
        }
    }


    if (checkoutLoading && paymentBrowserOpened && paymentRedirect != null && cardPaymentResult == null && paymentReturnUri == null) AlertDialog(onDismissRequest = { }, title = { Text("Confirmando tu pedido") }, text = { Text("Estamos comprobando que el pago se ha recibido correctamente. Esto puede tardar unos segundos.") }, confirmButton = { })
    var cardPaymentDialogDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(cardPaymentResult?.orderId, cardPaymentResult?.paid) { cardPaymentDialogDismissed = false }
    cardPaymentResult?.let { result -> if (!cardPaymentDialogDismissed) {
        val title = if (result.paid == true) "Pedido recibido" else "Pago no completado"
        val message = if (result.paid == true) "Tu pedido #${result.orderId} se ha pagado correctamente." else "El pago no se ha completado."
        AlertDialog(
            onDismissRequest = { cardPaymentDialogDismissed = true },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.consumeCardPaymentResult()
                    if (result.paid == true) { checkoutOpen = false; tab = AppTab.HOME }
                }) { Text(if (result.paid == true) "Seguir comprando" else "Volver al carrito") }
            }
        )
    } }
    bizumOrderId?.let { orderId ->
        val closeBizumConfirmation = { viewModel.consumeBizumOrder(); checkoutOpen = false; tab = AppTab.HOME; viewModel.refreshCart() }
        androidx.compose.material3.AlertDialog(onDismissRequest = closeBizumConfirmation, title = { androidx.compose.material3.Text("Pedido recibido") }, text = { androidx.compose.material3.Text("Tu pedido #$orderId ha sido recibido.\n\nRealiza el pago por Bizum al 679 97 28 88 y utiliza el número de pedido como referencia de pago.\n\nTu pedido no se procesará hasta que se haya recibido el importe.") }, confirmButton = { androidx.compose.material3.TextButton(onClick = closeBizumConfirmation) { androidx.compose.material3.Text("Seguir comprando") } })
    }
    LaunchedEffect(paymentReturnUri) {
        val uri = paymentReturnUri ?: return@LaunchedEffect
        val host = uri.host.orEmpty().lowercase()
        val httpsReturn = uri.scheme == "https" && (host == "criosrango.es" || host == "www.criosrango.es") && uri.path.orEmpty().startsWith("/app-payment-return")
        val customReturn = uri.scheme == "criosrango" && host == "payment-return"
        if (httpsReturn || customReturn) {
            val result = uri.getQueryParameter("result")
            val orderId = uri.getQueryParameter("order_id")?.toIntOrNull()
            when (result) {
                "cancel" -> {
                    if (orderId != null) {
                        viewModel.handleCardPaymentCancelled(orderId)
                        checkoutOpen = false
                        tab = AppTab.CART
                    }
                }
                "ok" -> viewModel.verifyCardPaymentReturn()
            }
        }
        val isProductLink = (uri.scheme == "https" || uri.scheme == "http") && (host == "criosrango.es" || host == "www.criosrango.es") && uri.pathSegments.firstOrNull()?.equals("producto", ignoreCase = true) == true
        if (isProductLink) uri.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() }?.let(viewModel::openProductBySlug)
        paymentReturnUriState.value = null
    }
    LaunchedEffect(paymentRedirect) {
        val redirect = paymentRedirect
        if (redirect == null) paymentBrowserOpened = false else if (!paymentBrowserOpened) {
            val opened = runCatching { androidx.browser.customtabs.CustomTabsIntent.Builder().setShowTitle(false).setUrlBarHidingEnabled(true).build().launchUrl(context, Uri.parse(redirect.url)) }.isSuccess
            paymentBrowserOpened = opened
            if (!opened) viewModel.verifyCardPaymentReturn()
        }
    }
    val paymentLifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(paymentLifecycleOwner, paymentRedirect, paymentBrowserOpened) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && paymentBrowserOpened && paymentRedirect != null) viewModel.verifyCardPaymentReturn() }
        paymentLifecycleOwner.lifecycle.addObserver(observer)
        onDispose { paymentLifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StoreTopBar(cartQuantity: Int, onSearch: () -> Unit, onCart: () -> Unit) = TopAppBar(
    title = {},
    navigationIcon = {
        Image(
            painter = painterResource(R.drawable.home_logo),
            contentDescription = "Críos & Rango",
            modifier = Modifier.size(34.dp),
            contentScale = ContentScale.Fit
        )
    },
    actions = {
        IconButton(onSearch) { Icon(Icons.Outlined.Search, "Buscar") }
        Box(
            modifier = Modifier
                .width(56.dp)
                .fillMaxHeight()
        ) {
            BadgedBox(
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.CenterStart),
                badge = { if (cartQuantity > 0) Badge { Text(cartQuantity.toString(), maxLines = 1) } }
            ) {
                IconButton(onCart) { Icon(Icons.Outlined.ShoppingBag, "Carrito") }
            }
        }
    },
    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)
)

@Composable
private fun LoadingState(padding: PaddingValues) {
    ProductSkeletonGrid(
        Modifier
            .fillMaxSize()
            .padding(padding)
    )
}
private fun catalogHasUsableContent(tab: AppTab, products: List<StoreProduct>, homeProducts: List<StoreProduct>, activeCategoryProducts: List<StoreProduct>): Boolean = when (tab) {
    AppTab.HOME -> products.isNotEmpty() || homeProducts.isNotEmpty()
    AppTab.CATEGORIES, AppTab.OUTLET -> activeCategoryProducts.isNotEmpty()
    AppTab.SEARCH -> products.isNotEmpty()
    else -> false
}

private fun parseMinorPrice(value: String): Double = value.toDoubleOrNull()?.div(100.0) ?: parsePrice(value)
private fun parsePrice(value: String): Double = value.replace(".", "").replace(",", ".").replace(" €", "").toDoubleOrNull() ?: 0.0
private fun Double.formatPrice(): String = "%,.2f €".format(java.util.Locale.forLanguageTag("es-ES"), this)
