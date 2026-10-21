package es.criosrango.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.criosrango.shared.loyalty.LoyaltyReward
import es.criosrango.shared.loyalty.LoyaltyWallet
import es.criosrango.shared.loyalty.redeemableOptions
import es.criosrango.shared.promotions.Promotion
import es.criosrango.shared.promotions.isVisibleToAnonymous
import kotlinx.coroutines.launch

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
fun StoreWalletScreen(
    owner: StoreWalletOwner,
    authenticated: Boolean,
    sessionId: String?,
    cartCouponCodes: Set<String>,
    onApplyCoupon: suspend (String) -> CouponApplicationResult,
    onBack: () -> Unit
) {
    val state by owner.state.collectAsState()
    val wallet = state.wallet
    val loading = state.walletLoading || state.redeemLoading || state.couponApplyLoading
    val error = state.operationError ?: state.walletError
    val promotions = state.promotions
    val promotionsLoading = state.promotionsLoading
    val promotionsError = state.promotionsError
    val onApplyPromotion: suspend (String) -> Unit = { code -> owner.applyPromotion(code, onApplyCoupon) }
    val onRefresh: () -> Unit = { owner.refresh() }
    val onApplyReward: (LoyaltyReward) -> Unit = { reward -> owner.applyReward(reward, onApplyCoupon) }
    val onRedeem: (Int) -> Unit = { points -> owner.redeem(points, onApplyCoupon) }
    LaunchedEffect(owner, sessionId) {
        owner.onSessionChanged(sessionId)
        if (sessionId != null) owner.refresh()
    }
    LaunchedEffect(owner, cartCouponCodes) { owner.setAppliedCouponCodes(cartCouponCodes) }

    var showRedeemDialog by remember { mutableStateOf(false) }
    var showWalletInfo by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val options = wallet?.redeemableOptions() ?: emptyList()
    val pendingRewards = (wallet?.pendingRewards.orEmpty() + listOfNotNull(state.pendingReward))
        .distinctBy { it.code.trim().lowercase() }

    Column(Modifier.fillMaxWidth()) {
        StorePageHeader(title = "Cupones y promociones", onBack = onBack, backEnabled = !loading)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).offset(y = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

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
                        val walletMinor = es.criosrango.shared.loyalty.parseMoneyMinorUnits(wallet.walletValue)
                        val minimumMinor = es.criosrango.shared.loyalty.parseMoneyMinorUnits(wallet.minimumRedeemValue)
                        // Double is used only for the visual progress ratio, never for displayed money.
                        val progress = if (minimumMinor > 0L) {
                            (walletMinor.toDouble() / minimumMinor.toDouble()).coerceIn(0.0, 1.0)
                        } else 0.0

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
                                    formatWalletMoney(wallet.walletValue) + if (walletMinor >= minimumMinor) " disponibles" else " acumulados",
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

                         if (walletMinor < minimumMinor) {
                            Text(
                                walletProgressLabel(wallet),
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
                                walletRemainingLabel(wallet),
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

        if (authenticated && pendingRewards.isNotEmpty()) {
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
                    pendingRewards.forEachIndexed { index, reward ->
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
                                "Saldo de monedero · " + formatWalletMoney(reward.amount),
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
                        if (index < pendingRewards.lastIndex) {
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
            title = {
                Text(
                    "¿Cuánto quieres utilizar?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { option ->
                        Button(
                            onClick = {
                                showRedeemDialog = false
                                onRedeem(option.points)
                            },
                            enabled = !loading,
                            modifier = Modifier.fillMaxWidth().height(40.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F5C4D), contentColor = Color.White)
                        ) {
                            Text(
                                if (option.isMaximum) "Máximo: " + formatWalletMoney(option.value)
                                else formatWalletMoney(option.value),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                    if (loading) CircularProgressIndicator(Modifier.padding(top = 4.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = { showRedeemDialog = false }, enabled = !loading) {
                    Text("Cancelar")
                }
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



fun formatWalletMoney(value: String): String {
    val minor = es.criosrango.shared.loyalty.parseMoneyMinorUnits(value)
    val sign = if (minor < 0L) "-" else ""
    val absolute = kotlin.math.abs(minor)
    return sign + (absolute / 100L).toString() + "," + (absolute % 100L).toString().padStart(2, '0') + " €"
}


fun walletProgressLabel(wallet: LoyaltyWallet): String =
    "Has acumulado " + formatWalletMoney(wallet.walletValue) + " de " + formatWalletMoney(wallet.minimumRedeemValue)

fun walletRemainingLabel(wallet: LoyaltyWallet): String =
    "Te faltan " + formatWalletMoney(
        es.criosrango.shared.loyalty.subtractMoneyAmounts(wallet.minimumRedeemValue, wallet.walletValue)
    ) + " para desbloquear tu saldo.
