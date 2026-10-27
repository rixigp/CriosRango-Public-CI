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

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val secureStore = AndroidSecureKeyValueStore(appContext)

    init {
        // Remove only the legacy plaintext secret; keep the non-secret order_id.
        preferences.edit().remove(ORDER_KEY).apply()
    }

    override fun load(): PendingClaimOrder? {
        val id = preferences.getInt(ORDER_ID, 0)
        val key = secureStore.get(ORDER_KEY).orEmpty()
        return if (id > 0 && key.isNotBlank()) PendingClaimOrder(id, key) else null
    }

    override fun save(order: PendingClaimOrder): Boolean {
        if (order.orderId <= 0 || order.orderKey.isBlank()) return false
        preferences.edit().putInt(ORDER_ID, order.orderId).remove(ORDER_KEY).apply()
        secureStore.put(ORDER_KEY, order.orderKey)
        return load() == order
    }

    override fun clear() {
        secureStore.remove(ORDER_KEY)
        preferences.edit().remove(ORDER_ID).remove(ORDER_KEY).apply()
    }
}
