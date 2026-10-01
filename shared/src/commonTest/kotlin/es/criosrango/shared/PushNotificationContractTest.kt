package es.criosrango.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PushNotificationContractTest {
    @Test
    fun types_areStable() {
        assertEquals("new_products", PushNotificationType.NEW_PRODUCTS)
        assertEquals("order_status", PushNotificationType.ORDER_STATUS)
    }

    @Test
    fun orderId_contract_roundTripsExactly() {
        assertEquals(123, PushNotificationContract.parseOrderId("123"))
    }

    @Test
    fun missingOrInvalidOrderId_neverProducesAnOrderId() {
        assertNull(PushNotificationContract.parseOrderId(null))
        assertNull(PushNotificationContract.parseOrderId(""))
        assertNull(PushNotificationContract.parseOrderId("0"))
        assertNull(PushNotificationContract.parseOrderId("-7"))
    }
}
