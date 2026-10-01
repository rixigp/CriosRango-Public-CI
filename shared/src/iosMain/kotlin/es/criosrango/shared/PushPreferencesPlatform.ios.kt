package es.criosrango.shared
import platform.Foundation.NSUserDefaults
actual object PushPreferencesPlatform {
 private val d=NSUserDefaults.standardUserDefaults
 actual fun newProducts()=if(d.objectForKey("criosrango_push_new_products")==null)true else d.boolForKey("criosrango_push_new_products")
 actual fun orderUpdates()=if(d.objectForKey("criosrango_push_order_updates")==null)true else d.boolForKey("criosrango_push_order_updates")
 actual fun setNewProducts(value:Boolean){d.setBool(value,"criosrango_push_new_products")}
 actual fun setOrderUpdates(value:Boolean){d.setBool(value,"criosrango_push_order_updates")}
 actual fun requestPermission(){}
}