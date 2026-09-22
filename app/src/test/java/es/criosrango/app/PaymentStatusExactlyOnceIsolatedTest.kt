package es.criosrango.app

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
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

    @Test
    fun processDeathWithPendingPayment_callsPaymentStatusExactlyOnce() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences(
            "j5-process-death-" + System.nanoTime(), Context.MODE_PRIVATE
        )
        val pendingStore = PendingCardPaymentStore(preferences)
        check(pendingStore.save(LastCheckout(123, "wc_order_123", "https://criosrango.es/pay/123")))

        val api = CountingStoreApi()
        newViewModel(context, api, pendingStore)

        idleMainLooper()

        assertTrue(api.paymentStatusCalls.get() > 0)
        assertEquals(1, api.paymentStatusCalls.get())
    }

    @Test
    fun multipleResumeEventsWhilePaymentStatusIsInFlight_callPaymentStatusExactlyOnce() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences(
            "j5-lifecycle-" + System.nanoTime(), Context.MODE_PRIVATE
        )
        val pendingStore = PendingCardPaymentStore(preferences)
        val api = CountingStoreApi()
        api.blockPaymentStatus = true

        val viewModel = newViewModel(context, api, pendingStore)

        val field = ShopViewModel::class.java.getDeclaredField("lastCheckout")
        field.isAccessible = true
        field.set(viewModel, LastCheckout(123, "wc_order_123", "https://criosrango.es/pay/123"))

        viewModel.verifyCardPaymentReturn()
        idleMainLooper()
        check(api.paymentStatusStarted.isCompleted)

        repeat(5) {
            viewModel.verifyCardPaymentReturn()
        }

        assertTrue(api.paymentStatusCalls.get() > 0)
        assertEquals(1, api.paymentStatusCalls.get())

        api.releasePaymentStatus.complete(Unit)
        idleMainLooper()

        assertEquals(1, api.paymentStatusCalls.get())
    }
}