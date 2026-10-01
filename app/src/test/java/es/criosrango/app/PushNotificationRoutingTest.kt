package es.criosrango.app

import android.content.Intent
import es.criosrango.shared.PushNotificationContract
import es.criosrango.shared.PushNotificationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushNotificationRoutingTest {
    @Test
    fun newProductsPayload_routesToNewProductsWithoutOrder() {
        val intent = Intent().apply {
            putExtra(PushNotificationContract.TYPE_KEY, PushNotificationType.NEW_PRODUCTS)
        }

        assertEquals(PushNotificationType.NEW_PRODUCTS, intent.getStringExtra(PushNotificationContract.TYPE_KEY))
        assertNull(intent.getStringExtra(PushNotificationContract.ORDER_ID_KEY))
    }

    @Test
    fun orderStatusPayload_roundTripsExactOrderId() {
        val intent = Intent().apply {
            putExtra(PushNotificationContract.TYPE_KEY, PushNotificationType.ORDER_STATUS)
            putExtra(PushNotificationContract.ORDER_ID_KEY, "123")
        }

        assertEquals(PushNotificationType.ORDER_STATUS, intent.getStringExtra(PushNotificationContract.TYPE_KEY))
        assertEquals(123, PushNotificationContract.parseOrderId(intent.getStringExtra(PushNotificationContract.ORDER_ID_KEY)))
    }

    @Test
    fun missingOrderId_neverProducesAnOrderId() {
        val intent = Intent().apply {
            putExtra(PushNotificationContract.TYPE_KEY, PushNotificationType.ORDER_STATUS)
        }

        assertNull(PushNotificationContract.parseOrderId(intent.getStringExtra(PushNotificationContract.ORDER_ID_KEY)))
    }
}
