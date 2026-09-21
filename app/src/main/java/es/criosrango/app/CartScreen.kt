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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import compose.icons.TablerIcons
import compose.icons.tablericons.Bed
import compose.icons.tablericons.Hanger
import compose.icons.tablericons.Shirt
import compose.icons.tablericons.Tag
import kotlinx.coroutines.launch

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
    accountUserId: Int?,
    onLogin: () -> Unit,
    hasPendingCardPayment: Boolean,
    onContinuePendingPayment: () -> Unit,
    onCheckPendingPayment: () -> Unit
) {
    var clearCartConfirm by remember {
        mutableStateOf(false)
    }

    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                    Box(openModifier) { CatalogImage(item.images.firstOrNull()?.src, item.name.cleanWooText(), Modifier.size(78.dp).clip(RoundedCornerShape(10.dp))) }
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
            HorizontalDivider()
            Text("Subtotal: ${formatMinorUnits(cart.totals.consumerSubtotal(), cart.totals.currencyMinorUnit, cart.totals.currencySymbol)}", Modifier.padding(top = 8.dp))
            when {
                cart.totals.totalShipping == null -> Text("Envío: Se calcula en el checkout", color = Color.Gray)
                cart.totals.consumerShipping().toBigDecimalOrZero() == java.math.BigDecimal.ZERO -> Text("Envío: Gratis")
                else -> Text("Envío: ${formatMinorUnits(cart.totals.consumerShipping(), cart.totals.currencyMinorUnit, cart.totals.currencySymbol)}")
            }
            Text("Total: ${formatMinorUnits(cart.totals.totalPrice, cart.totals.currencyMinorUnit, cart.totals.currencySymbol)}", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (cart.totals.totalDiscount != "0") Text("Descuentos: -${formatMinorUnits(cart.totals.totalDiscount, cart.totals.currencyMinorUnit, cart.totals.currencySymbol)}", color = Color(0xFF183B35), style = MaterialTheme.typography.bodySmall)
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
            if (hasPendingCardPayment) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFFF4E5)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Hay un pago pendiente. Espera a que se confirme antes de iniciar una nueva compra.",
                            color = Color(0xFF7A4E00)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onContinuePendingPayment,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Continuar pago")
                            }
                            OutlinedButton(
                                onClick = onCheckPendingPayment,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Comprobar pago")
                            }
                        }
                    }
                }
            }
            Button(
                onClick = onCheckout,
                enabled = state == CartLoadState.SUCCESS_ITEMS && AccountCartCheckoutPolicy.canStartNewCheckout(hasPendingCardPayment),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF183B35))
            ) {
                Text("Finalizar compra")
            }
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
