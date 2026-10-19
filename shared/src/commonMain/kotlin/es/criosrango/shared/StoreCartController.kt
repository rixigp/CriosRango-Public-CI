package es.criosrango.shared

import es.criosrango.shared.model.StoreCart
import es.criosrango.shared.model.StoreCartLine
import es.criosrango.shared.model.StoreCartVariation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Immutable UI snapshot; both platforms render this same state. */
data class StoreCartState(
    val cart: StoreCart = StoreCart(),
    val loadState: StoreCartLoadState = StoreCartLoadState.LOADING,
    val error: String? = null,
    val couponLoading: Boolean = false,
    val couponError: String? = null
)

/** Shared UI-facing owner for cart state and mutations. The API/session store remains the transport adapter. */
class StoreCartController(
    internal val store: StoreCartStore,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) {
    val state: StateFlow<StoreCartState> = combine(
        store.cart,
        store.state,
        store.error,
        store.couponLoading,
        store.couponError
    ) { cart, loadState, error, couponLoading, couponError ->
        StoreCartState(cart, loadState, error, couponLoading, couponError)
    }.stateIn(scope, SharingStarted.Eagerly, StoreCartState())

    fun refresh() = store.refresh()
    fun add(productId: Int, quantity: Int = 1, variation: List<StoreCartVariation> = emptyList()) =
        store.add(productId, quantity, variation)
    fun update(line: StoreCartLine, quantity: Int) = store.update(line, quantity)
    fun remove(line: StoreCartLine) = store.remove(line)
    fun clear() = store.clear()
    fun applyCoupon(code: String) = store.applyCoupon(code)
    fun removeCoupon(code: String) = store.removeCoupon(code)
}
