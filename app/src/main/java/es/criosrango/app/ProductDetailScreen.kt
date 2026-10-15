package es.criosrango.app

import es.criosrango.shared.StoreProductDetailScreen
import es.criosrango.shared.isVariationAvailableForPurchase

import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawWithContent

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
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

internal fun productCombinationSelectionKey(
    productId: Int,
    variationId: Int?,
    selectedAttributes: Map<String, String>
): String = buildString {
    append(productId)
    append("|")
    append(variationId ?: 0)
    selectedAttributes
        .toSortedMap()
        .forEach { (name, value) ->
            append("|")
            append(name)
            append("=")
            append(value)
        }
}

internal data class ProductDetailVariationSnapshot(
    val availableForPurchase: Boolean,
    val attributes: List<Pair<String, String>>
)

internal data class ProductDetailAttributeOptionState(
    val existsGlobally: Boolean,
    val compatibleWithCurrentSelection: Boolean
)

internal fun productDetailAttributeOptionClickable(
    chosen: Boolean,
    existsGlobally: Boolean
): Boolean = chosen || existsGlobally

private fun variationHasAttributeValue(
    variation: ProductDetailVariationSnapshot,
    attributeName: String,
    selectedValue: String
): Boolean =
    variation.attributes.any { (variationAttributeName, variationValue) ->
        attributesMatch(variationAttributeName, attributeName) &&
            attributeValuesMatch(selectedValue, variationValue)
    }

private fun variationHasTerm(
    variation: ProductDetailVariationSnapshot,
    attributeName: String,
    termSlug: String,
    termName: String
): Boolean =
    variation.attributes.any { (variationAttributeName, variationValue) ->
        attributesMatch(variationAttributeName, attributeName) &&
            (attributeValuesMatch(variationValue, termSlug) ||
                attributeValuesMatch(variationValue, termName))
    }

internal fun productDetailAttributeOptionState(
    variations: List<ProductDetailVariationSnapshot>,
    selected: Map<String, String>,
    attributeName: String,
    termSlug: String,
    termName: String
): ProductDetailAttributeOptionState {
    val existsGlobally = variations.any { variation ->
        variation.availableForPurchase &&
            variationHasTerm(variation, attributeName, termSlug, termName)
    }

    val compatibleWithCurrentSelection = variations.any { variation ->
        variation.availableForPurchase &&
            variationHasTerm(variation, attributeName, termSlug, termName) &&
            selected.all { (selectedAttributeName, selectedValue) ->
                attributesMatch(selectedAttributeName, attributeName) ||
                    variationHasAttributeValue(
                        variation,
                        selectedAttributeName,
                        selectedValue
                    )
            }
    }

    return ProductDetailAttributeOptionState(
        existsGlobally = existsGlobally,
        compatibleWithCurrentSelection = compatibleWithCurrentSelection
    )
}

internal fun productDetailSelectAttributeOption(
    selected: MutableMap<String, String>,
    attributeName: String,
    termSlug: String,
    termName: String,
    variations: List<ProductDetailVariationSnapshot>
) {
    selected[attributeName] = termSlug

    val selectedAfterChange = selected.toMap()
    val conflictingAttributes = selectedAfterChange
        .filterKeys { !attributesMatch(it, attributeName) }
        .filterNot { (selectedAttributeName, selectedValue) ->
            variations.any { variation ->
                variation.availableForPurchase &&
                    variationHasTerm(
                        variation,
                        attributeName,
                        termSlug,
                        termName
                    ) &&
                    variationHasAttributeValue(
                        variation,
                        selectedAttributeName,
                        selectedValue
                    )
            }
        }
        .keys

    conflictingAttributes.forEach(selected::remove)
}

internal fun productDetailToggleAttributeSelection(
    selected: MutableMap<String, String>,
    attributeName: String,
    termSlug: String,
    chosen: Boolean,
    existsGlobally: Boolean,
    termName: String = termSlug,
    variations: List<ProductDetailVariationSnapshot> = emptyList()
) {
    if (chosen) {
        selected.remove(attributeName)
    } else if (existsGlobally) {
        productDetailSelectAttributeOption(
            selected = selected,
            attributeName = attributeName,
            termSlug = termSlug,
            termName = termName,
            variations = variations
        )
    }
}

