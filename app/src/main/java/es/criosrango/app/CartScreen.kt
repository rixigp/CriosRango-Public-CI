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
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import es.criosrango.shared.loyalty.redeemableOptions
import es.criosrango.shared.promotions.Promotion
import es.criosrango.shared.promotions.PromotionRepository
import coil.compose.AsyncImage
import compose.icons.TablerIcons
import compose.icons.tablericons.Bed
import compose.icons.tablericons.Hanger
import compose.icons.tablericons.Shirt
import compose.icons.tablericons.Tag
import kotlinx.coroutines.launch

private fun cartPromotionTitle(promotion: Promotion): String {
    val key = listOfNotNull(promotion.code, promotion.title, promotion.description).joinToString(" ").lowercase()
    return when {
        key.contains("blackcrios") || key.contains("black friday") -> "Black Friday 20%"
        key.contains("bienvenida") || key.contains("welcome") -> "Promoción de bienvenida"
        key.contains("cumple") || key.contains("birthday") -> "Tu regalo de cumpleaños"
        else -> promotion.title
    }
}

private fun cartPromotionIcon(promotion: Promotion): String {
    val key = listOfNotNull(promotion.code, promotion.title, promotion.description).joinToString(" ").lowercase()
    return when {
        key.contains("blackcrios") || key.contains("black friday") -> "％"
        key.contains("bienvenida") || key.contains("welcome") -> "🏷"
        key.contains("cumple") || key.contains("birthday") -> "🎂"
        else -> "🏷"
    }
}

