package es.criosrango.app

import android.content.Context
import android.util.Log
import android.util.AtomicFile
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class LastCheckout(val orderId: Int, val orderKey: String, val paymentUrl: String)

internal data class EncryptedPendingBlob(val iv: ByteArray, val ciphertext: ByteArray)

internal interface PendingPaymentCipher {
    fun encrypt(plaintext: ByteArray): EncryptedPendingBlob
    fun decrypt(blob: EncryptedPendingBlob): ByteArray
}

internal class AndroidKeystorePendingPaymentCipher : PendingPaymentCipher {
    override fun encrypt(plaintext: ByteArray): EncryptedPendingBlob {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        require(iv.size == GCM_IV_BYTES)
        cipher.updateAAD(AAD)
        return EncryptedPendingBlob(iv, cipher.doFinal(plaintext))
    }

    override fun decrypt(blob: EncryptedPendingBlob): ByteArray {
        require(blob.iv.size == GCM_IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getExistingKey(), GCMParameterSpec(GCM_TAG_BITS, blob.iv))
        cipher.updateAAD(AAD)
        return cipher.doFinal(blob.ciphertext)
    }

    private fun getExistingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as? SecretKey
            ?: throw GeneralSecurityException("Pending-payment key unavailable")
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "criosrango_pending_payment_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private val AAD = byteArrayOf(0x43, 0x52, 0x50, 0x50, 0x01)
    }
}

class PendingCardPaymentStore internal constructor(
    private val file: File,
    private val cipher: PendingPaymentCipher
) {
    private val atomicFile = AtomicFile(file)

    @Synchronized
    fun save(checkout: LastCheckout): Boolean {
        if (checkout.orderId <= 0 || checkout.orderKey.isBlank() || checkout.paymentUrl.isBlank()) return false
        return try {
            val plaintext = encodeCheckout(checkout)
            val encrypted = cipher.encrypt(plaintext)
            val blob = encodeBlob(encrypted)
            val stream = atomicFile.startWrite()
            try {
                stream.write(blob)
                stream.flush()
                stream.fd.sync()
                atomicFile.finishWrite(stream)
                true
            } catch (error: Exception) {
                atomicFile.failWrite(stream)
                Log.w(TAG, "save failed: ${error.javaClass.simpleName}")
                false
            } finally {
                plaintext.fill(0)
                blob.fill(0)
            }
        } catch (error: Exception) {
            Log.w(TAG, "encrypt/save failed: ${error.javaClass.simpleName}")
            false
        }
    }

    @Synchronized
    fun load(): LastCheckout? {
        if (!file.exists() && !File(file.path + ".bak").exists()) return null
        return try {
            val bytes = atomicFile.openRead().use { it.readBytes() }
            if (bytes.isEmpty()) throw IOException("Empty blob")
            val encrypted = decodeBlob(bytes)
            val plaintext = cipher.decrypt(encrypted)
            try {
                decodeCheckout(plaintext)
            } finally {
                plaintext.fill(0)
                bytes.fill(0)
                encrypted.iv.fill(0)
                encrypted.ciphertext.fill(0)
            }
        } catch (error: Exception) {
            Log.w(TAG, "load failed; pending state unavailable: ${error.javaClass.simpleName}")
            runCatching { atomicFile.delete() }
            null
        }
    }

    @Synchronized
    fun clear(): Boolean = try {
        atomicFile.delete()
        File(file.path + ".new").delete()
        true
    } catch (error: Exception) {
        Log.w(TAG, "clear failed: ${error.javaClass.simpleName}")
        false
    }

    private fun encodeCheckout(checkout: LastCheckout): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(PLAINTEXT_VERSION)
                output.writeInt(checkout.orderId)
                output.writeUTF(checkout.orderKey)
                output.writeUTF(checkout.paymentUrl)
            }
            bytes.toByteArray()
        }

    private fun decodeCheckout(bytes: ByteArray): LastCheckout =
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val version = input.readInt()
            if (version != PLAINTEXT_VERSION) throw IOException("Unknown plaintext version")
            val orderId = input.readInt()
            val orderKey = input.readUTF()
            val paymentUrl = input.readUTF()
            if (input.available() != 0 || orderId <= 0 || orderKey.isBlank() || paymentUrl.isBlank()) {
                throw IOException("Invalid pending marker")
            }
            LastCheckout(orderId, orderKey, paymentUrl)
        }

    private fun encodeBlob(blob: EncryptedPendingBlob): ByteArray =
        ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(BLOB_VERSION)
                output.writeInt(blob.iv.size)
                output.write(blob.iv)
                output.writeInt(blob.ciphertext.size)
                output.write(blob.ciphertext)
            }
            bytes.toByteArray()
        }

    private fun decodeBlob(bytes: ByteArray): EncryptedPendingBlob =
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            if (input.readInt() != MAGIC) throw IOException("Invalid blob magic")
            if (input.readInt() != BLOB_VERSION) throw IOException("Unknown blob version")
            val ivLength = input.readInt()
            if (ivLength != GCM_IV_BYTES || input.available() < ivLength + 4) throw IOException("Invalid IV")
            val iv = ByteArray(ivLength).also(input::readFully)
            val ciphertextLength = input.readInt()
            if (ciphertextLength < GCM_TAG_BYTES || ciphertextLength != input.available()) {
                throw IOException("Invalid ciphertext length")
            }
            val ciphertext = ByteArray(ciphertextLength).also(input::readFully)
            EncryptedPendingBlob(iv, ciphertext)
        }

    companion object {
        private const val TAG = "PendingCardPaymentStore"
        private const val LEGACY_FILE_NAME = "criosrango_pending_card_payment"
        private const val PLAINTEXT_VERSION = 1
        private const val BLOB_VERSION = 1
        private const val MAGIC = 0x43525050
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BYTES = 16

        fun create(context: Context): PendingCardPaymentStore {
            val appContext = context.applicationContext
            // Development builds have no production pending-payment state to migrate.
            // Remove only the old encrypted preferences file; never read legacy plaintext.
            runCatching { appContext.deleteSharedPreferences(LEGACY_FILE_NAME) }
            val file = File(appContext.noBackupFilesDir, "pending-card-payment-v1.bin")
            return PendingCardPaymentStore(file, AndroidKeystorePendingPaymentCipher())
        }
    }
}
