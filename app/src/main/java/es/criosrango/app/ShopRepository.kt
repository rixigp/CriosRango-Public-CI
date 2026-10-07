package es.criosrango.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.net.SocketTimeoutException
import android.util.Log
import okhttp3.OkHttpClient
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

interface StoreApi {
    suspend fun products(
        perPage: Int = 24,
        page: Int = 1,
        search: String? = null,
        category: Int? = null,
        orderBy: String? = null,
        order: String? = null,
        after: String? = null,
        featured: Boolean? = null
    ): List<StoreProduct>

    suspend fun productsByTag(
        perPage: Int = 100,
        page: Int = 1,
        tag: String
    ): List<StoreProduct>

    suspend fun brands(
        perPage: Int = 100,
        page: Int = 1
    ): List<BrandTerm> = emptyList()

    suspend fun productsByBrand(
        perPage: Int = 100,
        page: Int = 1,
        brand: String
    ): List<StoreProduct> {
        throw UnsupportedOperationException("product_brand is not supported by this StoreApi implementation")
    }

    suspend fun product(id: Int): StoreProduct

    suspend fun productWithVariationAvailability(id: Int): StoreProduct

    suspend fun outletAvailability(): es.criosrango.shared.model.OutletAvailability {
        throw UnsupportedOperationException("Outlet availability is not supported by this StoreApi implementation")
    }

    suspend fun categories(perPage: Int = 100): List<ProductCategory>
    suspend fun cart(): WooCart
    suspend fun addCartItem(request: AddCartRequest): WooCart
    suspend fun updateCartItem(key: String, quantity: Int): WooCart
    suspend fun removeCartItem(key: String): WooCart
    suspend fun applyCoupon(code: String): WooCart
    suspend fun removeCoupon(code: String): WooCart

    suspend fun checkout(): CheckoutResponse
    suspend fun createCheckout(request: CreateOrderRequest): CheckoutResponse
    suspend fun getOrderStatus(orderId: Int, orderKey: String): OrderStatusResponse
    suspend fun selectShippingRate(request: SelectShippingRateRequest): WooCart
    suspend fun updateCustomer(request: UpdateCustomerRequest): WooCart
}

class StoreSession(private val preferences: android.content.SharedPreferences) {
    private companion object {
        const val CART_TOKEN = "woo_cart_token"
        const val NONCE = "woo_nonce"
        const val COOKIE_HEADER = "woo_cookie_header"
    }

    var cartToken: String?
        get() = preferences.getString(CART_TOKEN, null)
        set(value) { preferences.edit().putString(CART_TOKEN, value).apply() }

    var nonce: String?
        get() = preferences.getString(NONCE, null)
        set(value) { preferences.edit().putString(NONCE, value).apply() }

    var cookieHeader: String?
        get() = preferences.getString(COOKIE_HEADER, null)
        set(value) { preferences.edit().putString(COOKIE_HEADER, value).apply() }

