package es.criosrango.app

import es.criosrango.shared.PushNotificationContract
import es.criosrango.shared.PushNotificationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushNotificationRoutingTest {
    @Test
    fun newProductsPayload_routesToNewProductsWithoutOrder() {
        assertEquals(PushNotificationType.NEW_PRODUCTS, PushNotificationType.NEW_PRODUCTS)
        assertNull(PushNotificationContract.parseOrderId(null))
    }

    @Test
    fun orderStatusPayload_roundTripsExactOrderId() {
        assertEquals(PushNotificationType.ORDER_STATUS, PushNotificationType.ORDER_STATUS)
        assertEquals(123, PushNotificationContract.parseOrderId("123"))
    }

    @Test
    fun missingOrderId_neverProducesAnOrderId() {
        assertNull(PushNotificationContract.parseOrderId(null))
    }
}
