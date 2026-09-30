package es.criosrango.shared

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * UI/lifecycle adapter over the shared CatalogPaginator.
 * It owns no pagination rules: sequencing, deduplication, invalidation and errors stay shared.
 */
class CatalogPaginatorStore<T>(
    private val scope: CoroutineScope,
    identity: (T) -> Any
) {
    private val paginator = CatalogPaginator<T>(identity = identity)

    val state: StateFlow<CatalogPagingState<T>>
        get() = paginator.state

    fun start(queryKey: String, loadPage: suspend (page: Int, perPage: Int) -> CatalogPage<T>): Job =
        scope.launch { paginator.start(queryKey, loadPage) }

    fun loadNext(loadPage: suspend (page: Int, perPage: Int) -> CatalogPage<T>): Job =
        scope.launch { paginator.loadNext(loadPage) }

    fun invalidate(): Job = scope.launch { paginator.invalidate() }
}
