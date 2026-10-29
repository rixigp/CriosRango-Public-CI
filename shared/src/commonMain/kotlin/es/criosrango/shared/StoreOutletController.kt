package es.criosrango.shared

import es.criosrango.shared.model.OutletAvailability
import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreProduct
import es.criosrango.shared.model.countFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

enum class StoreOutletSort { RECENT, PRICE_ASC, PRICE_DESC, NAME_ASC }

enum class StoreOutletFacetDimension(val apiValue: String) {
    SIZES("sizes"), COLORS("colors"), BRANDS("brands");

    companion object {
        fun fromApiValue(value: String): StoreOutletFacetDimension =
            values().firstOrNull { it.apiValue == value }
                ?: throw IllegalArgumentException("Unknown Outlet facet dimension: $value")
    }
}

/** Canonical logical Outlet query. Page is deliberately not part of query identity. */
data class StoreOutletQuery(
    val categoryId: Int,
    val originCategoryIds: List<Int> = emptyList(),
    val sizeIds: List<Int> = emptyList(),
    val colorIds: List<Int> = emptyList(),
    val brandIds: List<Int> = emptyList(),
    val sort: StoreOutletSort = StoreOutletSort.RECENT
) {
    fun canonical(): StoreOutletQuery = copy(
        originCategoryIds = originCategoryIds.distinct().sorted(),
        sizeIds = sizeIds.distinct().sorted(),
        colorIds = colorIds.distinct().sorted(),
        brandIds = brandIds.distinct().sorted()
    )
}

data class StoreOutletQueryKey(
    val categoryId: Int,
    val originCategoryIds: List<Int> = emptyList(),
    val sizeIds: List<Int> = emptyList(),
    val colorIds: List<Int> = emptyList(),
    val brandIds: List<Int> = emptyList(),
    val sort: StoreOutletSort = StoreOutletSort.RECENT
) {
    fun paginatorKey(): String = buildString {
        append("outlet:").append(categoryId)
        append(":origin=").append(originCategoryIds.distinct().sorted().joinToString(","))
        append(":sizes=").append(sizeIds.distinct().sorted().joinToString(","))
        append(":colors=").append(colorIds.distinct().sorted().joinToString(","))
        append(":brands=").append(brandIds.distinct().sorted().joinToString(","))
        append(":sort=").append(sort.name)
    }

    companion object {
        fun from(query: StoreOutletQuery): StoreOutletQueryKey {
            val q = query.canonical()
            return StoreOutletQueryKey(q.categoryId, q.originCategoryIds, q.sizeIds, q.colorIds, q.brandIds, q.sort)
        }
    }
}

/**
 * Transport boundary for Outlet. The controller owns business/query state; this source only executes
 * metadata, Filter API/facet and hydration operations chosen by the controller.
 */
fun interface StoreOutletDataSource {
    suspend fun loadPage(query: StoreOutletQuery, page: Int, perPage: Int): CatalogPage<StoreProduct>
    suspend fun loadFilterOptions(): StoreFilterOptions = StoreFilterOptions(emptyList(), emptyList(), emptyList())
    suspend fun loadFacet(query: StoreOutletQuery, dimension: StoreOutletFacetDimension): Map<Int, Int> = emptyMap()
}

