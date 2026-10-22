package es.criosrango.shared

object PushNotificationType {
    const val NEW_PRODUCTS = "new_products"
    const val ORDER_STATUS = "order_status"
    const val BIRTHDAY_COUPON = "birthday_coupon"
}

sealed class PushDestination {
    data class Order(val orderId: Int) : PushDestination()
    object NewProducts : PushDestination()
    object Promotions : PushDestination()
    object Account : PushDestination()
    object Ignore : PushDestination()
}

data class PushPayload(
    val type: String?,
    val orderId: Int?,
    val title: String?,
    val body: String?,
    val destination: PushDestination
)

object PushNotificationContract {
    const val TYPE_KEY = "type"
    const val ORDER_ID_KEY = "order_id"
    const val TITLE_KEY = "title"
    const val BODY_KEY = "body"
    const val GENERIC_TITLE = "Críos&Rango"
    const val GENERIC_BODY = "Tienes una nueva notificación"

    fun parseOrderId(value: String?): Int? = value?.toIntOrNull()?.takeIf { it > 0 }

    fun destination(type: String?, orderId: String? = null): PushDestination =
        when (type?.trim()) {
            PushNotificationType.ORDER_STATUS ->
                parseOrderId(orderId)?.let { PushDestination.Order(it) } ?: PushDestination.Account
            PushNotificationType.NEW_PRODUCTS -> PushDestination.NewProducts
            PushNotificationType.BIRTHDAY_COUPON -> PushDestination.Promotions
            else -> PushDestination.Ignore
        }

    fun parsePayload(
        type: String?,
        orderId: String?,
        notificationTitle: String? = null,
        notificationBody: String? = null,
        dataTitle: String? = null,
        dataBody: String? = null
    ): PushPayload {
        val normalizedType = type?.trim()?.takeIf { it.isNotEmpty() }
        return PushPayload(
            type = normalizedType,
            orderId = parseOrderId(orderId),
            title = firstNonBlank(notificationTitle, dataTitle),
            body = firstNonBlank(notificationBody, dataBody),
            destination = destination(normalizedType, orderId)
        )
    }

    private fun firstNonBlank(primary: String?, secondary: String?): String? =
        primary?.takeIf { it.isNotBlank() } ?: secondary?.takeIf { it.isNotBlank() }
}

data class IosPushNavigation(val type: String, val orderId: Int? = null) {
    val destination: PushDestination
        get() = if (
            type == PushNotificationType.ORDER_STATUS && (orderId == null || orderId <= 0)
        ) PushDestination.Ignore else PushNotificationContract.destination(type, orderId?.toString())
}

object StorePushPreferencesContract {
    const val TITLE = "Notificaciones"
    const val NEWS_TITLE = "Novedades y nuevos productos"
    const val NEWS_DESCRIPTION = "Resumen diario de novedades."
    const val ORDER_UPDATES_TITLE = "Actualizaciones de pedidos"
    const val ORDER_UPDATES_DESCRIPTION = "Cambios relevantes de tus pedidos."

    val visibleTextInOrder: List<String>
        get() = listOf(TITLE, NEWS_TITLE, NEWS_DESCRIPTION, ORDER_UPDATES_TITLE, ORDER_UPDATES_DESCRIPTION)
}

expect object PushPreferencesPlatform {
    fun newProducts(): Boolean
    fun orderUpdates(): Boolean
    fun setNewProducts(value: Boolean)
    fun setOrderUpdates(value: Boolean)
    fun requestPermission()
}
