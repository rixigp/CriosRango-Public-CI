package es.criosrango.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PendingCardPaymentStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun processDeathRecovery_restoresOrderIdOrderKeyAndPaymentUrl() {
        val prefs = context.getSharedPreferences("h2-process-death", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = PendingCardPaymentStore(prefs)
        val original = LastCheckout(13001, "wc_order_key_13001", "https://criosrango.es/checkout/order-pay/13001/?key=wc_order_key_13001")
        assertEquals(true, store.save(original))
        val recreatedStore = PendingCardPaymentStore(prefs)
        assertEquals(original, recreatedStore.load())
        assertNull(prefs.getString("billing_email", null))
        assertEquals(original.paymentUrl, prefs.getString("payment_url", null))
    }

    @Test
    fun save_rejectsInvalidMarker() {
        val prefs = context.getSharedPreferences("h2-invalid", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = PendingCardPaymentStore(prefs)
        assertEquals(false, store.save(LastCheckout(0, "key", "https://example.invalid/payment")))
        assertEquals(false, store.save(LastCheckout(13003, "", "https://example.invalid/payment")))
        assertNull(store.load())
    }

    @Test
    fun clear_removesOnlyCheckoutMarker() {
        val prefs = context.getSharedPreferences("h2-clear", Context.MODE_PRIVATE)
        prefs.edit().clear().putString("unrelated", "keep").commit()
        val store = PendingCardPaymentStore(prefs)
        assertEquals(true, store.save(LastCheckout(13004, "wc_order_key_13004", "https://example.invalid/payment/13004")))
        assertEquals(true, store.clear())
        assertNull(store.load())
        assertEquals(0, prefs.getInt("order_id", 0))
        assertNull(prefs.getString("order_key", null))
        assertNull(prefs.getString("billing_email", null))
        assertNull(prefs.getString("payment_url", null))
        assertEquals("keep", prefs.getString("unrelated", null))
    }
    @Test
    fun processDeathPendingPayment_doesNotBlockNewCheckout() {
        val prefs = context.getSharedPreferences("h2-new-checkout", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = PendingCardPaymentStore(prefs)
        assertEquals(
            true,
            store.save(LastCheckout(13005, "wc_order_key_13005", "https://example.invalid/payment/13005"))
        )

        val recreatedStore = PendingCardPaymentStore(prefs)
        assertEquals(13005, recreatedStore.load()?.orderId)
        assertEquals(true, AccountCartCheckoutPolicy.canStartNewCheckout(hasPendingCardPayment = true))
    }

}
