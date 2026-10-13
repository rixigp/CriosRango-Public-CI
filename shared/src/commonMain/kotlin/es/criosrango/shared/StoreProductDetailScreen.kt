package es.criosrango.shared

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun StoreProductDetailScreen(
    state: StoreProductDetailUiState,
    modifier: Modifier = Modifier,
    showStoreTopBar: Boolean = true,
    cartQuantity: Int = 0,
    onSearch: () -> Unit = {},
    onCart: () -> Unit = {},
    imageCount: Int,
    imageSlot: @Composable (Int, Modifier) -> Unit,
    fullscreenImageSlot: @Composable (Int, Modifier) -> Unit = imageSlot,
    galleryKey: Any? = state.title,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    onRetry: () -> Unit = {},
    onBack: () -> Unit,
    onShare: () -> Unit,
    onReserve: () -> Unit,
    onAttributeOptionSelected: (String, String) -> Unit,
    onQuantityMinus: () -> Unit,
    onQuantityPlus: () -> Unit,
    onAddToCart: () -> Unit
) {
    var fullscreenPage by androidx.compose.runtime.remember(galleryKey, imageCount) {
        androidx.compose.runtime.mutableStateOf<Int?>(null)
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                if (showStoreTopBar) {
                    StoreGlobalTopBar(
                        cartQuantity = cartQuantity,
                        onSearch = onSearch,
                        onCart = onCart
                    )
                }
                StoreCatalogHeader(title = state.title, showBack = true, onBack = onBack)
            }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 6.dp) {
                Button(
                    onClick = onAddToCart,
                    enabled = state.primaryActionEnabled,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 10.dp)
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.primaryActionLabel == "Añadido al carrito") Color(0xFF183B35) else Color(0xFFE4EFEA),
                        contentColor = if (state.primaryActionLabel == "Añadido al carrito") Color.White else Color(0xFF183B35),
                        disabledContainerColor = Color(0xFFE3DEE3),
                        disabledContentColor = Color(0xFF9E989E)
                    )
                ) {
                    Icon(
                        imageVector = if (state.primaryActionLabel == "Añadido al carrito") Icons.Default.Check else Icons.Outlined.ShoppingBag,
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(state.primaryActionLabel)
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top
        ) {
            if (imageCount > 0) {
                val pagerState = rememberPagerState(pageCount = { imageCount })
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 20.dp), pageSpacing = 10.dp) { index ->
                    imageSlot(index, Modifier.fillMaxWidth().aspectRatio(.78f).clickable { fullscreenPage = index }.semantics {
                        role = Role.Button
                        contentDescription = "Ampliar imagen ${index + 1}"
                    })
                }
                if (imageCount > 1) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        repeat(imageCount) { index ->
                            Box(Modifier.padding(horizontal = 3.dp).size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                                .background(if (pagerState.currentPage == index) Color(0xFF183B35) else Color(0xFFD8D4D7), RoundedCornerShape(50)))
                        }
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp).height(46.dp),
                shape = RoundedCornerShape(23.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD8D4D7)),
                color = Color.Transparent
            ) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onShare, enabled = state.shareEnabled, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Icon(Icons.Outlined.Share, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text("Compartir")
                    }
                    VerticalDivider(Modifier.height(24.dp), color = Color(0xFFD8D4D7))
                    TextButton(onClick = onReserve, enabled = state.reserveEnabled, modifier = Modifier.weight(1f).fillMaxHeight()) {
                        Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text("Reservarlo")
                    }
                }
            }
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 6.dp)) {
                if (state.isOnSale) {
                    Text("OFERTA", color = Color(0xFFD18162), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = state.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF183B35)
                )
                Spacer(Modifier.height(8.dp))
                if (state.displayPrice.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(state.displayPrice, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF252225))
                        if (state.showRegularPrice && state.regularPrice.isNotBlank()) {
                            Text(state.regularPrice, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF9E9E9E), textDecoration = TextDecoration.LineThrough)
                        }
                    }
                }
                if (state.brandText != null || state.compositionText != null || state.descriptionText.isNotBlank()) Spacer(Modifier.height(18.dp))
                state.brandText?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = Color(0xFF454145))
                }
                state.compositionText?.let {
                    if (state.brandText != null) Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF777277))
                }
                if (state.descriptionText.isNotBlank()) {
                    if (state.brandText != null || state.compositionText != null) Spacer(Modifier.height(8.dp))
                    Text(state.descriptionText, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF777277))
                }
                Spacer(Modifier.height(12.dp))
            }
            state.attributeGroups.forEach { group ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                    Text(group.label, fontWeight = FontWeight.Bold)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        group.options.forEach { option ->
                            val round = option.kind == StoreProductDetailOptionKind.Color
                            OutlinedButton(
                                onClick = { onAttributeOptionSelected(group.label, option.value) },
                                enabled = option.enabled,
                                shape = RoundedCornerShape(if (round) 50.dp else 14.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    if (option.selected) 2.dp else 1.dp,
                                    when {
                                        option.selected -> Color(0xFF183B35)
                                        option.incompatible -> Color(0xFFD0CDD0)
                                        else -> Color(0xFF8B878B)
                                    }
                                ),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = when {
                                        option.selected && option.kind == StoreProductDetailOptionKind.Size -> Color(0xFF183B35)
                                        option.selected -> Color(0xFFE4EFEA)
                                        else -> Color.Transparent
                                    },
                                    contentColor = when {
                                        option.selected && option.kind == StoreProductDetailOptionKind.Size -> Color.White
                                        option.incompatible -> Color(0xFF777277)
                                        else -> Color(0xFF353235)
                                    },
                                    disabledContentColor = Color(0xFFAAA6AA)
                                ),
                                contentPadding = when (option.kind) {
                                    StoreProductDetailOptionKind.Size -> PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                                    StoreProductDetailOptionKind.Color -> PaddingValues(horizontal = 14.dp, vertical = 9.dp)
                                    StoreProductDetailOptionKind.Default -> ButtonDefaults.ContentPadding
                                },
                                modifier = Modifier.storeProductDetailOptionSlash(option.incompatible && !option.selected, RoundedCornerShape(if (round) 50.dp else 14.dp))
                            ) {
                                if (option.swatchColor != null) {
                                    Box(Modifier.size(20.dp).graphicsLayer { alpha = if (option.incompatible) 0.55f else 1f }.background(option.swatchColor, RoundedCornerShape(50)))
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(
                                    option.value,
                                    textDecoration = if (option.strikeThrough) TextDecoration.LineThrough else TextDecoration.None,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
            if (isLoading) {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                }
            }
            errorMessage?.let { message ->
                Surface(Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onRetry) { Text("Reintentar") }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cantidad", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(16.dp))
                    IconButton(onClick = onQuantityMinus, enabled = state.quantityMinusEnabled) { Icon(Icons.Default.Remove, contentDescription = "Reducir") }
                    Text(state.quantity.toString(), fontWeight = FontWeight.Bold)
                    IconButton(onClick = onQuantityPlus, enabled = state.quantityPlusEnabled) { Icon(Icons.Default.Add, contentDescription = "Aumentar") }
                }
            }
        }
    }
    fullscreenPage?.let { initialPage ->
        if (imageCount > 0) StoreProductDetailFullscreenGallery(imageCount, initialPage, fullscreenImageSlot) { fullscreenPage = null }
    }
}

