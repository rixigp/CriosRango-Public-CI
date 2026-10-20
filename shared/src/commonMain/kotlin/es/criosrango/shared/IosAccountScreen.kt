package es.criosrango.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import es.criosrango.shared.account.AccountCustomerAddress
import es.criosrango.shared.account.AccountOrderSummary
import es.criosrango.shared.account.AccountRepository
import es.criosrango.shared.account.AccountUser
import es.criosrango.shared.StoreAccountRootScreen
import es.criosrango.shared.StoreAccountSessionState
import es.criosrango.shared.StoreLoginScreen
import es.criosrango.shared.loyalty.LoyaltyRepository
import es.criosrango.shared.promotions.Promotion
import es.criosrango.shared.promotions.PromotionRepository
import es.criosrango.shared.promotions.isVisibleToAnonymous
import es.criosrango.shared.loyalty.LoyaltyReward
import es.criosrango.shared.loyalty.LoyaltyWallet
import es.criosrango.shared.loyalty.redeemableOptions
import kotlinx.coroutines.launch

private val SPANISH_PROVINCE_CODES = setOf("C","VI","AB","A","AL","O","AV","BA","B","BI","BU","CC","CA","S","CS","CE","CR","CO","CU","GI","GR","GU","SS","H","HU","J","LE","L","LO","LU","M","MA","ML","MU","NA","OR","P","GC","PO","SA","TF","SG","SE","SO","T","TE","TO","V","VA","ZA","Z")

private enum class IosAccountPage { HOME, LOGIN, REGISTER, FORGOT, PROFILE, DATA, ADDRESS, ORDERS, WALLET, INFO, HELP, ORDER_DETAIL }

