package es.criosrango.app

import es.criosrango.shared.friendlyAppliedCouponName

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
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
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
import kotlinx.coroutines.CancellationException
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

private fun buildLineIcon(name: String, content: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name=name, defaultWidth=24.dp, defaultHeight=24.dp, viewportWidth=24f, viewportHeight=24f).apply {
        path(fill=null, stroke=SolidColor(Color.Black), strokeLineWidth=1.8f, strokeLineCap=androidx.compose.ui.graphics.StrokeCap.Round, strokeLineJoin=androidx.compose.ui.graphics.StrokeJoin.Round, pathBuilder=content)
    }.build()

@Composable
private fun CartApplyButton(onClick: () -> Unit, enabled: Boolean = true, loading: Boolean = false, modifier: Modifier = Modifier.height(36.dp)) = Button(onClick=onClick, enabled=enabled, shape=RoundedCornerShape(50), colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF0F5C4D), contentColor=Color.White), contentPadding=PaddingValues(horizontal=12.dp, vertical=0.dp), modifier=modifier) { if (loading) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth=2.dp, color=Color.White) else Text("Aplicar", style=MaterialTheme.typography.labelMedium) }
private fun cartIsWalletCoupon(code: String): Boolean = code.trim().startsWith("CR-MONEDERO-", ignoreCase=true)
private val CartWalletLineIcon = buildLineIcon("CartWalletLineIcon") { moveTo(3.5f,6.5f); lineTo(18.5f,6.5f); lineTo(20.5f,8.5f); lineTo(20.5f,18f); lineTo(3.5f,18f); close(); moveTo(3.5f,6.5f); lineTo(3.5f,5f); lineTo(17f,5f); moveTo(15.5f,12.5f); lineTo(20.5f,12.5f); moveTo(17.5f,12.5f); lineTo(17.5f,12.5f) }
private val CartDiscountLineIcon = buildLineIcon("CartDiscountLineIcon") { moveTo(4f,7f); lineTo(20f,7f); lineTo(20f,17f); lineTo(4f,17f); close(); moveTo(8f,12f); lineTo(16f,12f) }
private val CartTagLineIcon = buildLineIcon("CartTagLineIcon") { moveTo(3.5f,11f); lineTo(11f,3.5f); lineTo(20f,12.5f); lineTo(12.5f,20f); close(); moveTo(8f,8f); lineTo(8f,8f) }
private val CartCakeLineIcon = buildLineIcon("CartCakeLineIcon") { moveTo(4f,10f); lineTo(20f,10f); lineTo(20f,19f); lineTo(4f,19f); close(); moveTo(4f,14f); lineTo(20f,14f); moveTo(8f,10f); lineTo(8f,7f); moveTo(12f,10f); lineTo(12f,6f); moveTo(16f,10f); lineTo(16f,7f) }
private val CartCloseLineIcon = buildLineIcon("CartCloseLineIcon") { moveTo(7f,7f); lineTo(17f,17f); moveTo(17f,7f); lineTo(7f,17f) }
@Composable private fun CartAppliedCouponIcon(kind:String) { val icon=when(kind){"wallet"->CartWalletLineIcon;"credit"->CartWalletLineIcon;"discount"->CartDiscountLineIcon;"cake"->CartCakeLineIcon;else->CartTagLineIcon}; Icon(icon,null,tint=Color(0xFF0F5C4D),modifier=Modifier.size(20.dp)) }

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
        try {
            promotions = promotionRepository.getPromotions()
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            promotionsError = exception.message ?: "No se han podido cargar las promociones."
        } finally {
            promotionsLoading = false
        }
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
                    val walletApplied = cart.coupons.any { cartIsWalletCoupon(it.code) }

                    if (!walletApplied) Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFE8F5EF)
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(CartWalletLineIcon, null, tint=Color(0xFF0F5C4D), modifier=Modifier.size(28.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Monedero", fontWeight = FontWeight.SemiBold, color = Color(0xFF0F5C4D))
                                Text(
                                    loyaltyDisplayMoney(wallet.walletValue) + " disponibles",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF315B52)
                                )
                            }
                            if (walletOptions.isNotEmpty()) CartApplyButton({ showWalletDialog = true }, enabled=!loyaltyLoading)
                        }
                    }

                    if (wallet.pendingRewards.any { reward ->
                        cart.coupons.none { it.code.equals(reward.code, ignoreCase = true) }
                    }) {
                        Text(
                            "Saldo listo para usar",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Surface(Modifier.fillMaxWidth(), shape=RoundedCornerShape(14.dp), color=Color(0xFFF4F0F5)) {
                            Column(Modifier.fillMaxWidth().padding(horizontal=12.dp, vertical=8.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            wallet.pendingRewards.forEach { reward ->
                                val applied = cart.coupons.any { it.code.equals(reward.code, ignoreCase = true) }
                                if (!applied) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(CartWalletLineIcon, null, tint=Color(0xFF0F5C4D), modifier=Modifier.size(22.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "Saldo de monedero · " + loyaltyDisplayMoney(reward.amount),
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
                                        CartApplyButton({ loyaltyViewModel.applyPending(reward, applyWalletCoupon) }, enabled=!loyaltyLoading)
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
                    else -> {
                        val hasAvailablePromotions = promotions.any { promotion ->
                            val code = promotion.code?.trim().orEmpty()
                            code.isBlank() || cart.coupons.none { it.code.equals(code, ignoreCase = true) }
                        }
                        if (hasAvailablePromotions) Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                                        verticalAlignment = Alignment.CenterVertically
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
                                            CartApplyButton({ applyCoupon(code) }, enabled=!applied && !couponLoading)
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                        CartApplyButton({ applyCoupon(couponCode.trim()) }, enabled=couponCode.trim().isNotEmpty() && !couponLoading, loading=couponLoading, modifier=Modifier.height(54.dp))
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
                                    val appliedAmount = coupon.totals.consumerDiscount()
                                    val walletAmount = appliedAmount.toLongOrNull()?.takeIf { it > 0L }
                                        ?.let { formatMinorUnits(appliedAmount, cart.totals.currencyMinorUnit, cart.totals.currencySymbol) }
                                    val name = friendlyAppliedCouponName(coupon.label, coupon.code, walletAmount)
                                    val key = "${coupon.label} ${coupon.code}".lowercase()
                                    val iconKind = when { cartIsWalletCoupon(coupon.code) -> "wallet"; isCredit -> "credit"; key.contains("blackcrios") || key.contains("black friday") -> "discount"; key.contains("cumple") || key.contains("birthday") -> "cake"; else -> "tag" }
                                    Row(Modifier.fillMaxWidth().heightIn(min=36.dp).padding(vertical=2.dp), verticalAlignment=Alignment.CenterVertically) {
                                        CartAppliedCouponIcon(iconKind)
                                        Spacer(Modifier.width(8.dp))
                                        Text(name, fontWeight=FontWeight.Medium, style=MaterialTheme.typography.bodySmall, modifier=Modifier.weight(1f, fill=true), maxLines=1, softWrap=false, overflow=TextOverflow.Ellipsis)
                                        Text("-" + formatMinorUnits(coupon.totals.consumerDiscount(), cart.totals.currencyMinorUnit, cart.totals.currencySymbol), fontWeight=FontWeight.SemiBold, color=Color(0xFF0F5C4D), modifier=Modifier.padding(start=4.dp))
                                        IconButton({ removeCoupon(coupon.code) }, enabled=!couponLoading, modifier=Modifier.size(32.dp)) { Icon(CartCloseLineIcon, "Quitar", tint=Color(0xFF5F6368), modifier=Modifier.size(18.dp)) }
                                    }
                                    if (index < cart.coupons.lastIndex) HorizontalDivider(Modifier.padding(start=28.dp), color=Color(0xFFE8E8E3), thickness=1.dp)
                                }
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
            HorizontalDivider(color = Color(0xFFE8E8E3))
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Subtotal", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(formatMinorUnits(cart.totals.consumerSubtotal(), cart.totals.currencyMinorUnit, cart.totals.currencySymbol), fontWeight = FontWeight.Medium)
                }
                val discount = cart.totals.consumerDiscount()
                if (discount.toLongOrNull()?.let { it > 0L } == true) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Descuentos", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Color(0xFF315B52))
                        Text("-" + formatMinorUnits(discount, cart.totals.currencyMinorUnit, cart.totals.currencySymbol), style = MaterialTheme.typography.bodySmall, color = Color(0xFF0F5C4D), fontWeight = FontWeight.Medium)
                    }
                }
                when {
                    cart.totals.totalShipping == null -> Row(Modifier.fillMaxWidth()) {
                        Text("Envío", modifier = Modifier.weight(1f), color = Color.Gray)
                        Text("Se calcula en el checkout", color = Color.Gray)
                    }
                    cart.totals.consumerShipping().toBigDecimalOrZero() == java.math.BigDecimal.ZERO -> Row(Modifier.fillMaxWidth()) {
                        Text("Envío", modifier = Modifier.weight(1f))
                        Text("Gratis")
                    }
                    else -> Row(Modifier.fillMaxWidth()) {
                        Text("Envío", modifier = Modifier.weight(1f))
                        Text(formatMinorUnits(cart.totals.consumerShipping(), cart.totals.currencyMinorUnit, cart.totals.currencySymbol))
                    }
                }
                HorizontalDivider(color = Color(0xFFE8E8E3), modifier = Modifier.padding(vertical = 3.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Total", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(formatMinorUnits(cart.totals.totalPrice, cart.totals.currencyMinorUnit, cart.totals.currencySymbol), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
            }
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
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(48.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF183B35))
            ) {
                Text("Continuar compra")
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
