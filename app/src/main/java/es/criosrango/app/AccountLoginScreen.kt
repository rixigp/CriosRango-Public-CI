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
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material.icons.outlined.Phone
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
import es.criosrango.shared.promotions.isVisibleToAnonymous
import es.criosrango.shared.loyalty.LoyaltyReward
import es.criosrango.shared.loyalty.redeemableOptions
import es.criosrango.shared.loyalty.subtractMoneyAmounts

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountLoginScreen(
    padding: PaddingValues,
    vm: AccountViewModel = viewModel(),
    openLoginOnStart: Boolean = false,
    initialOrderId: Int? = null,
    openPromotionsOnStart: Boolean = false,
    onAuthenticated: (() -> Unit)? = null,
    onPromotionsOpened: () -> Unit = {},
    onBackFromLogin: (() -> Unit)? = null,
    onRootBackAvailable: (Boolean) -> Unit = {},
    onBeforeLogout: suspend () -> Unit = {},
    loyaltyViewModel: LoyaltyViewModel = viewModel(),
    applyWalletCoupon: suspend (String) -> Boolean = { false },
    cartCouponCodes: Set<String> = emptySet()
) {
    val authState by vm.authState.collectAsStateWithLifecycle()
    val user by vm.user.collectAsStateWithLifecycle()
    val orders by vm.orders.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val forgotPasswordLoading by vm.forgotPasswordLoading.collectAsStateWithLifecycle()
    val ordersRefreshing by vm.ordersRefreshing.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val accountError by vm.accountError.collectAsStateWithLifecycle()
    val notificationContext = LocalContext.current
    val notice by vm.notice.collectAsStateWithLifecycle()
    val address by vm.address.collectAsStateWithLifecycle()
    val loyaltyWallet by loyaltyViewModel.wallet.collectAsStateWithLifecycle()
    val loyaltyLoading by loyaltyViewModel.loading.collectAsStateWithLifecycle()
    val loyaltyError by loyaltyViewModel.error.collectAsStateWithLifecycle()
    val promotionRepository = remember {
        PromotionRepository(
            bearerTokenProvider = { AccountSessionStore.shared(notificationContext.applicationContext).load() }
        )
    }
    var promotions by remember { mutableStateOf(emptyList<Promotion>()) }
    var promotionsLoading by remember { mutableStateOf(false) }
    var promotionsError by remember { mutableStateOf<String?>(null) }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showRegister by remember { mutableStateOf(false) }
    var showForgot by remember { mutableStateOf(false) }
    var showLogin by remember { mutableStateOf(openLoginOnStart) }
    var selectedInfoPage by remember { mutableStateOf<AccountInfoPage?>(null) }
    val currentUser = user
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
    val selectedOrder = orders.firstOrNull { it.id == selectedOrderId }
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

    LaunchedEffect(currentUser?.id, initialOrderId) {
        if (currentUser != null && initialOrderId != null) selectedOrderId = initialOrderId
    }

    LaunchedEffect(currentUser?.id) {
        if (currentUser != null) {
            showLogin = false
            if (openPromotionsOnStart) accountSection = AccountSection.WALLET
            PushNotificationController.initialize(notificationContext)
            onAuthenticated?.invoke()
        }
    }

    LaunchedEffect(currentUser?.id, accountSection) {
        if (currentUser != null && accountSection == AccountSection.ORDERS) {
            vm.refreshOrders()
        }
        if (currentUser != null && accountSection == AccountSection.WALLET) {
            loyaltyViewModel.refresh()
            promotionsLoading = true
            promotionsError = null
            runCatching { promotionRepository.getPromotions() }
                .onSuccess { promotions = it }
                .onFailure {
                    promotions = emptyList()
                    promotionsError = it.message ?: "No se han podido cargar las promociones."
                }
            promotionsLoading = false
        }
    }

    if (currentUser == null && accountError?.type == StoreErrorType.SESSION_EXPIRED) {
        StoreErrorState(accountError!!, padding, onLogin = { vm.clearAccountMessages(); showLogin = true })
        return
    }

    if (authState == AccountAuthState.CHECKING && accountError != null) {
        StoreErrorState(accountError!!, padding, vm::retrySession)
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
            AccountInformationPageContent(selectedInfoPage!!, onBack = { selectedInfoPage = null; accountSection = AccountSection.HOME })
        }
        return
    }

    BackHandler(enabled = selectedOrder != null) { selectedOrderId = null }
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
        AccountOrderDetailScreenV2(padding, selectedOrder) { selectedOrderId = null }
        return
    }

    if (currentUser != null) {
        val fullName = listOf(currentUser.firstName, currentUser.lastName).filter { it.isNotBlank() }.joinToString(" ")
        if (accountSection == AccountSection.ORDERS) {
            PullToRefreshBox(
                modifier = Modifier.fillMaxSize().padding(padding),
                isRefreshing = ordersRefreshing,
                onRefresh = vm::refreshOrders
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                    contentPadding = PaddingValues(top = 20.dp, bottom = 24.dp)
                ) {
                    AccountOrdersContent(orders, loading, accountError, { accountSection = AccountSection.HOME }, { selectedOrderId = it }, vm::refreshOrders)
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                    .then(if (accountSection == AccountSection.HELP) Modifier else Modifier.padding(horizontal = 24.dp))
            ) {
                if (accountSection != AccountSection.HELP) Spacer(Modifier.height(if (accountSection == AccountSection.PROFILE) 8.dp else 20.dp))
                when (accountSection) {
                    AccountSection.HOME -> AccountHomeContentV2(
                        fullName = fullName,
                        email = currentUser.email,
                        onOrders = { accountSection = AccountSection.ORDERS },
                        onWallet = { accountSection = AccountSection.WALLET },
                        onProfile = { accountSection = AccountSection.PROFILE },
                        onLogin = { showLogin = true },
                        onHelp = { accountSection = AccountSection.HELP },
                        onInfoPage = { selectedInfoPage = it }
                    )
                    AccountSection.PROFILE -> AccountProfileContent(loading, { accountSection = AccountSection.HOME }, { accountSection = AccountSection.DATA }, { accountSection = AccountSection.ADDRESSES }, { vm.clearAccountMessages(); showForgot = true }, { vm.logout(onBeforeLogout) {
                            showLogin = true
                            showRegister = false
                            showForgot = false
                            selectedOrderId = null
                            selectedInfoPage = null
                            accountSection = AccountSection.HOME
                            vm.clearAccountMessages()
                        } })
                    AccountSection.DATA -> AccountPersonalDataContent(vm, currentUser) { accountSection = AccountSection.PROFILE }
                    AccountSection.ADDRESSES -> AccountAddressContent(vm, address) { accountSection = AccountSection.PROFILE }
                    AccountSection.HELP -> AccountHelpContent { accountSection = AccountSection.HOME }
                    AccountSection.WALLET -> AccountWalletContent(
                        authenticated = currentUser != null,
                        wallet = loyaltyWallet,
                        loading = loyaltyLoading,
                        error = loyaltyError,
                        promotions = promotions,
                        promotionsLoading = promotionsLoading,
                        promotionsError = promotionsError,
                        cartCouponCodes = cartCouponCodes,
                        onApplyPromotion = { code -> applyWalletCoupon(code) },
                        onBack = { accountSection = AccountSection.HOME },
                        onRefresh = loyaltyViewModel::refresh,
                        onApplyReward = { reward -> loyaltyViewModel.applyPending(reward, applyWalletCoupon) },
                        onRedeem = { points -> loyaltyViewModel.redeem(points, applyWalletCoupon) }
                    )
                    AccountSection.ORDERS -> Unit
                }
                if (accountSection != AccountSection.HELP) Spacer(Modifier.height(if (accountSection == AccountSection.PROFILE) 12.dp else 24.dp))
            }
        }
        if (showForgot) AccountForgotPasswordDialog(login, forgotPasswordLoading, error, notice, { showForgot = false; vm.clearAccountMessages() }, vm::forgotPassword)
    } else if (!showLogin) {
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .then(if (accountSection == AccountSection.HELP) Modifier else Modifier.padding(horizontal = 24.dp))
        ) {
            if (accountSection != AccountSection.HELP) Spacer(Modifier.height(20.dp))
            when (accountSection) {
                AccountSection.HELP -> AccountHelpContent { accountSection = AccountSection.HOME }
                else -> AccountHomeContentV2(
                    fullName = null,
                    email = null,
                    onOrders = { showLogin = true },
                    onWallet = { showLogin = true },
                    onProfile = { showLogin = true },
                    onLogin = { showLogin = true },
                    onHelp = { accountSection = AccountSection.HELP },
                    onInfoPage = { selectedInfoPage = it }
                )
            }
            if (accountSection != AccountSection.HELP) Spacer(Modifier.height(24.dp))
        }
    } else {
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            CatalogScreenHeader(
                title = "Iniciar sesión",
                onBack = leaveLogin,
                bottomPadding = 4.dp
            )
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(login, { login = it }, label = { Text("Correo o usuario") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(password, { password = it }, label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let { Spacer(Modifier.height(12.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(20.dp))
                Button({ vm.login(login.trim(), password) }, enabled = !loading, modifier = Modifier.fillMaxWidth()) { if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Iniciar sesión") }
                Spacer(Modifier.height(8.dp))
                TextButton({ vm.clearAccountMessages(); showForgot = true }, modifier = Modifier.fillMaxWidth()) { Text("He olvidado mi contraseña") }
                OutlinedButton({ vm.clearAccountMessages(); showRegister = true }, modifier = Modifier.fillMaxWidth()) { Text("Crear cuenta") }
            }
            if (showRegister) AccountRegisterDialog(login, loading, error, dismissRegister, vm::createAccount)
            if (showForgot) AccountForgotPasswordDialog(login, forgotPasswordLoading, error, notice, dismissForgot, vm::forgotPassword)
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

@Composable
private fun AccountHomeContentV2(
    fullName: String?,
    email: String?,
    onOrders: () -> Unit,
    onWallet: () -> Unit,
    onProfile: () -> Unit,
    onLogin: () -> Unit,
    onHelp: () -> Unit,
    onInfoPage: (AccountInfoPage) -> Unit
) {
    val isAuthenticated = !fullName.isNullOrBlank() && !email.isNullOrBlank()
    val nameParts = fullName.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    val initials = buildString { nameParts.firstOrNull()?.firstOrNull()?.let { append(it.uppercaseChar()) }; nameParts.drop(1).firstOrNull()?.firstOrNull()?.let { append(it.uppercaseChar()) } }.ifBlank { "CR" }
    Column(Modifier.fillMaxWidth()) {
        Text("Cuenta", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF183B35))
        Spacer(Modifier.height(20.dp))
        if (isAuthenticated) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(Modifier.size(60.dp), shape = CircleShape, color = Color(0xFFE9E6EA)) { Box(contentAlignment = Alignment.Center) { Text(initials, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = Color(0xFF183B35)) } }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) { Text(fullName.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium); Spacer(Modifier.height(3.dp)); Text(email.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onLogin).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(60.dp).background(Color(0xFFE3EFEB), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Outlined.Person, contentDescription = null, tint = Color(0xFF183B35), modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Inicia sesión o regístrate", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(3.dp))
                    Text("Accede a tu cuenta", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Spacer(Modifier.height(if (isAuthenticated) 24.dp else 12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccountCompactAccess(Modifier.weight(1f), "Pedidos", Icons.Outlined.History, onOrders)
            AccountCompactAccess(Modifier.weight(1f), "Cupones y promociones", Icons.Outlined.Sell, onWallet)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AccountCompactAccess(Modifier.weight(1f), "Perfil", Icons.Outlined.Person, onProfile)
            AccountCompactAccess(Modifier.weight(1f), "Ayuda", Icons.AutoMirrored.Outlined.HelpOutline, onHelp)
        }
        Spacer(Modifier.height(28.dp))
        AccountInformationHomeSection(onInfoPage)
        Spacer(Modifier.height(20.dp))
        Text("Versión ${BuildConfig.VERSION_NAME}", Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
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

@Composable
fun AccountWalletContent(
    authenticated: Boolean,
    wallet: es.criosrango.shared.loyalty.LoyaltyWallet?,
    loading: Boolean,
    error: String?,
    promotions: List<Promotion>,
    promotionsLoading: Boolean,
    promotionsError: String?,
    cartCouponCodes: Set<String>,
    onApplyPromotion: suspend (String) -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onApplyReward: (LoyaltyReward) -> Unit,
    onRedeem: (Int) -> Unit
) {
    var showRedeemDialog by remember { mutableStateOf(false) }
    var showWalletInfo by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val options = wallet?.redeemableOptions() ?: emptyList()

    Column(
        Modifier.fillMaxWidth().offset(y = (-32).dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, enabled = !loading, modifier = Modifier.size(40.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
            }
            Text(
                "Cupones y promociones",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }

        when {
            authenticated && loading && wallet == null -> CircularProgressIndicator(modifier = Modifier.padding(vertical = 12.dp))
            authenticated && wallet != null -> {
Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF6F0))
                ) {
                    Box(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        val walletAmount = wallet.walletValue
                            .replace(',', '.')
                            .toDoubleOrNull()
                            ?: 0.0
                        val minimumAmount = wallet.minimumRedeemValue
                            .replace(',', '.')
                            .toDoubleOrNull()
                            ?: 0.0
                        val progress =
                            if (minimumAmount > 0.0)
                                (walletAmount / minimumAmount).coerceIn(0.0, 1.0)
                            else
                                0.0
                        val remaining =
                            (minimumAmount - walletAmount).coerceAtLeast(0.0)

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(end = 34.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            WalletVisualIcon()
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    "Monedero",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    color = Color(0xFF183B35)
                                )
                                Text(
                                    loyaltyDisplayMoney(wallet.walletValue) + if (walletAmount >= minimumAmount) " disponibles" else " acumulados",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    color = Color(0xFF0F5C4D)
                                )
                            }
                            if (walletAmount >= minimumAmount && options.isNotEmpty()) {
                                Button(
                                    onClick = { showRedeemDialog = true },
                                    enabled = !loading,
                                    modifier = Modifier.width(76.dp).height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF0F5C4D),
                                        contentColor = Color.White
                                    )
                                ) { Text("Usar", style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                        
                         if (walletAmount < minimumAmount) {
                            Text(
                                "Has acumulado " + loyaltyDisplayMoney(wallet.walletValue) +
                                    " de " + loyaltyDisplayMoney(wallet.minimumRedeemValue),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF40514D)
                            )
                            Box(
                                modifier = Modifier.fillMaxWidth().height(6.dp)
                                    .background(Color(0xFFD7E8E1), RoundedCornerShape(3.dp))
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxWidth(progress.toFloat()).height(6.dp)
                                        .background(Color(0xFF0F5C4D), RoundedCornerShape(3.dp))
                                )
                            }
                            Text(
                                "Te faltan " + loyaltyDisplayMoney(
                                    formatWalletRemaining(remaining)
                                ) + " para desbloquear tu saldo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF40514D)
                            )
                        }
                    }
                        IconButton(
                            onClick = { showWalletInfo = true },
                            modifier = Modifier.align(Alignment.TopEnd).size(32.dp)
                        ) {
                            Icon(
                                imageVector = InfoLineIcon,
                                contentDescription = "Cómo funciona el monedero",
                                tint = Color(0xFF0F5C4D),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                    }
                }
            }
        }

        if (authenticated && !wallet?.pendingRewards.isNullOrEmpty()) {
            Text(
                "Saldo listo para usar",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F0F8))
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp)
                ) {
                    wallet?.pendingRewards.orEmpty().forEachIndexed { index, reward ->
                        val applied = cartCouponCodes.any { it.equals(reward.code, ignoreCase = true) }
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = WalletLineIcon,
                                contentDescription = "Saldo de monedero",
                                tint = Color(0xFF0F5C4D),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Saldo de monedero · " + reward.amount.replace('.', ',') + " €",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF183B35),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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
                                Button(
                                    onClick = { onApplyReward(reward) },
                                    enabled = !loading,
                                    shape = RoundedCornerShape(50),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF0F5C4D),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Text("Aplicar", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        if (index < wallet!!.pendingRewards.lastIndex) {
                            HorizontalDivider(
                                Modifier.padding(start = 30.dp),
                                color = Color(0xFFDCD8E8),
                                thickness = 1.dp
                            )
                        }
                    }
                }
            }
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onRefresh, enabled = !loading) { Text("Reintentar") }
        }

        val visiblePromotions = if (authenticated) promotions else promotions.filter { it.isVisibleToAnonymous() }
        Text(
            "Promociones disponibles (${visiblePromotions.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        when {
            promotionsLoading && visiblePromotions.isEmpty() -> CircularProgressIndicator(modifier = Modifier.padding(vertical = 8.dp))
            promotionsError != null && visiblePromotions.isEmpty() -> Text(promotionsError!!, color = MaterialTheme.colorScheme.error)
            visiblePromotions.isEmpty() -> Text("No hay promociones disponibles.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> visiblePromotions.sortedByDescending { it.priority }.forEachIndexed { index, promotion ->
                val code = promotion.code?.takeIf { it.isNotBlank() }
                val applied = code?.let { couponCode ->
                    cartCouponCodes.any { it.equals(couponCode, ignoreCase = true) }
                } == true
                val isBirthday = promotion.type.equals("birthday_coupon", ignoreCase = true) || listOfNotNull(promotion.title, promotion.description).any { it.contains("cumpleaños", ignoreCase = true) || it.contains("birthday", ignoreCase = true) }

                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = promotionCardColor(promotion, isBirthday)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(accountPromotionIcon(promotion), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 1.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                if (isBirthday) "Tu regalo de cumpleaños" else promotion.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF183B35)
                            )
                            val description = if (isBirthday) {
                                "15% de descuento por tu cumpleaños."
                            } else {
                                promotion.description.trim()
                            }
                            if (description.isNotBlank()) {
                                Text(
                                    description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF40514D)
                                )
                            }
                            if (isBirthday) {
                                Text(
                                    "Válido durante 15 días.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF40514D)
                                )
                            }
                            promotion.expiresAt?.takeIf { it.isNotBlank() }?.let {
                                PromotionDateRow("Válido hasta " + formatPromotionExpiry(it))
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        code?.let { couponCode ->
                            if (applied) {
                                Surface(
                                    modifier = Modifier.width(78.dp).height(36.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    color = Color(0xFFE8F5EF)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            "Aplicado",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                            color = Color(0xFF0F5C4D),
                                            fontWeight = FontWeight.SemiBold,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            } else {
                                Button(
                                    onClick = { scope.launch { onApplyPromotion(couponCode) } },
                                    enabled = !loading,
                                    modifier = Modifier.width(78.dp).height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF0F5C4D),
                                        contentColor = Color.White
                                    )
                                ) { Text("Aplicar", style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showWalletInfo) {
        AlertDialog(
            onDismissRequest = { showWalletInfo = false },
            title = {
                Text(
                    "¿Cómo funciona tu monedero?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    "1 € gastado = 1 punto.\n\n" +
                        "Cuando acumules 100 puntos, podrás convertirlos en 5 € de saldo para tus compras.\n\n" +
                        "La barra te indica cuánto te falta para desbloquear el saldo.\n\n" +
                        "Cuando tengas saldo disponible, pulsa “Usar” para elegir cuánto quieres aplicar a tu compra."
                )
            },
            confirmButton = {
                Button(
                    onClick = { showWalletInfo = false },
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0F5C4D),
                        contentColor = Color.White
                    )
                ) { Text("Entendido", style = MaterialTheme.typography.labelMedium) }
            }
        )
    }

    if (showRedeemDialog && options.isNotEmpty()) {
        LoyaltyRedeemDialog(
            options = options,
            loading = loading,
            onDismiss = { showRedeemDialog = false },
            onSelect = { option ->
                showRedeemDialog = false
                onRedeem(option.points)
            }
        )
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
    Column(Modifier.fillMaxWidth()) {
        CatalogScreenHeader(title = "Ayuda", onBack = onBack, bottomPadding = 4.dp)
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(4.dp))
            Text("Contacta con nosotros", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = Color(0xFF183B35))
            Spacer(Modifier.height(16.dp))
            HelpContactRow("WhatsApp", "633 246 788", Icons.AutoMirrored.Outlined.Chat) {
                val message = "Hola, necesito ayuda con la app de Críos&Rango."
                val encodedMessage = java.net.URLEncoder.encode(message, "UTF-8")
                val whatsappUri = Uri.parse("https://wa.me/34633246788?text=$encodedMessage")
                val intent = Intent(Intent.ACTION_VIEW, whatsappUri)
                runCatching { context.startActivity(intent) }.onFailure {
                    Toast.makeText(context, "No se ha podido abrir WhatsApp.", Toast.LENGTH_SHORT).show()
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            HelpContactRow("Llamar", "969 091 236", Icons.Outlined.Phone) {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+34969091236"))
                runCatching { context.startActivity(intent) }.onFailure {
                    Toast.makeText(context, "No se ha podido abrir el teléfono.", Toast.LENGTH_SHORT).show()
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            HelpContactRow("Correo electrónico", "criosrango@criosrango.es", Icons.Outlined.Email) {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:criosrango@criosrango.es")
                    putExtra(Intent.EXTRA_SUBJECT, "Consulta desde la app Críos&Rango")
                }
                runCatching { context.startActivity(intent) }.onFailure {
                    Toast.makeText(context, "No se ha podido abrir el correo.", Toast.LENGTH_SHORT).show()
                }
            }
            Spacer(Modifier.height(28.dp))
            StoreHoursSection()
        }
    }
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

private fun LazyListScope.AccountOrdersContent(
    orders: List<AccountOrderSummary>,
    loading: Boolean,
    accountError: StoreUiError?,
    onBack: () -> Unit,
    onOrderClick: (Int) -> Unit,
    onRefresh: () -> Unit
) {
    item {
        AccountSectionHeader("Pedidos", onBack)
        Spacer(Modifier.height(16.dp))
    }
    if (loading && orders.isEmpty()) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
        }
    } else if (accountError != null && accountError.type != StoreErrorType.SESSION_EXPIRED && orders.isEmpty()) {
        item {
            StoreErrorState(accountError, PaddingValues(0.dp), onRefresh)
        }
    } else if (orders.isEmpty()) {
        item {
            Text("Todavía no tienes pedidos.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else if (accountError != null && accountError.type != StoreErrorType.SESSION_EXPIRED) {
        item {
            Text(accountError.title, color = MaterialTheme.colorScheme.error)
            Text(accountError.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onRefresh, enabled = !loading) { Text("Reintentar") }
        }
    } else {
        items(orders.size) { index ->
            val order = orders[index]
            Card(Modifier.fillMaxWidth().clickable { onOrderClick(order.id) }) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Pedido #${order.number.ifBlank { order.id.toString() }}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                        OrderStatusBadge(order.status, order.statusLabel)
                    }
                    order.dateCreated?.takeIf { it.isNotBlank() }?.let { rawDate ->
                        val p = rawDate.take(10).split("-")
                        Spacer(Modifier.height(8.dp))
                        Text("Fecha: ${if (p.size == 3) "${p[2]}-${p[1]}-${p[0]}" else rawDate.take(10)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (order.items.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        order.items.forEach { item ->
                            Text("${item.quantity} × ${item.name}")
                            val variationText = item.variations.filter { it.name.isNotBlank() && it.value.isNotBlank() }.joinToString(" · ") {
                                "${if (it.name.equals("Tallas", true)) "Talla" else it.name}: ${it.value}"
                            }
                            if (variationText.isNotBlank()) Text(variationText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(5.dp))
                        }
                    }
                    if (order.paymentMethodTitle.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(if (order.paymentMethodTitle.contains("bizum", true)) "Pago con Bizum" else "Pago con tarjeta")
                    }
                    if (order.total.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (order.currency == "EUR") "Total: ${order.total.replace('.', ',')} €" else "${order.total} ${order.currency}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AccountProfileContent(loading: Boolean, onBack: () -> Unit, onPersonalData: () -> Unit, onAddress: () -> Unit, onPassword: () -> Unit, onLogout: () -> Unit) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val context = LocalContext.current

    AccountSectionHeader("Perfil", onBack)
    Spacer(Modifier.height(8.dp))
    ProfileMenuRow("Datos personales", Icons.Outlined.Person, onPersonalData)
    Spacer(Modifier.height(8.dp))
    ProfileMenuRow("Dirección de entrega", Icons.Outlined.LocationOn, onAddress)
    Spacer(Modifier.height(8.dp))
    ProfileMenuRow("Cambiar contraseña", Icons.Outlined.Lock, onPassword)
    Spacer(Modifier.height(8.dp))
    ProfileMenuRow(
        AccountDeletion.TITLE,
        Icons.Outlined.Delete,
        onClick = { showDeleteConfirmation = true },
        containerColor = AccountDeletion.background,
        contentColor = AccountDeletion.accent
    )
    Spacer(Modifier.height(12.dp))
    PushNotificationPreferences()
    Spacer(Modifier.height(12.dp))
    Card(Modifier.fillMaxWidth().clickable(onClick = onLogout), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFECEF))) { Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.AutoMirrored.Outlined.Logout, null, tint = Color(0xFFC73B4C)); Spacer(Modifier.width(14.dp)); Text("Cerrar sesión", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = Color(0xFFC73B4C)) } }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(AccountDeletion.TITLE) },
            text = { Text(AccountDeletion.MESSAGE) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AccountDeletion.URL))
                    runCatching { context.startActivity(intent) }
                        .onFailure {
                            Toast.makeText(
                                context,
                                "No se ha podido abrir la página de eliminación.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                }) { Text(AccountDeletion.CONTINUE) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(AccountDeletion.CANCEL)
                }
            }
        )
    }
}

@Composable
private fun ProfileMenuRow(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    containerColor: Color = Color(0xFFF0EDF1),
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = contentColor)
            Text("›", style = MaterialTheme.typography.titleLarge, color = contentColor)
        }
    }
}

@Composable
private fun AccountPersonalDataContent(vm: AccountViewModel, user: AccountUser, onBack: () -> Unit) {
    val saving by vm.savingAccountDetails.collectAsStateWithLifecycle(); val saveError by vm.accountDataError.collectAsStateWithLifecycle(); val saveNotice by vm.accountDataNotice.collectAsStateWithLifecycle(); var firstName by remember(user.id) { mutableStateOf(user.firstName) }; var lastName by remember(user.id) { mutableStateOf(user.lastName) }
    AccountSectionHeader("Datos personales", onBack); Spacer(Modifier.height(20.dp)); OutlinedTextField(firstName, { firstName = it; vm.clearAccountDataMessages() }, label = { Text("Nombre") }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(14.dp)); OutlinedTextField(lastName, { lastName = it; vm.clearAccountDataMessages() }, label = { Text("Apellidos") }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(14.dp)); OutlinedTextField(user.email.ifBlank { "—" }, {}, label = { Text("Correo electrónico") }, readOnly = true, singleLine = true, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(14.dp)); OutlinedTextField(formatBirthDateForDisplay(user.birthDate).ifBlank { "No indicada" }, {}, label = { Text("Fecha de nacimiento") }, readOnly = true, singleLine = true, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(20.dp)); Button({ vm.updateAccountDetails(firstName.trim(), lastName.trim()) }, enabled = !saving, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF183B35))) { if (saving) Text("Guardando...") else Text("Guardar cambios") }; saveError?.let { Spacer(Modifier.height(10.dp)); Text(it, color = MaterialTheme.colorScheme.error) }; saveNotice?.let { Spacer(Modifier.height(10.dp)); Text(it, color = Color(0xFF183B35)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountAddressContent(vm: AccountViewModel, address: AccountCustomerAddress?, onBack: () -> Unit) {
    val saving by vm.savingAddress.collectAsStateWithLifecycle(); val saveError by vm.addressSaveError.collectAsStateWithLifecycle(); val saveNotice by vm.addressSaveNotice.collectAsStateWithLifecycle(); var firstName by remember(address) { mutableStateOf(address?.firstName.orEmpty()) }; var lastName by remember(address) { mutableStateOf(address?.lastName.orEmpty()) }; var address1 by remember(address) { mutableStateOf(address?.address1.orEmpty()) }; var address2 by remember(address) { mutableStateOf(address?.address2.orEmpty()) }; var postcode by remember(address) { mutableStateOf(address?.postcode.orEmpty()) }; var city by remember(address) { mutableStateOf(address?.city.orEmpty()) }; var state by remember(address) { mutableStateOf(address?.state.orEmpty()) }; var provinceExpanded by remember { mutableStateOf(false) }; val provinceName = SPANISH_PROVINCES.firstOrNull { it.code.equals(state, true) }?.name.orEmpty()
    AccountSectionHeader("Dirección de entrega", onBack); Spacer(Modifier.height(20.dp)); AccountAddressField("Nombre", firstName, !saving) { firstName = it; vm.clearAddressSaveMessages() }; Spacer(Modifier.height(14.dp)); AccountAddressField("Apellidos", lastName, !saving) { lastName = it; vm.clearAddressSaveMessages() }; Spacer(Modifier.height(14.dp)); AccountAddressField("Dirección", address1, !saving) { address1 = it; vm.clearAddressSaveMessages() }; Spacer(Modifier.height(14.dp)); AccountAddressField("Dirección adicional / Piso, puerta, etc. (opcional)", address2, !saving) { address2 = it; vm.clearAddressSaveMessages() }; Spacer(Modifier.height(14.dp)); AccountAddressField("Código postal", postcode, !saving) { postcode = it; vm.clearAddressSaveMessages() }; Spacer(Modifier.height(14.dp)); AccountAddressField("Localidad", city, !saving) { city = it; vm.clearAddressSaveMessages() }; Spacer(Modifier.height(14.dp)); ExposedDropdownMenuBox(expanded = provinceExpanded, onExpandedChange = { if (!saving) provinceExpanded = it }) { OutlinedTextField(provinceName.ifBlank { state }, {}, label = { Text("Provincia") }, readOnly = true, enabled = !saving, singleLine = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = provinceExpanded) }, modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = !saving).fillMaxWidth()); ExposedDropdownMenu(expanded = provinceExpanded, onDismissRequest = { provinceExpanded = false }) { SPANISH_PROVINCES.distinctBy { it.code }.forEach { province -> DropdownMenuItem(text = { Text(province.name) }, onClick = { state = province.code; provinceExpanded = false; vm.clearAddressSaveMessages() }) } } }; Spacer(Modifier.height(14.dp)); OutlinedTextField("España", {}, label = { Text("País") }, readOnly = true, enabled = !saving, singleLine = true, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(20.dp)); Button({ vm.saveAddress(AccountCustomerAddress(firstName = firstName, lastName = lastName, email = address?.email.orEmpty(), phone = address?.phone.orEmpty(), address1 = address1, address2 = address2, postcode = postcode, city = city, state = state, country = "ES")) }, enabled = !saving, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF183B35))) { if (saving) Text("Guardando...") else Text("Guardar dirección") }; saveError?.let { Spacer(Modifier.height(10.dp)); Text(it, color = MaterialTheme.colorScheme.error) }; saveNotice?.let { Spacer(Modifier.height(10.dp)); Text(it) }
}

