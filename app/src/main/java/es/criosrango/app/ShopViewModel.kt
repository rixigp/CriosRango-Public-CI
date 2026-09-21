package es.criosrango.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import retrofit2.HttpException

enum class CheckoutPhase { IDLE, QUOTING, READY, CREATING_ORDER, ORDER_CREATED, OPENING_PAYMENT, FAILED }
data class PaymentRedirect(val generation: Long, val orderId: Int, val url: String)
data class CardPaymentResult(val orderId: Int, val paid: Boolean?)

enum class CategoryLoadState { IDLE, LOADING, LOADED_WITH_RESULTS, LOADED_EMPTY, ERROR }
data class CategoryLoadStatus(val categoryId: Int? = null, val state: CategoryLoadState = CategoryLoadState.IDLE)
internal val categoryCatalogLoadStatus = MutableStateFlow(CategoryLoadStatus())

fun StoreProduct.hasBrand(brand: BrandTerm): Boolean = tags.any { tag -> tag.slug.equals(brand.slug, ignoreCase = true) || tag.name.replace("&amp;", "&").replace("&#038;", "&").trim().equals(brand.name, ignoreCase = true) }

data class CategoryProductsState(val products: List<StoreProduct> = emptyList(), val loading: Boolean = false, val loaded: Boolean = false, val error: StoreUiError? = null, val requestVersion: Int = 0)

class ShopViewModel(private val repository: StoreRepository, val cartStore: CartStore, val deliveryAddressStore: DeliveryAddressStore, private val pendingCardPaymentStore: PendingCardPaymentStore) : ViewModel() {
    private val _checkout = MutableStateFlow<CheckoutResponse?>(null)
    val checkout: StateFlow<CheckoutResponse?> = _checkout.asStateFlow()
    private val _checkoutError = MutableStateFlow<String?>(null)
    val checkoutError: StateFlow<String?> = _checkoutError.asStateFlow()
    private val _checkoutLoading = MutableStateFlow(false)
    val checkoutLoading: StateFlow<Boolean> = _checkoutLoading.asStateFlow()
    private val _products = MutableStateFlow<List<StoreProduct>>(emptyList())
    private val brandProductsCache = mutableMapOf<String, List<StoreProduct>>()
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
    init {
        refreshHome()
        viewModelScope.launch {
            cartStore.refresh()
            reconcileAfterProcessDeath()
        }
    }
    private var productsRequestVersion = 0
    private val searchResultCache = mutableMapOf<String, List<StoreProduct>>()

    fun refreshHome() {
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
        _activeCategoryProducts.value = _categoryProducts.value[categoryId]?.products.orEmpty()
    }

    private fun updateCategoryProducts(categoryId: Int, state: CategoryProductsState) {
        _categoryProducts.value = _categoryProducts.value.toMutableMap().apply { put(categoryId, state) }
        if (_activeCategoryId.value == categoryId) {
            _activeCategoryProducts.value = state.products
        }
    }

    internal fun applyCategoryCacheUpdate(update: CategoryCacheUpdate) {
        if (_activeCategoryId.value != update.categoryId) return
        val current = _categoryProducts.value[update.categoryId]
        updateCategoryProducts(update.categoryId, CategoryProductsState(update.products.toList(), current?.loading == true, true, null, current?.requestVersion ?: 0))
    }

