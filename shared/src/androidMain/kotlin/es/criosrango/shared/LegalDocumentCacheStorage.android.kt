package es.criosrango.shared

import android.content.Context
import android.content.SharedPreferences

actual object LegalDocumentCacheStorage {
    @Volatile private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.applicationContext.getSharedPreferences("criosrango_legal_cache", Context.MODE_PRIVATE)
    }

    actual fun get(key: String): String? = preferences?.getString(key, null)
    actual fun put(key: String, value: String) {
        val prefs = preferences ?: return
        prefs.edit().putString(key, value).commit()
    }
}

fun initializeLegalDocumentCache(context: Context) = LegalDocumentCacheStorage.initialize(context)

actual fun legalCurrentTimeMillis(): Long = System.currentTimeMillis()
