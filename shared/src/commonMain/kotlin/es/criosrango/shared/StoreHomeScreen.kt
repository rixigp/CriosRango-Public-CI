package es.criosrango.shared

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreGlobalBrand
import es.criosrango.shared.model.StoreProduct
import es.criosrango.shared.generated.resources.Res
import es.criosrango.shared.generated.resources.category_bautizo
import es.criosrango.shared.generated.resources.category_bebe_nina
import es.criosrango.shared.generated.resources.category_bebe_nino
import es.criosrango.shared.generated.resources.category_calzado
import es.criosrango.shared.generated.resources.category_comunion_nina
import es.criosrango.shared.generated.resources.category_comunion_nino
import es.criosrango.shared.generated.resources.category_hombre
import es.criosrango.shared.generated.resources.category_mujer
import es.criosrango.shared.generated.resources.category_nina
import es.criosrango.shared.generated.resources.category_nino
import es.criosrango.shared.generated.resources.category_outlet
import es.criosrango.shared.generated.resources.category_recien_nacido
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.Image
import kotlinx.coroutines.delay

internal val STORE_HOME_CATEGORY_IMAGES = mapOf(
    "category_bautizo" to Res.drawable.category_bautizo,
    "category_bebe_nina" to Res.drawable.category_bebe_nina,
    "category_bebe_nino" to Res.drawable.category_bebe_nino,
    "category_calzado" to Res.drawable.category_calzado,
    "category_comunion_nina" to Res.drawable.category_comunion_nina,
    "category_comunion_nino" to Res.drawable.category_comunion_nino,
    "category_hombre" to Res.drawable.category_hombre,
    "category_mujer" to Res.drawable.category_mujer,
    "category_nina" to Res.drawable.category_nina,
    "category_nino" to Res.drawable.category_nino,
    "category_recien_nacido" to Res.drawable.category_recien_nacido,
    "category_outlet" to Res.drawable.category_outlet
)

internal fun storeHomeBrandPages(state: StoreHomeState): List<List<StoreGlobalBrand>> =
    state.brands.chunked(HOME_BRANDS_VISUAL_PAGE_SIZE)

internal fun storeHomeSectionHasRenderableContent(
    status: StoreHomeSectionStatus,
    hasItems: Boolean
): Boolean = hasItems && status != StoreHomeSectionStatus.LOADING &&
    status != StoreHomeSectionStatus.ERROR && status != StoreHomeSectionStatus.EMPTY

