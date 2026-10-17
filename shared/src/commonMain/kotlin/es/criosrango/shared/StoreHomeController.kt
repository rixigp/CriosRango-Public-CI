package es.criosrango.shared

import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.api.StoreHomeMediaConfigResponse
import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreGlobalBrand
import es.criosrango.shared.model.StoreProduct
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

const val HOME_NOVEDADES_LIMIT = 8
const val HOME_BRANDS_MAX = 16
const val HOME_BRANDS_VISUAL_PAGE_SIZE = 8
const val HOME_CATEGORY_COUNT = 11

internal val STORE_HOME_HERO_IMAGES = listOf(
    "https://images.pexels.com/photos/6617704/pexels-photo-6617704.jpeg?auto=compress&cs=tinysrgb&w=1600",
    "https://images.pexels.com/photos/19915135/pexels-photo-19915135.jpeg?auto=compress&cs=tinysrgb&w=1600",
    "https://images.pexels.com/photos/30804210/pexels-photo-30804210.jpeg?auto=compress&cs=tinysrgb&w=1600",
    "https://images.pexels.com/photos/29614374/pexels-photo-29614374.jpeg?auto=compress&cs=tinysrgb&w=1600"
)

internal const val STORE_HOME_OUTLET_WINTER_IMAGE =
    "https://www.criosrango.es/wp-content/uploads/2025/11/208-25TR-JOBIM-1-scaled-reczmlo0rsccbsjo2he9f17qguqnu6a9b27sqk1364.webp"
internal const val STORE_HOME_OUTLET_SUMMER_IMAGE =
    "https://www.criosrango.es/wp-content/uploads/2025/05/mayoral-boys-ss-polo-white-3106-select-size-10-yrs-273862-p-1.jpg"

data class HomeMediaConfig(
    val heroImages: List<String>,
    val outletWinterImage: String?,
    val outletSummerImage: String?
)

val STORE_HOME_FALLBACK_MEDIA_CONFIG = HomeMediaConfig(
    heroImages = STORE_HOME_HERO_IMAGES,
    outletWinterImage = STORE_HOME_OUTLET_WINTER_IMAGE,
    outletSummerImage = STORE_HOME_OUTLET_SUMMER_IMAGE
)

internal fun resolveHomeMediaConfig(remote: StoreHomeMediaConfigResponse): HomeMediaConfig {
    val heroes = remote.heroImages.orEmpty().mapNotNull(::validHomeMediaUrl).distinct()
    return HomeMediaConfig(
        heroImages = heroes.ifEmpty { STORE_HOME_FALLBACK_MEDIA_CONFIG.heroImages },
        outletWinterImage = validHomeMediaUrl(remote.outletWinterImage)
            ?: STORE_HOME_FALLBACK_MEDIA_CONFIG.outletWinterImage,
        outletSummerImage = validHomeMediaUrl(remote.outletSummerImage)
            ?: STORE_HOME_FALLBACK_MEDIA_CONFIG.outletSummerImage
    )
}

private fun validHomeMediaUrl(value: String?): String? {
    val url = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return url.takeIf {
        (it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)) &&
            it.substringAfter("://").substringBefore('/').isNotBlank()
    }
}

/** Shared editorial Home category plus the canonical shared asset key used by the renderer. */
data class StoreHomeCategory(
    val category: StoreCategory,
    val imageKey: String
)

