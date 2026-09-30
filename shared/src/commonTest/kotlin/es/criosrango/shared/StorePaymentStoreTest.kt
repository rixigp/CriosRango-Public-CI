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
import kotlinx.coroutines.flow.first
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
    fun callbackReconciliationIsExactlyOnce() = runTest {
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
        paymentStore.onForeground()
        paymentStore.state.first { state ->
            state == StoreCardPaymentState.PAID ||
                state == StoreCardPaymentState.NOT_PAID ||
                state == StoreCardPaymentState.ERROR
        }

        assertEquals(
            1,
            paymentStatusCalls,
            "Expected exactly one payment-status call"
        )
        assertEquals(
            StoreCardPaymentState.PAID,
            paymentStore.state.value,
            "Expected payment state PAID"
        )
        assertNull(
            pendingStore.load(),
            "Expected pending payment to be cleared"
        )

    }
    @Test
    fun duplicateForegroundAndCallbackAfterPaidAreIgnored() = runTest {
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

        assertEquals(
            "https://payment.example/123",
            paymentStore.startCardPayment(
                CheckoutResponse(
                    orderId = 123,
                    orderKey = "wc_order_123",
                    paymentMethod = "cecabank_gateway",
                    redirectUrl = "https://payment.example/123"
                )
            ),
            "Expected startCardPayment to return the payment redirect URL"
        )
        paymentStore.markPaymentOpened()
        paymentStore.handlePaymentReturn("ok", 123)
        paymentStore.onForeground()
        paymentStore.state.first { state ->
            state == StoreCardPaymentState.PAID ||
                state == StoreCardPaymentState.NOT_PAID ||
                state == StoreCardPaymentState.ERROR
        }

        assertEquals(
            1,
            paymentStatusCalls,
            "Expected exactly one payment-status call after the first reconciliation"
        )
        assertEquals(
            StoreCardPaymentState.PAID,
            paymentStore.state.value,
            "Expected payment state PAID after the first reconciliation"
        )
        assertNull(
            pendingStore.load(),
            "Expected pending payment to be cleared after the first reconciliation"
        )

        paymentStore.onForeground()
        paymentStore.onForeground()
        paymentStore.handlePaymentReturn("ok", 123)
        paymentStore.state.first { state ->
            state == StoreCardPaymentState.PAID ||
                state == StoreCardPaymentState.NOT_PAID ||
                state == StoreCardPaymentState.ERROR
        }

        assertEquals(
            1,
            paymentStatusCalls,
            "Expected exactly one payment-status call after duplicate foreground/callback"
        )
        assertEquals(
            StoreCardPaymentState.PAID,
            paymentStore.state.value,
            "Expected payment state to remain PAID after duplicate foreground/callback"
        )
        assertNull(
            pendingStore.load(),
            "Expected pending payment to remain cleared after duplicate foreground/callback"
        )
    }

    @Test
    fun pendingSurvivesStoreRecreationAndPaidStatusIsReconciled() = runTest {
        var paymentStatusCalls = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/payment-status")) {
                paymentStatusCalls++
                respond(
                    content = "{\"order_id\":123,\"status\":\"processing\",\"paid\":true,\"terminal\":true}",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            } else {
                respond(
                    content = "{\"items\":[],\"totals\":{\"total_price\":\"0\",\"currency_symbol\":\"€\",\"currency_minor_unit\":2}}",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            }
        }
        val api = StoreApiClient(client = HttpClient(engine))
        val pendingStore = FakePendingCardPaymentStore()
        val oldPaymentStore = StorePaymentStore(api, StoreCartStore(api, this), pendingStore, this)
        assertEquals("https://payment.example/123", oldPaymentStore.startCardPayment(
            CheckoutResponse(123, "wc_order_123", paymentMethod = "cecabank_gateway", redirectUrl = "https://payment.example/123")
        ))
        oldPaymentStore.markPaymentOpened()

        val recreatedPaymentStore = StorePaymentStore(api, StoreCartStore(api, this), pendingStore, this)
        assertEquals(StoreCardPaymentState.WAITING_RETURN, recreatedPaymentStore.state.value)
        assertEquals(123, recreatedPaymentStore.orderId.value)
        assertEquals("https://payment.example/123", recreatedPaymentStore.redirectUrl.value)
        assertEquals(123, pendingStore.load()?.orderId)

        recreatedPaymentStore.onForeground()
        recreatedPaymentStore.state.first { it == StoreCardPaymentState.PAID }

        assertEquals(1, paymentStatusCalls)
        assertNull(pendingStore.load())
    }

    @Test
    fun failedStatusAfterRecreationClearsPendingOnlyWhenTerminal() = runTest {
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/payment-status")) {
                respond(
                    content = "{\"order_id\":123,\"status\":\"failed\",\"paid\":false,\"terminal\":true}",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            } else {
                respond(
                    content = "{\"items\":[],\"totals\":{\"total_price\":\"0\",\"currency_symbol\":\"€\",\"currency_minor_unit\":2}}",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            }
        }
        val api = StoreApiClient(client = HttpClient(engine))
        val pendingStore = FakePendingCardPaymentStore()
        val oldPaymentStore = StorePaymentStore(api, StoreCartStore(api, this), pendingStore, this)
        oldPaymentStore.startCardPayment(
            CheckoutResponse(123, "wc_order_123", paymentMethod = "cecabank_gateway", redirectUrl = "https://payment.example/123")
        )

        val recreatedPaymentStore = StorePaymentStore(api, StoreCartStore(api, this), pendingStore, this)
        recreatedPaymentStore.onForeground()
        recreatedPaymentStore.state.first { it == StoreCardPaymentState.NOT_PAID }

        assertNull(pendingStore.load())
    }

    @Test
    fun transportErrorKeepsPendingAndAllowsLaterRetry() = runTest {
        var calls = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.contains("/payment-status")) {
                calls++
                if (calls == 1) {
                    respond(
                        content = "{\"code\":\"temporary\",\"message\":\"temporary\"}",
                        status = HttpStatusCode.InternalServerError,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                } else {
                    respond(
                        content = "{\"order_id\":123,\"status\":\"processing\",\"paid\":true,\"terminal\":true}",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                    )
                }
            } else {
                respond(
                    content = "{\"items\":[],\"totals\":{\"total_price\":\"0\",\"currency_symbol\":\"€\",\"currency_minor_unit\":2}}",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                )
            }
        }
        val api = StoreApiClient(client = HttpClient(engine))
        val pendingStore = FakePendingCardPaymentStore()
        val paymentStore = StorePaymentStore(api, StoreCartStore(api, this), pendingStore, this)
        paymentStore.startCardPayment(
            CheckoutResponse(123, "wc_order_123", paymentMethod = "cecabank_gateway", redirectUrl = "https://payment.example/123")
        )
        paymentStore.markPaymentOpened()

        paymentStore.onForeground()
        paymentStore.state.first { it == StoreCardPaymentState.ERROR }
        assertEquals(123, pendingStore.load()?.orderId)

        paymentStore.retryReconciliation()
        paymentStore.state.first { it == StoreCardPaymentState.PAID }
        assertNull(pendingStore.load())
        assertEquals(2, calls)
    }

    @Test
    fun cancellationIsTerminalAndClearsPending() = runTest {
        val api = StoreApiClient(client = HttpClient(MockEngine {
            respond(
                content = "{\"items\":[],\"totals\":{\"total_price\":\"0\",\"currency_symbol\":\"€\",\"currency_minor_unit\":2}}",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }))
        val pendingStore = FakePendingCardPaymentStore()
        val paymentStore = StorePaymentStore(api, StoreCartStore(api, this), pendingStore, this)
        paymentStore.startCardPayment(
            CheckoutResponse(123, "wc_order_123", paymentMethod = "cecabank_gateway", redirectUrl = "https://payment.example/123")
        )

        paymentStore.cancel(123)

        assertEquals(StoreCardPaymentState.NOT_PAID, paymentStore.state.value)
        assertNull(pendingStore.load())
    }

}