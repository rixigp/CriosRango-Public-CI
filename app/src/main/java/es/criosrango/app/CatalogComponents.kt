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

// PRODUCT_SORT_START

internal enum class ProductSortMode(
    val label: String
) {
    RECENT("Más recientes"),
    PRICE_ASC("Precio: menor a mayor"),
    PRICE_DESC("Precio: mayor a menor"),
    NAME_ASC("Nombre A-Z")
}

internal fun sortProducts(
    products: List<StoreProduct>,
    mode: ProductSortMode
): List<StoreProduct> =
    when (mode) {

        ProductSortMode.RECENT ->
            products

        ProductSortMode.PRICE_ASC ->
            products.sortedBy {
                it.prices.price.toLongOrNull()
                    ?: Long.MAX_VALUE
            }

        ProductSortMode.PRICE_DESC ->
            products.sortedByDescending {
                it.prices.price.toLongOrNull()
                    ?: Long.MIN_VALUE
            }

        ProductSortMode.NAME_ASC ->
            products.sortedBy {
                it.name.lowercase()
            }
    }

@Composable
internal fun ProductSortControl(
    mode: ProductSortMode,
    onMode: (ProductSortMode) -> Unit
) {
    var expanded by remember {
        mutableStateOf(false)
    }

    Row(
        verticalAlignment =
            Alignment.CenterVertically
    ) {
        Text(
            text = "Ordenar por",
            style =
                MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )

        Box {
            androidx.compose.material3.TextButton(
                onClick = {
                    expanded = true
                }
            ) {
                Text(
                    text = "${mode.label}  ▾",
                    color =
                        MaterialTheme.colorScheme.onBackground
                )
            }

            androidx.compose.material3.DropdownMenu(
                expanded = expanded,
                onDismissRequest = {
                    expanded = false
                }
            ) {
                ProductSortMode.values().forEach { option ->

                    androidx.compose.material3.DropdownMenuItem(
                        text = {
                            Text(option.label)
                        },
                        onClick = {
                            expanded = false
                            onMode(option)
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun SortableProductGrid(
    products: List<StoreProduct>,
    modifier: Modifier = Modifier,
    onProduct: (StoreProduct) -> Unit
) {
    var sortMode by remember {
        mutableStateOf(ProductSortMode.RECENT)
    }

    val sorted =
        sortProducts(products, sortMode)

    Column(modifier) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 20.dp,
                    vertical = 8.dp
                ),
            horizontalArrangement =
                Arrangement.End
        ) {
            ProductSortControl(
                mode = sortMode,
                onMode = {
                    sortMode = it
                }
            )
        }

        ProductGrid(
            sorted,
            Modifier.weight(1f),
            onProduct
        )
    }
}

// PRODUCT_SORT_END
