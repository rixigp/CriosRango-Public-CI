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
import androidx.compose.material.icons.automirrored.outlined.Chat
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

internal fun productColorSwatch(value: String): Color? {
    val key = java.text.Normalizer
        .normalize(
            value.lowercase().trim(),
            java.text.Normalizer.Form.NFD
        )
        .replace("\\p{Mn}+".toRegex(), "")

    return when {
        key.startsWith("#") && key.length == 7 ->
            runCatching {
                Color(android.graphics.Color.parseColor(key))
            }.getOrNull()

        "azul pavo" in key -> Color(0xFF315D66)
        "marino" in key -> Color(0xFF1F2D4D)
        "azul" in key -> Color(0xFF527DA8)
        "celeste" in key -> Color(0xFF9BCBE3)
        "turquesa" in key -> Color(0xFF3AAFA9)

        "verde botella" in key -> Color(0xFF274E3C)
        "verde" in key -> Color(0xFF668A63)
        "menta" in key -> Color(0xFFA8D5BA)

        "rojo" in key -> Color(0xFFB94A48)
        "granate" in key || "burdeos" in key -> Color(0xFF743A46)
        "rosa" in key -> Color(0xFFE6A7B8)
        "fucsia" in key -> Color(0xFFC74779)

        "naranja" in key -> Color(0xFFD9824B)
        "amarillo" in key -> Color(0xFFE4C34A)
        "mostaza" in key -> Color(0xFFC89B3C)

        "violeta" in key || "morado" in key -> Color(0xFF80658D)
        "lila" in key -> Color(0xFFB6A0C9)

        "beige" in key || "arena" in key -> Color(0xFFD8C6A5)
        "camel" in key -> Color(0xFFB9855C)
        "marron" in key || "chocolate" in key -> Color(0xFF765344)
        "crudo" in key -> Color(0xFFE9E1D1)

        "blanco" in key -> Color.White
        "negro" in key -> Color(0xFF222222)
        "gris" in key -> Color(0xFF8A8A8A)

        else -> null
    }
}

