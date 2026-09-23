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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun RemoteStoreImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var loading by remember(url) { mutableStateOf(!url.isNullOrBlank()) }

    LaunchedEffect(url) {
        if (url.isNullOrBlank()) {
            loading = false
            return@LaunchedEffect
        }
        runCatching {
            val bytes = sharedImageHttpClient.get(url).body<ByteArray>()
            withContext(Dispatchers.Default) { decodeStoreImage(bytes) }
        }.onSuccess {
            bitmap = it
        }.also {
            loading = false
        }
    }

    Box(modifier.background(Color(0xFFE8E2DD)), contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(BitmapPainter(it), contentDescription, Modifier.fillMaxSize(), contentScale = contentScale)
        }
        if (loading) CircularProgressIndicator()
    }
}

private val sharedImageHttpClient by lazy { createStoreHttpClient() }

expect fun decodeStoreImage(bytes: ByteArray): ImageBitmap?
