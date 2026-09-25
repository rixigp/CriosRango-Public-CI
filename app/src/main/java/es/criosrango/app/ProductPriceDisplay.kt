package es.criosrango.app

val StoreProduct.hasDisplayablePrice: Boolean
    get() = prices.price.toLongOrNull()?.let { it > 0L } == true