    @Synchronized
    fun update(headers: okhttp3.Headers) {
        headers["Cart-Token"]?.takeIf { it.isNotBlank() }?.let { cartToken = it }
        headers["Nonce"]?.takeIf { it.isNotBlank() }?.let { nonce = it }
        headers.values("Set-Cookie").forEach { raw ->
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

    fun clear() {
        preferences.edit().remove(CART_TOKEN).remove(NONCE).remove(COOKIE_HEADER).apply()
    }
}

/** Persistent delivery data only; payment credentials are never stored. */
class DeliveryAddressStore(private val preferences: android.content.SharedPreferences) {
    private val accountOwnerKey = "delivery_account_owner_id"

    fun load(accountId: Int? = null): CustomerAddress? {
        val ownerId = preferences.getString(accountOwnerKey, null)?.toIntOrNull()
        if (!AccountCartCheckoutPolicy.isAddressVisibleToAccount(ownerId, accountId)) return null
        return CustomerAddress(
            preferences.getString("delivery_first_name", "").orEmpty(), preferences.getString("delivery_last_name", "").orEmpty(),
            preferences.getString("delivery_email", "").orEmpty(), preferences.getString("delivery_phone", "").orEmpty(),
            preferences.getString("delivery_address", "").orEmpty(), preferences.getString("delivery_postcode", "").orEmpty(),
            preferences.getString("delivery_city", "").orEmpty(), preferences.getString("delivery_state", "").orEmpty(),
            preferences.getString("delivery_country", "ES").orEmpty()
        ).takeIf { it.firstName.isNotBlank() || it.lastName.isNotBlank() || it.address1.isNotBlank() }
    }

    fun save(address: CustomerAddress, accountId: Int? = null) = preferences.edit()
        .putString("delivery_first_name", address.firstName).putString("delivery_last_name", address.lastName)
        .putString("delivery_email", address.email).putString("delivery_phone", address.phone)
        .putString("delivery_address", address.address1).putString("delivery_postcode", address.postcode)
        .putString("delivery_city", address.city).putString("delivery_state", address.state)
        .putString("delivery_country", address.country)
        .putString(accountOwnerKey, accountId?.toString())
        .apply()
}

data class DiagnosticNormalClient(
    val client: OkHttpClient,
    val cookieSent: Boolean,
    val cartTokenSent: Boolean,
    val nonceSent: Boolean
)

class StoreRepository(private val api: StoreApi) {
    private val cacheMutex = Mutex()

    private val categoryProductsPageDataSource =
        CategoryProductsPageDataSource(api)

    private val categoryCache = mutableMapOf<Int, List<StoreProduct>>()
    private val categoryInFlight = mutableMapOf<Int, CompletableDeferred<List<StoreProduct>>>()

    private val brandCache = mutableMapOf<String, List<StoreProduct>>()
    private val brandInFlight = mutableMapOf<String, CompletableDeferred<List<StoreProduct>>>()

    private val searchCache = mutableMapOf<String, List<StoreProduct>>()
    private val searchInFlight = mutableMapOf<String, CompletableDeferred<List<StoreProduct>>>()

    private suspend fun <K> cachedProducts(
        key: K,
        cache: MutableMap<K, List<StoreProduct>>,
        inFlight: MutableMap<K, CompletableDeferred<List<StoreProduct>>>,
        loader: suspend () -> List<StoreProduct>
    ): List<StoreProduct> {
        var owner = false

        val deferred = cacheMutex.withLock {
            cache[key]?.let { return it }

            inFlight[key] ?: CompletableDeferred<List<StoreProduct>>().also {
                inFlight[key] = it
                owner = true
            }
        }

        if (!owner) {
            return deferred.await()
        }

        return try {
            val loaded = loader()
            cacheMutex.withLock {
                cache[key] = loaded
                inFlight.remove(key)
            }
            deferred.complete(loaded)
            loaded
        } catch (exception: Exception) {
            cacheMutex.withLock {
                inFlight.remove(key)
            }
            deferred.completeExceptionally(exception)
            throw exception
        }
    }

    suspend fun products(
        search: String? = null,
        category: Int? = null
    ): List<StoreProduct> {
        val cleanSearch = search?.trim()?.lowercase()?.takeIf { it.isNotBlank() }

        return when {
            category != null && cleanSearch == null ->
                cachedProducts(
                    key = category,
                    cache = categoryCache,
                    inFlight = categoryInFlight
                ) {
                    api.products(category = category)
                }

            category == null && cleanSearch != null ->
                cachedProducts(
                    key = cleanSearch,
                    cache = searchCache,
                    inFlight = searchInFlight
                ) {
                    api.products(search = search?.trim())
                }

            else ->
                api.products(
                    search = search,
                    category = category
                )
        }
    }

    suspend fun allBrands(): List<BrandTerm> {
        val accumulated = mutableListOf<BrandTerm>()
        var page = 1

        while (true) {
            val batch = api.brands(perPage = 100, page = page)
            if (batch.isEmpty()) break
            accumulated += batch
            if (batch.size < 100) break
            page++
        }

        return accumulated
            .filter { it.name.isNotBlank() && it.slug.isNotBlank() }
            .distinctBy { it.slug.trim().lowercase(java.util.Locale.ROOT) }
            .map { it.withPackagedLogoFallback() }
            .sortedBy { it.name.lowercase(java.util.Locale.ROOT) }
    }

    suspend fun productsByBrand(brand: BrandTerm): List<StoreProduct> {
        val cacheKey = brand.slug.trim().lowercase()

        return cachedProducts(
            key = cacheKey,
            cache = brandCache,
            inFlight = brandInFlight
        ) {
            val result = mutableListOf<StoreProduct>()
            var page = 1

            while (true) {
                val batch = try {
                    api.productsByBrand(
                        perPage = 100,
                        page = page,
                        brand = brand.slug
                    )
                } catch (exception: Exception) {
                    if (page == 1) throw exception
                    break
                }

                if (batch.isEmpty()) break

                result += batch

                if (batch.size < 100) break
                page++
            }

            result.distinctBy { it.id }
        }
    }

    suspend fun productsByBrandPage(
        brand: BrandTerm,
        page: Int,
        perPage: Int
    ): List<StoreProduct> = api.productsByBrand(
        perPage = perPage,
        page = page,
        brand = brand.slug
    )

    suspend fun productsByCategoryPage(
        categoryId: Int,
        page: Int,
        perPage: Int
    ): List<StoreProduct> =
        categoryProductsPageDataSource.productsByCategoryPage(
            categoryId = categoryId,
            page = page,
            perPage = perPage
        )

    suspend fun allProducts(): List<StoreProduct> {
        val result = mutableListOf<StoreProduct>()
        var page = 1

        while (true) {
            val batch = try {
                api.products(
                    perPage = 100,
                    page = page
                )
            } catch (exception: Exception) {
                if (page == 1) throw exception
                break
            }

            if (batch.isEmpty()) break

            result += batch

            if (batch.size < 100) break
            page++
        }

        return result.distinctBy { it.id }
    }

    suspend fun recentProducts(): List<StoreProduct> {
        val utc = java.util.TimeZone.getTimeZone("UTC")

        val calendar = java.util.Calendar
            .getInstance(utc)
            .apply {
                add(java.util.Calendar.DAY_OF_YEAR, -60)
            }

        val formatter = java.text.SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            java.util.Locale.US
        ).apply {
            timeZone = utc
        }

        val cutoff = formatter.format(calendar.time)

        val homeProductCount = 8

        val recent = api.products(
            perPage = homeProductCount,
            orderBy = "date",
            order = "desc",
            after = cutoff
        )

        if (recent.size >= homeProductCount) {
            return recent.take(homeProductCount)
        }

        val latest = api.products(
            perPage = homeProductCount,
            orderBy = "date",
            order = "desc"
        )

        return (recent + latest)
            .distinctBy { it.id }
            .take(homeProductCount)
    }
    suspend fun categories() = api.categories()
    suspend fun outletAvailability() = api.outletAvailability()
    suspend fun product(id: Int) = api.product(id)
    suspend fun checkout() = api.checkout()
    suspend fun createCheckout(request: CreateOrderRequest) = api.createCheckout(request)
    suspend fun selectShippingRate(request: SelectShippingRateRequest) = api.selectShippingRate(request)
    suspend fun updateCustomer(request: UpdateCustomerRequest) = api.updateCustomer(request)
    suspend fun applyCoupon(code: String) = api.applyCoupon(code)
    suspend fun removeCoupon(code: String) = api.removeCoupon(code)

    suspend fun productWithVariationAvailability(id: Int): StoreProduct =
        api.productWithVariationAvailability(id)
}

enum class CartLoadState { LOADING, SUCCESS_ITEMS, SUCCESS_EMPTY, ERROR }

internal class CartAddGate {
    private var inFlight = false