/** Curated Home categories. IDs, labels and order are the approved Android baseline. */
val HOME_ROOT_CATEGORIES: List<StoreHomeCategory> = listOf(
    StoreHomeCategory(StoreCategory(id = 560, parent = 0, name = "Bautizo", count = 0, slug = ""), "category_bautizo"),
    StoreHomeCategory(StoreCategory(id = 292, parent = 0, name = "Bebe niña", count = 0, slug = ""), "category_bebe_nina"),
    StoreHomeCategory(StoreCategory(id = 294, parent = 0, name = "Bebé niño", count = 0, slug = ""), "category_bebe_nino"),
    StoreHomeCategory(StoreCategory(id = 420, parent = 0, name = "Calzado", count = 0, slug = ""), "category_calzado"),
    StoreHomeCategory(StoreCategory(id = 504, parent = 0, name = "Comunión niña", count = 0, slug = ""), "category_comunion_nina"),
    StoreHomeCategory(StoreCategory(id = 509, parent = 0, name = "Comunión niño", count = 0, slug = ""), "category_comunion_nino"),
    StoreHomeCategory(StoreCategory(id = 70, parent = 0, name = "Hombre", count = 0, slug = ""), "category_hombre"),
    StoreHomeCategory(StoreCategory(id = 71, parent = 0, name = "Mujer", count = 0, slug = ""), "category_mujer"),
    StoreHomeCategory(StoreCategory(id = 68, parent = 0, name = "Niña", count = 0, slug = ""), "category_nina"),
    StoreHomeCategory(StoreCategory(id = 310, parent = 0, name = "Niño", count = 0, slug = ""), "category_nino"),
    StoreHomeCategory(StoreCategory(id = 67, parent = 0, name = "Recién nacido", count = 0, slug = ""), "category_recien_nacido")
)

 /** Fallback Outlet season destinations already used by Android when Store API omits these child terms. */
val HOME_OUTLET_SEASON_CATEGORIES: List<StoreCategory> = listOf(
    StoreCategory(id = 446, parent = 445, name = "Hombre invierno", count = 0, slug = "hombre-invierno-outlet"),
    StoreCategory(id = 475, parent = 445, name = "Hombre verano", count = 0, slug = "hombre-verano-outlet")
)

enum class StoreHomeSectionStatus {
    LOADING,
    CONTENT,
    ERROR,
    EMPTY
}

enum class StoreHomeMediaStatus {
    LOADING,
    REMOTE,
    EMPTY,
    ERROR
}

data class StoreHomeState(
    val novedadesPreview: List<StoreProduct> = emptyList(),
    val categories: List<StoreHomeCategory> = HOME_ROOT_CATEGORIES,
    val brands: List<StoreGlobalBrand> = emptyList(),
    /** Full category snapshot used only to resolve Home's Invierno/Verano Outlet destinations. */
    val outletCategories: List<StoreCategory> = emptyList(),
    val mediaConfig: HomeMediaConfig = STORE_HOME_FALLBACK_MEDIA_CONFIG.copy(heroImages = emptyList()),
    val mediaStatus: StoreHomeMediaStatus = StoreHomeMediaStatus.LOADING,
    val novedadesStatus: StoreHomeSectionStatus = StoreHomeSectionStatus.LOADING,
    val categoriesStatus: StoreHomeSectionStatus = StoreHomeSectionStatus.CONTENT,
    val brandsStatus: StoreHomeSectionStatus = StoreHomeSectionStatus.LOADING,
    val outletStatus: StoreHomeSectionStatus = StoreHomeSectionStatus.LOADING,
    val novedadesError: String? = null,
    val categoriesError: String? = null,
    val brandsError: String? = null,
    val outletError: String? = null,
    val refreshing: Boolean = false
)

interface StoreHomeDataSource {
    suspend fun products(perPage: Int, orderBy: String, order: String): List<StoreProduct>
    suspend fun brands(): List<StoreGlobalBrand>
    suspend fun outletCategories(): List<StoreCategory>
    suspend fun homeMediaConfig(): StoreHomeMediaConfigResponse? = null
    fun retryBrands() = Unit
}

/** Reuses the app's existing StoreBrandsController; Home does not create a second brands fetcher. */
class StoreApiHomeDataSource(
    private val api: StoreApiClient,
    private val brandsController: StoreBrandsController
) : StoreHomeDataSource {
    override suspend fun products(perPage: Int, orderBy: String, order: String): List<StoreProduct> =
        api.products(perPage = perPage, page = 1, orderBy = orderBy, order = order)

    override suspend fun brands(): List<StoreGlobalBrand> {
        val state = brandsController.state.first { !it.brandsLoading }
        if (state.brands.isEmpty() && state.brandsError != null) {
            error(state.brandsError)
        }
        return state.brands
            .filter { it.id > 0 && it.count > 0 && it.name.isNotBlank() && it.slug.isNotBlank() }
            .sortedWith(compareByDescending<StoreGlobalBrand> { it.count }.thenBy { it.name.trim().lowercase() })
            .take(HOME_BRANDS_MAX)
    }

    override fun retryBrands() {
        brandsController.retryBrands()
    }

    override suspend fun homeMediaConfig(): StoreHomeMediaConfigResponse = api.homeMediaConfig()

    override suspend fun outletCategories(): List<StoreCategory> {
        val all = mutableListOf<StoreCategory>()
        var page = 1
        while (true) {
            val batch = api.categories(perPage = 100, page = page)
            all += batch
            if (batch.size < 100) break
            page++
        }
        return all.distinctBy { it.id }
    }
}

