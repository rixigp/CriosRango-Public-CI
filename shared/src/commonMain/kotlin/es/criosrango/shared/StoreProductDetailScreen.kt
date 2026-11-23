package es.criosrango.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val ProductGreen = Color(0xFF183B35)
private val ProductMint = Color(0xFFE4EFEA)
private val ProductBorder = Color(0xFFD8D9D5)
private val ProductMuted = Color(0xFF777277)

@OptIn(ExperimentalLayoutApi::class)
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
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        topBar = {
            Column {
                if (showStoreTopBar) {
                    StoreGlobalTopBar(cartQuantity = cartQuantity, onSearch = onSearch, onCart = onCart)
                }
                StoreCatalogHeader(title = state.title, showBack = true, onBack = onBack)
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top
        ) {
            if (imageCount > 0) {
                val pagerState = rememberPagerState(pageCount = { imageCount })
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    pageSpacing = 10.dp
                ) { index ->
                    imageSlot(index, Modifier.fillMaxWidth().aspectRatio(.78f).clickable { fullscreenPage = index }.semantics {
                        role = Role.Button
                        contentDescription = "Ampliar imagen \${index + 1}"
                    })
                }
                if (imageCount > 1) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(imageCount) { index ->
                            Box(
                                Modifier.padding(horizontal = 3.dp)
                                    .size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                                    .background(if (pagerState.currentPage == index) ProductGreen else Color(0xFFD8D4D7), CircleShape)
                            )
                        }
                    }
                }
            }
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    .padding(top = 6.dp, bottom = 22.dp).height(46.dp),
                shape = RoundedCornerShape(23.dp),
                border = BorderStroke(1.dp, ProductBorder),
                color = Color.Transparent
            ) {
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = onShare,
                        enabled = state.shareEnabled,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = ButtonDefaults.textButtonColors(contentColor = ProductGreen)
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text("Compartir")
                    }
                    VerticalDivider(Modifier.height(24.dp), color = ProductBorder)
                    TextButton(
                        onClick = onReserve,
                        enabled = state.reserveEnabled,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        colors = ButtonDefaults.textButtonColors(contentColor = ProductGreen)
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.Chat, contentDescription = null)
                        Spacer(Modifier.width(7.dp))
                        Text("Reservarlo")
                    }
                }
            }
            StoreProductDetailSummary(state)
            StoreProductSizeHelpCard()
            val orderedGroups = state.attributeGroups.filter { it.options.isNotEmpty() }.sortedBy { group ->
                when {
                    group.label.contains("color", ignoreCase = true) -> 0
                    group.label.contains("talla", ignoreCase = true) -> 1
                    else -> 2
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                orderedGroups.forEach { group -> StoreProductDetailVariationCard(group, onAttributeOptionSelected) }
            }
            if (isLoading) {
                Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                }
            }
            errorMessage?.let { message ->
                Surface(Modifier.fillMaxWidth().padding(20.dp), shape = RoundedCornerShape(18.dp), tonalElevation = 1.dp) {
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 28.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onRetry) { Text("Reintentar") }
                    }
                }
            }
            StoreProductDetailPurchaseRow(state, onQuantityMinus, onQuantityPlus, onAddToCart)
        }
    }
    fullscreenPage?.let { initialPage ->
        if (imageCount > 0) StoreProductDetailFullscreenGallery(imageCount, initialPage, fullscreenImageSlot) { fullscreenPage = null }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoreProductDetailSummary(state: StoreProductDetailUiState) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 18.dp)) {
        if (state.isOnSale) {
            Text("OFERTA", color = Color(0xFFD18162), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
        }
        val hasBrand = !state.brandText.isNullOrBlank() || !state.brandLogoUrl.isNullOrBlank()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(if (hasBrand) 1.62f else 1f).widthIn(min = 0.dp)) {
                Text(state.title, color = ProductGreen, fontSize = 22.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold)
                if (state.displayPrice.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(state.displayPrice, color = ProductGreen, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold)
                        if (state.showRegularPrice && state.regularPrice.isNotBlank()) {
                            Text(
                                state.regularPrice,
                                color = Color(0xFF92958F),
                                fontSize = 15.sp,
                                lineHeight = 20.sp,
                                textDecoration = TextDecoration.LineThrough
                            )
                        }
                    }
                }
                state.compositionText?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(7.dp))
                    Text(it, color = ProductMuted, fontSize = 13.sp, lineHeight = 18.sp)
                }
                if (state.descriptionText.isNotBlank()) {
                    Spacer(Modifier.height(7.dp))
                    Text(state.descriptionText, color = ProductMuted, fontSize = 14.sp, lineHeight = 20.sp)
                }
            }
            if (hasBrand) {
                Box(Modifier.padding(horizontal = 10.dp).width(1.dp).height(72.dp).background(Color(0xFFE5E7E2)))
                Box(Modifier.weight(1f).heightIn(min = 72.dp), contentAlignment = Alignment.Center) {
                    val logo = state.brandLogoUrl?.takeIf { it.isNotBlank() }
                    if (logo != null) {
                        RemoteStoreImage(logo, state.brandText, Modifier.fillMaxWidth().height(92.dp), ContentScale.Fit, backgroundColor = null)
                    } else {
                        state.brandText?.let {
                            Text(
                                it,
                                modifier = Modifier.fillMaxWidth(),
                                color = ProductGreen,
                                fontSize = 15.sp,
                                lineHeight = 19.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                overflow = TextOverflow.Clip
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreProductSizeHelpCard() {
    val uriHandler = LocalUriHandler.current
    val body = buildAnnotatedString {
        append("Si la talla que necesitas, no se encuentra disponible, contacta con nosotros en ")
        pushStringAnnotation("url", "mailto:criosrango@criosrango.es")
        withStyle(SpanStyle(color = Color(0xFF52665E), textDecoration = TextDecoration.Underline)) { append("criosrango@criosrango.es") }
        pop()
        append(" o nuestro whatsapp ")
        pushStringAnnotation("url", "https://wa.me/34633246788")
        withStyle(SpanStyle(color = Color(0xFF52665E), textDecoration = TextDecoration.Underline)) { append("633 246 788") }
        pop()
        append(".")
    }
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 18.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFEAF1E8)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(Modifier.width(46.dp).height(34.dp)) {
                Canvas(Modifier.size(32.dp).align(Alignment.CenterStart)) {
                    val green = Color(0xFF398D51)
                    val stroke = 1.9.dp.toPx()
                    drawCircle(green, 7.dp.toPx(), Offset(9.5.dp.toPx(), 18.dp.toPx()), style = Stroke(stroke))
                    drawCircle(green, 2.5.dp.toPx(), Offset(9.5.dp.toPx(), 18.dp.toPx()), style = Stroke(stroke))
                    drawLine(green, Offset(15.8.dp.toPx(), 13.5.dp.toPx()), Offset(29.dp.toPx(), 13.5.dp.toPx()), stroke)
                    drawLine(green, Offset(15.8.dp.toPx(), 20.1.dp.toPx()), Offset(29.dp.toPx(), 20.1.dp.toPx()), stroke)
                    drawLine(green, Offset(19.dp.toPx(), 13.5.dp.toPx()), Offset(19.dp.toPx(), 17.dp.toPx()), stroke)
                    drawLine(green, Offset(23.dp.toPx(), 13.5.dp.toPx()), Offset(23.dp.toPx(), 16.dp.toPx()), stroke)
                    drawLine(green, Offset(27.dp.toPx(), 13.5.dp.toPx()), Offset(27.dp.toPx(), 17.dp.toPx()), stroke)
                }
                Box(Modifier.align(Alignment.CenterEnd).width(1.dp).height(34.dp).background(Color(0x24183B35)))
            }
            Column(Modifier.weight(1f)) {
                Text("Pregunta por más tallas!", color = ProductGreen, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(3.dp))
                ClickableText(
                    text = body,
                    style = TextStyle(color = Color(0xFF52665E), fontSize = 14.sp, lineHeight = 20.sp),
                    onClick = { offset ->
                        body.getStringAnnotations("url", offset, (offset + 1).coerceAtMost(body.length)).firstOrNull()?.let { uriHandler.openUri(it.item) }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoreProductDetailVariationCard(
    group: StoreProductDetailAttributeGroupUi,
    onAttributeOptionSelected: (String, String) -> Unit
) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, ProductBorder),
        color = Color.Transparent
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 11.dp)) {
            Text(
                group.label.uppercase(),
                color = ProductGreen,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.9.sp
            )
            Spacer(Modifier.height(5.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                group.options.forEach { option ->
                    when (option.kind) {
                        StoreProductDetailOptionKind.Color -> StoreProductColorOption(option) { onAttributeOptionSelected(group.label, option.value) }
                        StoreProductDetailOptionKind.Size -> StoreProductSizeOption(option) { onAttributeOptionSelected(group.label, option.value) }
                        StoreProductDetailOptionKind.Default -> StoreProductDefaultOption(option) { onAttributeOptionSelected(group.label, option.value) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreProductColorOption(option: StoreProductDetailOptionUi, onClick: () -> Unit) {
    Column(
        Modifier.widthIn(min = 52.dp, max = 80.dp)
            .clickable(enabled = option.enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 3.dp)
            .graphicsLayer { alpha = if (option.enabled) 1f else 0.38f },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            if (option.selected) {
                Box(Modifier.size(40.dp).background(Color.White, CircleShape).border(2.dp, ProductGreen, CircleShape), contentAlignment = Alignment.Center) {
                    StoreProductColorSwatch(option)
                }
            } else {
                StoreProductColorSwatch(option)
            }
            if (option.selected) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(13.dp).border(1.dp, Color.White, CircleShape).background(ProductGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✓", color = Color.White, fontSize = 8.sp, lineHeight = 8.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Text(
            option.value,
            color = ProductGreen,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = if (option.selected) FontWeight.SemiBold else FontWeight.Normal,
            textDecoration = if (option.strikeThrough) TextDecoration.LineThrough else TextDecoration.None,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StoreProductColorSwatch(option: StoreProductDetailOptionUi) {
    Box(
        Modifier.size(32.dp)
            .border(1.dp, ProductBorder, CircleShape)
            .background(option.swatchColor ?: Color(0xFFE7E6E1), CircleShape)
            .storeProductDetailOptionSlash(option.incompatible && !option.selected, CircleShape)
    )
}

@Composable
private fun StoreProductSizeOption(option: StoreProductDetailOptionUi, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = option.enabled,
        modifier = Modifier.height(40.dp).widthIn(min = 58.dp, max = 100.dp)
            .storeProductDetailOptionSlash(option.incompatible && !option.selected, RoundedCornerShape(13.dp)),
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, if (option.selected) ProductGreen else ProductBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (option.selected) ProductGreen else Color.Transparent,
            contentColor = if (option.selected) Color.White else ProductGreen,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = Color(0xFFAAA6AA)
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp)
    ) {
        Text(
            option.value,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            textDecoration = if (option.strikeThrough) TextDecoration.LineThrough else TextDecoration.None,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StoreProductDefaultOption(option: StoreProductDetailOptionUi, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = option.enabled,
        modifier = Modifier.heightIn(min = 40.dp).widthIn(max = 140.dp)
            .storeProductDetailOptionSlash(option.incompatible && !option.selected, RoundedCornerShape(13.dp)),
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, if (option.selected) ProductGreen else ProductBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (option.selected) ProductGreen else Color.Transparent,
            contentColor = if (option.selected) Color.White else ProductGreen,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = Color(0xFFAAA6AA)
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(option.value, textDecoration = if (option.strikeThrough) TextDecoration.LineThrough else TextDecoration.None, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StoreProductDetailPurchaseRow(
    state: StoreProductDetailUiState,
    onQuantityMinus: () -> Unit,
    onQuantityPlus: () -> Unit,
    onAddToCart: () -> Unit
) {
    val displayActionLabel = if (state.primaryActionLabel == "Elige una combinación") "Elige opciones" else state.primaryActionLabel
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 18.dp, bottom = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            Modifier.width(116.dp).height(58.dp),
            shape = CircleShape,
            border = BorderStroke(1.dp, ProductBorder),
            color = Color.Transparent
        ) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onQuantityMinus, enabled = state.quantityMinusEnabled, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Remove, contentDescription = "Reducir", tint = if (state.quantityMinusEnabled) ProductGreen else Color(0xFFAEB2AD), modifier = Modifier.size(18.dp))
                }
                Text(state.quantity.toString(), modifier = Modifier.weight(1f), color = ProductGreen, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                IconButton(onClick = onQuantityPlus, enabled = state.quantityPlusEnabled, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Aumentar", tint = if (state.quantityPlusEnabled) ProductGreen else Color(0xFFAEB2AD), modifier = Modifier.size(18.dp))
                }
            }
        }
        Button(
            onClick = onAddToCart,
            enabled = state.primaryActionEnabled,
            modifier = Modifier.weight(1f).height(58.dp),
            shape = CircleShape,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.primaryActionEnabled || state.primaryActionLabel == "Añadido al carrito") ProductGreen else ProductMint,
                contentColor = if (state.primaryActionEnabled || state.primaryActionLabel == "Añadido al carrito") Color.White else ProductGreen,
                disabledContainerColor = ProductMint,
                disabledContentColor = ProductGreen
            )
        ) {
            Icon(
                imageVector = if (state.primaryActionLabel == "Añadido al carrito") Icons.Default.Check else Icons.Outlined.ShoppingBag,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(displayActionLabel, fontSize = 14.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
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
    IosBackHandler(IosBackPriority.OVERLAY, onBack = onDismiss)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val pagerState = rememberPagerState(initialPage = initialPage.coerceIn(0, imageCount - 1), pageCount = { imageCount })
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                var scale by androidx.compose.runtime.remember(page) { androidx.compose.runtime.mutableStateOf(1f) }
                var offsetX by androidx.compose.runtime.remember(page) { androidx.compose.runtime.mutableStateOf(0f) }
                var offsetY by androidx.compose.runtime.remember(page) { androidx.compose.runtime.mutableStateOf(0f) }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                        scale = (scale * zoomChange).coerceIn(1f, 4f)
                        if (scale > 1f) {
                            offsetX += panChange.x
                            offsetY += panChange.y
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }
                    imageSlot(
                        page,
                        Modifier.fillMaxWidth()
                            .transformable(
                                state = transformState,
                                canPan = { scale > 1f }
                            )
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                            }
                    )
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
