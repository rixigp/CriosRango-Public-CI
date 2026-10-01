package es.criosrango.shared
actual object PushPreferencesPlatform {
 private var initialized=false
 private var values=mutableMapOf("new_products" to true,"order_updates" to true)
 actual fun newProducts()=values["new_products"]?:true
 actual fun orderUpdates()=values["order_updates"]?:true
 actual fun setNewProducts(value:Boolean){values["new_products"]=value}
 actual fun setOrderUpdates(value:Boolean){values["order_updates"]=value}
 actual fun requestPermission(){}
}