private fun Modifier.productDetailIncompatibleSlash(
    show: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    color: Color = Color(0xFF8B878B)
): Modifier = if (!show) {
    this
} else {
    clip(shape).drawWithContent {
        drawContent()
        drawLine(
            color = color,
            start = Offset(0f, size.height),
            end = Offset(size.width, 0f),
            strokeWidth = 1.25.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

internal fun productDetailAddButtonEnabled(
    currentCombinationAdded: Boolean,
    canAdd: Boolean,
    quantity: Int,
    minimumQuantity: Int,
    multipleOf: Int,
    availableMaximum: Int?
): Boolean =
    !currentCombinationAdded &&
        canAdd &&
        quantity >= minimumQuantity &&
        (quantity - minimumQuantity) % multipleOf == 0 &&
        (availableMaximum == null || quantity <= availableMaximum)

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
internal fun ProductDetail(product: StoreProduct, variation: StoreProduct?, cartItems: List<CartItem>, loadVariation: (Int) -> Unit, onBack: () -> Unit, onCart: () -> Unit, onSearch: () -> Unit, onAdd: (CartItem) -> Unit) {
    val context = LocalContext.current
    val selected = remember(product.id) { mutableStateMapOf<String, String>() }
    var quantity by remember(product.id) { mutableIntStateOf(1) }
    val selectableAttributes = product.attributes.filter { it.terms.isNotEmpty() }
    val variationSnapshots = product.variations.map { candidate ->
        ProductDetailVariationSnapshot(
            availableForPurchase = candidate.isAvailableForPurchase(),
            attributes = candidate.attributes.map { it.name to it.value }
        )
    }
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

    val currentSelectionKey = productCombinationSelectionKey(
        product.id,
        matching?.id,
        selectedAttributes
    )

    var lastAddedSelectionKey by remember(product.id) {
        mutableStateOf<String?>(null)
    }

    val currentCombinationAdded =
        alreadyAdded ||
        lastAddedSelectionKey == currentSelectionKey
    val canAdd = current != null && isVariationAvailableForPurchase(current.isInStock, current.isPurchasable) &&
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

    val effectiveProduct = current ?: product
    val detailInfoText = HtmlCompat.fromHtml(
        product.description.ifBlank { product.shortDescription },
        HtmlCompat.FROM_HTML_MODE_COMPACT
    ).toString()
    val detailInfoLines = detailInfoText.lines().map { it.trim() }.filter { it.isNotBlank() }
    val detailBrandLine = detailInfoLines.firstOrNull { it.startsWith("Marca:", ignoreCase = true) }
    val detailCompositionLine = detailInfoLines.firstOrNull {
        it.startsWith("Composición:", ignoreCase = true) || it.startsWith("Composicion:", ignoreCase = true)
    }
    val detailRemainingInfo = detailInfoLines.filterNot { it == detailBrandLine || it == detailCompositionLine }.joinToString("\n")
    val galleryImages = (current?.images ?: product.images).ifEmpty { product.images }
    val detailUiState = es.criosrango.shared.StoreProductDetailUiState(
        title = product.name.cleanWooText(),
        displayPrice = if (effectiveProduct.hasDisplayablePrice) effectiveProduct.displayPrice() else "",
        regularPrice = if (effectiveProduct.hasDisplayablePrice) effectiveProduct.regularDisplayPrice() else "",
        showRegularPrice = effectiveProduct.onSale,
        isOnSale = product.onSale,
        attributeGroups = product.attributes.filter { it.terms.isNotEmpty() }.map { attribute ->
            val orderedTerms = if (attribute.name.equals("Tallas", true)) {
                attribute.terms.sortedWith(compareBy({ productSizeSortKey(it.name) }, { it.name }))
            } else attribute.terms
            es.criosrango.shared.StoreProductDetailAttributeGroupUi(
                label = attribute.name,
                options = orderedTerms.map { term ->
                    val chosen = selected[attribute.name]?.let { attributeValuesMatch(it, term.slug) || attributeValuesMatch(it, term.name) } == true
                    val optionState = productDetailAttributeOptionState(variationSnapshots, selected, attribute.name, term.slug, term.name)
                    val existsGlobally = optionState.existsGlobally
                    val compatibleNow = optionState.compatibleWithCurrentSelection
                    es.criosrango.shared.StoreProductDetailOptionUi(
                        value = term.name,
                        selected = chosen,
                        enabled = productDetailAttributeOptionClickable(chosen, existsGlobally),
                        incompatible = existsGlobally && !compatibleNow && !chosen,
                        strikeThrough = attribute.name.equals("Tallas", true) && !existsGlobally,
                        kind = when {
                            attribute.name.equals("Color", true) -> es.criosrango.shared.StoreProductDetailOptionKind.Color
                            attribute.name.equals("Tallas", true) -> es.criosrango.shared.StoreProductDetailOptionKind.Size
                            else -> es.criosrango.shared.StoreProductDetailOptionKind.Default
                        },
                        swatchColor = if (attribute.name.equals("Color", true)) productColorSwatch(term.name) else null
                    )
                }
            )
        },
        quantity = quantity,
        quantityMinusEnabled = true,
        quantityPlusEnabled = availableMaximum == null || quantity + multipleOf <= availableMaximum,
        primaryActionLabel = when {
            product.type == "variable" && matching == null -> "Elige una combinación"
            currentCombinationAdded -> "Añadido al carrito"
            else -> "Añadir al carrito"
        },
        primaryActionEnabled = productDetailAddButtonEnabled(
            currentCombinationAdded = currentCombinationAdded,
            canAdd = canAdd,
            quantity = quantity,
            minimumQuantity = minimumQuantity,
            multipleOf = multipleOf,
            availableMaximum = availableMaximum
        ),
        shareEnabled = true,
        reserveEnabled = product.type != "variable" || matching != null,
        descriptionText = detailRemainingInfo,
        brandText = detailBrandLine,
        compositionText = detailCompositionLine
    )

    StoreProductDetailScreen(
        state = detailUiState,
        cartQuantity = cartItems.sumOf { it.quantity },
        onSearch = onSearch,
        onCart = onCart,
        imageCount = galleryImages.size,
        galleryKey = product.id,
        imageSlot = { index, imageModifier ->
            val item = galleryImages[index]
            CatalogImage(item.src, product.name, imageModifier.clip(RoundedCornerShape(16.dp)), ContentScale.Crop)
        },
        fullscreenImageSlot = { index, imageModifier ->
            AsyncImage(model = galleryImages[index].src, contentDescription = product.name, modifier = imageModifier, contentScale = ContentScale.Fit)
        },
        onBack = onBack,
        onShare = {
            val shareText = buildString {
                append(product.name.cleanWooText())
                if (product.permalink.isNotBlank()) { append("\n\n"); append(product.permalink) }
            }
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Compartir producto"))
        },
        onReserve = {
            val details = selected.entries.filter { it.value.isNotBlank() }.joinToString("\n") { entry ->
                val label = if (entry.key.equals("Tallas", true)) "Talla" else entry.key
                val value = if (entry.key.equals("Color", true)) entry.value.replaceFirstChar { it.uppercase() } else entry.value.uppercase()
                "$label: $value"
            }
            val message = buildString {
                append("Hola, quiero reservar:\n")
                append(product.name.cleanWooText())
                if (details.isNotBlank()) { append("\n"); append(details) }
                if (product.permalink.isNotBlank()) { append("\n"); append(product.permalink) }
            }
            val uri = Uri.parse("https://wa.me/34633246788?text=${Uri.encode(message)}")
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        },
        onAttributeOptionSelected = { groupLabel, value ->
            val attribute = product.attributes.firstOrNull { it.name == groupLabel }
            val term = attribute?.terms?.firstOrNull { it.name == value }
            if (attribute != null && term != null) {
                val chosen = selected[attribute.name]?.let { attributeValuesMatch(it, term.slug) || attributeValuesMatch(it, term.name) } == true
                val optionState = productDetailAttributeOptionState(variationSnapshots, selected, attribute.name, term.slug, term.name)
                productDetailToggleAttributeSelection(
                    selected = selected,
                    attributeName = attribute.name,
                    termSlug = term.slug,
                    chosen = chosen,
                    existsGlobally = optionState.existsGlobally,
                    termName = term.name,
                    variations = variationSnapshots
                )
            }
        },
        onQuantityMinus = { if (limits != null && quantity > minimumQuantity) quantity -= multipleOf },
        onQuantityPlus = { if (availableMaximum == null || quantity + multipleOf <= availableMaximum) quantity += multipleOf },
        onAddToCart = addCurrentProductToCart
    )


}
