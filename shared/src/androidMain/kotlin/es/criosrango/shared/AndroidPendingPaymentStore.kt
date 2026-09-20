package es.criosrango.shared

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import es.criosrango.shared.api.PendingPaymentStore
import es.criosrango.shared.model.PendingPayment

class AndroidPendingPaymentStore(context: Context) : PendingPaymentStore {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "criosrango_payment",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    override fun load(): PendingPayment? {
        val id = prefs.getInt("order_id", 0)
        val key = prefs.getString("order_key", null)
        return if (id > 0 && !key.isNullOrBlank()) PendingPayment(id, key) else null
    }
    override fun save(payment: PendingPayment) {
        prefs.edit().putInt("order_id", payment.orderId).putString("order_key", payment.orderKey).apply()
    }
    override fun clear() { prefs.edit().clear().apply() }
}
