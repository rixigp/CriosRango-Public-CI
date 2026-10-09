package es.criosrango.shared.api

import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import es.criosrango.shared.account.AccountTokenStore
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import es.criosrango.shared.checkoutDiagLog
import es.criosrango.shared.sanitizeCheckoutDiag

interface StoreSessionStore {
    var cartToken: String?
    var nonce: String?
    var cookieHeader: String?

    fun clear() {
        cartToken = null
        nonce = null
        cookieHeader = null
    }

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
    override val message: String,
    val removedCoupons: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    val updatedCart: StoreCart? = null
) : Exception(message)

data class StoreCustomerDiagnostic(
    val billingEmail: String?,
    val billingFirstName: String?,
    val billingLastName: String?,
    val shippingEmail: String?
)

class StoreApiClient(
    private val baseUrl: String = "https://criosrango.es/wp-json/wc/store/v1/",
    private val client: HttpClient = createStoreHttpClient(),
    private val session: StoreSessionStore = InMemoryStoreSessionStore(),
    private val accountTokenStore: AccountTokenStore? = null
) {
    private val json = Json { ignoreUnknownKeys = true }
    private var lastCustomerDiagnostic: StoreCustomerDiagnostic? = null
    private var customerUpdatedBeforeCoupon = false

    fun diagnosticCustomer(): StoreCustomerDiagnostic? = lastCustomerDiagnostic
    fun diagnosticCustomerUpdatedBeforeCoupon(): Boolean = customerUpdatedBeforeCoupon
    private suspend inline fun <reified T> executeCart(
        operation: String? = null,
        method: String? = null,
        path: String? = null,
        request: suspend () -> io.ktor.client.statement.HttpResponse
    ): T {
        val isCheckoutGet = operation == "checkout" && method == "GET" && path == "checkout"
        val isCheckoutPost = operation == "checkout-post" && method == "POST" && path == "checkout"
        if (isCheckoutGet) checkoutDiagLog("HTTP_CHECKOUT START method=GET path=/checkout")
        val response = try {
            request()
        } catch (exception: Exception) {
            if (isCheckoutGet) {
                checkoutDiagLog(
                    "HTTP_CHECKOUT REQUEST_EXCEPTION type=${exception::class.qualifiedName} " +
                        "message=${sanitizeCheckoutDiag(exception.message.orEmpty(), 500)}"
                )
            }
            if (isCheckoutPost) {
                checkoutDiagLog(
                    "HTTP_CHECKOUT_POST REQUEST_EXCEPTION type=${exception::class.qualifiedName} " +
                        "message=${sanitizeCheckoutDiag(exception.message.orEmpty(), 600)}"
                )
            }
            throw exception
        }
        session.updateFromResponse(response.headers)
        val raw = response.bodyAsText()
        if (isCheckoutGet) {
            checkoutDiagLog(
                "HTTP_CHECKOUT RESPONSE status=${response.status.value} " +
                    "contentType=${sanitizeCheckoutDiag(response.headers[HttpHeaders.ContentType].orEmpty(), 120)} " +
                    "body=${sanitizeCheckoutDiag(raw)}"
            )
        }
        if (isCheckoutPost) {
            checkoutDiagLog(
                "HTTP_CHECKOUT_POST RESPONSE status=${response.status.value} " +
                    "contentType=${sanitizeCheckoutDiag(response.headers[HttpHeaders.ContentType].orEmpty(), 120)} " +
                    "body=${sanitizeCheckoutDiag(raw)}"
            )
        }
        // Parse the top-level error independently from its optional cart payload.
        // A malformed/variant data.cart must never erase the coupon error code itself.
        val errorEnvelope = if (!response.status.isSuccess()) {
            runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
        } else null
        val errorCode = errorEnvelope?.get("code")
            ?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
        val errorMessage = errorEnvelope?.get("message")
            ?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }
        val errorData = errorEnvelope?.get("data")
            ?.let { runCatching { it.jsonObject }.getOrNull() }
        val removedCoupons = errorData?.get("removed_coupons")
            ?.let { element -> runCatching { json.decodeFromJsonElement<Map<String, JsonElement>>(element) }.getOrNull() }
            .orEmpty()
        val updatedCart = errorData?.get("cart")?.let { element ->
            runCatching { json.decodeFromJsonElement<es.criosrango.shared.model.StoreCart>(element) }
                .onFailure { exception ->
                    checkoutDiagLog(
                        "HTTP ERROR CART_PARSE_ERROR operation=$operation status=${response.status.value} " +
                            "type=${exception::class.qualifiedName} message=${sanitizeCheckoutDiag(exception.message.orEmpty(), 300)}"
                    )
                }
                .getOrNull()
        }
        if (operation != null && !response.status.isSuccess()) {
            checkoutDiagLog(
                "HTTP ERROR operation=$operation method=$method path=$path status=${response.status.value} " +
                    "backendCode=${errorCode?.let { sanitizeCheckoutDiag(it) } ?: "null"} " +
                    "backendMessage=${sanitizeCheckoutDiag(errorMessage.orEmpty())} " +
                    "cartPresent=${updatedCart != null} removedCouponCount=${removedCoupons.size} " +
                    "body=${sanitizeCheckoutDiag(raw)}"
            )
        }
        if (!response.status.isSuccess()) {
            throw StoreApiException(
                statusCode = response.status.value,
                apiCode = errorCode,
                message = errorMessage?.takeIf { it.isNotBlank() } ?: raw.ifBlank { response.status.description },
                removedCoupons = removedCoupons,
                updatedCart = updatedCart
            )
        }
        return try {
            json.decodeFromString<T>(raw).also {
                if (isCheckoutGet) checkoutDiagLog("HTTP_CHECKOUT PARSE_OK")
                if (isCheckoutPost) checkoutDiagLog("HTTP_CHECKOUT_POST PARSE_OK")
            }
        } catch (exception: Exception) {
            if (isCheckoutGet) {
                checkoutDiagLog(
                    "HTTP_CHECKOUT PARSE_ERROR type=${exception::class.qualifiedName} " +
                        "message=${sanitizeCheckoutDiag(exception.message.orEmpty(), 500)}"
                )
            }
            if (isCheckoutPost) {
                checkoutDiagLog(
                    "HTTP_CHECKOUT_POST PARSE_ERROR type=${exception::class.qualifiedName} " +
                        "message=${sanitizeCheckoutDiag(exception.message.orEmpty(), 600)}"
                )
            }
            throw exception
        }
    }

    /** Drops local Store API credentials after best-effort remote cleanup. */
    fun clearSession() = session.clear()

    private fun io.ktor.client.request.HttpRequestBuilder.sessionHeaders() {
        accountTokenStore?.load()?.takeIf { it.isNotBlank() }?.let {
            header(HttpHeaders.Authorization, "Bearer $it")
        }
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

    suspend fun applyCoupon(code: String): StoreCart {
        customerUpdatedBeforeCoupon = lastCustomerDiagnostic != null
        val normalizedCode = code.trim()
        require(normalizedCode.isNotEmpty()) { "El código del cupón no puede estar vacío." }
        return executeCart {
            client.post(baseUrl + "cart/apply-coupon") {
                sessionHeaders()
                url { parameter("code", normalizedCode) }
            }
        }
    }

    suspend fun removeCoupon(code: String): StoreCart {
        val normalizedCode = code.trim()
        require(normalizedCode.isNotEmpty()) { "El código del cupón no puede estar vacío." }
        return executeCart {
            client.post(baseUrl + "cart/remove-coupon") {
                sessionHeaders()
                url { parameter("code", normalizedCode) }
            }
        }
    }


    suspend fun checkout(): CheckoutResponse =
        executeCart(operation = "checkout", method = "GET", path = "checkout") {
            client.get(baseUrl + "checkout") { sessionHeaders() }
        }

    suspend fun updateCustomer(request: UpdateCustomerRequest): StoreCart =
        executeCart(operation = "update-customer", method = "POST", path = "cart/update-customer") {
            lastCustomerDiagnostic = StoreCustomerDiagnostic(
                billingEmail = request.billingAddress.email,
                billingFirstName = request.billingAddress.firstName,
                billingLastName = request.billingAddress.lastName,
                shippingEmail = request.shippingAddress.email
            )
            customerUpdatedBeforeCoupon = false
            client.post(baseUrl + "cart/update-customer") {
                sessionHeaders()
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    suspend fun selectShippingRate(request: SelectShippingRateRequest): StoreCart =
        executeCart(operation = "select-shipping-rate", method = "POST", path = "cart/select-shipping-rate") {
            client.post(baseUrl + "cart/select-shipping-rate") {
                sessionHeaders()
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    suspend fun createCheckout(request: CreateOrderRequest): CheckoutResponse {
        checkoutDiagLog(
            "HTTP_CHECKOUT_POST START path=/checkout payment_method=${sanitizeCheckoutDiag(request.paymentMethod, 80)}"
        )
        return executeCart(operation = "checkout-post", method = "POST", path = "checkout") {
            client.post(baseUrl + "checkout") {
                sessionHeaders()
                header("X-CriosRango-App", "1")
                contentType(ContentType.Application.Json)
                setBody(request)
            }
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

    suspend fun outletAvailability(): es.criosrango.shared.model.OutletAvailability =
        executeCart {
            client.get("https://criosrango.es/wp-json/criosrango/v1/outlet-availability") {
                sessionHeaders()
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
        val models = json.decodeFromString<List<StoreProduct>>(rawJson)
        return rawJson to models
    }

    internal suspend fun categoriesWithRawJson(perPage: Int = 100): Pair<String, List<StoreCategory>> {
        val response = client.get("${baseUrl}products/categories") { parameter("per_page", perPage) }
        val rawJson = response.bodyAsText()
        val models = json.decodeFromString<List<StoreCategory>>(rawJson)
        return rawJson to models
    }

    fun close() = client.close()
}
