package es.criosrango.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingCardPaymentStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun processDeathRecovery_restoresSameOrderWithoutCreatingAnother() {
        val prefs = context.getSharedPreferences("h2-process-death", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val original = PendingCardPayment(13001, "wc_order_key_13001", "test@example.com")
        assertEquals(true, PendingCardPaymentStore(prefs).save(original))

        val recreatedStore = PendingCardPaymentStore(prefs)
        assertEquals(original, recreatedStore.load())
        assertEquals(original.orderId, recreatedStore.load()?.orderId)
        assertEquals(original.orderKey, recreatedStore.load()?.orderKey)
        assertEquals(original.billingEmail, recreatedStore.load()?.billingEmail)
    }

    @Test
    fun pendingState_isRetainedUntilExplicitTerminalClear() {
        val prefs = context.getSharedPreferences("h2-terminal", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val store = PendingCardPaymentStore(prefs)
        val payment = PendingCardPayment(13002, "wc_order_key_13002", "test@example.com")
        assertEquals(true, store.save(payment))

        assertEquals(payment, store.load())
        assertEquals(payment, store.load())
        assertEquals(payment, store.load())

        assertEquals(true, store.clear())
        assertNull(store.load())
    }
}
