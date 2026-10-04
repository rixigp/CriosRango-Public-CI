package es.criosrango.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
class PendingCardPaymentStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val key = SecretKeySpec(ByteArray(32) { (it + 1).toByte() }, "AES")

    private fun file(name: String): File = File(context.cacheDir, "$name.bin").apply {
        delete()
        File(path + ".bak").delete()
        File(path + ".new").delete()
    }

    private fun store(file: File, cipher: PendingPaymentCipher = TestPendingPaymentCipher(key)) =
        PendingCardPaymentStore(file, cipher)

    @Test
    fun saveLoadAndProcessRecreation_restoreEncryptedMarker() {
        val file = file("pending-process-death")
        val original = LastCheckout(13001, "wc_order_key_13001", "https://criosrango.es/order-pay/13001/?key=wc_order_key_13001")
        assertTrue(store(file).save(original))
        assertEquals(original, store(file).load())
        val storedBytes = file.readBytes()
        val storedText = storedBytes.toString(Charsets.ISO_8859_1)
        assertFalse(storedText.contains(original.orderKey))
        assertFalse(storedText.contains(original.paymentUrl))
    }

    @Test
    fun exhaustedMarkerSurvivesRestart() {
        val file = file("pending-exhausted-restart")
        val marker = LastCheckout(13010, "wc_order_key_13010", "https://example.invalid/payment/13010")
        assertTrue(store(file).save(marker))
        assertEquals(marker, store(file).load())
    }

    @Test
    fun startupWithoutMarkerReturnsNull() {
        assertNull(store(file("pending-empty-startup")).load())
    }

    @Test
    fun terminalUnpaidMarkerCanBeCleared() {
        val file = file("pending-terminal-unpaid")
        val store = store(file)
        assertTrue(store.save(LastCheckout(13011, "wc_order_key_13011", "https://example.invalid/payment/13011")))
        assertTrue(store.clear())
        assertNull(store.load())
    }

    @Test
    fun cancelledPaymentMarkerCanBeCleared() {
        val file = file("pending-cancelled")
        val store = store(file)
        assertTrue(store.save(LastCheckout(13012, "wc_order_key_13012", "https://example.invalid/payment/13012")))
        assertTrue(store.clear())
        assertNull(store.load())
    }

    @Test
    fun overwrite_returnsLatestMarker() {
        val file = file("pending-overwrite")
        val store = store(file)
        assertTrue(store.save(LastCheckout(13001, "first-key", "https://example.invalid/first")))
        val latest = LastCheckout(13002, "second-key", "https://example.invalid/second")
        assertTrue(store.save(latest))
        assertEquals(latest, store.load())
    }

    @Test
    fun clearRemovesBlobAndLoadReturnsNull() {
        val file = file("pending-clear")
        val store = store(file)
        assertTrue(store.save(LastCheckout(13004, "wc_order_key_13004", "https://example.invalid/payment")))
        assertTrue(store.clear())
        assertNull(store.load())
        assertFalse(file.exists())
        assertFalse(File(file.path + ".bak").exists())
        assertFalse(File(file.path + ".new").exists())
    }

    @Test
    fun invalidMarkerIsRejected() {
        val file = file("pending-invalid")
        val store = store(file)
        assertFalse(store.save(LastCheckout(0, "key", "https://example.invalid/payment")))
        assertFalse(store.save(LastCheckout(13003, "", "https://example.invalid/payment")))
        assertNull(store.load())
    }

    @Test
    fun corruptBlobDoesNotCrashAndIsRemoved() {
        val file = file("pending-corrupt")
        assertTrue(store(file).save(LastCheckout(13005, "secret-key", "https://example.invalid/secret")))
        file.writeBytes(byteArrayOf(1, 2, 3))
        assertNull(store(file).load())
        assertFalse(file.exists())
    }

    @Test
    fun manipulatedCiphertextFailsAuthenticationSafely() {
        val file = file("pending-tampered")
        assertTrue(store(file).save(LastCheckout(13006, "secret-key", "https://example.invalid/secret")))
        val bytes = file.readBytes()
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x40).toByte()
        file.writeBytes(bytes)
        assertNull(store(file).load())
        assertFalse(file.exists())
    }

    @Test
    fun invalidIvIsRejectedSafely() {
        val file = file("pending-invalid-iv")
        assertTrue(store(file).save(LastCheckout(13007, "secret-key", "https://example.invalid/secret")))
        val bytes = file.readBytes()
        bytes[11] = 11
        file.writeBytes(bytes)
        assertNull(store(file).load())
        assertFalse(file.exists())
    }

    @Test
    fun unknownBlobVersionIsRejectedSafely() {
        val file = file("pending-unknown-version")
        assertTrue(store(file).save(LastCheckout(13008, "secret-key", "https://example.invalid/secret")))
        val bytes = file.readBytes()
        bytes[7] = 2
        file.writeBytes(bytes)
        assertNull(store(file).load())
        assertFalse(file.exists())
    }

    @Test
    fun incompleteTemporaryFileDoesNotInventMarker() {
        val file = file("pending-temp-only")
        File(file.path + ".new").writeBytes(byteArrayOf(1, 2, 3))
        assertNull(store(file).load())
    }

    @Test
    fun unavailableKeyReturnsNoMarkerWithoutCrash() {
        val file = file("pending-key-unavailable")
        assertTrue(store(file).save(LastCheckout(13009, "secret-key", "https://example.invalid/secret")))
        val unavailable = object : PendingPaymentCipher {
            override fun encrypt(plaintext: ByteArray): EncryptedPendingBlob = throw GeneralSecurityException("unavailable")
            override fun decrypt(blob: EncryptedPendingBlob): ByteArray = throw GeneralSecurityException("unavailable")
        }
        assertNull(store(file, unavailable).load())
        assertFalse(file.exists())
    }

}

internal class TestPendingPaymentCipher(private val key: SecretKeySpec) : PendingPaymentCipher {
    override fun encrypt(plaintext: ByteArray): EncryptedPendingBlob {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return EncryptedPendingBlob(cipher.iv, cipher.doFinal(plaintext))
    }

    override fun decrypt(blob: EncryptedPendingBlob): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, blob.iv))
        return cipher.doFinal(blob.ciphertext)
    }
}
