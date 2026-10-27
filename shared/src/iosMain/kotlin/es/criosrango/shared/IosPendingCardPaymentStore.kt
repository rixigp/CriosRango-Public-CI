package es.criosrango.shared

import platform.Foundation.NSUserDefaults

class IosPendingCardPaymentStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults
) : PendingCardPaymentStore {
    override fun save(payment: StorePendingCardPayment): Boolean {
        if (payment.orderId <= 0 || payment.orderKey.isBlank() || payment.paymentUrl.isBlank()) return false
        defaults.setInteger(payment.orderId.toLong(), forKey = ORDER_ID)
        val keySaved = IosSecureKeyValueStore.write(ORDER_KEY, payment.orderKey, defaults)
        val urlSaved = IosSecureKeyValueStore.write(PAYMENT_URL, payment.paymentUrl, defaults)
        return keySaved && urlSaved && load() == payment
    }

    override fun load(): StorePendingCardPayment? {
        val orderId = defaults.integerForKey(ORDER_ID).toInt()
        val orderKey = IosSecureKeyValueStore.read(ORDER_KEY, defaults).orEmpty()
        val paymentUrl = IosSecureKeyValueStore.read(PAYMENT_URL, defaults).orEmpty()
        if (orderId <= 0 || orderKey.isBlank() || paymentUrl.isBlank()) return null
        return StorePendingCardPayment(orderId, orderKey, paymentUrl)
    }

    override fun clear(): Boolean {
        defaults.removeObjectForKey(ORDER_ID)
        IosSecureKeyValueStore.delete(ORDER_KEY, defaults)
        IosSecureKeyValueStore.delete(PAYMENT_URL, defaults)
        return true
    }

    private companion object {
        const val ORDER_ID = "criosrango_ios_pending_payment_order_id"
        const val ORDER_KEY = "criosrango_ios_pending_payment_order_key"
        const val PAYMENT_URL = "criosrango_ios_pending_payment_url"
    }
}
