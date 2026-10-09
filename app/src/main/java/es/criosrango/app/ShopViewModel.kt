package es.criosrango.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.CancellationException
import android.util.Log
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.TimeoutCancellationException
import es.criosrango.shared.CatalogPage
import es.criosrango.shared.CatalogPaginator
import es.criosrango.shared.CatalogPagingState
import es.criosrango.shared.checkoutDiagLog
import es.criosrango.shared.sanitizeCheckoutDiag
import es.criosrango.shared.api.StoreApiException

internal suspend fun clearPendingAfterPaidCartCleanup(clearCart: suspend () -> Boolean, clearPending: () -> Boolean): Boolean {
    if (!clearCart()) return false
    return clearPending()
}

enum class CheckoutPhase { IDLE, QUOTING, READY, CREATING_ORDER, ORDER_CREATED, OPENING_PAYMENT, FAILED }
data class PaymentRedirect(val generation: Long, val orderId: Int, val url: String)
data class CardPaymentResult(val orderId: Int, val paid: Boolean?)

internal class CheckoutSubmissionGate {
    private var acquired = false
    @Synchronized fun tryAcquire(): Boolean {
        if (acquired) return false
        acquired = true
        return true
    }
    @Synchronized fun release() { acquired = false }
}

enum class CategoryLoadState { IDLE, LOADING, LOADED_WITH_RESULTS, LOADED_EMPTY, ERROR }
data class CategoryLoadStatus(val categoryId: Int? = null, val state: CategoryLoadState = CategoryLoadState.IDLE)
internal val categoryCatalogLoadStatus = MutableStateFlow(CategoryLoadStatus())

data class CategoryProductsState(val products: List<StoreProduct> = emptyList(), val loading: Boolean = false, val loaded: Boolean = false, val error: StoreUiError? = null, val requestVersion: Int = 0)

class ShopViewModel(private val repository: StoreRepository, val cartStore: CartStore, val deliveryAddressStore: DeliveryAddressStore, private val pendingCardPaymentStore: PendingCardPaymentStore) : ViewModel() {
    private val _checkout = MutableStateFlow<CheckoutResponse?>(null)
    val checkout: StateFlow<CheckoutResponse?> = _checkout.asStateFlow()
    private val _checkoutError = MutableStateFlow<String?>(null)
    val checkoutError: StateFlow<String?> = _checkoutError.asStateFlow()
    private val _checkoutLoading = MutableStateFlow(false)
    val checkoutLoading: StateFlow<Boolean> = _checkoutLoading.asStateFlow()
    private val _products = MutableStateFlow<List<StoreProduct>>(emptyList())
    private val brandProductsCache = mutableMapOf<String, CatalogPagingState<StoreProduct>>()
    private val brandPaginators = mutableMapOf<String, CatalogPaginator<StoreProduct>>()
    private val _brandPagingState = MutableStateFlow(CatalogPagingState<StoreProduct>())
    val brandPagingState: StateFlow<CatalogPagingState<StoreProduct>> = _brandPagingState.asStateFlow()
    private var brandStateSyncJob: Job? = null
    private var activeBrand: BrandTerm? = null
    private val _activeBrandProducts = MutableStateFlow<List<StoreProduct>>(emptyList())
    val activeBrandProducts: StateFlow<List<StoreProduct>> = _activeBrandProducts.asStateFlow()
    private val _activeBrandSlug = MutableStateFlow<String?>(null)
    val activeBrandSlug: StateFlow<String?> = _activeBrandSlug.asStateFlow()
    val products: StateFlow<List<StoreProduct>> = _products.asStateFlow()
    private val _categoryProducts = MutableStateFlow<Map<Int, CategoryProductsState>>(emptyMap())
    val categoryProducts: StateFlow<Map<Int, CategoryProductsState>> = _categoryProducts.asStateFlow()
    private val _activeCategoryId = MutableStateFlow<Int?>(null)
    val activeCategoryId: StateFlow<Int?> = _activeCategoryId.asStateFlow()
    private val _activeCategoryProducts = MutableStateFlow<List<StoreProduct>>(emptyList())
    val activeCategoryProducts: StateFlow<List<StoreProduct>> = _activeCategoryProducts.asStateFlow()
    private val categoryPaginators = mutableMapOf<Int, CatalogPaginator<StoreProduct>>()
    private val _categoryPagingState = MutableStateFlow(CatalogPagingState<StoreProduct>())
    val categoryPagingState: StateFlow<CatalogPagingState<StoreProduct>> = _categoryPagingState.asStateFlow()
    private var categoryStateSyncJob: Job? = null
    private val _categories = MutableStateFlow<List<ProductCategory>>(emptyList())
    val categories: StateFlow<List<ProductCategory>> = _categories.asStateFlow()
    private val _homeProducts = MutableStateFlow<List<StoreProduct>>(emptyList())
    val homeProducts: StateFlow<List<StoreProduct>> = _homeProducts.asStateFlow()
    private val _brands = MutableStateFlow<List<BrandTerm>>(APP_BRANDS)
    val brands: StateFlow<List<BrandTerm>> = _brands.asStateFlow()
    private val _selectedProduct = MutableStateFlow<StoreProduct?>(null)
    val selectedProduct: StateFlow<StoreProduct?> = _selectedProduct.asStateFlow()
    private val _selectedVariation = MutableStateFlow<StoreProduct?>(null)
    val selectedVariation: StateFlow<StoreProduct?> = _selectedVariation.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _initialLoading = MutableStateFlow(true)
    val initialLoading: StateFlow<Boolean> = _initialLoading.asStateFlow()
    private val _error = MutableStateFlow<StoreUiError?>(null)
    val error: StateFlow<StoreUiError?> = _error.asStateFlow()

    private sealed interface CatalogOperation {
        data object Home : CatalogOperation
        data class Category(val id: Int) : CatalogOperation
        data class Search(val query: String) : CatalogOperation
        data class Brand(val brand: BrandTerm) : CatalogOperation
    }

