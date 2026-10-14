package es.criosrango.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import compose.icons.TablerIcons
import compose.icons.tablericons.Bed
import compose.icons.tablericons.Hanger
import compose.icons.tablericons.Shirt
import compose.icons.tablericons.Tag
import es.criosrango.shared.model.countFor
import es.criosrango.shared.OutletBubbleDefinition
import es.criosrango.shared.fixedOutletBubbles
import es.criosrango.shared.outletBubbleDisplayLabel
import es.criosrango.shared.StoreOutletBubbles
import es.criosrango.shared.outletCatalogFilterKey

// OUTLET_CATEGORY_BUBBLES_START

internal fun productBelongsToCategory(
    product: StoreProduct,
    categoryId: Int,
    categoriesById: Map<Int, ProductCategory>
): Boolean {

    return product.categories.any { assigned ->

        var currentId = assigned.id
        val visited = mutableSetOf<Int>()

        while (
            currentId != 0 &&
            visited.add(currentId)
        ) {
            if (currentId == categoryId) {
                return@any true
            }

            currentId =
                categoriesById[currentId]?.parent
                    ?: 0
        }

        false
    }
}

internal fun isCategoryDescendantOf(
    categoryId: Int,
    ancestorId: Int,
    categoriesById: Map<Int, ProductCategory>
): Boolean {
    var currentId = categoryId
    val visited = mutableSetOf<Int>()
    while (currentId != 0 && visited.add(currentId)) {
        if (currentId == ancestorId) return true
        currentId = categoriesById[currentId]?.parent ?: 0
    }
    return false
}

internal fun productBelongsToOutletOriginCategory(
    product: StoreProduct,
    categoryId: Int,
    categoriesById: Map<Int, ProductCategory>
): Boolean {

    val assignedCategoryIds =
        product.categories.map { it.id } + product.originalCategoryIds

    return assignedCategoryIds.any { assignedId ->

        var currentId = assignedId
        val visited = mutableSetOf<Int>()

        while (
            currentId != 0 &&
            visited.add(currentId)
        ) {
            if (currentId == categoryId) {
                return@any true
            }

            currentId =
                categoriesById[currentId]?.parent
                    ?: 0
        }

        false
    }
}

internal fun productBelongsToOutletBubble(
    product: StoreProduct,
    bubble: OutletBubbleDefinition,
    categoriesById: Map<Int, ProductCategory>
): Boolean =
    bubble.categoryIds.any { categoryId ->
        productBelongsToOutletOriginCategory(
            product = product,
            categoryId = categoryId,
            categoriesById = categoriesById
        )
    }

@Composable
internal fun OutletCatalogStateAdapter(
    current: ProductCategory?,
    products: List<StoreProduct>,
    availabilityStore: OutletAvailabilityStore,
    allCategories: List<ProductCategory>,
    modifier: Modifier = Modifier,
    onProduct: (StoreProduct) -> Unit,
    pagingState: es.criosrango.shared.CatalogPagingState<StoreProduct>? = null,
    onLoadNextPage: (() -> Unit)? = null,
    pagingKey: Any? = null,
    headerTitle: String? = null,
    headerOnBack: (() -> Unit)? = null,
    headerContent: (@Composable ColumnScope.() -> Unit)? = null,
    compactHeaderSpacing: Boolean = false,
    onRetryInitial: (() -> Unit)? = null
) {

    // Solo las categorías finales dentro de Outlet.
    if (current?.parent != 445) {
        val isOutletPath = current != null &&
            isCategoryDescendantOf(current.id, 445, allCategories.associateBy { it.id })
        CatalogFilteredProductGrid(
            products = products,
            modifier = modifier,
            onProduct = onProduct,
            pagingState = pagingState,
            onLoadNextPage = onLoadNextPage,
            pagingKey = pagingKey,
            headerTitle = headerTitle,
            headerOnBack = headerOnBack,
            headerContent = headerContent,
            // Outlet's final dialog is always the shared owner. The normal
            // Catalog keeps its existing defaults and presentation.
            sharedFilterDialog = true,
            useOutletFilterKey = isOutletPath,
            useSharedProductListHeaderContent = isOutletPath,
            onRetryInitial = onRetryInitial
        )
        return
    }

    val categoriesById =
        remember(allCategories) {
            allCategories.associateBy { it.id }
        }

    // Las burbujas son definiciones fijas de Kotlin. Su visibilidad procede
    // únicamente del snapshot persistido/dinámico de disponibilidad.
    val bubbles =
        remember(current.id) {
            fixedOutletBubbles(current.id)
        }

    val availability by availabilityStore.snapshot.collectAsStateWithLifecycle()

    LaunchedEffect(current.id) {
        runCatching {
            availabilityStore.refresh()
        }
    }

    val hasValidAvailabilitySnapshot =
        availability?.schemaVersion == 1

    val visibleBubbles =
        remember(bubbles, availability) {
            if (!hasValidAvailabilitySnapshot) {
                bubbles
            } else {
                bubbles.filter { bubble ->
                    bubble.categoryIds.any { categoryId ->
                        availability?.countFor(current.id, categoryId)?.let { it > 0 } == true
                    }
                }
            }
        }

    var selectedBubbleKey by androidx.compose.runtime.saveable.rememberSaveable(current.id) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(selectedBubbleKey, visibleBubbles) {
        if (
            selectedBubbleKey != null &&
            visibleBubbles.none { it.key == selectedBubbleKey }
        ) {
            selectedBubbleKey = null
        }
    }

    val selectedBubble =
        remember(visibleBubbles, selectedBubbleKey) {
            visibleBubbles.firstOrNull { bubble ->
                bubble.key == selectedBubbleKey
            }
        }

    val visibleProducts =
        remember(
            products,
            selectedBubble,
            categoriesById
        ) {
            if (selectedBubble == null) {
                products
            } else {
                products.filter { product ->
                    productBelongsToOutletBubble(
                        product = product,
                        bubble = selectedBubble,
                        categoriesById = categoriesById
                    )
                }
            }
        }

    // Si la burbuja seleccionada no tiene coincidencias en las páginas ya cargadas,
    // seguimos usando el paginador A2 existente hasta encontrar la primera o agotar
    // la categoría. No se cambia CategoryProductsPageDataSource ni su page/perPage.
    val shouldProbeMorePages =
        selectedBubble != null &&
            visibleProducts.isEmpty() &&
            pagingState != null &&
            !pagingState.isInitialLoading &&
            !pagingState.isAppending &&
            pagingState.appendError == null &&
            pagingState.hasMore &&
            onLoadNextPage != null

    LaunchedEffect(
        current.id,
        selectedBubbleKey,
        products.size,
        pagingState?.currentPage,
        pagingState?.hasMore,
        pagingState?.isAppending,
        pagingState?.appendError
    ) {
        if (shouldProbeMorePages) {
            onLoadNextPage?.invoke()
        }
    }

    Column(modifier) {
        val probingSelectedBubble =
            selectedBubble != null &&
                visibleProducts.isEmpty() &&
                pagingState?.appendError == null &&
                (pagingState?.isInitialLoading == true ||
                    pagingState?.isAppending == true ||
                    pagingState?.hasMore == true)

        if (probingSelectedBubble) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp)
                )
            }
        } else {
            CatalogFilteredProductGrid(
                products = visibleProducts,
                modifier = Modifier.weight(1f),
                onProduct = onProduct,
                pagingState = pagingState,
                onLoadNextPage = onLoadNextPage,
                pagingKey = listOf(
                    pagingKey,
                    selectedBubbleKey
                ),
                headerTitle = headerTitle,
                headerOnBack = headerOnBack,
                headerContent = {
                    StoreOutletBubbles(
                        bubbles = visibleBubbles,
                        selectedKey = selectedBubbleKey,
                        onSelected = { selectedBubbleKey = it }
                    )
                },
                sharedFilterDialog = true,
                useOutletFilterKey = true,
                useSharedProductListHeaderContent = true

            )
        }
    }
}

