package es.criosrango.app

import es.criosrango.shared.account.AccountCustomerAddress
import es.criosrango.shared.account.AccountOrderSummary
import es.criosrango.shared.AccountDeletion
import es.criosrango.shared.account.AccountUser
import es.criosrango.shared.BirthDatePickerField
import es.criosrango.shared.formatBirthDateForDisplay

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import es.criosrango.shared.promotions.Promotion
import es.criosrango.shared.promotions.PromotionRepository
import es.criosrango.shared.StoreAccountHelpScreen
import es.criosrango.shared.StoreAccountRootScreen
import es.criosrango.shared.StorePageHeader
import es.criosrango.shared.StoreOrdersOwner
import es.criosrango.shared.StoreOrdersScreen
import es.criosrango.shared.StoreOrderDetailScreen
import es.criosrango.shared.StoreOrderSelectionResolution
import es.criosrango.shared.StoreLoginScreen
import es.criosrango.shared.StoreRegisterOwner
import es.criosrango.shared.StoreRegisterScreen
import es.criosrango.shared.StoreForgotPasswordOwner
import es.criosrango.shared.StoreForgotPasswordScreen
import es.criosrango.shared.StoreWalletOwner
import es.criosrango.shared.StoreWalletScreen
import es.criosrango.shared.CouponApplicationResult
import es.criosrango.shared.loyalty.LoyaltyRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountLoginScreen(
    padding: PaddingValues,
    vm: AccountViewModel = viewModel(),
    openLoginOnStart: Boolean = false,
    initialOrderId: Int? = null,
    onPushOrderConsumed: (Int) -> Unit = {},
    openPromotionsOnStart: Boolean = false,
    onAuthenticated: (() -> Unit)? = null,
    onPromotionsOpened: () -> Unit = {},
    onBackFromLogin: (() -> Unit)? = null,
    onRootBack: () -> Unit = {},
    onRootBackAvailable: (Boolean) -> Unit = {},
    onBeforeLogout: suspend () -> Unit = {},
    applyWalletCoupon: suspend (String) -> Boolean = { false },
    cartCouponCodes: Set<String> = emptySet()
) {
    val registerOwner = remember(vm.sharedRepository) { StoreRegisterOwner(vm.sharedRepository) }
    val forgotPasswordOwner = remember(vm.sharedRepository) { StoreForgotPasswordOwner(vm.sharedRepository) }
    val authState by vm.authState.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    val orders by vm.orders.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val ordersRefreshing by vm.ordersRefreshing.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val accountError by vm.accountError.collectAsStateWithLifecycle()
    val notificationContext = LocalContext.current
    val notice by vm.notice.collectAsStateWithLifecycle()
    val address by vm.address.collectAsStateWithLifecycle()
    val loyaltyRepository = remember { LoyaltyRepository(AccountSessionStore.shared(notificationContext.applicationContext)) }
    val promotionRepository = remember {
        PromotionRepository(
            bearerTokenProvider = { AccountSessionStore.shared(notificationContext.applicationContext).load() }
        )
    }
    val walletOwner = remember(loyaltyRepository, promotionRepository) {
        StoreWalletOwner(loyaltyRepository, promotionRepository)
    }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showRegister by remember { mutableStateOf(false) }
    var showForgot by remember { mutableStateOf(false) }
    var showLogin by remember { mutableStateOf(openLoginOnStart) }
    var selectedInfoPage by remember { mutableStateOf<AccountInfoPage?>(null) }
    val currentUser = user
    LaunchedEffect(currentUser?.id) { walletOwner.onSessionChanged(currentUser?.id?.toString()) }
    var accountSection by remember {
        mutableStateOf(if (openPromotionsOnStart) AccountSection.WALLET else AccountSection.HOME)
    }
    LaunchedEffect(authState) {
        if (shouldResetAccountUiForAuthState(authState)) {
            showLogin = true
            showRegister = false
            showForgot = false
            login = ""
            password = ""
            selectedInfoPage = null
            accountSection = AccountSection.HOME
            vm.clearAccountMessages()
        }
    }
    var selectedOrderId by remember { mutableStateOf<Int?>(initialOrderId) }
    var selectedOrderFromShared by remember { mutableStateOf<AccountOrderSummary?>(null) }
    // onNewIntent can deliver another order while AccountLoginScreen remains composed.
    // Treat each changed incoming id as a selection event, not an initial-only value.
    LaunchedEffect(initialOrderId) {
        if (initialOrderId != null && initialOrderId > 0) {
            logAndroidPushRoute("ACCOUNT_RECEIVED=$initialOrderId")
            selectedInfoPage = null
            accountSection = AccountSection.ORDERS
            showLogin = currentUser == null
            selectedOrderFromShared = null
            selectedOrderId = initialOrderId
        }
    }
    val clearOrderSelection = {
        val cleared = AccountOrderSelectionState(selectedOrderId, selectedOrderFromShared).clear()
        selectedOrderId = cleared.orderId
        selectedOrderFromShared = cleared.sharedOrder
    }
    var returnPickerOrder by remember { mutableStateOf<AccountOrderSummary?>(null) }
    val sharedOrdersOwner = vm.sharedOrdersOwner
    val sharedOrdersState by sharedOrdersOwner.state.collectAsStateWithLifecycle()
    LaunchedEffect(currentUser?.id) { sharedOrdersOwner.onSessionChanged(currentUser?.id); selectedOrderFromShared = null }
    LaunchedEffect(currentUser?.id, selectedOrderId) {
        val id = selectedOrderId ?: return@LaunchedEffect
        if (currentUser == null || selectedOrderFromShared != null) return@LaunchedEffect
        when (val resolution = sharedOrdersOwner.resolveOrderById(id)) {
            is StoreOrderSelectionResolution.Resolved -> if (selectedOrderId == id && resolution.order.id == id) selectedOrderFromShared = resolution.order
            StoreOrderSelectionResolution.NotFound -> if (selectedOrderId == id && initialOrderId != id) selectedOrderId = null
            StoreOrderSelectionResolution.Pending -> Unit
            is StoreOrderSelectionResolution.TransientError -> Unit
        }
    }
    val selectedOrder = AccountOrderSelectionState(selectedOrderId, selectedOrderFromShared).resolveFrom(sharedOrdersState.orders)
    LaunchedEffect(initialOrderId, currentUser?.id, selectedOrder?.id) {
        val targetOrderId = initialOrderId ?: return@LaunchedEffect
        if (shouldConsumePendingOrder(targetOrderId, selectedOrder?.id, authenticated = currentUser != null)) {
            logAndroidPushRoute("ORDER_SELECTED=$targetOrderId")
            onPushOrderConsumed(targetOrderId)
        }
    }
    LaunchedEffect(currentUser?.id, selectedOrderId, sharedOrdersState.loading, sharedOrdersState.refreshing, sharedOrdersState.error, sharedOrdersState.orders) {
        val id = selectedOrderId ?: return@LaunchedEffect
        if (currentUser == null || selectedOrderFromShared != null) return@LaunchedEffect
        when (val resolution = sharedOrdersOwner.resolveSelection(id)) {
            is StoreOrderSelectionResolution.Resolved -> if (selectedOrderId == id && resolution.order.id == id) selectedOrderFromShared = resolution.order
            StoreOrderSelectionResolution.NotFound -> if (selectedOrderId == id && initialOrderId != id) selectedOrderId = null
            StoreOrderSelectionResolution.Pending, is StoreOrderSelectionResolution.TransientError -> Unit
        }
    }
    val accountRootBackAvailable = selectedInfoPage == null && selectedOrder == null && !showLogin && accountSection == AccountSection.HOME
    SideEffect { onRootBackAvailable(accountRootBackAvailable) }
    val leaveLogin: () -> Unit = {
        when (accountAuthBackDestination(AccountAuthDestination.LOGIN, returnToCartAfterLogin = onBackFromLogin != null)) {
            AccountAuthDestination.CART -> onBackFromLogin?.invoke()
            AccountAuthDestination.ACCOUNT -> {
                showLogin = false
                showRegister = false
                showForgot = false
                vm.clearAccountMessages()
            }
            else -> Unit
        }
    }
    val dismissRegister: () -> Unit = {
        if (!loading && accountAuthBackDestination(AccountAuthDestination.REGISTER) == AccountAuthDestination.LOGIN) {
            showRegister = false
            vm.clearAccountMessages()
        }
    }
    val dismissForgot: () -> Unit = {
        if (!loading && accountAuthBackDestination(AccountAuthDestination.FORGOT_PASSWORD) == AccountAuthDestination.LOGIN) {
            showForgot = false
            vm.clearAccountMessages()
        }
    }

    LaunchedEffect(openPromotionsOnStart, currentUser?.id) {
        if (openPromotionsOnStart && currentUser != null) {
            accountSection = AccountSection.WALLET
            onPromotionsOpened()
        }
    }

    LaunchedEffect(currentUser?.id) {
        if (currentUser != null) {
            showLogin = false
            if (openPromotionsOnStart) accountSection = AccountSection.WALLET
            PushNotificationController.initialize(notificationContext)
            onAuthenticated?.invoke()
        }
    }

    if (currentUser == null && accountError?.type == StoreErrorType.SESSION_EXPIRED) {
        StoreErrorState(accountError!!, padding, onLogin = { vm.clearAccountMessages(); showLogin = true })
        return
    }

    if (authState == AccountAuthState.CHECKING && accountError != null) {
        StoreErrorState(
            accountError!!,
            padding,
            vm::retrySession,
        )
        return
    }

    if (authState == AccountAuthState.CHECKING) {
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.5.dp
            )
        }
        return
    }

    BackHandler(enabled = selectedInfoPage != null && selectedOrder == null) {
        selectedInfoPage = null
        accountSection = AccountSection.HOME
    }

    if (selectedInfoPage != null && selectedOrder == null) {
        Column(Modifier.fillMaxSize().padding(padding)) {
            AccountInformationPageAdapter(selectedInfoPage!!, onBack = { selectedInfoPage = null; accountSection = AccountSection.HOME })
        }
        return
    }

    // System/gesture Back uses the same state transition as the detail header.
    BackHandler(enabled = selectedOrder != null) { clearOrderSelection() }
    BackHandler(enabled = selectedOrder == null && !showLogin && accountSection != AccountSection.HOME) {
        accountSection = when (accountSection) {
            AccountSection.DATA, AccountSection.ADDRESSES -> AccountSection.PROFILE
            AccountSection.PROFILE, AccountSection.ORDERS, AccountSection.WALLET, AccountSection.HELP -> AccountSection.HOME
            AccountSection.HOME -> AccountSection.HOME
        }
    }
    BackHandler(enabled = currentUser == null && showLogin && selectedOrder == null && !showRegister && !showForgot) {
        leaveLogin()
    }

    if (currentUser != null && selectedOrder != null) {
        Box(Modifier.fillMaxSize().padding(padding)) {
            StoreOrderDetailScreen(
                order = selectedOrder,
                onBack = { clearOrderSelection() },
                onRequestReturn = { order ->
                    if (order.items.size == 1) {
                        requestReturnViaWhatsApp(notificationContext, order, order.items.first())
                    } else if (order.items.isNotEmpty()) returnPickerOrder = order
                }
            )
        }
        returnPickerOrder?.let { order -> AccountReturnProductPicker(order) { returnPickerOrder = null } }
        return
    }
    if (currentUser != null) {
        val fullName = listOf(currentUser.firstName, currentUser.lastName).filter { it.isNotBlank() }.joinToString(" ")
        if (accountSection == AccountSection.ORDERS) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                StoreOrdersScreen(
                    owner = sharedOrdersOwner,
                    sessionState = vm.sharedAuthSession,
                    onBack = { accountSection = AccountSection.HOME },
                    onOpenOrder = { val selected = AccountOrderSelectionState().select(it); selectedOrderFromShared = selected.sharedOrder; selectedOrderId = selected.orderId },
                    onSessionExpired = { vm.retrySession() }
                )
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding)
                    .then(if (accountSection in setOf(AccountSection.HOME, AccountSection.HELP, AccountSection.PROFILE, AccountSection.DATA, AccountSection.ADDRESSES)) Modifier else Modifier.verticalScroll(rememberScrollState()))
                    .then(if (accountSection in setOf(AccountSection.HOME, AccountSection.HELP, AccountSection.PROFILE, AccountSection.DATA, AccountSection.ADDRESSES, AccountSection.WALLET)) Modifier else Modifier.padding(horizontal = 24.dp))
            ) {
                if (accountSection != AccountSection.HELP) Spacer(Modifier.height(if (accountSection in setOf(AccountSection.HOME, AccountSection.PROFILE, AccountSection.DATA, AccountSection.ADDRESSES, AccountSection.WALLET)) 0.dp else 20.dp))
                when (accountSection) {
                    AccountSection.HOME -> StoreAccountRootScreen(
                        contentHorizontalPadding = 24.dp,
                        fullName = fullName,
                        email = currentUser.email,
                        appVersion = BuildConfig.VERSION_NAME,
                        onBack = onRootBack,
                        onOrders = { accountSection = AccountSection.ORDERS },
                        onWallet = { accountSection = AccountSection.WALLET },
                        onProfile = { accountSection = AccountSection.PROFILE },
                        onLogin = { showLogin = true },
                        onHelp = { accountSection = AccountSection.HELP },
                        onInfoPage = { selectedInfoPage = AccountInfoPage.valueOf(it.name) }
                    )
                    AccountSection.PROFILE -> es.criosrango.shared.StoreProfileMenuScreen(
                        onBack = { accountSection = AccountSection.HOME },
                        onPersonalData = { accountSection = AccountSection.DATA },
                        onAddress = { accountSection = AccountSection.ADDRESSES },
                        onPassword = { vm.clearAccountMessages(); showForgot = true },
                        notificationPreferences = { PushNotificationPreferences() },
                        onDeleteAccount = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AccountDeletion.URL))
                            runCatching { notificationContext.startActivity(intent) }.onFailure {
                                Toast.makeText(notificationContext, "No se ha podido abrir la página de eliminación.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onLogout = { vm.logout(onBeforeLogout) {
                            showLogin = true
                            showRegister = false
                            showForgot = false
                            selectedOrderId = null
                            selectedInfoPage = null
                            accountSection = AccountSection.HOME
                            vm.clearAccountMessages()
                        } })
                    AccountSection.DATA -> es.criosrango.shared.StoreProfileScreen(
                        repository = vm.sharedRepository,
                        sessionState = vm.sharedAuthSession,
                        user = currentUser,
                        onUserChanged = vm::publishProfileUser,
                        onSessionExpired = { vm.retrySession() },
                        onBack = { accountSection = AccountSection.PROFILE },
                        bodyHorizontalPadding = 24.dp
                    )
                    AccountSection.ADDRESSES -> es.criosrango.shared.StoreAddressScreen(
                        repository = vm.sharedRepository,
                        addressOwner = vm.sharedAddressOwner,
                        onSessionExpired = { vm.retrySession() },
                        onBack = { accountSection = AccountSection.PROFILE },
                        bodyHorizontalPadding = 24.dp
                    )
                    AccountSection.HELP -> AccountHelpContent { accountSection = AccountSection.HOME }
                    AccountSection.WALLET -> StoreWalletScreen(
                        owner = walletOwner,
                        authenticated = currentUser != null,
                        sessionId = currentUser?.id?.toString(),
                        cartCouponCodes = cartCouponCodes,
                        onApplyCoupon = { code ->
                            if (applyWalletCoupon(code)) CouponApplicationResult.Success
                            else CouponApplicationResult.failure("No se ha podido aplicar el cupón al carrito.")
                        },
                        onBack = { accountSection = AccountSection.HOME }
                    )
                    AccountSection.ORDERS -> Unit
                }
                if (accountSection != AccountSection.HELP) Spacer(Modifier.height(if (accountSection == AccountSection.PROFILE) 12.dp else 24.dp))
            }
        }
        if (showForgot) StoreForgotPasswordScreen(owner = forgotPasswordOwner, initialEmail = login, onDismiss = { showForgot = false; vm.clearAccountMessages() })
    } else if (!showLogin) {
        Column(
            Modifier.fillMaxSize().padding(padding)
                    .then(if (accountSection in setOf(AccountSection.HOME, AccountSection.HELP, AccountSection.PROFILE, AccountSection.DATA, AccountSection.ADDRESSES)) Modifier else Modifier.verticalScroll(rememberScrollState()))
                .then(if (accountSection == AccountSection.HELP) Modifier else Modifier.padding(horizontal = 24.dp))
        ) {
            if (accountSection != AccountSection.HELP) Spacer(Modifier.height(when (accountSection) { AccountSection.HOME -> 0.dp; AccountSection.PROFILE, AccountSection.DATA, AccountSection.ADDRESSES -> 8.dp; else -> 20.dp }))
            when (accountSection) {
                AccountSection.HELP -> AccountHelpContent { accountSection = AccountSection.HOME }
                else -> StoreAccountRootScreen(
                    fullName = null,
                    email = null,
                    appVersion = BuildConfig.VERSION_NAME,
                    onBack = onRootBack,
                    onOrders = { showLogin = true },
                    onWallet = { showLogin = true },
                    onProfile = { showLogin = true },
                    onLogin = { showLogin = true },
                    onHelp = { accountSection = AccountSection.HELP },
                    onInfoPage = { selectedInfoPage = AccountInfoPage.valueOf(it.name) }
                )
            }
            if (accountSection != AccountSection.HELP) Spacer(Modifier.height(24.dp))
        }
    } else {
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            StoreLoginScreen(
                login = login,
                password = password,
                loading = loading,
                error = error,
                onLoginChanged = { login = it },
                onPasswordChanged = { password = it },
                onSubmit = { submittedLogin, submittedPassword -> vm.login(submittedLogin, submittedPassword) },
                onForgotPassword = { vm.clearAccountMessages(); forgotPasswordOwner.reset(login); showForgot = true },
                onRegister = { vm.clearAccountMessages(); registerOwner.reset(login); showRegister = true },
                onBack = leaveLogin
            )
            if (showRegister) StoreRegisterScreen(owner = registerOwner, initialEmail = login, onDismiss = dismissRegister, onRegistered = { created -> showRegister = false; vm.completeRegistration(created) })
            if (showForgot) StoreForgotPasswordScreen(owner = forgotPasswordOwner, initialEmail = login, onDismiss = dismissForgot)
        }
    }
}

