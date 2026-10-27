package es.criosrango.shared

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanFalse
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.NSData
import platform.Foundation.NSDictionary
import platform.Foundation.NSString
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUTF8StringEncoding
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

/**
 * Keychain-backed storage for iOS secrets. Legacy defaults are consulted only for
 * one-way migration; a legacy value is removed only after a successful Keychain write.
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosSecureKeyValueStore {
    private const val SERVICE = "es.criosrango.app.secure"

    fun read(key: String, defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults): String? {
        readKeychain(key)?.let {
            // Defensive cleanup of a duplicate left by an interrupted/older migration.
            defaults.removeObjectForKey(key)
            return it
        }

        val legacy = defaults.stringForKey(key) ?: return null
        if (writeKeychain(key, legacy)) defaults.removeObjectForKey(key)
        return legacy
    }

    /** New values are written only to Keychain. Returns false on any Security failure. */
    fun write(key: String, value: String, defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults): Boolean {
        if (!writeKeychain(key, value)) return false
        defaults.removeObjectForKey(key)
        return true
    }

    fun delete(key: String, defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults) {
        val status = SecItemDelete(baseQuery(key) as CFDictionaryRef)
        if (status != errSecSuccess && status != errSecItemNotFound) logStatus("delete", key, status)
        defaults.removeObjectForKey(key)
    }

    private fun readKeychain(key: String): String? = memScoped {
        val query = NSDictionary.dictionaryWithDictionary(
            baseQueryMap(key) + mapOf(
                kSecReturnData to kCFBooleanTrue!!,
                kSecMatchLimit to kSecMatchLimitOne
            )
        )
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query as CFDictionaryRef, result.ptr)
        if (status == errSecItemNotFound) return@memScoped null
        if (status != errSecSuccess) {
            logStatus("read", key, status)
            return@memScoped null
        }
        val data = result.value as? NSData ?: return@memScoped null
        NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString()
    }

    private fun writeKeychain(key: String, value: String): Boolean {
        val data = NSString.create(string = value).dataUsingEncoding(NSUTF8StringEncoding) ?: return false
        val query = NSDictionary.dictionaryWithDictionary(
            baseQueryMap(key) + mapOf(
                kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly,
                kSecValueData to data
            )
        )
        val status = SecItemAdd(query as CFDictionaryRef, null)
        if (status == errSecSuccess) return true
        if (status != errSecDuplicateItem) {
            logStatus("write", key, status)
            return false
        }

        val update = NSDictionary.dictionaryWithDictionary(mapOf(kSecValueData to data))
        val updateStatus = SecItemUpdate(baseQuery(key) as CFDictionaryRef, update as CFDictionaryRef)
        if (updateStatus != errSecSuccess) logStatus("update", key, updateStatus)
        return updateStatus == errSecSuccess
    }

    private fun baseQuery(key: String): NSDictionary = NSDictionary.dictionaryWithDictionary(baseQueryMap(key))

    private fun baseQueryMap(key: String): Map<Any?, Any?> = mapOf(
        kSecClass to kSecClassGenericPassword,
        kSecAttrService to SERVICE,
        kSecAttrAccount to key,
        // Explicitly disable synchronizable/iCloud Keychain items.
        kSecAttrSynchronizable to kCFBooleanFalse!!
    )

    private fun logStatus(operation: String, key: String, status: Int) {
        println("IosSecureKeyValueStore operation=$operation key=$key OSStatus=$status")
    }
}