    private var lastCatalogOperation: CatalogOperation = CatalogOperation.Home
    val categoryLoadStatus: StateFlow<CategoryLoadStatus> = categoryCatalogLoadStatus.asStateFlow()
    private val quantityJobs = mutableMapOf<String, Job>()
    private val desiredQuantities = mutableMapOf<String, Int>()
    private var checkoutJob: Job? = null
    private var checkoutGeneration = 0L
    private val _checkoutPhase = MutableStateFlow(CheckoutPhase.IDLE)
    val checkoutPhase: StateFlow<CheckoutPhase> = _checkoutPhase.asStateFlow()
    private val _paymentRedirect = MutableStateFlow<PaymentRedirect?>(null)
    private val _bizumOrderId = MutableStateFlow<Int?>(null)
    val bizumOrderId = _bizumOrderId
    val paymentRedirect: StateFlow<PaymentRedirect?> = _paymentRedirect.asStateFlow()
    private var productsRequestVersion = 0
    private val searchResultCache = mutableMapOf<String, List<StoreProduct>>()
    private var brandLoadJob: Job? = null

    init {
        viewModelScope.launch {
            _checkoutError.collect { value ->
                checkoutDiagLog("STATE checkoutError=${sanitizeCheckoutDiag(value.orEmpty(), 500)} isNull=${value == null}")
            }
        }
    }

    private fun loadBrands() {
        brandLoadJob?.cancel()
        brandLoadJob = viewModelScope.launch {
            val loadedBrands = runCatching { repository.allBrands() }.getOrNull().orEmpty()
            if (loadedBrands.isNotEmpty()) {
                _brands.value = loadedBrands
            }
        }
    }

    fun refreshHome() {
        loadBrands()
        lastCatalogOperation = CatalogOperation.Home
        val requestVersion = ++productsRequestVersion
        categoryCatalogLoadStatus.value = CategoryLoadStatus()
        viewModelScope.launch {
            _isLoading.value = true; _error.value = null
            supervisorScope {
                val result = async { runCatching { repository.products() } }
                val categoryResult = async { runCatching { repository.categories() } }
                val recentResult = async { runCatching { repository.recentProducts() } }
                val loadedProductsResult = result.await()
                val loadedRecentResult = recentResult.await()
                val loadedProducts = loadedProductsResult.getOrNull()
                val loadedRecentProducts = loadedRecentResult.getOrNull()
                if (requestVersion == productsRequestVersion && loadedProducts != null) _products.value = loadedProducts
                if (requestVersion == productsRequestVersion && loadedRecentProducts != null) _homeProducts.value = loadedRecentProducts
                if (requestVersion == productsRequestVersion && (!loadedProducts.isNullOrEmpty() || !loadedRecentProducts.isNullOrEmpty())) _initialLoading.value = false
                if (requestVersion == productsRequestVersion) _isLoading.value = false
                val loadedCategories = categoryResult.await().getOrNull()?.filter { it.count > 0 } ?: _categories.value
                _categories.value = (loadedCategories + listOf(ProductCategory(id = 446, parent = 445, name = "Hombre invierno", count = 0, slug = "hombre-invierno-outlet"), ProductCategory(id = 475, parent = 445, name = "Hombre verano", count = 0, slug = "hombre-verano-outlet"))).distinctBy { it.id }
                if (requestVersion == productsRequestVersion && _products.value.isEmpty() && _homeProducts.value.isEmpty()) {
                    val failure = loadedProductsResult.exceptionOrNull() ?: loadedRecentResult.exceptionOrNull()
                    _error.value = (failure as? Exception)?.toStoreUiError() ?: StoreUiError(StoreErrorType.UNEXPECTED)
                }
            }
            _selectedVariation.value = null
        }
    }

    fun loadVariation(variationId: Int) { viewModelScope.launch { try { _selectedVariation.value = repository.product(variationId) } catch (_: Exception) { _selectedVariation.value = null } } }

    private fun activateCategory(categoryId: Int) {
        _activeCategoryId.value = categoryId
        val state = categoryPaginators[categoryId]?.state?.value ?: CatalogPagingState()
        _categoryPagingState.value = state
        _activeCategoryProducts.value = state.items
        _categoryProducts.value = _categoryProducts.value.toMutableMap().apply {
            put(categoryId, CategoryProductsState(
                products = state.items,
                loading = state.isInitialLoading || state.isAppending,
                loaded = state.currentPage > 0,
                error = (state.initialError ?: state.appendError)?.let { (it as? Exception)?.toStoreUiError() },
                requestVersion = state.currentPage
            ))
        }
    }

    private fun updateCategoryPagingState(categoryId: Int, state: CatalogPagingState<StoreProduct>) {
        if (_activeCategoryId.value != categoryId) return
        _categoryPagingState.value = state
        _activeCategoryProducts.value = state.items
        _categoryProducts.value = _categoryProducts.value.toMutableMap().apply {
            put(categoryId, CategoryProductsState(
                products = state.items,
                loading = state.isInitialLoading || state.isAppending,
                loaded = state.currentPage > 0,
                error = (state.initialError ?: state.appendError)?.let { (it as? Exception)?.toStoreUiError() },
                requestVersion = state.currentPage
            ))
        }
    }

    private fun updateCategoryProducts(categoryId: Int, state: CategoryProductsState) {
        _categoryProducts.value = _categoryProducts.value.toMutableMap().apply { put(categoryId, state) }
        if (_activeCategoryId.value == categoryId) {
            _activeCategoryProducts.value = state.products
        }
    }

    internal fun applyCategoryCacheUpdate(update: CategoryCacheUpdate) {
        if (_activeCategoryId.value != update.categoryId) return
        // The active category UI is paginator-owned. A background Room/global-sync
        // update must not replace a partial paged list with the full cached snapshot.
        if (categoryPaginators.containsKey(update.categoryId)) return
        val current = _categoryProducts.value[update.categoryId]
        updateCategoryProducts(update.categoryId, CategoryProductsState(update.products.toList(), current?.loading == true, true, null, current?.requestVersion ?: 0))
    }

