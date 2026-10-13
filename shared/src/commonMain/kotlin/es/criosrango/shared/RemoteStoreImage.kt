package es.criosrango.shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import io.ktor.client.request.get
import io.ktor.client.call.body
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
internal fun RemoteStoreImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val initialBitmap = remember(url) { url?.let(RemoteStoreImageSessionCache::peek) }
    var bitmap by remember(url) { mutableStateOf(initialBitmap) }
    var loading by remember(url) { mutableStateOf(!url.isNullOrBlank() && initialBitmap == null) }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            loading = false
            return@LaunchedEffect
        }
        try {
            bitmap = RemoteStoreImageSessionCache.getOrLoad(url)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            bitmap = null
        } finally {
            loading = false
        }
    }

    Box(modifier.background(Color(0xFFE8E2DD)), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(BitmapPainter(it), contentDescription, Modifier.fillMaxSize(), contentScale = contentScale) }
        if (loading && bitmap == null) CircularProgressIndicator()
    }
}

private object RemoteStoreImageSessionCache {
    private const val MAX_ENTRIES = 64
    private const val MAX_CACHED_PIXELS = 24_000_000L
    private val mutex = Mutex()
    private val cache = mutableMapOf<String, ImageBitmap>()
    private val visibleCache = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())
    private val lru = mutableListOf<String>()
    private val inFlight = mutableMapOf<String, Deferred<ImageBitmap?>>()
    private var cachedPixels = 0L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun peek(url: String): ImageBitmap? = visibleCache.value[url]

    suspend fun getOrLoad(url: String): ImageBitmap? {
        var cached: ImageBitmap? = null
        var pending: Deferred<ImageBitmap?>? = null
        mutex.withLock {
            cache[url]?.let {
                cached = it
                lru.remove(url)
                lru.add(url)
                return@withLock
            }
            pending = inFlight[url]
            if (pending == null) {
                lateinit var request: Deferred<ImageBitmap?>
                request = scope.async {
                    try {
                        val bytes = sharedImageHttpClient.get(url).body<ByteArray>()
                        val decoded = withContext(Dispatchers.Default) { decodeStoreImage(bytes) }
                        if (decoded != null) {
                            mutex.withLock {
                                if (!cache.containsKey(url)) {
                                    val pixels = decoded.width.toLong() * decoded.height.toLong()
                                    if (pixels in 1..MAX_CACHED_PIXELS) {
                                        while (lru.isNotEmpty() &&
                                            (cache.size >= MAX_ENTRIES || cachedPixels + pixels > MAX_CACHED_PIXELS)
                                        ) {
                                            val oldest = lru.removeAt(0)
                                            cache.remove(oldest)?.let {
                                                cachedPixels -= it.width.toLong() * it.height.toLong()
                                            }
                                        }
                                        cache[url] = decoded
                                        lru.add(url)
                                        cachedPixels += pixels
                                        visibleCache.value = cache.toMap()
                                    }
                                }
                            }
                        }
                        decoded
                    } finally {
                        withContext(NonCancellable) {
                            mutex.withLock {
                                if (inFlight[url] === request) inFlight.remove(url)
                            }
                        }
                    }
                }
                inFlight[url] = request
                pending = request
            }
        }
        if (cached != null) return cached
        return (pending ?: return null).await()
    }
}

private val sharedImageHttpClient by lazy { createStoreHttpClient() }

expect fun decodeStoreImage(bytes: ByteArray): ImageBitmap?