internal enum class AccountAuthDestination { ACCOUNT, LOGIN, REGISTER, FORGOT_PASSWORD, CART }

internal fun accountAuthBackDestination(
    current: AccountAuthDestination,
    returnToCartAfterLogin: Boolean = false
): AccountAuthDestination = when (current) {
    AccountAuthDestination.REGISTER, AccountAuthDestination.FORGOT_PASSWORD -> AccountAuthDestination.LOGIN
    AccountAuthDestination.LOGIN -> if (returnToCartAfterLogin) AccountAuthDestination.CART else AccountAuthDestination.ACCOUNT
    AccountAuthDestination.ACCOUNT, AccountAuthDestination.CART -> AccountAuthDestination.ACCOUNT
}

private enum class AccountSection { HOME, ORDERS, WALLET, PROFILE, DATA, ADDRESSES, HELP }

@Composable
private fun AccountCompactAccess(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .heightIn(min = 72.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(Color(0xFFE3EFEB), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFF183B35),
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Clip
        )
    }
}

private fun buildLineIcon(name: String, content: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = androidx.compose.ui.graphics.SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round,
            strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round,
            pathBuilder = content
        )
    }.build()

private val WalletLineIcon = buildLineIcon("WalletLineIcon") {
    moveTo(3.5f, 6.5f)
    lineTo(18.5f, 6.5f)
    lineTo(20.5f, 8.5f)
    lineTo(20.5f, 18f)
    lineTo(3.5f, 18f)
    close()
    moveTo(3.5f, 6.5f)
    lineTo(3.5f, 5f)
    lineTo(17f, 5f)
    moveTo(15.5f, 12.5f)
    lineTo(20.5f, 12.5f)
    moveTo(17.5f, 12.5f)
    lineTo(17.5f, 12.5f)
}

