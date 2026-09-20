package es.criosrango.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class PendingCardPayment(val orderId: Int, val orderKey: String, val billingEmail: String, val paymentUrl: String)

class PendingCardPaymentStore internal constructor(private val preferences: SharedPreferences) {
    fun save(payment: PendingCardPayment): Boolean =
        if (payment.orderId <= 0 || payment.orderKey.isBlank() || payment.billingEmail.isBlank() || payment.paymentUrl.isBlank()) false
        else preferences.edit()
            .putInt(KEY_ORDER_ID, payment.orderId)
            .putString(KEY_ORDER_KEY, payment.orderKey)
            .putString(KEY_BILLING_EMAIL, payment.billingEmail)
            .putString(KEY_PAYMENT_URL, payment.paymentUrl)
            .commit()

    fun load(): PendingCardPayment? {
        val orderId = preferences.getInt(KEY_ORDER_ID, 0)
        val orderKey = preferences.getString(KEY_ORDER_KEY, null).orEmpty()
        val billingEmail = preferences.getString(KEY_BILLING_EMAIL, null).orEmpty()
        val paymentUrl = preferences.getString(KEY_PAYMENT_URL, null).orEmpty()
        if (orderId <= 0 || orderKey.isBlank() || billingEmail.isBlank() || paymentUrl.isBlank()) return null
        return PendingCardPayment(orderId, orderKey, billingEmail, paymentUrl)
    }

    fun clear(): Boolean = preferences.edit().clear().commit()

    companion object {
        private const val FILE_NAME = "criosrango_pending_card_payment"
        private const val KEY_ORDER_ID = "order_id"
        private const val KEY_ORDER_KEY = "order_key"
        private const val KEY_BILLING_EMAIL = "billing_email"
        private const val KEY_PAYMENT_URL = "payment_url"

        fun create(context: Context): PendingCardPaymentStore {
            val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            return PendingCardPaymentStore(prefs)
        }
    }
}
