package es.criosrango.app

import es.criosrango.shared.StoreActiveFilterChipUi
import es.criosrango.shared.StoreProductItemUi
import es.criosrango.shared.StoreProductSortOption

internal fun StoreProduct.toStoreProductItemUi(): StoreProductItemUi {
    val image = images.firstOrNull()
    return StoreProductItemUi(
        id = id,
        nameText = name.cleanWooText(),
        imageIdentity = image?.src,
        displayPriceText = displayPrice(),
        regularPriceText = regularDisplayPrice(),
        showPrice = hasDisplayablePrice,
        isOnSale = onSale
    )
}

internal fun ProductSortMode.toStoreProductSortOption(): StoreProductSortOption =
    when (this) {
        ProductSortMode.RECENT -> StoreProductSortOption.RECENT
        ProductSortMode.PRICE_ASC -> StoreProductSortOption.PRICE_ASC
        ProductSortMode.PRICE_DESC -> StoreProductSortOption.PRICE_DESC
        ProductSortMode.NAME_ASC -> StoreProductSortOption.NAME_ASC
    }

internal fun StoreProductSortOption.toAndroidProductSortMode(): ProductSortMode =
    when (this) {
        StoreProductSortOption.RECENT -> ProductSortMode.RECENT
        StoreProductSortOption.PRICE_ASC -> ProductSortMode.PRICE_ASC
        StoreProductSortOption.PRICE_DESC -> ProductSortMode.PRICE_DESC
        StoreProductSortOption.NAME_ASC -> ProductSortMode.NAME_ASC
    }

internal fun androidActiveFilterChips(
    sizes: Set<String>,
    colors: Set<String>,
    brands: Set<String>,
    allowBrandFilter: Boolean
): List<StoreActiveFilterChipUi> = buildList {
    sizes.sortedWith(compareBy({ productSizeSortKey(it) }, { it })).forEach {
        add(StoreActiveFilterChipUi(id = "size:$it", label = "Talla $it"))
    }
    colors.sorted().forEach {
        add(StoreActiveFilterChipUi(id = "color:$it", label = it))
    }
    if (allowBrandFilter) brands.sorted().forEach {
        add(StoreActiveFilterChipUi(id = "brand:$it", label = it))
    }
}
