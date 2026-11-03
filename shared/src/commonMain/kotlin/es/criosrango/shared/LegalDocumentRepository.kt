package es.criosrango.shared

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

private const val LEGAL_CACHE_TTL_MILLIS = 24L * 60L * 60L * 1000L
private val legalCacheJson = Json { ignoreUnknownKeys = true }

@Serializable
data class LegalPageCacheEntry(val slug: String, val title: String, val rawHtml: String, val fetchedAt: Long)

internal fun isValidLegalCacheEntry(entry: LegalPageCacheEntry?): Boolean = entry != null && entry.rawHtml.isNotBlank()
internal fun chooseValidLegalEntry(remote: LegalPageCacheEntry?, cached: LegalPageCacheEntry?): LegalPageCacheEntry? =
    if (isValidLegalCacheEntry(remote)) remote else cached?.takeIf { isValidLegalCacheEntry(it) }

expect object LegalDocumentCacheStorage {
    fun get(key: String): String?
    fun put(key: String, value: String)
}
expect fun legalCurrentTimeMillis(): Long

object LegalDocumentRepository {
    private val client = WordPressPagesClient()
    private val lock = Mutex()
    private val inFlight = mutableMapOf<String, Deferred<LegalPageCacheEntry?>>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _updates = MutableStateFlow<Map<String, LegalPageCacheEntry>>(emptyMap())
    val updates: StateFlow<Map<String, LegalPageCacheEntry>> = _updates

    private fun cacheKey(slug: String) = "legal_cache_v1_$slug"

    fun peek(slug: String): LegalPageCacheEntry? {
        _updates.value[slug]?.takeIf { it.rawHtml.isNotBlank() }?.let { return it }
        val loaded = LegalDocumentCacheStorage.get(cacheKey(slug))?.let { raw ->
            runCatching { legalCacheJson.decodeFromString<LegalPageCacheEntry>(raw) }.getOrNull()
        }?.takeIf { it.rawHtml.isNotBlank() } ?: return null
        publish(loaded)
        return loaded
    }

    suspend fun load(slug: String): LegalPageCacheEntry? {
        require(slug.isNotBlank())
        val cached = peek(slug)
        if (cached != null && legalCurrentTimeMillis() - cached.fetchedAt < LEGAL_CACHE_TTL_MILLIS) return cached
        if (cached != null) {
            scope.launch { runCatching { refreshDeduplicated(slug) } }
            return cached
        }
        return refreshDeduplicated(slug)
    }

    private suspend fun refreshDeduplicated(slug: String): LegalPageCacheEntry? {
        val deferred = lock.withLock {
            inFlight[slug] ?: scope.async {
                val page = client.getPageBySlug(slug) ?: return@async null
                val entry = LegalPageCacheEntry(slug, page.title.rendered, page.content.rendered, legalCurrentTimeMillis())
                if (entry.rawHtml.isBlank()) return@async peek(slug)
                val serialized = legalCacheJson.encodeToString(entry)
                lock.withLock {
                    runCatching { LegalDocumentCacheStorage.put(cacheKey(slug), serialized) }
                    publish(entry)
                }
                entry
            }.also { inFlight[slug] = it }
        }
        return try { deferred.await() } finally {
            lock.withLock { if (inFlight[slug] === deferred) inFlight.remove(slug) }
        }
    }

    private fun publish(entry: LegalPageCacheEntry) {
        _updates.update { it + (entry.slug to entry) }
    }
}
