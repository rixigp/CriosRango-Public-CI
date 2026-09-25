package es.criosrango.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
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

// OUTLET_CATEGORY_BUBBLES_START

internal fun outletBubbleKey(value: String): String =
    java.text.Normalizer
        .normalize(
            value.lowercase().trim(),
            java.text.Normalizer.Form.NFD
        )
        .replace("\\p{Mn}+".toRegex(), "")

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

@Composable
internal fun OutletAwareCatalogGrid(
    current: ProductCategory?,
    products: List<StoreProduct>,
    allCategories: List<ProductCategory>,
    modifier: Modifier = Modifier,
    onProduct: (StoreProduct) -> Unit
) {

    // Solo las categorías finales dentro de Outlet.
    if (current?.parent != 445) {
        CatalogFilteredProductGrid(
            products,
            modifier,
            onProduct
        )
        return
    }

    val categoriesById =
        remember(allCategories) {
            allCategories.associateBy { it.id }
        }

    val audience =
        remember(current.id) {
            outletBubbleKey(current.name)
                .substringBefore(" ")
        }

    val originalRoot =
        remember(allCategories, audience) {
            allCategories.firstOrNull {
                it.parent == 0 &&
                outletBubbleKey(it.name) == audience
            }
        }

    val bubbles =
        remember(
            products,
            allCategories,
            originalRoot?.id
        ) {
            val rootId = originalRoot?.id

            if (rootId == null) {
                emptyList()
            } else {
                allCategories
                    .filter { category ->
                        rootId != null && category.id != rootId && isCategoryDescendantOf(category.id, rootId, categoriesById)
                    }
                    .filter { category ->
                        products.any { product ->
                            productBelongsToOutletOriginCategory(
                                product,
                                category.id,
                                categoriesById
                            )
                        }
                    }
                    .distinctBy { it.id }
            }
        }

    var selectedCategoryId by remember(current.id) {
        mutableStateOf<Int?>(null)
    }

    val visibleProducts =
        remember(
            products,
            selectedCategoryId,
            categoriesById
        ) {
            val selected =
                selectedCategoryId

            if (selected == null) {
                products
            } else {
                products.filter { product ->
                    productBelongsToOutletOriginCategory(
                        product,
                        selected,
                        categoriesById
                    )
                }
            }
        }

    Column(modifier) {

        if (bubbles.isNotEmpty()) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(
                        rememberScrollState()
                    )
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = 6.dp,
                        bottom = 10.dp
                    ),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                val allSelected =
                    selectedCategoryId == null

                OutlinedButton(
                    onClick = {
                        selectedCategoryId = null
                    },
                    shape =
                        RoundedCornerShape(50.dp),
                    colors =
                        androidx.compose.material3
                            .ButtonDefaults
                            .outlinedButtonColors(
                                containerColor =
                                    if (allSelected)
                                        Color(0xFF163B35)
                                    else
                                        Color.Transparent,
                                contentColor =
                                    if (allSelected)
                                        Color.White
                                    else
                                        MaterialTheme
                                            .colorScheme
                                            .onBackground
                            )
                ) {
                    Text("Todas")
                }

                bubbles.forEach { category ->

                    val selected =
                        selectedCategoryId ==
                            category.id

                    OutlinedButton(
                        onClick = {
                            selectedCategoryId =
                                category.id
                        },
                        shape =
                            RoundedCornerShape(50.dp),
                        colors =
                            androidx.compose.material3
                                .ButtonDefaults
                                .outlinedButtonColors(
                                    containerColor =
                                        if (selected)
                                            Color(0xFF163B35)
                                        else
                                            Color.Transparent,
                                    contentColor =
                                        if (selected)
                                            Color.White
                                        else
                                            MaterialTheme
                                                .colorScheme
                                                .onBackground
                                )
                    ) {
                        Text(category.name)
                    }
                }
            }
        }

        CatalogFilteredProductGrid(
            products = visibleProducts,
            modifier = Modifier.weight(1f),
            onProduct = onProduct
        )
    }
}

