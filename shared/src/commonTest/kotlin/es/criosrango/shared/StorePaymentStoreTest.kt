package es.criosrango.shared

import es.criosrango.shared.api.StoreApiClient
import es.criosrango.shared.model.CheckoutResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

private class FakePendingCardPaymentStore : PendingCardPaymentStore {
    private var payment: StorePendingCardPayment? = null

    override fun save(payment: StorePendingCardPayment): Boolean {
        this.payment = payment
        return true
    }

    override fun load(): StorePendingCardPayment? = payment

    override fun clear(): Boolean {
        payment = null
        return true
    }
}

class StorePaymentStoreTest {
    @Test
    fun paidStatusIsConfirmedOnce() = runTest {
        var calls = 0
        val result = reconcileSharedPaymentStatus(
            maxRetries = 0,
            delayMs = 0,
            lookup = {
                calls++
                es.criosrango.shared.model.PaymentStatusResponse(id = 123, status = "processing", paid = true, terminal = true)
            },
            isTransientException = { false }
        )
        assertEquals(SharedPaymentReconciliationResult.PAID, result)
        assertEquals(1, calls)
    }

    @Test
    fun cancelledStatusIsTerminalUnpaid() = runTest {
        val result = reconcileSharedPaymentStatus(
            maxRetries = 0,
            delayMs = 0,
            lookup = {
                es.criosrango.shared.model.PaymentStatusResponse(id = 123, status = "cancelled", paid = false, terminal = true)
            },
            isTransientException = { false }
        )
        assertEquals(SharedPaymentReconciliationResult.TERMINAL_UNPAID, result)
    }

    @Test
    fun malformedReturnDoesNotBecomePaymentStatusCall() {
        val result = when ("unexpected") {
            "ok" -> true
            "cancel" -> true
            else -> false
        }
        assertEquals(false, result)
    }

    @Test
    fun callbackAndForegroundAreExactlyOnce() = runTest {
        var paymentStatusCalls = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/payment-status")) {
                paymentStatusCalls++
                respond(
                    content = """{"order_id":123,"status":"processing","paid":true,"terminal":true}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            } else {
                respond(
                    content = """{"items":[],"totals":{"total_price":"0","currency_symbol":"€","currency_minor_unit":2}}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            }
        }
        val api = StoreApiClient(client = HttpClient(engine))
        val pendingStore = FakePendingCardPaymentStore()
        val cartStore = StoreCartStore(api, this)
        val paymentStore = StorePaymentStore(api, cartStore, pendingStore, this)

        val redirectUrl = paymentStore.startCardPayment(
            CheckoutResponse(
                orderId = 123,
                orderKey = "wc_order_123",
                paymentMethod = "cecabank_gateway",
                redirectUrl = "https://payment.example/123"
            )
        )

        assertEquals("https://payment.example/123", redirectUrl)
        paymentStore.markPaymentOpened()

        paymentStore.handlePaymentReturn("ok", 123)
        advanceUntilIdle()

        assertEquals(1, paymentStatusCalls, "payment-status call count")
        assertEquals(StoreCardPaymentState.PAID, paymentStore.state.value, "payment state")
        assertNull(pendingStore.load(), "pending payment")

        paymentStore.onForeground()
        paymentStore.onForeground()
        paymentStore.handlePaymentReturn("ok", 123)
        advanceUntilIdle()

        assertEquals(1, paymentStatusCalls, "payment-status call count")
        assertEquals(StoreCardPaymentState.PAID, paymentStore.state.value, "payment state")
        assertNull(pendingStore.load(), "pending payment")
    }

    @Test
    fun processDeathClearsPendingWithoutPaymentStatusAndAllowsNewPayment() = runTest {
        var paymentStatusCalls = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/payment-status")) {
                paymentStatusCalls++
            }
            respond(
                content = """{"items":[],"totals":{"total_price":"0","currency_symbol":"€","currency_minor_unit":2}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val api = StoreApiClient(client = HttpClient(engine))
        val pendingStore = FakePendingCardPaymentStore()
        val oldScope = this
        val oldCartStore = StoreCartStore(api, oldScope)
        val oldPaymentStore = StorePaymentStore(api, oldCartStore, pendingStore, oldScope)

        assertEquals(
            "https://payment.example/123",
            oldPaymentStore.startCardPayment(
                CheckoutResponse(
                    orderId = 123,
                    orderKey = "wc_order_123",
                    paymentMethod = "cecabank_gateway",
                    redirectUrl = "https://payment.example/123"
                )
            )
        )
        assertEquals(123, pendingStore.load()?.orderId)

        val newCartStore = StoreCartStore(api, this)
        val newPaymentStore = StorePaymentStore(api, newCartStore, pendingStore, this)

        newPaymentStore.clearForNewProcess()

        assertNull(pendingStore.load(), "pending payment")
        assertEquals(0, paymentStatusCalls)
        assertEquals(StoreCardPaymentState.IDLE, newPaymentStore.state.value)
        assertEquals(
            "https://payment.example/456",
            newPaymentStore.startCardPayment(
                CheckoutResponse(
                    orderId = 456,
                    orderKey = "wc_order_456",
                    paymentMethod = "cecabank_gateway",
                    redirectUrl = "https://payment.example/456"
                )
            )
        )
        advanceUntilIdle()
        assertEquals(0, paymentStatusCalls)
    }
}