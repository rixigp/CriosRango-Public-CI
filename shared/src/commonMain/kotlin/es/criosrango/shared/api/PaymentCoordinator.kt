package es.criosrango.shared.api

import es.criosrango.shared.model.PaymentReturn
import es.criosrango.shared.model.PaymentState
import es.criosrango.shared.model.PendingPayment
import es.criosrango.shared.model.classifyPaymentStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

interface PaymentStatusProvider {
    suspend fun paymentStatus(orderId: Int, orderKey: String): es.criosrango.shared.model.OrderStatusResponse
}
interface PaymentCartActions {
    suspend fun consumeConfirmedOrder()
    suspend fun restoreRemoteAfterUnpaidCheckout()
}
data class PaymentRedirect(val orderId: Int, val url: String)

class PaymentCoordinator(
    private val api: PaymentStatusProvider,
    private val pendingStore: PendingPaymentStore,
    private val cartActions: PaymentCartActions,
    private val scope: CoroutineScope,
    private val maxAttempts: Int = 10,
    private val delayMillis: Long = 1500L
) {
    private val _state = MutableStateFlow<PaymentState?>(null)
    val state: StateFlow<PaymentState?> = _state.asStateFlow()
    private var verificationJob: Job? = null

    fun restorePending(): PendingPayment? = pendingStore.load()

    fun begin(orderId: Int, orderKey: String, redirectUrl: String): PaymentRedirect {
        pendingStore.save(PendingPayment(orderId, orderKey))
        _state.value = PaymentState.PENDING
        return PaymentRedirect(orderId, redirectUrl)
    }

    fun cancel(orderId: Int) {
        verificationJob?.cancel()
        pendingStore.clear()
        _state.value = PaymentState.CANCELLED
        scope.launch { cartActions.restoreRemoteAfterUnpaidCheckout() }
    }

    fun handleReturn(returnValue: PaymentReturn) {
        when (returnValue) {
            is PaymentReturn.Ok -> verify(returnValue.orderId)
            is PaymentReturn.Cancelled -> {
                val id = returnValue.orderId ?: pendingStore.load()?.orderId
                if (id != null) cancel(id) else _state.value = PaymentState.CANCELLED
            }
            PaymentReturn.Unknown -> Unit
        }
    }

    fun verify(orderIdHint: Int? = null) {
        if (verificationJob?.isActive == true) return
        val pending = pendingStore.load()
        val orderId = orderIdHint ?: pending?.orderId ?: return
        val orderKey = pending?.orderKey ?: return
        verificationJob = scope.launch {
            try {
                var attempts = 0
                while (true) {
                    val response = api.paymentStatus(orderId, orderKey)
                    when (classifyPaymentStatus(response)) {
                        es.criosrango.shared.model.PaymentVerificationResult.PAID -> {
                            cartActions.consumeConfirmedOrder()
                            pendingStore.clear()
                            _state.value = PaymentState.PAID
                            return@launch
                        }
                        es.criosrango.shared.model.PaymentVerificationResult.FAILED -> {
                            cartActions.restoreRemoteAfterUnpaidCheckout()
                            pendingStore.clear()
                            _state.value = PaymentState.FAILED
                            return@launch
                        }
                        es.criosrango.shared.model.PaymentVerificationResult.PENDING -> {
                            if (attempts >= maxAttempts) {
                                _state.value = PaymentState.PENDING
                                return@launch
                            }
                        }
                        es.criosrango.shared.model.PaymentVerificationResult.ERROR -> {
                            _state.value = PaymentState.UNKNOWN_ERROR
                            return@launch
                        }
                    }
                    attempts++
                    delay(delayMillis)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _state.value = PaymentState.UNKNOWN_ERROR
            }
        }
    }

    fun cancelVerification() {
        verificationJob?.cancel()
        verificationJob = null
    }
}