internal fun productSizeSortKey(value: String): Int {
    val clean = value
        .uppercase()
        .trim()
        .replace(" ", "")

    return when {
        clean.endsWith("M") ->
            clean.removeSuffix("M").toIntOrNull() ?: 999

        clean.endsWith("A") ->
            (clean.removeSuffix("A").toIntOrNull() ?: 99) * 12

        clean == "XXS" -> 1000
        clean == "XS" -> 1001
        clean == "S" -> 1002
        clean == "M" -> 1003
        clean == "L" -> 1004
        clean == "XL" -> 1005
        clean == "XXL" -> 1006

        else -> 2000
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProductDetail(product: StoreProduct, variation: StoreProduct?, cartItems: List<CartItem>, loadVariation: (Int) -> Unit, onBack: () -> Unit, onCart: () -> Unit, onAdd: (CartItem) -> Unit) {
    val context = LocalContext.current
    val selected = remember(product.id) { mutableStateMapOf<String, String>() }
    var fullscreenGalleryPage by remember(product.id) {
        mutableStateOf<Int?>(null)
    }
    var quantity by remember(product.id) { mutableIntStateOf(1) }
    val selectableAttributes = product.attributes.filter { it.terms.isNotEmpty() }
    val selectedVariation = product.variations.firstOrNull { candidate ->
            selectableAttributes.all { attribute ->
                val selectedValue = selected[attribute.name]
                val variationValue = candidate.attributes.firstOrNull { attributesMatch(it.name, attribute.name) }?.value
                selectedValue != null && variationValue != null && attributeValuesMatch(selectedValue, variationValue)
            }
    }
            val matching = selectedVariation?.takeIf { it.isAvailableForPurchase() }
            LaunchedEffect(selectedVariation?.id) { selectedVariation?.id?.let(loadVariation) }
            val current = if (variation?.id == selectedVariation?.id) variation else if (product.type == "simple") product else null
    val limits = current?.purchaseLimits()
    val minimumQuantity = limits?.minimum ?: 1
    val maximumQuantity = limits?.maximum
    val multipleOf = limits?.multipleOf ?: 1
    val existingQuantity = cartItems
        .filter { it.productId == product.id && it.variationId == matching?.id }
        .sumOf { it.quantity }
    val availableMaximum = maximumQuantity?.minus(existingQuantity)?.coerceAtLeast(0)
    val selectedAttributes = selected.toMap()

    val alreadyAdded = cartItems.any {
        it.productId == product.id &&
        it.variationId == matching?.id &&
        it.selectedAttributes == selectedAttributes
    }

    val currentSelectionKey = buildString {
        append(product.id)
        append("|")
        append(matching?.id ?: 0)

        selectedAttributes
            .toSortedMap()
            .forEach { (name, value) ->
                append("|")
                append(name)
                append("=")
                append(value)
            }
    }

    var lastAddedSelectionKey by remember(product.id) {
        mutableStateOf<String?>(null)
    }

    val currentCombinationAdded =
        alreadyAdded ||
        lastAddedSelectionKey == currentSelectionKey
    val canAdd = current != null && current.isInStock && current.isPurchasable == true &&
        (product.type == "simple" || matching != null)

    val addCurrentProductToCart: () -> Unit = {
        val currentPrice = current ?: product

        val labels = product.attributes.mapNotNull { attribute ->
            selected[attribute.name]?.let { slug ->
                attribute.terms.firstOrNull { it.slug == slug }?.name ?: slug
            }
        }

        onAdd(
            CartItem(
                productId = product.id,
                name = product.name.cleanWooText(),
                imageUrl = currentPrice.images.firstOrNull()?.src
                    ?: product.images.firstOrNull()?.src.orEmpty(),
                unitPrice = currentPrice.prices.price,
                variationId = matching?.id,
                selectedAttributes = selected.toMap(),
                variationLabel = labels.joinToString(" · "),
                quantity = quantity
            )
        )

        lastAddedSelectionKey = currentSelectionKey
    }
    LaunchedEffect(current?.id, limits) {
        if (limits != null) {
            val highestValid = limits.minimum + ((limits.maximum - limits.minimum) / limits.multipleOf) * limits.multipleOf
            val effectiveMaximum = availableMaximum ?: highestValid
            quantity = quantity.coerceIn(limits.minimum, effectiveMaximum.coerceAtLeast(limits.minimum))
            if ((quantity - limits.minimum) % limits.multipleOf != 0) quantity = limits.minimum
        } else {
            quantity = 1
        }
    }
    BackHandler { onBack() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detalle") },
                navigationIcon = {
                    IconButton(onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Volver"
                        )
                    }
                },
                actions = {
                    BadgedBox(
                        badge = {
                            if (cartItems.sumOf { it.quantity } > 0) {
                                Badge {
                                    Text(
                                        cartItems.sumOf { it.quantity }.toString()
                                    )
                                }
                            }
                        }
                    ) {
                        IconButton(onCart) {
                            Icon(
                                Icons.Outlined.ShoppingBag,
                                "Ir al carrito"
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                shadowElevation = 6.dp
            ) {
                Button(
                    onClick = addCurrentProductToCart,
                    enabled =
                        canAdd &&
                        quantity >= minimumQuantity &&
                        (quantity - minimumQuantity) % multipleOf == 0 &&
                        (availableMaximum == null ||
                            quantity <= availableMaximum),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(
                            start = 20.dp,
                            end = 20.dp,
                            top = 10.dp,
                            bottom = 10.dp
                        )
                        .height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor =
                            if (currentCombinationAdded)
                                Color(0xFF183B35)
                            else
                                Color(0xFFE4EFEA),

                        contentColor =
                            if (currentCombinationAdded)
                                Color.White
                            else
                                Color(0xFF183B35),

                        disabledContainerColor =
                            Color(0xFFE3DEE3),

                        disabledContentColor =
                            Color(0xFF9E989E)
                    )
                ) {
                    Icon(
                        imageVector =
                            if (currentCombinationAdded)
                                Icons.Default.Check
                            else
                                Icons.Outlined.ShoppingBag,
                        contentDescription = null
                    )

                    Spacer(Modifier.width(8.dp))

                    Text(
                        when {
                            product.type == "variable" &&
                                matching == null ->
                                "Elige una combinación"

                            currentCombinationAdded ->
                                "Añadido al carrito"

                            else ->
                                "Añadir al carrito"
                        }
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(bottom = 28.dp)) {
            item {
                Column {
                    val galleryImages =
                    (current?.images ?: product.images).ifEmpty { product.images }

                if (galleryImages.isNotEmpty()) {
                    val pagerState = rememberPagerState(
                        pageCount = { galleryImages.size }
                    )

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        pageSpacing = 10.dp
                    ) { page ->
                        val item = galleryImages[page]

                        CatalogImage(
                            item.src,
                            product.name,
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(.78f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    fullscreenGalleryPage = page
                                },
                            ContentScale.Crop
                        )
                    }

                    if (galleryImages.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            repeat(galleryImages.size) { index ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 3.dp)
                                        .size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                                        .background(
                                            if (pagerState.currentPage == index)
                                                Color(0xFF183B35)
                                            else
                                                Color(0xFFD8D4D7),
                                            RoundedCornerShape(50)
                                        )
                                )
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .height(46.dp),
                    shape = RoundedCornerShape(23.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        Color(0xFFD8D4D7)
                    ),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                val shareText = buildString {
                                    append(product.name.cleanWooText())

                                    if (product.permalink.isNotBlank()) {
                                        append("\n\n")
                                        append(product.permalink)
                                    }
                                }

                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }

                                context.startActivity(
                                    Intent.createChooser(
                                        shareIntent,
                                        "Compartir producto"
                                    )
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Icon(
                                Icons.Outlined.Share,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(7.dp))
                            Text("Compartir")
                        }

                        VerticalDivider(
                            modifier = Modifier.height(24.dp),
                            color = Color(0xFFD8D4D7)
                        )

                        TextButton(
                            onClick = {
                                val details = selected.entries
                                    .filter { it.value.isNotBlank() }
                                    .joinToString("\n") { entry ->
                                        val label =
                                            if (entry.key.equals("Tallas", true)) "Talla"
                                            else entry.key

                                        val value =
                                            if (entry.key.equals("Color", true))
                                                entry.value.replaceFirstChar { it.uppercase() }
                                            else
                                                entry.value.uppercase()

                                        "$label: $value"
                                    }

                                val message = buildString {
                                    append("Hola, quiero reservar:\n")
                                    append(product.name.cleanWooText())

                                    if (details.isNotBlank()) {
                                        append("\n")
                                        append(details)
                                    }

                                    if (product.permalink.isNotBlank()) {
                                        append("\n")
                                        append(product.permalink)
                                    }
                                }

                                val uri = Uri.parse(
                                    "https://wa.me/34633246788?text=${Uri.encode(message)}"
                                )

                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, uri)
                                )
                            },
                            enabled = product.type != "variable" || matching != null,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Icon(
                                Icons.AutoMirrored.Outlined.Chat,
                                contentDescription = null
                            )
                            Spacer(Modifier.width(7.dp))
                            Text("Reservarlo")
                        }
                    }
                }

                Column(
                    Modifier
                        .padding(horizontal = 20.dp)
                        .padding(top = 6.dp)
                ) {
                    if (product.onSale) {
                        Text(
                            "OFERTA",
                            color = Color(0xFFD18162),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                    }

                    Text(
                        text = product.name.cleanWooText(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF183B35)
                    )

                    Spacer(Modifier.height(8.dp))

                    val effectiveProduct = current ?: product
                    if (effectiveProduct.hasDisplayablePrice) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                effectiveProduct.displayPrice(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF252225)
                            )

                            if (effectiveProduct.onSale) {
                                Text(
                                    effectiveProduct.regularDisplayPrice(),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color(0xFF9E9E9E),
                                    textDecoration = TextDecoration.LineThrough
                                )
                            }
                        }
                    }

                    val infoText = HtmlCompat.fromHtml(
                        product.description.ifBlank {
                            product.shortDescription
                        },
                        HtmlCompat.FROM_HTML_MODE_COMPACT
                    ).toString()

                    val infoLines = infoText
                        .lines()
                        .map { it.trim() }
                        .filter { it.isNotBlank() }

                    val brandLine = infoLines.firstOrNull {
                        it.startsWith("Marca:", ignoreCase = true)
                    }

                    val compositionLine = infoLines.firstOrNull {
                        it.startsWith("Composición:", ignoreCase = true) ||
                        it.startsWith("Composicion:", ignoreCase = true)
                    }

                    val remainingInfo = infoLines
                        .filterNot {
                            it == brandLine ||
                            it == compositionLine
                        }
                        .joinToString("\n")

                    if (
                        brandLine != null ||
                        compositionLine != null ||
                        remainingInfo.isNotBlank()
                    ) {
                        Spacer(Modifier.height(18.dp))
                    }

                    brandLine?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF454145)
                        )
                    }

                    compositionLine?.let {
                        if (brandLine != null) {
                            Spacer(Modifier.height(4.dp))
                        }

                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF777277)
                        )
                    }

                    if (remainingInfo.isNotBlank()) {
                        if (
                            brandLine != null ||
                            compositionLine != null
                        ) {
                            Spacer(Modifier.height(8.dp))
                        }

                        Text(
                            text = remainingInfo,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF777277)
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                }
            }
            }
            product.attributes.filter { it.terms.isNotEmpty() }.forEach { attribute ->
                item { Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) { Text(attribute.name, fontWeight = FontWeight.Bold); LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val orderedTerms =
                        if (attribute.name.equals("Tallas", true)) {
                            attribute.terms.sortedWith(
                                compareBy(
                                    { productSizeSortKey(it.name) },
                                    { it.name }
                                )
                            )
                        } else {
                            attribute.terms
                        }

                    items(orderedTerms) { term ->
                    val chosen = selected[attribute.name]?.let { attributeValuesMatch(it, term.slug) || attributeValuesMatch(it, term.name) } == true
                    val available = product.variations.any { candidate ->
                            candidate.isAvailableForPurchase() &&
                            candidate.attributes.any { variationAttribute ->
                                attributesMatch(variationAttribute.name, attribute.name) &&
                                    (attributeValuesMatch(variationAttribute.value, term.slug) || attributeValuesMatch(variationAttribute.value, term.name))
                            } &&
                            selectableAttributes.all { selectedAttribute ->
                                if (attributesMatch(selectedAttribute.name, attribute.name)) true
                                else selected[selectedAttribute.name]?.let { selectedValue ->
                                    candidate.attributes.firstOrNull { attributesMatch(it.name, selectedAttribute.name) }?.value?.let { variationValue ->
                                        attributeValuesMatch(selectedValue, variationValue)
                                    }
                                } ?: true
                            }
                    }
                    if (attribute.name.equals("Color", true)) {
                        val swatch = productColorSwatch(term.name)

                        OutlinedButton(
                            onClick = {
                                if (available) {
                                    selected[attribute.name] = term.slug
                                }
                            },
                            enabled = available,
                            shape = RoundedCornerShape(50.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                if (chosen) 2.dp else 1.dp,
                                if (chosen)
                                    Color(0xFF183B35)
                                else
                                    Color(0xFF8B878B)
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor =
                                    if (chosen)
                                        Color(0xFFE4EFEA)
                                    else
                                        Color.Transparent
                            ),
                            contentPadding = PaddingValues(
                                horizontal = 14.dp,
                                vertical = 9.dp
                            )
                        ) {
                            if (swatch != null) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(
                                            swatch,
                                            RoundedCornerShape(50)
                                        )
                                        .then(
                                            if (
                                                term.name.contains(
                                                    "blanco",
                                                    ignoreCase = true
                                                )
                                            ) {
                                                Modifier
                                                    .clip(RoundedCornerShape(50))
                                            } else {
                                                Modifier
                                            }
                                        )
                                )

                                Spacer(Modifier.width(8.dp))
                            }

                            Text(term.name)
                        }
                    } else if (attribute.name.equals("Tallas", true)) {
                        OutlinedButton(
                            onClick = {
                                if (available) {
                                    selected[attribute.name] = term.slug
                                }
                            },
                            enabled = available,
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                if (chosen) 2.dp else 1.dp,
                                when {
                                    chosen -> Color(0xFF183B35)
                                    available -> Color(0xFF8B878B)
                                    else -> Color(0xFFD0CDD0)
                                }
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor =
                                    if (chosen)
                                        Color(0xFF183B35)
                                    else
                                        Color.Transparent,
                                contentColor =
                                    if (chosen)
                                        Color.White
                                    else
                                        Color(0xFF353235),
                                disabledContentColor =
                                    Color(0xFFAAA6AA)
                            ),
                            contentPadding = PaddingValues(
                                horizontal = 18.dp,
                                vertical = 10.dp
                            )
                        ) {
                            Text(
                                text = term.name,
                                textDecoration =
                                    if (!available)
                                        TextDecoration.LineThrough
                                    else
                                        TextDecoration.None
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                if (available) {
                                    selected[attribute.name] = term.slug
                                }
                            },
                            enabled = available,
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor =
                                    if (chosen)
                                        Color(0xFFE4EFEA)
                                    else
                                        Color.Transparent
                            )
                        ) {
                            Text(term.name)
                        }
                    }
                } } } }
            }
            item { Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) { Text("Cantidad", fontWeight = FontWeight.Bold); Spacer(Modifier.width(16.dp)); IconButton({ if (limits != null && quantity > minimumQuantity) quantity -= multipleOf }) { Icon(Icons.Default.Remove, "Reducir") }; Text(quantity.toString(), fontWeight = FontWeight.Bold); IconButton({ if (availableMaximum == null || quantity + multipleOf <= availableMaximum) quantity += multipleOf }, enabled = availableMaximum == null || quantity + multipleOf <= availableMaximum) { Icon(Icons.Default.Add, "Aumentar") } } }


        }
    }
    fullscreenGalleryPage?.let { initialPage ->
        val fullscreenImages =
            (current?.images ?: product.images).ifEmpty { product.images }

        if (fullscreenImages.isNotEmpty()) {
            Dialog(
                onDismissRequest = {
                    fullscreenGalleryPage = null
                },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false
                )
            ) {
                val fullscreenPagerState = rememberPagerState(
                    initialPage = initialPage,
                    pageCount = { fullscreenImages.size }
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    HorizontalPager(
                        state = fullscreenPagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        var scale by remember(page) {
                            mutableFloatStateOf(1f)
                        }
                        var offsetX by remember(page) {
                            mutableFloatStateOf(0f)
                        }
                        var offsetY by remember(page) {
                            mutableFloatStateOf(0f)
                        }

                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = fullscreenImages[page].src,
                                contentDescription = product.name,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(page) {
                                        detectTransformGestures { _, pan, zoom, _ ->
                                            scale =
                                                (scale * zoom)
                                                    .coerceIn(1f, 4f)

                                            if (scale > 1f) {
                                                offsetX += pan.x
                                                offsetY += pan.y
                                            } else {
                                                offsetX = 0f
                                                offsetY = 0f
                                            }
                                        }
                                    }
                                    .graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                        translationX = offsetX
                                        translationY = offsetY
                                    },
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            fullscreenGalleryPage = null
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(12.dp)
                            .background(
                                Color.Black.copy(alpha = 0.45f),
                                RoundedCornerShape(50)
                            )
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Color.White
                        )
                    }

                    if (fullscreenImages.size > 1) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(bottom = 24.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            repeat(fullscreenImages.size) { index ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .size(
                                            if (fullscreenPagerState.currentPage == index)
                                                8.dp
                                            else
                                                6.dp
                                        )
                                        .background(
                                            if (fullscreenPagerState.currentPage == index)
                                                Color.White
                                            else
                                                Color.White.copy(alpha = 0.4f),
                                            RoundedCornerShape(50)
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}
