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

internal val CATEGORY_PREVIEW_IMAGES = mapOf(
    67 to "https://www.criosrango.es/wp-content/uploads/2026/09/conjunto-peto-estampado-y-jersey-recien-nacida-laurel-XL-4.avif",
    68 to "https://www.criosrango.es/wp-content/uploads/2026/09/conjunto-sudadera-bolsillos-y-pantalon-nina-botella-XL-1.avif",
    70 to "https://www.criosrango.es/wp-content/uploads/2026/05/226_104164026f01_016955_1.webp",
    71 to "https://www.criosrango.es/wp-content/uploads/2026/08/556cado321-46-2.jpg",
    292 to "https://www.criosrango.es/wp-content/uploads/2026/09/conjunto-pantalon-y-blusa-con-chaleco-bebe-violeta-mezcla-XL-4.avif",
    294 to "https://www.criosrango.es/wp-content/uploads/2026/08/chaqueton-pelo-bolsillos-bebe-alaska-XL-1.avif",
    310 to "https://www.criosrango.es/wp-content/uploads/2026/09/polo-combinado-nino-lago-XL-1.avif",
    420 to "https://www.criosrango.es/wp-content/uploads/2026/01/Captura-de-pantalla-2026-01-21-a-las-17.48.57.png",
    445 to "https://www.criosrango.es/wp-content/uploads/2026/09/conjunto-pantalon-y-blusa-con-chaleco-bebe-violeta-mezcla-XL-4.avif",
    446 to "https://www.criosrango.es/wp-content/uploads/2026/09/conjunto-pantalon-y-blusa-con-chaleco-bebe-violeta-mezcla-XL-4.avif",
    447 to "https://www.criosrango.es/wp-content/uploads/2025/12/sueter-caja-topos.jpg",
    448 to "https://www.criosrango.es/wp-content/uploads/2025/12/pantalon-jogger-sarga-bebe-sombra-XL-4.avif",
    449 to "https://www.criosrango.es/wp-content/uploads/2025/10/pantalon-basico-nina-negro-XL-4.avif",
    475 to "https://www.criosrango.es/wp-content/uploads/2026/09/conjunto-pantalon-y-blusa-con-chaleco-bebe-violeta-mezcla-XL-4.avif",
    476 to "https://www.criosrango.es/wp-content/uploads/2025/06/mono-tiffosi.jpg",
    477 to "https://www.criosrango.es/wp-content/uploads/2025/05/mayoral-boys-ss-polo-white-3106-select-size-10-yrs-273862-p-1.jpg",
    478 to "https://www.criosrango.es/wp-content/uploads/2026/03/conjunto-pantalon-largo-y-top-de-rayas-nina-crudo-menta-XL-1-1.avif",
    504 to "https://www.criosrango.es/wp-content/uploads/2026/01/Captura-de-pantalla-2026-01-21-a-las-17.48.57.png",
    509 to "https://www.criosrango.es/wp-content/uploads/2025/11/208-25TR-JOBIM-1-scaled-reczmlo0rsccbsjo2he9f17qguqnu6a9b27sqk1364.webp",
    560 to "https://www.criosrango.es/wp-content/uploads/2026/02/633312_0003Y.jpg"
)

internal enum class HomeOutletSeason {
    WINTER,
    SUMMER
}

internal fun outletSeasonKey(value: String): String =
    java.text.Normalizer
        .normalize(
            value.lowercase().trim(),
            java.text.Normalizer.Form.NFD
        )
        .replace("\\p{Mn}+".toRegex(), "")

internal fun outletCategoriesForSeason(
    categories: List<ProductCategory>,
    season: HomeOutletSeason
): List<ProductCategory> {

    val seasonWord =
        when (season) {
            HomeOutletSeason.WINTER -> "invierno"
            HomeOutletSeason.SUMMER -> "verano"
        }

    return categories
        .filter { it.parent == 445 }
        .filter {
            outletSeasonKey(it.name).contains(seasonWord)
        }
        .distinctBy { it.id }
}

internal fun outletSeasonImageUrl(
    season: HomeOutletSeason,
    categories: List<ProductCategory>,
    products: List<StoreProduct>
): String =
    when (season) {
        HomeOutletSeason.WINTER ->
            "https://www.criosrango.es/wp-content/uploads/2025/11/208-25TR-JOBIM-1-scaled-reczmlo0rsccbsjo2he9f17qguqnu6a9b27sqk1364.webp"

        HomeOutletSeason.SUMMER ->
            "https://www.criosrango.es/wp-content/uploads/2025/05/mayoral-boys-ss-polo-white-3106-select-size-10-yrs-273862-p-1.jpg"
    }

