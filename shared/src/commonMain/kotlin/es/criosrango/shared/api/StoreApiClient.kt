package es.criosrango.shared.api

import io.ktor.http.Headers
import es.criosrango.shared.createStoreHttpClient
import es.criosrango.shared.model.StoreCart
import es.criosrango.shared.model.StoreCartApiError
import es.criosrango.shared.model.StoreCartRequest
import es.criosrango.shared.model.CustomerAddress
import es.criosrango.shared.model.UpdateCustomerRequest
import es.criosrango.shared.model.SelectShippingRateRequest
import es.criosrango.shared.model.CheckoutResponse
import es.criosrango.shared.model.CreateOrderRequest
import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreProduct
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.parameter
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json

interface StoreSessionStore {
    var cartToken: String?
    var nonce: String?
    var cookieHeader: String?

    fun updateFromResponse(headers: Headers) {
        headers["Cart-Token"]?.takeIf { it.isNotBlank() }?.let { cartToken = it }
        headers["Nonce"]?.takeIf { it.isNotBlank() }?.let { nonce = it }
        headers.getAll("Set-Cookie").orEmpty().forEach { raw ->
            val pair = raw.substringBefore(";").trim()
            val name = pair.substringBefore("=", "")
            if (name.isNotBlank() && pair.contains("=")) {
                val current = cookieHeader.orEmpty()
                    .split(";")
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .associate { it.substringBefore("=") to it }
                    .toMutableMap()
                current[name] = pair
                cookieHeader = current.values.joinToString("; ")
            }
        }
    }
}

class InMemoryStoreSessionStore(
    override var cartToken: String? = null,
    override var nonce: String? = null,
    override var cookieHeader: String? = null
) : StoreSessionStore

class StoreApiException(
    val statusCode: Int,
    val apiCode: String?,
    override val message: String
) : Exception(message)

class StoreApiClient(
    private val baseUrl: String = "https://criosrango.es/wp-json/wc/store/v1/",
    private val client: HttpClient = createStoreHttpClient(),
    private val session: StoreSessionStore = InMemoryStoreSessionStore()
) {
    private suspend inline fun <reified T> executeCart(
        request: suspend () -> io.ktor.client.statement.HttpResponse
    ): T {
        val response = request()
        session.updateFromResponse(response.headers)
        val raw = response.bodyAsText()
        if (!response.status.isSuccess()) {
            val error = runCatching {
                Json { ignoreUnknownKeys = true }.decodeFromString<es.criosrango.shared.model.StoreCartApiError>(raw)
            }.getOrNull()
            throw StoreApiException(
                response.status.value,
                error?.code,
                error?.message?.takeIf { it.isNotBlank() } ?: raw.ifBlank { response.status.description }
            )
        }
        return Json { ignoreUnknownKeys = true }.decodeFromString(raw)
    }

    private fun io.ktor.client.request.HttpRequestBuilder.sessionHeaders() {
        session.cartToken?.let { headers.append("Cart-Token", it) }
        session.nonce?.let { headers.append("Nonce", it) }
        session.cookieHeader?.let { headers.append("Cookie", it) }
    }

    suspend fun cart(): es.criosrango.shared.model.StoreCart =
        executeCart { client.get(baseUrl + "cart") { sessionHeaders() } }

    suspend fun addCartItem(request: es.criosrango.shared.model.StoreCartRequest): es.criosrango.shared.model.StoreCart =
        executeCart {
            client.post(baseUrl + "cart/add-item") {
                sessionHeaders()
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody(request)
            }
        }

    suspend fun updateCartItem(key: String, quantity: Int): es.criosrango.shared.model.StoreCart =
        executeCart {
            client.post(baseUrl + "cart/update-item") {
                sessionHeaders()
                url { parameter("key", key); parameter("quantity", quantity) }
            }
        }

    suspend fun removeCartItem(key: String): es.criosrango.shared.model.StoreCart =
        executeCart {
            client.post(baseUrl + "cart/remove-item") {
                sessionHeaders()
                url { parameter("key", key) }
            }
        }


    suspend fun checkout(): CheckoutResponse =
        executeCart { client.get(baseUrl + "checkout") { sessionHeaders() } }

    suspend fun updateCustomer(request: UpdateCustomerRequest): StoreCart =
        executeCart {
            client.post(baseUrl + "cart/update-customer") {
                sessionHeaders()
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    suspend fun selectShippingRate(request: SelectShippingRateRequest): StoreCart =
        executeCart {
            client.post(baseUrl + "cart/select-shipping-rate") {
                sessionHeaders()
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    suspend fun createCheckout(request: CreateOrderRequest): CheckoutResponse =
        executeCart {
            client.post(baseUrl + "checkout") {
                sessionHeaders()
                header("X-CriosRango-App", "1")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    suspend fun paymentStatus(orderId: Int, orderKey: String): es.criosrango.shared.model.PaymentStatusResponse =
        executeCart {
            client.get("https://criosrango.es/wp-json/criosrango/v1/payment-status") {
                sessionHeaders()
                parameter("order_id", orderId)
                parameter("key", orderKey)
            }
        }

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

    internal suspend fun productsWithRawJson(perPage: Int = 12, page: Int = 1, category: Int? = null): Pair<String, List<StoreProduct>> {
        val response = client.get("${baseUrl}products") {
            parameter("per_page", perPage); parameter("page", page)
            category?.let { parameter("category", it) }
        }
        val rawJson = response.bodyAsText()
        val models = Json { ignoreUnknownKeys = true }.decodeFromString<List<StoreProduct>>(rawJson)
        return rawJson to models
    }

    internal suspend fun categoriesWithRawJson(perPage: Int = 100): Pair<String, List<StoreCategory>> {
        val response = client.get("${baseUrl}products/categories") { parameter("per_page", perPage) }
        val rawJson = response.bodyAsText()
        val models = Json { ignoreUnknownKeys = true }.decodeFromString<List<StoreCategory>>(rawJson)
        return rawJson to models
    }

    fun close() = client.close()
}
