package es.criosrango.shared

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import es.criosrango.shared.api.StoreApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Shared application-lifetime owners survive temporary screen removal during in-flow navigation. */
internal object StoreScreenOwners {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val homeScrollState = LazyListState()
    val novedadesScrollState = LazyGridState()
    private var homeApi: StoreApiClient? = null
    private var homeBrands: StoreBrandsController? = null
    private var homeController: StoreHomeController? = null
    private var novedadesApi: StoreApiClient? = null
    private var novedadesController: StoreNovedadesController? = null
    private var searchApi: StoreApiClient? = null
    private var searchController: StoreSearchController? = null

    fun home(api: StoreApiClient, brands: StoreBrandsController): StoreHomeController {
        if (homeController == null || homeApi !== api || homeBrands !== brands) {
            homeApi = api
            homeBrands = brands
            homeController = StoreHomeController(scope, StoreApiHomeDataSource(api, brands))
        }
        return homeController!!
    }

    fun novedades(api: StoreApiClient): StoreNovedadesController {
        if (novedadesController == null || novedadesApi !== api) {
            novedadesApi = api
            novedadesController = StoreNovedadesController(scope, StoreApiNovedadesDataSource(api))
        }
        return novedadesController!!
    }

    fun search(api: StoreApiClient): StoreSearchController {
        if (searchController == null || searchApi !== api) {
            searchApi = api
            searchController = StoreSearchController(scope, StoreApiSearchDataSource(api))
        }
        return searchController!!
    }
}

/** Returns the application-lifetime Home owner so startup can warm it before Home is composed. */
fun storeHomeController(api: StoreApiClient, brands: StoreBrandsController): StoreHomeController =
    StoreScreenOwners.home(api, brands)

/** Returns the application-lifetime Search owner so results survive Product Detail navigation. */
fun storeSearchController(api: StoreApiClient): StoreSearchController = StoreScreenOwners.search(api)
