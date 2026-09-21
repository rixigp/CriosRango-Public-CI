package es.criosrango.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class LastCheckout(val orderId: Int, val orderKey: String, val paymentUrl: String)

class PendingCardPaymentStore internal constructor(private val preferences: SharedPreferences) {
    fun save(checkout: LastCheckout): Boolean =
        if (checkout.orderId <= 0 || checkout.orderKey.isBlank() || checkout.paymentUrl.isBlank()) false
        else preferences.edit()
            .putInt(KEY_ORDER_ID, checkout.orderId)
            .putString(KEY_ORDER_KEY, checkout.orderKey)
            .putString(KEY_PAYMENT_URL, checkout.paymentUrl)
            .remove(KEY_BILLING_EMAIL)
            .commit()

    fun load(): LastCheckout? {
        val orderId = preferences.getInt(KEY_ORDER_ID, 0)
        val orderKey = preferences.getString(KEY_ORDER_KEY, null).orEmpty()
        val paymentUrl = preferences.getString(KEY_PAYMENT_URL, null).orEmpty()
        if (orderId <= 0 || orderKey.isBlank() || paymentUrl.isBlank()) return null
        return LastCheckout(orderId, orderKey, paymentUrl)
    }

    fun clear(): Boolean = preferences.edit()
        .remove(KEY_ORDER_ID)
        .remove(KEY_ORDER_KEY)
        .remove(KEY_BILLING_EMAIL)
        .remove(KEY_PAYMENT_URL)
        .commit()

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
