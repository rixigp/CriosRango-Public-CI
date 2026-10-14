package es.criosrango.shared

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation

actual fun decodeStoreImage(bytes: ByteArray): ImageBitmap? {
    val skiaBitmap = runCatching {
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }.getOrNull()
    if (skiaBitmap != null) return skiaBitmap

    if (!bytes.isAvifOrAvis()) return null

    return runCatching {
        val encodedData = bytes.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
        }
        val nativeImage = UIImage(data = encodedData) ?: return@runCatching null
        val pngData = UIImagePNGRepresentation(nativeImage) ?: return@runCatching null
        if (pngData.length == 0uL) return@runCatching null

        val pngBytes = ByteArray(pngData.length.toInt())
        pngBytes.usePinned { pinned ->
            pngData.getBytes(pinned.addressOf(0), pngData.length)
        }
        Image.makeFromEncoded(pngBytes).toComposeImageBitmap()
    }.getOrNull()
}

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
    var offset = 8
    while (offset + 4 <= scanEnd) {
        val brand = String(this, offset, 4, Charsets.ISO_8859_1)
        if (brand == "avif" || brand == "avis") return true
        offset += 4
    }
    return false
}
