package es.criosrango.shared
actual object PushPreferencesPlatform {
 private var initialized=false
 private var values=mutableMapOf("new_products" to true,"order_updates" to true)
 private var syncState = PushPreferencesSyncState(
  PushPreferencesSnapshot(true, true), PushPreferencesSnapshot(true, true), false
 )
 actual fun newProducts()=values["new_products"]?:true
 actual fun orderUpdates()=values["order_updates"]?:true
 actual fun setNewProducts(value:Boolean){values["new_products"]=value}
 actual fun setOrderUpdates(value:Boolean){values["order_updates"]=value}
 actual fun loadSyncState() = syncState.copy(desired = PushPreferencesSnapshot(newProducts(), orderUpdates()))
 actual fun saveDesired(snapshot:PushPreferencesSnapshot) {
  setNewProducts(snapshot.newProducts); setOrderUpdates(snapshot.orderUpdates)
  syncState = syncState.copy(desired=snapshot, pending=true)
 }
 actual fun saveConfirmed(snapshot:PushPreferencesSnapshot) { syncState=syncState.copy(confirmed=snapshot) }
 actual fun savePending(pending:Boolean) { syncState=syncState.copy(pending=pending) }
 actual fun requestPermission(){}
}