// OUTLET_CATEGORY_BUBBLES_END

@Composable
internal fun CategoriesScreen(categories: List<ProductCategory>, products: List<StoreProduct>, path: MutableList<Int>, padding: PaddingValues, loading: Boolean, categoryPagingState: es.criosrango.shared.CatalogPagingState<StoreProduct>, loadNextCategoryPage: () -> Unit, loadCategory: (Int) -> Unit, loadCategoryTree: (Int) -> Unit, onProduct: (StoreProduct) -> Unit, availabilityStore: OutletAvailabilityStore, onRootBack: (() -> Unit)? = null, onOpen: (ProductCategory) -> Unit,
    outletSeasonFilter: HomeOutletSeason? = null, onSingleLevelBack: (() -> Unit)? = null, onRootRetry: (() -> Unit)? = null) {
    val currentId = path.lastOrNull()
    val current = categories.firstOrNull { it.id == currentId }
    val rawChildren =
        categories.filter {
            it.parent == currentId
        }

    val children =
        if (
            currentId == 445 &&
            outletSeasonFilter != null
        ) {
            val seasonWord =
                when (outletSeasonFilter) {
                    HomeOutletSeason.WINTER -> "invierno"
                    HomeOutletSeason.SUMMER -> "verano"
                }

            rawChildren.filter {
                outletSeasonKey(it.name).contains(seasonWord)
            }
        } else {
            rawChildren
        }
    var showOutletProductsFromFilters by remember(currentId) { mutableStateOf(false) }
    val currentIsOutletPath = current != null &&
        isCategoryDescendantOf(current.id, 445, categories.associateBy { it.id })

    val onCategoriesBack: () -> Unit = {
        if (showOutletProductsFromFilters) {
            showOutletProductsFromFilters = false
        } else if (path.size == 1 && onSingleLevelBack != null) {
            onSingleLevelBack()
        } else if (path.isNotEmpty()) {
            path.removeAt(path.lastIndex)
        } else {
            onRootBack?.invoke()
        }
        Unit
    }

    BackHandler(enabled = true) {
        onCategoriesBack()
    }
    LaunchedEffect(currentId, children.size) {
        if (currentId != null) {
            if (children.isEmpty()) loadCategory(currentId)
            else loadCategoryTree(currentId)
        }
    }
    if (path.isEmpty()) {
        CategoryList(
            categories.filter { it.parent == 0 },
            categories,
            products,
            padding,
            loading,
            onOpen,
            onBack = onCategoriesBack,
            onRetry = onRootRetry ?: onCategoriesBack,
            showEmptyRetry = true
        )
    } else if (children.isNotEmpty() && currentIsOutletPath && !showOutletProductsFromFilters && current != null) {
        es.criosrango.shared.StoreSubcategoriesScreen(
            parent = es.criosrango.shared.model.StoreCategory(
                id = current.id, parent = current.parent, name = current.name, slug = current.slug, count = current.count
            ),
            categories = children.map { child ->
                es.criosrango.shared.model.StoreCategory(
                    id = child.id, parent = child.parent, name = child.name, slug = child.slug, count = child.count
                )
            },
            loading = loading,
            onBack = onCategoriesBack,
            onCategory = { child -> categories.firstOrNull { it.id == child.id }?.let(onOpen) },
            onFilterClick = { showOutletProductsFromFilters = true },
            onRetry = { loadCategoryTree(current.id) },
            modifier = Modifier.fillMaxSize().padding(padding)
        )
    } else if (children.isNotEmpty() && !showOutletProductsFromFilters) {
        CategoryParentWithFilters(
            title = current?.let { approvedCategoryName(it) } ?: "Categorías",
            children = children,
            allCategories = categories,
            products = products,
            padding = padding,
            loading = loading,
            onBack = onCategoriesBack,
            onOpen = onOpen,
            onProduct = onProduct,
            pagingState = categoryPagingState,
            onLoadNextPage = loadNextCategoryPage,
            onRetryInitial = { current?.id?.let(loadCategory) }
        )
    } else {
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutletCatalogStateAdapter(
                current = current,
                products = products,
                allCategories = categories,
                availabilityStore = availabilityStore,
                modifier = Modifier.fillMaxSize(),
                onProduct = onProduct,
                pagingState = categoryPagingState,
                onLoadNextPage = loadNextCategoryPage,
                pagingKey = currentId,
                headerTitle = current?.name ?: "Productos",
                headerOnBack = onCategoriesBack,
                onRetryInitial = current?.takeIf {
                    it.id != 445 && !isCategoryDescendantOf(it.id, 445, categories.associateBy { category -> category.id })
                }?.let { category -> { loadCategory(category.id) } }
            )
        }
    }
}

