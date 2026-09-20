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
    fun processDeathRecovery_restoresSameOrderWithoutCreatingAnother() {
        val prefs = context.getSharedPreferences("h2-process-death", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val original = PendingCardPayment(13001, "wc_order_key_13001", "test@example.com", "https://payment.example/13001")
        assertEquals(true, PendingCardPaymentStore(prefs).save(original))

        val recreatedStore = PendingCardPaymentStore(prefs)
        assertEquals(original, recreatedStore.load())
        assertEquals(original.orderId, recreatedStore.load()?.orderId)
        assertEquals(original.orderKey, recreatedStore.load()?.orderKey)
        assertEquals(original.billingEmail, recreatedStore.load()?.billingEmail)
        assertEquals(original.paymentUrl, recreatedStore.load()?.paymentUrl)
    }

    @Test
    fun pendingState_isRetainedUntilExplicitTerminalClear() {
        val prefs = context.getSharedPreferences("h2-terminal", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = PendingCardPaymentStore(prefs)
        val payment = PendingCardPayment(13002, "wc_order_key_13002", "test@example.com", "https://payment.example/13002")
        assertEquals(true, store.save(payment))

        assertEquals(payment, store.load())
        assertEquals(payment, store.load())
        assertEquals(payment, store.load())

        assertEquals(true, store.clear())
        assertNull(store.load())
    }
    @Test
    fun clear_removesAllPendingPaymentFields() {
        val prefs = context.getSharedPreferences("h2-clear-all", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = PendingCardPaymentStore(prefs)
        val payment = PendingCardPayment(13004, "wc_order_key_13004", "test@example.com", "https://payment.example/13004")

        assertEquals(true, store.save(payment))
        assertEquals(true, store.clear())
        assertNull(store.load())
        assertEquals(0, prefs.getInt("order_id", 0))
        assertNull(prefs.getString("order_key", null))
        assertNull(prefs.getString("billing_email", null))
        assertNull(prefs.getString("payment_url", null))
    }

    @Test
    fun save_rejectsMissingPaymentUrl() {
        val prefs = context.getSharedPreferences("h2-invalid", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = PendingCardPaymentStore(prefs)
        val payment = PendingCardPayment(13003, "wc_order_key_13003", "test@example.com", "")

        assertEquals(false, store.save(payment))
        assertNull(store.load())
    }

}