    fun loadCategory(categoryId: Int) {
        lastCatalogOperation = CatalogOperation.Category(categoryId)
        CategoryLoadTelemetry.tap(categoryId)
        val requestVersion = ++productsRequestVersion
        _activeCategoryId.value = categoryId
        categoryStateSyncJob?.cancel()
        val paginator = categoryPaginators.getOrPut(categoryId) { CatalogPaginator(identity = { it.id }) }
        val currentState = paginator.state.value
        _categoryPagingState.value = currentState
        _activeCategoryProducts.value = currentState.items
        updateCategoryPagingState(categoryId, currentState)
        categoryStateSyncJob = viewModelScope.launch {
            paginator.state.collect { state ->
                if (requestVersion != productsRequestVersion || _activeCategoryId.value != categoryId) return@collect
                updateCategoryPagingState(categoryId, state)
                if (state.initialError != null) {
                    _error.value = (state.initialError as? Exception)?.toStoreUiError() ?: StoreUiError(StoreErrorType.UNEXPECTED)
                    categoryCatalogLoadStatus.value = CategoryLoadStatus(categoryId, CategoryLoadState.ERROR)
                } else if (!state.isInitialLoading) {
                    categoryCatalogLoadStatus.value = CategoryLoadStatus(
                        categoryId,
                        if (state.items.isEmpty()) CategoryLoadState.LOADED_EMPTY else CategoryLoadState.LOADED_WITH_RESULTS
                    )
                    _isLoading.value = false
                    if (state.currentPage > 0) CategoryLoadTelemetry.uiProducts(categoryId, state.items.size, "paged")
                }
                if (state.appendError != null) {
                    _error.value = (state.appendError as? Exception)?.toStoreUiError() ?: StoreUiError(StoreErrorType.UNEXPECTED)
                }
            }
        }
        categoryCatalogLoadStatus.value = CategoryLoadStatus(categoryId, if (currentState.currentPage > 0) CategoryLoadState.LOADED_WITH_RESULTS else CategoryLoadState.LOADING)
        if (currentState.currentPage > 0 || currentState.isInitialLoading) {
            _isLoading.value = currentState.isInitialLoading
            return
        }
        _isLoading.value = true
        _error.value = null
        viewModelScope.launch {
            paginator.start(categoryId.toString()) { page, perPage ->
                val items = repository.productsByCategoryPage(
                    categoryId = categoryId,
                    page = page,
                    perPage = perPage
                )
                CatalogPage(items = items, hasMore = items.size >= perPage)
            }
            if (requestVersion == productsRequestVersion && _activeCategoryId.value == categoryId) {
                val state = paginator.state.value
                updateCategoryPagingState(categoryId, state)
                _isLoading.value = state.isInitialLoading
                if (state.initialError != null) _error.value = (state.initialError as? Exception)?.toStoreUiError() ?: StoreUiError(StoreErrorType.UNEXPECTED)
            }
        }
    }

    fun loadNextCategoryPage() {
        val categoryId = _activeCategoryId.value ?: return
        val paginator = categoryPaginators[categoryId] ?: return
        val state = paginator.state.value
        if (state.isAppending || !state.hasMore || state.currentPage <= 0) return
        viewModelScope.launch {
            paginator.loadNext { page, perPage ->
                val items = repository.productsByCategoryPage(
                    categoryId = categoryId,
                    page = page,
                    perPage = perPage
                )
                CatalogPage(items = items, hasMore = items.size >= perPage)
            }
            if (_activeCategoryId.value == categoryId) {
                updateCategoryPagingState(categoryId, paginator.state.value)
            }
        }
    }

    fun loadCategoryTree(categoryId: Int) {
        // Kept as a compatibility entry point for CategoriesScreen. It no longer
        // expands the tree and fires one HTTP request per leaf. Parent categories
        // are resolved by Room/global catalog aggregation or by one priority request.
        loadCategory(categoryId)
    }

    fun search(query: String) {
        val cleanForOperation = query.trim()
        if (cleanForOperation.isNotBlank()) lastCatalogOperation = CatalogOperation.Search(cleanForOperation)
        val requestVersion = ++productsRequestVersion
        categoryCatalogLoadStatus.value = CategoryLoadStatus()
        if (query.isBlank()) { _products.value = emptyList(); _isLoading.value = false; _error.value = null; return }
        val cleanQuery = query.trim(); val cacheKey = cleanQuery.lowercase(); val cached = searchResultCache[cacheKey]
        if (cached != null) { _products.value = cached; _isLoading.value = false; _error.value = null; return }
        viewModelScope.launch {
            _isLoading.value = true; _error.value = null
            try {
                val tokens = cleanQuery.split(Regex("\\s+")).filter { it.length >= 2 }.distinct()
                val loaded = if (tokens.size <= 1) repository.products(search = cleanQuery) else tokens.map { token -> async { repository.products(search = token) } }.awaitAll().flatten().distinctBy { it.id }
                searchResultCache[cacheKey] = loaded
                if (requestVersion == productsRequestVersion) _products.value = loaded
            } catch (exception: Exception) { if (requestVersion == productsRequestVersion) _error.value = exception.toStoreUiError() }
            finally { if (requestVersion == productsRequestVersion) _isLoading.value = false }
        }
    }

    fun loadBrand(brand: BrandTerm) {
        lastCatalogOperation = CatalogOperation.Brand(brand)
        val requestVersion = ++productsRequestVersion
        categoryCatalogLoadStatus.value = CategoryLoadStatus()
        val brandKey = brand.slug.trim().lowercase()
        activeBrand = brand
        _activeBrandSlug.value = brandKey
        brandStateSyncJob?.cancel()
        val paginator = brandPaginators.getOrPut(brandKey) { CatalogPaginator(identity = { it.id }) }
        val currentState = paginator.state.value
        _brandPagingState.value = currentState
        _activeBrandProducts.value = currentState.items
        brandProductsCache[brandKey] = currentState
        brandStateSyncJob = viewModelScope.launch {
            paginator.state.collect { state ->
                if (requestVersion != productsRequestVersion || _activeBrandSlug.value != brandKey) return@collect
                _brandPagingState.value = state
                _activeBrandProducts.value = state.items
                brandProductsCache[brandKey] = state
                if (state.initialError != null) _error.value = (state.initialError as? Exception)?.toStoreUiError() ?: StoreUiError(StoreErrorType.UNEXPECTED)
                if (!state.isInitialLoading) _isLoading.value = false
            }
        }
        if (currentState.currentPage > 0 || currentState.isInitialLoading) {
            _isLoading.value = currentState.isInitialLoading
            return
        }
        _brandPagingState.value = CatalogPagingState(isInitialLoading = true)
        viewModelScope.launch {
            _isLoading.value = true; _error.value = null
            paginator.start(brandKey) { page, perPage ->
                val items = repository.productsByBrandPage(brand, page, perPage)
                CatalogPage(items = items, hasMore = items.size >= perPage)
            }
            if (requestVersion == productsRequestVersion && _activeBrandSlug.value == brandKey) {
                val state = paginator.state.value
                _brandPagingState.value = state
                _activeBrandProducts.value = state.items
                brandProductsCache[brandKey] = state
                _isLoading.value = false
                if (state.initialError != null) _error.value = (state.initialError as? Exception)?.toStoreUiError() ?: StoreUiError(StoreErrorType.UNEXPECTED)
            }
        }
    }

