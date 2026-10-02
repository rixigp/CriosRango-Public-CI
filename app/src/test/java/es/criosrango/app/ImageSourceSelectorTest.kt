package es.criosrango.app

import es.criosrango.shared.model.ProductImage as SharedProductImage
import kotlin.test.Test
import kotlin.test.assertEquals

class ImageSourceSelectorTest {
    private val src = "https://example.com/full.jpg"
    private val thumbnail = "https://example.com/thumb.jpg"
    private val srcSet = "https://example.com/300.jpg 300w, https://example.com/600.jpg 600w, https://example.com/768.jpg 768w"

    @Test
    fun selectsSmallestCandidateAtOrAboveTarget() {
        assertEquals(
            "https://example.com/600.jpg",
            selectResponsiveImageUrl(src, thumbnail, srcSet, 500)
        )
    }

    @Test
    fun selectsExactWidth() {
        assertEquals(
            "https://example.com/600.jpg",
            selectResponsiveImageUrl(src, thumbnail, srcSet, 600)
        )
    }

    @Test
    fun selectsSmallestCandidateForLowerTarget() {
        assertEquals(
            "https://example.com/300.jpg",
            selectResponsiveImageUrl(src, thumbnail, srcSet, 250)
        )
    }

    @Test
    fun fallsBackToOriginalWhenTargetExceedsLargestCandidate() {
        assertEquals(
            src,
            selectResponsiveImageUrl(src, thumbnail, srcSet, 1000)
        )
    }

    @Test
    fun sortsUnorderedCandidates() {
        val unordered = "https://example.com/768.jpg 768w, https://example.com/300.jpg 300w, https://example.com/600.jpg 600w"
        assertEquals(
            "https://example.com/600.jpg",
            selectResponsiveImageUrl(src, thumbnail, unordered, 500)
        )
    }

    @Test
    fun ignoresMalformedEntriesWithoutCrashing() {
        val malformed = "not-a-candidate, https://example.com/600.jpg nope, https://example.com/600.jpg 600w, broken 0w"
        assertEquals(
            "https://example.com/600.jpg",
            selectResponsiveImageUrl(src, thumbnail, malformed, 500)
        )
    }

    @Test
    fun fallsBackWhenSrcSetIsEmpty() {
        assertEquals(
            src,
            selectResponsiveImageUrl(src, thumbnail, "", 500)
        )
        assertEquals(
            thumbnail,
            selectResponsiveImageUrl("", thumbnail, "", 78, preferThumbnailFallback = true)
        )
    }

    @Test
    fun neverLeavesImageEmptyWhenOnlyThumbnailExists() {
        assertEquals(
            thumbnail,
            selectResponsiveImageUrl("", thumbnail, "", 500)
        )
    }

    @Test
    fun adapterPreservesSrcSet() {
        val shared = SharedProductImage(
            src = src,
            thumbnail = thumbnail,
            srcSet = "https://example.com/600.jpg 600w"
        )

        val android = shared.toAndroid()

        assertEquals(shared.srcSet, android.srcSet)
    }
}
