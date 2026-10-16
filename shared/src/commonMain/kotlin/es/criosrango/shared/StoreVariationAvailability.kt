package es.criosrango.shared
/** Strict shared availability rule; unknown stock or purchasability is unavailable. */
fun isVariationAvailableForPurchase(isInStock: Boolean?, isPurchasable: Boolean?): Boolean = isInStock == true && isPurchasable == true
/** Literal equality after trim/case normalization; an empty attribute is never a wildcard. */
fun variationAttributeValuesMatch(left: String?, right: String?): Boolean {
 val a = left?.trim()?.lowercase().orEmpty(); val b = right?.trim()?.lowercase().orEmpty()
 return a.isNotEmpty() && b.isNotEmpty() && a == b
}
