package es.criosrango.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.criosrango.shared.loyalty.LoyaltyReward
import es.criosrango.shared.loyalty.LoyaltyWallet
import es.criosrango.shared.loyalty.parseMoneyMinorUnits
import es.criosrango.shared.loyalty.redeemableOptions
import es.criosrango.shared.loyalty.subtractMoneyAmounts
import es.criosrango.shared.promotions.Promotion
import es.criosrango.shared.promotions.cartVisiblePromotions
import kotlinx.coroutines.launch

private val WalletGreen = Color(0xFF0F5C4D)
private val WalletDark = Color(0xFF183B35)
private val WalletSoftGreen = Color(0xFFEAF6F0)
private val WalletBody = Color(0xFF40514D)

fun formatWalletMoney(value: String): String {
    val minor = parseMoneyMinorUnits(value)
    val sign = if (minor < 0L) "-" else ""
    val absolute = kotlin.math.abs(minor)
    return sign + (absolute / 100L).toString() + "," + (absolute % 100L).toString().padStart(2, '0') + " €"
}

fun walletProgressLabel(wallet: LoyaltyWallet): String =
    "Has acumulado " + formatWalletMoney(wallet.walletValue) + " de " + formatWalletMoney(wallet.minimumRedeemValue)

fun walletRemainingLabel(wallet: LoyaltyWallet): String =
    "Te faltan " + formatWalletMoney(subtractMoneyAmounts(wallet.minimumRedeemValue, wallet.walletValue)) + " para desbloquear tu saldo."

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
    var showRedeemDialog by remember { mutableStateOf(false) }
    var showWalletInfo by remember { mutableStateOf(false) }
    val options = state.wallet?.redeemableOptions().orEmpty()

    LaunchedEffect(owner, sessionId) {
        owner.onSessionChanged(sessionId)
        if (sessionId != null) owner.refresh()
    }
    LaunchedEffect(owner, cartCouponCodes) { owner.setAppliedCouponCodes(cartCouponCodes) }

    Column(Modifier.fillMaxWidth()) {
        StorePageHeader(title = "Cupones y promociones", onBack = onBack, backEnabled = !state.busy)
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when {
                authenticated && state.walletLoading && state.wallet == null ->
                    CircularProgressIndicator(Modifier.padding(vertical = 12.dp))
                authenticated && state.walletError != null && state.wallet == null ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(state.walletError.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = owner::refresh) { Text("Reintentar") }
                    }
                authenticated && state.wallet != null -> WalletBalanceCard(
                    wallet = state.wallet!!,
                    loading = state.busy,
                    canRedeem = options.isNotEmpty(),
                    onInfo = { showWalletInfo = true },
                    onRedeem = { showRedeemDialog = true }
                )
            }

            if (authenticated && !state.wallet?.pendingRewards.isNullOrEmpty()) {
                Text("Saldo listo para usar", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Card(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F0F8))
                ) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp)) {
                        state.wallet?.pendingRewards.orEmpty().forEachIndexed { index, reward ->
                            val applied = state.appliedCouponCodes.any { it.equals(reward.code.trim(), ignoreCase = true) }
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = "Saldo de monedero", tint = WalletGreen, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                    Text("Saldo de monedero · " + formatWalletMoney(reward.amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = WalletDark)
                                    Text(reward.code, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (applied) AppliedWalletLabel()
                                else Button(
                                    onClick = { owner.applyReward(reward, onApplyCoupon) },
                                    enabled = !state.busy,
                                    shape = RoundedCornerShape(18.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = WalletGreen, contentColor = Color.White),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                ) { Text("Aplicar", style = MaterialTheme.typography.labelMedium) }
                            }
                            if (index < state.wallet!!.pendingRewards.lastIndex) HorizontalDivider(Modifier.padding(start = 30.dp), color = Color(0xFFDCD8E8))
                        }
                    }
                }
            }

            state.operationError?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (state.pendingRedemptionPoints != null) {
                    TextButton(
                        onClick = { owner.redeem(state.pendingRedemptionPoints!!, onApplyCoupon) },
                        enabled = !state.busy
                    ) { Text("Reintentar canje") }
                }
            }

            Text(
                "Promociones disponibles (" + cartVisiblePromotions(state.promotions, authenticated).size + ")",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            val visiblePromotions = cartVisiblePromotions(state.promotions, authenticated)
            when {
                state.promotionsLoading && visiblePromotions.isEmpty() -> CircularProgressIndicator(Modifier.padding(vertical = 8.dp))
                state.promotionsError != null && visiblePromotions.isEmpty() -> Column {
                    Text(state.promotionsError.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = owner::refresh) { Text("Reintentar") }
                }
                visiblePromotions.isEmpty() -> Text("No hay promociones disponibles.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> visiblePromotions.forEach { promotion ->
                    WalletPromotionCard(
                        promotion = promotion,
                        appliedCodes = state.appliedCouponCodes,
                        busy = state.busy,
                        onApply = { code -> owner.applyPromotion(code, onApplyCoupon) }
                    )
                }
            }
            if (state.walletLoading && state.wallet != null) CircularProgressIndicator(Modifier.padding(vertical = 4.dp).size(18.dp))
        }
    }

    if (showWalletInfo) {
        AlertDialog(
            onDismissRequest = { showWalletInfo = false },
            title = { Text("¿Cómo funciona tu monedero?", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "1 € gastado = 1 punto.\n\n" +
                        "Cuando acumules los puntos mínimos, podrás convertirlos en saldo para tus compras.\n\n" +
                        "La barra indica cuánto te falta para desbloquear el saldo. Cuando tengas saldo disponible, pulsa “Usar” para elegir cuánto quieres aplicar."
                )
            },
            confirmButton = { Button(onClick = { showWalletInfo = false }) { Text("Aceptar") } }
        )
    }

    if (showRedeemDialog) {
        AlertDialog(
            onDismissRequest = { if (!state.busy) showRedeemDialog = false },
            title = { Text("¿Cuánto quieres utilizar?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { option ->
                        Button(
                            onClick = {
                                showRedeemDialog = false
                                owner.redeem(option.points, onApplyCoupon)
                            },
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = WalletGreen, contentColor = Color.White)
                        ) {
                            Text(if (option.isMaximum) "Máximo: " + formatWalletMoney(option.value) else formatWalletMoney(option.value))
                        }
                    }
                    if (state.redeemLoading) CircularProgressIndicator(Modifier.padding(top = 4.dp))
                }
            },
            confirmButton = { TextButton(onClick = { showRedeemDialog = false }, enabled = !state.busy) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun WalletBalanceCard(
    wallet: LoyaltyWallet,
    loading: Boolean,
    canRedeem: Boolean,
    onInfo: () -> Unit,
    onRedeem: () -> Unit
) {
    val walletMinor = parseMoneyMinorUnits(wallet.walletValue)
    val minimumMinor = parseMoneyMinorUnits(wallet.minimumRedeemValue)
    val progress = if (minimumMinor > 0L) (walletMinor.toDouble() / minimumMinor.toDouble()).coerceIn(0.0, 1.0).toFloat() else 0f
    val reached = walletMinor >= minimumMinor
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = WalletSoftGreen)
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(end = 34.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, tint = WalletGreen, modifier = Modifier.size(36.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("Monedero", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = WalletDark, maxLines = 1)
                        Text(
                            formatWalletMoney(wallet.walletValue) + if (reached) " disponibles" else " acumulados",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = WalletGreen,
                            maxLines = 1
                        )
                    }
                    if (reached && canRedeem) Button(
                        onClick = onRedeem,
                        enabled = !loading,
                        modifier = Modifier.width(76.dp).height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WalletGreen, contentColor = Color.White)
                    ) { Text("Usar", style = MaterialTheme.typography.labelMedium) }
                }
                if (!reached) {
                    Text(walletProgressLabel(wallet), style = MaterialTheme.typography.bodySmall, color = WalletBody)
                    LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth().height(6.dp), color = WalletGreen, trackColor = Color(0xFFD7E8E1))
                    Text(walletRemainingLabel(wallet), style = MaterialTheme.typography.bodySmall, color = WalletBody)
                }
            }
            IconButton(onClick = onInfo, modifier = Modifier.align(Alignment.TopEnd).size(32.dp)) {
                Icon(Icons.Outlined.Info, contentDescription = "Cómo funciona el monedero", tint = WalletGreen, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun WalletPromotionCard(
    promotion: Promotion,
    appliedCodes: Set<String>,
    busy: Boolean,
    onApply: (String) -> Unit
) {
    val code = promotion.code?.takeIf { it.isNotBlank() }
    val applied = code?.let { target -> appliedCodes.any { it.equals(target.trim(), ignoreCase = true) } } == true
    val isBirthday = promotion.type.equals("birthday_coupon", ignoreCase = true) ||
        listOfNotNull(promotion.title, promotion.description).any {
            it.contains("cumpleaños", ignoreCase = true) || it.contains("birthday", ignoreCase = true)
        }
    val background = if (isBirthday) Color(0xFFFFF0F2) else Color(0xFFFFF8E8)
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = background)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
            Icon(
                if (isBirthday) Icons.Outlined.Cake else Icons.Outlined.LocalOffer,
                contentDescription = null,
                tint = if (isBirthday) Color(0xFFD96A86) else Color(0xFFD49A00),
                modifier = Modifier.padding(top = 1.dp).size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (isBirthday) "Tu regalo de cumpleaños" else promotion.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = WalletDark)
                val description = if (isBirthday) "15% de descuento por tu cumpleaños." else promotion.description.trim()
                if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodySmall, color = WalletBody)
                if (isBirthday) Text("Válido durante 15 días.", style = MaterialTheme.typography.labelSmall, color = WalletBody)
                promotion.expiresAt?.takeIf { it.isNotBlank() }?.let {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Outlined.CalendarToday, contentDescription = null, tint = Color(0xFF7A8884), modifier = Modifier.size(13.dp))
                        Text("Válido hasta " + formatWalletExpiry(it), style = MaterialTheme.typography.labelSmall, color = Color(0xFF687773))
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            if (code != null) {
                if (applied) AppliedWalletLabel()
                else Button(
                    onClick = { onApply(code) },
                    enabled = !busy,
                    modifier = Modifier.width(78.dp).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WalletGreen, contentColor = Color.White)
                ) { Text("Aplicar", style = MaterialTheme.typography.labelMedium) }
            }
        }
    }
}

@Composable
private fun AppliedWalletLabel() {
    Surface(shape = RoundedCornerShape(50), color = Color(0xFFE8F5EF)) {
        Text("Aplicado", modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = WalletGreen, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelSmall)
    }
}

private fun formatWalletExpiry(value: String): String {
    val date = value.substringBefore('T').substringBefore(' ')
    val parts = date.split('-')
    return if (parts.size == 3 && parts[0].length == 4) parts[2] + "/" + parts[1] + "/" + parts[0] else date
}