data class StoreOutletState(
    val activeCategoryId: Int? = null,
    val screenTitle: String = "Outlet",
    val query: StoreOutletQuery? = null,
    val queryKey: StoreOutletQueryKey? = null,
    val selectedBubbleKey: String? = null,
    val selectedSizes: Set<String> = emptySet(),
    val selectedColors: Set<String> = emptySet(),
    val selectedBrands: Set<String> = emptySet(),
    val draftSizes: Set<String> = emptySet(),
    val draftColors: Set<String> = emptySet(),
    val draftBrands: Set<String> = emptySet(),
    val selectedSort: StoreOutletSort = StoreOutletSort.RECENT,
    val filterDialogVisible: Boolean = false,
    val filterOptionsState: StoreFilterOptionsState = StoreFilterOptionsState.Idle,
    val facetCounts: Map<String, Map<Int, Int>> = emptyMap(),
    val loadedFacetKeys: Map<String, String> = emptyMap(),
    val visibleBubbles: List<OutletBubbleDefinition> = emptyList(),
    val paging: CatalogPagingState<StoreProduct> = CatalogPagingState(),
    // Kept as presentation-compatible aliases. They are no longer independently filtered/sorted.
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
    val isBubbleProbeRequired: Boolean get() = false
    val isProbingSelectedBubble: Boolean get() = false
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

/** Single common business/state owner for every active Outlet path on Android and iOS. */
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

    private var cachedOptions: StoreFilterOptions? = null
    private var optionsLoading = false
    private var activeFacetDimension = StoreOutletFacetDimension.SIZES
    private val facetGenerations = mutableMapOf<StoreOutletFacetDimension, Int>()
    private val facetCache = mutableMapOf<String, Map<Int, Int>>()
    private val inFlightFacetKeys = mutableSetOf<String>()

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
            activateCurrentQuery()
        } else {
            publish(current.paging)
        }
    }

    fun selectCategory(categoryId: Int) {
        val current = _state.value
        if (current.activeCategoryId == categoryId) {
            if (current.query == null) activateCurrentQuery()
            else {
                val existing = current.queryKey?.let { paginators[it]?.state?.value }
                if (existing != null && existing.currentPage == 0 && !existing.isInitialLoading) startInitial(current.query!!, current.queryKey!!)
                else if (existing != null) publish(existing)
            }
            return
        }

        saveSelection(current.activeCategoryId, current)
        val selection = retainedSelections[categoryId] ?: Selection()
        val optionsState = cachedOptions?.let(StoreFilterOptionsState::Ready) ?: current.filterOptionsState
        _state.value = StoreOutletState(
            activeCategoryId = categoryId,
            selectedBubbleKey = selection.bubbleKey,
            selectedSizes = selection.sizes,
            selectedColors = selection.colors,
            selectedBrands = selection.brands,
            draftSizes = selection.sizes,
            draftColors = selection.colors,
            draftBrands = selection.brands,
            selectedSort = selection.sort,
            filterOptionsState = optionsState
        )
        activateCurrentQuery()
    }

    fun selectBubble(key: String?) {
        val current = _state.value
        if (key != null && current.visibleBubbles.none { it.key == key }) return
        if (key == current.selectedBubbleKey) return
        _state.value = current.copy(selectedBubbleKey = key)
        saveSelection(current.activeCategoryId, _state.value)
        invalidateFacets(StoreOutletFacetDimension.SIZES, StoreOutletFacetDimension.COLORS, StoreOutletFacetDimension.BRANDS)
        activateCurrentQuery()
    }

    /** Immediate applied-selection methods used by active chips and compatibility call sites. */
    fun selectSizes(values: Set<String>) = updateAppliedSelection { it.copy(sizes = values) }
    fun selectColors(values: Set<String>) = updateAppliedSelection { it.copy(colors = values) }
    fun selectBrands(values: Set<String>) = updateAppliedSelection { it.copy(brands = values) }

    fun selectSort(sort: StoreOutletSort) {
        val current = _state.value
        if (sort == current.selectedSort) return
        _state.value = current.copy(selectedSort = sort)
        saveSelection(current.activeCategoryId, _state.value)
        activateCurrentQuery()
    }

    fun openFilters() {
        val current = _state.value
        _state.value = current.copy(
            filterDialogVisible = true,
            draftSizes = current.selectedSizes,
            draftColors = current.selectedColors,
            draftBrands = current.selectedBrands
        )
        ensureFilterOptions()
        requestFacet(activeFacetDimension)
    }

    fun retryFilterOptions() = ensureFilterOptions(force = true)

    fun selectFacetDimension(dimension: String) {
        activeFacetDimension = StoreOutletFacetDimension.fromApiValue(dimension)
        requestFacet(activeFacetDimension)
    }

    fun changeDraftSizes(values: Set<String>) {
        _state.value = _state.value.copy(draftSizes = values)
        invalidateFacets(StoreOutletFacetDimension.COLORS, StoreOutletFacetDimension.BRANDS)
    }

    fun changeDraftColors(values: Set<String>) {
        _state.value = _state.value.copy(draftColors = values)
        invalidateFacets(StoreOutletFacetDimension.SIZES, StoreOutletFacetDimension.BRANDS)
    }

    fun changeDraftBrands(values: Set<String>) {
        _state.value = _state.value.copy(draftBrands = values)
        invalidateFacets(StoreOutletFacetDimension.SIZES, StoreOutletFacetDimension.COLORS)
    }

    fun clearDraftFilters() {
        _state.value = _state.value.copy(draftSizes = emptySet(), draftColors = emptySet(), draftBrands = emptySet())
        invalidateFacets(StoreOutletFacetDimension.SIZES, StoreOutletFacetDimension.COLORS, StoreOutletFacetDimension.BRANDS)
    }

    fun cancelFilters() {
        val current = _state.value
        _state.value = current.copy(
            filterDialogVisible = false,
            draftSizes = current.selectedSizes,
            draftColors = current.selectedColors,
            draftBrands = current.selectedBrands
        )
    }

    fun applyFilters(): Boolean {
        val current = _state.value
        val changed = current.selectedSizes != current.draftSizes ||
            current.selectedColors != current.draftColors || current.selectedBrands != current.draftBrands
        _state.value = current.copy(
            filterDialogVisible = false,
            selectedSizes = current.draftSizes,
            selectedColors = current.draftColors,
            selectedBrands = current.draftBrands
        )
        saveSelection(current.activeCategoryId, _state.value)
        if (changed) activateCurrentQuery() else publish(current.paging)
        return changed
    }

    fun clearFilters() {
        val current = _state.value
        if (current.selectedSizes.isEmpty() && current.selectedColors.isEmpty() && current.selectedBrands.isEmpty() &&
            current.draftSizes.isEmpty() && current.draftColors.isEmpty() && current.draftBrands.isEmpty()) return
        _state.value = current.copy(
            selectedSizes = emptySet(), selectedColors = emptySet(), selectedBrands = emptySet(),
            draftSizes = emptySet(), draftColors = emptySet(), draftBrands = emptySet()
        )
        saveSelection(current.activeCategoryId, _state.value)
        invalidateFacets(StoreOutletFacetDimension.SIZES, StoreOutletFacetDimension.COLORS, StoreOutletFacetDimension.BRANDS)
        activateCurrentQuery()
    }

    fun onVisibleRangeChanged(lastVisibleIndex: Int) {
        val current = _state.value
        val paging = current.paging
        if (current.sortedProducts.isNotEmpty() && paging.currentPage > 0 &&
            !paging.isInitialLoading && !paging.isAppending && paging.appendError == null &&
            paging.hasMore && lastVisibleIndex >= current.sortedProducts.size - 1 - CatalogPaginator.PREFETCH_DISTANCE
        ) requestNext(retry = false)
    }

    fun retryInitial() {
        val current = _state.value
        val query = current.query ?: return
        val key = current.queryKey ?: return
        startInitial(query, key)
    }

    fun retryAppend() = requestNext(retry = true)

    fun retainedPaginatorCount(): Int = paginators.size

    private fun updateAppliedSelection(transform: (Selection) -> Selection) {
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
            draftSizes = after.sizes,
            draftColors = after.colors,
            draftBrands = after.brands,
            selectedSort = after.sort
        )
        invalidateFacets(StoreOutletFacetDimension.SIZES, StoreOutletFacetDimension.COLORS, StoreOutletFacetDimension.BRANDS)
        activateCurrentQuery()
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
        if (snapshot?.schemaVersion != 1) return emptyList()
        return bubbles.filter { bubble ->
            bubble.categoryIds.any { categoryIdForBubble -> snapshot.countFor(activeCategoryId, categoryIdForBubble) > 0 }
        }
    }

    private fun appliedQuery(): StoreOutletQuery? {
        val state = _state.value
        val categoryId = state.activeCategoryId ?: return null
        val options = cachedOptions
        fun ids(names: Set<String>, values: List<StoreFilterOption>): List<Int> =
            values.filter { it.name in names }.map { it.id }.distinct().sorted()
        val bubbleOrigins = visibleBubbles(categoryId)
            .firstOrNull { it.key == state.selectedBubbleKey }
            ?.categoryIds.orEmpty().distinct().sorted()
        return StoreOutletQuery(
            categoryId = categoryId,
            originCategoryIds = bubbleOrigins,
            sizeIds = ids(state.selectedSizes, options?.sizes.orEmpty()),
            colorIds = ids(state.selectedColors, options?.colors.orEmpty()),
            brandIds = ids(state.selectedBrands, options?.brands.orEmpty()),
            sort = state.selectedSort
        ).canonical()
    }

    private fun draftQuery(excluding: StoreOutletFacetDimension): StoreOutletQuery? {
        val state = _state.value
        val categoryId = state.activeCategoryId ?: return null
        val options = cachedOptions ?: return null
        fun ids(names: Set<String>, values: List<StoreFilterOption>): List<Int> =
            values.filter { it.name in names }.map { it.id }.distinct().sorted()
        val bubbleOrigins = visibleBubbles(categoryId)
            .firstOrNull { it.key == state.selectedBubbleKey }
            ?.categoryIds.orEmpty().distinct().sorted()
        return StoreOutletQuery(
            categoryId = categoryId,
            originCategoryIds = bubbleOrigins,
            sizeIds = if (excluding == StoreOutletFacetDimension.SIZES) emptyList() else ids(state.draftSizes, options.sizes),
            colorIds = if (excluding == StoreOutletFacetDimension.COLORS) emptyList() else ids(state.draftColors, options.colors),
            brandIds = if (excluding == StoreOutletFacetDimension.BRANDS) emptyList() else ids(state.draftBrands, options.brands),
            sort = StoreOutletSort.RECENT
        ).canonical()
    }

    private fun activateCurrentQuery() {
        val query = appliedQuery() ?: return
        val key = StoreOutletQueryKey.from(query)
        val paginator = paginators.getOrPut(key) { CatalogPaginator(identity = { it.id }) }
        _state.value = _state.value.copy(query = query, queryKey = key)
        activeCollector?.cancel()
        activeCollector = scope.launch {
            paginator.state.collect { paging ->
                if (_state.value.queryKey == key) publish(paging)
            }
        }
        publish(paginator.state.value)
        if (paginator.state.value.currentPage == 0 && !paginator.state.value.isInitialLoading) startInitial(query, key)
    }

    private fun startInitial(query: StoreOutletQuery, key: StoreOutletQueryKey) {
        if (!initialStartPending.add(key)) return
        val paginator = paginators.getOrPut(key) { CatalogPaginator(identity = { it.id }) }
        scope.launch {
            try {
                paginator.start(key.paginatorKey()) { page, perPage -> dataSource.loadPage(query, page, perPage) }
            } finally {
                initialStartPending.remove(key)
            }
        }
    }

    private fun requestNext(retry: Boolean) {
        val current = _state.value
        val query = current.query ?: return
        val key = current.queryKey ?: return
        val paginator = paginators[key] ?: return
        val paging = paginator.state.value
        if (paging.currentPage <= 0 || paging.isInitialLoading || paging.isAppending || !paging.hasMore) return
        if (!retry && paging.appendError != null) return
        scope.launch { paginator.loadNext { page, perPage -> dataSource.loadPage(query, page, perPage) } }
    }

    private fun ensureFilterOptions(force: Boolean = false) {
        cachedOptions?.takeIf { !force }?.let {
            _state.value = _state.value.copy(filterOptionsState = StoreFilterOptionsState.Ready(it))
            publish(_state.value.paging)
            if (_state.value.filterDialogVisible) requestFacet(activeFacetDimension)
            return
        }
        if (optionsLoading) return
        optionsLoading = true
        _state.value = _state.value.copy(filterOptionsState = StoreFilterOptionsState.Loading)
        scope.launch {
            try {
                val options = dataSource.loadFilterOptions()
                cachedOptions = options
                _state.value = _state.value.copy(filterOptionsState = StoreFilterOptionsState.Ready(options))
                publish(_state.value.paging)
                if (_state.value.filterDialogVisible) requestFacet(activeFacetDimension)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    filterOptionsState = StoreFilterOptionsState.Error(e.message ?: "No se han podido cargar los filtros.")
                )
            } finally {
                optionsLoading = false
            }
        }
    }

    private fun facetKey(dimension: StoreOutletFacetDimension, query: StoreOutletQuery): String =
        listOf(
            dimension.apiValue,
            query.categoryId.toString(),
            query.originCategoryIds.joinToString(","),
            query.sizeIds.joinToString(","),
            query.colorIds.joinToString(","),
            query.brandIds.joinToString(",")
        ).joinToString("|")

    private fun requestFacet(dimension: StoreOutletFacetDimension) {
        val query = draftQuery(dimension) ?: return
        val key = facetKey(dimension, query)
        facetCache[key]?.let { counts ->
            val current = _state.value
            _state.value = current.copy(
                facetCounts = current.facetCounts + (dimension.apiValue to counts),
                loadedFacetKeys = current.loadedFacetKeys + (dimension.apiValue to key)
            )
            publish(_state.value.paging)
            return
        }
        if (!inFlightFacetKeys.add(key)) return
        val generation = (facetGenerations[dimension] ?: 0) + 1
        facetGenerations[dimension] = generation
        scope.launch {
            try {
                val counts = dataSource.loadFacet(query, dimension)
                val current = _state.value
                val currentQuery = draftQuery(dimension)
                if (facetGenerations[dimension] == generation && currentQuery != null && facetKey(dimension, currentQuery) == key) {
                    facetCache[key] = counts
                    _state.value = current.copy(
                        facetCounts = current.facetCounts + (dimension.apiValue to counts),
                        loadedFacetKeys = current.loadedFacetKeys + (dimension.apiValue to key)
                    )
                    publish(_state.value.paging)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keep the dimension retryable; never derive counts from the currently loaded page.
            } finally {
                inFlightFacetKeys.remove(key)
            }
        }
    }

    private fun invalidateFacets(vararg dimensions: StoreOutletFacetDimension) {
        val current = _state.value
        val loaded = current.loadedFacetKeys.toMutableMap()
        dimensions.forEach { dimension ->
            facetGenerations[dimension] = (facetGenerations[dimension] ?: 0) + 1
            loaded.remove(dimension.apiValue)
        }
        _state.value = current.copy(loadedFacetKeys = loaded)
        if (activeFacetDimension in dimensions && _state.value.filterDialogVisible) requestFacet(activeFacetDimension)
    }

    private fun publish(paging: CatalogPagingState<StoreProduct>) {
        val old = _state.value
        val categoryId = old.activeCategoryId
        val visible = visibleBubbles(categoryId)
        val selectedKey = old.selectedBubbleKey?.takeIf { key -> visible.any { it.key == key } }
        val options = cachedOptions
        val sizes = options?.sizes.orEmpty().map { it.name }
        val colors = options?.colors.orEmpty().map { it.name }
        val brands = options?.brands.orEmpty().map { it.name }

        fun enabled(dimension: StoreOutletFacetDimension, values: List<StoreFilterOption>, selected: Set<String>): Set<String> {
            val key = old.loadedFacetKeys[dimension.apiValue]
            if (key == null) return values.map { it.name }.toSet()
            val counts = old.facetCounts[dimension.apiValue].orEmpty()
            return values.filter { (counts[it.id] ?: 0) > 0 || it.name in selected }.map { it.name }.toSet()
        }

        val displayTitle = categoryId?.let { categoriesById[it] }?.let(::storeCategoryDisplayName) ?: "Outlet"
        val items = paging.items
        val next = old.copy(
            screenTitle = displayTitle,
            selectedBubbleKey = selectedKey,
            visibleBubbles = visible,
            paging = paging,
            matchedProducts = items,
            filteredProducts = items,
            sortedProducts = items,
            sizes = sizes,
            colors = colors,
            brands = brands,
            enabledSizes = enabled(StoreOutletFacetDimension.SIZES, options?.sizes.orEmpty(), old.draftSizes),
            enabledColors = enabled(StoreOutletFacetDimension.COLORS, options?.colors.orEmpty(), old.draftColors),
            enabledBrands = enabled(StoreOutletFacetDimension.BRANDS, options?.brands.orEmpty(), old.draftBrands)
        )
        _state.value = next
        saveSelection(categoryId, next)
    }
}
