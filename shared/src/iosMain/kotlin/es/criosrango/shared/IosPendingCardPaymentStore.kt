package es.criosrango.shared

import platform.Foundation.NSUserDefaults

class IosPendingCardPaymentStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults
) : PendingCardPaymentStore {
    override fun save(payment: StorePendingCardPayment): Boolean {
        defaults.setInteger(payment.orderId.toLong(), forKey = ORDER_ID)
        defaults.setObject(payment.orderKey, forKey = ORDER_KEY)
        defaults.setObject(payment.paymentUrl, forKey = PAYMENT_URL)
        return true
    }

    override fun load(): StorePendingCardPayment? {
        val orderId = defaults.integerForKey(ORDER_ID).toInt()
        val orderKey = defaults.stringForKey(ORDER_KEY).orEmpty()
        val paymentUrl = defaults.stringForKey(PAYMENT_URL).orEmpty()
        if (orderId <= 0 || orderKey.isBlank() || paymentUrl.isBlank()) return null
        return StorePendingCardPayment(orderId, orderKey, paymentUrl)
    }

    override fun clear(): Boolean {
        defaults.removeObjectForKey(ORDER_ID)
        defaults.removeObjectForKey(ORDER_KEY)
        defaults.removeObjectForKey(PAYMENT_URL)
        return true
    }

    private companion object {
        const val ORDER_ID = "criosrango_ios_pending_payment_order_id"
        const val ORDER_KEY = "criosrango_ios_pending_payment_order_key"
        const val PAYMENT_URL = "criosrango_ios_pending_payment_url"
    }
}
