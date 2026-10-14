package es.criosrango.shared

import es.criosrango.shared.model.OutletAvailability
import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreProduct
import es.criosrango.shared.model.countFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** The only backend query shape for Outlet. Bubble, filters and sort are local transforms. */
data class StoreOutletQuery(val categoryId: Int)

data class StoreOutletQueryKey(val categoryId: Int) {
    fun paginatorKey(): String = categoryId.toString()
}

fun interface StoreOutletDataSource {
    suspend fun loadPage(query: StoreOutletQuery, page: Int, perPage: Int): CatalogPage<StoreProduct>
}

enum class StoreOutletSort { RECENT, PRICE_ASC, PRICE_DESC, NAME_ASC }

data class StoreOutletState(
    val activeCategoryId: Int? = null,
    val query: StoreOutletQuery? = null,
    val queryKey: StoreOutletQueryKey? = null,
    val selectedBubbleKey: String? = null,
    val selectedSizes: Set<String> = emptySet(),
    val selectedColors: Set<String> = emptySet(),
    val selectedBrands: Set<String> = emptySet(),
    val selectedSort: StoreOutletSort = StoreOutletSort.RECENT,
    val visibleBubbles: List<OutletBubbleDefinition> = emptyList(),
    val paging: CatalogPagingState<StoreProduct> = CatalogPagingState(),
    val matchedProducts: List<StoreProduct> = emptyList(),
    val filteredProducts: List<StoreProduct> = emptyList(),
    val sortedProducts: List<StoreProduct> = emptyList(),
    val sizes: List<String> = emptyList(),
    val colors: List<String> = emptyList(),
    val brands: List<String> = emptyList(),
    val enabledSizes: Set<String> = emptySet(),
    val enabledColors: Set<String> = emptySet(),
    val enabledBrands: Set<String> = emptySet()
) {
    val activeFilterCount: Int get() = selectedSizes.size + selectedColors.size + selectedBrands.size
    val isBubbleProbeRequired: Boolean
        get() = selectedBubbleKey != null && matchedProducts.isEmpty() &&
            paging.currentPage > 0 && !paging.isInitialLoading && !paging.isAppending &&
            paging.initialError == null && paging.appendError == null && paging.hasMore
}

sealed interface StoreOutletIntent {
    data class SelectCategory(val categoryId: Int) : StoreOutletIntent
    data class SelectBubble(val key: String?) : StoreOutletIntent
    data class SelectSizes(val values: Set<String>) : StoreOutletIntent
    data class SelectColors(val values: Set<String>) : StoreOutletIntent
    data class SelectBrands(val values: Set<String>) : StoreOutletIntent
    data class SelectSort(val sort: StoreOutletSort) : StoreOutletIntent
    data class VisibleRangeChanged(val lastVisibleIndex: Int) : StoreOutletIntent
    data object RetryInitial : StoreOutletIntent
    data object RetryAppend : StoreOutletIntent
}

/**
 * Common Outlet business/state owner. Platform code supplies category data and a page transport;
 * this controller decides the category query, matching, filters, sort, retention and paging policy.
 */