    @Synchronized
    fun tryAcquire(): Boolean {
        if (inFlight) return false
        inFlight = true
        return true
    }

    @Synchronized
    fun release() {
        inFlight = false
    }
}

internal suspend fun clearConfirmedPaymentLines(
    items: List<CartLine>,
    removeLine: suspend (CartLine) -> Boolean
): Boolean {
    for (item in items.toList()) {
        if (!removeLine(item)) return false
    }
    return true
}

class CartStore(private val api: StoreApi, private val session: StoreSession, private val preferences: android.content.SharedPreferences) {
    private val _cart = MutableStateFlow(WooCart())
    val cart: StateFlow<WooCart> = _cart.asStateFlow()
    private val _state = MutableStateFlow(CartLoadState.LOADING)
    val state: StateFlow<CartLoadState> = _state.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _couponLoading = MutableStateFlow(false)
    val couponLoading: StateFlow<Boolean> = _couponLoading.asStateFlow()
    private val _couponError = MutableStateFlow<String?>(null)
    val couponError: StateFlow<String?> = _couponError.asStateFlow()
    private val cleanupPendingPreference = "post_purchase_cart_cleanup_pending"
    private val _postPurchaseCartCleanupPending = MutableStateFlow(preferences.getBoolean(cleanupPendingPreference, false))
    val postPurchaseCartCleanupPending: StateFlow<Boolean> = _postPurchaseCartCleanupPending.asStateFlow()
    private var confirmedCart = WooCart()
    private val lineLocks = mutableMapOf<String, Mutex>()
    private val cartMutex = Mutex()
    private val couponMutationMutex = Mutex()
    private val addGate = CartAddGate()