internal fun storeHomeOutletSeasonCategory(
    state: StoreHomeState,
    season: String
): StoreCategory? = state.outletCategories.firstOrNull {
    it.parent == 445 && it.name.lowercase().contains(season)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreHomeScreen(
    storeApi: StoreApiClient,
    brandsController: StoreBrandsController,
    padding: PaddingValues,
    cartStore: StoreCartStore? = null,
    homeController: StoreHomeController? = null,
    onProduct: (StoreProduct) -> Unit,
    onCategory: (StoreCategory) -> Unit,
    onNovedades: () -> Unit,
    onBrand: (StoreGlobalBrand) -> Unit,
    onAllBrands: () -> Unit,
    onOutletWinter: (StoreCategory?) -> Unit,
    onOutletSummer: (StoreCategory?) -> Unit,
    onOutletAll: () -> Unit
) {
    val controller = homeController ?: storeHomeController(storeApi, brandsController)
    val state by controller.state.collectAsState()
    var heroIndex by remember { mutableStateOf(0) }

    LaunchedEffect(controller) { controller.start() }
    val heroImages = when (state.mediaStatus) {
        StoreHomeMediaStatus.LOADING -> state.mediaConfig.heroImages
        StoreHomeMediaStatus.REMOTE,
        StoreHomeMediaStatus.EMPTY,
        StoreHomeMediaStatus.ERROR -> state.mediaConfig.heroImages
    }

    LaunchedEffect(heroImages) {
        if (heroImages.isNotEmpty()) {
            heroIndex = heroIndex.coerceIn(0, heroImages.lastIndex)
            while (heroImages.size > 1) {
                // Keep the currently displayed bitmap until the next one is in the shared
                // RemoteStoreImage cache. Only then change the Crossfade target.
                delay(3500)
                val nextIndex = (heroIndex + 1) % heroImages.size
                while (!prefetchRemoteStoreImage(heroImages[nextIndex])) {
                    delay(1_000)
                }
                heroIndex = nextIndex
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = controller::refresh,
        modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())
    ) {
        LazyColumn(
            state = StoreScreenOwners.homeScrollState,
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Box(
                    Modifier.fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (heroImages.isNotEmpty()) {
                        Crossfade(targetState = heroIndex.coerceIn(0, heroImages.lastIndex)) { index ->
                            RemoteStoreImage(
                                heroImages[index],
                                null,
                                Modifier.fillMaxSize(),
                                ContentScale.Crop,
                                backgroundColor = null
                            )
                        }
                    }
                }
            }
            item {
                Column {
                    StoreHomeSectionHeader("Categorías")
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        state.categories.chunked(3).forEach { rowCategories ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowCategories.forEach { entry ->
                                    Column(
                                        Modifier.weight(1f).clickable { onCategory(entry.category) },
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Image(
                                            painter = painterResource(STORE_HOME_CATEGORY_IMAGES.getValue(entry.imageKey)),
                                            contentDescription = entry.category.name,
                                            modifier = Modifier.fillMaxWidth().aspectRatio(1f).padding(1.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            entry.category.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            textAlign = TextAlign.Center,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                repeat(3 - rowCategories.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            item {
                Column {
                    StoreHomeSectionHeader("Novedades", "Ver todo", onNovedades)
                    Spacer(Modifier.height(12.dp))
                    if (storeHomeSectionHasRenderableContent(state.novedadesStatus, state.novedadesPreview.isNotEmpty())) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            items(state.novedadesPreview, key = { it.id }) { product ->
                                IosProductCard(product, onProduct, cartStore)
                            }
                        }
                    } else {
                        StoreHomeSectionStatus(
                            status = state.novedadesStatus,
                            error = state.novedadesError,
                            emptyMessage = "No hay novedades.",
                            onRetry = controller::retryNovedades
                        )
                    }
                    if (state.novedadesError != null && state.novedadesPreview.isNotEmpty()) {
                        StoreHomeInlineError(state.novedadesError!!, controller::retryNovedades)
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth()) {
                    StoreHomeSectionHeader("Outlet", "Ver todo", onOutletAll)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StoreHomeOutletCard(
                            "Invierno",
                            state.mediaConfig.outletWinterImage ?: STORE_HOME_OUTLET_WINTER_IMAGE,
                            { onOutletWinter(storeHomeOutletSeasonCategory(state, "invierno")) },
                            Modifier.weight(1f)
                        )
                        StoreHomeOutletCard(
                            "Verano",
                            state.mediaConfig.outletSummerImage ?: STORE_HOME_OUTLET_SUMMER_IMAGE,
                            { onOutletSummer(storeHomeOutletSeasonCategory(state, "verano")) },
                            Modifier.weight(1f)
                        )
                    }
                    if (state.outletStatus != StoreHomeSectionStatus.CONTENT ||
                        state.outletError != null
                    ) {
                        StoreHomeSectionStatus(
                            status = state.outletStatus,
                            error = state.outletError,
                            emptyMessage = "No hay destinos de Outlet disponibles.",
                            onRetry = controller::retryOutlet
                        )
                    }
                }
            }
            item {
                Column {
                    StoreHomeSectionHeader("Marcas", "Ver todas", onAllBrands)
                    Spacer(Modifier.height(12.dp))
                    val pages = storeHomeBrandPages(state)
                    if (storeHomeSectionHasRenderableContent(state.brandsStatus, state.brands.isNotEmpty())) {
                        val pagerState = rememberPagerState(pageCount = { pages.size })
                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { page ->
                            Column(
                                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                pages[page].chunked(4).forEach { rowBrands ->
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowBrands.forEach { brand ->
                                            Box(
                                                Modifier.weight(1f).height(54.dp)
                                                    .clip(RoundedCornerShape(18.dp))
                                                    .background(Color.White)
                                                    .clickable { onBrand(brand) }
                                                    .padding(horizontal = 2.dp, vertical = 2.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (!brand.imageUrl.isNullOrBlank()) {
                                                    RemoteStoreImage(
                                                        brand.imageUrl,
                                                        brand.name,
                                                        Modifier.fillMaxSize(),
                                                        ContentScale.Fit,
                                                        backgroundColor = null
                                                    )
                                                } else {
                                                    Text(
                                                        brand.name,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        textAlign = TextAlign.Center,
                                                        maxLines = 2,
                                                        modifier = Modifier.padding(horizontal = 8.dp)
                                                    )
                                                }
                                            }
                                        }
                                        repeat(4 - rowBrands.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                }
                                if (pages[page].size <= 4) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        repeat(4) { Spacer(Modifier.weight(1f).height(54.dp)) }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                            repeat(pages.size) { index ->
                                Box(
                                    Modifier.padding(horizontal = 4.dp)
                                        .size(if (pagerState.currentPage == index) 8.dp else 6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (pagerState.currentPage == index) Color(0xFF183B35)
                                            else Color(0xFF183B35).copy(alpha = 0.22f)
                                        )
                                )
                            }
                        }
                    } else {
                        StoreHomeSectionStatus(
                            status = state.brandsStatus,
                            error = state.brandsError,
                            emptyMessage = "No hay marcas disponibles.",
                            onRetry = controller::retryBrands
                        )
                    }
                    if (state.brandsError != null && state.brands.isNotEmpty()) {
                        StoreHomeInlineError(state.brandsError!!, controller::retryBrands)
                    }
                }
            }
        }
    }
}

@Composable
private fun StoreHomeSectionHeader(
    title: String,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF183B35)
        )
        if (action != null && onAction != null) {
            Box(
                modifier = Modifier.width(88.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        action,
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFD18162),
                        textAlign = TextAlign.End
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreHomeSectionStatus(
    status: StoreHomeSectionStatus,
    error: String?,
    emptyMessage: String,
    onRetry: () -> Unit
) {
    when (status) {
        StoreHomeSectionStatus.LOADING -> Unit
        StoreHomeSectionStatus.ERROR -> Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(error ?: "No se ha podido cargar esta sección.", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onRetry) { Text("Reintentar") }
        }
        StoreHomeSectionStatus.EMPTY -> Text(
            emptyMessage,
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        StoreHomeSectionStatus.CONTENT -> if (error != null) StoreHomeInlineError(error, onRetry)
    }
}

@Composable
private fun StoreHomeInlineError(message: String, onRetry: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onRetry) { Text("Reintentar") }
    }
}

@Composable
private fun StoreHomeOutletCard(
    title: String,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier.height(112.dp).clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFE7E2E6)).clickable(onClick = onClick)
    ) {
        RemoteStoreImage(imageUrl, "Outlet $title", Modifier.fillMaxSize(), ContentScale.Crop, backgroundColor = null)
        Box(
            Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.60f))
                )
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(13.dp)) {
            Text("Outlet", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(title, color = Color.White.copy(alpha = 0.95f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