    fun loadNextBrandPage() {
        val brandKey = _activeBrandSlug.value ?: return
        val brand = activeBrand ?: return
        val paginator = brandPaginators[brandKey] ?: return
        val state = paginator.state.value
        if (state.isAppending || !state.hasMore || state.currentPage <= 0) return
        viewModelScope.launch {
            paginator.loadNext { page, perPage ->
                val items = repository.productsByBrandPage(brand, page, perPage)
                CatalogPage(items = items, hasMore = items.size >= perPage)
            }
            if (_activeBrandSlug.value == brandKey && activeBrand?.slug?.trim()?.lowercase() == brandKey) {
                val updated = paginator.state.value
                _brandPagingState.value = updated
                _activeBrandProducts.value = updated.items
                brandProductsCache[brandKey] = updated
            }
        }
    }

    fun retryLastOperation() {
        when (val operation = lastCatalogOperation) {
            CatalogOperation.Home -> refreshHome()
            is CatalogOperation.Category -> loadCategory(operation.id)
            is CatalogOperation.Search -> search(operation.query)
            is CatalogOperation.Brand -> loadBrand(operation.brand)
        }
    }

    fun openProductBySlug(rawSlug: String) {
        val slug = rawSlug.trim().trim('/').lowercase(); if (slug.isBlank()) return
        viewModelScope.launch {
            fun matches(product: StoreProduct): Boolean {
                val urlSlug = runCatching { val segments = android.net.Uri.parse(product.permalink).pathSegments; val productIndex = segments.indexOfFirst { it.equals("producto", ignoreCase = true) }; if (productIndex >= 0) segments.getOrNull(productIndex + 1) else segments.lastOrNull() }.getOrNull()
                return urlSlug?.equals(slug, ignoreCase = true) == true
            }
            val cached = (_products.value + _homeProducts.value).distinctBy { it.id }.firstOrNull(::matches)
            val product = cached ?: try { repository.products(search = slug.replace('-', ' ')).firstOrNull(::matches) ?: repository.products(search = slug).firstOrNull(::matches) } catch (_: Exception) { null }
            if (product != null) openProduct(product) else _error.value = StoreUiError(StoreErrorType.UNEXPECTED)
        }
    }

    fun openProduct(product: StoreProduct) { viewModelScope.launch { _isLoading.value = true; _error.value = null; _selectedVariation.value = null; try { _selectedProduct.value = repository.productWithVariationAvailability(product.id) } catch (_: Exception) { _selectedProduct.value = product } finally { _isLoading.value = false } } }
    fun closeProduct() { _selectedProduct.value = null; _selectedVariation.value = null }

    fun updateCartQuantity(item: CartLine, quantity: Int) {
        invalidateCheckout(); val target = quantity.coerceAtLeast(0); cartStore.setOptimisticQuantity(item, target); desiredQuantities[item.key] = target
        if (quantityJobs[item.key] == null) quantityJobs[item.key] = viewModelScope.launch {
            delay(140)
            while (desiredQuantities[item.key] != null) {
                val desired = desiredQuantities[item.key] ?: break
                if (!cartStore.update(item, desired)) { desiredQuantities.remove(item.key); break }
                if (desiredQuantities[item.key] == desired) { desiredQuantities.remove(item.key); break }
                delay(40)
            }
            quantityJobs.remove(item.key)
        }
    }

    fun addToCart(item: CartItem) {
        invalidateCheckout()
        viewModelScope.launch {
            val id = item.variationId ?: item.productId
            val variation = item.selectedAttributes.map { (attribute, value) -> CartVariation("pa_${normalizeAttributeValue(attribute)}", value) }
            val accepted = cartStore.add(AddCartRequest(id, item.quantity, variation), item.productId)
            if (!accepted && _selectedProduct.value?.id == item.productId) _selectedProduct.value = runCatching { repository.productWithVariationAvailability(item.productId) }.getOrNull()
        }
    }
    fun canIncreaseCart(item: CartLine): Boolean = item.quantityLimits?.maximum?.takeUnless { it == 9999 }?.let { item.quantity < it } ?: true
    fun cartIncrement(item: CartLine): Int = item.quantityLimits?.multipleOf?.takeIf { it > 0 } ?: 1
    fun removeCartLine(item: CartLine) { invalidateCheckout(); viewModelScope.launch { cartStore.remove(item) } }

    fun applyCoupon(code: String) {
        invalidateCheckout()
        viewModelScope.launch { cartStore.applyCoupon(code) }
    }

    fun removeCoupon(code: String) {
        invalidateCheckout()
        viewModelScope.launch { cartStore.removeCoupon(code) }
    }

    fun clearCart() {
        invalidateCheckout()
        viewModelScope.launch { cartStore.cart.value.items.toList().forEach { item -> cartStore.remove(item) } }
    }
    fun refreshCart() { invalidateCheckout(); viewModelScope.launch { cartStore.refresh() } }

    fun loadCheckout(address: CustomerAddress) = loadCheckoutInternal(address, successMessage = null)

    private fun loadCheckoutAfterCouponInvalidation(address: CustomerAddress) =
        loadCheckoutInternal(address, successMessage = COUPON_INVALIDATED_CHECKOUT_MESSAGE)