    suspend fun refresh() {
        cartMutex.withLock {
            _state.value = CartLoadState.LOADING
            try {
                val remote = withTimeout(18_000) { api.cart() }
                val snapshot = localSnapshot()
                val restored = if (remote.items.isEmpty() && snapshot.isNotEmpty()) rebuildRemote(snapshot) else remote
                accept(restored)
            } catch (exception: Exception) {
                _cart.value = confirmedCart
                _state.value = CartLoadState.ERROR
                _error.value = exception.toStoreUiError().message
            }
        }
    }
    suspend fun add(request: AddCartRequest, parentProductId: Int): Boolean {
        if (_postPurchaseCartCleanupPending.value) {
            _error.value = "Pedido pagado. Estamos actualizando tu carrito antes de permitir otra compra."
            return false
        }
        if (!addGate.tryAcquire()) return false
        return try {
            execute("POST /cart/add-item") { api.addCartItem(request) }.also {
            if (it) {
                persistParentIds(parentProductId)
                confirmedCart = confirmedCart.withParentIds()
                _cart.value = confirmedCart
            }
        }
        } finally {
            addGate.release()
        }
    }
    suspend fun update(line: CartLine, quantity: Int): Boolean = lineLocks.getOrPut(line.key) { Mutex() }.withLock {
        execute("POST /cart/update-item") { api.updateCartItem(line.key, quantity) }
    }
    suspend fun remove(line: CartLine): Boolean = execute("POST /cart/remove-item") { api.removeCartItem(line.key) }

    fun setOptimisticQuantity(line: CartLine, quantity: Int) {
        val confirmedLine = confirmedCart.items.firstOrNull { it.key == line.key } ?: return
        val optimisticLine = confirmedLine.optimisticQuantity(quantity)
        val confirmedTotal = confirmedCart.totals.totalPrice.toBigDecimalOrZero()
        val confirmedSubtotal = confirmedLine.totals.consumerSubtotal().toBigDecimalOrZero()
        val optimisticSubtotal = optimisticLine.totals.consumerSubtotal().toBigDecimalOrZero()
        val optimisticTotals = confirmedCart.totals.copy(
            totalPrice = confirmedTotal.add(optimisticSubtotal.subtract(confirmedSubtotal)).toPlainString()
        )
        _cart.value = confirmedCart.copy(
            items = confirmedCart.items.map { if (it.key == line.key) optimisticLine else it },
            totals = optimisticTotals,
            itemsCount = confirmedCart.items.sumOf { if (it.key == line.key) quantity else it.quantity }
        )
    }

