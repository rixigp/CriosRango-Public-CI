package es.criosrango.shared

import platform.Foundation.NSDate
import platform.Foundation.NSUserDefaults

actual object LegalDocumentCacheStorage {
    private val defaults = NSUserDefaults.standardUserDefaults
    actual fun get(key: String): String? = defaults.stringForKey(key)
    actual fun put(key: String, value: String) { defaults.setObject(value, forKey = key) }
}

actual fun legalCurrentTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