@Composable
fun CriosRangoIOSAccountScreen(
    repository: AccountRepository,
    sessionState: StoreAccountSessionState,
    loyaltyRepository: LoyaltyRepository,
    appVersion: String = "",
    cartCouponCodes: Set<String> = emptySet(),
    onApplyWalletCoupon: suspend (String) -> Unit,
    modifier: Modifier = Modifier,
    onOpenExternalUrl: (String) -> Unit,
    initialOrderId: Int? = null,
    openPromotionsOnStart: Boolean = false,
    openLoginOnStart: Boolean = false,
    onBackFromLogin: (() -> Unit)? = null,
    onAuthenticated: (() -> Unit)? = null,
    onPromotionsOpened: () -> Unit = {},
    onBeforeLogout: suspend () -> Unit = {}
) {
    var page by remember {
        mutableStateOf(if (openPromotionsOnStart) IosAccountPage.WALLET else if (openLoginOnStart) IosAccountPage.LOGIN else IosAccountPage.HOME)
    }
    var user by remember(sessionState) { mutableStateOf(sessionState.snapshot.value.user) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedOrder by remember { mutableStateOf<AccountOrderSummary?>(null) }
    val ordersOwner = remember(repository, sessionState) { StoreOrdersOwner(repository, sessionState) }
    var pendingPushOrderId by remember { mutableStateOf(initialOrderId) }
    var selectedInfoPage by remember { mutableStateOf<AccountInfoPage?>(null) }
    var forgotReturnPage by remember { mutableStateOf(IosAccountPage.LOGIN) }
    var addressReturnPage by remember { mutableStateOf(IosAccountPage.HOME) }
    val scope = rememberCoroutineScope()
    val sharedAddressOwner = remember(repository) { StoreAddressOwner(repository) }
    val authSnapshot by sessionState.snapshot.collectAsState()
    LaunchedEffect(authSnapshot.user?.id) { ordersOwner.onSessionChanged(authSnapshot.user?.id) }
    val promotionRepository = remember(repository) {
        PromotionRepository(bearerTokenProvider = { repository.currentToken() })
    }
    fun invalidateExpiredSession() { sessionState.logout(); user = null; selectedOrder = null; error = null; page = IosAccountPage.HOME }

    LaunchedEffect(openPromotionsOnStart, user?.id) {
        if (openPromotionsOnStart && user != null) {
            page = IosAccountPage.WALLET
            onPromotionsOpened()
        }
    }

    LaunchedEffect(user?.id, pendingPushOrderId) {
        val id = pendingPushOrderId ?: return@LaunchedEffect
        if (user == null) return@LaunchedEffect
        try {
            ordersOwner.onSessionChanged(user?.id)
            ordersOwner.load()
            selectedOrder = ordersOwner.findById(id)
            if (selectedOrder != null) { page = IosAccountPage.ORDER_DETAIL; pendingPushOrderId = null }
            else if (ordersOwner.state.value.error == null) pendingPushOrderId = null
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Retain the pending ID after a transient failure so it can be resolved on remount.
        }
    }

    fun finishAuthentication(authenticatedUser: AccountUser) {
        user = authenticatedUser
        error = null
        page = if (openPromotionsOnStart) IosAccountPage.WALLET else IosAccountPage.HOME
        scope.launch {
            runCatching { repository.claimPendingOrder() }
                .onFailure { println("KMP_ACCOUNT_CLAIM_FAILED=${it.message}") }
            if (!repository.hasSession) {
                user = null
                error = "La sesión ha caducado. Vuelve a iniciar sesión."
                page = IosAccountPage.HOME
            } else {
                onAuthenticated?.invoke()
            }
        }
    }

    LaunchedEffect(authSnapshot.status, authSnapshot.user, authSnapshot.error) {
        when (authSnapshot.status) {
            StoreAccountAuthStatus.AUTHENTICATED -> user = authSnapshot.user
            StoreAccountAuthStatus.UNAUTHENTICATED -> user = null
            StoreAccountAuthStatus.CHECKING -> Unit
        }
        error = authSnapshot.error
    }

    MaterialTheme {
        androidx.compose.foundation.layout.Box(modifier.fillMaxSize()) {
        if (authSnapshot.status == StoreAccountAuthStatus.CHECKING && authSnapshot.loading) {
            FullScreenLoading("Comprobando sesión")
        } else {
            when (page) {
                IosAccountPage.HOME -> Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
                    (error ?: authSnapshot.error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    StoreAccountRootScreen(
                        modifier = Modifier.weight(1f),
                        contentHorizontalPadding = 4.dp,
                        contentTopSpacing = 38.dp,
                        headerHorizontalPadding = 0.dp,
                        fullName = user?.let { it.displayName.ifBlank { it.email } },
                        email = user?.email,
                        appVersion = appVersion,
                        onOrders = { if (user == null) page = IosAccountPage.LOGIN else page = IosAccountPage.ORDERS },
                        onWallet = { if (user == null) page = IosAccountPage.LOGIN else page = IosAccountPage.WALLET },
                        onProfile = { if (user == null) page = IosAccountPage.LOGIN else page = IosAccountPage.PROFILE },
                        onLogin = { error = null; page = IosAccountPage.LOGIN },
                        onHelp = { error = null; page = IosAccountPage.HELP },
                        onInfoPage = { selectedInfoPage = AccountInfoPage.valueOf(it.name); error = null; page = IosAccountPage.INFO }
                    )
                }
                IosAccountPage.LOGIN -> IosLoginScreen(
                    repository,
                    sessionState,
                    onAuthenticated = ::finishAuthentication,
                    onRegister = { page = IosAccountPage.REGISTER },
                    onForgot = { forgotReturnPage = IosAccountPage.LOGIN; page = IosAccountPage.FORGOT },
                    onBack = { if (onBackFromLogin != null) onBackFromLogin() else page = IosAccountPage.HOME }
                )
                IosAccountPage.REGISTER -> IosRegisterScreen(
                    repository,
                    onAuthenticated = ::finishAuthentication,
                    onLogin = { page = IosAccountPage.LOGIN },
                    onBack = { page = IosAccountPage.HOME }
                )
                IosAccountPage.FORGOT -> IosForgotPasswordScreen(repository) { page = forgotReturnPage }
                IosAccountPage.PROFILE -> IosProfileMenuScreen(
                    onPersonalData = { page = IosAccountPage.DATA },
                    onAddress = { addressReturnPage = IosAccountPage.PROFILE; page = IosAccountPage.ADDRESS },
                    onPassword = { forgotReturnPage = IosAccountPage.PROFILE; page = IosAccountPage.FORGOT },
                    onDeleteAccount = { onOpenExternalUrl(AccountDeletion.URL) },
                    onLogout = {
                        scope.launch {
                            runCatching { onBeforeLogout() }
                            runCatching { repository.logout() }
                            sessionState.logout()
                            user = null
                            error = null
                            page = IosAccountPage.HOME
                        }
                    },
                    onBack = { page = IosAccountPage.HOME }
                )
                IosAccountPage.DATA -> StoreProfileScreen(
                    repository = repository,
                    sessionState = sessionState,
                    user = user,
                    onUserChanged = { updated -> user = updated; sessionState.publishAuthenticatedUser(updated) },
                    onSessionExpired = ::invalidateExpiredSession,
                    onBack = { page = IosAccountPage.PROFILE }
                )
                IosAccountPage.ADDRESS -> StoreAddressScreen(
                    repository = repository,
                    addressOwner = sharedAddressOwner,
                    onSessionExpired = ::invalidateExpiredSession,
                    onBack = { page = addressReturnPage }
                )
                IosAccountPage.ORDERS -> StoreOrdersScreen(ordersOwner, sessionState, onBack = { page = IosAccountPage.HOME }, onOpenOrder = { selectedOrder = it; page = IosAccountPage.ORDER_DETAIL }, onSessionExpired = ::invalidateExpiredSession)
                IosAccountPage.WALLET -> IosWalletScreen(
                    authenticated = user != null,
                    repository = loyaltyRepository,
                    promotionRepository = promotionRepository,
                    cartCouponCodes = cartCouponCodes,
                    onApplyCoupon = onApplyWalletCoupon,
                    onBack = { page = IosAccountPage.HOME }
                )
                IosAccountPage.INFO -> selectedInfoPage?.let { infoPage -> IosInformationPageScreen(infoPage) { page = IosAccountPage.HOME } }
                IosAccountPage.HELP -> IosHelpScreen { page = IosAccountPage.HOME }
                IosAccountPage.ORDER_DETAIL -> selectedOrder?.let { order -> StoreOrderDetailScreen(order, onBack = { page = IosAccountPage.ORDERS }, onRequestReturn = { selected -> onOpenExternalUrl("https://wa.me/34633246788?text=Hola%2C%20quiero%20solicitar%20la%20devoluci%C3%B3n%20de%20mi%20pedido%20%23" + selected.number + "%20%3FPod%C3%A9is%20indicarme%20c%C3%B3mo%20proceder%3F") }) }
            }
        }
        }
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

private val CakeLineIcon = buildLineIcon("CakeLineIcon") {
    moveTo(4f, 10f)
    lineTo(20f, 10f)
    lineTo(20f, 19f)
    lineTo(4f, 19f)
    close()
    moveTo(4f, 14f)
    lineTo(20f, 14f)
    moveTo(8f, 10f)
    lineTo(8f, 7f)
    moveTo(12f, 10f)
    lineTo(12f, 6f)
    moveTo(16f, 10f)
    lineTo(16f, 7f)
}

private val TagLineIcon = buildLineIcon("TagLineIcon") {
    moveTo(3.5f, 11f)
    lineTo(11f, 3.5f)
    lineTo(20f, 12.5f)
    lineTo(12.5f, 20f)
    close()
    moveTo(8f, 8f)
    lineTo(8f, 8f)
}

private val DiscountTagLineIcon = buildLineIcon("DiscountTagLineIcon") {
    moveTo(4f, 7f)
    lineTo(20f, 7f)
    lineTo(20f, 17f)
    lineTo(4f, 17f)
    close()
    moveTo(8f, 12f)
    lineTo(16f, 12f)
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
@Composable
private fun PromotionVisualIcon(promotion: Promotion, isBirthday: Boolean) {
    val kind = promotionVisualKind(promotion, isBirthday)
    val background = when (kind) {
        PromotionVisualKind.BIRTHDAY -> Color(0xFFFDECEF)
        PromotionVisualKind.WELCOME -> Color(0xFFEAF4FC)
        PromotionVisualKind.DISCOUNT -> Color(0xFFFFF6DF)
    }
    val iconColor = when (kind) {
        PromotionVisualKind.BIRTHDAY -> Color(0xFFD96A86)
        PromotionVisualKind.WELCOME -> Color(0xFF2E7BB4)
        PromotionVisualKind.DISCOUNT -> Color(0xFFD49A00)
    }
    Box(
        modifier = Modifier.size(40.dp).background(background, RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center
    ) {
        when (kind) {
            PromotionVisualKind.BIRTHDAY -> Icon(
                imageVector = CakeLineIcon,
                contentDescription = "Cumpleaños",
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            PromotionVisualKind.WELCOME -> Icon(
                imageVector = TagLineIcon,
                contentDescription = "Promoción de bienvenida",
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            PromotionVisualKind.DISCOUNT -> Icon(
                imageVector = DiscountTagLineIcon,
                contentDescription = "Descuento",
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private enum class PromotionVisualKind { BIRTHDAY, WELCOME, DISCOUNT }

private fun promotionVisualKind(promotion: Promotion, isBirthday: Boolean): PromotionVisualKind {
    if (isBirthday) return PromotionVisualKind.BIRTHDAY
    val text = listOfNotNull(promotion.type, promotion.title, promotion.description)
        .joinToString(" ")
        .lowercase()
    return if ("bienvenida" in text || "welcome" in text) {
        PromotionVisualKind.WELCOME
    } else {
        PromotionVisualKind.DISCOUNT
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
private fun IosWalletScreen(
    authenticated: Boolean,
    repository: LoyaltyRepository,
    promotionRepository: PromotionRepository,
    cartCouponCodes: Set<String>,
    onApplyCoupon: suspend (String) -> Unit,
    onBack: () -> Unit
) {
    var wallet by remember { mutableStateOf<LoyaltyWallet?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showRedeemDialog by remember { mutableStateOf(false) }
    var showWalletInfo by remember { mutableStateOf(false) }
    var pendingPoints by remember { mutableStateOf<Int?>(null) }
    var pendingRequestId by remember { mutableStateOf<String?>(null) }
    var promotions by remember { mutableStateOf(emptyList<Promotion>()) }
    var promotionsLoading by remember { mutableStateOf(true) }
    var promotionsError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        scope.launch {
            loading = true
            error = null
            runCatching { repository.getWallet() }
                .onSuccess { wallet = it }
                .onFailure { error = it.message ?: "No se ha podido cargar el monedero." }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        refresh()
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

    fun redeem(points: Int) {
        val requestId = if (pendingPoints == points && !pendingRequestId.isNullOrBlank()) {
            pendingRequestId!!
        } else {
            pendingPoints = points
            repository.newRequestId().also { pendingRequestId = it }
        }
        scope.launch {
            loading = true
            error = null
            runCatching { repository.redeem(points, requestId) }
                .onSuccess { response ->
                    wallet = runCatching { repository.getWallet() }.getOrNull() ?: wallet
                    val coupon = response.coupon
                    if (coupon == null) error = "El canje no ha devuelto un cupón utilizable." else onApplyCoupon(coupon.code)
                    pendingPoints = null
                    pendingRequestId = null
                }
                .onFailure { error = it.message ?: "No se ha podido utilizar el monedero." }
            loading = false
        }
    }

    fun applyReward(reward: LoyaltyReward) {
        scope.launch {
            loading = true
            error = null
            onApplyCoupon(reward.code)
            wallet = runCatching { repository.getWallet() }.getOrNull() ?: wallet
            loading = false
        }
    }

    val options = wallet?.redeemableOptions() ?: emptyList()

    Column(Modifier.fillMaxSize()) {
        StorePageHeader(title = "Cupones y promociones", onBack = onBack, backEnabled = !loading)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).offset(y = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

        when {
            authenticated && loading && wallet == null -> FullScreenLoading("Cargando monedero")
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
                        val walletAmount = wallet!!.walletValue
                            .replace(',', '.')
                            .toDoubleOrNull()
                            ?: 0.0
                        val minimumAmount = wallet!!.minimumRedeemValue
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
                                    wallet!!.walletValue + if (walletAmount >= minimumAmount) " disponibles" else " acumulados",
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
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F5C4D), contentColor = Color.White)
                                ) { Text("Usar", style = MaterialTheme.typography.labelMedium) }
                            }
                        }

                         if (walletAmount < minimumAmount) {
                            Text(
                                "Has acumulado " + wallet!!.walletValue +
                                    " de " + wallet!!.minimumRedeemValue,
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
                                "Te faltan " + formatWalletRemaining(remaining) + " para desbloquear tu saldo.",
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
                    wallet!!.pendingRewards.forEachIndexed { index, reward ->
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
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text(
                                    "Saldo de monedero · " + reward.amount.replace('.', ',') + " €",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF183B35)
                                )
                                Text(
                                    reward.code,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (applied) {
                                Text(
                                    "Aplicado",
                                    color = Color(0xFF0F5C4D),
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            } else {
                                TextButton(
                                    onClick = { applyReward(reward) },
                                    enabled = !loading,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) { Text("Aplicar") }
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
            TextButton(
                onClick = { if (pendingPoints != null) redeem(pendingPoints!!) else refresh() },
                enabled = !loading
            ) { Text(if (pendingPoints != null) "Reintentar" else "Reintentar carga") }
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
                val applied = code?.let { couponCode -> cartCouponCodes.any { it.equals(couponCode, ignoreCase = true) } } == true
                val isBirthday = promotion.type.equals("birthday_coupon", ignoreCase = true) || listOfNotNull(promotion.title, promotion.description).any { it.contains("cumpleaños", ignoreCase = true) || it.contains("birthday", ignoreCase = true) }
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp),
                    colors = CardDefaults.cardColors(containerColor = promotionCardColor(promotion, isBirthday))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        PromotionVisualIcon(promotion = promotion, isBirthday = isBirthday)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                if (isBirthday) "Tu regalo de cumpleaños" else promotion.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF183B35)
                            )
                            val description = if (isBirthday) "15% de descuento por tu cumpleaños." else promotion.description.trim()
                            if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodySmall, color = Color(0xFF40514D))
                            if (isBirthday) Text("Válido durante 15 días.", style = MaterialTheme.typography.labelSmall, color = Color(0xFF40514D))
                            promotion.expiresAt?.takeIf { it.isNotBlank() }?.let {
                                PromotionDateRow("Válido hasta " + formatPromotionExpiry(it))
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        code?.let { couponCode ->
                            Button(
                                onClick = { if (!applied) scope.launch { onApplyCoupon(couponCode) } },
                                enabled = !loading,
                                modifier = Modifier.width(78.dp).height(36.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(18.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F5C4D), contentColor = Color.White)
                            ) { Text(if (applied) "Aplicado" else "Aplicar", style = MaterialTheme.typography.labelMedium) }
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
        AlertDialog(
            onDismissRequest = { if (!loading) showRedeemDialog = false },
            title = { Text("¿Cuánto quieres utilizar?" ) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { option ->
                        Button(onClick = { showRedeemDialog = false; redeem(option.points) }, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
                            Text(if (option.isMaximum) "Máximo: " + option.value + " €" else option.value + " €")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showRedeemDialog = false }, enabled = !loading) { Text("Cancelar") } }
        )
    }
}

private fun promotionCardColor(promotion: Promotion, isBirthday: Boolean): Color = when (promotionVisualKind(promotion, isBirthday)) {
    PromotionVisualKind.BIRTHDAY -> Color(0xFFFDECEF)
    PromotionVisualKind.WELCOME -> Color(0xFFEAF4FC)
    PromotionVisualKind.DISCOUNT -> Color(0xFFFFF6DF)
}


private fun formatPromotionExpiry(value: String): String {
    val date = value.trim().take(10)
    val parts = date.split("-")
    return if (parts.size == 3 && parts[0].length == 4 && parts[1].length == 2 && parts[2].length == 2) {
        parts[2] + "/" + parts[1] + "/" + parts[0]
    } else value
}

@Composable
private fun IosInformationPageScreen(page: AccountInfoPage, onBack: () -> Unit) {
    val client = remember { WordPressPagesClient() }
    var retryKey by remember { mutableStateOf(0) }
    var loading by remember(page, retryKey) { mutableStateOf(true) }
    var result by remember(page, retryKey) { mutableStateOf<WordPressPage?>(null) }
    var notFound by remember(page, retryKey) { mutableStateOf(false) }
    var error by remember(page, retryKey) { mutableStateOf<String?>(null) }
    LaunchedEffect(page, retryKey) {
        loading = true; result = null; notFound = false; error = null
        runCatching { client.getPageBySlug(page.slug) }
            .onSuccess { loaded -> if (loaded == null) notFound = true else result = loaded; loading = false }
            .onFailure { error = "No se ha podido cargar esta información."; loading = false }
    }
    Column(Modifier.fillMaxSize()) {
        StorePageHeader(title = page.title, onBack = onBack)
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(24.dp))
        when {
            loading -> FullScreenLoading("Cargando información")
            notFound -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No se ha encontrado esta información.")
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { retryKey++ }) { Text("Reintentar") }
            }
            error != null -> Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = { retryKey++ }) { Text("Reintentar") }
            }
            result != null -> {
                val title = wordpressHtmlToText(result!!.title.rendered).ifBlank { page.title }
                val content = wordpressHtmlToText(result!!.content.rendered)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Text(title, style = MaterialTheme.typography.headlineSmall) }
                    item { if (content.isBlank()) Text("No hay contenido disponible.") else Text(content, style = MaterialTheme.typography.bodyLarge) }
                }
            }
        }

        }
    }
}

@Composable
private fun IosHelpScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        StorePageHeader(title = "Ayuda", onBack = onBack)
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(28.dp))
        Text("Contacta con nosotros", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Text("WhatsApp: 633 246 788")
        Text("Teléfono: 969 091 236")
        Text("Correo electrónico: criosrango@criosrango.es")
        Spacer(Modifier.height(20.dp))
        Text("Horario de atención", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        Text("Lunes a viernes: 10:00–13:30 · 17:30–21:00")
        Text("Sábado: 10:00–13:30")
        Text("Domingo: Cerrado")
        }
    }
}

@Composable
private fun IosLoginScreen(
    repository: AccountRepository,
    sessionState: StoreAccountSessionState,
    onAuthenticated: (AccountUser) -> Unit,
    onRegister: () -> Unit,
    onForgot: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    StoreLoginScreen(
        login = login,
        password = password,
        loading = busy,
        error = error,
        onLoginChanged = { login = it; error = null },
        onPasswordChanged = { password = it; error = null },
        onSubmit = { submittedLogin, submittedPassword ->
            if (submittedLogin.isBlank() || submittedPassword.isBlank()) {
                error = "Introduce tu correo y contraseña."
            } else if (!busy) {
                busy = true
                error = null
                scope.launch {
                    try {
                        val authenticatedUser = sessionState.login(submittedLogin.trim(), submittedPassword)
                        if (authenticatedUser != null) onAuthenticated(authenticatedUser)
                        else error = sessionState.snapshot.value.error ?: "No hemos podido iniciar sesión."
                    } finally {
                        busy = false
                    }
                }
            }
        },
        onForgotPassword = onForgot,
        onRegister = onRegister,
        onBack = onBack,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun IosRegisterScreen(
    repository: AccountRepository,
    onAuthenticated: (AccountUser) -> Unit,
    onLogin: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AccountForm("Crear cuenta", onBack) {
        OutlinedTextField(firstName, { firstName = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(lastName, { lastName = it }, label = { Text("Apellidos") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it }, label = { Text("Teléfono") }, modifier = Modifier.fillMaxWidth())
        BirthDatePickerField(birthDate, { birthDate = it }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
        OutlinedTextField(password, { password = it }, label = { Text("Contraseña") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            enabled = !busy && email.isNotBlank() && password.isNotBlank(),
            onClick = {
                busy = true; error = null
                scope.launch {
                    runCatching { repository.register(email, password, firstName, lastName, phone, birthDate) }
                        .onSuccess(onAuthenticated)
                        .onFailure { error = it.message ?: "No se ha podido crear la cuenta." }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Creando…" else "Crear cuenta") }
        TextButton(onClick = onLogin) { Text("Ya tengo una cuenta") }
    }
}

@Composable
private fun IosForgotPasswordScreen(repository: AccountRepository, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var login by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AccountForm("Recuperar contraseña", onBack) {
        OutlinedTextField(login, { login = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        message?.let { Text(it) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            enabled = !busy && login.isNotBlank(),
            onClick = {
                busy = true; message = null; error = null
                scope.launch {
                    runCatching { repository.forgotPassword(login) }
                        .onSuccess { message = it.ifBlank { "Revisa tu correo para continuar." } }
                        .onFailure { error = it.message ?: "No se ha podido solicitar la recuperación." }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Enviando…" else "Enviar recuperación") }
    }
}

@Composable
private fun IosProfileMenuScreen(
    onPersonalData: () -> Unit,
    onAddress: () -> Unit,
    onPassword: () -> Unit,
    onDeleteAccount: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            StorePageHeader(title = "Perfil", showBack = true, onBack = onBack, horizontalPadding = 0.dp)
            Spacer(Modifier.height(10.dp))
            IosProfileActionRow("Datos personales", "👤", onPersonalData)
            IosProfileActionRow("Dirección de entrega", "⌖", onAddress)
            IosProfileActionRow("Cambiar contraseña", "🔒", onPassword)
            IosPushPreferences()
            IosProfileActionRow(
                AccountDeletion.TITLE,
                "🗑",
                { showDeleteConfirmation = true },
                containerColor = AccountDeletion.background,
                contentColor = AccountDeletion.accent
            )
            IosProfileActionRow(
                "Cerrar sesión",
                "↪",
                onLogout,
                containerColor = Color(0xFFFFECEF),
                contentColor = Color(0xFFC73B4C)
            )
        }
    }
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(AccountDeletion.TITLE) },
            text = { Text(AccountDeletion.MESSAGE) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    onDeleteAccount()
                }) { Text(AccountDeletion.CONTINUE) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text(AccountDeletion.CANCEL) }
            }
        )
    }
}

@Composable
private fun IosProfileActionRow(
    title: String,
    icon: String,
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
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(icon, modifier = Modifier.width(28.dp), color = contentColor)
            Spacer(Modifier.width(14.dp))
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium, color = contentColor)
            Text("›", style = MaterialTheme.typography.titleLarge, color = contentColor)
        }
    }
}

@Composable
private fun IosProfileScreen(
    repository: AccountRepository,
    user: AccountUser?,
    onUserChanged: (AccountUser?) -> Unit,
    onSessionExpired: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var firstName by remember { mutableStateOf(user?.firstName.orEmpty()) }
    var lastName by remember { mutableStateOf(user?.lastName.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    AccountForm("Mi perfil", onBack) {
        Text("Email: " + user?.email.orEmpty())
        OutlinedTextField(
            formatBirthDateForDisplay(user?.birthDate).ifBlank { "No indicada" },
            {},
            label = { Text("Fecha de nacimiento") },
            readOnly = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(firstName, { firstName = it }, label = { Text("Nombre") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(lastName, { lastName = it }, label = { Text("Apellidos") }, modifier = Modifier.fillMaxWidth())
        notice?.let { Text(it) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            enabled = !busy,
            onClick = {
                busy = true; error = null; notice = null
                val cleanFirstName = firstName.trim()
                val cleanLastName = lastName.trim()
                if (cleanFirstName.isBlank() || cleanLastName.isBlank()) {
                    error = "Introduce tu nombre y apellidos."
                    busy = false
                    return@Button
                }
                scope.launch {
                    runCatching {
                        val current = repository.customerAddress()
                        repository.saveCustomerAddress(current.copy(firstName = cleanFirstName, lastName = cleanLastName))
                        repository.me()
                    }.onSuccess {
                        onUserChanged(it)
                        notice = "Datos actualizados"
                    }.onFailure {
                        if (!repository.hasSession) {
                            onSessionExpired()
                        } else {
                            error = it.message ?: "No se han podido guardar los datos."
                        }
                    }
                    busy = false
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(if (busy) "Guardando…" else "Guardar cambios") }
    }
}

@Composable
private fun IosAddressScreen(repository: AccountRepository, onSessionExpired: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var address by remember { mutableStateOf(AccountCustomerAddress()) }
    var loaded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { repository.customerAddress() }
            .onSuccess { address = it; error = null; loaded = true }
            .onFailure {
                if (!repository.hasSession) {
                    onSessionExpired()
                } else {
                    error = it.message ?: "No se ha podido cargar la dirección."
                }
                loaded = true
            }
    }
    AccountForm("Mi dirección", onBack) {
        if (!loaded) {
            FullScreenLoading("Cargando dirección")
        } else {
            AccountAddressFields(address) { address = it }
            notice?.let { Text(it) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !busy,
                onClick = {
                    busy = true; error = null; notice = null
                    scope.launch {
                        runCatching {
                            val clean = address.copy(firstName = address.firstName.trim(), lastName = address.lastName.trim(), address1 = address.address1.trim(), address2 = address.address2.trim(), postcode = address.postcode.trim(), city = address.city.trim(), state = address.state.trim(), country = "ES")
                            require(clean.firstName.isNotBlank() && clean.lastName.isNotBlank() && clean.address1.isNotBlank() && clean.postcode.isNotBlank() && clean.city.isNotBlank() && clean.state.isNotBlank()) { "Completa todos los campos obligatorios." }
                            require(clean.postcode.length == 5 && clean.postcode.all { it.isDigit() }) { "Introduce un código postal válido." }
                            require(SPANISH_PROVINCE_CODES.any { it.equals(clean.state, ignoreCase = true) }) { "Selecciona una provincia." }
                            repository.saveCustomerAddress(clean)
                        }
                            .onSuccess { notice = "Dirección actualizada" }
                            .onFailure {
                                if (!repository.hasSession) {
                                    onSessionExpired()
                                } else {
                                    error = it.message ?: "No se ha podido guardar la dirección."
                                }
                            }
                        busy = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (busy) "Guardando…" else "Guardar dirección") }
        }
    }
}

@Composable
private fun AccountAddressFields(address: AccountCustomerAddress, onChange: (AccountCustomerAddress) -> Unit) {
    val fields = listOf(
        "Nombre" to address.firstName, "Apellidos" to address.lastName, "Teléfono" to address.phone,
        "Dirección" to address.address1, "Dirección 2" to address.address2, "Código postal" to address.postcode,
        "Ciudad" to address.city, "Provincia" to address.state, "País" to address.country
    )
    fields.forEach { (label, value) ->
        OutlinedTextField(
            value, { newValue ->
                onChange(
                    when (label) {
                        "Nombre" -> address.copy(firstName = newValue)
                        "Apellidos" -> address.copy(lastName = newValue)
                        "Teléfono" -> address.copy(phone = newValue)
                        "Dirección" -> address.copy(address1 = newValue)
                        "Dirección 2" -> address.copy(address2 = newValue)
                        "Código postal" -> address.copy(postcode = newValue)
                        "Ciudad" -> address.copy(city = newValue)
                        "Provincia" -> address.copy(state = newValue)
                        "País" -> address.copy(country = newValue)
                        else -> address
                    }
                )
            },
            label = { Text(label) }, modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun AccountForm(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            TextButton(onClick = onBack) { Text("Atrás") }
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
private fun FullScreenLoading(text: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(text)
    }
}


private const val BIRTH_MILLIS_PER_DAY = 86_400_000L

fun birthDateToEpochMillis(value: String?): Long? {
    val raw = value?.trim().orEmpty()
    if (!raw.matches(Regex("""\d{4}-\d{2}-\d{2}"""))) return null
    val year = raw.substring(0, 4).toInt()
    val month = raw.substring(5, 7).toInt()
    val day = raw.substring(8, 10).toInt()
    if (month !in 1..12 || day !in 1..birthDaysInMonth(year, month)) return null
    return birthDaysFromCivil(year, month, day) * BIRTH_MILLIS_PER_DAY
}

fun epochMillisToBirthDate(value: Long?): String? {
    value ?: return null
    val days = birthFloorDiv(value, BIRTH_MILLIS_PER_DAY)
    val civil = birthCivilFromDays(days)
    return civil.year.toString().padStart(4, '0') + "-" +
        civil.month.toString().padStart(2, '0') + "-" +
        civil.day.toString().padStart(2, '0')
}

fun formatBirthDateForDisplay(value: String?): String =
    value?.takeIf { it.length == 10 && it[4] == '-' && it[7] == '-' }
        ?.let { it.substring(8, 10) + "/" + it.substring(5, 7) + "/" + it.substring(0, 4) }
        .orEmpty()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthDatePickerField(
    birthDate: String?,
    onBirthDateChanged: (String?) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = formatBirthDateForDisplay(birthDate),
        onValueChange = {},
        readOnly = true,
        enabled = enabled,
        singleLine = true,
        label = { Text("Fecha de nacimiento") },
        placeholder = { Text("DD/MM/YYYY") },
        modifier = modifier,
        supportingText = { Text("Úsala para recibir tu sorpresa de cumpleaños.") },
        trailingIcon = {
            TextButton(onClick = { open = true }, enabled = enabled) { Text("Elegir") }
        }
    )
    if (open) {
        val state = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = birthDateToEpochMillis(birthDate)
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedDateMillis != null,
                    onClick = {
                        onBirthDateChanged(epochMillisToBirthDate(state.selectedDateMillis))
                        open = false
                    }
                ) { Text("Aceptar") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onBirthDateChanged(null); open = false }) { Text("Borrar") }
                    TextButton(onClick = { open = false }) { Text("Cancelar") }
                }
            }
        ) {
            DatePicker(state = state, showModeToggle = false)
        }
    }
}

private fun birthDaysInMonth(year: Int, month: Int): Int = when (month) {
    2 -> if (birthIsLeapYear(year)) 29 else 28
    4, 6, 9, 11 -> 30
    else -> 31
}

private fun birthIsLeapYear(year: Int): Boolean =
    year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

private fun birthFloorDiv(value: Long, divisor: Long): Long {
    val quotient = value / divisor
    return if (value % divisor < 0) quotient - 1 else quotient
}

private data class BirthCivilDate(val year: Int, val month: Int, val day: Int)

private fun birthDaysFromCivil(year: Int, month: Int, day: Int): Long {
    var y = year.toLong()
    val m = month.toLong()
    val d = day.toLong()
    y -= if (m <= 2) 1 else 0
    val era = if (y >= 0) y / 400 else (y - 399) / 400
    val yoe = y - era * 400
    val mp = m + if (m > 2) -3 else 9
    val doy = (153 * mp + 2) / 5 + d - 1
    val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
    return era * 146097 + doe - 719468
}

private fun birthCivilFromDays(daysSinceEpoch: Long): BirthCivilDate {
    val z = daysSinceEpoch + 719468
    val era = if (z >= 0) z / 146097 else (z - 146096) / 146097
    val doe = z - era * 146097
    val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
    var y = yoe + era * 400
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val d = doy - (153 * mp + 2) / 5 + 1
    val m = mp + if (mp < 10) 3 else -9
    y += if (m <= 2) 1 else 0
    return BirthCivilDate(y.toInt(), m.toInt(), d.toInt())
}
private fun formatWalletRemaining(value: Double): String {
    val cents = kotlin.math.round(value * 100.0).toLong()
    val whole = cents / 100L
    val fraction = kotlin.math.abs(cents % 100L)
    return whole.toString() + "," + fraction.toString().padStart(2, '0')
}

