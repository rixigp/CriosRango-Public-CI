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
import coil.compose.AsyncImage
import compose.icons.TablerIcons
import compose.icons.tablericons.Bed
import compose.icons.tablericons.Hanger
import compose.icons.tablericons.Shirt
import compose.icons.tablericons.Tag
import kotlinx.coroutines.launch

internal fun String.decodeProductHtml(): String =
    android.text.Html.fromHtml(
        this,
        android.text.Html.FROM_HTML_MODE_LEGACY
    ).toString()

internal fun String.decodeBrandEntities(): String = this
    .replace("&amp;", "&")
    .replace("&#038;", "&")
    .replace("&nbsp;", " ")
    .trim()

@Composable
internal fun ProductSkeletonGrid(modifier: Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(6) {
            Column(Modifier.fillMaxWidth()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(.78f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFE8E5DF))
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth(.82f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFE8E5DF))
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth(.48f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFE8E5DF))
                )
            }
        }
    }
}

@Composable
internal fun ProductGrid(
    products: List<StoreProduct>,
    modifier: Modifier = Modifier,
    onProduct: (StoreProduct) -> Unit,
    pagingState: es.criosrango.shared.CatalogPagingState<StoreProduct>? = null,
    onLoadNextPage: (() -> Unit)? = null,
    resetKey: Any? = null
) {
    val gridState =
        androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val categoryStatus by categoryCatalogLoadStatus.collectAsStateWithLifecycle()

    val effectiveResetKey = resetKey
    var previousResetKey by remember { mutableStateOf(effectiveResetKey) }

    LaunchedEffect(effectiveResetKey) {
        if (previousResetKey != effectiveResetKey) {
            if (products.isNotEmpty()) {
                gridState.scrollToItem(0)
            }
            previousResetKey = effectiveResetKey
        }
    }

    if (pagingState != null && onLoadNextPage != null) {
        val latestPagingState by rememberUpdatedState(pagingState)
        val latestProductsSize by rememberUpdatedState(products.size)
        val latestLoadNextPage by rememberUpdatedState(onLoadNextPage)
        val prefetchDistance = es.criosrango.shared.CatalogPaginator.PREFETCH_DISTANCE

        LaunchedEffect(gridState, resetKey) {
            snapshotFlow {
                val state = latestPagingState
                val itemCount = latestProductsSize
                val lastVisibleIndex =
                    gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1

                itemCount > 0 &&
                    !state.isInitialLoading &&
                    !state.isAppending &&
                    state.appendError == null &&
                    state.hasMore &&
                    lastVisibleIndex >= itemCount - 1 - prefetchDistance
            }.collect { shouldLoad ->
                if (shouldLoad) {
                    latestLoadNextPage()
                }
            }
        }
    }

    val initialPagingLoading = pagingState?.isInitialLoading == true

    if (products.isEmpty() && (initialPagingLoading || categoryStatus.state == CategoryLoadState.LOADING)) {
        ProductSkeletonGrid(modifier)
    } else if (products.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No encontramos productos",
                color = Color.Gray
            )
        }
    } else {
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns =
                androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
            modifier = modifier,
            state = gridState,
            contentPadding = PaddingValues(start = 20.dp, top = 4.dp, end = 20.dp, bottom = 20.dp),
            horizontalArrangement =
                Arrangement.spacedBy(12.dp),
            verticalArrangement =
                Arrangement.spacedBy(14.dp)
        ) {
            items(
                products,
                key = { it.id }
            ) { product ->
                ProductCard(
                    product,
                    onProduct,
                    onImageReady = { categoryStatus.categoryId?.let { CategoryLoadTelemetry.firstImage(it, product.id) } }
                )
            }

            if (pagingState?.isAppending == true) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp))
                    }
                }
            } else if (pagingState?.appendError != null && pagingState.hasMore && onLoadNextPage != null) {
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onLoadNextPage) {
                            Text("Reintentar")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ProductCard(product: StoreProduct, onProduct: (StoreProduct) -> Unit, onImageReady: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onProduct(product) }
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .aspectRatio(.78f)
        ) {
            val targetWidthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
            val image = product.images.firstOrNull()
            CatalogImage(
                selectResponsiveImageUrl(
                    src = image?.src.orEmpty(),
                    thumbnail = image?.thumbnail.orEmpty(),
                    srcSet = image?.srcSet.orEmpty(),
                    targetWidthPx = targetWidthPx
                ),
                product.name,
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp)),
                ContentScale.Crop,
                onImageReady
            )
            if (product.onSale) {
                Text(
                    "OFERTA",
                    Modifier
                        .padding(8.dp)
                        .background(Color(0xFFD18162), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.TopStart) {
            Text(product.name.cleanWooText(), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        }
        if (product.hasDisplayablePrice) {
            Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(product.displayPrice(), color = Color(0xFF183B35), fontWeight = FontWeight.Bold)
                if (product.onSale) {
                    Spacer(Modifier.width(8.dp))
                    Text(product.regularDisplayPrice(), color = Color.Gray, style = MaterialTheme.typography.bodySmall, textDecoration = TextDecoration.LineThrough)
                }
            }
        }
    }
}