@Composable
private fun cartPromotionBackground(promotion: Promotion): Color {
    val key = listOfNotNull(promotion.code, promotion.title, promotion.description).joinToString(" ").lowercase()
    return when {
        key.contains("blackcrios") || key.contains("black friday") -> Color(0xFFFFF7D6)
        key.contains("bienvenida") || key.contains("welcome") -> Color(0xFFEAF3FF)
        key.contains("cumple") || key.contains("birthday") -> Color(0xFFFDECEF)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
}

private fun cartPromotionDate(value: String): String? {
    val date = value.substringBefore("T").substringBefore(" ")
    val parts = date.split("-")
    return if (parts.size == 3 && parts[0].length == 4) {
        parts[2].padStart(2, '0') + "/" + parts[1].padStart(2, '0') + "/" + parts[0]
    } else null
}

private fun cartFriendlyCouponName(label: String, code: String): String {
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
internal fun CartScreen(
    cart: WooCart,
    state: CartLoadState,
    error: String?,
    padding: PaddingValues,
    updateQuantity: (CartLine, Int) -> Unit,
    canIncrease: (CartLine) -> Boolean,
    increment: (CartLine) -> Int,
    removeLine: (CartLine) -> Unit,
    clearCart: () -> Unit,
    openLine: (CartLine) -> Unit,
    retry: () -> Unit,
    onCheckout: () -> Unit,
    couponLoading: Boolean,
    couponError: String?,
    applyCoupon: (String) -> Unit,
    removeCoupon: (String) -> Unit,
    loyaltyViewModel: LoyaltyViewModel,
    applyWalletCoupon: suspend (String) -> Boolean,
    accountUserId: Int?,
    onLogin: () -> Unit,
) {
    var clearCartConfirm by remember { mutableStateOf(false) }
    var couponExpanded by remember { mutableStateOf(false) }
    var couponCode by remember { mutableStateOf("") }
    val loyaltyWallet by loyaltyViewModel.wallet.collectAsStateWithLifecycle()
    val loyaltyLoading by loyaltyViewModel.loading.collectAsStateWithLifecycle()
    val loyaltyError by loyaltyViewModel.error.collectAsStateWithLifecycle()
    var showWalletDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val promotionRepository = remember {
        PromotionRepository(
            bearerTokenProvider = { AccountSessionStore.shared(context.applicationContext).load() }
        )
    }
    var promotions by remember { mutableStateOf(emptyList<Promotion>()) }
    var promotionsLoading by remember { mutableStateOf(true) }
    var promotionsError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(accountUserId) {
        promotionsLoading = true
        promotionsError = null
        runCatching { promotionRepository.getPromotions() }
            .onSuccess { promotions = it }
            .onFailure { promotionsError = it.message ?: "No se han podido cargar las promociones." }
        promotionsLoading = false
    }

    LaunchedEffect(accountUserId) {
        if (accountUserId != null) loyaltyViewModel.refresh()
    }

    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Tu carrito",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF183B35)
                )

                if (cart.items.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            clearCartConfirm = true
                        }
                    ) {
                        Icon(
                            Icons.Outlined.DeleteSweep,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Vaciar carrito")
                    }
                }
            }
        }
        if (state == CartLoadState.LOADING && cart.items.isEmpty()) item { CircularProgressIndicator(color = Color(0xFF183B35)) }
        if (!error.isNullOrBlank()) item { Row(verticalAlignment = Alignment.CenterVertically) { Text(error, color = Color(0xFFB3261E), modifier = Modifier.weight(1f)); TextButton(retry) { Text("Reintentar") } } }
        if (state == CartLoadState.ERROR && cart.items.isEmpty()) item { Text("No se ha podido recuperar el carrito.", color = Color.Gray) }
        if (state == CartLoadState.SUCCESS_EMPTY) item { Text("Tu carrito está vacío", color = Color.Gray) }
        items(cart.items, key = { it.key }) { item ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val openModifier = Modifier.clickable { openLine(item) }
                    BoxWithConstraints(openModifier.size(78.dp)) {
                        val targetWidthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
                        val image = item.images.firstOrNull()
                        CatalogImage(
                            selectResponsiveImageUrl(
                                src = image?.src.orEmpty(),
                                thumbnail = image?.thumbnail.orEmpty(),
                                srcSet = image?.srcSet.orEmpty(),
                                targetWidthPx = targetWidthPx,
                                preferThumbnailFallback = true
                            ),
                            item.name.cleanWooText(),
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(10.dp))
                        )
                    }
                    Column(openModifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(item.name.cleanWooText(), fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (item.variation.isNotEmpty()) Text(item.variation.joinToString(" · ") { "${it.attribute.removePrefix("pa_")}: ${it.value}" }, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Text("${formatMinorUnits(item.consumerUnitPrice(), item.prices.currencyMinorUnit, item.prices.currencySymbol)} / ud.", fontWeight = FontWeight.Bold, color = Color(0xFF183B35))
                        Text("Subtotal: ${formatMinorUnits(item.totals.consumerSubtotal(), item.prices.currencyMinorUnit, item.prices.currencySymbol)}", style = MaterialTheme.typography.bodySmall)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton({
                                if (item.quantity <= 1) {
                                    removeLine(item)
                                } else {
                                    updateQuantity(item, item.quantity - increment(item))
                                }
                            }) { Icon(Icons.Default.Remove, "Reducir") }
                            Text(item.quantity.toString())
                            IconButton({ updateQuantity(item, item.quantity + increment(item)) }, enabled = canIncrease(item)) { Icon(Icons.Default.Add, "Aumentar") }
                        }
                    }
                    IconButton({ removeLine(item) }) { Icon(Icons.Default.DeleteOutline, "Eliminar") }
                }
            }
        }
        if (cart.items.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Promociones y descuentos",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (accountUserId == null) {
                    TextButton(onClick = onLogin) { Text("Inicia sesión para usar tu Monedero") }
                } else if (loyaltyWallet != null) {
                    val wallet = loyaltyWallet!!
                    val eligibleSubtotal = es.criosrango.shared.loyalty.subtractMoneyAmounts(
                        cart.totals.consumerSubtotal(),
                        cart.totals.consumerDiscount()
                    )
                    val walletOptions = wallet.redeemableOptions(eligibleSubtotal)

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFE8F5EF)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("◉", color = Color(0xFF0F5C4D), fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Monedero", fontWeight = FontWeight.SemiBold, color = Color(0xFF0F5C4D))
                                Text(
                                    loyaltyDisplayMoney(wallet.walletValue) + " disponibles",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF315B52)
                                )
                            }
                            if (walletOptions.isNotEmpty()) {
                                Button(
                                    onClick = { showWalletDialog = true },
                                    enabled = !loyaltyLoading,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 5.dp),
                                    modifier = Modifier.heightIn(min = 36.dp)
                                ) { Text("Aplicar") }
                            }
                        }
                    }

                    if (wallet.pendingRewards.isNotEmpty()) {
                        Text(
                            "Créditos listos para usar",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            wallet.pendingRewards.forEach { reward ->
                                val applied = cart.coupons.any { it.code.equals(reward.code, ignoreCase = true) }
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    tonalElevation = 1.dp
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Crédito listo para usar", fontWeight = FontWeight.Medium)
                                            Text(loyaltyDisplayMoney(reward.amount), style = MaterialTheme.typography.bodySmall)
                                        }
                                        if (applied) {
                                            Text("Aplicado", color = Color(0xFF0F5C4D), fontWeight = FontWeight.SemiBold)
                                        } else {
                                            TextButton(
                                                onClick = { loyaltyViewModel.applyPending(reward, applyWalletCoupon) },
                                                enabled = !loyaltyLoading
                                            ) { Text("Aplicar") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                when {
                    promotionsLoading -> CircularProgressIndicator(Modifier.size(20.dp))
                    !promotionsError.isNullOrBlank() -> Text(
                        promotionsError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                    promotions.isEmpty() -> Text(
                        "No hay promociones disponibles.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall
                    )
                    else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Promociones disponibles",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        promotions.sortedByDescending { it.priority }.forEach { promotion ->
                            val code = promotion.code?.trim().orEmpty()
                            val applied = code.isNotBlank() && cart.coupons.any { it.code.equals(code, ignoreCase = true) }
                            val title = cartPromotionTitle(promotion)
                            val description = promotion.description.trim()
                            val background = cartPromotionBackground(promotion)
                            if (!applied) {
                            Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = background
                                ) {
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Text(cartPromotionIcon(promotion), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 1.dp))
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(title, fontWeight = FontWeight.SemiBold)
                                            if (description.isNotBlank()) {
                                                Text(description, style = MaterialTheme.typography.bodySmall)
                                            }
                                            promotion.expiresAt?.let { expires ->
                                                cartPromotionDate(expires)?.let { date ->
                                                    Text(
                                                        "Válido hasta $date",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = Color(0xFF5F6368)
                                                    )
                                                }
                                            }
                                        }
                                        if (code.isNotBlank()) {
                                            TextButton(
                                                onClick = { applyCoupon(code) },
                                                enabled = !applied && !couponLoading
                                            ) {
                                                Text(if (applied) "Aplicado" else "Aplicar")
                                            }
                                        }
                                    }
                            }
                            }
                        }
                    }
                }

                if (!couponExpanded) {
                    TextButton(
                        onClick = { couponExpanded = true },
                        enabled = !couponLoading
                    ) { Text("Tengo otro código de descuento") }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = couponCode,
                            onValueChange = { couponCode = it },
                            label = { Text("Código") },
                            singleLine = true,
                            enabled = !couponLoading,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { applyCoupon(couponCode.trim()) },
                            enabled = couponCode.trim().isNotEmpty() && !couponLoading
                        ) {
                            if (couponLoading) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Aplicar")
                            }
                        }
                    }
                    couponError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (cart.coupons.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            "Descuentos aplicados",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        cart.coupons.forEach { coupon ->
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    cartFriendlyCouponName(coupon.label, coupon.code),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "-" + formatMinorUnits(
                                        coupon.totals.consumerDiscount(),
                                        cart.totals.currencyMinorUnit,
                                        cart.totals.currencySymbol
                                    ),
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0F5C4D)
                                )
                                TextButton(
                                    onClick = { removeCoupon(coupon.code) },
                                    enabled = !couponLoading
                                ) { Text("Quitar") }
                            }
                        }
                    }
                }

                loyaltyError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (cart.items.isNotEmpty()) item {
            HorizontalDivider()
            Text("Subtotal: " + formatMinorUnits(cart.totals.consumerSubtotal(), cart.totals.currencyMinorUnit, cart.totals.currencySymbol), Modifier.padding(top = 8.dp))
            val discount = cart.totals.consumerDiscount()
            if (discount.toLongOrNull()?.let { it > 0L } == true) Text("Descuentos: -" + formatMinorUnits(discount, cart.totals.currencyMinorUnit, cart.totals.currencySymbol), color = Color(0xFF183B35), style = MaterialTheme.typography.bodySmall)
            when {
                cart.totals.totalShipping == null -> Text("Envío: Se calcula en el checkout", color = Color.Gray)
                cart.totals.consumerShipping().toBigDecimalOrZero() == java.math.BigDecimal.ZERO -> Text("Envío: Gratis")
                else -> Text("Envío: ${formatMinorUnits(cart.totals.consumerShipping(), cart.totals.currencyMinorUnit, cart.totals.currencySymbol)}")
            }
            Text("Total: " + formatMinorUnits(cart.totals.totalPrice, cart.totals.currencyMinorUnit, cart.totals.currencySymbol), Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (AccountCartCheckoutPolicy.showGuestLoginCta(accountUserId, cart.items.size)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "¿Ya tienes cuenta? Inicia sesión para acceder a tus datos y pedidos.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = onLogin,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Iniciar sesión")
                    }
                }
            }
            Button(
                onClick = onCheckout,
                enabled = state == CartLoadState.SUCCESS_ITEMS,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF183B35))
            ) {
                Text("Finalizar compra")
            }
        }
    }

    if (showWalletDialog && loyaltyWallet != null) {
        val eligibleSubtotal = es.criosrango.shared.loyalty.subtractMoneyAmounts(
            cart.totals.consumerSubtotal(),
            cart.totals.consumerDiscount()
        )
        val wallet = loyaltyWallet!!
        val walletOptions = wallet.redeemableOptions(eligibleSubtotal)
        if (walletOptions.isNotEmpty()) {
            LoyaltyRedeemDialog(
                options = walletOptions,
                loading = loyaltyLoading,
                onDismiss = { showWalletDialog = false },
                onSelect = { option ->
                    showWalletDialog = false
                    loyaltyViewModel.redeem(option.points, applyWalletCoupon)
                }
            )
        }
    }

    if (clearCartConfirm) {
        AlertDialog(
            onDismissRequest = {
                clearCartConfirm = false
            },
            title = {
                Text("Vaciar carrito")
            },
            text = {
                Text("¿Quieres eliminar todos los productos del carrito?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clearCartConfirm = false
                        clearCart()
                    }
                ) {
                    Text("Vaciar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        clearCartConfirm = false
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}