@Composable
internal fun CategoryList(categories: List<ProductCategory>, allCategories: List<ProductCategory>, products: List<StoreProduct>,
    padding: PaddingValues, loading: Boolean, onOpen: (ProductCategory) -> Unit, onBack: () -> Unit, onRetry: (() -> Unit)?, showEmptyRetry: Boolean) {
    val visibleCategories = categories.filter { it.parent == 0 && !it.name.equals("Outlet", true) && !it.slug.equals("outlet", true) }.distinctBy { it.id }
    es.criosrango.shared.StoreCategoriesScreen(
        categories = visibleCategories.map { es.criosrango.shared.model.StoreCategory(id=it.id,parent=it.parent,name=it.name,slug=it.slug,count=it.count) },
        loading = loading, errorMessage = null, onBack = onBack,
        onCategory = { selected -> visibleCategories.firstOrNull { it.id == selected.id }?.let(onOpen) },
        onRetry = onRetry, showEmptyRetry = showEmptyRetry, modifier = Modifier.padding(padding)
    )
}

internal fun subcategoryImageRes(
    category: ProductCategory,
    allCategories: List<ProductCategory>
): Int? {

    fun clean(value: String): String =
        java.text.Normalizer
            .normalize(
                value.lowercase().trim(),
                java.text.Normalizer.Form.NFD
            )
            .replace("\\p{Mn}+".toRegex(), "")

    val child = clean(category.name)

    val parent = allCategories
        .firstOrNull { it.id == category.parent }
        ?.name
        ?.let(::clean)
        .orEmpty()

    return when {

        // BEBÉ NIÑA
        parent == "bebe nina" &&
        child == "abrigos y cazadoras" ->
            R.drawable.subcat_bebe_nina_abrigos_cazadoras

        parent == "bebe nina" &&
        child == "ropa de bano" ->
            R.drawable.subcat_bebe_nina_ropa_bano

        parent == "bebe nina" &&
        child == "ropa de sport" ->
            R.drawable.subcat_bebe_nina_ropa_sport

        parent == "bebe nina" &&
        child == "ropa de vestir" ->
            R.drawable.subcat_bebe_nina_ropa_vestir


        // BEBÉ NIÑO
        parent == "bebe nino" &&
        child == "abrigos y cazadoras" ->
            R.drawable.subcat_bebe_nino_abrigos_cazadoras

        parent == "bebe nino" &&
        child == "ropa de bano" ->
            R.drawable.subcat_bebe_nino_ropa_bano

        parent == "bebe nino" &&
        child == "ropa de sport" ->
            R.drawable.subcat_bebe_nino_ropa_sport

        parent == "bebe nino" &&
        child == "ropa de vestir" ->
            R.drawable.subcat_bebe_nino_ropa_vestir


        // NIÑA
        parent == "nina" &&
        child == "abrigos y cazadoras" ->
            R.drawable.subcat_nina_abrigos_cazadoras

        parent == "nina" &&
        child == "calzado" ->
            R.drawable.subcat_nina_calzado

        parent == "nina" &&
        child == "ropa de bano" ->
            R.drawable.subcat_nina_ropa_bano

        parent == "nina" &&
        child == "ropa de sport" ->
            R.drawable.subcat_nina_ropa_sport

        parent == "nina" &&
        child == "ropa de vestir" ->
            R.drawable.subcat_nina_ropa_vestir


        // NIÑO
        parent == "nino" &&
        child == "abrigos y cazadoras" ->
            R.drawable.subcat_nino_abrigos_cazadoras

        parent == "nino" &&
        child == "ropa de bano" ->
            R.drawable.subcat_nino_ropa_bano

        parent == "nino" &&
        child == "ropa de sport" ->
            R.drawable.subcat_nino_ropa_sport

        parent == "nino" &&
        child == "ropa de vestir" ->
            R.drawable.subcat_nino_ropa_vestir


        // RECIÉN NACIDO
        parent == "recien nacido" &&
        child == "abrigos y cazadoras" ->
            R.drawable.subcat_recien_nacido_abrigos_cazadoras

        parent == "recien nacido" &&
        child == "bodys" ->
            R.drawable.subcat_recien_nacido_bodys

        parent == "recien nacido" &&
        child == "complementos" ->
            R.drawable.subcat_recien_nacido_complementos

        parent == "recien nacido" &&
        child == "jesusitos y vestidos" ->
            R.drawable.subcat_recien_nacido_jesusitos_vestidos

        parent == "recien nacido" &&
        child == "peleles y conjuntos" ->
            R.drawable.subcat_recien_nacido_peleles_conjuntos


        // HOMBRE
        parent == "hombre" &&
        child == "abrigos y cazadoras" ->
            R.drawable.subcat_hombre_abrigos_cazadoras

        parent == "hombre" &&
        child == "americanas y trajes" ->
            R.drawable.subcat_hombre_americanas_trajes

        parent == "hombre" &&
        child == "camisas" ->
            R.drawable.subcat_hombre_camisas

        parent == "hombre" &&
        child == "camisetas y polos" ->
            R.drawable.subcat_hombre_camisetas_polos

        parent == "hombre" &&
        child == "complementos y bano" ->
            R.drawable.subcat_hombre_complementos_bano

        parent == "hombre" &&
        (
            child == "jerseis y chaquetas" ||
            child == "jerseys y chaquetas"
        ) ->
            R.drawable.subcat_hombre_jerseis_chaquetas

        parent == "hombre" &&
        child == "pantalones y bermudas" ->
            R.drawable.subcat_hombre_pantalones_bermudas

        parent == "hombre" &&
        child == "sudaderas" ->
            R.drawable.subcat_hombre_sudaderas


        // MUJER
        parent == "mujer" &&
        child == "abrigos y cazadoras" ->
            R.drawable.subcat_mujer_abrigos_cazadoras

        parent == "mujer" &&
        child == "camisas y camisetas" ->
            R.drawable.subcat_mujer_camisas_camisetas

        parent == "mujer" &&
        child == "chaquetas y chalecos" ->
            R.drawable.subcat_mujer_chaquetas_chalecos

        parent == "mujer" &&
        child == "complementos" ->
            R.drawable.subcat_mujer_complementos

        parent == "mujer" &&
        (
            child == "jerseis" ||
            child == "jerseys"
        ) ->
            R.drawable.subcat_mujer_jerseis

        parent == "mujer" &&
        child == "pantalones y faldas" ->
            R.drawable.subcat_mujer_pantalones_faldas

        parent == "mujer" &&
        child == "ropa de fiesta" ->
            R.drawable.subcat_mujer_ropa_fiesta

        parent == "mujer" &&
        child.contains("vestidos") &&
        child.contains("conjuntos") ->
            R.drawable.subcat_mujer_vestidos_conjuntos_monos

        
        parent == "ropa de fiesta" &&
        child == "conjuntos y monos" ->
            R.drawable.subcat_mujer_fiesta_conjuntos_monos

        parent == "ropa de fiesta" &&
        child == "vestidos de fiesta" ->
            R.drawable.subcat_mujer_fiesta_vestidos

        // OUTLET
        parent == "outlet" &&
        child == "hombre invierno" ->
            R.drawable.outlet_hombre_invierno

        parent == "outlet" &&
        child == "hombre verano" ->
            R.drawable.outlet_hombre_verano

        parent == "outlet" &&
        child == "mujer invierno" ->
            R.drawable.outlet_mujer_invierno

        parent == "outlet" &&
        child == "mujer verano" ->
            R.drawable.outlet_mujer_verano

        parent == "outlet" &&
        child == "nina invierno" ->
            R.drawable.outlet_nina_invierno

        parent == "outlet" &&
        child == "nina verano" ->
            R.drawable.outlet_nina_verano

        parent == "outlet" &&
        child == "nino invierno" ->
            R.drawable.outlet_nino_invierno

        parent == "outlet" &&
        child == "nino verano" ->
            R.drawable.outlet_nino_verano

else -> null
    }
}




