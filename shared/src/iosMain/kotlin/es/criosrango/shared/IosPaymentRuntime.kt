package es.criosrango.shared

import es.criosrango.shared.api.PaymentCartActions
import es.criosrango.shared.api.PaymentCoordinator
import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.model.PaymentReturn
import es.criosrango.shared.model.PaymentState
import es.criosrango.shared.model.parsePaymentReturn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private val iosPaymentScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
private val iosSession = IosStoreSessionStore()
private val iosApi = StoreApiClient(session = iosSession)
private val iosCoordinator = PaymentCoordinator(
    api = iosApi,
    pendingStore = IosPendingPaymentStore(),
    cartActions = object : PaymentCartActions {
        override suspend fun consumeConfirmedOrder() {
            val cart = iosApi.cart()
            cart.items.forEach { iosApi.removeCartItem(it.key) }
        }
        override suspend fun restoreRemoteAfterUnpaidCheckout() {
            // The iOS host keeps the remote cart untouched here; its local cart
            // layer can supply a snapshot-backed implementation when available.
        }
    },
    scope = iosPaymentScope
)

fun beginIosPayment(orderId: Int, orderKey: String, redirectUrl: String): String =
    iosCoordinator.begin(orderId, orderKey, redirectUrl).url

fun handleIosPaymentCallback(rawUrl: String) {
    when (val parsed = parsePaymentReturn(rawUrl)) {
        PaymentReturn.Unknown -> Unit
        else -> iosCoordinator.handleReturn(parsed)
    }
}

fun verifyIosPendingPayment() {
    iosCoordinator.verify()
}

fun iosPaymentState(): PaymentState? = iosCoordinator.state.value
