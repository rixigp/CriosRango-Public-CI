package es.criosrango.shared

import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.model.StoreCart
import es.criosrango.shared.model.StoreCartLine
import es.criosrango.shared.model.StoreCartRequest
import es.criosrango.shared.model.StoreCartVariation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class StoreCartLoadState { LOADING, SUCCESS_ITEMS, SUCCESS_EMPTY, ERROR }

class StoreCartStore(
    private val api: StoreApiClient,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) {
    private val mutex = Mutex()
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
                    .onFailure {
                        _couponError.value = buildString {
                            append("DBG type=")
                            append(it::class.simpleName ?: "<unknown>")

                            append(" | message=")
                            append(it.message ?: "<null>")

                            append(" | causeType=")
                            append(it.cause?.let { cause ->
                                cause::class.simpleName
                            } ?: "<null>")

                            append(" | causeMessage=")
                            append(it.cause?.message ?: "<null>")
                        }
                    }
                _couponLoading.value = false
            }
        }
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
                _state.value = StoreCartLoadState.LOADING
                _error.value = null
                runCatching { api.cart() }
                    .onSuccess { accept(it) }
                    .onFailure {
                        _state.value = StoreCartLoadState.ERROR
                        _error.value = it.message ?: "No se ha podido recuperar el carrito."
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
        if (quantity <= 0) {
            remove(line)
            return
        }
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
        _cart.value = cart
        _state.value = if (cart.items.isEmpty()) StoreCartLoadState.SUCCESS_EMPTY else StoreCartLoadState.SUCCESS_ITEMS
        _error.value = null
        _couponError.value = null
    }

    fun clearAfterConfirmedPayment() {
        scope.launch {
            clearAfterConfirmedPaymentAwait()
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

    suspend fun refreshAwait() {
        mutex.withLock {
            _state.value = StoreCartLoadState.LOADING
            _error.value = null
            runCatching { api.cart() }
                .onSuccess { accept(it) }
                .onFailure { throw it }
        }
    }

    private fun fail(error: Throwable) {
        _state.value = StoreCartLoadState.ERROR
        _error.value = error.message ?: "No se ha podido actualizar el carrito."
    }


}