    private fun loadCheckoutInternal(address: CustomerAddress, successMessage: String?) {
        val generation = ++checkoutGeneration; clearCheckoutForNewGeneration(); checkoutJob?.cancel(); checkoutJob = viewModelScope.launch {
            _checkoutLoading.value = true; _checkoutPhase.value = CheckoutPhase.QUOTING; _checkoutError.value = null
            var checkoutDiagPoint = "LOAD_CHECKOUT_START"
            checkoutDiagLog("LOAD_CHECKOUT START generation=$generation phase=${_checkoutPhase.value}")
            try {
                checkoutDiagPoint = "UPDATE_CUSTOMER"
                val response = repository.updateCustomer(UpdateCustomerRequest(address))
                logCheckoutCart("UPDATE_CUSTOMER OK", response)
                if (generation != checkoutGeneration) return@launch
                if (response.errors.isNotEmpty()) { response.errors.forEach { Log.d("CriosRangoStore", "WooCommerce code=${it.code} message=${it.message} endpoint=POST /cart/update-customer") }; throw CartException(response.errors.joinToString("\\n") { it.message }) }
                logShippingResponse(response); cartStore.replace(response)
                checkoutDiagLog("POST_GET cartStore_shipping_rates_count=${cartStore.cart.value.shippingRates.sumOf { it.rates.size }}")
                cartStore.cart.value.shippingRates.forEach { pack -> pack.rates.forEach { rate ->
                    checkoutDiagLog("POST_GET rateId=${sanitizeCheckoutDiag(rate.rateId, 100)} selected=${rate.selected} packageId=${pack.packageId}")
                } }
                checkoutDiagPoint = "REPOSITORY_CHECKOUT"
                checkoutDiagLog("CHECKOUT_GET START")
                val checkoutResponse = try {
                    repository.checkout()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    logCheckoutGetException(exception)
                    throw exception
                }
                logCheckoutResponse("CHECKOUT_GET OK", checkoutResponse)
                if (generation != checkoutGeneration) return@launch
                if (checkoutResponse.errors.isNotEmpty()) throw CartException(checkoutResponse.errors.joinToString("\\n") { it.message })

                checkoutDiagPoint = "POST_GET_RATE_EVALUATION"
                val visibleRates = response.visibleShippingRatesForDestination()
                val hasSelectedRate = visibleRates.any { it.rates.any { rate -> rate.selected } }
                val fallbackRate = visibleRates.firstOrNull { it.rates.isNotEmpty() }?.let { it to it.rates.first() }
                checkoutDiagLog("POST_GET shipping_rates_count=${visibleRates.sumOf { it.rates.size }} hasSelectedRate=$hasSelectedRate fallbackRate=${fallbackRate?.let { "${it.first.packageId}:${sanitizeCheckoutDiag(it.second.rateId, 100)}" } ?: "null"}")
                visibleRates.forEach { pack -> pack.rates.forEach { rate ->
                    checkoutDiagLog("POST_GET rateId=${sanitizeCheckoutDiag(rate.rateId, 100)} selected=${rate.selected} packageId=${pack.packageId}")
                } }
                checkoutDiagLog("SELECT_RATE_BRANCH entered=${!hasSelectedRate && fallbackRate != null}")
                val finalCheckoutResponse = if (!hasSelectedRate && fallbackRate != null) {
                    checkoutDiagPoint = "SELECT_SHIPPING_RATE"
                    val (packageRate, rate) = fallbackRate
                    val selectedCart = repository.selectShippingRate(SelectShippingRateRequest(packageRate.packageId, rate.rateId))
                    if (selectedCart.errors.isNotEmpty()) throw CartException(selectedCart.errors.joinToString("\\n") { it.message })
                    if (generation != checkoutGeneration) return@launch
                    logShippingResponse(selectedCart); cartStore.replace(selectedCart)
                    checkoutDiagPoint = "REQUOTE_CHECKOUT"
                    val requotedCheckout = repository.checkout()
                    if (requotedCheckout.errors.isNotEmpty()) throw CartException(requotedCheckout.errors.joinToString("\\n") { it.message })
                    requotedCheckout
                } else checkoutResponse

                checkoutDiagPoint = "BEFORE_FINAL_ASSIGN"
                checkoutDiagLog("BEFORE_FINAL_ASSIGN phase=${_checkoutPhase.value} checkoutIsNull=${_checkout.value == null} checkoutError=${sanitizeCheckoutDiag(_checkoutError.value.orEmpty(), 500)} finalOrderId=${finalCheckoutResponse.orderId} experimentalCart=${finalCheckoutResponse.experimentalCart != null} totalShipping=${finalCheckoutResponse.totals.totalShipping}")
                _checkout.value = finalCheckoutResponse; _checkoutError.value = successMessage; _checkoutPhase.value = CheckoutPhase.READY
                checkoutDiagPoint = "AFTER_FINAL_ASSIGN"
                checkoutDiagLog("AFTER_FINAL_ASSIGN phase=${_checkoutPhase.value} checkoutIsNull=${_checkout.value == null} checkoutError=${sanitizeCheckoutDiag(_checkoutError.value.orEmpty(), 500)} orderId=${_checkout.value?.orderId} experimentalCart=${_checkout.value?.experimentalCart != null} totalShipping=${_checkout.value?.totals?.totalShipping}")
            } catch (exception: CancellationException) { throw exception } catch (exception: Exception) {
                checkoutDiagLog("CATCH point=$checkoutDiagPoint type=${exception::class.qualifiedName} message=${sanitizeCheckoutDiag(exception.message.orEmpty(), 500)}")
                _checkoutError.value = exception.message; _checkout.value = null; _checkoutPhase.value = CheckoutPhase.FAILED
                checkoutDiagLog("CATCH_STATE phase=${_checkoutPhase.value} checkoutIsNull=${_checkout.value == null} checkoutError=${sanitizeCheckoutDiag(_checkoutError.value.orEmpty(), 500)}")
            }
            finally { if (generation == checkoutGeneration) _checkoutLoading.value = false }
        }
    }

    private fun logCheckoutCart(label: String, cart: WooCart) {
        checkoutDiagLog(
            "$label coupons_count=${cart.coupons.size} coupon_codes=${cart.coupons.joinToString(",") { sanitizeCheckoutDiag(it.code, 80) }} " +
                "total_items=${cart.totals.totalItems} total_items_tax=${cart.totals.totalItemsTax} " +
                "total_discount=${cart.totals.totalDiscount} total_discount_tax=${cart.totals.totalDiscountTax} " +
                "total_price=${cart.totals.totalPrice}"
        )
        cart.shippingRates.forEach { packageRate ->
            packageRate.rates.forEach { rate ->
                checkoutDiagLog(
                    "$label SHIPPING package_id=${packageRate.packageId} rateId=${sanitizeCheckoutDiag(rate.rateId, 100)} " +
                        "methodId=${sanitizeCheckoutDiag(rate.methodId, 100)} selected=${rate.selected} price=${rate.price}"
                )
            }
        }
        checkoutDiagLog(
            "$label errors_count=${cart.errors.size} errors=${cart.errors.joinToString("|") { "${sanitizeCheckoutDiag(it.code, 100)}:${sanitizeCheckoutDiag(it.message, 300)}" }}"
        )
    }

