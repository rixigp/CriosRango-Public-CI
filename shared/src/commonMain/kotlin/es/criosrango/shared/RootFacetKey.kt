package es.criosrango.shared

enum class RootFacetDimension { SIZES, COLORS, BRANDS }

data class RootFacetKey(
    val scope: StoreRootFilterScope,
    val dimension: RootFacetDimension,
    val sizeIds: List<Int>,
    val colorIds: List<Int>,
    val brandIds: List<Int>
) {
    companion object {
        fun create(scope: StoreRootFilterScope, dimension: RootFacetDimension, sizeIds: List<Int>, colorIds: List<Int>, brandIds: List<Int>): RootFacetKey {
            val sizes = sizeIds.distinct().sorted()
            val colors = colorIds.distinct().sorted()
            val brands = brandIds.distinct().sorted()
            return when (dimension) {
                RootFacetDimension.SIZES -> RootFacetKey(scope, dimension, emptyList(), colors, brands)
                RootFacetDimension.COLORS -> RootFacetKey(scope, dimension, sizes, emptyList(), brands)
                RootFacetDimension.BRANDS -> RootFacetKey(scope, dimension, sizes, colors, emptyList())
            }
        }
    }
}

class RootFacetRequestGeneration {
    private val generations = mutableMapOf<RootFacetDimension, Long>()
    fun begin(dimension: RootFacetDimension): Long {
        val next = (generations[dimension] ?: 0L) + 1L
        generations[dimension] = next
        return next
    }
    fun accepts(dimension: RootFacetDimension, generation: Long, requestedKey: RootFacetKey, currentKey: RootFacetKey): Boolean =
        generations[dimension] == generation && requestedKey == currentKey
}
