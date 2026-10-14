package es.criosrango.shared

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.posix.memcpy

actual fun decodeStoreImage(bytes: ByteArray): ImageBitmap? {
    val skiaBitmap = runCatching {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }.getOrNull()
    if (skiaBitmap != null) return skiaBitmap

    if (!bytes.isAvifOrAvis()) return null
    return decodeAvifWithUIKit(bytes)
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun decodeAvifWithUIKit(bytes: ByteArray): ImageBitmap? = runCatching {
    if (bytes.isEmpty()) return@runCatching null

    val encodedData = bytes.usePinned { pinned ->
        NSData.create(
            bytes = pinned.addressOf(0),
            length = bytes.size.toULong()
        )
    }
    val nativeImage = UIImage(data = encodedData) ?: return@runCatching null
    val pngData = UIImagePNGRepresentation(nativeImage) ?: return@runCatching null
    val pngLength = pngData.length.toInt()
    if (pngLength <= 0) return@runCatching null

    val pngSource = pngData.bytes ?: return@runCatching null
    val pngBytes = ByteArray(pngLength)
    pngBytes.usePinned { pinned ->
        memcpy(pinned.addressOf(0), pngSource, pngLength.toULong())
    }
    Image.makeFromEncoded(pngBytes).toComposeImageBitmap()
}.getOrNull()

private fun ByteArray.isAvifOrAvis(): Boolean {
    if (size < 16) return false
    if (this[4] != 'f'.code.toByte() ||
        this[5] != 't'.code.toByte() ||
        this[6] != 'y'.code.toByte() ||
        this[7] != 'p'.code.toByte()
    ) return false

    val declaredBoxSize = (
        ((this[0].toInt() and 0xff) shl 24) or
        ((this[1].toInt() and 0xff) shl 16) or
        ((this[2].toInt() and 0xff) shl 8) or
        (this[3].toInt() and 0xff)
    )
    val scanEnd = minOf(size, if (declaredBoxSize in 16..size) declaredBoxSize else 64)

    fun matchesBrand(offset: Int): Boolean =
        offset + 4 <= scanEnd &&
            this[offset] == 'a'.code.toByte() &&
            this[offset + 1] == 'v'.code.toByte() &&
            this[offset + 2] == 'i'.code.toByte() &&
            (this[offset + 3] == 'f'.code.toByte() || this[offset + 3] == 's'.code.toByte())

    if (matchesBrand(8)) return true

    var offset = 16
    while (offset + 4 <= scanEnd) {
        if (matchesBrand(offset)) return true
        offset += 4
    }
    return false
}