/**
 * Single Home data owner. Each network section commits independently, so one failed request
 * never clears healthy sections. Refresh/retry are explicit; no automatic retry loop is used.
 */
class StoreHomeController(
    private val scope: CoroutineScope,
    private val source: StoreHomeDataSource
) {
    private val mutableState = MutableStateFlow(StoreHomeState())
    val state: StateFlow<StoreHomeState> = mutableState.asStateFlow()

    private var productsGeneration = 0L
    private var brandsGeneration = 0L
    private var outletGeneration = 0L
    private var mediaGeneration = 0L
    private var refreshJob: Job? = null
    private var refreshGeneration = 0L
    /** Last successful non-empty remote media config, retained across refresh errors. */
    private var lastValidRemoteMediaConfig: HomeMediaConfig? = null

    fun start() {
        if (mutableState.value.novedadesStatus == StoreHomeSectionStatus.LOADING &&
            mutableState.value.brandsStatus == StoreHomeSectionStatus.LOADING &&
            mutableState.value.outletStatus == StoreHomeSectionStatus.LOADING
        ) loadSections(showRefreshing = false)
    }

    fun refresh() {
        loadSections(showRefreshing = true)
    }

    private fun loadSections(showRefreshing: Boolean) {
        refreshJob?.cancel()
        val refreshRequest = ++refreshGeneration
        val productsRequest = ++productsGeneration
        val brandsRequest = ++brandsGeneration
        val outletRequest = ++outletGeneration
        val mediaRequest = ++mediaGeneration
        // Snapshot the last known-good remote config before media enters LOADING.
        val mediaConfigToPreserve = lastValidRemoteMediaConfig
        refreshJob = scope.launch {
            mutableState.value = mutableState.value.copy(
                refreshing = showRefreshing,
                mediaStatus = if (showRefreshing) StoreHomeMediaStatus.LOADING else mutableState.value.mediaStatus
            )
            try {
                coroutineScope {
                    launch { loadProducts(productsRequest) }
                    launch { loadBrands(brandsRequest) }
                    launch { loadOutletCategories(outletRequest) }
                    launch { loadHomeMediaConfig(mediaRequest, mediaConfigToPreserve) }
                }
            } finally {
                if (refreshRequest == refreshGeneration) {
                    mutableState.value = mutableState.value.copy(refreshing = false)
                }
            }
        }
    }

    fun retryNovedades() {
        val request = ++productsGeneration
        scope.launch { loadProducts(request) }
    }

    fun retryBrands() {
        source.retryBrands()
        val request = ++brandsGeneration
        scope.launch { loadBrands(request) }
    }

    fun retryOutlet() {
        val request = ++outletGeneration
        scope.launch { loadOutletCategories(request) }
    }

    private suspend fun loadProducts(request: Long) {
        val previous = mutableState.value
        mutableState.value = previous.copy(
            novedadesStatus = if (previous.novedadesPreview.isEmpty()) StoreHomeSectionStatus.LOADING else previous.novedadesStatus,
            novedadesError = null
        )
        try {
            val products = source.products(HOME_NOVEDADES_LIMIT, orderBy = "date", order = "desc")
                .distinctBy { it.id }
                .take(HOME_NOVEDADES_LIMIT)
            if (request != productsGeneration) return
            mutableState.value = mutableState.value.copy(
                novedadesPreview = products,
                novedadesStatus = if (products.isEmpty()) StoreHomeSectionStatus.EMPTY else StoreHomeSectionStatus.CONTENT,
                novedadesError = null
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (request != productsGeneration) return
            val current = mutableState.value
            mutableState.value = current.copy(
                novedadesStatus = if (current.novedadesPreview.isEmpty()) StoreHomeSectionStatus.ERROR else StoreHomeSectionStatus.CONTENT,
                novedadesError = error.message ?: "No se han podido cargar las novedades."
            )
        }
    }

    private suspend fun loadBrands(request: Long) {
        val previous = mutableState.value
        mutableState.value = previous.copy(
            brandsStatus = if (previous.brands.isEmpty()) StoreHomeSectionStatus.LOADING else previous.brandsStatus,
            brandsError = null
        )
        try {
            val brands = source.brands()
                .distinctBy { it.id }
                .filter { it.id > 0 && it.count > 0 && it.name.isNotBlank() && it.slug.isNotBlank() }
                .sortedWith(compareByDescending<StoreGlobalBrand> { it.count }.thenBy { it.name.trim().lowercase() })
                .take(HOME_BRANDS_MAX)
            if (request != brandsGeneration) return
            mutableState.value = mutableState.value.copy(
                brands = brands,
                brandsStatus = if (brands.isEmpty()) StoreHomeSectionStatus.EMPTY else StoreHomeSectionStatus.CONTENT,
                brandsError = null
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (request != brandsGeneration) return
            val current = mutableState.value
            mutableState.value = current.copy(
                brandsStatus = if (current.brands.isEmpty()) StoreHomeSectionStatus.ERROR else StoreHomeSectionStatus.CONTENT,
                brandsError = error.message ?: "No se han podido cargar las marcas."
            )
        }
    }

    private suspend fun loadHomeMediaConfig(
        request: Long,
        previousValidRemoteConfig: HomeMediaConfig?
    ) {
        try {
            val remote = source.homeMediaConfig()
                ?: throw IllegalStateException("Home media configuration is unavailable.")
            if (request != mediaGeneration) return
            val remoteHeroImages = remote.heroImages.orEmpty()
                .mapNotNull(::validHomeMediaUrl)
                .distinct()
            val resolvedConfig = resolveHomeMediaConfig(remote)
            val mediaStatus = if (remoteHeroImages.isEmpty()) {
                lastValidRemoteMediaConfig = null
                StoreHomeMediaStatus.EMPTY
            } else {
                lastValidRemoteMediaConfig = resolvedConfig
                StoreHomeMediaStatus.REMOTE
            }
            // Commit the resolved images and status together so the renderer never observes
            // an intermediate empty/fallback list while switching from loading to the result.
            mutableState.value = mutableState.value.copy(
                mediaConfig = resolvedConfig,
                mediaStatus = mediaStatus
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            if (request != mediaGeneration) return
            val preservedConfig = previousValidRemoteConfig
            mutableState.value = if (preservedConfig != null) {
                mutableState.value.copy(
                    mediaConfig = preservedConfig,
                    mediaStatus = StoreHomeMediaStatus.REMOTE
                )
            } else {
                mutableState.value.copy(
                    mediaConfig = STORE_HOME_FALLBACK_MEDIA_CONFIG,
                    mediaStatus = StoreHomeMediaStatus.ERROR
                )
            }
        }
    }

    private suspend fun loadOutletCategories(request: Long) {
        val previous = mutableState.value
        mutableState.value = previous.copy(
            outletStatus = if (previous.outletCategories.isEmpty()) StoreHomeSectionStatus.LOADING else previous.outletStatus,
            outletError = null
        )
        try {
            val categories = (source.outletCategories() + HOME_OUTLET_SEASON_CATEGORIES).distinctBy { it.id }
            if (request != outletGeneration) return
            mutableState.value = mutableState.value.copy(
                outletCategories = categories,
                outletStatus = if (categories.isEmpty()) StoreHomeSectionStatus.EMPTY else StoreHomeSectionStatus.CONTENT,
                outletError = null
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (request != outletGeneration) return
            val current = mutableState.value
            mutableState.value = current.copy(
                outletStatus = if (current.outletCategories.isEmpty()) StoreHomeSectionStatus.ERROR else StoreHomeSectionStatus.CONTENT,
                outletError = error.message ?: "No se ha podido cargar el Outlet."
            )
        }
    }
}