@Composable
internal fun HomeCommercialSectionHeader(
    title: String,
    action: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF183B35)
        )

        Text(
            text = "$action ›",
            color = Color(0xFFD18162),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onAction)
                .padding(horizontal = 6.dp, vertical = 4.dp)
        )
    }
}

@Composable
internal fun HomeOutletSeasonCard(
    title: String,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(112.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFE7E2E6))
            .clickable(onClick = onClick)
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Outlet $title",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.60f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(13.dp)
        ) {
            Text(
                text = "Outlet",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = title,
                color = Color.White.copy(alpha = 0.95f),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
internal fun HomeOutletSection(
    categories: List<ProductCategory>,
    products: List<StoreProduct>,
    onWinter: () -> Unit,
    onSummer: () -> Unit,
    onSeeAll: () -> Unit
) {
    val winterImage =
        remember(categories, products) {
            outletSeasonImageUrl(
                season = HomeOutletSeason.WINTER,
                categories = categories,
                products = products
            )
        }

    val summerImage =
        remember(categories, products) {
            outletSeasonImageUrl(
                season = HomeOutletSeason.SUMMER,
                categories = categories,
                products = products
            )
        }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        HomeCommercialSectionHeader(
            title = "Outlet",
            action = "Ver todo",
            onAction = onSeeAll
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeOutletSeasonCard(
                title = "Invierno",
                imageUrl = winterImage,
                onClick = onWinter,
                modifier = Modifier.weight(1f)
            )

            HomeOutletSeasonCard(
                title = "Verano",
                imageUrl = summerImage,
                onClick = onSummer,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun HomeBrandCard(
    brand: BrandTerm,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = brand.localLogoRes),
            contentDescription = brand.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
internal fun HomeBrandsSection(
    brands: List<BrandTerm>,
    onBrand: (BrandTerm) -> Unit,
    onSeeAll: () -> Unit
) {
    val brandPages = remember(brands) {
        brands
            .filter { it.name.isNotBlank() }
            .chunked(8)
    }

    if (brandPages.isEmpty()) return

    val pagerState = rememberPagerState(
        pageCount = { brandPages.size }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        HomeCommercialSectionHeader(
            title = "Marcas",
            action = "Ver todas",
            onAction = onSeeAll
        )

        Spacer(Modifier.height(12.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val pageBrands = brandPages[page]

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                pageBrands.chunked(4).forEach { rowBrands ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowBrands.forEach { brand ->
                            HomeBrandCard(
                                brand = brand,
                                onClick = { onBrand(brand) },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        repeat(4 - rowBrands.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }

                if (pageBrands.size <= 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(4) {
                            Spacer(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(54.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(brandPages.size) { index ->
                val selected = pagerState.currentPage == index
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (selected) 8.dp else 6.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(
                            if (selected) {
                                Color(0xFF183B35)
                            } else {
                                Color(0xFF183B35).copy(alpha = 0.22f)
                            }
                        )
                )
            }
        }
    }
}


// NOVEDADES_V2_START

internal fun novedadesKey(value: String): String =
    java.text.Normalizer
        .normalize(
            value.lowercase().trim(),
            java.text.Normalizer.Form.NFD
        )
        .replace("\\p{Mn}+".toRegex(), "")


internal fun productMatchesNovedadesAudience(
    product: StoreProduct,
    audience: String,
    allCategories: List<ProductCategory>
): Boolean {

    if (audience == "Todas") return true

    val byId = allCategories.associateBy { it.id }
    val names = mutableSetOf<String>()

    product.categories.forEach { productCategory ->

        names += novedadesKey(productCategory.name)

        var currentId = productCategory.id
        val visited = mutableSetOf<Int>()

        while (
            currentId != 0 &&
            visited.add(currentId)
        ) {
            val category = byId[currentId] ?: break

            names += novedadesKey(category.name)

            currentId = category.parent
        }
    }

    return when (audience) {

        "Niña" ->
            "nina" in names

        "Niño" ->
            "nino" in names

        "Bebé" ->
            "bebe nina" in names ||
            "bebe nino" in names ||
            "recien nacido" in names

        "Mujer" ->
            "mujer" in names

        "Hombre" ->
            "hombre" in names

        else -> true
    }
}


@Composable
internal fun NovedadesScreen(
    products: List<StoreProduct>,
    categories: List<ProductCategory>,
    padding: PaddingValues,
    onBack: () -> Unit,
    onProduct: (StoreProduct) -> Unit
) {
    BackHandler {
        onBack()
    }

    var selected by remember {
        mutableStateOf("Todas")
    }

    var sortMode by remember {
        mutableStateOf(ProductSortMode.RECENT)
    }

    val options = listOf(
        "Todas",
        "Niña",
        "Niño",
        "Bebé",
        "Mujer",
        "Hombre"
    )

    val baseFiltered = remember(
        products,
        categories,
        selected
    ) {
        products.filter {
            productMatchesNovedadesAudience(
                it,
                selected,
                categories
            )
        }
    }

    val filtered =
        sortProducts(
            baseFiltered,
            sortMode
        )

    Column(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.background
            )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 12.dp,
                    end = 20.dp,
                    top = 18.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            IconButton(
                onClick = onBack
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver"
                )
            }

            Column {
                Text(
                    text = "Novedades",
                    style =
                        MaterialTheme.typography.headlineMedium
                )

                Text(
                    text = "Últimas incorporaciones",
                    style =
                        MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(
                    rememberScrollState()
                )
                .padding(horizontal = 20.dp),
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {

            options.forEach { option ->

                val active =
                    selected == option

                OutlinedButton(
                    onClick = {
                        selected = option
                    },
                    shape =
                        RoundedCornerShape(50.dp),
                    colors =
                        androidx.compose.material3
                            .ButtonDefaults
                            .outlinedButtonColors(
                                containerColor =
                                    if (active)
                                        Color(0xFF163B35)
                                    else
                                        Color.Transparent,
                                contentColor =
                                    if (active)
                                        Color.White
                                    else
                                        MaterialTheme
                                            .colorScheme
                                            .onBackground
                            )
                ) {
                    Text(option)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Text(
                text = "${filtered.size} productos",
                style =
                    MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )

            ProductSortControl(
                mode = sortMode,
                onMode = {
                    sortMode = it
                }
            )
        }

        Spacer(Modifier.height(10.dp))

        ProductGrid(
            products = filtered,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            onProduct = onProduct
        )
    }
}

// NOVEDADES_V2_END

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun HomeScreen(
    products: List<StoreProduct>,
    allProducts: List<StoreProduct>,
    roots: List<ProductCategory>,
    padding: PaddingValues,
    onProduct: (StoreProduct) -> Unit,
    onAllCategories: () -> Unit,
    onCategory: (ProductCategory) -> Unit,
    onOutlet: (ProductCategory) -> Unit,
    homeListState: androidx.compose.foundation.lazy.LazyListState,
    showAll: Boolean,
    onShowAllChange: (Boolean) -> Unit
,
    brands: List<BrandTerm>,
    onBrand: (BrandTerm) -> Unit,
    onAllBrands: () -> Unit,
    onOutletWinter: () -> Unit,
    onOutletSummer: () -> Unit,
    onOutletAll: () -> Unit) {
    val outlet = roots.firstOrNull { it.name.normalizedKey().contains("outlet") }
    val rootCategories = roots
        .filter {
            it.parent == 0 &&
            !it.name.equals("Outlet", ignoreCase = true) &&
            !it.slug.equals("outlet", ignoreCase = true)
        }
        .distinctBy { it.id }
    val novedades = remember(products) { products.shuffled().take(8) }
    if (showAll) {
        NovedadesScreen(
            products = products,
            categories = roots,
            padding = padding,
            onBack = { onShowAllChange(false) },
            onProduct = onProduct
        )
        return
    }

    LazyColumn(
        state = homeListState,
        modifier = Modifier.padding(bottom = padding.calculateBottomPadding()).background(MaterialTheme.colorScheme.background), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        item {
            WinterCampaignHero()
        }


        if (!showAll) {
            item {
            HomeSectionTitle("Categorías")
        }

        item {
            CategoryPage(
                categories = rootCategories,
                allCategories = roots,
                allProducts = allProducts,
                onCategory = onCategory
            )
        }

        item {
            Spacer(Modifier.height(18.dp))
        }

        item { HomeSectionTitle("Novedades", "Ver todo") { onShowAllChange(true) } }
            item { ProductCarousel(novedades, onProduct) }
            item {
    HomeOutletSection(
        categories = roots,
        products = allProducts,
        onWinter = onOutletWinter,
        onSummer = onOutletSummer,
        onSeeAll = onOutletAll
    )
}
item {
    HomeBrandsSection(
        brands = brands,
        onBrand = onBrand,
        onSeeAll = onAllBrands
    )
}
        }
    }
}

@Composable
internal fun CategoryPage(
    categories: List<ProductCategory>,
    allCategories: List<ProductCategory>,
    allProducts: List<StoreProduct>,
    onCategory: (ProductCategory) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        categories.chunked(3).forEach { rowCategories ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowCategories.forEach { category ->
                    Box(Modifier.weight(1f)) {
                        CategoryTile(
                            category = category,
                            allCategories = allCategories,
                            allProducts = allProducts,
                            onCategory = onCategory
                        )
                    }
                }

                repeat(3 - rowCategories.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
internal fun HomeSectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF183B35)
        )
        if (action != null && onAction != null) TextButton(onAction) { Text(action, color = Color(0xFFD18162)) }
    }
}

internal fun categoryImageRes(category: ProductCategory): Int? {
    val key = java.text.Normalizer
        .normalize(
            category.name.lowercase(),
            java.text.Normalizer.Form.NFD
        )
        .replace("\\p{Mn}+".toRegex(), "")

    return when {
        key.contains("bautizo") ->
            R.drawable.category_bautizo

        key.contains("bebe") && key.contains("nina") ->
            R.drawable.category_bebe_nina

        key.contains("bebe") && key.contains("nino") ->
            R.drawable.category_bebe_nino

        key.contains("calzado") ->
            R.drawable.category_calzado

        key.contains("comunion") && key.contains("nina") ->
            R.drawable.category_comunion_nina

        key.contains("comunion") && key.contains("nino") ->
            R.drawable.category_comunion_nino

        key.contains("recien") ->
            R.drawable.category_recien_nacido

        key.trim() == "hombre" ->
            R.drawable.category_hombre

        key.trim() == "mujer" ->
            R.drawable.category_mujer

        key.trim() == "nina" ->
            R.drawable.category_nina

        key.trim() == "nino" ->
            R.drawable.category_nino

        else -> null
    }
}


// APPROVED_CATEGORY_CELL_START

internal fun approvedCategoryName(category: ProductCategory): String {
    return when (category.name.lowercase().trim()) {
        "bebe niña", "bebé niña" -> "Bebé niña"
        "bebe niño", "bebé niño" -> "Bebé niño"
        else -> category.name
    }
}

@Composable
internal fun ApprovedCategoryCell(
    category: ProductCategory,
    onClick: () -> Unit
) {
    val imageRes = categoryImageRes(category)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            if (imageRes != null) {
                Image(
                    painter = painterResource(imageRes),
                    contentDescription = approvedCategoryName(category),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(1.dp)
                        .scale(1.16f),
                    contentScale = ContentScale.Fit
                )
            } else {
                CategoryVisual(
                    category,
                    Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = approvedCategoryName(category),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

// APPROVED_CATEGORY_CELL_END

@Composable
internal fun CategoryTile(
    category: ProductCategory,
    allCategories: List<ProductCategory>,
    allProducts: List<StoreProduct>,
    onCategory: (ProductCategory) -> Unit
) {
    ApprovedCategoryCell(
        category = category,
        onClick = {
            onCategory(category)
        }
    )
}

@Composable
internal fun ProductCarousel(products: List<StoreProduct>, onProduct: (StoreProduct) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        items(products, key = { it.id }) { product ->
            Column(Modifier.width(148.dp).clickable { onProduct(product) }) {
                Box {
                    CatalogImage(product.images.firstOrNull()?.src, product.name, Modifier.fillMaxWidth().aspectRatio(.78f).clip(RoundedCornerShape(16.dp)), ContentScale.Crop)
                    if (product.onSale) Text("OFERTA", Modifier.padding(8.dp).background(Color(0xFFD18162), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 4.dp), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                        Text(product.name.cleanWooText(), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(product.displayPrice(), color = Color(0xFF183B35), fontWeight = FontWeight.Bold)
                    if (product.onSale) Text("  ${product.regularDisplayPrice()}", color = Color.Gray, style = MaterialTheme.typography.bodySmall, textDecoration = TextDecoration.LineThrough)
                }
            }
        }
    }
}

@Composable
internal fun OutletFeature(category: ProductCategory, onCategory: (ProductCategory) -> Unit) {
    Card(onClick = { onCategory(category) }, modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE4EFEA))) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryVisual(category, Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)))
            Column(Modifier.padding(start = 16.dp)) { Text("Outlet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF183B35)); Text("Ver Outlet", color = Color(0xFFD18162), fontWeight = FontWeight.SemiBold) }
        }
    }
}



@Composable
internal fun WinterCampaignHero() {

    val images = remember {
        listOf(
            "https://images.pexels.com/photos/6617704/pexels-photo-6617704.jpeg?auto=compress&cs=tinysrgb&w=1600",
            "https://images.pexels.com/photos/19915135/pexels-photo-19915135.jpeg?auto=compress&cs=tinysrgb&w=1600",
            "https://images.pexels.com/photos/30804210/pexels-photo-30804210.jpeg?auto=compress&cs=tinysrgb&w=1600",
            "https://images.pexels.com/photos/29614374/pexels-photo-29614374.jpeg?auto=compress&cs=tinysrgb&w=1600"
        )
    }

    var heroIndex by remember {
        mutableStateOf(0)
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3500)
            heroIndex = (heroIndex + 1) % images.size
        }
    }

    Crossfade(
        targetState = heroIndex,
        animationSpec = tween(durationMillis = 900)
    ) { index ->

        AsyncImage(
            model = images[index],
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f),
            contentScale = ContentScale.Crop
        )
    }
}
