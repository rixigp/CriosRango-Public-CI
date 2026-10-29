package es.criosrango.shared

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanFalse
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.NSData
import platform.Foundation.NSString
import kotlin.native.Platform
import platform.Foundation.NSUserDefaults
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.SecItemUpdate
import platform.Security.errSecDuplicateItem
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrService
import platform.Security.kSecAttrSynchronizable
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData
import platform.posix.memcpy

/**
 * Keychain-only storage for iOS secrets.
 *
 * Legacy UserDefaults values are deliberately discarded without reading or copying them.
 * This is an intentional clean cutover: missing Keychain items remain absent.
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosSecureKeyValueStore {
    private const val SERVICE = "es.criosrango.app.secure"

    private val LEGACY_SENSITIVE_KEYS = listOf(
        "woo_cart_token",
        "woo_nonce",
        "woo_cookie_header",
        "criosrango_account_token",
        "criosrango_ios_pending_payment_order_key",
        "criosrango_ios_pending_payment_url",
        "criosrango_pending_claim_order_key",
        "criosrango_push_pending_unregister_account"
    )

    fun read(key: String, defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults): String? {
        clearLegacySensitiveValues(defaults)
        return readKeychain(key)
    }

    /** New values are written only to Keychain; legacy values are never a fallback. */
    fun write(key: String, value: String, defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults): Boolean {
        clearLegacySensitiveValues(defaults)
        return writeKeychain(key, value)
    }

    fun delete(key: String, defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults) {
        clearLegacySensitiveValues(defaults)
        val query = createDictionary(baseQueryMap(key))
        try {
            val status = SecItemDelete(query as CFDictionaryRef)
            if (status != errSecSuccess && status != errSecItemNotFound) logStatus("delete", key, status)
        } finally {
            CFRelease(query)
        }
    }

    /**
     * Idempotently discard all known legacy sensitive keys without retrieving their values.
     * This runs on every access so logout and ordinary store use also clean old installations.
     */
    private fun clearLegacySensitiveValues(defaults: NSUserDefaults) {
        LEGACY_SENSITIVE_KEYS.forEach(defaults::removeObjectForKey)
    }

    private fun readKeychain(key: String): String? = memScoped {
        val query = createDictionary(
            baseQueryMap(key) + mapOf(
                kSecReturnData to kCFBooleanTrue!!,
                kSecMatchLimit to kSecMatchLimitOne
            )
        )
        try {
            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query as CFDictionaryRef, result.ptr)
            if (status == errSecItemNotFound) return@memScoped null
            if (status != errSecSuccess) {
                logStatus("read", key, status)
                return@memScoped null
            }
            val data = result.value as? NSData ?: return@memScoped null
            data.toKotlinString()
        } finally {
            CFRelease(query)
        }
    }

    private fun writeKeychain(key: String, value: String): Boolean {
        val data = value.toNSData()
        val query = createDictionary(
            baseQueryMap(key) + mapOf(
                kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                kSecValueData to data
            )
        )
        try {
            val status = SecItemAdd(query as CFDictionaryRef, null)
            if (status == errSecSuccess) return true
            if (status != errSecDuplicateItem) {
                logStatus("write", key, status)
                return false
            }
        } finally {
            CFRelease(query)
        }

        val update = createDictionary(mapOf(kSecValueData to data))
        val updateQuery = createDictionary(baseQueryMap(key))
        try {
            val updateStatus = SecItemUpdate(
                updateQuery as CFDictionaryRef,
                update as CFDictionaryRef
            )
            if (updateStatus != errSecSuccess) logStatus("update", key, updateStatus)
            return updateStatus == errSecSuccess
        } finally {
            CFRelease(updateQuery)
            CFRelease(update)
        }
    }

    private fun baseQueryMap(key: String): Map<Any?, Any?> = mapOf(
        kSecClass to kSecClassGenericPassword,
        kSecAttrService to (SERVICE as NSString),
        kSecAttrAccount to (key as NSString),
        // Explicitly disable synchronizable/iCloud Keychain items.
        kSecAttrSynchronizable to kCFBooleanFalse!!
    )

    private fun createDictionary(values: Map<Any?, Any?>): CFMutableDictionaryRef {
        val dictionary = CFDictionaryCreateMutable(
            kCFAllocatorDefault,
            values.size.toLong(),
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr
        ) ?: error("CFDictionaryCreateMutable returned null")
        values.forEach { (key, value) ->
            CFDictionaryAddValue(dictionary, key.asCFTypeRef(), value.asCFTypeRef())
        }
        return dictionary
    }

    @OptIn(
        ExperimentalForeignApi::class,
        BetaInteropApi::class
    )
    private fun String.toNSData(): NSData {
        val byteArray = encodeToByteArray()

        if (byteArray.isEmpty()) {
            val dummy = byteArrayOf(0)

            return dummy.usePinned { pinned ->
                NSData.create(
                    bytes = pinned.addressOf(0),
                    length = 0UL
                )
            }
        }

        return byteArray.usePinned { pinned ->
            NSData.create(
                bytes = pinned.addressOf(0),
                length = byteArray.size.toULong()
            )
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun NSData.toKotlinString(): String? {
        val size = length.toInt()

        if (size == 0) {
            return ""
        }

        val source = bytes ?: return null
        val output = ByteArray(size)

        output.usePinned { pinned ->
            memcpy(
                pinned.addressOf(0),
                source,
                length
            )
        }

        return output.decodeToString()
    }

    private fun Any?.asCFTypeRef(): CFTypeRef = when (this) {
        is String -> (this as NSString) as CFTypeRef
        is NSString -> this as CFTypeRef
        is NSData -> this as CFTypeRef
        null -> error("Null is not a valid Keychain dictionary value")
        else -> this as CFTypeRef // CoreFoundation constants supplied by Security/CoreFoundation.
    }

    @OptIn(kotlin.experimental.ExperimentalNativeApi::class)
    private fun logStatus(operation: String, key: String, status: Int) {
        if (Platform.isDebugBinary) println("IosSecureKeyValueStore operation=$operation key=$key OSStatus=$status")
    }
}