    fun loadCategory(categoryId: Int) {
        lastCatalogOperation = CatalogOperation.Category(categoryId)
        CategoryLoadTelemetry.tap(categoryId)
        activateCategory(categoryId)
        val requestVersion = ++productsRequestVersion
        val categoryRequestVersion = (_categoryProducts.value[categoryId]?.requestVersion ?: 0) + 1
        val previous = _categoryProducts.value[categoryId]
        updateCategoryProducts(categoryId, CategoryProductsState(previous?.products.orEmpty(), true, previous?.loaded == true, null, categoryRequestVersion))
        categoryCatalogLoadStatus.value = CategoryLoadStatus(categoryId, CategoryLoadState.LOADING)
        viewModelScope.launch {
            _isLoading.value = true; _error.value = null
            try {
                val loaded = repository.products(category = categoryId)
                updateCategoryProducts(categoryId, CategoryProductsState(loaded, false, true, null, categoryRequestVersion))
                if (_activeCategoryId.value == categoryId) CategoryLoadTelemetry.uiProducts(categoryId, loaded.size, "network")
                if (requestVersion == productsRequestVersion && _activeCategoryId.value == categoryId) categoryCatalogLoadStatus.value = CategoryLoadStatus(categoryId, if (loaded.isEmpty()) CategoryLoadState.LOADED_EMPTY else CategoryLoadState.LOADED_WITH_RESULTS)
            } catch (exception: Exception) {
                val current = _categoryProducts.value[categoryId]
                updateCategoryProducts(categoryId, CategoryProductsState(current?.products.orEmpty(), false, current?.loaded == true, exception.toStoreUiError(), categoryRequestVersion))
                if (requestVersion == productsRequestVersion && _activeCategoryId.value == categoryId) {
                    categoryCatalogLoadStatus.value = CategoryLoadStatus(categoryId, CategoryLoadState.ERROR)
                    _error.value = exception.toStoreUiError()
                }
            } finally {
                if (requestVersion == productsRequestVersion) _isLoading.value = false
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
        _activeBrandSlug.value = brandKey
        _activeBrandProducts.value = brandProductsCache[brandKey].orEmpty()
        viewModelScope.launch {
            _isLoading.value = true; _error.value = null
            try {
                val direct = runCatching { repository.productsByBrand(brand) }.getOrDefault(emptyList())
                val loaded = if (direct.isNotEmpty()) direct else repository.allProducts().filter { it.hasBrand(brand) }
                val isolated = loaded.distinctBy { it.id }
                brandProductsCache[brandKey] = isolated
                if (requestVersion == productsRequestVersion && _activeBrandSlug.value == brandKey) {
                    _activeBrandProducts.value = isolated
                }
            } catch (exception: Exception) {
                if (requestVersion == productsRequestVersion && _activeBrandSlug.value == brandKey) {
                    _error.value = exception.toStoreUiError()
                }
            } finally { if (requestVersion == productsRequestVersion) _isLoading.value = false }
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
    fun clearCart() {
        invalidateCheckout()
        viewModelScope.launch { cartStore.cart.value.items.toList().forEach { item -> cartStore.remove(item) } }
    }
    fun refreshCart() { invalidateCheckout(); viewModelScope.launch { cartStore.refresh() } }

    fun loadCheckout(address: CustomerAddress) {
        val generation = ++checkoutGeneration; clearCheckoutForNewGeneration(); checkoutJob?.cancel(); checkoutJob = viewModelScope.launch {
            _checkoutLoading.value = true; _checkoutPhase.value = CheckoutPhase.QUOTING; _checkoutError.value = null
            try {
                val response = repository.updateCustomer(UpdateCustomerRequest(address)); if (generation != checkoutGeneration) return@launch
                if (response.errors.isNotEmpty()) { response.errors.forEach { Log.d("CriosRangoStore", "WooCommerce code=${it.code} message=${it.message} endpoint=POST /cart/update-customer") }; throw CartException(response.errors.joinToString("\n") { it.message }) }
                logShippingResponse(response); cartStore.replace(response); val checkoutResponse = repository.checkout(); if (generation != checkoutGeneration) return@launch
                if (checkoutResponse.errors.isNotEmpty()) throw CartException(checkoutResponse.errors.joinToString("\n") { it.message })
                _checkout.value = checkoutResponse; _checkoutError.value = null; _checkoutPhase.value = CheckoutPhase.READY
            } catch (exception: CancellationException) { throw exception } catch (exception: Exception) { _checkoutError.value = exception.toStoreUiError().message; _checkout.value = null; _checkoutPhase.value = CheckoutPhase.FAILED }
            finally { if (generation == checkoutGeneration) _checkoutLoading.value = false }
        }
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
    private var processDeathReconciliationJob: Job? = null
    private var processDeathPaidOrderId: Int? = null
    private val _cardPaymentResult = MutableStateFlow<CardPaymentResult?>(null)
    val cardPaymentResult: StateFlow<CardPaymentResult?> = _cardPaymentResult.asStateFlow()

    private enum class ReconcileOutcome { PAID, CLEARED, PENDING, NO_MARKER }
    private val reconciliationMutex = kotlinx.coroutines.sync.Mutex()

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
                pendingCardPaymentStore.clear()
                lastCheckout = null
                _paymentRedirect.value = null
                if (publishPaidResult) _cardPaymentResult.value = CardPaymentResult(confirmedOrderId, true)
                _checkoutPhase.value = CheckoutPhase.ORDER_CREATED
                cartStore.clearAfterConfirmedPayment()
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

    private fun reconcileAfterProcessDeath() {
        if (lastCheckout == null) return
        processDeathReconciliationJob = viewModelScope.launch {
            val previousOrderId = lastCheckout?.orderId
            val outcome = runCatching {
                reconcileLastCheckout(
                    publishPaidResult = true,
                    preserveMarkerOnExhaustion = false
                )
            }.getOrElse {
                pendingCardPaymentStore.clear()
                lastCheckout = null
                _paymentRedirect.value = null
                ReconcileOutcome.CLEARED
            }
            if (outcome == ReconcileOutcome.PAID) {
                processDeathPaidOrderId = previousOrderId
            }
        }
    }

    fun createOrder(address: CustomerAddress, paymentMethod: String, shippingRateId: String?) {
        val generation = checkoutGeneration
        viewModelScope.launch {
            processDeathReconciliationJob?.join()
            if (processDeathPaidOrderId != null) return@launch
            val quote = _checkout.value
            if (shippingRateId.isNullOrBlank() || paymentMethod.isNullOrBlank()) { _checkoutError.value = "Selecciona una tarifa y un método de pago válidos."; return@launch }
            _checkoutLoading.value = true; _checkoutPhase.value = CheckoutPhase.CREATING_ORDER; _checkoutError.value = null
            try {
                val currentCart = cartStore.cart.value
                val request = CreateOrderRequest(paymentMethod = paymentMethod, billing_address = address, shipping_address = address, shippingRate = shippingRateId, expectedTotal = currentCart.totals.totalPrice, paymentData = emptyMap())
                val response = repository.createCheckout(request); if (generation != checkoutGeneration) return@launch
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
            } catch (exception: Exception) { if (generation != checkoutGeneration) return@launch; _checkoutError.value = exception.toStoreUiError().message; _checkoutPhase.value = CheckoutPhase.FAILED }
            finally { if (generation == checkoutGeneration) _checkoutLoading.value = false }
        }
    }

    fun handleCardPaymentCancelled(orderId: Int) {
        if (lastCheckout?.orderId != orderId) return
        verifyCardPaymentReturn()
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
                if (!isTransientPaymentStatusException(exception)) {
                    pendingCardPaymentStore.clear()
                    lastCheckout = null
                    _paymentRedirect.value = null
                    _checkoutError.value = "No hemos podido comprobar el pago."
                }
            } finally {
                _checkoutLoading.value = false
            }
        }
    }

    fun consumeCardPaymentResult() { _cardPaymentResult.value = null }
    fun consumePaymentRedirect(redirect: PaymentRedirect) { if (_paymentRedirect.value == redirect && redirect.generation == checkoutGeneration) _paymentRedirect.value = null }
    fun consumeBizumOrder() { _bizumOrderId.value = null }
    fun abandonCheckout() = invalidateCheckout()
    private fun invalidateCheckout() { ++checkoutGeneration; checkoutJob?.cancel(); clearCheckoutForNewGeneration(); _checkoutLoading.value = false; _checkoutPhase.value = CheckoutPhase.IDLE }
    private fun clearCheckoutForNewGeneration() { _checkout.value = null; _checkoutError.value = null; _paymentRedirect.value = null; _bizumOrderId.value = null }
    fun openCartLine(item: CartLine) { viewModelScope.launch { val productId = item.parentProductId ?: item.id; _isLoading.value = true; _selectedVariation.value = null; _selectedProduct.value = runCatching { repository.productWithVariationAvailability(productId) }.getOrNull(); _isLoading.value = false } }
    fun clearError() { _error.value = null }
    private fun logShippingResponse(cart: WooCart) { cart.shippingRates.forEach { packageRate -> packageRate.rates.forEach { rate -> Log.d("CriosRangoShipping", "package=${packageRate.packageId} method=${rate.methodId} rate=${rate.rateId} selected=${rate.selected} price=${rate.price} taxes=${rate.taxes}") } }; Log.d("CriosRangoShipping", "totals shipping=${cart.totals.totalShipping} shippingTax=${cart.totals.totalShippingTax} total=${cart.totals.totalPrice}") }

    class Factory(private val repository: StoreRepository, private val cartStore: CartStore, private val deliveryAddressStore: DeliveryAddressStore, private val pendingCardPaymentStore: PendingCardPaymentStore) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ShopViewModel(repository, cartStore, deliveryAddressStore, pendingCardPaymentStore) as T
    }
}