@Composable
internal fun CategoryCard(
    category: ProductCategory,
    allCategories: List<ProductCategory>,
    products: List<StoreProduct>,
    onOpen: (ProductCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val imageRes = subcategoryImageRes(category, allCategories)
    Box(modifier = modifier) {
        ApprovedCategoryCell(
            category = category,
            onClick = { onOpen(category) },
            imageResOverride = imageRes
        )
    }
}
internal fun StoreProduct.filterValues(attribute: String): Set<String> =
    attributes
        .filter { it.name.equals(attribute, true) }
        .flatMap { it.terms.map { term -> term.name } }
        .filter { it.isNotBlank() }
        .toSet()

internal fun catalogFilterKey(value: String): String = es.criosrango.shared.storeCatalogFilterKey(value)

internal fun StoreProduct.matchesCatalogFilters(
    sizes: Set<String>,
    colors: Set<String>,
    brands: Set<String>,
    filterKey: (String) -> String = ::catalogFilterKey
): Boolean {
    val productSizes =
        filterValues("Tallas")
            .map(filterKey)
            .toSet()

    val productColors =
        filterValues("Color")
            .map(filterKey)
            .toSet()

    val productBrands =
        tags.map { it.name }
            .filter { it.isNotBlank() }
            .map(filterKey)
            .toSet()

    val wantedSizes =
        sizes.map(filterKey).toSet()

    val wantedColors =
        colors.map(filterKey).toSet()

    val wantedBrands =
        brands.map(filterKey).toSet()

    return (
        (wantedSizes.isEmpty() ||
            productSizes.any { it in wantedSizes }) &&
        (wantedColors.isEmpty() ||
            productColors.any { it in wantedColors }) &&
        (wantedBrands.isEmpty() ||
            productBrands.any { it in wantedBrands })
    )
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CatalogFilteredProductGrid(
    products: List<StoreProduct>,
    modifier: Modifier = Modifier,
    onProduct: (StoreProduct) -> Unit,
    pagingState: es.criosrango.shared.CatalogPagingState<StoreProduct>? = null,
    onLoadNextPage: (() -> Unit)? = null,
    pagingKey: Any? = null,
    allowBrandFilter: Boolean = true,
    headerTitle: String? = null,
    headerOnBack: (() -> Unit)? = null,
    headerContent: (@Composable ColumnScope.() -> Unit)? = null,
    compactHeaderSpacing: Boolean = false,
    sharedFilterDialog: Boolean = true,
    onRetryInitial: (() -> Unit)? = null,
    useOutletFilterKey: Boolean = false,
    useSharedProductListHeaderContent: Boolean = false
) {
    var filtersOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var sortMode by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(ProductSortMode.RECENT)
    }
    var selectedSizes by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectedColors by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectedBrands by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(setOf<String>()) }

    val filterKey = if (useOutletFilterKey || !sharedFilterDialog) ::outletCatalogFilterKey else ::catalogFilterKey

    val sizes = products.flatMap { it.filterValues("Tallas") }
        .distinct().sorted()

    val colors = products.flatMap { it.filterValues("Color") }
        .distinct().sorted()

    val brands = products.flatMap { it.tags.map { tag -> tag.name } }
        .filter { it.isNotBlank() }
        .distinct().sorted()

    val possibleSizes = products
        .filter { it.matchesCatalogFilters(emptySet(), selectedColors, if (allowBrandFilter) selectedBrands else emptySet(), filterKey) }
        .flatMap { it.filterValues("Tallas") }
        .toSet()

    val possibleColors = products
        .filter { it.matchesCatalogFilters(selectedSizes, emptySet(), if (allowBrandFilter) selectedBrands else emptySet(), filterKey) }
        .flatMap { it.filterValues("Color") }
        .toSet()

    val possibleBrands = products
        .filter { it.matchesCatalogFilters(selectedSizes, selectedColors, emptySet(), filterKey) }
        .flatMap { it.tags.map { tag -> tag.name } }
        .toSet()


    val effectiveSelectedBrands =
        if (allowBrandFilter) selectedBrands else emptySet()

    val filteredBase = products.filter {
        it.matchesCatalogFilters(
            selectedSizes,
            selectedColors,
            effectiveSelectedBrands,
            filterKey
        )
    }

    val filtered =
        sortProducts(
            filteredBase,
            sortMode
        )

    val active = selectedSizes.size + selectedColors.size +
        if (allowBrandFilter) selectedBrands.size else 0

    val activeFilterChips = buildList {
        selectedSizes
            .sortedWith(compareBy({ productSizeSortKey(it) }, { it }))
            .forEach { value ->
                add(ActiveFilterChip("Talla $value") { selectedSizes = selectedSizes - value })
            }
        selectedColors.sorted().forEach { value ->
            add(ActiveFilterChip(value) { selectedColors = selectedColors - value })
        }
        if (allowBrandFilter) {
            selectedBrands.sorted().forEach { value ->
                add(ActiveFilterChip(value) { selectedBrands = selectedBrands - value })
            }
        }
    }

    val sharedProductList = headerTitle != null && headerOnBack != null && (headerContent == null || useSharedProductListHeaderContent)
    val categoryStatus by categoryCatalogLoadStatus.collectAsStateWithLifecycle()
    val sharedGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val sharedResetKey = if (pagingState != null) {
        listOf(pagingKey, sortMode, selectedSizes, selectedColors, selectedBrands)
    } else {
        null
    }
    var previousSharedResetKey by remember { mutableStateOf(sharedResetKey) }
    LaunchedEffect(sharedProductList, sharedResetKey) {
        if (!sharedProductList) return@LaunchedEffect
        if (previousSharedResetKey != sharedResetKey) {
            if (filtered.isNotEmpty()) sharedGridState.scrollToItem(0)
            previousSharedResetKey = sharedResetKey
        }
    }
    val latestSharedPagingState by rememberUpdatedState(pagingState)
    val latestSharedProductsSize by rememberUpdatedState(filtered.size)
    val latestSharedLoadNextPage by rememberUpdatedState(onLoadNextPage)
    val sharedPrefetchDistance = es.criosrango.shared.CatalogPaginator.PREFETCH_DISTANCE
    LaunchedEffect(sharedGridState, sharedResetKey, sharedProductList) {
        if (!sharedProductList || pagingState == null || onLoadNextPage == null) return@LaunchedEffect
        snapshotFlow {
            val currentPaging = latestSharedPagingState
            val itemCount = latestSharedProductsSize
            val lastVisibleIndex = sharedGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            itemCount > 0 &&
                currentPaging?.isInitialLoading != true &&
                currentPaging?.isAppending != true &&
                currentPaging?.appendError == null &&
                currentPaging?.hasMore == true &&
                lastVisibleIndex >= itemCount - 1 - sharedPrefetchDistance
        }.collect { shouldLoad ->
            if (shouldLoad) latestSharedLoadNextPage?.invoke()
        }
    }

    if (sharedProductList) {
        val sharedState = es.criosrango.shared.StoreProductListUiState(
            title = headerTitle.orEmpty(),
            items = filtered.map { it.toStoreProductItemUi() },
            selectedSort = sortMode.toStoreProductSortOption(),
            availableSortOptions = es.criosrango.shared.StoreProductSortOption.values().toList(),
            activeFilterCount = active,
            activeFilterChips = androidActiveFilterChips(
                selectedSizes,
                selectedColors,
                selectedBrands,
                allowBrandFilter
            ),
            isInitialLoading = filtered.isEmpty() &&
                (pagingState?.isInitialLoading == true || categoryStatus.state == CategoryLoadState.LOADING),
            isLoadingMore = filtered.isNotEmpty() && pagingState?.isAppending == true,
            isEmpty = filtered.isEmpty() &&
                !(pagingState?.isInitialLoading == true || categoryStatus.state == CategoryLoadState.LOADING),
            emptyMessage = "No encontramos productos",
            initialErrorMessage = if (onRetryInitial != null) {
                pagingState?.initialError?.let { error -> error.message?.takeIf { it.isNotBlank() } ?: "No se han podido cargar los productos." }
            } else null,
            appendErrorMessage = if (pagingState?.appendError != null) "" else null,
            appendRetryLabel = "Reintentar",
            canLoadMore = pagingState?.hasMore == true
        )
        es.criosrango.shared.StoreProductListScreen(
            state = sharedState,
            gridState = sharedGridState,
            modifier = modifier,
            onBack = { headerOnBack?.invoke() },
            onProductSelected = { id -> filtered.firstOrNull { it.id == id }?.let(onProduct) },
            onSortSelected = { sortMode = it.toAndroidProductSortMode() },
            onFilterClick = { filtersOpen = true },
            onRemoveFilter = { id ->
                when {
                    id.startsWith("size:") -> selectedSizes = selectedSizes - id.removePrefix("size:")
                    id.startsWith("color:") -> selectedColors = selectedColors - id.removePrefix("color:")
                    id.startsWith("brand:") -> selectedBrands = selectedBrands - id.removePrefix("brand:")
                }
            },
            onClearFilters = {
                selectedSizes = emptySet()
                selectedColors = emptySet()
                if (allowBrandFilter) selectedBrands = emptySet()
            },
            onRetryInitial = { onRetryInitial?.invoke() },
            onRetryAppend = { onLoadNextPage?.invoke() },
            headerContent = headerContent ?: {},
            outletPresentation = useOutletFilterKey,
            imageContent = { item, imageModifier ->
                val product = filtered.firstOrNull { it.id == item.id }
                val image = product?.images?.firstOrNull()
                val targetWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                    maxWidth.roundToPx()
                }
                val outletImageModifier = if (useOutletFilterKey) {
                    imageModifier.padding(1.dp).graphicsLayer { scaleX = 1.16f; scaleY = 1.16f }
                } else imageModifier
                CatalogImage(
                    product?.let {
                        selectResponsiveImageUrl(
                            src = image?.src.orEmpty(),
                            thumbnail = image?.thumbnail.orEmpty(),
                            srcSet = image?.srcSet.orEmpty(),
                            targetWidthPx = targetWidthPx
                        )
                    },
                    item.nameText,
                    outletImageModifier,
                    if (useOutletFilterKey) androidx.compose.ui.layout.ContentScale.Fit else androidx.compose.ui.layout.ContentScale.Crop,
                    onSuccess = {
                        categoryStatus.categoryId?.let { categoryId ->
                            CategoryLoadTelemetry.firstImage(categoryId, item.id)
                        }
                    }
                )
            }
        )
    } else {
        Column(modifier) {
            if (headerTitle != null && headerOnBack != null) {
                CatalogProductListHeader(title = headerTitle, onBack = headerOnBack, activeFilters = active, onOpenFilters = { filtersOpen = true }, bottomPadding = if (compactHeaderSpacing) 0.dp else CatalogHeaderGeometry.bottomPadding)
            }
            headerContent?.invoke(this)

            if (activeFilterChips.isNotEmpty()) {
                ActiveFilterChips(
                    filters = activeFilterChips,
                    onClearAll = {
                        selectedSizes = emptySet()
                        selectedColors = emptySet()
                        if (allowBrandFilter) selectedBrands = emptySet()
                    }
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CatalogHeaderGeometry.horizontalPadding, vertical = if (compactHeaderSpacing) 0.dp else CatalogHeaderGeometry.controlsVerticalPadding),
                horizontalArrangement = Arrangement.Start
            ) {
                ProductSortControl(mode = sortMode, onMode = { sortMode = it })
                if (headerTitle == null) {
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { filtersOpen = true }) {
                        Text(if (active == 0) "Filtros" else "Filtros ($active)")
                    }
                }
            }
            ProductGrid(
                products = filtered,
                modifier = Modifier.weight(1f),
                onProduct = onProduct,
                pagingState = pagingState,
                onLoadNextPage = onLoadNextPage,
                resetKey = if (pagingState != null) {
                    listOf(pagingKey, sortMode, selectedSizes, selectedColors, selectedBrands)
                } else {
                    null
                }
            )
        }
    }

    if (filtersOpen) {
        if (sharedProductList && sharedFilterDialog) {
            es.criosrango.shared.StoreCatalogFilterDialog(
                sizes = sizes, colors = colors, brands = brands,
                selectedSizes = selectedSizes, selectedColors = selectedColors, selectedBrands = selectedBrands,
                enabledSizes = possibleSizes, enabledColors = possibleColors, enabledBrands = possibleBrands,
                showBrands = allowBrandFilter,
                onSizesChanged = { selectedSizes = it }, onColorsChanged = { selectedColors = it }, onBrandsChanged = { selectedBrands = it },
                onApply = { filtersOpen = false },
                onClear = { selectedSizes = emptySet(); selectedColors = emptySet(); if (allowBrandFilter) selectedBrands = emptySet() },
                onDismiss = { filtersOpen = false }
            )
        } else {
            AlertDialog(
                onDismissRequest = { filtersOpen = false },
                title = { Text("Filtros") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        CatalogFilterChoices("Talla", sizes, selectedSizes, possibleSizes) { selectedSizes = it }
                        Spacer(Modifier.height(20.dp))
                        CatalogFilterChoices("Color", colors, selectedColors, possibleColors) { selectedColors = it }
                        if (allowBrandFilter && brands.isNotEmpty()) {
                            Spacer(Modifier.height(20.dp))
                            CatalogFilterChoices("Marca", brands, selectedBrands, possibleBrands) { selectedBrands = it }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { filtersOpen = false }) { Text("Aplicar") } },
                dismissButton = {
                    TextButton(onClick = {
                        selectedSizes = emptySet(); selectedColors = emptySet()
                        if (allowBrandFilter) selectedBrands = emptySet()
                    }) { Text("Limpiar") }
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CatalogFilterChoices(
    title: String,
    values: List<String>,
    selected: Set<String>,
    enabledValues: Set<String>,
    onChange: (Set<String>) -> Unit
) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val orderedValues = if (title.equals("Talla", true) || title.equals("Tallas", true)) {
            values.sortedWith(compareBy({ productSizeSortKey(it) }, { it }))
        } else values
        orderedValues.forEach { value ->
            FilterChip(
                selected = value in selected,
                enabled = value in selected || value in enabledValues,
                onClick = { onChange(if (value in selected) selected - value else selected + value) },
                leadingIcon = {
                    if (title.equals("Color", true)) {
                        productColorSwatch(value)?.let { swatch ->
                            Box(Modifier.size(18.dp).background(swatch, RoundedCornerShape(50)))
                        }
                    }
                },
                label = { Text(value) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CategoryParentWithFilters(
    title: String,
    children: List<ProductCategory>,
    allCategories: List<ProductCategory>,
    products: List<StoreProduct>,
    padding: PaddingValues,
    loading: Boolean,
    onBack: () -> Unit,
    onOpen: (ProductCategory) -> Unit,
    onProduct: (StoreProduct) -> Unit,
    pagingState: es.criosrango.shared.CatalogPagingState<StoreProduct>,
    onLoadNextPage: () -> Unit,
    onRetryInitial: () -> Unit
) {
    var open by remember { mutableStateOf(false) }
    var sizes by remember { mutableStateOf(setOf<String>()) }
    var colors by remember { mutableStateOf(setOf<String>()) }
    var brands by remember { mutableStateOf(setOf<String>()) }
    var sortMode by remember { mutableStateOf(ProductSortMode.RECENT) }

    val parentCategory = allCategories.firstOrNull { it.id == children.firstOrNull()?.parent }
    val isOutletParent = parentCategory?.slug.equals("outlet", ignoreCase = true) ||
        parentCategory?.name.equals("Outlet", ignoreCase = true)
    val filterKey = if (isOutletParent) ::outletCatalogFilterKey else ::catalogFilterKey

    val allSizes = products.flatMap { it.filterValues("Tallas") }.distinct().sorted()
    val allColors = products.flatMap { it.filterValues("Color") }.distinct().sorted()
    val allBrands = products.flatMap { it.tags.map { t -> t.name } }
        .filter { it.isNotBlank() }.distinct().sorted()

    val possibleParentSizes = products
        .filter { it.matchesCatalogFilters(emptySet(), colors, brands, filterKey) }
        .flatMap { it.filterValues("Tallas") }
        .toSet()

    val possibleParentColors = products
        .filter { it.matchesCatalogFilters(sizes, emptySet(), brands, filterKey) }
        .flatMap { it.filterValues("Color") }
        .toSet()

    val possibleParentBrands = products
        .filter { it.matchesCatalogFilters(sizes, colors, emptySet(), filterKey) }
        .flatMap { it.tags.map { tag -> tag.name } }
        .toSet()

    val filtered = products.filter {
        it.matchesCatalogFilters(sizes, colors, brands, filterKey)
    }

    val active = sizes.size + colors.size + brands.size

    val activeFilterChips = buildList {
        sizes
            .sortedWith(compareBy({ productSizeSortKey(it) }, { it }))
            .forEach { value ->
                add(ActiveFilterChip("Talla $value") { sizes = sizes - value })
            }
        colors.sorted().forEach { value ->
            add(ActiveFilterChip(value) { colors = colors - value })
        }
        brands.sorted().forEach { value ->
            add(ActiveFilterChip(value) { brands = brands - value })
        }
    }

    if (parentCategory != null) {
        if (active == 0) {
            es.criosrango.shared.StoreSubcategoriesScreen(
                parent = es.criosrango.shared.model.StoreCategory(
                    id = parentCategory.id, parent = parentCategory.parent, name = parentCategory.name,
                    slug = parentCategory.slug, count = parentCategory.count
                ),
                title = title,
                categories = children.map { category ->
                    es.criosrango.shared.model.StoreCategory(
                        id = category.id, parent = category.parent, name = category.name,
                        slug = category.slug, count = category.count
                    )
                },
                // These children come from the loaded category tree; this loading flag belongs to parent products.
                loading = false,
                onBack = onBack,
                onCategory = { selected -> children.firstOrNull { it.id == selected.id }?.let(onOpen) },
                onFilterClick = { open = true },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        } else {
            val sortedProducts = sortProducts(filtered, sortMode)
            val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
            val latestCategoryPagingState by rememberUpdatedState(pagingState)
            val latestFilteredProductsSize by rememberUpdatedState(sortedProducts.size)
            val latestCategoryLoadNextPage by rememberUpdatedState(onLoadNextPage)
            val categoryPrefetchDistance = es.criosrango.shared.CatalogPaginator.PREFETCH_DISTANCE
            LaunchedEffect(gridState, parentCategory.id, sortMode, sizes, colors, brands) {
                snapshotFlow {
                    val currentPaging = latestCategoryPagingState
                    val itemCount = latestFilteredProductsSize
                    val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                    itemCount > 0 &&
                        !currentPaging.isInitialLoading &&
                        !currentPaging.isAppending &&
                        currentPaging.appendError == null &&
                        currentPaging.hasMore &&
                        lastVisibleIndex >= itemCount - 1 - categoryPrefetchDistance
                }.collect { shouldLoad ->
                    if (shouldLoad) latestCategoryLoadNextPage()
                }
            }

            val listState = es.criosrango.shared.StoreProductListUiState(
                title = title,
                items = sortedProducts.map { it.toStoreProductItemUi() },
                selectedSort = sortMode.toStoreProductSortOption(),
                availableSortOptions = es.criosrango.shared.StoreProductSortOption.values().toList(),
                activeFilterCount = active,
                activeFilterChips = androidActiveFilterChips(sizes, colors, brands, true),
                isInitialLoading = sortedProducts.isEmpty() && pagingState.isInitialLoading,
                isLoadingMore = sortedProducts.isNotEmpty() && pagingState.isAppending,
                isEmpty = sortedProducts.isEmpty() && !pagingState.isInitialLoading,
                emptyMessage = "No hay productos con estos filtros",
                initialErrorMessage = pagingState.initialError?.let { error ->
                    error.message?.takeIf { it.isNotBlank() } ?: "No se han podido cargar los productos."
                },
                appendErrorMessage = if (pagingState.appendError != null) "" else null,
                appendRetryLabel = "Reintentar",
                canLoadMore = pagingState.hasMore
            )
            es.criosrango.shared.StoreProductListScreen(
                state = listState,
                gridState = gridState,
                modifier = Modifier.fillMaxSize().padding(padding),
                onBack = onBack,
                onProductSelected = { id -> sortedProducts.firstOrNull { it.id == id }?.let(onProduct) },
                onSortSelected = { sortMode = it.toAndroidProductSortMode() },
                onFilterClick = { open = true },
                onRemoveFilter = { id ->
                    when {
                        id.startsWith("size:") -> sizes = sizes - id.removePrefix("size:")
                        id.startsWith("color:") -> colors = colors - id.removePrefix("color:")
                        id.startsWith("brand:") -> brands = brands - id.removePrefix("brand:")
                    }
                },
                onClearFilters = { sizes = emptySet(); colors = emptySet(); brands = emptySet() },
                onRetryInitial = onRetryInitial,
                onRetryAppend = onLoadNextPage,
                imageContent = { item, imageModifier ->
                    val product = sortedProducts.firstOrNull { it.id == item.id }
                    val image = product?.images?.firstOrNull()
                    val targetWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxWidth.roundToPx() }
                    CatalogImage(
                        product?.let {
                            selectResponsiveImageUrl(
                                src = image?.src.orEmpty(),
                                thumbnail = image?.thumbnail.orEmpty(),
                                srcSet = image?.srcSet.orEmpty(),
                                targetWidthPx = targetWidthPx
                            )
                        },
                        item.nameText,
                        imageModifier,
                        androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
            )
        }
    } else {
        Column(Modifier.fillMaxSize().padding(padding)) {
            es.criosrango.shared.StoreCatalogHeader(
                title = title,
                showBack = true,
                onBack = onBack,
                trailing = {
                    OutlinedButton(onClick = { open = true }) {
                        Text(if (active == 0) "Filtros" else "Filtros ($active)")
                    }
                }
            )
    
            ActiveFilterChips(
                filters = activeFilterChips,
                onClearAll = {
                    sizes = emptySet()
                    colors = emptySet()
                    brands = emptySet()
                }
            )
    
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    
            if (active == 0) {
                if (parentCategory == null || isOutletParent) {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            start = CatalogCategoryVisualTokens.horizontalPadding,
                            top = CatalogCategoryVisualTokens.rowSpacing,
                            end = CatalogCategoryVisualTokens.horizontalPadding,
                            bottom = 28.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(CatalogCategoryVisualTokens.rowSpacing)
                    ) {
                        items(children.chunked(3)) { row ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                row.forEach { category ->
                                    CategoryCard(
                                        category = category,
                                        allCategories = allCategories,
                                        products = products,
                                        onOpen = onOpen,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            } else if (!loading && filtered.isEmpty()) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No hay productos con estos filtros")
                    TextButton(onClick = {
                        sizes = emptySet()
                        colors = emptySet()
                        brands = emptySet()
                    }) {
                        Text("Limpiar filtros")
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        ProductSortControl(
                            mode = sortMode,
                            onMode = { sortMode = it }
                        )
                    }
                    ProductGrid(
                        sortProducts(filtered, sortMode),
                        Modifier.weight(1f),
                        onProduct
                    )
                }
            }
        }
    }

    if (open) {
        if (parentCategory != null) {
            es.criosrango.shared.StoreCatalogFilterDialog(
                sizes = allSizes.sortedWith(compareBy({ productSizeSortKey(it) }, { it })),
                colors = allColors, brands = allBrands,
                selectedSizes = sizes, selectedColors = colors, selectedBrands = brands,
                enabledSizes = possibleParentSizes, enabledColors = possibleParentColors, enabledBrands = possibleParentBrands,
                onSizesChanged = { sizes = it }, onColorsChanged = { colors = it }, onBrandsChanged = { brands = it },
                onApply = { open = false },
                onClear = { sizes = emptySet(); colors = emptySet(); brands = emptySet() },
                onDismiss = { open = false }
            )
        } else {
            AlertDialog(
                onDismissRequest = { open = false },
                title = { Text("Filtros") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        CatalogFilterChoices("Talla", allSizes, sizes, possibleParentSizes) { sizes = it }
                        Spacer(Modifier.height(20.dp))
                        CatalogFilterChoices("Color", allColors, colors, possibleParentColors) { colors = it }
                        if (allBrands.isNotEmpty()) {
                            Spacer(Modifier.height(20.dp))
                            CatalogFilterChoices("Marca", allBrands, brands, possibleParentBrands) { brands = it }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { open = false }) { Text("Aplicar") } },
                dismissButton = { TextButton(onClick = { sizes = emptySet(); colors = emptySet(); brands = emptySet() }) { Text("Limpiar") } }
            )
        }
    }
}