private val CalendarLineIcon = buildLineIcon("CalendarLineIcon") {
    moveTo(4f, 6f)
    lineTo(20f, 6f)
    lineTo(20f, 20f)
    lineTo(4f, 20f)
    close()
    moveTo(4f, 10f)
    lineTo(20f, 10f)
    moveTo(8f, 4f)
    lineTo(8f, 8f)
    moveTo(16f, 4f)
    lineTo(16f, 8f)
}

private val InfoLineIcon = buildLineIcon("InfoLineIcon") {
    moveTo(12f, 4f)
    lineTo(12f, 4f)
    moveTo(12f, 10f)
    lineTo(12f, 18f)
    moveTo(12f, 7f)
    lineTo(12f, 7f)
    moveTo(4f, 12f)
    curveTo(4f, 7.6f, 7.6f, 4f, 12f, 4f)
    curveTo(16.4f, 4f, 20f, 7.6f, 20f, 12f)
    curveTo(20f, 16.4f, 16.4f, 20f, 12f, 20f)
    curveTo(7.6f, 20f, 4f, 16.4f, 4f, 12f)
}

@Composable
private fun WalletVisualIcon() {
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = WalletLineIcon,
            contentDescription = null,
            tint = Color(0xFF0F5C4D),
            modifier = Modifier.size(28.dp)
        )
    }
}
private fun accountPromotionIcon(promotion: Promotion): String {
    val key = listOfNotNull(promotion.code, promotion.title, promotion.description).joinToString(" ").lowercase()
    return when {
        key.contains("blackcrios") || key.contains("black friday") -> "％"
        key.contains("bienvenida") || key.contains("welcome") -> "🏷"
        key.contains("cumple") || key.contains("birthday") -> "🎂"
        else -> "🏷"
    }
}