class StoreOutletController(
    private val scope: CoroutineScope,
    private val dataSource: StoreOutletDataSource
) {
    private data class Selection(
        val bubbleKey: String? = null,
        val sizes: Set<String> = emptySet(),
        val colors: Set<String> = emptySet(),
        val brands: Set<String> = emptySet(),
        val sort: StoreOutletSort = StoreOutletSort.RECENT
    )

    private val paginators = linkedMapOf<StoreOutletQueryKey, CatalogPaginator<StoreProduct>>()
    private val initialStartPending = mutableSetOf<StoreOutletQueryKey>()
    private var activeCollector: Job? = null
    private val retainedSelections = mutableMapOf<Int, Selection>()
    private var categoriesById: Map<Int, StoreCategory> = emptyMap()
    private var availability: OutletAvailability? = null
    private var lastProbeToken: String? = null
    private val _state = MutableStateFlow(StoreOutletState())
    val state: StateFlow<StoreOutletState> = _state.asStateFlow()

    fun dispatch(intent: StoreOutletIntent) {
        when (intent) {
            is StoreOutletIntent.SelectCategory -> selectCategory(intent.categoryId)
            is StoreOutletIntent.SelectBubble -> selectBubble(intent.key)
            is StoreOutletIntent.SelectSizes -> selectSizes(intent.values)
            is StoreOutletIntent.SelectColors -> selectColors(intent.values)
            is StoreOutletIntent.SelectBrands -> selectBrands(intent.values)
            is StoreOutletIntent.SelectSort -> selectSort(intent.sort)
            is StoreOutletIntent.VisibleRangeChanged -> onVisibleRangeChanged(intent.lastVisibleIndex)
            StoreOutletIntent.RetryInitial -> retryInitial()
            StoreOutletIntent.RetryAppend -> retryAppend()
        }
    }

    fun updateCategories(categories: List<StoreCategory>) {
        categoriesById = categories.associateBy { it.id }
        publish(_state.value.paging)
    }

    fun updateAvailability(snapshot: OutletAvailability?) {
        availability = snapshot
        val current = _state.value
        val visible = visibleBubbles(current.activeCategoryId)
        val selected = current.selectedBubbleKey?.takeIf { key -> visible.any { it.key == key } }
        if (selected != current.selectedBubbleKey) {
            _state.value = current.copy(selectedBubbleKey = selected)
            saveSelection(current.activeCategoryId, _state.value)
            lastProbeToken = null
        }
        publish(current.paging)
    }

    fun selectCategory(categoryId: Int) {
        if (_state.value.activeCategoryId == categoryId) {
            val existing = paginators[StoreOutletQueryKey(categoryId)]?.state?.value
            if (existing != null && existing.currentPage == 0 && !existing.isInitialLoading) startInitial(categoryId)
            else if (existing != null) publish(existing)
            return
        }

        saveSelection(_state.value.activeCategoryId, _state.value)
        val selection = retainedSelections[categoryId] ?: Selection()
        lastProbeToken = null
        val key = StoreOutletQueryKey(categoryId)
        val paginator = paginators.getOrPut(key) { CatalogPaginator(identity = { it.id }) }
        _state.value = StoreOutletState(
            activeCategoryId = categoryId,
            query = StoreOutletQuery(categoryId),
            queryKey = key,
            selectedBubbleKey = selection.bubbleKey,
            selectedSizes = selection.sizes,
            selectedColors = selection.colors,
            selectedBrands = selection.brands,
            selectedSort = selection.sort
        )
        activeCollector?.cancel()
        activeCollector = scope.launch {
            paginator.state.collect { paging ->
                if (_state.value.queryKey == key) publish(paging)
            }
        }
        publish(paginator.state.value)
        if (paginator.state.value.currentPage == 0 && !paginator.state.value.isInitialLoading) startInitial(categoryId)
    }

    fun selectBubble(key: String?) {
        val current = _state.value
        val validKey = key?.takeIf { candidate -> current.visibleBubbles.any { it.key == candidate } }
        if (validKey == current.selectedBubbleKey) return
        lastProbeToken = null
        _state.value = current.copy(selectedBubbleKey = validKey)
        saveSelection(current.activeCategoryId, _state.value)
        publish(current.paging)
    }

    fun selectSizes(values: Set<String>) = updateSelection { it.copy(sizes = values) }
    fun selectColors(values: Set<String>) = updateSelection { it.copy(colors = values) }
    fun selectBrands(values: Set<String>) = updateSelection { it.copy(brands = values) }
    fun selectSort(sort: StoreOutletSort) = updateSelection { it.copy(sort = sort) }

    fun clearFilters() = updateSelection { it.copy(sizes = emptySet(), colors = emptySet(), brands = emptySet()) }

    fun onVisibleRangeChanged(lastVisibleIndex: Int) {
        val current = _state.value
        val paging = current.paging
        if (current.sortedProducts.isNotEmpty() && paging.currentPage > 0 &&
            !paging.isInitialLoading && !paging.isAppending && paging.appendError == null &&
            paging.hasMore && lastVisibleIndex >= current.sortedProducts.size - 1 - CatalogPaginator.PREFETCH_DISTANCE
        ) {
            requestNext(retry = false)
        }
    }

    fun retryInitial() {
        _state.value.activeCategoryId?.let(::startInitial)
    }

    fun retryAppend() = requestNext(retry = true)

    fun retainedPaginatorCount(): Int = paginators.size

    private fun startInitial(categoryId: Int) {
        val key = StoreOutletQueryKey(categoryId)
        if (!initialStartPending.add(key)) return
        val paginator = paginators.getOrPut(key) { CatalogPaginator(identity = { it.id }) }
        scope.launch {
            try {
                paginator.start(key.paginatorKey()) { page, perPage ->
                    dataSource.loadPage(StoreOutletQuery(categoryId), page, perPage)
                }
            } finally {
                initialStartPending.remove(key)
            }
        }
    }

    private fun requestNext(retry: Boolean) {
        val current = _state.value
        val categoryId = current.activeCategoryId ?: return
        val key = StoreOutletQueryKey(categoryId)
        val paginator = paginators[key] ?: return
        val paging = paginator.state.value
        if (paging.currentPage <= 0 || paging.isInitialLoading || paging.isAppending || !paging.hasMore) return
        if (!retry && paging.appendError != null) return
        scope.launch {
            paginator.loadNext { page, perPage ->
                dataSource.loadPage(StoreOutletQuery(categoryId), page, perPage)
            }
        }
    }

    private fun updateSelection(transform: (Selection) -> Selection) {
        val current = _state.value
        val categoryId = current.activeCategoryId ?: return
        val before = Selection(current.selectedBubbleKey, current.selectedSizes, current.selectedColors, current.selectedBrands, current.selectedSort)
        val after = transform(before)
        if (before == after) return
        retainedSelections[categoryId] = after
        _state.value = current.copy(
            selectedBubbleKey = after.bubbleKey,
            selectedSizes = after.sizes,
            selectedColors = after.colors,
            selectedBrands = after.brands,
            selectedSort = after.sort
        )
        publish(current.paging)
    }

    private fun saveSelection(categoryId: Int?, state: StoreOutletState) {
        if (categoryId == null) return
        retainedSelections[categoryId] = Selection(
            bubbleKey = state.selectedBubbleKey,
            sizes = state.selectedSizes,
            colors = state.selectedColors,
            brands = state.selectedBrands,
            sort = state.selectedSort
        )
    }

    private fun visibleBubbles(categoryId: Int?): List<OutletBubbleDefinition> {
        val activeCategoryId = categoryId ?: return emptyList()
        val bubbles = fixedOutletBubbles(activeCategoryId)
        val snapshot = availability
        if (snapshot?.schemaVersion != 1) return bubbles
        return bubbles.filter { bubble ->
            bubble.categoryIds.any { categoryIdForBubble ->
                snapshot.countFor(activeCategoryId, categoryIdForBubble) > 0
            }
        }
    }

    private fun publish(paging: CatalogPagingState<StoreProduct>) {
        val old = _state.value
        val categoryId = old.activeCategoryId
        val visible = visibleBubbles(categoryId)
        val selectedKey = old.selectedBubbleKey?.takeIf { key -> visible.any { it.key == key } }
        val selectedBubble = visible.firstOrNull { it.key == selectedKey }
        val matched = if (selectedBubble == null) paging.items else paging.items.filter { product ->
            productBelongsToOutletBubble(product, selectedBubble, categoriesById)
        }
        fun values(product: StoreProduct, attribute: String): Set<String> =
            product.attributes.filter { it.name.equals(attribute, ignoreCase = true) }
                .flatMap { it.terms.map { term -> term.name } }.filter { it.isNotBlank() }.toSet()
        fun matches(product: StoreProduct, sizes: Set<String>, colors: Set<String>, brands: Set<String>): Boolean {
            val productSizes = values(product, "Tallas").map(::outletCatalogFilterKey).toSet()
            val productColors = values(product, "Color").map(::outletCatalogFilterKey).toSet()
            val productBrands = product.tags.map { it.name }.filter { it.isNotBlank() }.map(::outletCatalogFilterKey).toSet()
            val wantedSizes = sizes.map(::outletCatalogFilterKey).toSet()
            val wantedColors = colors.map(::outletCatalogFilterKey).toSet()
            val wantedBrands = brands.map(::outletCatalogFilterKey).toSet()
            return (wantedSizes.isEmpty() || productSizes.any { it in wantedSizes }) &&
                (wantedColors.isEmpty() || productColors.any { it in wantedColors }) &&
                (wantedBrands.isEmpty() || productBrands.any { it in wantedBrands })
        }
        val sizes = matched.flatMap { values(it, "Tallas") }.distinct().sorted()
        val colors = matched.flatMap { values(it, "Color") }.distinct().sorted()
        val brands = matched.flatMap { it.tags.map { tag -> tag.name } }.filter { it.isNotBlank() }.distinct().sorted()
        val selectedSizes = old.selectedSizes
        val selectedColors = old.selectedColors
        val selectedBrands = old.selectedBrands
        val enabledSizes = matched.filter { matches(it, emptySet(), selectedColors, selectedBrands) }.flatMap { values(it, "Tallas") }.toSet()
        val enabledColors = matched.filter { matches(it, selectedSizes, emptySet(), selectedBrands) }.flatMap { values(it, "Color") }.toSet()
        val enabledBrands = matched.filter { matches(it, selectedSizes, selectedColors, emptySet()) }.flatMap { it.tags.map { tag -> tag.name } }.toSet()
        val filtered = matched.filter { matches(it, selectedSizes, selectedColors, selectedBrands) }
        val sorted = when (old.selectedSort) {
            StoreOutletSort.RECENT -> filtered
            StoreOutletSort.PRICE_ASC -> filtered.sortedBy { it.prices.price.toLongOrNull() ?: Long.MAX_VALUE }
            StoreOutletSort.PRICE_DESC -> filtered.sortedByDescending { it.prices.price.toLongOrNull() ?: Long.MIN_VALUE }
            StoreOutletSort.NAME_ASC -> filtered.sortedBy { it.name.lowercase() }
        }
        val next = old.copy(
            selectedBubbleKey = selectedKey,
            visibleBubbles = visible,
            paging = paging,
            matchedProducts = matched,
            filteredProducts = filtered,
            sortedProducts = sorted,
            sizes = sizes,
            colors = colors,
            brands = brands,
            enabledSizes = enabledSizes,
            enabledColors = enabledColors,
            enabledBrands = enabledBrands
        )
        _state.value = next
        saveSelection(categoryId, next)
        if (next.isBubbleProbeRequired) {
            val key = next.queryKey
            val token = "${key?.categoryId}:${next.selectedBubbleKey}:${paging.currentPage}"
            if (token != lastProbeToken) {
                lastProbeToken = token
                requestNext(retry = false)
            }
        }
    }
}
