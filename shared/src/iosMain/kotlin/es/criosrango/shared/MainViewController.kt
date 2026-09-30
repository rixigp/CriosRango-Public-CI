package es.criosrango.shared

import androidx.compose.ui.window.ComposeUIViewController
import es.criosrango.shared.account.AccountRepository
import es.criosrango.shared.api.StoreApiClient
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController

private var iosPaymentStore: StorePaymentStore? = null

fun MainViewController() : UIViewController {
    val storeSession = IosStoreSessionStore()
    val storeApi = StoreApiClient(session = storeSession)
    val cartStore = StoreCartStore(storeApi)
    val accountRepository = AccountRepository(
        tokenStore = IosAccountTokenStore(),
        claimOrderStore = IosClaimOrderStore()
    )
    val pendingStore = IosPendingCardPaymentStore()
    // Create and publish the payment store before UIKit can deliver a cold-start URL.
    // StorePaymentStore restores the durable pending state during construction.
    val paymentStore = StorePaymentStore(storeApi, cartStore, pendingStore)
    iosPaymentStore = paymentStore

    return ComposeUIViewController {
        CriosRangoIOSRootScreen(
        storeApi = storeApi,
        accountRepository = accountRepository,
        cartStore = cartStore,
        checkoutStore = StoreCheckoutStore(storeApi, cartStore, accountRepository),
        paymentStore = paymentStore,
        onOpenPayment = ::openIosPaymentUrl,
        onOpenExternalUrl = ::openIosExternalUrl
        )
    }
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


fun openIosExternalUrl(url: String) {
    val nsUrl = NSURL(string = url) ?: return
    UIApplication.sharedApplication.openURL(
        nsUrl,
        options = emptyMap<Any?, Any?>(),
        completionHandler = null
    )
}
