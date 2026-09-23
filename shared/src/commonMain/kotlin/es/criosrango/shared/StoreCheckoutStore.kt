package es.criosrango.shared

import es.criosrango.shared.account.AccountCustomerAddress
import es.criosrango.shared.account.AccountRepository
import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.model.CheckoutResponse
import es.criosrango.shared.model.CreateOrderRequest
import es.criosrango.shared.model.CustomerAddress
import es.criosrango.shared.model.SelectShippingRateRequest
import es.criosrango.shared.model.StoreCart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class StoreCheckoutPhase { IDLE, LOADING, READY, CREATING_ORDER, ORDER_CREATED, ERROR }

class StoreCheckoutSubmissionGate {
    private val mutex = Mutex()
    private var acquired = false

    suspend fun tryAcquire(): Boolean = mutex.withLock {
        if (acquired) false else {
            acquired = true
            true
        }
    }

    suspend fun release() {
        mutex.withLock { acquired = false }
    }
}

class StoreCheckoutStore(
    private val api: StoreApiClient,
    private val cartStore: StoreCartStore,
    private val accountRepository: AccountRepository,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) {
    private val scope = scope
    private val mutex = Mutex()
    private val submissionGate = StoreCheckoutSubmissionGate()

    private val _checkout = MutableStateFlow<CheckoutResponse?>(null)
    val checkout: StateFlow<CheckoutResponse?> = _checkout.asStateFlow()
    private val _cart = MutableStateFlow(StoreCart())
    val cart: StateFlow<StoreCart> = _cart.asStateFlow()
    private val _phase = MutableStateFlow(StoreCheckoutPhase.IDLE)
    val phase: StateFlow<StoreCheckoutPhase> = _phase.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _accountAddress = MutableStateFlow<CustomerAddress?>(null)
    val accountAddress: StateFlow<CustomerAddress?> = _accountAddress.asStateFlow()
    private val _createdOrder = MutableStateFlow<CheckoutResponse?>(null)
    val createdOrder: StateFlow<CheckoutResponse?> = _createdOrder.asStateFlow()

    fun load() {
        scope.launch {
            mutex.withLock {
                _phase.value = StoreCheckoutPhase.LOADING
                _error.value = null
                runCatching {
                    val address = if (accountRepository.hasSession) {
                        runCatching { accountRepository.customerAddress().toCustomerAddress() }.getOrNull()
                    } else null
                    _accountAddress.value = address
                    val response = api.checkout()
                    cartStore.refresh()
                    response
                }.onSuccess {
                    _checkout.value = it
                    _cart.value = cartStore.cart.value
                    _phase.value = StoreCheckoutPhase.READY
                }.onFailure { fail(it) }
            }
        }
    }

    fun updateCustomer(address: CustomerAddress) {
        scope.launch {
            mutex.withLock {
                _phase.value = StoreCheckoutPhase.LOADING
                _error.value = null
                runCatching {
                    if (accountRepository.hasSession) {
                        runCatching {
                            accountRepository.saveCustomerAddress(address.toAccountCustomerAddress())
                        }
                    }
                    api.updateCustomer(es.criosrango.shared.model.UpdateCustomerRequest(address, address))
                    api.checkout()
                }.onSuccess {
                    _checkout.value = it
                    _cart.value = cartStore.cart.value
                    _phase.value = StoreCheckoutPhase.READY
                }.onFailure { fail(it) }
            }
        }
    }

    fun selectShipping(packageId: Int, rateId: String) {
        scope.launch {
            mutex.withLock {
                _phase.value = StoreCheckoutPhase.LOADING
                _error.value = null
                runCatching {
                    api.selectShippingRate(SelectShippingRateRequest(packageId, rateId))
                    api.checkout()
                }.onSuccess {
                    _cart.value = cartStore.cart.value
                    _checkout.value = it
                    _phase.value = StoreCheckoutPhase.READY
                }.onFailure { fail(it) }
            }
        }
    }

    fun createOrder(address: CustomerAddress, paymentMethod: String, shippingRateId: String) {
        if (paymentMethod.isBlank() || shippingRateId.isBlank()) {
            _error.value = "Selecciona una tarifa de envío y un método de pago."
            _phase.value = StoreCheckoutPhase.ERROR
            return
        }
        scope.launch {
            if (!submissionGate.tryAcquire()) return@launch
            try {
                mutex.withLock {
                    _phase.value = StoreCheckoutPhase.CREATING_ORDER
                    _error.value = null
                    val currentCart = cartStore.cart.value
                    val response = api.createCheckout(
                        CreateOrderRequest(
                            paymentMethod = paymentMethod,
                            billingAddress = address,
                            shippingAddress = address,
                            shippingRate = shippingRateId,
                            expectedTotal = currentCart.totals.totalPrice
                        )
                    )
                    if (response.errors.isNotEmpty()) {
                        throw IllegalStateException(response.errors.joinToString("\n") { it.message })
                    }
                    if (response.orderId == null) {
                        throw IllegalStateException("La tienda no ha confirmado la creación del pedido.")
                    }
                    _checkout.value = response
                    _createdOrder.value = response
                    _phase.value = StoreCheckoutPhase.ORDER_CREATED
                }
            } catch (t: Throwable) {
                _error.value = t.message ?: "No se ha podido crear el pedido."
                _phase.value = StoreCheckoutPhase.ERROR
            } finally {
                submissionGate.release()
            }
        }
    }

    fun clearCreatedOrder() { _createdOrder.value = null }

    private fun fail(t: Throwable) {
        _error.value = t.message ?: "No se ha podido cargar el checkout."
        _phase.value = StoreCheckoutPhase.ERROR
    }

    private fun AccountCustomerAddress.toCustomerAddress() = CustomerAddress(
        firstName, lastName, email, phone, address1, address2, postcode, city, state, country.ifBlank { "ES" }
    )

    private fun CustomerAddress.toAccountCustomerAddress() = AccountCustomerAddress(
        firstName, lastName, email, phone, address1, address2, postcode, city, state, country
    )
}
