package es.criosrango.shared

import android.content.Context
import android.util.AtomicFile
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Android Keystore-backed storage for small credential strings.
 *
 * The file contains only a versioned header, a fresh GCM IV and authenticated ciphertext.
 * No credential plaintext is written to disk.
 */
class AndroidSecureKeyValueStore(context: Context) {
    private val directory = File(context.applicationContext.noBackupFilesDir, DIRECTORY_NAME).apply {
        if (!exists() && !mkdirs()) throw IllegalStateException("Unable to initialize secure storage")
    }

    @Synchronized
    fun get(key: String): String? {
        val file = fileFor(key)
        if (!file.baseFile.exists()) return null
        return try {
            val bytes = AtomicFile(file.baseFile).openRead().use { it.readBytes() }
            val input = DataInputStream(ByteArrayInputStream(bytes))
            if (input.readInt() != MAGIC || input.readInt() != FORMAT_VERSION) {
                throw IllegalArgumentException("Unsupported secure storage format")
            }
            val ivLength = input.readInt()
            if (ivLength != GCM_IV_BYTES || ivLength > input.available()) {
                throw IllegalArgumentException("Invalid secure storage IV")
            }
            val iv = ByteArray(ivLength)
            input.readFully(iv)
            val ciphertextLength = input.readInt()
            if (ciphertextLength < GCM_TAG_BYTES || ciphertextLength != input.available()) {
                throw IllegalArgumentException("Invalid secure storage payload")
            }
            val ciphertext = ByteArray(ciphertextLength)
            input.readFully(ciphertext)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), javax.crypto.spec.GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
        } catch (error: Exception) {
            if (error is android.security.keystore.KeyPermanentlyInvalidatedException) {
                runCatching {
                    KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }.deleteEntry(KEY_ALIAS)
                }
            }
            runCatching { AtomicFile(file.baseFile).delete() }
            null
        }
    }

    @Synchronized
    fun put(key: String, value: String?) {
        if (value == null) {
            remove(key)
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val bytes = ByteArrayOutputStream().use { buffer ->
            DataOutputStream(buffer).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(FORMAT_VERSION)
                output.writeInt(iv.size)
                output.write(iv)
                output.writeInt(ciphertext.size)
                output.write(ciphertext)
            }
            buffer.toByteArray()
        }
        val atomic = AtomicFile(fileFor(key).baseFile)
        var stream: FileOutputStream? = null
        try {
            stream = atomic.startWrite()
            stream.write(bytes)
            stream.fd.sync()
            atomic.finishWrite(stream)
        } catch (error: Exception) {
            stream?.let { atomic.failWrite(it) }
            throw error
        }
    }

    @Synchronized
    fun remove(key: String) {
        runCatching { AtomicFile(fileFor(key).baseFile).delete() }
    }

    private fun fileFor(key: String): AtomicFile {
        require(key.isNotBlank()) { "Secure storage key must not be blank" }
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        val name = digest.joinToString("") { "%02x".format(it) }
        return AtomicFile(File(directory, "$name.bin"))
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
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val DIRECTORY_NAME = "secure-credentials-v1"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "criosrango_android_secure_credentials_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val GCM_IV_BYTES = 12
        const val GCM_TAG_BITS = 128
        const val GCM_TAG_BYTES = GCM_TAG_BITS / 8
        const val MAGIC = 0x43525343
        const val FORMAT_VERSION = 1
    }
}
