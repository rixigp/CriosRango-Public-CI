package es.criosrango.shared
object PushNotificationType { const val NEW_PRODUCTS="new_products"; const val ORDER_STATUS="order_status" }
data class IosPushNavigation(val type:String,val orderId:Int?=null)
expect object PushPreferencesPlatform { fun newProducts():Boolean; fun orderUpdates():Boolean; fun setNewProducts(value:Boolean); fun setOrderUpdates(value:Boolean); fun requestPermission() }