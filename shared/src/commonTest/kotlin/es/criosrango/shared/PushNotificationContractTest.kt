package es.criosrango.shared
import kotlin.test.Test
import kotlin.test.assertEquals
class PushNotificationContractTest {
 @Test fun types_areStable(){ assertEquals("new_products",PushNotificationType.NEW_PRODUCTS); assertEquals("order_status",PushNotificationType.ORDER_STATUS) }
}