    private fun logCheckoutResponse(label: String, response: CheckoutResponse) {
        val totals = response.totals
        checkoutDiagLog(
            "$label errors_count=${response.errors.size} errors=${response.errors.joinToString("|") { "${sanitizeCheckoutDiag(it.code, 100)}:${sanitizeCheckoutDiag(it.message, 300)}" }} " +
                "total_items=${totals.totalItems} total_items_tax=${totals.totalItemsTax} " +
                "total_discount=${totals.totalDiscount} total_discount_tax=${totals.totalDiscountTax} " +
                "total_shipping=${totals.totalShipping} total_shipping_tax=${totals.totalShippingTax} total_price=${totals.totalPrice} " +
                "experimental_cart=${response.experimentalCart != null} shipping_rates=not_exposed_by_checkout_model"
        )
        response.experimentalCart?.totals?.let { experimental ->
            checkoutDiagLog(
                "$label EXPERIMENTAL_CART total_items=${experimental.totalItems} total_items_tax=${experimental.totalItemsTax} " +
                    "total_discount=${experimental.totalDiscount} total_discount_tax=${experimental.totalDiscountTax} " +
                    "total_shipping=${experimental.totalShipping} total_shipping_tax=${experimental.totalShippingTax} total_price=${experimental.totalPrice}"
            )
        }
    }

    private fun logCheckoutGetException(exception: Exception) {
        val detail = when (exception) {
            is StoreApiException -> " status=${exception.statusCode} apiCode=${sanitizeCheckoutDiag(exception.apiCode.orEmpty(), 100)}"
            else -> ""
        }
        val cause = exception.cause
        val causeDetail = when (cause) {
            is StoreApiException -> " causeType=${cause::class.qualifiedName} causeStatus=${cause.statusCode} causeApiCode=${sanitizeCheckoutDiag(cause.apiCode.orEmpty(), 100)} causeMessage=${sanitizeCheckoutDiag(cause.message.orEmpty(), 400)}"
            null -> ""
            else -> " causeType=${cause::class.qualifiedName} causeMessage=${sanitizeCheckoutDiag(cause.message.orEmpty(), 400)}"
        }
        checkoutDiagLog(
            "CHECKOUT_GET EXCEPTION type=${exception::class.qualifiedName}$detail " +
                "message=${sanitizeCheckoutDiag(exception.message.orEmpty(), 500)}$causeDetail"
        )
    }

    fun selectShippingRate(packageId: Int, rateId: String) {
        val generation = ++checkoutGeneration; clearCheckoutForNewGeneration(); checkoutJob?.cancel(); checkoutJob = viewModelScope.launch {
            _checkoutLoading.value = true; _checkoutPhase.value = CheckoutPhase.QUOTING; _checkoutError.value = null
            try {
                val response = repository.selectShippingRate(SelectShippingRateRequest(packageId, rateId)); if (generation != checkoutGeneration) return@launch
                if (response.errors.isNotEmpty()) { response.errors.forEach { Log.d("CriosRangoStore", "WooCommerce code=${it.code} message=${it.message} endpoint=POST /cart/select-shipping-rate") }; throw CartException(response.errors.joinToString("\n") { it.message }) }
                logShippingResponse(response); cartStore.replace(response); val checkoutResponse = repository.checkout(); if (generation != checkoutGeneration) return@launch
                if (checkoutResponse.errors.isNotEmpty()) throw CartException(checkoutResponse.errors.joinToString("\n") { it.message })
                _checkout.value = checkoutResponse; _checkoutPhase.value = CheckoutPhase.READY
            } catch (exception: CancellationException) { throw exception } catch (exception: Exception) { _checkoutError.value = exception.toStoreUiError().message; _checkoutPhase.value = CheckoutPhase.FAILED }
            finally { if (generation == checkoutGeneration) _checkoutLoading.value = false }
        }
    }

    private var lastCheckout: LastCheckout? = pendingCardPaymentStore.load()
    private val checkoutSubmissionGate = CheckoutSubmissionGate()
    private val _cardPaymentResult = MutableStateFlow<CardPaymentResult?>(null)
    val cardPaymentResult: StateFlow<CardPaymentResult?> = _cardPaymentResult.asStateFlow()

    private class PaidCartCleanupException(cause: Exception) : Exception("Paid cart cleanup failed", cause)

    private enum class ReconcileOutcome { PAID, CLEARED, PENDING, NO_MARKER }
    private val reconciliationMutex = kotlinx.coroutines.sync.Mutex()
    init {
        // Load the durable marker before startup work; a restart must not discard it.
        lastCheckout = pendingCardPaymentStore.load()
        refreshHome()
        viewModelScope.launch {
            cartStore.refresh()
        }
    }


    private suspend fun reconcileLastCheckout(publishPaidResult: Boolean = true, preserveMarkerOnExhaustion: Boolean = false): ReconcileOutcome = reconciliationMutex.withLock {
        val checkout = lastCheckout ?: pendingCardPaymentStore.load()?.also { lastCheckout = it } ?: return@withLock ReconcileOutcome.NO_MARKER
        when (val result = reconcilePaymentStatus(
            maxRetries = PAYMENT_RECONCILIATION_MAX_RETRIES,
            delayMs = PAYMENT_RECONCILIATION_DELAY_MS
        ) {
            cartStore.lookupOrderStatus(checkout.orderId, checkout.orderKey)
        }) {
            is PaymentReconciliationResult.PAID -> {
                val confirmedOrderId = if (result.order.id > 0) result.order.id else checkout.orderId
                // If cart cleanup fails, leave the durable marker intact for recovery.
                val cleanupCompleted = try {
                    clearPendingAfterPaidCartCleanup(
                        clearCart = { cartStore.clearAfterConfirmedPayment() },
                        clearPending = { pendingCardPaymentStore.clear() }
                    )
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    throw PaidCartCleanupException(exception)
                }
                if (cleanupCompleted) lastCheckout = null
                _paymentRedirect.value = null
                if (publishPaidResult) _cardPaymentResult.value = CardPaymentResult(confirmedOrderId, true)
                _checkoutPhase.value = CheckoutPhase.ORDER_CREATED
                ReconcileOutcome.PAID
            }
            PaymentReconciliationResult.TERMINAL_UNPAID -> {
                pendingCardPaymentStore.clear()
                lastCheckout = null
                _paymentRedirect.value = null
                ReconcileOutcome.CLEARED
            }
            PaymentReconciliationResult.EXHAUSTED -> {
                if (!preserveMarkerOnExhaustion) {
                    pendingCardPaymentStore.clear()
                    lastCheckout = null
                    _paymentRedirect.value = null
                }
                ReconcileOutcome.PENDING
            }
        }
    }