@Composable
private fun AccountAddressField(label: String, value: String, enabled: Boolean, onValueChange: (String) -> Unit) { OutlinedTextField(value, onValueChange, label = { Text(label) }, singleLine = true, enabled = enabled, modifier = Modifier.fillMaxWidth()) }

@Composable
fun AccountSectionHeader(title: String, onBack: () -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver") }; Spacer(Modifier.width(6.dp)); Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF183B35)) } }

@Composable
private fun AccountRegisterDialog(initialEmail: String, loading: Boolean, error: String?, onDismiss: () -> Unit, onCreate: (String, String, String, String, String, String?) -> Unit) {
    var firstName by remember { mutableStateOf("") }; var lastName by remember { mutableStateOf("") }; var email by remember(initialEmail) { mutableStateOf(initialEmail) }; var phone by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var repeatPassword by remember { mutableStateOf("") }; var birthDate by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = { if (!loading) onDismiss() }, title = { Text("Crear cuenta") }, text = { Column(Modifier.verticalScroll(rememberScrollState())) { OutlinedTextField(firstName, { firstName = it }, label = { Text("Nombre") }, singleLine = true); Spacer(Modifier.height(8.dp)); OutlinedTextField(lastName, { lastName = it }, label = { Text("Apellidos") }, singleLine = true); Spacer(Modifier.height(8.dp)); OutlinedTextField(email, { email = it }, label = { Text("Correo electrónico") }, singleLine = true); Spacer(Modifier.height(8.dp)); OutlinedTextField(phone, { phone = it }, label = { Text("Teléfono (opcional)") }, singleLine = true); Spacer(Modifier.height(8.dp)); BirthDatePickerField(birthDate, { birthDate = it }, modifier = Modifier.fillMaxWidth(), enabled = !loading); Spacer(Modifier.height(8.dp)); OutlinedTextField(password, { password = it }, label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(), singleLine = true); Spacer(Modifier.height(8.dp)); OutlinedTextField(repeatPassword, { repeatPassword = it }, label = { Text("Repetir contraseña") }, visualTransformation = PasswordVisualTransformation(), singleLine = true); if (repeatPassword.isNotEmpty() && password != repeatPassword) { Spacer(Modifier.height(8.dp)); Text("Las contraseñas no coinciden.", color = MaterialTheme.colorScheme.error) }; error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) } } }, confirmButton = { TextButton({ onCreate(firstName.trim(), lastName.trim(), email.trim(), phone.trim(), password, birthDate) }, enabled = !loading && firstName.isNotBlank() && lastName.isNotBlank() && email.isNotBlank() && password.length >= 8 && password == repeatPassword) { if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Crear cuenta") } }, dismissButton = { TextButton(onDismiss, enabled = !loading) { Text("Cancelar") } })
}

@Composable
private fun AccountForgotPasswordDialog(initialLogin: String, loading: Boolean, error: String?, notice: String?, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    var login by remember(initialLogin) { mutableStateOf(initialLogin) }
    AlertDialog(onDismissRequest = { if (!loading) onDismiss() }, title = { Text("Recuperar contraseña") }, text = { Column { Text("Introduce tu correo o nombre de usuario."); Spacer(Modifier.height(12.dp)); OutlinedTextField(login, { login = it }, label = { Text("Correo o usuario") }, singleLine = true); error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }; notice?.let { Spacer(Modifier.height(8.dp)); Text(it) } } }, confirmButton = { if (notice == null) TextButton({ onSend(login.trim()) }, enabled = !loading && login.isNotBlank()) { if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Enviar correo") } else TextButton(onDismiss) { Text("Cerrar") } }, dismissButton = { if (notice == null) TextButton(onDismiss, enabled = !loading) { Text("Cancelar") } })
}
private fun formatWalletRemaining(value: Double): String {
    val cents = kotlin.math.round(value * 100.0).toLong()
    val whole = cents / 100L
    val fraction = kotlin.math.abs(cents % 100L)
    return whole.toString() + "," + fraction.toString().padStart(2, '0')
}



internal fun shouldResetAccountUiForAuthState(authState: AccountAuthState): Boolean =
    authState == AccountAuthState.UNAUTHENTICATED
