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
    private val pageSize: Int = CatalogPaginator.PAGE_SIZE,
    identity: (T) -> Any
) {
    private val paginator = CatalogPaginator(pageSize = pageSize, identity = identity)

    val state: StateFlow<CatalogPagingState<T>>
        get() = paginator.state

    fun start(queryKey: String, loadPage: suspend (page: Int, perPage: Int) -> CatalogPage<T>): Job =
        scope.launch { paginator.start(queryKey, loadPage) }

    fun loadNext(loadPage: suspend (page: Int, perPage: Int) -> CatalogPage<T>): Job =
        scope.launch { paginator.loadNext(loadPage) }

    fun restore(queryKey: String, state: CatalogPagingState<T>): Job = scope.launch { paginator.restore(queryKey, state) }

    fun invalidate(): Job = scope.launch { paginator.invalidate() }
}
