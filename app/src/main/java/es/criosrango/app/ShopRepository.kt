package es.criosrango.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CompletableDeferred
import okhttp3.OkHttpClient
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.net.SocketTimeoutException
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import retrofit2.http.DELETE
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Path
import retrofit2.http.Body

interface StoreApi {
    @GET("products")
    suspend fun products(
        @Query("per_page") perPage: Int = 24,
        @Query("page") page: Int = 1,
        @Query("search") search: String? = null,
        @Query("category") category: Int? = null,
        @Query("orderby") orderBy: String? = null,
        @Query("order") order: String? = null,
        @Query("after") after: String? = null,
        @Query("featured") featured: Boolean? = null
    ): List<StoreProduct>

    @GET("products")
    suspend fun productsByTag(
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
        @Query("tag") tag: String
    ): List<StoreProduct>

    @GET("products/{id}")
    suspend fun product(@Path("id") id: Int): StoreProduct

    /** Public catalog operation; SharedCatalogStoreApiAdapter routes this to KMP. */
    suspend fun productWithVariationAvailability(id: Int): StoreProduct

    @GET("products/categories")
    suspend fun categories(@Query("per_page") perPage: Int = 100): List<ProductCategory>

    @GET("cart")
    suspend fun cart(): WooCart

    @POST("cart/add-item")
    suspend fun addCartItem(@Body request: AddCartRequest): WooCart

    @POST("cart/update-item")
    suspend fun updateCartItem(@Query("key") key: String, @Query("quantity") quantity: Int): WooCart

    @POST("cart/remove-item")
    suspend fun removeCartItem(@Query("key") key: String): WooCart

    @GET("checkout")
    suspend fun checkout(): CheckoutResponse

    @retrofit2.http.Headers("X-CriosRango-App: 1")
    @POST("checkout")
    suspend fun createCheckout(@Body request: CreateOrderRequest): CheckoutResponse

    @retrofit2.http.GET("/wp-json/criosrango/v1/payment-status")
    suspend fun getOrderStatus(
        @retrofit2.http.Query("order_id") orderId: Int,
        @retrofit2.http.Query("key") orderKey: String
    ): OrderStatusResponse

    @POST("cart/select-shipping-rate")
    suspend fun selectShippingRate(@Body request: SelectShippingRateRequest): WooCart

    @POST("cart/update-customer")
    suspend fun updateCustomer(@Body request: UpdateCustomerRequest): WooCart
}

class StoreSession(private val preferences: android.content.SharedPreferences) {
    private companion object {
        const val CART_TOKEN = "woo_cart_token"
        const val NONCE = "woo_nonce"
    }

    var cartToken: String?
        get() = preferences.getString(CART_TOKEN, null)
        private set(value) { preferences.edit().putString(CART_TOKEN, value).apply() }

    var nonce: String?
        get() = preferences.getString(NONCE, null)
        private set(value) { preferences.edit().putString(NONCE, value).apply() }

    @Synchronized
    fun update(headers: okhttp3.Headers) {
        headers["Cart-Token"]?.takeIf { it.isNotBlank() }?.let { cartToken = it }
        headers["Nonce"]?.takeIf { it.isNotBlank() }?.let { nonce = it }
    }

    fun clear() {
        preferences.edit().remove(CART_TOKEN).remove(NONCE).apply()
    }
}

/** Persistent delivery data only; payment credentials are never stored. */
class DeliveryAddressStore(private val preferences: android.content.SharedPreferences) {
    fun load(): CustomerAddress? = CustomerAddress(
        preferences.getString("delivery_first_name", "").orEmpty(), preferences.getString("delivery_last_name", "").orEmpty(),
        preferences.getString("delivery_email", "").orEmpty(), preferences.getString("delivery_phone", "").orEmpty(),
        preferences.getString("delivery_address", "").orEmpty(), preferences.getString("delivery_postcode", "").orEmpty(),
        preferences.getString("delivery_city", "").orEmpty(), preferences.getString("delivery_state", "").orEmpty(),
        preferences.getString("delivery_country", "ES").orEmpty()
    ).takeIf { it.firstName.isNotBlank() || it.lastName.isNotBlank() || it.address1.isNotBlank() }

    fun save(address: CustomerAddress) = preferences.edit()
        .putString("delivery_first_name", address.firstName).putString("delivery_last_name", address.lastName)
        .putString("delivery_email", address.email).putString("delivery_phone", address.phone)
        .putString("delivery_address", address.address1).putString("delivery_postcode", address.postcode)
        .putString("delivery_city", address.city).putString("delivery_state", address.state)
        .putString("delivery_country", address.country).apply()
}