@Composable
private fun PromotionDateRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            imageVector = CalendarLineIcon,
            contentDescription = null,
            tint = Color(0xFF7A8884),
            modifier = Modifier.size(13.dp)
        )
        Text(text, style = MaterialTheme.typography.labelSmall, color = Color(0xFF687773))
    }
}



private fun promotionCardColor(promotion: Promotion, isBirthday: Boolean): Color {
    val key = listOfNotNull(promotion.code, promotion.title, promotion.description).joinToString(" ").lowercase()
    return when {
        isBirthday || key.contains("cumple") || key.contains("birthday") -> Color(0xFFFDECEF)
        key.contains("bienvenida") || key.contains("welcome") -> Color(0xFFEAF4FC)
        else -> Color(0xFFFFF6DF)
    }
}


private fun formatPromotionExpiry(value: String): String {
    val date = value.trim().take(10)
    val parts = date.split("-")
    return if (parts.size == 3 && parts[0].length == 4 && parts[1].length == 2 && parts[2].length == 2) {
        parts[2] + "/" + parts[1] + "/" + parts[0]
    } else value
}

@Composable
private fun AccountHelpContent(onBack: () -> Unit) {
    val context = LocalContext.current
    StoreAccountHelpScreen(
        onBack = onBack,
        onWhatsApp = {
            val message = "Hola, necesito ayuda con la app de Críos&Rango."
            val encodedMessage = java.net.URLEncoder.encode(message, "UTF-8")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/34633246788?text=$encodedMessage"))
            runCatching { context.startActivity(intent) }.onFailure {
                Toast.makeText(context, "No se ha podido abrir WhatsApp.", Toast.LENGTH_SHORT).show()
            }
        },
        onCall = {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+34969091236"))
            runCatching { context.startActivity(intent) }.onFailure {
                Toast.makeText(context, "No se ha podido abrir el teléfono.", Toast.LENGTH_SHORT).show()
            }
        },
        onEmail = {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:criosrango@criosrango.es")
                putExtra(Intent.EXTRA_SUBJECT, "Consulta desde la app Críos&Rango")
            }
            runCatching { context.startActivity(intent) }.onFailure {
                Toast.makeText(context, "No se ha podido abrir el correo.", Toast.LENGTH_SHORT).show()
            }
        }
    )
}

