package es.criosrango.shared.api
import es.criosrango.shared.createStoreHttpClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
@Serializable data class StoreFilterFacetItem(@SerialName("term_id") val termId: Int, val count: Int)
@Serializable data class StoreFilterQueryResponse(
 @SerialName("schema_version") val schemaVersion: Int = 1, val page: Int, @SerialName("per_page") val perPage: Int,
 val total: Int, @SerialName("has_more") val hasMore: Boolean, @SerialName("product_ids") val productIds: List<Int>,
 val facets: Map<String, List<StoreFilterFacetItem>> = emptyMap()
)
/** Shared KMP owner for the variation-aware WordPress filter endpoint. */
class StoreFilterApiClient(private val endpoint: String = "https://criosrango.es/wp-json/criosrango-filter/v1/query", private val client: HttpClient = createStoreHttpClient()) {
 suspend fun query(scope: String, outletCategoryId: Int? = null, originCategoryIds: List<Int> = emptyList(), sizeIds: List<Int> = emptyList(), colorIds: List<Int> = emptyList(), brandIds: List<Int> = emptyList(), page: Int = 1, perPage: Int = 12, sort: String = "newest", facet: String? = null): StoreFilterQueryResponse {
  require(scope == "catalog" || scope == "outlet"); require(page >= 1 && perPage in 1..100)
  require(sort in setOf("newest", "oldest", "price_asc", "price_desc", "title_asc", "title_desc", "default"))
  if (facet != null) require(facet in setOf("sizes", "colors", "brands"))
  val response: StoreFilterQueryResponse = client.get(endpoint) {
   parameter("scope", scope)
   outletCategoryId?.let { parameter("outlet_category_id", it) }
   if (originCategoryIds.isNotEmpty()) parameter("origin_category_ids", originCategoryIds.distinct().sorted().joinToString(","))
   if (sizeIds.isNotEmpty()) parameter("size_ids", sizeIds.distinct().sorted().joinToString(","))
   if (colorIds.isNotEmpty()) parameter("color_ids", colorIds.distinct().sorted().joinToString(","))
   if (brandIds.isNotEmpty()) parameter("brand_ids", brandIds.distinct().sorted().joinToString(","))
   parameter("page", page); parameter("per_page", perPage); parameter("sort", sort); facet?.let { parameter("facet", it) }
  }.body()
  if (response.schemaVersion != 1) throw IllegalStateException("Unsupported Filter API schema version: ${response.schemaVersion}")
  if (response.page != page || response.perPage != perPage) throw IllegalStateException("Filter API pagination response does not match the request.")
  return response
 }
 fun close() = client.close()
}