    suspend fun applyCoupon(code: String): Boolean {
        val normalizedCode = code.trim()
        if (normalizedCode.isEmpty()) {
            _couponError.value = "No se ha podido aplicar este código de descuento."
            return false
        }
        return couponMutationMutex.withLock {
            if (_couponLoading.value) return@withLock false
            _couponLoading.value = true
            _couponError.value = null
            try {
                val response = withTimeout(18_000) { api.applyCoupon(normalizedCode) }
                if (response.errors.isNotEmpty()) {
                    throw CartException(
                        buildString {
                            append("DBG path=cart.errors ")
                            append(
                                response.errors.joinToString(" | ") { error ->
                                    "code=${error.code.ifBlank { "<blank>" }}" +
                                        " message=${error.message.ifBlank { "<blank>" }}"
                                }
                            )
                        }
                    )
                }
                accept(response)
                true
            } catch (exception: Exception) {
                val originalMessage =
                    exception.message?.takeIf(String::isNotBlank)

                _couponError.value =
                    if (originalMessage?.startsWith("DBG path=cart.errors") == true) {
                        originalMessage
                    } else {
                        buildString {
                            append("DBG path=exception")
                            append(" type=")
                            append(exception::class.simpleName ?: "<unknown>")
                            append(" message=")
                            append(exception.message ?: "<null>")
                            append(" causeType=")
                            append(
                                exception.cause?.let {
                                    it::class.simpleName
                                } ?: "<null>"
                            )
                            append(" causeMessage=")
                            append(exception.cause?.message ?: "<null>")
                        }
                    }
                false
            } finally {
                _couponLoading.value = false
            }
        }
    }

    suspend fun removeCoupon(code: String): Boolean {
        val normalizedCode = code.trim()
        if (normalizedCode.isEmpty()) {
            _couponError.value = "No se ha podido quitar este código de descuento."
            return false
        }
        return couponMutationMutex.withLock {
            if (_couponLoading.value) return@withLock false
            _couponLoading.value = true
            _couponError.value = null
            try {
                val response = withTimeout(18_000) { api.removeCoupon(normalizedCode) }
                if (response.errors.isNotEmpty()) throw CartException(response.errors.joinToString("\n") { it.message })
                accept(response)
                true
            } catch (exception: Exception) {
                _couponError.value = exception.message?.takeIf(String::isNotBlank) ?: "No se ha podido quitar este código de descuento."
                false
            } finally {
                _couponLoading.value = false
            }
        }
    }

    fun clearError() { _error.value = null }

    fun replace(response: WooCart) {
        accept(response)
    }

    suspend fun lookupOrderStatus(
        orderId: Int,
        orderKey: String
    ): OrderStatusResponse = withTimeout(18_000) {
        api.getOrderStatus(orderId, orderKey)
    }

    suspend fun restoreRemoteAfterUnpaidCheckout() {
        val snapshot = _cart.value.items
        if (snapshot.isEmpty()) return
        val remote = withTimeout(18_000) { api.cart() }
        _cart.value = if (remote.items.isEmpty()) {
            rebuildRemote(snapshot)
        } else remote
    }

    suspend fun clearAfterConfirmedPayment(): Boolean {
        val items = _cart.value.items.toList()
        if (!clearConfirmedPaymentLines(items, ::remove)) {
            preferences.edit().putBoolean(cleanupPendingPreference, true).apply()
            _postPurchaseCartCleanupPending.value = true
            _error.value = "Pedido pagado. Estamos actualizando tu carrito antes de permitir otra compra."
            return false
        }

        if (_cart.value.items.isEmpty()) {
            confirmedCart = WooCart()
            _cart.value = confirmedCart
            preferences.edit().remove("cart_snapshot").remove("cart_line_parents").remove(cleanupPendingPreference).apply()
            _postPurchaseCartCleanupPending.value = false
            _state.value = CartLoadState.SUCCESS_EMPTY
            _error.value = null
            return true
        }

        preferences.edit().putBoolean(cleanupPendingPreference, true).apply()
        _postPurchaseCartCleanupPending.value = true
        _error.value = "Pedido pagado. Estamos actualizando tu carrito antes de permitir otra compra."
        return false
    }

    suspend fun retryPostPurchaseCartCleanup() {
        if (_postPurchaseCartCleanupPending.value) clearAfterConfirmedPayment()
    }

    suspend fun consumeConfirmedOrder() {
        confirmedCart = WooCart()
        _cart.value = confirmedCart
        preferences.edit().remove("cart_snapshot").remove("cart_line_parents").apply()
        _state.value = CartLoadState.SUCCESS_EMPTY
        runCatching { api.cart() }
    }

