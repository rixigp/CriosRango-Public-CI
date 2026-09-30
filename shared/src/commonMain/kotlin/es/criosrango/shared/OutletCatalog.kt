package es.criosrango.shared

import es.criosrango.shared.model.StoreCategory
import es.criosrango.shared.model.StoreProduct

data class OutletBubbleDefinition(val key: String, val label: String, val categoryIds: Set<Int>)
private fun outletKey(value: String): String = value.lowercase().trim().replace(Regex("[^a-z0-9]+"), " ")
fun outletBubble(label: String, vararg categoryIds: Int) = OutletBubbleDefinition(outletKey(label), label, categoryIds.toSet())

fun fixedOutletBubbles(outletCategoryId: Int): List<OutletBubbleDefinition> = when (outletCategoryId) {
    446, 475 -> listOf(outletBubble("Abrigos y cazadoras",430),outletBubble("Americanas y trajes",320),outletBubble("Camisas",318),outletBubble("Camisetas y polos",319),outletBubble("Complementos y baño",317),outletBubble("Jerseis y Chaquetas",459),outletBubble("Pantalones y bermudas",321),outletBubble("Sudaderas",471))
    447, 476 -> listOf(outletBubble("Abrigos y cazadoras",431),outletBubble("Camisas y camisetas",324),outletBubble("Chaquetas y chalecos",326),outletBubble("Complementos",468),outletBubble("Jerséis",461),outletBubble("Pantalones y faldas",325),outletBubble("Ropa de fiesta",323),outletBubble("Vestidos, conjuntos y monos casual",322))
    449, 478 -> listOf(outletBubble("Abrigos y cazadoras",433,428),outletBubble("Calzado",421),outletBubble("Ropa de baño",80,286),outletBubble("Ropa de sport",313,289),outletBubble("Ropa de vestir",316,311))
    448, 477 -> listOf(outletBubble("Abrigos y cazadoras",434,429),outletBubble("Ropa de baño",78,287),outletBubble("Ropa de sport",314,290),outletBubble("Ropa de vestir",315,312))
    else -> emptyList()
}

fun productBelongsToOutletOriginCategory(product: StoreProduct, categoryId: Int, categoriesById: Map<Int, StoreCategory>): Boolean {
    val assigned = product.categories.map { it.id } + product.originalCategoryIds
    return assigned.any { assignedId ->
        var current = assignedId
        val visited = mutableSetOf<Int>()
        while (current != 0 && visited.add(current)) {
            if (current == categoryId) return@any true
            current = categoriesById[current]?.parent ?: 0
        }
        false
    }
}
fun productBelongsToOutletBubble(product: StoreProduct, bubble: OutletBubbleDefinition, categoriesById: Map<Int, StoreCategory>): Boolean =
    bubble.categoryIds.any { productBelongsToOutletOriginCategory(product, it, categoriesById) }

fun outletBubbleDisplayLabel(label: String): String = when (label) {
    "Abrigos y cazadoras" -> "Abrigos"
    "Camisas y camisetas" -> "Camisas"
    "Chaquetas y chalecos" -> "Chaquetas"
    "Jerséis" -> "Jerséis"
    "Pantalones y faldas" -> "Pantalones"
    "Ropa de fiesta" -> "Fiesta"
    "Vestidos, conjuntos y monos casual" -> "Vestidos y conjuntos"
    "Americanas y trajes" -> "Americanas"
    "Camisetas y polos" -> "Camisetas"
    "Complementos y baño" -> "Complementos"
    "Jerseis y Chaquetas" -> "Jerséis"
    "Pantalones y bermudas" -> "Pantalones"
    "Ropa de baño" -> "Baño"
    "Ropa de sport" -> "Sport"
    "Ropa de vestir" -> "Vestir"
    else -> label
}