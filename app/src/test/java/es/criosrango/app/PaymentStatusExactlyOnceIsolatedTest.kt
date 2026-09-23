package es.criosrango.app

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
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
        ),
        private val checkoutResponse: CheckoutResponse = CheckoutResponse()
    ) : StoreApi {
        val paymentStatusCalls = AtomicInteger(0)
        val createCheckoutCalls = AtomicInteger(0)
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
        override suspend fun createCheckout(request: CreateOrderRequest): CheckoutResponse {
            createCheckoutCalls.incrementAndGet()
            return checkoutResponse
        }
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

        assertNotNull(
            "PROCESS-DEATH: pendingStore.load() before ViewModel must not be null",
            pendingStore.load()
        )

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
    }

    @Test
    fun multipleResumeEventsWhilePaymentStatusIsInFlight_callPaymentStatusExactlyOnce() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences(
            "j5-lifecycle-" + System.nanoTime(), Context.MODE_PRIVATE
        )
        val pendingStore = PendingCardPaymentStore(preferences)
        val api = CountingStoreApi()

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
    fun processDeathUnpaid_firstTapShowsLoadingAndContinuesWithoutSecondTap() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences(
            "j5-regression-unpaid-" + System.nanoTime(), Context.MODE_PRIVATE
        )
        val pendingStore = PendingCardPaymentStore(preferences)
        check(pendingStore.save(LastCheckout(123, "wc_order_123", "https://criosrango.es/pay/123")))

        val api = CountingStoreApi(
            checkoutResponse = CheckoutResponse(
                orderId = 456,
                orderKey = "wc_order_456",
                redirectUrl = "https://criosrango.es/pay/456"
            )
        )
        api.blockPaymentStatus = true
        val viewModel = newViewModel(context, api, pendingStore)

        idleMainLooperImmediate()
        assertTrue(api.paymentStatusStarted.isCompleted)

        viewModel.createOrder(validAddress(), "cecabank_gateway", "flat_rate:1")

        assertTrue(
            "REGRESSION-UNPAID: checkoutLoading must become true immediately on the first tap",
            viewModel.checkoutLoading.value
        )
        assertEquals(
            "REGRESSION-UNPAID: new checkout must not be sent while reconciliation is in flight",
            0,
            api.createCheckoutCalls.get()
        )

        repeat(5) {
            viewModel.createOrder(validAddress(), "cecabank_gateway", "flat_rate:1")
        }
        assertEquals(
            "REGRESSION-UNPAID: additional taps must not create another order",
            0,
            api.createCheckoutCalls.get()
        )

        api.releasePaymentStatus.complete(Unit)
        repeat(3) { idleMainLooperImmediate() }

        assertEquals(
            "REGRESSION-UNPAID: exactly one new order must be created without another tap",
            1,
            api.createCheckoutCalls.get()
        )
        assertTrue(
            "REGRESSION-UNPAID: checkoutLoading must eventually clear after creating the order",
            !viewModel.checkoutLoading.value
        )
    }

    @Test
    fun processDeathReconciliationStillBlockedAfterFiveSeconds_doesNotCreateOrderAndReleasesGate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences(
            "j5-regression-timeout-" + System.nanoTime(), Context.MODE_PRIVATE
        )
        val pendingStore = PendingCardPaymentStore(preferences)
        check(pendingStore.save(LastCheckout(123, "wc_order_123", "https://criosrango.es/pay/123")))

        val api = CountingStoreApi(
            checkoutResponse = CheckoutResponse(
                orderId = 456,
                orderKey = "wc_order_456",
                redirectUrl = "https://criosrango.es/pay/456"
            )
        )
        api.blockPaymentStatus = true
        val viewModel = newViewModel(context, api, pendingStore)

        idleMainLooperImmediate()
        assertTrue(api.paymentStatusStarted.isCompleted)

        viewModel.createOrder(validAddress(), "cecabank_gateway", "flat_rate:1")
        assertTrue(
            "REGRESSION-TIMEOUT: first tap must enter loading immediately",
            viewModel.checkoutLoading.value
        )

        Thread.sleep(5_200L)
        idleMainLooperImmediate()

        assertEquals(
            "REGRESSION-TIMEOUT: no new order may be created while reconciliation is still unknown",
            0,
            api.createCheckoutCalls.get()
        )
        assertTrue(
            "REGRESSION-TIMEOUT: loading must be false after the 5 second visible wait",
            !viewModel.checkoutLoading.value
        )
        assertEquals(
            "REGRESSION-TIMEOUT: checkout must return to READY",
            CheckoutPhase.READY,
            viewModel.checkoutPhase.value
        )
        assertEquals(
            "REGRESSION-TIMEOUT: short retry message must be visible",
            "Estamos comprobando el pago anterior. Inténtalo de nuevo en unos segundos.",
            viewModel.checkoutError.value
        )

        viewModel.createOrder(validAddress(), "cecabank_gateway", "flat_rate:1")
        assertTrue(
            "REGRESSION-TIMEOUT: gate must be released after the 5 second timeout",
            viewModel.checkoutLoading.value
        )
        assertEquals(
            "REGRESSION-TIMEOUT: retry must still wait for the unresolved reconciliation",
            0,
            api.createCheckoutCalls.get()
        )

        api.releasePaymentStatus.complete(Unit)
        repeat(3) { idleMainLooperImmediate() }
        assertEquals(
            "REGRESSION-TIMEOUT: once reconciliation resolves unpaid, exactly one retry order may be created",
            1,
            api.createCheckoutCalls.get()
        )
    }

    @Test
    fun processDeathPaid_firstTapShowsLoadingAndDoesNotCreateNewOrder() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences(
            "j5-regression-paid-" + System.nanoTime(), Context.MODE_PRIVATE
        )
        val pendingStore = PendingCardPaymentStore(preferences)
        check(pendingStore.save(LastCheckout(123, "wc_order_123", "https://criosrango.es/pay/123")))

        val api = CountingStoreApi(
            orderStatus = OrderStatusResponse(
                id = 123,
                status = "processing",
                paid = true,
                needsPayment = false,
                terminal = false
            )
        )
        api.blockPaymentStatus = true
        val viewModel = newViewModel(context, api, pendingStore)

        idleMainLooperImmediate()
        assertTrue(api.paymentStatusStarted.isCompleted)

        viewModel.createOrder(validAddress(), "cecabank_gateway", "flat_rate:1")

        assertTrue(
            "REGRESSION-PAID: checkoutLoading must become true immediately on the first tap",
            viewModel.checkoutLoading.value
        )
        assertEquals(
            "REGRESSION-PAID: new checkout must not be sent while reconciliation is in flight",
            0,
            api.createCheckoutCalls.get()
        )

        api.releasePaymentStatus.complete(Unit)
        repeat(3) { idleMainLooperImmediate() }

        assertEquals(
            "REGRESSION-PAID: no new order may be created after old payment is PAID",
            0,
            api.createCheckoutCalls.get()
        )
        assertEquals(
            "REGRESSION-PAID: reconciled PAID result must leave the checkout phase coherent",
            CheckoutPhase.ORDER_CREATED,
            viewModel.checkoutPhase.value
        )
        assertTrue(
            "REGRESSION-PAID: checkoutLoading must clear after PAID reconciliation",
            !viewModel.checkoutLoading.value
        )
    }

}
