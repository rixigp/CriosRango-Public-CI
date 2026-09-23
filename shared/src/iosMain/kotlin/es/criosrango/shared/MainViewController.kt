package es.criosrango.shared

import androidx.compose.ui.window.ComposeUIViewController
import es.criosrango.shared.account.AccountRepository
import es.criosrango.shared.api.StoreApiClient
import platform.Foundation.NSURL
import platform.UIKit.UIApplication

private var iosPaymentStore: StorePaymentStore? = null

fun MainViewController() = ComposeUIViewController {
    val storeSession = IosStoreSessionStore()
    val storeApi = StoreApiClient(session = storeSession)
    val cartStore = StoreCartStore(storeApi)
    val accountRepository = AccountRepository(
        tokenStore = IosAccountTokenStore(),
        claimOrderStore = IosClaimOrderStore()
    )
    val pendingStore = IosPendingCardPaymentStore()
    pendingStore.clear()
    val paymentStore = StorePaymentStore(storeApi, cartStore, pendingStore)
    paymentStore.clearForNewProcess()
    iosPaymentStore = paymentStore

    CriosRangoIOSRootScreen(
        storeApi = storeApi,
        accountRepository = accountRepository,
        cartStore = cartStore,
        checkoutStore = StoreCheckoutStore(storeApi, cartStore, accountRepository),
        paymentStore = paymentStore,
        onOpenPayment = ::openIosPaymentUrl
    )
}

fun handleIosPaymentReturn(result: String?, orderId: Int?) {
    iosPaymentStore?.handlePaymentReturn(result, orderId)
}

fun handleIosPaymentForeground() {
    iosPaymentStore?.onForeground()
}

private fun openIosPaymentUrl(url: String) {
    val nsUrl = NSURL(string = url) ?: run {
        iosPaymentStore?.handlePaymentOpenFailure()
        return
    }
    UIApplication.sharedApplication.openURL(
        nsUrl,
        options = emptyMap<Any?, Any?>(),
        completionHandler = { success ->
            if (success) iosPaymentStore?.markPaymentOpened()
            else iosPaymentStore?.handlePaymentOpenFailure()
        }
    )
}
