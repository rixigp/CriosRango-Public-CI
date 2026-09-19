package es.criosrango.shared.api
import es.criosrango.shared.createStoreHttpClient
import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreProduct
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
class StoreApiClient(
    private val baseUrl: String = "https://criosrango.es/wp-json/wc/store/v1/",
    private val client: HttpClient = createStoreHttpClient()
) {
    suspend fun product(id: Int): StoreProduct =
        client.get(baseUrl + "products/" + id).body()

    suspend fun productWithVariationAvailability(id: Int): StoreProduct {
        val product = product(id)
        if (product.variations.isEmpty()) return product
        val details = coroutineScope {
            product.variations.map { variation ->
                async { runCatching { product(variation.id) }.getOrNull() }
            }.awaitAll()
        }
        return product.copy(
            variations = product.variations.mapIndexed { index, variation ->
                val detail = details[index]
                variation.copy(
                    prices = detail?.prices ?: variation.prices,
                    images = detail?.images ?: variation.images,
                    isInStock = detail?.isInStock ?: variation.isInStock,
                    isPurchasable = detail?.isPurchasable ?: variation.isPurchasable,
                    isOnBackorder = detail?.isOnBackorder ?: variation.isOnBackorder,
                    lowStockRemaining = detail?.lowStockRemaining ?: variation.lowStockRemaining,
                    stockStatus = detail?.stockStatus ?: variation.stockStatus,
                    stockQuantity = detail?.stockQuantity ?: variation.stockQuantity,
                    manageStock = detail?.manageStock ?: variation.manageStock,
                    quantityLimits = detail?.quantityLimits ?: variation.quantityLimits,
                    addToCart = detail?.addToCart ?: variation.addToCart
                )
            }
        )
    }

    suspend fun products(
        perPage: Int = 24,
        page: Int = 1,
        search: String? = null,
        category: Int? = null,
        orderBy: String? = null,
        order: String? = null,
        after: String? = null,
        featured: Boolean? = null,
        tag: String? = null
    ): List<StoreProduct> =
        client.get("${baseUrl}products") {
            parameter("per_page", perPage)
            parameter("page", page)
            search?.let { parameter("search", it) }
            category?.let { parameter("category", it) }
            orderBy?.let { parameter("orderby", it) }
            order?.let { parameter("order", it) }
            after?.let { parameter("after", it) }
            featured?.let { parameter("featured", it) }
            tag?.let { parameter("tag", it) }
        }.body()
    suspend fun categories(perPage: Int = 100): List<StoreCategory> =
        client.get("${baseUrl}products/categories") { parameter("per_page", perPage) }.body()
    /** DEBUG/SMOKE only: raw JSON and models from the same HTTP response. */
    internal suspend fun productsWithRawJson(perPage: Int = 12, page: Int = 1, category: Int? = null): Pair<String, List<StoreProduct>> {
        val response = client.get("${baseUrl}products") {
            parameter("per_page", perPage); parameter("page", page)
            category?.let { parameter("category", it) }
        }
        val rawJson = response.bodyAsText()
        val models = Json { ignoreUnknownKeys = true }.decodeFromString<List<StoreProduct>>(rawJson)
        return rawJson to models
    }
    /** DEBUG/SMOKE only: raw JSON and models from the same HTTP response. */
    internal suspend fun categoriesWithRawJson(perPage: Int = 100): Pair<String, List<StoreCategory>> {
        val response = client.get("${baseUrl}products/categories") { parameter("per_page", perPage) }
        val rawJson = response.bodyAsText()
        val models = Json { ignoreUnknownKeys = true }.decodeFromString<List<StoreCategory>>(rawJson)
        return rawJson to models
    }
    fun close() = client.close()
}