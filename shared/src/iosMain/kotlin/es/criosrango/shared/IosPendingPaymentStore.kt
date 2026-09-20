package es.criosrango.shared

import es.criosrango.shared.api.PendingPaymentStore
import es.criosrango.shared.model.PendingPayment
import platform.Foundation.NSUserDefaults

class IosPendingPaymentStore : PendingPaymentStore {
    private val defaults = NSUserDefaults.standardUserDefaults
    override fun load(): PendingPayment? {
        val id = defaults.integerForKey("criosrango.payment.order_id")
        val key = defaults.stringForKey("criosrango.payment.order_key")
        return if (id > 0 && !key.isNullOrBlank()) PendingPayment(id, key) else null
    }
    override fun save(payment: PendingPayment) {
        defaults.setInteger(payment.orderId.toLong(), forKey = "criosrango.payment.order_id")
        defaults.setObject(payment.orderKey, forKey = "criosrango.payment.order_key")
    }
    override fun clear() {
        defaults.removeObjectForKey("criosrango.payment.order_id")
        defaults.removeObjectForKey("criosrango.payment.order_key")
    }
}
