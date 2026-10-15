package es.criosrango.shared

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import es.criosrango.shared.model.StoreProduct
import es.criosrango.shared.model.StoreProductAttributeFilter

const val ROOT_FILTER_DIAG_BASE_SHA = "b9dc0a8bb3776634c81f73152ddc0e1b43a1a251"
const val ROOT_FILTER_DIAG_BUILD_ID = "root-filter-physical-diag-20261015-a1"

data class RootFilterDiagnosticState(
    val scope: String = "—",
    val queryKey: String = "—",
    val sizeNames: String = "—",
    val sizeIds: String = "—",
    val colorNames: String = "—",
    val colorIds: String = "—",
    val brandNames: String = "—",
    val brandIds: String = "—",
    val page: Int? = null,
    val perPage: Int? = null,
    val category: Int? = null,
    val attributeFilters: String = "—",
    val tagFilters: String = "—",
    val requestUrl: String = "—",
    val requestHost: String = "—",
    val httpStatus: String = "—",
    val apiCount: Int? = null,
    val apiIds: String = "—",
    val apiError: String = "none",
    val decodeError: String = "none",
    val paginatorCount: Int? = null,
    val paginatorIds: String = "—",
    val hasMore: Boolean? = null,
    val initialLoading: Boolean? = null,
    val paginatorError: String = "none",
    val displayCount: Int? = null,
    val displayIds: String = "—",
    val empty: Boolean? = null
)

object RootFilterDiagnosticTelemetry {
    private val mutable = MutableStateFlow(RootFilterDiagnosticState())
    val state = mutable.asStateFlow()

    fun ownerRequest(
        scope: String,
        queryKey: String,
        sizeNames: Set<String>,
        sizeOptions: List<StoreFilterOption>,
        colorNames: Set<String>,
        colorOptions: List<StoreFilterOption>,
        brandNames: Set<String>,
        brandOptions: List<StoreFilterOption>,
        page: Int,
        perPage: Int,
        category: Int?,
        attributes: List<StoreProductAttributeFilter>,
        tagIds: List<Int>
    ) {
        mutable.value = mutable.value.copy(
            scope = scope,
            queryKey = queryKey,
            sizeNames = sizeNames.sorted().joinToString().ifBlank { "—" },
            sizeIds = sizeOptions.filter { it.name in sizeNames }.map { it.id }.distinct().sorted().joinToString().ifBlank { "—" },
            colorNames = colorNames.sorted().joinToString().ifBlank { "—" },
            colorIds = colorOptions.filter { it.name in colorNames }.map { it.id }.distinct().sorted().joinToString().ifBlank { "—" },
            brandNames = brandNames.sorted().joinToString().ifBlank { "—" },
            brandIds = brandOptions.filter { it.name in brandNames }.map { it.id }.distinct().sorted().joinToString().ifBlank { "—" },
            page = page,
            perPage = perPage,
            category = category,
            attributeFilters = attributes.joinToString(" ; ") { "${it.taxonomy}=${it.termIds.joinToString(",")}" }.ifBlank { "—" },
            tagFilters = tagIds.joinToString().ifBlank { "—" },
            httpStatus = "pending",
            apiCount = null,
            apiIds = "—",
            apiError = "none",
            decodeError = "none",
            paginatorError = "none"
        )
    }

    fun apiRequestStarted(url: String) {
        val host = runCatching { url.substringAfter("://").substringBefore("/").substringBefore("?") }.getOrDefault("unavailable")
        mutable.value = mutable.value.copy(requestUrl = url, requestHost = host, httpStatus = "pending")
    }

    fun apiResponse(url: String, status: Int) {
        val host = runCatching { url.substringAfter("://").substringBefore("/").substringBefore("?") }.getOrDefault("unavailable")
        mutable.value = mutable.value.copy(requestUrl = url, requestHost = host, httpStatus = status.toString())
    }

    fun apiDecoded(products: List<StoreProduct>) {
        mutable.value = mutable.value.copy(apiCount = products.size, apiIds = products.joinToString(",") { it.id.toString() }, apiError = "none", decodeError = "none")
    }

    fun apiFailed(stage: String, error: Throwable) {
        val message = "${error::class.simpleName}: ${error.message ?: "no message"}".take(180)
        mutable.value = when (stage) {
            "decode" -> mutable.value.copy(decodeError = message)
            else -> mutable.value.copy(apiError = message)
        }
    }

    fun paginatorPublished(count: Int, ids: String, hasMore: Boolean, initialLoading: Boolean, error: String) {
        mutable.value = mutable.value.copy(
            paginatorCount = count, paginatorIds = ids.ifBlank { "—" },
            hasMore = hasMore, initialLoading = initialLoading,
            paginatorError = error.ifBlank { "none" }
        )
    }

    fun display(count: Int, ids: String, empty: Boolean) {
        mutable.value = mutable.value.copy(displayCount = count, displayIds = ids.ifBlank { "—" }, empty = empty)
    }
}