@Composable
fun StoreProductDetailFullscreenGallery(
    imageCount: Int,
    initialPage: Int,
    imageSlot: @Composable (Int, Modifier) -> Unit,
    onDismiss: () -> Unit
) {
    if (imageCount <= 0) return
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, imageCount - 1), pageCount = { imageCount })
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                var scale by androidx.compose.runtime.remember(page) { androidx.compose.runtime.mutableStateOf(1f) }
                var offsetX by androidx.compose.runtime.remember(page) { androidx.compose.runtime.mutableStateOf(0f) }
                var offsetY by androidx.compose.runtime.remember(page) { androidx.compose.runtime.mutableStateOf(0f) }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    imageSlot(page, Modifier.fillMaxWidth().pointerInput(page) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 4f)
                            if (scale > 1f) { offsetX += pan.x; offsetY += pan.y }
                            else { offsetX = 0f; offsetY = 0f }
                        }
                    }.graphicsLayer {
                        scaleX = scale; scaleY = scale; translationX = offsetX; translationY = offsetY
                    })
                }
            }
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(50))) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
            }
            if (imageCount > 1) {
                Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp), horizontalArrangement = Arrangement.Center) {
                    repeat(imageCount) { index ->
                        Box(Modifier.padding(horizontal = 4.dp).size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                            .background(if (pagerState.currentPage == index) Color.White else Color.White.copy(alpha = .4f), RoundedCornerShape(50)))
                    }
                }
            }
        }
    }
}

private fun Modifier.storeProductDetailOptionSlash(show: Boolean, shape: androidx.compose.ui.graphics.Shape): Modifier =
    if (!show) this else clip(shape).drawWithContent {
        drawContent()
        drawLine(
            color = Color(0xFF8B878B),
            start = Offset(0f, size.height),
            end = Offset(size.width, 0f),
            strokeWidth = 1.25.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