@Composable
private fun StoreHoursSection() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Horario de atención",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF183B35)
        )
        Spacer(Modifier.height(14.dp))
        StoreHoursRow("Lunes", "10:00–13:30 · 17:30–21:00")
        StoreHoursRow("Martes", "10:00–13:30 · 17:30–21:00")
        StoreHoursRow("Miércoles", "10:00–13:30 · 17:30–21:00")
        StoreHoursRow("Jueves", "10:00–13:30 · 17:30–21:00")
        StoreHoursRow("Viernes", "10:00–13:30 · 17:30–21:00")
        StoreHoursRow("Sábado", "10:00–13:30")
        StoreHoursRow("Domingo", "Cerrado")
    }
}

@Composable
private fun StoreHoursRow(day: String, hours: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = day,
            modifier = Modifier.width(90.dp),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = hours,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HelpContactRow(title: String, value: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(44.dp).background(Color(0xFFE3EFEB), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color(0xFF183B35), modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(3.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


@Composable
private fun AccountPersonalDataContent(vm: AccountViewModel, user: AccountUser, onBack: () -> Unit) {
    val saving by vm.savingAccountDetails.collectAsStateWithLifecycle(); val saveError by vm.accountDataError.collectAsStateWithLifecycle(); val saveNotice by vm.accountDataNotice.collectAsStateWithLifecycle(); var firstName by remember(user.id) { mutableStateOf(user.firstName) }; var lastName by remember(user.id) { mutableStateOf(user.lastName) }
    StorePageHeader(title = "Datos personales", onBack = onBack); Spacer(Modifier.height(20.dp)); OutlinedTextField(firstName, { firstName = it; vm.clearAccountDataMessages() }, label = { Text("Nombre") }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(14.dp)); OutlinedTextField(lastName, { lastName = it; vm.clearAccountDataMessages() }, label = { Text("Apellidos") }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(14.dp)); OutlinedTextField(user.email.ifBlank { "—" }, {}, label = { Text("Correo electrónico") }, readOnly = true, singleLine = true, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(14.dp)); OutlinedTextField(formatBirthDateForDisplay(user.birthDate).ifBlank { "No indicada" }, {}, label = { Text("Fecha de nacimiento") }, readOnly = true, singleLine = true, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(20.dp)); Button({ vm.updateAccountDetails(firstName.trim(), lastName.trim()) }, enabled = !saving, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF183B35))) { if (saving) Text("Guardando...") else Text("Guardar cambios") }; saveError?.let { Spacer(Modifier.height(10.dp)); Text(it, color = MaterialTheme.colorScheme.error) }; saveNotice?.let { Spacer(Modifier.height(10.dp)); Text(it, color = Color(0xFF183B35)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountAddressContent(vm: AccountViewModel, address: AccountCustomerAddress?, onBack: () -> Unit) {
    val saving by vm.savingAddress.collectAsStateWithLifecycle(); val saveError by vm.addressSaveError.collectAsStateWithLifecycle(); val saveNotice by vm.addressSaveNotice.collectAsStateWithLifecycle(); var firstName by remember(address) { mutableStateOf(address?.firstName.orEmpty()) }; var lastName by remember(address) { mutableStateOf(address?.lastName.orEmpty()) }; var address1 by remember(address) { mutableStateOf(address?.address1.orEmpty()) }; var address2 by remember(address) { mutableStateOf(address?.address2.orEmpty()) }; var postcode by remember(address) { mutableStateOf(address?.postcode.orEmpty()) }; var city by remember(address) { mutableStateOf(address?.city.orEmpty()) }; var state by remember(address) { mutableStateOf(address?.state.orEmpty()) }; var provinceExpanded by remember { mutableStateOf(false) }; val provinceName = SPANISH_PROVINCES.firstOrNull { it.code.equals(state, true) }?.name.orEmpty()
    StorePageHeader(title = "Dirección de entrega", onBack = onBack); Spacer(Modifier.height(20.dp)); AccountAddressField("Nombre", firstName, !saving) { firstName = it; vm.clearAddressSaveMessages() }; Spacer(Modifier.height(14.dp)); AccountAddressField("Apellidos", lastName, !saving) { lastName = it; vm.clearAddressSaveMessages() }; Spacer(Modifier.height(14.dp)); AccountAddressField("Dirección", address1, !saving) { address1 = it; vm.clearAddressSaveMessages() }; Spacer(Modifier.height(14.dp)); AccountAddressField("Dirección adicional / Piso, puerta, etc. (opcional)", address2, !saving) { address2 = it; vm.clearAddressSaveMessages() }; Spacer(Modifier.height(14.dp)); AccountAddressField("Código postal", postcode, !saving) { postcode = it; vm.clearAddressSaveMessages() }; Spacer(Modifier.height(14.dp)); AccountAddressField("Localidad", city, !saving) { city = it; vm.clearAddressSaveMessages() }; Spacer(Modifier.height(14.dp)); ExposedDropdownMenuBox(expanded = provinceExpanded, onExpandedChange = { if (!saving) provinceExpanded = it }) { OutlinedTextField(provinceName.ifBlank { state }, {}, label = { Text("Provincia") }, readOnly = true, enabled = !saving, singleLine = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = provinceExpanded) }, modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !saving).fillMaxWidth()); ExposedDropdownMenu(expanded = provinceExpanded, onDismissRequest = { provinceExpanded = false }) { SPANISH_PROVINCES.distinctBy { it.code }.forEach { province -> DropdownMenuItem(text = { Text(province.name) }, onClick = { state = province.code; provinceExpanded = false; vm.clearAddressSaveMessages() }) } } }; Spacer(Modifier.height(14.dp)); OutlinedTextField("España", {}, label = { Text("País") }, readOnly = true, enabled = !saving, singleLine = true, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(20.dp)); Button({ vm.saveAddress(AccountCustomerAddress(firstName = firstName, lastName = lastName, email = address?.email.orEmpty(), phone = address?.phone.orEmpty(), address1 = address1, address2 = address2, postcode = postcode, city = city, state = state, country = "ES")) }, enabled = !saving, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF183B35))) { if (saving) Text("Guardando...") else Text("Guardar dirección") }; saveError?.let { Spacer(Modifier.height(10.dp)); Text(it, color = MaterialTheme.colorScheme.error) }; saveNotice?.let { Spacer(Modifier.height(10.dp)); Text(it) }
}

