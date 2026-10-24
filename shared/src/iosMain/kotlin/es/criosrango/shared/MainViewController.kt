package es.criosrango.shared

import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.runtime.CompositionLocalProvider
import es.criosrango.shared.account.AccountRepository
import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.loyalty.LoyaltyRepository
import platform.Foundation.NSURL
import platform.Foundation.NSBundle
import platform.Foundation.NSSelectorFromString
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerDelegateProtocol
import platform.UIKit.UIGestureRecognizerStateEnded
import platform.UIKit.UIRectEdgeLeft
import platform.UIKit.UIScreenEdgePanGestureRecognizer
import kotlinx.cinterop.StableRef
import kotlinx.cinterop.asCPointer
import platform.darwin.NSObject
import platform.darwin.ObjCAction
import platform.darwin.OBJC_ASSOCIATION_RETAIN_NONATOMIC
import platform.darwin.objc_setAssociatedObject

private var iosPaymentStore: StorePaymentStore? = null
private var iosRootViewController: UIViewController? = null

// The key is process-lifetime metadata only; the associated target itself is retained by each controller.
private val edgeBackTargetAssociationKey = StableRef.create(Unit).asCPointer()

private class IosEdgeBackTarget(private val coordinator: IosBackCoordinator) : NSObject(), UIGestureRecognizerDelegateProtocol {
    @ObjCAction fun handleEdgePan(recognizer: UIScreenEdgePanGestureRecognizer) {
        if (recognizer.state != UIGestureRecognizerStateEnded) return
        if (recognizer.translationInView(recognizer.view).x >= 72.0) coordinator.dispatchBack()
    }
    override fun gestureRecognizer(gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWithGestureRecognizer: UIGestureRecognizer): Boolean = true
}
private val iosPushNavigation = androidx.compose.runtime.mutableStateOf<IosPushNavigation?>(null)

fun MainViewController() : UIViewController {
    val storeSession = IosStoreSessionStore()
    val accountTokenStore = IosAccountTokenStore()
    val storeApi = StoreApiClient(
        session = storeSession,
        accountTokenStore = accountTokenStore
    )
    val cartStore = StoreCartStore(storeApi)
    val accountRepository = AccountRepository(
        tokenStore = accountTokenStore,
        claimOrderStore = IosClaimOrderStore()
    )
    val loyaltyRepository = LoyaltyRepository(accountTokenStore)
    val pendingStore = IosPendingCardPaymentStore()
    // Create and publish the payment store before UIKit can deliver a cold-start URL.
    // StorePaymentStore restores the durable pending state during construction.
    val paymentStore = StorePaymentStore(storeApi, cartStore, pendingStore)
    iosPaymentStore = paymentStore

    val backCoordinator = IosBackCoordinator()
    val controller = ComposeUIViewController {
        CompositionLocalProvider(LocalIosBackCoordinator provides backCoordinator) {
        CriosRangoIOSRootScreen(
        storeApi = storeApi,
        accountRepository = accountRepository,
         accountTokenStore = accountTokenStore,
         accountAppVersion = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "",
         loyaltyRepository = loyaltyRepository,
        cartStore = cartStore,
        checkoutStore = StoreCheckoutStore(storeApi, cartStore, accountRepository),
        paymentStore = paymentStore,
        onOpenPayment = ::openIosPaymentUrl,
        onOpenExternalUrl = ::openIosExternalUrl,
        onShareProductText = ::shareIosProductText,
        onReserveProductMessage = ::openIosReserveMessage,
            pushNavigation = iosPushNavigation.value,
            onPushNavigationConsumed = { iosPushNavigation.value = null }
        )
        }
    }
    val edgeBackTarget = IosEdgeBackTarget(backCoordinator)
    // Strongly tie the target to this controller's lifetime instead of relying on target/action retention.
    objc_setAssociatedObject(controller, edgeBackTargetAssociationKey, edgeBackTarget, OBJC_ASSOCIATION_RETAIN_NONATOMIC)
    val edgeBackRecognizer = UIScreenEdgePanGestureRecognizer(target = edgeBackTarget, action = NSSelectorFromString("handleEdgePan:"))
    edgeBackRecognizer.edges = UIRectEdgeLeft
    edgeBackRecognizer.delegate = edgeBackTarget
    controller.view.addGestureRecognizer(edgeBackRecognizer)
    iosRootViewController = controller
    return controller
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


private fun shareIosProductText(text: String) {
    val controller = iosRootViewController ?: return
    val activityController = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
    controller.presentViewController(activityController, animated = true, completion = null)
}

private fun openIosReserveMessage(message: String) {
    val encoded = message.encodeToByteArray().joinToString("") { byte ->
        val value = byte.toInt() and 0xFF
        val character = value.toChar()
        val allowed = value in 0x30..0x39 || value in 0x41..0x5A || value in 0x61..0x7A || character in "_-!.~'()*"
        if (allowed) character.toString() else "%" + value.toString(16).uppercase().padStart(2, '0')
    }
    openIosExternalUrl("https://wa.me/34633246788?text=$encoded")
}

fun openIosExternalUrl(url: String) {
    val nsUrl = NSURL(string = url) ?: return
    UIApplication.sharedApplication.openURL(
        nsUrl,
        options = emptyMap<Any?, Any?>(),
        completionHandler = null
    )
}

fun handleIosPushNotification(type: String?, orderId: Int) {
    val t = type?.lowercase() ?: return
    if (t == PushNotificationType.NEW_PRODUCTS || t == PushNotificationType.ORDER_STATUS || t == PushNotificationType.BIRTHDAY_COUPON) iosPushNavigation.value = IosPushNavigation(t, orderId.takeIf { it > 0 }?.toInt())
}