    private suspend fun execute(endpoint: String, operation: suspend () -> WooCart): Boolean {
        try {
            _error.value = null
            val response = withTimeout(18_000) { operation() }
            if (response.errors.isNotEmpty()) {
                response.errors.forEach { error -> Log.d("CriosRangoStore", "WooCommerce code=${error.code} message=${error.message} endpoint=$endpoint") }
                throw CartException(response.errors.joinToString("\n") { it.message })
            }
            accept(response)
            return true
        } catch (exception: Exception) {
            _cart.value = confirmedCart
            _state.value = CartLoadState.ERROR
            _error.value = exception.toStoreUiError().message
            return false
        }
    }

    private fun accept(response: WooCart) {
        _couponError.value = null
        confirmedCart = response.withParentIds()
        _cart.value = confirmedCart
        _state.value = if (confirmedCart.itemsCount == 0) CartLoadState.SUCCESS_EMPTY else CartLoadState.SUCCESS_ITEMS
        persistSnapshot(confirmedCart.items)
    }

    private suspend fun rebuildRemote(snapshot: List<CartLine>): WooCart {
        var rebuilt = WooCart()
        snapshot.forEach { line ->
            rebuilt = withTimeout(18_000) {
                api.addCartItem(AddCartRequest(line.id, line.quantity, line.variation))
            }
            if (rebuilt.errors.isNotEmpty()) throw CartException(rebuilt.errors.joinToString("\n") { it.message })
        }
        return withTimeout(18_000) { api.cart() }
    }

    private fun persistSnapshot(items: List<CartLine>) {
        if (items.isEmpty()) preferences.edit().remove("cart_snapshot").apply()
        else preferences.edit().putString("cart_snapshot", Gson().toJson(items)).apply()
    }

    private fun localSnapshot(): List<CartLine> = preferences.getString("cart_snapshot", null)?.let {
        runCatching { Gson().fromJson<List<CartLine>>(it, object : TypeToken<List<CartLine>>() {}.type) }.getOrDefault(emptyList())
    }.orEmpty().filter { it.id > 0 && it.quantity > 0 }

    private fun WooCart.withParentIds(): WooCart = copy(items = items.map { line ->
        line.copy(
            parentProductId = parentIds()[line.key],
            consumerUnitPrice = line.totals.consumerSubtotal().toBigDecimalOrZero()
                .divide(line.quantity.coerceAtLeast(1).toBigDecimal(), 0, java.math.RoundingMode.HALF_UP)
                .toPlainString()
        )
    })

    private fun CartLine.optimisticQuantity(newQuantity: Int): CartLine {
        val safeQuantity = newQuantity.coerceAtLeast(0)
        val oldQuantity = quantity.coerceAtLeast(1)
        val netUnit = totals.lineTotal.toBigDecimalOrZero().divide(oldQuantity.toBigDecimal(), 0, java.math.RoundingMode.HALF_UP)
        val taxUnit = totals.lineTotalTax.toBigDecimalOrZero().divide(oldQuantity.toBigDecimal(), 0, java.math.RoundingMode.HALF_UP)
        return copy(
            quantity = safeQuantity,
            totals = totals.copy(
                lineTotal = netUnit.multiply(safeQuantity.toBigDecimal()).toPlainString(),
                lineTotalTax = taxUnit.multiply(safeQuantity.toBigDecimal()).toPlainString()
            ),
            consumerUnitPrice = consumerUnitPrice()
        )
    }
    private fun parentIds(): Map<String, Int> = preferences.getString("cart_line_parents", "")
        .orEmpty().split(';').mapNotNull { entry -> entry.split(':').takeIf { it.size == 2 }?.let { it[0] to it[1].toIntOrNull() } }.filter { it.second != null }.associate { it.first to it.second!! }
    private fun persistParentIds(parentProductId: Int) {
        val parents = parentIds().toMutableMap()
        _cart.value.items.forEach { parents[it.key] = parentProductId }
        preferences.edit().putString("cart_line_parents", parents.entries.joinToString(";") { "${it.key}:${it.value}" }).apply()
    }
}

class CartException(message: String) : Exception(message)

fun Exception.toStoreUiError(): StoreUiError = toStoreUiErrorForCatalog()
