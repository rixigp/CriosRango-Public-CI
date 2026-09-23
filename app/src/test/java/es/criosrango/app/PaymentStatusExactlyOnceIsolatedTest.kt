package es.criosrango.app

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
        ),
        private val checkoutResponse: CheckoutResponse = CheckoutResponse(),
        private val holdCheckout: Boolean = false
    ) : StoreApi {
        val paymentStatusCalls = AtomicInteger(0)
        val createCheckoutCalls = AtomicInteger(0)
        val cartCalls = AtomicInteger(0)
        val paymentStatusStarted = CompletableDeferred<Unit>()
        val checkoutStarted = CompletableDeferred<Unit>()
        val checkoutRelease = CompletableDeferred<Unit>()

        override suspend fun getOrderStatus(orderId: Int, orderKey: String): OrderStatusResponse {
            paymentStatusCalls.incrementAndGet()
            paymentStatusStarted.complete(Unit)
            return orderStatus
        }

        override suspend fun products(perPage: Int, page: Int, search: String?, category: Int?, orderBy: String?, order: String?, after: String?, featured: Boolean?) = emptyList<StoreProduct>()
        override suspend fun productsByTag(perPage: Int, page: Int, tag: String) = emptyList<StoreProduct>()
        override suspend fun product(id: Int) = StoreProduct(id = id)
        override suspend fun productWithVariationAvailability(id: Int) = StoreProduct(id = id)
        override suspend fun categories(perPage: Int) = emptyList<ProductCategory>()
        override suspend fun cart(): WooCart {
            cartCalls.incrementAndGet()
            return WooCart()
        }
        override suspend fun addCartItem(request: AddCartRequest) = WooCart()
        override suspend fun updateCartItem(key: String, quantity: Int) = WooCart()
        override suspend fun removeCartItem(key: String) = WooCart()
        override suspend fun checkout() = CheckoutResponse()
        override suspend fun createCheckout(request: CreateOrderRequest): CheckoutResponse {
            createCheckoutCalls.incrementAndGet()
            checkoutStarted.complete(Unit)
            if (holdCheckout) checkoutRelease.await()
            return checkoutResponse
        }
        override suspend fun selectShippingRate(request: SelectShippingRateRequest) = WooCart()
        override suspend fun updateCustomer(request: UpdateCustomerRequest) = WooCart()
    }

    private fun newViewModel(context: Context, api: CountingStoreApi, pending: PendingCardPaymentStore): ShopViewModel {
        val preferences = context.getSharedPreferences(
            "j5-simplified-" + System.identityHashCode(api),
            Context.MODE_PRIVATE
        )
        val session = StoreSession(preferences)
        val cartStore = CartStore(api, session, preferences)
        val deliveryAddressStore = DeliveryAddressStore(preferences)
        return ShopViewModel(StoreRepository(api), cartStore, deliveryAddressStore, pending)
    }

    private fun idleMainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    private fun setLastCheckout(viewModel: ShopViewModel, checkout: LastCheckout?) {
        val field = ShopViewModel::class.java.getDeclaredField("lastCheckout")
        field.isAccessible = true
        field.set(viewModel, checkout)
    }

    private fun validAddress() = CustomerAddress(
        firstName = "Test",
        lastName = "User",
        email = "test@example.com",
        phone = "600000000",
        address1 = "Calle Test 1",
        postcode = "28001",
        city = "Madrid",
        state = "M",
        country = "ES"
    )

    @Test
    fun pendingMarkerAtStartup_isCleared_withoutPaymentStatus() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("j5-startup-" + System.nanoTime(), Context.MODE_PRIVATE)
        val pendingStore = PendingCardPaymentStore(preferences)
        assertTrue(pendingStore.save(LastCheckout(123, "wc_order_123", "https://criosrango.es/pay/123")))

        val api = CountingStoreApi()
        newViewModel(context, api, pendingStore)
        idleMainLooper()

        assertNull(pendingStore.load())
        assertEquals(0, api.paymentStatusCalls.get())
    }

    @Test
    fun firstCreateOrder_afterProcessDeathStartsImmediately_withoutReconciliation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("j5-create-" + System.nanoTime(), Context.MODE_PRIVATE)
        val pendingStore = PendingCardPaymentStore(preferences)
        assertTrue(pendingStore.save(LastCheckout(123, "wc_order_123", "https://criosrango.es/pay/123")))

        val api = CountingStoreApi(
            checkoutResponse = CheckoutResponse(
                orderId = 456,
                orderKey = "wc_order_456",
                redirectUrl = "https://criosrango.es/pay/456"
            ),
            holdCheckout = true
        )
        val viewModel = newViewModel(context, api, pendingStore)
        idleMainLooper()

        viewModel.createOrder(validAddress(), "cecabank_gateway", "flat_rate:1")

        assertTrue(viewModel.checkoutLoading.value)
        assertEquals(0, api.paymentStatusCalls.get())

        repeat(5) {
            viewModel.createOrder(validAddress(), "cecabank_gateway", "flat_rate:1")
        }
        idleMainLooper()

        assertEquals(1, api.createCheckoutCalls.get())
        assertEquals(0, api.paymentStatusCalls.get())
        assertTrue(viewModel.checkoutLoading.value)

        api.checkoutRelease.complete(Unit)
        idleMainLooper()
        assertFalse(viewModel.checkoutLoading.value)
    }

    @Test
    fun multipleTaps_createExactlyOneOrder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("j5-multitap-" + System.nanoTime(), Context.MODE_PRIVATE)
        val pendingStore = PendingCardPaymentStore(preferences)
        val api = CountingStoreApi(
            checkoutResponse = CheckoutResponse(
                orderId = 456,
                orderKey = "wc_order_456",
                redirectUrl = "https://criosrango.es/pay/456"
            ),
            holdCheckout = true
        )
        val viewModel = newViewModel(context, api, pendingStore)
        idleMainLooper()

        repeat(10) {
            viewModel.createOrder(validAddress(), "cecabank_gateway", "flat_rate:1")
        }
        idleMainLooper()

        assertEquals(1, api.createCheckoutCalls.get())
        api.checkoutRelease.complete(Unit)
        idleMainLooper()
        assertFalse(viewModel.checkoutLoading.value)
    }

    @Test
    fun normalCecabankReturn_stillUsesPaymentStatus() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("j5-return-" + System.nanoTime(), Context.MODE_PRIVATE)
        val pendingStore = PendingCardPaymentStore(preferences)
        val api = CountingStoreApi(
            orderStatus = OrderStatusResponse(
                id = 123, status = "processing", paid = true, needsPayment = false, terminal = true
            )
        )
        val viewModel = newViewModel(context, api, pendingStore)
        idleMainLooper()

        val pending = LastCheckout(123, "wc_order_123", "https://criosrango.es/pay/123")
        assertTrue(pendingStore.save(pending))
        setLastCheckout(viewModel, pending)

        viewModel.verifyCardPaymentReturn()
        idleMainLooper()

        assertEquals(1, api.paymentStatusCalls.get())
        assertEquals(CheckoutPhase.ORDER_CREATED, viewModel.checkoutPhase.value)
        assertTrue(viewModel.cardPaymentResult.value?.paid == true)
        assertNull(pendingStore.load())
    }

    @Test
    fun normalCecabankCancellation_clearsPendingAndKeepsCartUsable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("j5-cancel-" + System.nanoTime(), Context.MODE_PRIVATE)
        val pendingStore = PendingCardPaymentStore(preferences)
        val api = CountingStoreApi()
        val viewModel = newViewModel(context, api, pendingStore)
        idleMainLooper()

        val pending = LastCheckout(123, "wc_order_123", "https://criosrango.es/pay/123")
        assertTrue(pendingStore.save(pending))
        setLastCheckout(viewModel, pending)

        viewModel.handleCardPaymentCancelled(123)
        idleMainLooper()

        assertNull(pendingStore.load())
        assertEquals(0, api.paymentStatusCalls.get())
        assertEquals(CardPaymentResult(123, false), viewModel.cardPaymentResult.value)
        assertEquals(CheckoutPhase.IDLE, viewModel.checkoutPhase.value)
        assertTrue(api.cartCalls.get() >= 1)
    }
}
