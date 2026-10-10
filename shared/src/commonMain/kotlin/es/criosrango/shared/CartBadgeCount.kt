package es.criosrango.shared

/** Number of units represented by a cart snapshot, independent of backend items_count. */
fun cartUnitCount(quantities: Iterable<Int>): Int =
    quantities.sumOf { it.coerceAtLeast(0) }

/** Compact label shared by every cart badge. */
fun cartBadgeText(quantity: Int): String =
    if (quantity > 99) "99+" else quantity.coerceAtLeast(0).toString()
