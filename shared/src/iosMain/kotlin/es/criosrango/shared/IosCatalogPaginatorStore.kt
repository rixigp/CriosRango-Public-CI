package es.criosrango.shared

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow

/**
 * iOS lifecycle/UI adapter over the shared CatalogPaginator.
 * Pagination, deduplication, query invalidation and error state remain owned by CatalogPaginator.
 */
class IosCatalogPaginatorStore<T>(
    private val scope: CoroutineScope,
    identity: (T) -> Any
) {
    private val paginator = CatalogPaginator<T>(identity = identity)

    val state: StateFlow<CatalogPagingState<T>>
        get() = paginator.state

    fun start(
        queryKey: String,
        loadPage: suspend (page: Int, perPage: Int) -> CatalogPage<T>
    ): Job = scope.launch {
        paginator.start(queryKey, loadPage)
    }

    fun loadNext(
        loadPage: suspend (page: Int, perPage: Int) -> CatalogPage<T>
    ): Job = scope.launch {
        paginator.loadNext(loadPage)
    }

    fun invalidate(): Job = scope.launch {
        paginator.invalidate()
    }
}
