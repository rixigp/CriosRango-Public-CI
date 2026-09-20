package es.criosrango.shared

import android.content.Context
import es.criosrango.shared.account.ClaimOrderStore
import es.criosrango.shared.account.PendingClaimOrder

class AndroidClaimOrderStore(context: Context) : ClaimOrderStore {
    private companion object {
        const val PREFS = "criosrango_pending_claim_order"
        const val ORDER_ID = "order_id"
        const val ORDER_KEY = "order_key"
    }

    private val preferences = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): PendingClaimOrder? {
        val id = preferences.getInt(ORDER_ID, 0)
        val key = preferences.getString(ORDER_KEY, null).orEmpty()
        return if (id > 0 && key.isNotBlank()) PendingClaimOrder(id, key) else null
    }

    override fun save(order: PendingClaimOrder): Boolean {
        if (order.orderId <= 0 || order.orderKey.isBlank()) return false
        preferences.edit().putInt(ORDER_ID, order.orderId).putString(ORDER_KEY, order.orderKey).apply()
        return load() == order
    }

    override fun clear() {
        preferences.edit().remove(ORDER_ID).remove(ORDER_KEY).apply()
    }
}
