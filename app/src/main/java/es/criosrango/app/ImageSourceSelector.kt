package es.criosrango.app

private data class SrcSetCandidate(
    val url: String,
    val widthPx: Int
)

internal fun selectResponsiveImageUrl(
    src: String,
    thumbnail: String,
    srcSet: String,
    targetWidthPx: Int,
    preferThumbnailFallback: Boolean = false
): String {
    val candidates = parseSrcSet(srcSet)

    if (candidates.isNotEmpty() && targetWidthPx > 0) {
        candidates.firstOrNull { it.widthPx >= targetWidthPx }?.let { return it.url }
        if (src.isNotBlank()) return src
    }

    if (preferThumbnailFallback && thumbnail.isNotBlank()) return thumbnail
    return src.ifBlank { thumbnail }
}

private fun parseSrcSet(srcSet: String): List<SrcSetCandidate> =
    srcSet
        .split(',')
        .mapNotNull { entry ->
            val tokens = entry.trim().split(Regex("\\s+"))
            if (tokens.size < 2) return@mapNotNull null

            val descriptor = tokens.last()
            if (!descriptor.endsWith('w')) return@mapNotNull null

            val width = descriptor.dropLast(1).toIntOrNull()
                ?.takeIf { it > 0 }
                ?: return@mapNotNull null
            val url = tokens.dropLast(1).joinToString(" ").trim()
                .takeIf { it.isNotBlank() }
                ?: return@mapNotNull null

            SrcSetCandidate(url, width)
        }
        .sortedBy { it.widthPx }
