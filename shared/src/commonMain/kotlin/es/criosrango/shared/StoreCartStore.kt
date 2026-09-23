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
    private val scope = scope

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

    private fun accept(cart: StoreCart) {
        _cart.value = cart
        _state.value = if (cart.items.isEmpty()) StoreCartLoadState.SUCCESS_EMPTY else StoreCartLoadState.SUCCESS_ITEMS
        _error.value = null
    }

    private fun fail(error: Throwable) {
        _state.value = StoreCartLoadState.ERROR
        _error.value = error.message ?: "No se ha podido actualizar el carrito."
    }
}
