package es.criosrango.shared

import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.model.StoreCart
import es.criosrango.shared.model.StoreCartLine
import es.criosrango.shared.model.StoreCartRequest
import es.criosrango.shared.model.StoreCartVariation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class StoreCartLoadState { LOADING, SUCCESS_ITEMS, SUCCESS_EMPTY, ERROR }

class StoreCartStore(
    private val api: StoreApiClient,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) {
    private val mutex = Mutex()
    private var hasResolvedCart = false
    private val _cart = MutableStateFlow(StoreCart())
    val cart: StateFlow<StoreCart> = _cart.asStateFlow()
    private val _state = MutableStateFlow(StoreCartLoadState.LOADING)
    val state: StateFlow<StoreCartLoadState> = _state.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _couponLoading = MutableStateFlow(false)
    val couponLoading: StateFlow<Boolean> = _couponLoading.asStateFlow()
    private val _couponError = MutableStateFlow<String?>(null)
    val couponError: StateFlow<String?> = _couponError.asStateFlow()
    private val scope = scope

    fun applyCoupon(code: String) {
        scope.launch {
            mutex.withLock {
                if (_couponLoading.value) return@withLock
                val normalizedCode = code.trim()
                if (normalizedCode.isEmpty()) {
                    _couponError.value = "No se ha podido aplicar este código de descuento."
                    return@withLock
                }
                _couponLoading.value = true
                _couponError.value = null
                runCatching { api.applyCoupon(normalizedCode) }
                    .onSuccess { accept(it) }
                    .onFailure { _couponError.value = it.message?.takeIf(String::isNotBlank) ?: "No se ha podido aplicar este código de descuento." }
                _couponLoading.value = false
            }
        }
    }

    suspend fun applyCouponAwait(code: String): Boolean = mutex.withLock {
        if (_couponLoading.value) return@withLock false
        val normalizedCode = code.trim()
        if (normalizedCode.isEmpty()) {
            _couponError.value = "No se ha podido aplicar este código de descuento."
            return@withLock false
        }
        _couponLoading.value = true
        _couponError.value = null
        val applied = runCatching { api.applyCoupon(normalizedCode) }
            .onSuccess { accept(it) }
            .onFailure { _couponError.value = it.message?.takeIf(String::isNotBlank) ?: "No se ha podido aplicar este código de descuento." }
            .isSuccess
        _couponLoading.value = false
        applied
    }

    fun removeCoupon(code: String) {
        scope.launch {
            mutex.withLock {
                if (_couponLoading.value) return@withLock
                val normalizedCode = code.trim()
                if (normalizedCode.isEmpty()) {
                    _couponError.value = "No se ha podido quitar este código de descuento."
                    return@withLock
                }
                _couponLoading.value = true
                _couponError.value = null
                runCatching { api.removeCoupon(normalizedCode) }
                    .onSuccess { accept(it) }
                    .onFailure { _couponError.value = it.message?.takeIf(String::isNotBlank) ?: "No se ha podido quitar este código de descuento." }
                _couponLoading.value = false
            }
        }
    }

    fun refresh() {
        scope.launch {
            mutex.withLock {
                // Keep a confirmed snapshot visible while refreshing. LOADING is only
                // meaningful before the first successful cart response.
                if (!hasResolvedCart) _state.value = StoreCartLoadState.LOADING
                _error.value = null
                try {
                    accept(api.cart())
                } catch (failure: Throwable) {
                    if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
                    if (!hasResolvedCart) _state.value = StoreCartLoadState.ERROR
                    _error.value = failure.message ?: "No se ha podido recuperar el carrito."
                }
            }
        }
    }

    fun add(productId: Int, quantity: Int = 1, variation: List<StoreCartVariation> = emptyList()) {
        scope.launch {
            mutex.withLock {
                _error.value = null
                runCatching { api.addCartItem(StoreCartRequest(productId, quantity, variation)) }
                    .onSuccess { accept(it) }
                    .onFailure { fail(it) }
            }
        }
    }

    fun update(line: StoreCartLine, quantity: Int) {
        val minimum = line.quantityLimits?.minimum ?: 1
        if (quantity <= 0 || quantity < minimum) {
            remove(line)
            return
        }
        val maximum = line.quantityLimits?.maximum?.takeUnless { it == 9999 }
        if ((quantity - line.quantity) % line.quantityStep() != 0 || (maximum != null && quantity > maximum)) return
        scope.launch {
            mutex.withLock {
                _error.value = null
                runCatching { api.updateCartItem(line.key, quantity) }
                    .onSuccess { accept(it) }
                    .onFailure { fail(it) }
            }
        }
    }

    fun remove(line: StoreCartLine) {
        scope.launch {
            mutex.withLock {
                _error.value = null
                runCatching { api.removeCartItem(line.key) }
                    .onSuccess { accept(it) }
                    .onFailure { fail(it) }
            }
        }
    }

    fun clear() {
        scope.launch {
            mutex.withLock {
                _error.value = null
                runCatching {
                    var current = _cart.value
                    current.items.toList().forEach { line ->
                        current = api.removeCartItem(line.key)
                        accept(current)
                    }
                    current
                }.onFailure { fail(it) }
            }
        }
    }

    private fun accept(cart: StoreCart) {
        hasResolvedCart = true
        _cart.value = cart
        _state.value = if (cart.items.isEmpty()) StoreCartLoadState.SUCCESS_EMPTY else StoreCartLoadState.SUCCESS_ITEMS
        _error.value = null
        _couponError.value = null
    }

    /** Best-effort remote cleanup, followed by unconditional local/session isolation. */
    suspend fun clearForLogoutAwait() {
        try {
            withTimeout(15_000) {
                mutex.withLock {
                    var current = runCatching { api.cart() }.getOrNull() ?: _cart.value
                    current.coupons.toList().forEach { coupon ->
                        current = runCatching { api.removeCoupon(coupon.code) }.getOrDefault(current)
                    }
                    current.items.toList().forEach { line ->
                        current = runCatching { api.removeCartItem(line.key) }.getOrDefault(current)
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            // Remote cleanup timed out; dropping the old token still isolates the next session.
        } finally {
            api.clearSession()
            _cart.value = StoreCart()
            hasResolvedCart = true
            _state.value = StoreCartLoadState.SUCCESS_EMPTY
            _error.value = null
            _couponError.value = null
            _couponLoading.value = false
        }
    }

    fun clearAfterConfirmedPayment() {
        scope.launch {
            try {
                clearAfterConfirmedPaymentAwait()
            } catch (t: Throwable) {
                if (t is CancellationException) throw t

                // La orden ya está creada: la limpieza remota es best-effort.
                resetLocalCartAfterConfirmedPaymentFailure()
            }
        }
    }

    suspend fun clearAfterConfirmedPaymentAwait() {
        mutex.withLock {
            var current = _cart.value
            current.items.toList().forEach { line ->
                current = api.removeCartItem(line.key)
                accept(current)
            }
        }
    }

    private fun resetLocalCartAfterConfirmedPaymentFailure() {
        hasResolvedCart = true
        _cart.value = StoreCart()
        _state.value = StoreCartLoadState.SUCCESS_EMPTY
        _error.value = null
        _couponError.value = null
        _couponLoading.value = false
    }

    suspend fun refreshAwait() {
        mutex.withLock {
            if (!hasResolvedCart) _state.value = StoreCartLoadState.LOADING
            _error.value = null
            try {
                accept(api.cart())
            } catch (failure: Throwable) {
                if (failure is CancellationException && failure !is TimeoutCancellationException) throw failure
                if (!hasResolvedCart) _state.value = StoreCartLoadState.ERROR
                _error.value = failure.message ?: "No se ha podido recuperar el carrito."
                throw failure
            }
        }
    }

    private fun fail(error: Throwable) {
        if (!hasResolvedCart) _state.value = StoreCartLoadState.ERROR
        _error.value = error.message ?: "No se ha podido actualizar el carrito."
    }


}