    fun createOrder(address: CustomerAddress, paymentMethod: String, shippingRateId: String?) {
        if (!checkoutSubmissionGate.tryAcquire()) return
        val generation = checkoutGeneration
        _checkoutLoading.value = true
        _checkoutPhase.value = CheckoutPhase.CREATING_ORDER
        _checkoutError.value = null
        val checkoutAtPaymentStart = _checkout.value
        checkoutDiagLog(
            "PAYMENT START paymentMethod=${sanitizeCheckoutDiag(paymentMethod, 80)} " +
                "checkoutPhase=${_checkoutPhase.value} currentOrderId=${checkoutAtPaymentStart?.orderId} " +
                "experimentalCart=${checkoutAtPaymentStart?.experimentalCart != null} " +
                "checkoutTotal=${checkoutAtPaymentStart?.totals?.totalPrice} cartTotal=${cartStore.cart.value.totals.totalPrice}"
        )
        checkoutDiagLog("CREATE_ORDER START")
        viewModelScope.launch {
            var createOrderPoint = "VALIDATE_INPUT"
            try {
                if (shippingRateId.isNullOrBlank() || paymentMethod.isNullOrBlank()) {
                    _checkoutError.value = "Selecciona una tarifa y un método de pago válidos."
                    _checkoutPhase.value = CheckoutPhase.FAILED
                    return@launch
                }
                val currentCart = cartStore.cart.value
                val selectedPackage = currentCart.visibleShippingRatesForDestination()
                    .firstOrNull { packageRate -> packageRate.rates.any { it.rateId == shippingRateId } }

                if (selectedPackage != null && selectedPackage.rates.none { it.rateId == shippingRateId && it.selected }) {
                    _checkoutPhase.value = CheckoutPhase.QUOTING
                    val revalidatedCart = repository.selectShippingRate(
                        SelectShippingRateRequest(selectedPackage.packageId, shippingRateId)
                    )
                    if (revalidatedCart.errors.isNotEmpty()) {
                        throw CartException(revalidatedCart.errors.joinToString("\n") { it.message })
                    }
                    if (generation != checkoutGeneration) return@launch
                    logShippingResponse(revalidatedCart)
                    cartStore.replace(revalidatedCart)
                    val revalidatedCheckout = repository.checkout()
                    if (revalidatedCheckout.errors.isNotEmpty()) {
                        throw CartException(revalidatedCheckout.errors.joinToString("\n") { it.message })
                    }
                    if (generation != checkoutGeneration) return@launch
                    _checkout.value = revalidatedCheckout
                    _checkoutPhase.value = CheckoutPhase.READY
                }

                val finalCart = cartStore.cart.value
                val request = CreateOrderRequest(paymentMethod = paymentMethod, billing_address = address, shipping_address = address, shippingRate = shippingRateId, expectedTotal = finalCart.totals.totalPrice, paymentData = emptyMap())
                createOrderPoint = "POST_CHECKOUT"
                checkoutDiagLog(
                    "CREATE_ORDER REQUEST paymentMethod=${sanitizeCheckoutDiag(paymentMethod, 80)} " +
                        "shippingRateId=${sanitizeCheckoutDiag(shippingRateId, 100)} expectedTotal=${finalCart.totals.totalPrice} " +
                        "priorOrderId=${_checkout.value?.orderId} experimentalCart=${_checkout.value?.experimentalCart != null}"
                )
                val response = repository.createCheckout(request)
                if (generation != checkoutGeneration) return@launch
                checkoutDiagLog(
                    "CREATE_ORDER OK orderId=${response.orderId} status=${sanitizeCheckoutDiag(response.status.orEmpty(), 100)} " +
                        "paymentResultStatus=${sanitizeCheckoutDiag(response.paymentResult?.paymentStatus.orEmpty(), 100)} " +
                        "redirectPresent=${response.paymentRedirectUrl().isNullOrBlank().not()} " +
                        "errors_count=${response.errors.size} errors=${response.errors.joinToString("|") { "${sanitizeCheckoutDiag(it.code, 100)}:${sanitizeCheckoutDiag(it.message, 400)}" }}"
                )
                if (response.errors.isNotEmpty()) throw CartException(response.errors.joinToString("\n") { it.message })
                if (response.orderId == null) throw CartException("La tienda no ha confirmado la creación del pedido.")
                _checkout.value = response; _checkoutPhase.value = CheckoutPhase.ORDER_CREATED
                val isNativeBizum = paymentMethod.equals("bizum", ignoreCase = true) || paymentMethod.equals("cheque", ignoreCase = true)
                if (isNativeBizum) { cartStore.consumeConfirmedOrder(); _bizumOrderId.value = response.orderId } else {
                    val orderKey = response.orderKey?.takeIf { it.isNotBlank() } ?: throw CartException("La tienda no ha devuelto la clave del pedido.")
                    val paymentUrl = response.paymentRedirectUrl() ?: throw CartException("La tienda no ha devuelto la URL de pago.")
                    val pending = LastCheckout(response.orderId, orderKey, paymentUrl)
                    if (!pendingCardPaymentStore.save(pending)) {
                        throw CartException("No se ha podido guardar el último checkout. No se abrirá la pasarela.")
                    }
                    lastCheckout = pending
                    _checkoutPhase.value = CheckoutPhase.OPENING_PAYMENT
                    _paymentRedirect.value = PaymentRedirect(generation, response.orderId, paymentUrl)
                }
            } catch (exception: Exception) {
                if (generation != checkoutGeneration) return@launch
                if (exception is CouponInvalidatedCheckoutException) {
                    checkoutDiagLog(
                        "CREATE_ORDER COUPON_INVALIDATED status=${exception.httpStatus} " +
                            "backendCode=${sanitizeCheckoutDiag(exception.backendCode, 120)} " +
                            "cartPresent=${exception.updatedCart != null} removedCouponCount=${exception.removedCouponNames.size}"
                    )
                    _paymentRedirect.value = null
                    _bizumOrderId.value = null
                    reconcileCouponInvalidation(
                        updatedCart = exception.updatedCart,
                        removedCouponCodes = exception.removedCouponCodes,
                        replaceCart = { updated ->
                            cartStore.replace(updated)
                            checkoutDiagLog(
                                "CREATE_ORDER COUPON_CART_REPLACED coupons=${updated.coupons.joinToString(",") { sanitizeCheckoutDiag(it.code, 80) }} " +
                                    "discount=${updated.totals.totalDiscount} total=${updated.totals.totalPrice}"
                            )
                        },
                        refreshCart = {
                            checkoutDiagLog("CREATE_ORDER COUPON_CART_REFRESH START")
                            cartStore.refresh()
                            checkoutDiagLog(
                                "CREATE_ORDER COUPON_CART_REFRESH END coupons=${cartStore.cart.value.coupons.joinToString(",") { sanitizeCheckoutDiag(it.code, 80) }} " +
                                    "discount=${cartStore.cart.value.totals.totalDiscount} total=${cartStore.cart.value.totals.totalPrice}"
                            )
                        },
                        currentCartCouponCodes = { cartStore.cart.value.coupons.map { it.code } },
                        onCheckout = { _checkout.value = it },
                        onPhase = { _checkoutPhase.value = it },
                        refreshCheckoutOnce = {
                            checkoutDiagLog("CREATE_ORDER COUPON_AUTO_CHECKOUT_REFRESH START")
                            loadCheckoutAfterCouponInvalidation(address)
                        },
                        onCartVerified = { codes, stillInvalid ->
                            checkoutDiagLog(
                                "CREATE_ORDER COUPON_CART_VERIFIED coupons=${codes.joinToString(",") { sanitizeCheckoutDiag(it, 80) }} " +
                                    "stillInvalidCoupon=$stillInvalid"
                            )
                        }
                    )
                } else {
                    val transformedError = exception.toStoreUiError().message
                    val apiDetail = exception as? StoreApiException
                    checkoutDiagLog(
                        "CREATE_ORDER CATCH point=$createOrderPoint type=${exception::class.qualifiedName} " +
                            "message=${sanitizeCheckoutDiag(exception.message.orEmpty(), 600)} " +
                            "httpStatus=${apiDetail?.statusCode ?: "null"} backendCode=${sanitizeCheckoutDiag(apiDetail?.apiCode.orEmpty(), 120)} " +
                            "backendMessage=${sanitizeCheckoutDiag(apiDetail?.message.orEmpty(), 600)} " +
                            "transformedMessage=${sanitizeCheckoutDiag(transformedError, 500)}"
                    )
                    _checkout.value = null
                    _checkoutError.value = transformedError
                    _checkoutPhase.value = CheckoutPhase.FAILED
                }
            } finally {
                checkoutSubmissionGate.release()
                if (generation == checkoutGeneration) _checkoutLoading.value = false
            }
        }
    }

