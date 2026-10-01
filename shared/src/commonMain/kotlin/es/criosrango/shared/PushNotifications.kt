package es.criosrango.shared

object PushNotificationType {
    const val NEW_PRODUCTS = "new_products"
    const val ORDER_STATUS = "order_status"
}

object PushNotificationContract {
    const val TYPE_KEY = "type"
    const val ORDER_ID_KEY = "order_id"

    fun parseOrderId(value: String?): Int? =
        value?.toIntOrNull()?.takeIf { it > 0 }
}

data class IosPushNavigation(val type: String, val orderId: Int? = null)

expect object PushPreferencesPlatform {
    fun newProducts(): Boolean
    fun orderUpdates(): Boolean
    fun setNewProducts(value: Boolean)
    fun setOrderUpdates(value: Boolean)
    fun requestPermission()
}
