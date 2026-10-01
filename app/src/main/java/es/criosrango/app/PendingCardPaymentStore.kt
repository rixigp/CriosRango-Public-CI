package es.criosrango.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException

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
        private const val TAG = "PendingCardPaymentStore"
        private const val FILE_NAME = "criosrango_pending_card_payment"
        private const val KEY_ORDER_ID = "order_id"
        private const val KEY_ORDER_KEY = "order_key"
        private const val KEY_BILLING_EMAIL = "billing_email"
        private const val KEY_PAYMENT_URL = "payment_url"

        @Suppress("DEPRECATION")
        fun create(context: Context): PendingCardPaymentStore {
            val appContext = context.applicationContext
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            fun openEncryptedPreferences(): SharedPreferences =
                EncryptedSharedPreferences.create(
                    appContext,
                    FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )

            val prefs = try {
                openEncryptedPreferences()
            } catch (error: GeneralSecurityException) {
                Log.w(TAG, "Encrypted pending-payment state is unreadable; resetting local state", error)
                resetEncryptedPreferences(appContext)
                openEncryptedPreferences()
            } catch (error: IOException) {
                Log.w(TAG, "Encrypted pending-payment state is malformed; resetting local state", error)
                resetEncryptedPreferences(appContext)
                openEncryptedPreferences()
            }

            return PendingCardPaymentStore(prefs)
        }

        private fun resetEncryptedPreferences(context: Context) {
            // API 24 is the app minimum, so delete only this SharedPreferences file.
            // Do not clear it through EncryptedSharedPreferences: a corrupted file may
            // fail again while trying to decrypt its contents.
            context.deleteSharedPreferences(FILE_NAME)
        }
    }
}