    fun handleCardPaymentCancelled(orderId: Int) {
        if (lastCheckout?.orderId != orderId) return

        // An explicit Cecabank cancellation is terminal for this payment attempt.
        // Do not reconcile it as a normal return: the customer must go back to the cart.
        pendingCardPaymentStore.clear()
        lastCheckout = null
        _paymentRedirect.value = null
        _cardPaymentResult.value = CardPaymentResult(orderId, false)
        invalidateCheckout()

        viewModelScope.launch {
            runCatching { cartStore.restoreRemoteAfterUnpaidCheckout() }
                .onFailure { exception ->
                    _checkoutError.value = exception.message
                }
        }
    }

    fun verifyCardPaymentReturn() {
        if (_checkoutLoading.value || lastCheckout == null) return
        viewModelScope.launch {
            _checkoutLoading.value = true
            try {
                reconcileLastCheckout(preserveMarkerOnExhaustion = true)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (exception !is PaidCartCleanupException && !isTransientPaymentStatusException(exception)) {
                    pendingCardPaymentStore.clear()
                    lastCheckout = null
                    _paymentRedirect.value = null
                }
                _checkoutError.value = "No hemos podido comprobar el pago."
            } finally {
                _checkoutLoading.value = false
            }
        }
    }

    fun consumeCardPaymentResult() { _cardPaymentResult.value = null }
    fun consumePaymentRedirect(redirect: PaymentRedirect) { if (_paymentRedirect.value == redirect && redirect.generation == checkoutGeneration) _paymentRedirect.value = null }
    fun consumeBizumOrder() { _bizumOrderId.value = null }
    fun abandonCheckout() = invalidateCheckout()
    private fun invalidateCheckout() { checkoutSubmissionGate.release(); ++checkoutGeneration; checkoutJob?.cancel(); clearCheckoutForNewGeneration(); _checkoutLoading.value = false; _checkoutPhase.value = CheckoutPhase.IDLE }
    private fun clearCheckoutForNewGeneration() { _checkout.value = null; _checkoutError.value = null; _paymentRedirect.value = null; _bizumOrderId.value = null }
    fun openCartLine(item: CartLine) { viewModelScope.launch { val productId = item.parentProductId ?: item.id; _isLoading.value = true; _selectedVariation.value = null; _selectedProduct.value = runCatching { repository.productWithVariationAvailability(productId) }.getOrNull(); _isLoading.value = false } }
    fun clearError() { _error.value = null }
    private fun logShippingResponse(cart: WooCart) { cart.shippingRates.forEach { packageRate -> packageRate.rates.forEach { rate -> Log.d("CriosRangoShipping", "package=${packageRate.packageId} method=${rate.methodId} rate=${rate.rateId} selected=${rate.selected} price=${rate.price} taxes=${rate.taxes}") } }; Log.d("CriosRangoShipping", "totals shipping=${cart.totals.totalShipping} shippingTax=${cart.totals.totalShippingTax} total=${cart.totals.totalPrice}") }

    class Factory(private val repository: StoreRepository, private val cartStore: CartStore, private val deliveryAddressStore: DeliveryAddressStore, private val pendingCardPaymentStore: PendingCardPaymentStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ShopViewModel(repository, cartStore, deliveryAddressStore, pendingCardPaymentStore) as T
    }
}