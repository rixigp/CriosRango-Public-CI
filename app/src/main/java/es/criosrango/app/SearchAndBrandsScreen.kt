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

internal fun String.decodeProductHtml(): String =
    android.text.Html.fromHtml(
        this,
        android.text.Html.FROM_HTML_MODE_LEGACY
    ).toString()

internal fun String.searchNormalized(): String =
    java.text.Normalizer
        .normalize(this, java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase(java.util.Locale.ROOT)
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()

internal fun searchDistance(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length

    var previous = IntArray(b.length + 1) { it }

    for (i in a.indices) {
        val current = IntArray(b.length + 1)
        current[0] = i + 1

        for (j in b.indices) {
            val cost = if (a[i] == b[j]) 0 else 1
            current[j + 1] = minOf(
                current[j] + 1,
                previous[j + 1] + 1,
                previous[j] + cost
            )
        }

        previous = current
    }

    return previous[b.length]
}

internal fun searchTokenMatches(token: String, text: String): Boolean {
    if (token.isBlank()) return true
    if (text.contains(token)) return true

    if (token.length < 4) return false

    val maxDistance = if (token.length >= 7) 2 else 1

    return text.split(" ").any { word ->
        word.isNotBlank() &&
            kotlin.math.abs(word.length - token.length) <= maxDistance &&
            searchDistance(word, token) <= maxDistance
    }
}

internal fun productSearchText(product: StoreProduct): String =
    buildString {
        append(product.name.decodeProductHtml()).append(' ')

        product.categories.forEach {
            append(it.name.decodeProductHtml()).append(' ')
            append(it.slug).append(' ')
        }

        product.tags.forEach {
            append(it.name.decodeProductHtml()).append(' ')
            append(it.slug).append(' ')
        }

        product.attributes.forEach { attribute ->
            append(attribute.name.decodeProductHtml()).append(' ')
            attribute.taxonomy?.let {
                append(it).append(' ')
            }
            attribute.terms.forEach {
                append(it.name.decodeProductHtml()).append(' ')
                append(it.slug).append(' ')
            }
        }
    }.searchNormalized()

internal fun productMatchesSearch(
    product: StoreProduct,
    normalizedQuery: String
): Boolean {
    if (normalizedQuery.isBlank()) return false

    val text = productSearchText(product)
    val tokens = normalizedQuery
        .split(" ")
        .filter { it.isNotBlank() }

    return tokens.all { searchTokenMatches(it, text) }
}

internal fun String.decodeBrandEntities(): String {
    return this
        .replace("&amp;", "&")
        .replace("&#038;", "&")
        .replace("&nbsp;", " ")
        .trim()
}

@Composable
internal fun BrandProductsScreen(
    brand: BrandTerm,
    products: List<StoreProduct>,
    loading: Boolean,
    padding: PaddingValues,
    onBack: () -> Unit,
    onProduct: (StoreProduct) -> Unit
) {
    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding()
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver"
                )
            }
            Text(
                text = brand.name,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
        }
        if (loading && products.isEmpty()) {
            ProductSkeletonGrid(Modifier.fillMaxSize())
        } else if (products.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("No hay productos disponibles de esta marca")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(products, key = { it.id }) { product ->
                    ProductCard(
                        product = product,
                        onProduct = onProduct
                    )
                }
            }
        }
    }
}


@Composable
internal fun AllBrandsScreen(
    brands: List<BrandTerm>,
    padding: PaddingValues,
    onBrand: (BrandTerm) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding()
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás")
            }
            Text("Todas las marcas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 16.dp,
                bottom = 24.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(brands, key = { it.slug }) { brand ->
                HomeBrandCard(
                    brand = brand,
                    onClick = { onBrand(brand) },
                    modifier = Modifier.height(72.dp)
                )
            }
        }
    }
}

