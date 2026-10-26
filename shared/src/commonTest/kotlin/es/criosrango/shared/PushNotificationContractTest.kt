package es.criosrango.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PushNotificationContractTest {
    @Test
    fun types_areStable() {
        assertEquals("new_products", PushNotificationType.NEW_PRODUCTS)
        assertEquals("order_status", PushNotificationType.ORDER_STATUS)
        assertEquals("birthday_coupon", PushNotificationType.BIRTHDAY_COUPON)
    }

    @Test
    fun orderId_contractRoundTripsAndRejectsInvalidValues() {
        assertEquals(123, PushNotificationContract.parseOrderId("123"))
        assertNull(PushNotificationContract.parseOrderId(null))
        assertNull(PushNotificationContract.parseOrderId(""))
        assertNull(PushNotificationContract.parseOrderId("0"))
        assertNull(PushNotificationContract.parseOrderId("-7"))
    }

    @Test
    fun orderStatus_routesToExactOrderOrAccountFallback() {
        assertEquals(PushDestination.Order(123), PushNotificationContract.destination("order_status", "123"))
        assertEquals(PushDestination.Account, PushNotificationContract.destination("order_status", "invalid"))
        assertEquals(PushDestination.Account, PushNotificationContract.destination("order_status", null))
    }

    @Test
    fun newProductsAndBirthdayCoupon_haveSharedDestinations() {
        assertEquals(PushDestination.NewProducts, PushNotificationContract.destination("new_products"))
        assertEquals(PushDestination.Promotions, PushNotificationContract.destination("birthday_coupon"))
    }

    @Test
    fun unknownType_isIgnored() {
        assertEquals(PushDestination.Ignore, PushNotificationContract.destination("made_up_event"))
    }

    @Test
    fun notificationBlockWinsAndBlankTextFallsBackToData() {
        val payload = PushNotificationContract.parsePayload(
            type = "new_products",
            orderId = null,
            notificationTitle = "¡Hay novedades!",
            notificationBody = "Han llegado productos nuevos.",
            dataTitle = "incorrecto",
            dataBody = "incorrecto"
        )
        assertEquals("¡Hay novedades!", payload.title)
        assertEquals("Han llegado productos nuevos.", payload.body)
    }

    @Test
    fun absentNotificationTextUsesDataAndExposesGenericFallbackConstants() {
        val payload = PushNotificationContract.parsePayload(
            type = "order_status",
            orderId = "12",
            notificationTitle = " ",
            notificationBody = null,
            dataTitle = "Pedido actualizado",
            dataBody = "Tu pedido está en camino."
        )
        assertEquals("Pedido actualizado", payload.title)
        assertEquals("Tu pedido está en camino.", payload.body)
        val empty = PushNotificationContract.parsePayload("order_status", "12")
        assertNull(empty.title)
        assertNull(empty.body)
        assertEquals("Críos&Rango", PushNotificationContract.GENERIC_TITLE)
        assertEquals("Tienes una nueva notificación", PushNotificationContract.GENERIC_BODY)
    }

    @Test
    fun preferenceRendererTextAndOrder_areStable() {
        assertEquals(
            listOf(
                "Notificaciones",
                "Novedades y nuevos productos",
                "Resumen diario de novedades.",
                "Actualizaciones de pedidos",
                "Cambios relevantes de tus pedidos."
            ),
            StorePushPreferencesContract.visibleTextInOrder
        )
    }
}
