package es.criosrango.shared

import es.criosrango.shared.account.ClaimOrderStore
import es.criosrango.shared.account.PendingClaimOrder
import platform.Foundation.NSUserDefaults

class IosClaimOrderStore : ClaimOrderStore {
    private companion object {
        const val ORDER_ID = "criosrango_pending_claim_order_id"
        const val ORDER_KEY = "criosrango_pending_claim_order_key"
    }

    private val defaults = NSUserDefaults.standardUserDefaults

    override fun load(): PendingClaimOrder? {
        val id = defaults.integerForKey(ORDER_ID).toInt()
        val key = defaults.stringForKey(ORDER_KEY).orEmpty()
        return if (id > 0 && key.isNotBlank()) PendingClaimOrder(id, key) else null
    }

    override fun save(order: PendingClaimOrder): Boolean {
        if (order.orderId <= 0 || order.orderKey.isBlank()) return false
        defaults.setInteger(order.orderId.toLong(), forKey = ORDER_ID)
        defaults.setObject(order.orderKey, forKey = ORDER_KEY)
        return load() == order
    }

    override fun clear() {
        defaults.removeObjectForKey(ORDER_ID)
        defaults.removeObjectForKey(ORDER_KEY)
    }
}
