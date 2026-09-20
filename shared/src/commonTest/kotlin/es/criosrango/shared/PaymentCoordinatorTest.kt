package es.criosrango.shared

import es.criosrango.shared.api.PaymentCartActions
import es.criosrango.shared.api.PaymentCoordinator
import es.criosrango.shared.api.PaymentStatusProvider
import es.criosrango.shared.api.PendingPaymentStore
import es.criosrango.shared.model.OrderStatusResponse
import es.criosrango.shared.model.PaymentState
import es.criosrango.shared.model.PendingPayment
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceUntilIdle
import kotlin.test.Test
import kotlin.test.assertEquals

class PaymentCoordinatorTest {
    @Test fun paidConsumesCartAndClearsPending() = runTest {
        val fixture = Fixture(this, listOf(OrderStatusResponse(42, "processing", true, false, false)))
        fixture.coordinator.begin(42, "key", "https://criosrango.es/pay")
        fixture.coordinator.verify()
        fixture.awaitTerminal()
        assertEquals(PaymentState.PAID, fixture.coordinator.state.value)
        assertEquals(1, fixture.consumed)
        assertEquals(null, fixture.store.load())
    }

    @Test fun failedRestoresCartAndClearsPending() = runTest {
        val fixture = Fixture(this, listOf(OrderStatusResponse(42, "failed", false, true, true)))
        fixture.coordinator.begin(42, "key", "https://criosrango.es/pay")
        fixture.coordinator.verify()
        fixture.awaitTerminal()
        assertEquals(PaymentState.FAILED, fixture.coordinator.state.value)
        assertEquals(1, fixture.restored)
        assertEquals(null, fixture.store.load())
    }

    @Test fun pendingTimeoutDoesNotRestoreOrConsume() = runTest {
        val fixture = Fixture(this, listOf(OrderStatusResponse(42, "pending", false, true, false)), maxAttempts = 2)
        fixture.coordinator.begin(42, "key", "https://criosrango.es/pay")
        fixture.coordinator.verify()
        fixture.awaitTerminal()
        assertEquals(PaymentState.PENDING, fixture.coordinator.state.value)
        assertEquals(0, fixture.consumed)
        assertEquals(0, fixture.restored)
        assertEquals(PendingPayment(42, "key"), fixture.store.load())
    }

    @Test fun concurrentVerifyUsesSinglePollingJob() = runTest {
        val fixture = Fixture(this, listOf(OrderStatusResponse(42, "processing", true, false, false)))
        fixture.coordinator.begin(42, "key", "https://criosrango.es/pay")
        fixture.coordinator.verify()
        fixture.coordinator.verify()
        fixture.awaitTerminal()
        assertEquals(1, fixture.consumed)
        assertEquals(1, fixture.calls)
    }

    @Test fun cancelledReturnRestoresCart() = runTest {
        val fixture = Fixture(this, emptyList())
        fixture.coordinator.begin(42, "key", "https://criosrango.es/pay")
        fixture.coordinator.handleReturn(es.criosrango.shared.model.PaymentReturn.Cancelled(42))
        advanceUntilIdle()
        assertEquals(PaymentState.CANCELLED, fixture.coordinator.state.value)
        assertEquals(1, fixture.restored)
        assertEquals(null, fixture.store.load())
    }

    private class Fixture(private val scope: kotlinx.coroutines.test.TestScope, statuses: List<OrderStatusResponse>, private val maxAttempts: Int = 10) {
        val store = FakeStore()
        var consumed = 0
        var restored = 0
        var calls = 0
        private val provider = object : PaymentStatusProvider {
            private val queue = statuses.toMutableList()
            override suspend fun paymentStatus(orderId: Int, orderKey: String): OrderStatusResponse {
                calls++
                return queue.removeFirstOrNull() ?: statuses.lastOrNull() ?: OrderStatusResponse(orderId, "pending", false, true, false)
            }
        }
        private val actions = object : PaymentCartActions {
            override suspend fun consumeConfirmedOrder() { consumed++ }
            override suspend fun restoreRemoteAfterUnpaidCheckout() { restored++ }
        }
        val coordinator = PaymentCoordinator(provider, store, actions, scope, maxAttempts, 1)
        suspend fun awaitTerminal() { delay(20); scope.advanceUntilIdle() }
    }
}
private typealias PaymentCoordinatorTestScope = kotlinx.coroutines.test.TestScope

private class FakeStore : PendingPaymentStore {
    private var value: PendingPayment? = null
    override fun load() = value
    override fun save(payment: PendingPayment) { value = payment }
    override fun clear() { value = null }
}
