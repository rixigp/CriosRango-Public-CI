package es.criosrango.shared

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataGetTypeID
import platform.CoreFoundation.CFGetTypeID
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionarySetValue
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanFalse
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import kotlin.native.Platform
import platform.Foundation.NSUserDefaults
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
        val query = createBaseQuery(key)
        try {
            val status = SecItemDelete(query)
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
        val query = createBaseQuery(key)
        try {
            setDictionaryValue(query, kSecReturnData, kCFBooleanTrue)
            setDictionaryValue(query, kSecMatchLimit, kSecMatchLimitOne)

            val result = alloc<CFTypeRefVar>()
            val status = SecItemCopyMatching(query, result.ptr)
            if (status == errSecItemNotFound) return@memScoped null
            if (status != errSecSuccess) {
                logStatus("read", key, status)
                return@memScoped null
            }

            val resultValue = result.value ?: return@memScoped null
            try {
                if (CFGetTypeID(resultValue) != CFDataGetTypeID()) return@memScoped null
                resultValue.reinterpret().toKotlinString()
            } finally {
                CFRelease(resultValue)
            }
        } finally {
            CFRelease(query)
        }
    }

    private fun writeKeychain(key: String, value: String): Boolean {
        val data = value.toCFData()
        try {
            val query = createBaseQuery(key)
            val addStatus = try {
                setDictionaryValue(
                    query,
                    kSecAttrAccessible,
                    kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
                )
                setDictionaryValue(query, kSecValueData, data)
                SecItemAdd(query, null)
            } finally {
                CFRelease(query)
            }

            if (addStatus == errSecSuccess) return true
            if (addStatus != errSecDuplicateItem) {
                logStatus("write", key, addStatus)
                return false
            }

            val update = createMutableDictionary()
            val updateQuery = createBaseQuery(key)
            try {
                setDictionaryValue(update, kSecValueData, data)
                val updateStatus = SecItemUpdate(updateQuery, update)
                if (updateStatus != errSecSuccess) logStatus("update", key, updateStatus)
                return updateStatus == errSecSuccess
            } finally {
                CFRelease(updateQuery)
                CFRelease(update)
            }
        } finally {
            CFRelease(data)
        }
    }

    /**
     * Construct the query directly in a native CFMutableDictionary. The type callbacks retain
     * inserted values, allowing the Create-owned service/account strings to be released below.
     */
    private fun createBaseQuery(key: String): CFMutableDictionaryRef {
        val dictionary = createMutableDictionary()
        val service = createCFString(SERVICE)
        val account = createCFString(key)
        try {
            setDictionaryValue(dictionary, kSecClass, kSecClassGenericPassword)
            setDictionaryValue(dictionary, kSecAttrService, service)
            setDictionaryValue(dictionary, kSecAttrAccount, account)
            // Explicitly disable synchronizable/iCloud Keychain items.
            setDictionaryValue(dictionary, kSecAttrSynchronizable, kCFBooleanFalse)
            return dictionary
        } catch (error: Throwable) {
            CFRelease(dictionary)
            throw error
        } finally {
            CFRelease(service)
            CFRelease(account)
        }
    }

    private fun createMutableDictionary(): CFMutableDictionaryRef =
        CFDictionaryCreateMutable(
            kCFAllocatorDefault,
            0,
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr
        ) ?: error("CFDictionaryCreateMutable returned null")

    private fun setDictionaryValue(
        dictionary: CFMutableDictionaryRef,
        key: CValuesRef<*>?,
        value: CValuesRef<*>?
    ) {
        CFDictionarySetValue(dictionary, key, value)
    }

    private fun createCFString(value: String) =
        CFStringCreateWithCString(
            kCFAllocatorDefault,
            value,
            kCFStringEncodingUTF8
        ) ?: error("CFStringCreateWithCString returned null")

    @OptIn(BetaInteropApi::class)
    private fun String.toCFData() = run {
        val bytes = encodeToByteArray()
        if (bytes.isEmpty()) {
            CFDataCreate(kCFAllocatorDefault, null, 0)
                ?: error("CFDataCreate returned null")
        } else {
            bytes.usePinned { pinned ->
                CFDataCreate(
                    kCFAllocatorDefault,
                    pinned.addressOf(0).reinterpret(),
                    bytes.size.toLong()
                ) ?: error("CFDataCreate returned null")
            }
        }
    }

    private fun platform.CoreFoundation.CFDataRef.toKotlinString(): String? {
        val size = CFDataGetLength(this).toInt()
        if (size == 0) return ""

        val source = CFDataGetBytePtr(this) ?: return null
        val output = ByteArray(size)
        output.usePinned { pinned ->
            memcpy(pinned.addressOf(0), source, size.toULong())
        }
        return output.decodeToString()
    }

    @OptIn(kotlin.experimental.ExperimentalNativeApi::class)
    private fun logStatus(operation: String, key: String, status: Int) {
        if (Platform.isDebugBinary) println("IosSecureKeyValueStore operation=$operation key=$key OSStatus=$status")
    }
}