@Composable
internal fun SearchScreen(
    products: List<StoreProduct>,
    allProducts: List<StoreProduct>,
    categories: List<ProductCategory>,
    brands: List<BrandTerm>,
    padding: PaddingValues,
    search: (String) -> Unit,
    onProduct: (StoreProduct) -> Unit,
    onCategory: (ProductCategory) -> Unit,
    loading: Boolean
,
    initialQuery: String = "",
    selectedBrand: BrandTerm? = null,
    showAllBrands: Boolean = false,
    onBackFromAllBrands: () -> Unit = {},
    onBrandFromAllBrands: (BrandTerm) -> Unit = {}) {
    if (showAllBrands) {
        AllBrandsScreen(
            brands = APP_BRANDS,
            padding = padding,
            onBrand = onBrandFromAllBrands,
            onBack = onBackFromAllBrands
        )
        return
    }

    var query by remember(initialQuery) {
        mutableStateOf(initialQuery)
    }
    var brandModeActive by remember(initialQuery, selectedBrand) {
        mutableStateOf(selectedBrand != null && initialQuery.isNotBlank())
    }

    LaunchedEffect(query, brandModeActive) {
        val clean = query.trim()
        if (brandModeActive && selectedBrand != null && clean == selectedBrand.name) {
            return@LaunchedEffect
        }
        if (clean.isBlank()) {
            search("")
        } else {
            kotlinx.coroutines.delay(350)
            search(clean)
        }
    }

    val normalizedQuery = query.searchNormalized()

    val displayedProducts = remember(
        products,
        allProducts,
        normalizedQuery
    ) {
        if (brandModeActive && selectedBrand != null) {
            products.distinctBy { it.id }
        } else if (normalizedQuery.isBlank()) {
            emptyList()
        } else {
            (products + allProducts)
                .distinctBy { it.id }
                .filter {
                    productMatchesSearch(it, normalizedQuery)
                }
                .sortedByDescending { product ->
                    val name = product.name.searchNormalized()
                    val tokens = normalizedQuery
                        .split(" ")
                        .filter { it.isNotBlank() }

                    when {
                        name == normalizedQuery -> 1000
                        name.startsWith(normalizedQuery) -> 800
                        name.contains(normalizedQuery) -> 600
                        else -> tokens.count { name.contains(it) } * 100
                    }
                }
        }
    }

    val categorySuggestions = remember(
        categories,
        normalizedQuery
    ) {
        if (normalizedQuery.length < 2) {
            emptyList()
        } else {
            val tokens = normalizedQuery
                .split(" ")
                .filter { it.isNotBlank() }

            categories
                .filter { category ->
                    val text = category.name.searchNormalized()
                    tokens.any {
                        searchTokenMatches(it, text)
                    }
                }
                .distinctBy { it.name.searchNormalized() }
                .take(2)
        }
    }

    val brandSuggestions = remember(
        brands,
        normalizedQuery
    ) {
        if (normalizedQuery.length < 2) {
            emptyList()
        } else {
            val tokens = normalizedQuery
                .split(" ")
                .filter { it.isNotBlank() }

            brands
                .filter { brand ->
                    val text = brand.name.searchNormalized()
                    tokens.any {
                        searchTokenMatches(it, text)
                    }
                }
                .take(2)
        }
    }

    Column(
        modifier = Modifier.padding(padding)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                brandModeActive = false
                query = it
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            placeholder = {
                Text("Buscar prendas, marcas...")
            },
            leadingIcon = {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { query = "" }
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Limpiar"
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp)
        )

        if (query.isBlank()) {
            Text(
                text = "Busca tu próxima prenda favorita",
                modifier = Modifier.padding(
                    horizontal = 20.dp
                ),
                color = Color.Gray
            )
        } else {
            val productSuggestions = displayedProducts.take(2)

            if (
                categorySuggestions.isNotEmpty() ||
                brandSuggestions.isNotEmpty() ||
                productSuggestions.isNotEmpty()
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 20.dp
                    )
                ) {
                    Text(
                        text = "Sugerencias",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    categorySuggestions.forEach { category ->
                        TextButton(
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                            onClick = {
                                onCategory(category)
                            }
                        ) {
                            Text("Categoría · ${category.name}")
                        }
                    }

                    brandSuggestions.forEach { brand ->
                        TextButton(
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                            onClick = {
                                query = brand.name
                            }
                        ) {
                            Text("Marca · ${brand.name}")
                        }
                    }

                    productSuggestions.forEach { product ->
                        TextButton(
                            modifier = Modifier.height(30.dp),
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                            onClick = {
                                onProduct(product)
                            }
                        ) {
                            Text(product.name.decodeProductHtml())
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
            }

            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (!loading && displayedProducts.isEmpty()) {
                Text(
                    text = "No hemos encontrado productos",
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 0.dp),
                    color = Color.Gray
                )
            } else if (displayedProducts.isNotEmpty()) {
                SortableProductGrid(
                    products = displayedProducts,
                    modifier = Modifier.fillMaxSize(),
                    onProduct = onProduct
                )
            }
        }
    }
}

@Composable
internal fun ProductSkeletonGrid(modifier: Modifier) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
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
    onProduct: (StoreProduct) -> Unit
) {
    val gridState =
        androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val categoryStatus by categoryCatalogLoadStatus.collectAsStateWithLifecycle()

    val productOrder = products.map { it.id }

    LaunchedEffect(productOrder) {
        if (products.isNotEmpty()) {
            gridState.scrollToItem(0)
        }
    }

    if (products.isEmpty() && categoryStatus.state == CategoryLoadState.LOADING) {
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
            contentPadding = PaddingValues(20.dp),
            horizontalArrangement =
                Arrangement.spacedBy(12.dp),
            verticalArrangement =
                Arrangement.spacedBy(18.dp)
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
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(.78f)
        ) {
            CatalogImage(
                product.images.firstOrNull()?.src,
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
            Text(
                product.name.cleanWooText(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (product.hasDisplayablePrice) {
            Row(
                Modifier.fillMaxWidth().height(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    product.displayPrice(),
                    color = Color(0xFF183B35),
                    fontWeight = FontWeight.Bold
                )
                if (product.onSale) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        product.regularDisplayPrice(),
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        textDecoration = TextDecoration.LineThrough
                    )
                }
            }
        }
    }
}
