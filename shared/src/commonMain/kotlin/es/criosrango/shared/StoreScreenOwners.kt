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
}
