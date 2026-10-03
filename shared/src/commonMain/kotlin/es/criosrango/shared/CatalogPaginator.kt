package es.criosrango.shared

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A single backend page returned by a paged catalog query. */
data class CatalogPage<T>(
    val items: List<T>,
    val hasMore: Boolean,
    val totalItems: Int? = null
)

data class CatalogPagingState<T>(
    val items: List<T> = emptyList(),
    val currentPage: Int = 0,
    val hasMore: Boolean = true,
    val isInitialLoading: Boolean = false,
    val isAppending: Boolean = false,
    val initialError: Throwable? = null,
    val appendError: Throwable? = null,
    val totalItems: Int? = null
)

/**
 * Shared KMP pagination state machine. Platform UIs only decide when to call
 * loadNext(); all page sequencing, append, deduplication and stale-query
 * protection is shared between Android and iOS.
 */
class CatalogPaginator<T>(
    private val pageSize: Int = PAGE_SIZE,
    private val identity: (T) -> Any
) {
    init {
        require(pageSize > 0)
    }

    companion object {
        const val PAGE_SIZE: Int = 12
        const val PREFETCH_DISTANCE: Int = 5
    }

    private val mutex = Mutex()
    private var generation = 0L
    private var requestInFlight = false
    private var activeQueryKey: String? = null

    private val _state = MutableStateFlow(CatalogPagingState<T>())
    val state: StateFlow<CatalogPagingState<T>> = _state.asStateFlow()

    /** Starts page 1 for a new query. Older in-flight responses are ignored. */
    suspend fun start(queryKey: String, loadPage: suspend (page: Int, perPage: Int) -> CatalogPage<T>) {
        val requestGeneration = mutex.withLock {
            generation += 1
            activeQueryKey = queryKey
            requestInFlight = true
            _state.value = CatalogPagingState(isInitialLoading = true)
            generation
        }

        try {
            val page = loadPage(1, pageSize)
            mutex.withLock {
                if (requestGeneration != generation || activeQueryKey != queryKey) return
                val items = distinct(page.items)
                _state.value = CatalogPagingState(
                    items = items,
                    currentPage = 1,
                    hasMore = page.hasMore,
                    isInitialLoading = false,
                    totalItems = page.totalItems
                )
                requestInFlight = false
            }
        } catch (error: Throwable) {
            mutex.withLock {
                if (requestGeneration != generation || activeQueryKey != queryKey) return
                _state.value = CatalogPagingState(
                    isInitialLoading = false,
                    initialError = error
                )
                requestInFlight = false
            }
        }
    }

    /** Loads exactly the next page. Duplicate concurrent calls are ignored. */
    suspend fun loadNext(loadPage: suspend (page: Int, perPage: Int) -> CatalogPage<T>) {
        val request = mutex.withLock {
            val current = _state.value
            if (
                current.isInitialLoading ||
                current.isAppending ||
                !current.hasMore ||
                requestInFlight ||
                current.currentPage <= 0
            ) return

            requestInFlight = true
            _state.value = current.copy(isAppending = true, appendError = null)
            Triple(generation, activeQueryKey, current.currentPage + 1)
        }

        val (requestGeneration, queryKey, nextPage) = request
        try {
            val page = loadPage(nextPage, pageSize)
            mutex.withLock {
                if (requestGeneration != generation || queryKey != activeQueryKey) return
                val current = _state.value
                _state.value = current.copy(
                    items = distinct(current.items + page.items),
                    currentPage = nextPage,
                    hasMore = page.hasMore,
                    isAppending = false,
                    appendError = null,
                    totalItems = page.totalItems ?: current.totalItems
                )
                requestInFlight = false
            }
        } catch (error: Throwable) {
            mutex.withLock {
                if (requestGeneration != generation || queryKey != activeQueryKey) return
                _state.value = _state.value.copy(
                    isAppending = false,
                    appendError = error
                )
                requestInFlight = false
            }
        }
    }

    /** Restores an already loaded query snapshot without issuing a new page-1 request. */
    suspend fun restore(queryKey: String, restoredState: CatalogPagingState<T>) {
        mutex.withLock {
            generation += 1
            activeQueryKey = queryKey
            requestInFlight = false
            _state.value = restoredState
        }
    }

    /** Invalidates the current query without starting a request. */
    suspend fun invalidate() {
        mutex.withLock {
            generation += 1
            activeQueryKey = null
            requestInFlight = false
            _state.value = CatalogPagingState()
        }
    }

    private fun distinct(items: List<T>): List<T> {
        val seen = HashSet<Any>()
        return items.filter { seen.add(identity(it)) }
    }
}
