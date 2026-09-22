package es.criosrango.app

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

@RunWith(RobolectricTestRunner::class)
class PaymentStatusExactlyOnceIsolatedTest {
    private class CountingStoreApi(
        private val orderStatus: OrderStatusResponse = OrderStatusResponse(
            id = 123, status = "cancelled", paid = false, needsPayment = false, terminal = true
        )
    ) : StoreApi {
        val paymentStatusCalls = AtomicInteger(0)
        val paymentStatusStarted = CompletableDeferred<Unit>()
        val releasePaymentStatus = CompletableDeferred<Unit>()
        var blockPaymentStatus = false

        override suspend fun getOrderStatus(orderId: Int, orderKey: String): OrderStatusResponse {
            paymentStatusCalls.incrementAndGet()
            paymentStatusStarted.complete(Unit)
            if (blockPaymentStatus) releasePaymentStatus.await()
            return orderStatus
        }

        override suspend fun products(perPage: Int, page: Int, search: String?, category: Int?, orderBy: String?, order: String?, after: String?, featured: Boolean?) = emptyList<StoreProduct>()
        override suspend fun productsByTag(perPage: Int, page: Int, tag: String) = emptyList<StoreProduct>()
        override suspend fun product(id: Int) = StoreProduct(id = id)
        override suspend fun productWithVariationAvailability(id: Int) = StoreProduct(id = id)
        override suspend fun categories(perPage: Int) = emptyList<ProductCategory>()
        override suspend fun cart() = WooCart()
        override suspend fun addCartItem(request: AddCartRequest) = WooCart()
        override suspend fun updateCartItem(key: String, quantity: Int) = WooCart()
        override suspend fun removeCartItem(key: String) = WooCart()
        override suspend fun checkout() = CheckoutResponse()
        override suspend fun createCheckout(request: CreateOrderRequest) = CheckoutResponse()
        override suspend fun selectShippingRate(request: SelectShippingRateRequest) = WooCart()
        override suspend fun updateCustomer(request: UpdateCustomerRequest) = WooCart()
    }

    private fun newViewModel(context: Context, api: CountingStoreApi, pending: PendingCardPaymentStore): ShopViewModel {
        val preferences = context.getSharedPreferences(
            "j5-exactly-once-isolated-" + System.identityHashCode(api),
            Context.MODE_PRIVATE
        )
        val session = StoreSession(preferences)
        val cartStore = CartStore(api, session, preferences)
        val deliveryAddressStore = DeliveryAddressStore(preferences)
        return ShopViewModel(StoreRepository(api), cartStore, deliveryAddressStore, pending)
    }

    private fun idleMainLooper() {
        val shadow = Shadows.shadowOf(Looper.getMainLooper())
        shadow.idle()
        shadow.runToEndOfTasks()
    }

    private fun idleMainLooperImmediate() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun processDeathWithPendingPayment_callsPaymentStatusExactlyOnce() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences(
            "j5-process-death-" + System.nanoTime(), Context.MODE_PRIVATE
        )
        val pendingStore = PendingCardPaymentStore(preferences)
        check(pendingStore.save(LastCheckout(123, "wc_order_123", "https://criosrango.es/pay/123")))

        assertNotNull("PROCESS-DEATH: pendingStore.load() before ViewModel must not be null", pendingStore.load())

        val api = CountingStoreApi()
        api.blockPaymentStatus = true
        val viewModel = newViewModel(context, api, pendingStore)

        idleMainLooperImmediate()

        assertTrue(
            "PROCESS-DEATH: paymentStatusStarted must be completed",
            api.paymentStatusStarted.isCompleted
        )
        assertEquals(
            "PROCESS-DEATH: paymentStatusCalls must equal 1 while paymentStatus is blocked",
            1,
            api.paymentStatusCalls.get()
        )

        repeat(5) {
            idleMainLooperImmediate()
        }

        assertEquals(
            "PROCESS-DEATH: paymentStatusCalls must remain 1 while paymentStatus is blocked",
            1,
            api.paymentStatusCalls.get()
        )

        api.releasePaymentStatus.complete(Unit)
        idleMainLooperImmediate()

        assertEquals(
            "PROCESS-DEATH: paymentStatusCalls must equal 1 after terminal unpaid cleanup",
            1,
            api.paymentStatusCalls.get()
        )
        assertEquals(
            "PROCESS-DEATH: pendingStore.load() must be null after TERMINAL_UNPAID",
            null,
            pendingStore.load()
        )

        val lastCheckoutField = ShopViewModel::class.java.getDeclaredField("lastCheckout")
        lastCheckoutField.isAccessible = true
        assertEquals(
            "PROCESS-DEATH: lastCheckout must be null after TERMINAL_UNPAID",
            null,
            lastCheckoutField.get(viewModel)
        )

        val processDeathJobField = ShopViewModel::class.java.getDeclaredField("processDeathReconciliationJob")
        processDeathJobField.isAccessible = true
        val job = processDeathJobField.get(viewModel) as Job
        assertTrue(
            "PROCESS-DEATH: reconciliation Job must be completed",
            job.isCompleted
        )
        assertTrue(
            "PROCESS-DEATH: reconciliation Job must not be cancelled",
            !job.isCancelled
        )
    }

    @Test
    fun multipleResumeEventsWhilePaymentStatusIsInFlight_callPaymentStatusExactlyOnce() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences(
            "j5-lifecycle-" + System.nanoTime(), Context.MODE_PRIVATE
        )
        val pendingStore = PendingCardPaymentStore(preferences)
        val api = CountingStoreApi()

        // J.5 diagnostic: let the empty-marker init path finish before
        // injecting the lifecycle test marker.
        val viewModel = newViewModel(context, api, pendingStore)
        idleMainLooperImmediate()

        val field = ShopViewModel::class.java.getDeclaredField("lastCheckout")
        field.isAccessible = true
        field.set(viewModel, LastCheckout(123, "wc_order_123", "https://criosrango.es/pay/123"))

        api.blockPaymentStatus = true
        viewModel.verifyCardPaymentReturn()
        idleMainLooperImmediate()
        assertTrue(
            "LIFECYCLE: paymentStatusStarted must be completed",
            api.paymentStatusStarted.isCompleted
        )
        assertTrue(
            "LIFECYCLE: checkoutLoading must remain true while paymentStatus is blocked",
            viewModel.checkoutLoading.value
        )
        assertEquals(
            "LIFECYCLE: paymentStatusCalls must equal 1 before resumes",
            1,
            api.paymentStatusCalls.get()
        )

        repeat(5) {
            viewModel.verifyCardPaymentReturn()
        }

        assertEquals(
            "LIFECYCLE: paymentStatusCalls must remain 1 after resumes",
            1,
            api.paymentStatusCalls.get()
        )
        assertTrue(
            "LIFECYCLE: checkoutLoading must remain true before release",
            viewModel.checkoutLoading.value
        )

        api.releasePaymentStatus.complete(Unit)
        idleMainLooperImmediate()

        assertEquals(
            "LIFECYCLE: paymentStatusCalls must equal 1 after release",
            1,
            api.paymentStatusCalls.get()
        )
    }
}