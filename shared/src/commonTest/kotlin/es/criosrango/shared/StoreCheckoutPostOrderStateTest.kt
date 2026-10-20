package es.criosrango.shared

import kotlin.test.Test
import kotlin.test.assertEquals

class StoreCheckoutPostOrderStateTest {
    @Test
    fun bizumOrderCreatedIsManualOrderNotPaid() {
        assertEquals(StoreCheckoutPostOrderState.MANUAL_ORDER_CREATED,
            resolveStoreCheckoutPostOrderState("bizum", 57593, StoreCardPaymentState.IDLE))
        assertEquals(StoreCheckoutPostOrderState.MANUAL_ORDER_CREATED,
            resolveStoreCheckoutPostOrderState("cheque", 57593, StoreCardPaymentState.PAID))
    }

    @Test
    fun manualOrderStateTakesPrecedenceOverPaymentReconciliationState() {
        assertEquals(StoreCheckoutPostOrderState.MANUAL_ORDER_CREATED,
            resolveStoreCheckoutPostOrderState("bizum", 57593, StoreCardPaymentState.NOT_PAID))
    }

    @Test
    fun cardFlowClassificationIsUnchanged() {
        assertEquals(StoreCheckoutPostOrderState.CARD_PAID,
            resolveStoreCheckoutPostOrderState("cecabank_gateway", 57593, StoreCardPaymentState.PAID))
        assertEquals(StoreCheckoutPostOrderState.TERMINAL_UNPAID_CANCELLED,
            resolveStoreCheckoutPostOrderState("cecabank_gateway", 57593, StoreCardPaymentState.NOT_PAID))
        assertEquals(StoreCheckoutPostOrderState.UNKNOWN,
            resolveStoreCheckoutPostOrderState("cecabank_gateway", 57593, StoreCardPaymentState.IDLE))
    }

    @Test
    fun missingOrderIdIsNotSuccess() {
        assertEquals(StoreCheckoutPostOrderState.NONE,
            resolveStoreCheckoutPostOrderState("bizum", null, StoreCardPaymentState.IDLE))
    }
}