// OUTLET_CATEGORY_BUBBLES_END

@Composable
private fun BackendDiagnosticResultDialog(
    result: String,
    running: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = if (running) ({}) else onDismiss,
        title = { Text("Diagnóstico backend") },
        text = {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(if (running) "Ejecutando prueba backend..." else result)
            }
        },
        confirmButton = {
            if (!running) {
                TextButton(onClick = {
                    val clipboard =
                        context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                    clipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText(
                            "Diagnóstico backend",
                            result
                        )
                    )
                }) {
                    Text("COPIAR RESULTADO")
                }
            }
        },
        dismissButton = {
            if (!running) {
                TextButton(onClick = onDismiss) {
                    Text("Cerrar")
                }
            }
        }
    )
}

@Composable
private fun CategoryTelemetryDialog(
    categoryId: Int,
    categoryName: String,
    onDismiss: () -> Unit
) {
    val snapshot = CategoryLoadTelemetry.snapshot(categoryId)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var backendDialogOpen by remember { mutableStateOf(false) }
    var backendRunning by remember { mutableStateOf(false) }
    var backendResult by remember { mutableStateOf<String?>(null) }
    var catalogCountDialogOpen by remember { mutableStateOf(false) }
    var catalogCountRunning by remember { mutableStateOf(false) }
    var catalogCountResult by remember { mutableStateOf<String?>(null) }

    fun value(v: Long?) = v?.let { it.toString() + " ms" } ?: "N/A"
    fun delta(from: Long?, to: Long?) =
        if (from != null && to != null) (to - from).toString() + " ms" else "N/A"

    val text = buildString {
        appendLine("DIAGNÓSTICO CATEGORÍA")
        appendLine("categoryId: " + categoryId)
        appendLine("categoryName: " + categoryName)
        appendLine()
        appendLine("SOURCE = " + (snapshot?.source?.uppercase() ?: "N/A"))
        appendLine("CATEGORY_TAP: " + value(snapshot?.tapMs))
        appendLine("CATEGORY_CACHE_START: " + value(snapshot?.cacheStartMs))
        appendLine("CATEGORY_CACHE_END: " + value(snapshot?.cacheEndMs))
        appendLine("CACHE_HIT / CACHE_MISS: " + (snapshot?.cacheHit?.uppercase() ?: "N/A"))
        appendLine("CATEGORY_NETWORK_START: " + value(snapshot?.networkStartMs))
        appendLine("CATEGORY_NETWORK_END: " + value(snapshot?.networkEndMs))
        appendLine("CATEGORY_PARSE_END: " + value(snapshot?.parseEndMs))
        appendLine("CATEGORY_ROOM_WRITE_END: " + value(snapshot?.roomWriteEndMs))
        appendLine("CATEGORY_UI_PRODUCTS: " + value(snapshot?.uiProductsMs))
        appendLine(
            "CATEGORY_FIRST_IMAGE: " +
                (snapshot?.firstImageMs?.let {
                    it.toString() + " ms (productId=" + snapshot.firstImageProductId + ")"
                } ?: "N/A")
        )
        appendLine()
        appendLine("TAP -> NETWORK_START: " + delta(snapshot?.tapMs, snapshot?.networkStartMs))
        appendLine("NETWORK_START -> NETWORK_END: " + delta(snapshot?.networkStartMs, snapshot?.networkEndMs))
        appendLine("NETWORK_END -> UI_PRODUCTS: " + delta(snapshot?.networkEndMs, snapshot?.uiProductsMs))
        appendLine("UI_PRODUCTS -> FIRST_IMAGE: " + delta(snapshot?.uiProductsMs, snapshot?.firstImageMs))
        appendLine("TAP -> UI_PRODUCTS: " + delta(snapshot?.tapMs, snapshot?.uiProductsMs))
        appendLine()
        appendLine("endpoint: GET products")
        appendLine("per_page: 24")
        appendLine("productos recibidos: " + (snapshot?.products ?: "N/A"))
        appendLine("páginas solicitadas antes de pintar: 1")
        appendLine("globalSync wait: NO")
        appendLine("networkGate wait: N/A")
        appendLine("priority mutex wait: N/A")
        appendLine(
            "UI_PRODUCTS antes de ROOM_WRITE_END: " +
                if (snapshot?.uiProductsMs != null && snapshot.roomWriteEndMs != null)
                    snapshot.uiProductsMs < snapshot.roomWriteEndMs
                else "N/A"
        )
        appendLine("DNS: " + (snapshot?.network?.dnsMs?.let { it.toString() + " ms" } ?: "N/A"))
        appendLine("CONNECT: " + (snapshot?.network?.connectMs?.let { it.toString() + " ms" } ?: "N/A"))
        appendLine("TLS: " + (snapshot?.network?.tlsMs?.let { it.toString() + " ms" } ?: "N/A"))
        appendLine(
            "REQUEST HEADERS: " +
                (snapshot?.network?.requestHeadersMs?.let { it.toString() + " ms" } ?: "N/A")
        )
        appendLine(
            "REQUEST BODY: " +
                (snapshot?.network?.requestBodyMs?.let { it.toString() + " ms" } ?: "N/A")
        )
        appendLine(
            "REQUEST BODY BYTES: " +
                (snapshot?.network?.requestBodyBytes?.toString() ?: "N/A")
        )
        appendLine(
            "TTFB / SERVER WAIT: " +
                (snapshot?.network?.ttfbMs?.let { it.toString() + " ms" } ?: "N/A")
        )
        appendLine(
            "RESPONSE HEADERS: " +
                (snapshot?.network?.responseHeadersMs?.let { it.toString() + " ms" } ?: "N/A")
        )
        appendLine(
            "RESPONSE BODY / DOWNLOAD: " +
                (snapshot?.network?.bodyDownloadMs?.let { it.toString() + " ms" } ?: "N/A")
        )
        appendLine(
            "RESPONSE BODY BYTES: " +
                (snapshot?.network?.responseBodyBytes?.toString() ?: "N/A")
        )
        appendLine(
            "TOTAL NETWORK (OkHttp): " +
                (snapshot?.network?.totalNetworkMs?.let { it.toString() + " ms" } ?: "N/A")
        )
        appendLine(
            "CONEXIÓN REUTILIZADA: " +
                (snapshot?.network?.connectionReused?.let { if (it) "SI" else "NO" } ?: "N/A")
        )
        appendLine("HTTP: " + (snapshot?.network?.protocol ?: "N/A"))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Diagnóstico categoría") },
        text = {
            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(text)
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(
                    enabled = !backendRunning,
                    onClick = {
                        if (!backendRunning) {
                            backendRunning = true
                        backendResult = null
                        backendDialogOpen = true
                        scope.launch {
                            backendResult = BackendDiagnosticRunner(StoreSession(context.getSharedPreferences("criosrango", android.content.Context.MODE_PRIVATE))).run(categoryId)
                            backendRunning = false
                        }
                        }
                    }
                ) {
                    Text("PRUEBA BACKEND")
                }

                TextButton(
                    enabled = !catalogCountRunning,
                    onClick = {
                        catalogCountRunning = true
                        catalogCountResult = null
                        catalogCountDialogOpen = true
                        scope.launch {
                            catalogCountResult = CatalogCountDiagnosticRunner(
                                StoreSession(context.getSharedPreferences("criosrango", android.content.Context.MODE_PRIVATE))
                            ).run()
                            catalogCountRunning = false
                        }
                    }
                ) {
                    Text("CONTEO CATEGORÍAS")
                }

                TextButton(onClick = {
                    val clipboard =
                        context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                            as android.content.ClipboardManager
                    clipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText(
                            "Diagnóstico categoría",
                            text
                        )
                    )
                }) {
                    Text("COPIAR DIAGNÓSTICO")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    )

    if (backendDialogOpen) {
        BackendDiagnosticResultDialog(
            result = backendResult ?: "",
            running = backendRunning,
            onDismiss = { backendDialogOpen = false }
        )
    }

    if (catalogCountDialogOpen) {
        BackendDiagnosticResultDialog(
            result = catalogCountResult ?: "",
            running = catalogCountRunning,
            onDismiss = { catalogCountDialogOpen = false }
        )
    }
}

@Composable
internal fun CategoriesScreen(categories: List<ProductCategory>, products: List<StoreProduct>, path: MutableList<Int>, padding: PaddingValues, loading: Boolean, loadCategory: (Int) -> Unit, loadCategoryTree: (Int) -> Unit, onProduct: (StoreProduct) -> Unit, onRootBack: (() -> Unit)? = null, onOpen: (ProductCategory) -> Unit,
    outletSeasonFilter: HomeOutletSeason? = null, onSingleLevelBack: (() -> Unit)? = null) {
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
    val onCategoriesBack: () -> Unit = {
        if (path.size == 1 && onSingleLevelBack != null) {
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
            onBack = onCategoriesBack
        )
    } else if (children.isNotEmpty()) {
        CategoryParentWithFilters(
            title = current?.let { approvedCategoryName(it) } ?: "Categorías",
            children = children,
            allCategories = categories,
            products = products,
            padding = padding,
            loading = loading,
            onBack = onCategoriesBack,
            onOpen = onOpen,
            onProduct = onProduct
        )
    } else {
        Column(Modifier.fillMaxSize().padding(padding)) {
            var telemetryDialogOpen by remember(currentId) { mutableStateOf(false) }
            CatalogScreenHeader(
                title = current?.name ?: "Productos",
                onBack = onCategoriesBack,
                onTitleLongPress = if (currentId != null) {
                    { telemetryDialogOpen = true }
                } else {
                    null
                }
            )
            if (telemetryDialogOpen && currentId != null) {
                CategoryTelemetryDialog(
                    categoryId = currentId,
                    categoryName = current?.name ?: "Productos",
                    onDismiss = { telemetryDialogOpen = false }
                )
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            OutletAwareCatalogGrid(
                current = current,
                products = products,
                allCategories = categories,
                modifier = Modifier.fillMaxSize(),
                onProduct = onProduct
            )
        }
    }
}

@Composable
internal fun CategoryList(
    categories: List<ProductCategory>,
    allCategories: List<ProductCategory>,
    products: List<StoreProduct>,
    padding: PaddingValues,
    loading: Boolean,
    onOpen: (ProductCategory) -> Unit,
    onBack: () -> Unit
) {
    val visibleCategories = categories
        .filter {
            it.parent == 0 &&
            !it.name.equals("Outlet", ignoreCase = true) &&
            !it.slug.equals("outlet", ignoreCase = true)
        }
        .distinctBy { it.id }

    val rows = visibleCategories.chunked(3)

    if (visibleCategories.isEmpty() && loading) {
        CategorySkeletonList(padding)
        return
    }

    LazyColumn(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 0.dp,
            bottom = 28.dp
        ),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            CatalogScreenHeader(
                title = "Categorías",
                onBack = onBack
            )
        }

        items(rows.size) { rowIndex ->
            val row = rows[rowIndex]

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(3) { index ->
                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        if (index < row.size) {
                            val category = row[index]

                            ApprovedCategoryCell(
                                category = category,
                                onClick = {
                                    onOpen(category)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}





@Composable
private fun CategorySkeletonList(padding: PaddingValues) {
    LazyColumn(
        modifier = Modifier
            .padding(padding)
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Box(
                Modifier
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .fillMaxWidth(.42f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFE8E5DF))
            )
        }
        items(4) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(3) {
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFE8E5DF))
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            Modifier
                                .fillMaxWidth(.78f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFE8E5DF))
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier
                                .fillMaxWidth(.58f)
                                .height(14.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFE8E5DF))
                        )
                    }
                }
            }
        }
    }
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
    val imageRes =
        subcategoryImageRes(
            category,
            allCategories
        )

    Column(
        modifier = Modifier
            .width(104.dp)
            .height(170.dp)
            .clickable {
                onOpen(category)
            },
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp),
            contentAlignment =
                Alignment.Center
        ) {

            if (imageRes != null) {
                Image(
                    painter =
                        painterResource(imageRes),
                    contentDescription =
                        category.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(5.dp),
                    contentScale =
                        ContentScale.Fit
                )
            } else {
                CategoryVisual(
                    category,
                    Modifier
                        .fillMaxSize()
                        .padding(10.dp)
                )
            }
        }

        Spacer(
            Modifier.height(6.dp)
        )

        Text(
            text = category.name,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            style =
                MaterialTheme.typography.bodyMedium,
            color =
                MaterialTheme.colorScheme.onBackground,
            textAlign =
                TextAlign.Center,
            maxLines = 3,
            overflow =
                TextOverflow.Ellipsis
        )
    }
}