data class DiagnosticNormalClient(
    val client: OkHttpClient,
    val cookieSent: Boolean,
    val cartTokenSent: Boolean,
    val nonceSent: Boolean
)

object StoreApiFactory {
    private var activeClient: OkHttpClient? = null
    private var activeSession: StoreSession? = null

    fun create(session: StoreSession): StoreApi {
        val cookieJar = object : CookieJar {
            private val cookies = mutableListOf<Cookie>()

            override fun saveFromResponse(url: HttpUrl, newCookies: List<Cookie>) {
                synchronized(cookies) {
                    cookies.removeAll { it.matches(url) }
                    cookies.addAll(newCookies)
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> =
                if (url.encodedPath == "/wp-json/wc/store/v1/products") {
                    emptyList()
                } else {
                    synchronized(cookies) { cookies.filter { it.matches(url) } }
                }
        }

        val client = OkHttpClient.Builder()
            .eventListenerFactory { CategoryNetworkEventListener() }
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .cookieJar(cookieJar)
            .addInterceptor { chain ->
                val request = chain.request()
                val builder = request.newBuilder()
                val isPublicProductsGet = request.method.equals("GET", ignoreCase = true) &&
                    request.url.encodedPath == "/wp-json/wc/store/v1/products"
                if (!isPublicProductsGet) {
                    session.cartToken?.let { builder.header("Cart-Token", it) }
                    session.nonce?.let { builder.header("Nonce", it) }
                }
                val response = chain.proceed(builder.build())
                if (request.tag(BackendDiagnosticRequest::class.java) == null) {
                    session.update(response.headers)
                }
                response
            }
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .build()

        activeClient = client
        activeSession = session

        return Retrofit.Builder()
            .baseUrl(STORE_API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(StoreApi::class.java)
    }

    fun diagnosticNormalClient(url: HttpUrl): DiagnosticNormalClient {
        val base = activeClient ?: error("StoreApiFactory no está inicializado")
        val readOnlyCookieJar = object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, newCookies: List<Cookie>) = Unit
            override fun loadForRequest(url: HttpUrl): List<Cookie> =
                base.cookieJar.loadForRequest(url)
        }
        val cookies = readOnlyCookieJar.loadForRequest(url)
        val client = base.newBuilder()
            .cookieJar(readOnlyCookieJar)
            .build()
        val session = activeSession ?: error("StoreApiFactory no tiene sesión activa")
        return DiagnosticNormalClient(
            client = client,
            cookieSent = cookies.isNotEmpty(),
            cartTokenSent = !session.cartToken.isNullOrBlank(),
            nonceSent = !session.nonce.isNullOrBlank()
        )
    }
}

class StoreRepository(private val api: StoreApi) {
    private val cacheMutex = Mutex()

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
                    api.productsByTag(
                        perPage = 100,
                        page = page,
                        tag = brand.slug
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
    suspend fun featuredProducts() = api.products(perPage = 8, featured = true)
    suspend fun categoryProducts(category: Int) = api.products(perPage = 8, category = category)
    suspend fun categories() = api.categories()
    suspend fun product(id: Int) = api.product(id)
    suspend fun checkout() = api.checkout()
    suspend fun createCheckout(request: CreateOrderRequest) = api.createCheckout(request)
    suspend fun selectShippingRate(request: SelectShippingRateRequest) = api.selectShippingRate(request)
    suspend fun updateCustomer(request: UpdateCustomerRequest) = api.updateCustomer(request)

    suspend fun productWithVariationAvailability(id: Int): StoreProduct =
        api.productWithVariationAvailability(id)
}

enum class CartLoadState { LOADING, SUCCESS_ITEMS, SUCCESS_EMPTY, ERROR }

class CartStore(private val api: StoreApi, private val session: StoreSession, private val preferences: android.content.SharedPreferences) {
    private val _cart = MutableStateFlow(WooCart())
    val cart: StateFlow<WooCart> = _cart.asStateFlow()
    private val _state = MutableStateFlow(CartLoadState.LOADING)
    val state: StateFlow<CartLoadState> = _state.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private var confirmedCart = WooCart()
    private val lineLocks = mutableMapOf<String, Mutex>()
    private val cartMutex = Mutex()

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
    suspend fun add(request: AddCartRequest, parentProductId: Int): Boolean = execute("POST /cart/add-item") { api.addCartItem(request) }.also {
        if (it) {
            persistParentIds(parentProductId)
            confirmedCart = confirmedCart.withParentIds()
            _cart.value = confirmedCart
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

    fun clearError() { _error.value = null }

    fun replace(response: WooCart) {
        accept(response)
    }

    suspend fun lookupOrderStatus(
        orderId: Int,
        orderKey: String,
        billingEmail: String
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
