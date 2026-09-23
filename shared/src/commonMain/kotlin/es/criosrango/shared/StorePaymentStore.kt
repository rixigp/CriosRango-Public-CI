package es.criosrango.shared

import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.model.CheckoutResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class StorePendingCardPayment(val orderId: Int, val orderKey: String, val paymentUrl: String)

interface PendingCardPaymentStore {
    fun save(payment: StorePendingCardPayment): Boolean
    fun load(): StorePendingCardPayment?
    fun clear(): Boolean
}

enum class StoreCardPaymentState { IDLE, OPENING, WAITING_RETURN, RECONCILING, PAID, NOT_PAID, ERROR }

class StorePaymentStore(
    private val api: StoreApiClient,
    private val cartStore: StoreCartStore,
    private val pendingStore: PendingCardPaymentStore,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) {
    private val scope = scope
    private val reconciliationMutex = Mutex()
    private var attemptActive = false
    private val _state = MutableStateFlow(StoreCardPaymentState.IDLE)
    val state: StateFlow<StoreCardPaymentState> = _state.asStateFlow()
    private val _redirectUrl = MutableStateFlow<String?>(null)
    val redirectUrl: StateFlow<String?> = _redirectUrl.asStateFlow()
    private val _orderId = MutableStateFlow<Int?>(null)
    val orderId: StateFlow<Int?> = _orderId.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun startCardPayment(checkout: CheckoutResponse): String? {
        if (!checkout.paymentMethod.equals("cecabank_gateway", ignoreCase = true)) return null
        val orderId = checkout.orderId ?: return fail("La tienda no ha devuelto el identificador del pedido.")
        val orderKey = checkout.orderKey?.takeIf { it.isNotBlank() } ?: return fail("La tienda no ha devuelto la clave del pedido.")
        val paymentUrl = checkout.redirectUrl
            ?: checkout.paymentResult?.redirectUrl
            ?: checkout.paymentResult?.paymentUrl
            ?: return fail("La tienda no ha devuelto la URL de pago.")
        val pending = StorePendingCardPayment(orderId, orderKey, paymentUrl)
        if (!pendingStore.save(pending)) return fail("No se ha podido guardar el intento de pago.")
        attemptActive = true
        _orderId.value = orderId
        _error.value = null
        _state.value = StoreCardPaymentState.OPENING
        _redirectUrl.value = paymentUrl
        return paymentUrl
    }

    fun markPaymentOpened() {
        if (attemptActive && _state.value == StoreCardPaymentState.OPENING) _state.value = StoreCardPaymentState.WAITING_RETURN
    }

    fun handlePaymentOpenFailure() {
        if (!attemptActive) return
        _redirectUrl.value = null
        _error.value = "No se ha podido abrir la pasarela de pago."
        _state.value = StoreCardPaymentState.ERROR
    }

    fun handlePaymentReturn(result: String?, returnedOrderId: Int?) {
        if (!attemptActive) return
        when (result?.lowercase()) {
            "cancel" -> cancel(returnedOrderId)
            "ok" -> reconcileOnce(returnedOrderId)
            else -> {
                _error.value = "La respuesta de la pasarela no es válida."
                _state.value = StoreCardPaymentState.ERROR
            }
        }
    }

    fun onForeground() {
        if (attemptActive && _state.value == StoreCardPaymentState.WAITING_RETURN) reconcileOnce(null)
    }

    fun retryReconciliation() {
        if (attemptActive && _state.value == StoreCardPaymentState.ERROR) {
            _state.value = StoreCardPaymentState.WAITING_RETURN
            reconcileOnce(null)
        }
    }

    fun cancel(returnedOrderId: Int?) {
        val pending = pendingStore.load() ?: return
        if (returnedOrderId != null && returnedOrderId != pending.orderId) return
        pendingStore.clear()
        attemptActive = false
        _redirectUrl.value = null
        _orderId.value = pending.orderId
        _state.value = StoreCardPaymentState.NOT_PAID
        _error.value = null
        cartStore.refresh()
    }

    fun clearForNewProcess() {
        pendingStore.clear()
        attemptActive = false
        _redirectUrl.value = null
        _orderId.value = null
        _error.value = null
        _state.value = StoreCardPaymentState.IDLE
    }

    private fun reconcileOnce(returnedOrderId: Int?) {
        scope.launch {
            reconciliationMutex.withLock {
                if (!attemptActive || _state.value == StoreCardPaymentState.RECONCILING) return@withLock
                val pending = pendingStore.load() ?: run {
                    attemptActive = false
                    return@withLock
                }
                if (returnedOrderId != null && returnedOrderId != pending.orderId) return@withLock
                _state.value = StoreCardPaymentState.RECONCILING
                _error.value = null
                try {
                    when (
                        reconcileSharedPaymentStatus(
                            maxRetries = 0,
                            delayMs = 0,
                            lookup = { api.paymentStatus(pending.orderId, pending.orderKey) },
                            isTransientException = { true }
                        )
                    ) {
                        SharedPaymentReconciliationResult.PAID -> {
                            pendingStore.clear()
                            attemptActive = false
                            _redirectUrl.value = null
                            _state.value = StoreCardPaymentState.PAID
                            cartStore.clearAfterConfirmedPayment()
                        }
                        SharedPaymentReconciliationResult.TERMINAL_UNPAID -> {
                            pendingStore.clear()
                            attemptActive = false
                            _redirectUrl.value = null
                            _state.value = StoreCardPaymentState.NOT_PAID
                            cartStore.refresh()
                        }
                        SharedPaymentReconciliationResult.EXHAUSTED -> {
                            pendingStore.clear()
                            attemptActive = false
                            _redirectUrl.value = null
                            _state.value = StoreCardPaymentState.ERROR
                            _error.value = "No hemos podido confirmar el pago. Puedes volver a intentarlo."
                            cartStore.refresh()
                        }
                    }
                } catch (t: Throwable) {
                    _state.value = StoreCardPaymentState.ERROR
                    _error.value = t.message ?: "No hemos podido comprobar el pago."
                }
            }
        }
    }

    private fun fail(message: String): String? {
        pendingStore.clear()
        attemptActive = false
        _redirectUrl.value = null
        _error.value = message
        _state.value = StoreCardPaymentState.ERROR
        return null
    }
}