internal fun StoreProduct.filterValues(attribute: String): Set<String> =
    attributes
        .filter { it.name.equals(attribute, true) }
        .flatMap { it.terms.map { term -> term.name } }
        .filter { it.isNotBlank() }
        .toSet()

internal fun catalogFilterKey(value: String): String =
    java.text.Normalizer
        .normalize(
            value.lowercase().trim(),
            java.text.Normalizer.Form.NFD
        )
        .replace("\\p{Mn}+".toRegex(), "")
        .replace("\\s+".toRegex(), " ")

internal fun StoreProduct.matchesCatalogFilters(
    sizes: Set<String>,
    colors: Set<String>,
    brands: Set<String>
): Boolean {
    val productSizes =
        filterValues("Tallas")
            .map(::catalogFilterKey)
            .toSet()

    val productColors =
        filterValues("Color")
            .map(::catalogFilterKey)
            .toSet()

    val productBrands =
        tags.map { it.name }
            .filter { it.isNotBlank() }
            .map(::catalogFilterKey)
            .toSet()

    val wantedSizes =
        sizes.map(::catalogFilterKey).toSet()

    val wantedColors =
        colors.map(::catalogFilterKey).toSet()

    val wantedBrands =
        brands.map(::catalogFilterKey).toSet()

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
    onProduct: (StoreProduct) -> Unit
) {
    var filtersOpen by remember { mutableStateOf(false) }
    var sortMode by remember {
        mutableStateOf(ProductSortMode.RECENT)
    }
    var selectedSizes by remember { mutableStateOf(setOf<String>()) }
    var selectedColors by remember { mutableStateOf(setOf<String>()) }
    var selectedBrands by remember { mutableStateOf(setOf<String>()) }

    val sizes = products.flatMap { it.filterValues("Tallas") }
        .distinct().sorted()

    val colors = products.flatMap { it.filterValues("Color") }
        .distinct().sorted()

    val brands = products.flatMap { it.tags.map { tag -> tag.name } }
        .filter { it.isNotBlank() }
        .distinct().sorted()

    val possibleSizes = products
        .filter { it.matchesCatalogFilters(emptySet(), selectedColors, selectedBrands) }
        .flatMap { it.filterValues("Tallas") }
        .toSet()

    val possibleColors = products
        .filter { it.matchesCatalogFilters(selectedSizes, emptySet(), selectedBrands) }
        .flatMap { it.filterValues("Color") }
        .toSet()

    val possibleBrands = products
        .filter { it.matchesCatalogFilters(selectedSizes, selectedColors, emptySet()) }
        .flatMap { it.tags.map { tag -> tag.name } }
        .toSet()


    val filteredBase = products.filter {
        it.matchesCatalogFilters(
            selectedSizes,
            selectedColors,
            selectedBrands
        )
    }

    val filtered =
        sortProducts(
            filteredBase,
            sortMode
        )

    val active = selectedSizes.size + selectedColors.size + selectedBrands.size

    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ProductSortControl(
                mode = sortMode,
                onMode = {
                    sortMode = it
                }
            )

            OutlinedButton(onClick = { filtersOpen = true }) {
                Text(if (active == 0) "Filtros" else "Filtros ($active)")
            }
        }

        ProductGrid(
            filtered,
            Modifier.weight(1f),
            onProduct
        )
    }

    if (filtersOpen) {
        AlertDialog(
            onDismissRequest = { filtersOpen = false },
            title = { Text("Filtros") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    CatalogFilterChoices("Talla", sizes, selectedSizes, possibleSizes) { selectedSizes = it }

                    Spacer(Modifier.height(20.dp))

                    CatalogFilterChoices("Color", colors, selectedColors, possibleColors) { selectedColors = it }

                    if (brands.isNotEmpty()) {
                        Spacer(Modifier.height(20.dp))

                        CatalogFilterChoices("Marca", brands, selectedBrands, possibleBrands) { selectedBrands = it }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { filtersOpen = false }) {
                    Text("Aplicar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        selectedSizes = emptySet()
                        selectedColors = emptySet()
                        selectedBrands = emptySet()
                    }
                ) {
                    Text("Limpiar")
                }
            }
        )
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

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val orderedValues =
            if (
                title.equals("Talla", true) ||
                title.equals("Tallas", true)
            ) {
                values.sortedWith(
                    compareBy(
                        { productSizeSortKey(it) },
                        { it }
                    )
                )
            } else {
                values
            }

        orderedValues.forEach { value ->
            FilterChip(
                selected = value in selected,
                enabled = value in selected || value in enabledValues,
                onClick = {
                    onChange(
                        if (value in selected)
                            selected - value
                        else
                            selected + value
                    )
                },
                leadingIcon = {
                    if (title.equals("Color", true)) {
                        productColorSwatch(value)?.let { swatch ->
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .background(
                                        swatch,
                                        RoundedCornerShape(50)
                                    )
                            )
                        }
                    }
                },
                label = {
                    Text(value)
                }
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
    onProduct: (StoreProduct) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    var sizes by remember { mutableStateOf(setOf<String>()) }
    var colors by remember { mutableStateOf(setOf<String>()) }
    var brands by remember { mutableStateOf(setOf<String>()) }

    val allSizes = products.flatMap { it.filterValues("Tallas") }.distinct().sorted()
    val allColors = products.flatMap { it.filterValues("Color") }.distinct().sorted()
    val allBrands = products.flatMap { it.tags.map { t -> t.name } }
        .filter { it.isNotBlank() }.distinct().sorted()

    val possibleParentSizes = products
        .filter { it.matchesCatalogFilters(emptySet(), colors, brands) }
        .flatMap { it.filterValues("Tallas") }
        .toSet()

    val possibleParentColors = products
        .filter { it.matchesCatalogFilters(sizes, emptySet(), brands) }
        .flatMap { it.filterValues("Color") }
        .toSet()

    val possibleParentBrands = products
        .filter { it.matchesCatalogFilters(sizes, colors, emptySet()) }
        .flatMap { it.tags.map { tag -> tag.name } }
        .toSet()

    val filtered = products.filter {
        it.matchesCatalogFilters(sizes, colors, brands)
    }

    val active = sizes.size + colors.size + brands.size

    Column(Modifier.fillMaxSize().padding(padding)) {
        CatalogScreenHeader(
            title = title,
            onBack = onBack
        ) {
            OutlinedButton(onClick = { open = true }) {
                Text(if (active == 0) "Filtros" else "Filtros ($active)")
            }
        }

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        if (active == 0) {
            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(children.chunked(3)) { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
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
                            repeat(3 - row.size) {
                                Spacer(Modifier.weight(1f))
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
            ProductGrid(filtered, Modifier.fillMaxSize(), onProduct)
        }
    }

    if (open) {
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
            confirmButton = {
                TextButton(onClick = { open = false }) { Text("Aplicar") }
            },
            dismissButton = {
                TextButton(onClick = {
                    sizes = emptySet()
                    colors = emptySet()
                    brands = emptySet()
                }) { Text("Limpiar") }
            }
        )
    }
}