@Composable
private fun AccountAddressField(label: String, value: String, enabled: Boolean, onValueChange: (String) -> Unit) { OutlinedTextField(value, onValueChange, label = { Text(label) }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth()) }


private fun formatWalletRemaining(value: Double): String {
    val cents = kotlin.math.round(value * 100.0).toLong()
    val whole = cents / 100L
    val fraction = kotlin.math.abs(cents % 100L)
    return whole.toString() + "," + fraction.toString().padStart(2, '0')
}



internal fun shouldResetAccountUiForAuthState(authState: AccountAuthState): Boolean =
    authState == AccountAuthState.UNAUTHENTICATED

internal fun incomingPushOrderSelection(currentOrderId: Int?, incomingOrderId: Int?): Int? =
    incomingOrderId?.takeIf { it > 0 } ?: currentOrderId

internal data class AccountOrderSelectionState(
    val orderId: Int? = null,
    val sharedOrder: AccountOrderSummary? = null
) {
    fun select(order: AccountOrderSummary) = copy(orderId = order.id, sharedOrder = order)

    fun clear() = AccountOrderSelectionState()

    fun resolveFrom(orders: List<AccountOrderSummary>): AccountOrderSummary? =
        sharedOrder?.takeIf { it.id == orderId } ?: orders.firstOrNull { it.id == orderId }
}
